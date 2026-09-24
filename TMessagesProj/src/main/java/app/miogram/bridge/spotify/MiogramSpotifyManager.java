package app.miogram.bridge.spotify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import app.miogram.bridge.ai.tools.MioTool;
import app.miogram.bridge.lyrics.MiogramLrcModel;
import app.miogram.bridge.lyrics.MiogramLyricsEngine;

/**
 * Spotify Integration Bridge for Miogram.
 * Intercepts Spotify Android broadcasts (metadata, playback state)
 * to provide real-time now-playing info, synced lyrics and MioTool actions.
 */
public class MiogramSpotifyManager {

    public static final String SPOTIFY_PACKAGE = "com.spotify.music";
    public static final String ACTION_METADATA_CHANGED = "com.spotify.music.metadatachanged";
    public static final String ACTION_PLAYBACK_STATE_CHANGED = "com.spotify.music.playbackstatechanged";
    public static final String ACTION_QUEUE_CHANGED = "com.spotify.music.queuechanged";

    private static volatile MiogramSpotifyManager instance;

    public static MiogramSpotifyManager getInstance() {
        if (instance == null) {
            synchronized (MiogramSpotifyManager.class) {
                if (instance == null) {
                    instance = new MiogramSpotifyManager();
                }
            }
        }
        return instance;
    }

    public interface SpotifyListener {
        void onSpotifyTrackChanged(String track, String artist, boolean isPlaying);
        void onSpotifyPlaybackChanged(boolean isPlaying);
    }

    private final List<SpotifyListener> listeners = new CopyOnWriteArrayList<>();

    private boolean registered = false;
    private boolean isPlaying = false;
    private String currentTrack = "";
    private String currentArtist = "";
    private String currentAlbum = "";
    private String currentTrackUri = "";
    private String currentAlbumArtUrl = "";
    private long durationMs = 0;
    private long lastPositionMs = 0;
    private long lastPositionTimestamp = 0;
    /** Last wall-clock broadcast from Spotify (any action). */
    private long lastBroadcastTime = 0;
    /** A paused/idle track older than this stops broadcasting (stale song bug). */
    private static final long STALE_TRACK_AGE_MS = 15 * 60 * 1000L;

    private final Map<String, String> artCache = new ConcurrentHashMap<>();

    static {
        try {
            MioTool.register(new MioTool.Def(
                    "spotify_now_playing",
                    "Spotify Now Playing",
                    "Returns current song, artist, album, duration and active lyrics line from Spotify.",
                    false,
                    (account, params, cb) -> {
                        MiogramSpotifyManager sm = getInstance();
                        JSONObject obj = new JSONObject();
                        try {
                            obj.put("playing", sm.isPlaying());
                            obj.put("track", sm.getCurrentTrack());
                            obj.put("artist", sm.getCurrentArtist());
                            obj.put("album", sm.getCurrentAlbum());
                            obj.put("url", sm.getTrackWebUrl());
                            obj.put("artwork", sm.getCurrentAlbumArtUrl());
                            obj.put("lyrics_line", sm.getCurrentLyricsLine());
                            if (cb != null) cb.run(obj.toString());
                        } catch (Throwable t) {
                            if (cb != null) cb.run("Spotify status error: " + t.getMessage());
                        }
                    }
            ));

            MioTool.register(new MioTool.Def(
                    "spotify_share_track",
                    "Spotify Share Track",
                    "Gets the Spotify web share link for currently playing song.",
                    false,
                    (account, params, cb) -> {
                        MiogramSpotifyManager sm = getInstance();
                        String url = sm.getTrackWebUrl();
                        if (TextUtils.isEmpty(url)) {
                            if (cb != null) cb.run("No track is currently playing in Spotify.");
                        } else {
                            if (cb != null) cb.run("Now playing: " + sm.getCurrentTrack() + " - " + sm.getCurrentArtist() + "\n" + url);
                        }
                    }
            ));
        } catch (Throwable ignore) {}
    }

    private String getExtraString(Intent intent, String... keys) {
        if (intent == null || intent.getExtras() == null) return null;
        for (String k : keys) {
            if (intent.hasExtra(k)) {
                Object val = intent.getExtras().get(k);
                if (val != null) {
                    String s = String.valueOf(val).trim();
                    if (!s.isEmpty()) return s;
                }
            }
        }
        return null;
    }

    private Boolean getExtraBoolean(Intent intent, String... keys) {
        if (intent == null || intent.getExtras() == null) return null;
        for (String k : keys) {
            if (intent.hasExtra(k)) {
                Object val = intent.getExtras().get(k);
                if (val instanceof Boolean) return (Boolean) val;
                if (val instanceof Number) return ((Number) val).intValue() != 0;
                if (val instanceof String) {
                    String s = ((String) val).trim().toLowerCase(Locale.ROOT);
                    if ("true".equals(s) || "1".equals(s)) return true;
                    if ("false".equals(s) || "0".equals(s)) return false;
                }
            }
        }
        return null;
    }

    private int getExtraInt(Intent intent, int fallback, String... keys) {
        if (intent == null || intent.getExtras() == null) return fallback;
        for (String k : keys) {
            if (intent.hasExtra(k)) {
                Object val = intent.getExtras().get(k);
                if (val instanceof Number) return ((Number) val).intValue();
                if (val instanceof String) {
                    try {
                        return Integer.parseInt(((String) val).trim());
                    } catch (Throwable ignore) {}
                }
            }
        }
        return fallback;
    }

    private final BroadcastReceiver spotifyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            lastBroadcastTime = SystemClock.elapsedRealtime();
            String action = intent.getAction();

            String id = getExtraString(intent, "id", "uri", "trackId");
            String artist = getExtraString(intent, "artist", "artistName", "singer");
            String album = getExtraString(intent, "album", "albumName");
            String track = getExtraString(intent, "track", "trackName", "title", "song");
            int length = getExtraInt(intent, (int) durationMs, "length", "duration");
            int position = getExtraInt(intent, (int) lastPositionMs, "playbackPosition", "currentPlaybackPosition", "position");
            Boolean playingBool = getExtraBoolean(intent, "playing", "playstate", "playState", "isPlaying");

            boolean metaUpdated = false;
            if (!TextUtils.isEmpty(track) && !track.equals(currentTrack)) {
                currentTrack = track;
                metaUpdated = true;
            }
            if (artist != null && !artist.isEmpty() && !artist.equals(currentArtist)) {
                currentArtist = artist;
                metaUpdated = true;
            }
            if (album != null && !album.isEmpty() && !album.equals(currentAlbum)) {
                currentAlbum = album;
            }
            if (id != null && !id.isEmpty()) {
                currentTrackUri = id;
            }
            if (length > 0) {
                durationMs = length;
            }
            if (position >= 0) {
                lastPositionMs = position;
                lastPositionTimestamp = SystemClock.elapsedRealtime();
            }

            if (playingBool != null) {
                isPlaying = playingBool;
            } else if (ACTION_METADATA_CHANGED.equals(action) && !TextUtils.isEmpty(track)) {
                boolean musicActive = false;
                try {
                    AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                    musicActive = am != null && am.isMusicActive();
                } catch (Throwable ignore) {}
                isPlaying = musicActive;
            }

            saveStateToPrefs();

            if (metaUpdated || ACTION_METADATA_CHANGED.equals(action)) {
                fetchAlbumArt(currentTrackUri, currentArtist, currentTrack);

                // Prefetch lyrics via MiogramLyricsEngine
                if (!TextUtils.isEmpty(currentTrack)) {
                    MiogramLyricsEngine.getInstance().fetchLyricsByMeta(currentArtist, currentTrack, (int) (durationMs / 1000), song -> notifyTrackChanged());
                }

                notifyTrackChanged();
                app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
            } else {
                notifyPlaybackChanged();
                app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
            }
        }
    };

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            if (isPlaying) {
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.messagePlayingProgressDidChanged, 0);
                } catch (Throwable ignore) {}
                AndroidUtilities.runOnUIThread(this, 1000);
            }
        }
    };

    private void updateTicker() {
        AndroidUtilities.cancelRunOnUIThread(progressTicker);
        if (isPlaying) {
            AndroidUtilities.runOnUIThread(progressTicker, 1000);
        }
    }

    private void loadStateFromPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        SharedPreferences sp = ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE);
        currentTrack = sp.getString("last_track", "");
        currentArtist = sp.getString("last_artist", "");
        currentAlbum = sp.getString("last_album", "");
        currentTrackUri = sp.getString("last_uri", "");
        currentAlbumArtUrl = sp.getString("last_art_url", "");
        durationMs = sp.getLong("last_duration", 0);
    }

    private void saveStateToPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .edit()
                .putString("last_track", currentTrack)
                .putString("last_artist", currentArtist)
                .putString("last_album", currentAlbum)
                .putString("last_uri", currentTrackUri)
                .putString("last_art_url", currentAlbumArtUrl)
                .putLong("last_duration", durationMs)
                .apply();
    }

    private MiogramSpotifyManager() {
        loadStateFromPrefs();
        ensureRegistered(ApplicationLoader.applicationContext);
    }

    public synchronized void ensureRegistered(Context context) {
        if (registered || context == null) return;
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_METADATA_CHANGED);
            filter.addAction(ACTION_PLAYBACK_STATE_CHANGED);
            filter.addAction(ACTION_QUEUE_CHANGED);
            filter.addAction("com.android.music.metachanged");
            filter.addAction("com.android.music.playstatechanged");
            filter.addAction("com.android.music.playbackcomplete");
            filter.addAction("com.android.music.queuechanged");
            filter.addAction("com.htc.music.metachanged");
            filter.addAction("fm.last.android.metachanged");
            filter.addAction("com.sec.android.app.music.metachanged");
            filter.addAction("com.nullsoft.winamp.metachanged");
            filter.addAction("com.amazon.mp3.metachanged");
            filter.addAction("com.miui.player.metachanged");
            filter.addAction("com.real.IMP.metachanged");
            filter.addAction("com.sonyericsson.music.metachanged");
            filter.addAction("com.rdio.android.metachanged");
            filter.addAction("com.samsung.sec.android.MusicPlayer.metachanged");
            filter.addAction("com.andrew.apollo.metachanged");
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(spotifyReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(spotifyReceiver, filter);
            }
            registered = true;
        } catch (Throwable t) {
            FileLog.e("MiogramSpotifyManager: register receiver failed", t);
        }
    }

    public static final String PREF_BRIDGE_ENABLED = "spotify_bridge_enabled_v1";
    public static final String PREF_LINKED_USERNAME = "spotify_linked_username_v1";

    public static String sanitizeUsername(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.startsWith("https://open.spotify.com/user/")) s = s.substring("https://open.spotify.com/user/".length());
        else if (s.startsWith("http://open.spotify.com/user/")) s = s.substring("http://open.spotify.com/user/".length());
        else if (s.startsWith("open.spotify.com/user/")) s = s.substring("open.spotify.com/user/".length());
        else if (s.startsWith("spotify:user:")) s = s.substring("spotify:user:".length());
        if (s.startsWith("@")) s = s.substring(1);
        if (s.contains("?")) s = s.substring(0, s.indexOf("?"));
        if (s.contains("#")) s = s.substring(0, s.indexOf("#"));
        if (s.contains("/")) s = s.substring(0, s.indexOf("/"));
        return s.trim();
    }

    public String getLinkedUsername() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return "";
        return ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .getString(PREF_LINKED_USERNAME, "");
    }

    public void setLinkedUsername(String username) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        String clean = sanitizeUsername(username);
        SharedPreferences prefs = ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_LINKED_USERNAME, clean).apply();
        if (TextUtils.isEmpty(clean)) {
            app.miogram.bridge.presence.MiogramLinkOwner.clear(prefs);
        } else {
            app.miogram.bridge.presence.MiogramLinkOwner.stamp(prefs);
        }
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public boolean isBridgeEnabled() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return false;
        return ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .getBoolean(PREF_BRIDGE_ENABLED, false);
    }

    public void setBridgeEnabled(boolean enabled) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_BRIDGE_ENABLED, enabled)
                .apply();
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public boolean isLinked() {
        return !TextUtils.isEmpty(getLinkedUsername()) || isBridgeEnabled() || isPlaying() || !TextUtils.isEmpty(currentTrack);
    }

    /**
     * Account-scoped visibility. A linked username belongs to the account that
     * linked it; pure device state (bridge/live track, no username) is shared.
     */
    public boolean isActiveFor(long tgUserId) {
        if (!isLinked()) return false;
        if (TextUtils.isEmpty(getLinkedUsername())) return true;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return true;
            SharedPreferences prefs = ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE);
            return app.miogram.bridge.presence.MiogramLinkOwner.visibleFor(prefs, tgUserId);
        } catch (Throwable ignore) {
            return true;
        }
    }

    public void openSpotifyLogin(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://accounts.spotify.com/login"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void openProfile(Context context, String username) {
        if (context == null) return;
        String u = !TextUtils.isEmpty(username) ? sanitizeUsername(username) : getLinkedUsername();
        String url = !TextUtils.isEmpty(u) ? ("https://open.spotify.com/user/" + u) : "https://open.spotify.com";
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void showLinkUserDialog(Context context, Runnable onDone) {
        if (context == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(app.miogram.bridge.MiogramLocale.get("Spotify Акаунт", "Spotify Аккаунт", "Spotify Account"));
        builder.setMessage(app.miogram.bridge.MiogramLocale.get(
                "Введіть ваш Spotify username або посилання на профіль (наприклад, https://open.spotify.com/user/...):",
                "Введите ваш Spotify username или ссылку на профиль (например, https://open.spotify.com/user/...):",
                "Enter your Spotify username or profile URL (e.g. https://open.spotify.com/user/...):"
        ));
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(true);
        input.setText(getLinkedUsername());
        builder.setView(input);

        builder.setPositiveButton(app.miogram.bridge.MiogramLocale.get("Підключити", "Подключить", "Connect"), (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                setLinkedUsername(val);
                setBridgeEnabled(true);
            }
            if (onDone != null) onDone.run();
        });

        builder.setNeutralButton(app.miogram.bridge.MiogramLocale.get("Увійти", "Войти", "Login"), (dialog, which) -> {
            openSpotifyLogin(context);
        });

        builder.setNegativeButton(app.miogram.bridge.MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        builder.show();
    }

    public void addListener(SpotifyListener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(SpotifyListener l) {
        listeners.remove(l);
    }

    private void notifyTrackChanged() {
        updateTicker();
        AndroidUtilities.runOnUIThread(() -> {
            for (SpotifyListener l : listeners) {
                try {
                    l.onSpotifyTrackChanged(currentTrack, currentArtist, isPlaying);
                } catch (Throwable ignore) {}
            }
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged);
            } catch (Throwable ignore) {}
        });
    }

    private void notifyPlaybackChanged() {
        updateTicker();
        AndroidUtilities.runOnUIThread(() -> {
            for (SpotifyListener l : listeners) {
                try {
                    l.onSpotifyPlaybackChanged(isPlaying);
                } catch (Throwable ignore) {}
            }
            try {
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.messagePlayingPlayStateChanged);
            } catch (Throwable ignore) {}
        });
    }

    public void openSpotifyApp(Context context) {
        if (context == null) return;
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(SPOTIFY_PACKAGE);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return;
            }
        } catch (Throwable ignore) {}
        try {
            Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"));
            webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(webIntent);
        } catch (Throwable ignore) {}
    }

    public boolean isPlaying() {
        if (!isPlaying || TextUtils.isEmpty(currentTrack)) {
            return false;
        }
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx != null) {
            try {
                AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
                if (am != null && !am.isMusicActive()) {
                    isPlaying = false;
                    return false;
                }
            } catch (Throwable ignore) {}
        }
        return true;
    }

    public boolean hasTrack() {
        return !TextUtils.isEmpty(currentTrack);
    }

    /**
     * Drops a long-idle track so a song switched off an hour ago stops
     * haunting presence, hints and cards. Playing state is never dropped.
     */
    public void dropStaleTrack() {
        if (TextUtils.isEmpty(currentTrack) || isPlaying) return;
        long age = lastBroadcastTime <= 0 ? Long.MAX_VALUE : (SystemClock.elapsedRealtime() - lastBroadcastTime);
        if (age < STALE_TRACK_AGE_MS) return;
        currentTrack = "";
        currentArtist = "";
        currentAlbum = "";
        currentTrackUri = "";
        currentAlbumArtUrl = "";
        durationMs = 0;
        lastPositionMs = 0;
        saveStateToPrefs();
        notifyPlaybackChanged();
    }

    public String getCurrentTrack() {
        return currentTrack;
    }

    public String getCurrentArtist() {
        return currentArtist;
    }

    public String getCurrentAlbum() {
        return currentAlbum;
    }

    public String getCurrentTrackUri() {
        return currentTrackUri;
    }

    public String getCurrentAlbumArtUrl() {
        return currentAlbumArtUrl;
    }

    public static String extractTrackId(String uri) {
        if (TextUtils.isEmpty(uri)) return null;
        if (uri.startsWith("spotify:track:")) {
            return uri.substring("spotify:track:".length());
        }
        if (uri.contains("open.spotify.com/track/")) {
            String sub = uri.substring(uri.indexOf("open.spotify.com/track/") + "open.spotify.com/track/".length());
            if (sub.contains("?")) sub = sub.substring(0, sub.indexOf("?"));
            if (sub.contains("/")) sub = sub.substring(0, sub.indexOf("/"));
            return sub;
        }
        return null;
    }

    public void fetchAlbumArt(String trackUri, String artist, String track) {
        if (TextUtils.isEmpty(trackUri) && (TextUtils.isEmpty(artist) || TextUtils.isEmpty(track))) return;

        final String cacheKey = (!TextUtils.isEmpty(trackUri) ? trackUri : (artist + ":" + track)).toLowerCase(Locale.ROOT);
        String cached = artCache.get(cacheKey);
        if (!TextUtils.isEmpty(cached)) {
            currentAlbumArtUrl = cached;
            saveStateToPrefs();
            notifyTrackChanged();
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            String artUrl = null;
            String trackId = extractTrackId(trackUri);
            if (!TextUtils.isEmpty(trackId)) {
                artUrl = fetchSpotifyOEmbedThumbnail(trackId);
            }

            if (TextUtils.isEmpty(artUrl) && !TextUtils.isEmpty(track)) {
                artUrl = fetchItunesArtwork(artist, track);
            }

            if (!TextUtils.isEmpty(artUrl)) {
                artCache.put(cacheKey, artUrl);
                final String finalUrl = artUrl;
                AndroidUtilities.runOnUIThread(() -> {
                    currentAlbumArtUrl = finalUrl;
                    saveStateToPrefs();
                    notifyTrackChanged();
                    app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
                });
            }
        });
    }

    private String fetchSpotifyOEmbedThumbnail(String trackId) {
        HttpURLConnection conn = null;
        try {
            URL u = new URL("https://open.spotify.com/oembed?url=https://open.spotify.com/track/" + trackId);
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "Miogram/1.0 (Android)");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = reader.readLine()) != null) sb.append(l);
                reader.close();
                JSONObject obj = new JSONObject(sb.toString());
                return obj.optString("thumbnail_url", null);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private String fetchItunesArtwork(String artist, String track) {
        HttpURLConnection conn = null;
        try {
            String query = (artist != null && !artist.isEmpty() ? artist + " " : "") + track;
            URL u = new URL("https://itunes.apple.com/search?term=" + URLEncoder.encode(query, "UTF-8") + "&entity=song&limit=1");
            conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestProperty("User-Agent", "Miogram/1.0 (Android)");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = reader.readLine()) != null) sb.append(l);
                reader.close();
                JSONObject obj = new JSONObject(sb.toString());
                JSONArray results = obj.optJSONArray("results");
                if (results != null && results.length() > 0) {
                    JSONObject first = results.getJSONObject(0);
                    String art = first.optString("artworkUrl100", null);
                    if (art != null) {
                        return art.replace("100x100bb", "600x600bb");
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    public String getTrackWebUrl() {
        if (TextUtils.isEmpty(currentTrackUri)) return "";
        if (currentTrackUri.startsWith("spotify:track:")) {
            return "https://open.spotify.com/track/" + currentTrackUri.substring("spotify:track:".length());
        }
        return currentTrackUri;
    }

    public long getCurrentPositionMs() {
        if (!isPlaying) return lastPositionMs;
        long elapsed = SystemClock.elapsedRealtime() - lastPositionTimestamp;
        return Math.max(0, lastPositionMs + elapsed);
    }

    public String getCurrentLyricsLine() {
        if (!isPlaying() || TextUtils.isEmpty(currentTrack)) return "";
        MiogramLrcModel.LrcSong song = MiogramLyricsEngine.getInstance().getCachedSongByMeta(currentArtist, currentTrack);
        if (song == null || song.lines.isEmpty()) {
            return currentTrack + " — " + currentArtist;
        }
        long pos = getCurrentPositionMs();
        int idx = song.findLineIndex(pos);
        if (idx >= 0 && idx < song.lines.size()) {
            String line = song.lines.get(idx).text;
            if (!TextUtils.isEmpty(line)) {
                return line;
            }
        }
        return currentTrack + " — " + currentArtist;
    }

    public void openInSpotify(Context context) {
        if (context == null) return;
        try {
            if (!TextUtils.isEmpty(currentTrackUri)) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(currentTrackUri));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } else if (!TextUtils.isEmpty(currentTrack)) {
                openSpotifySearch(context, (currentArtist != null && !currentArtist.isEmpty()
                        ? currentArtist + " " : "") + currentTrack);
            } else {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://open.spotify.com"));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    /**
     * Always works, no API keys: opens the Spotify app (or web fallback)
     * with a search for any "artist — title" query. Used when the legacy
     * playback broadcasts are unavailable on modern Spotify versions.
     */
    public static void openSpotifySearch(Context context, String query) {
        if (context == null || TextUtils.isEmpty(query)) return;
        try {
            String encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8");
            Intent intent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://open.spotify.com/search/" + encoded));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                intent.setPackage("com.spotify.music");
                context.startActivity(intent);
            } catch (Throwable appMissing) {
                intent.setPackage(null);
                context.startActivity(intent);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public static final String PREF_AUTO_TRANSFER = "spotify_auto_transfer_v1";
    private String lastAutoTransferredUri = "";
    private long lastAutoTransferTime = 0;

    public boolean isAutoTransferEnabled() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return false;
        return ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .getBoolean(PREF_AUTO_TRANSFER, false);
    }

    public void setAutoTransferEnabled(boolean enabled) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;
        ctx.getSharedPreferences("miogram_spotify", Context.MODE_PRIVATE)
                .edit().putBoolean(PREF_AUTO_TRANSFER, enabled).apply();
    }

    public void checkAutoTransfer(Context context, int currentAccount) {
        if (!isAutoTransferEnabled()) return;
        if (!isPlaying() || TextUtils.isEmpty(currentTrack)) return;

        // Debounce: don't re-trigger if already transferred this track in the last 45 seconds
        if (currentTrackUri.equals(lastAutoTransferredUri) && (SystemClock.elapsedRealtime() - lastAutoTransferTime < 45000)) {
            return;
        }

        // If Telegram's native player is already playing something actively, don't interrupt
        org.telegram.messenger.MessageObject playingMsg = org.telegram.messenger.MediaController.getInstance().getPlayingMessageObject();
        if (playingMsg != null && !org.telegram.messenger.MediaController.getInstance().isMessagePaused()) {
            return;
        }

        lastAutoTransferredUri = currentTrackUri;
        lastAutoTransferTime = SystemClock.elapsedRealtime();

        final String trackToPlay = currentTrack;
        final String artistToPlay = currentArtist;
        final String query = (artistToPlay != null && !artistToPlay.isEmpty() ? artistToPlay + " " : "") + trackToPlay;

        // Asynchronously search Telegram cloud / music engines
        app.miogram.bridge.music.MiogramMusicSearchEngine.searchAll(query, currentAccount, new app.miogram.bridge.music.MiogramMusicSearchEngine.SearchCallback() {
            @Override
            public void onResults(java.util.List<app.miogram.bridge.music.MiogramMusicTrack> tracks, boolean isFinal) {
                if (tracks != null && !tracks.isEmpty()) {
                    for (app.miogram.bridge.music.MiogramMusicTrack t : tracks) {
                        if (t != null && t.telegramMessage != null) {
                            AndroidUtilities.runOnUIThread(() -> {
                                org.telegram.messenger.MessageObject currentPlaying = org.telegram.messenger.MediaController.getInstance().getPlayingMessageObject();
                                if (currentPlaying == null || org.telegram.messenger.MediaController.getInstance().isMessagePaused()) {
                                    org.telegram.messenger.MediaController.getInstance().playMessage(t.telegramMessage);
                                    showAutoSyncBulletin(trackToPlay, artistToPlay);
                                }
                            });
                            return;
                        }
                    }
                }
            }

            @Override
            public void onError(String error) {
            }
        });
    }

    private void showAutoSyncBulletin(String track, String artist) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                org.telegram.ui.Components.BulletinFactory.global().createSimpleBulletin(
                        org.telegram.messenger.R.raw.saved_messages,
                        app.miogram.bridge.MiogramLocale.get("Синхронізовано зі Spotify", "Синхронизировано со Spotify", "Synced from Spotify"),
                        track + (TextUtils.isEmpty(artist) ? "" : " — " + artist)
                ).show();
            } catch (Throwable ignore) {}
        });
    }
}
