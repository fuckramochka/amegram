package app.miogram.bridge.steam;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.badge.MiogramSupabaseBridge;

/**
 * Secure Steam Gaming & Profile Bridge for Miogram.
 * Features:
 * - Public Steam Community XML metadata resolver (zero API keys required, zero private data).
 * - Safe Supabase cloud sync for cross-user profile visibility.
 * - Steam URI Deep-linking (steam://run/<appId>, steam://friends/add/<steamId>).
 */
public class MiogramSteamManager {

    private static class CachedProfile {
        final SteamProfile profile;
        final long timestamp;
        CachedProfile(SteamProfile profile) {
            this.profile = profile;
            this.timestamp = SystemClock.elapsedRealtime();
        }
    }

    private final Map<String, CachedProfile> profileQueryCache = new ConcurrentHashMap<>();

    private static volatile MiogramSteamManager instance;

    public static MiogramSteamManager getInstance() {
        if (instance == null) {
            synchronized (MiogramSteamManager.class) {
                if (instance == null) {
                    instance = new MiogramSteamManager();
                }
            }
        }
        return instance;
    }

    public static class SteamProfile {
        public long userId;
        public String steamId = "";
        public String personaName = "";
        public String avatarUrl = "";
        public String profileUrl = "";
        public String gameId = "";
        public String gameName = "";
        public String gameIconUrl = "";
        public String gameHours2Weeks = "";
        public String gameHoursTotal = "";
        public String mostPlayedGame = "";
        public String mostPlayedGameId = "";
        public String mostPlayedHours = "";
        public boolean isInGame = false;
        public String stateMessage = "";
        public long lastUpdated = 0;

        public boolean hasGame() {
            return (isInGame && !TextUtils.isEmpty(gameName)) || !TextUtils.isEmpty(mostPlayedGame);
        }

        /** True only while Steam reports the user inside a game right now. */
        public boolean isLiveGame() {
            return isInGame && !TextUtils.isEmpty(gameName);
        }
    }

    public interface ProfileCallback {
        void onProfileLoaded(SteamProfile profile);
    }

    private static final String PREFS_NAME = "miogram_steam_prefs";
    private static final String KEY_SELF_STEAM_ID = "self_steam_id";
    private static final String KEY_BROADCAST_ENABLED = "self_steam_broadcast_enabled";

    private final LongSparseArray<SteamProfile> profileCache = new LongSparseArray<>();
    private final LongSparseArray<Long> lastFetchTime = new LongSparseArray<>();

    /** Last successful live Steam XML fetch (any profile). 0 = never yet. */
    private volatile long lastLiveOkTime = 0;
    /** Last live fetch failure reason (network/private profile). Null when healthy. */
    private volatile String lastFetchError = null;

    /**
     * Warns when live Steam data is stale: profile closed/private, network
     * blocked, or Steam XML lagging. Null when fresh.
     */
    public String getStaleWarning() {
        if (!isLinked()) return null;
        long ok = lastLiveOkTime;
        if (ok == 0) return null;
        if (SystemClock.elapsedRealtime() - ok < 30 * 60 * 1000L) return null;
        String reason = lastFetchError;
        return MiogramLocale.get("Steam не оновлюється >30 хв", "Steam не обновляется >30 мин", "Steam stale >30 min")
                + (reason != null ? " (" + reason + ")" : "");
    }

    private MiogramSteamManager() {
        // Load self profile from cache if present
        loadSelfFromCache();
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isBroadcastEnabled() {
        return getPrefs().getBoolean(KEY_BROADCAST_ENABLED, true);
    }

    public void setBroadcastEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_BROADCAST_ENABLED, enabled).apply();
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public String getLinkedSteamId() {
        return getPrefs().getString(KEY_SELF_STEAM_ID, "");
    }

    public String getSelfSteamId() {
        return getLinkedSteamId();
    }

    public SteamProfile getSelfProfile() {
        long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        synchronized (profileCache) {
            SteamProfile p = profileCache.get(myId);
            if (p != null) return p;
        }
        loadSelfFromCache();
        synchronized (profileCache) {
            return profileCache.get(myId);
        }
    }

    public void setLinkedSteamId(String steamId) {
        String clean = steamId != null ? steamId.trim() : "";
        SharedPreferences prefs = getPrefs();
        prefs.edit().putString(KEY_SELF_STEAM_ID, clean).apply();
        if (TextUtils.isEmpty(clean)) {
            clearSelfCache();
            app.miogram.bridge.presence.MiogramLinkOwner.clear(prefs);
            long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (myId != 0) {
                synchronized (profileCache) {
                    profileCache.remove(myId);
                }
            }
        } else {
            app.miogram.bridge.presence.MiogramLinkOwner.stamp(prefs);
        }
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public boolean isLinked() {
        return !TextUtils.isEmpty(getLinkedSteamId());
    }

    /** Account-scoped visibility: the link shows only on the account that created it (legacy: everywhere). */
    public boolean isActiveFor(long tgUserId) {
        return isLinked() && app.miogram.bridge.presence.MiogramLinkOwner.visibleFor(getPrefs(), tgUserId);
    }

    /**
     * Resolves public Steam Community profile using the official XML feed.
     * Works for custom vanity URLs, friend IDs, vanity URLs with/without https, and SteamID64.
     */
    public void resolvePublicSteam(String query, ProfileCallback callback) {
        if (TextUtils.isEmpty(query)) {
            if (callback != null) callback.onProfileLoaded(null);
            return;
        }

        String q = query.trim();
        if (q.startsWith("https://")) q = q.substring(8);
        else if (q.startsWith("http://")) q = q.substring(7);

        boolean explicitProfiles = false;
        boolean explicitId = false;

        if (q.contains("steamcommunity.com/profiles/")) {
            explicitProfiles = true;
            q = q.substring(q.indexOf("steamcommunity.com/profiles/") + "steamcommunity.com/profiles/".length());
        } else if (q.contains("steamcommunity.com/id/")) {
            explicitId = true;
            q = q.substring(q.indexOf("steamcommunity.com/id/") + "steamcommunity.com/id/".length());
        }

        if (q.contains("?")) q = q.substring(0, q.indexOf("?"));
        if (q.contains("#")) q = q.substring(0, q.indexOf("#"));
        if (q.contains("/")) q = q.substring(0, q.indexOf("/"));
        final String clean = q.trim();

        if (TextUtils.isEmpty(clean)) {
            if (callback != null) callback.onProfileLoaded(null);
            return;
        }

        // Fast In-Memory Cache Lookup for instant 0ms rendering
        CachedProfile cached = profileQueryCache.get(clean);
        if (cached != null && cached.profile != null) {
            if (callback != null) callback.onProfileLoaded(cached.profile);
            if (SystemClock.elapsedRealtime() - cached.timestamp < 120_000) {
                return;
            }
        }

        // Friend Code (7 to 10 digits) -> convert to SteamID64
        String resolvedId64 = null;
        if (!explicitId && clean.matches("\\d{7,10}")) {
            try {
                long friendCode = Long.parseLong(clean);
                resolvedId64 = String.valueOf(76561197960265728L + friendCode);
            } catch (Throwable ignore) {}
        } else if (clean.matches("\\d{17}")) {
            resolvedId64 = clean;
        }

        if (resolvedId64 != null && cached == null) {
            CachedProfile cachedById = profileQueryCache.get(resolvedId64);
            if (cachedById != null && cachedById.profile != null) {
                if (callback != null) callback.onProfileLoaded(cachedById.profile);
                if (SystemClock.elapsedRealtime() - cachedById.timestamp < 120_000) {
                    return;
                }
            }
        }

        final String primaryUrl;
        final String secondaryUrl;

        if (resolvedId64 != null || explicitProfiles) {
            String targetId = resolvedId64 != null ? resolvedId64 : clean;
            primaryUrl = "https://steamcommunity.com/profiles/" + targetId + "/?xml=1";
            secondaryUrl = null;
        } else {
            primaryUrl = "https://steamcommunity.com/id/" + clean + "/?xml=1";
            secondaryUrl = "https://steamcommunity.com/profiles/" + clean + "/?xml=1";
        }

        final String finalResolvedId = resolvedId64;
        Utilities.globalQueue.postRunnable(() -> {
            SteamProfile profile = fetchSteamXmlWithRedirects(primaryUrl);
            if (profile == null && secondaryUrl != null) {
                profile = fetchSteamXmlWithRedirects(secondaryUrl);
            }

            final SteamProfile result = profile;
            if (result != null) {
                lastLiveOkTime = SystemClock.elapsedRealtime();
                lastFetchError = null;
                profileQueryCache.put(clean, new CachedProfile(result));
                if (!TextUtils.isEmpty(result.steamId)) {
                    profileQueryCache.put(result.steamId, new CachedProfile(result));
                }
                if (finalResolvedId != null) {
                    profileQueryCache.put(finalResolvedId, new CachedProfile(result));
                }
            } else {
                lastFetchError = MiogramLocale.get("профіль закритий або мережа", "профиль закрыт или сеть", "private profile or network");
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onProfileLoaded(result);
            });
        });
    }

    private SteamProfile fetchSteamXmlWithRedirects(String urlStr) {
        String currentUrl = urlStr;
        for (int redirects = 0; redirects < 4; redirects++) {
            HttpURLConnection conn = null;
            try {
                URL u = new URL(currentUrl);
                conn = (HttpURLConnection) u.openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(3500);
                conn.setReadTimeout(4000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept", "text/xml,application/xml,*/*");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("Accept-Encoding", "gzip, deflate");

                int code = conn.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = conn.getHeaderField("Location");
                    if (TextUtils.isEmpty(loc)) break;
                    if (!loc.contains("?xml=1") && !loc.contains("&xml=1")) {
                        loc += (loc.contains("?") ? "&" : "?") + "xml=1";
                    }
                    currentUrl = loc;
                    continue;
                }

                if (code == 200) {
                    InputStream inStream = conn.getInputStream();
                    String enc = conn.getHeaderField("Content-Encoding");
                    if (enc != null && enc.toLowerCase().contains("gzip")) {
                        inStream = new java.util.zip.GZIPInputStream(inStream);
                    }
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inStream, StandardCharsets.UTF_8));
                    StringBuilder xml = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xml.append(line).append("\n");
                    }
                    reader.close();

                    return parseSteamXml(xml.toString());
                }
                break;
            } catch (Throwable t) {
                FileLog.e("MiogramSteamManager: fetch error for " + currentUrl, t);
                break;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }

    private SteamProfile parseSteamXml(String xml) {
        if (TextUtils.isEmpty(xml) || !xml.contains("<profile>") || xml.contains("<error>")) return null;

        SteamProfile p = new SteamProfile();
        p.steamId = extractTag(xml, "steamID64");
        p.personaName = extractTag(xml, "steamID");
        p.avatarUrl = extractTag(xml, "avatarMedium");
        if (TextUtils.isEmpty(p.avatarUrl)) p.avatarUrl = extractTag(xml, "avatarFull");
        if (TextUtils.isEmpty(p.avatarUrl)) p.avatarUrl = extractTag(xml, "avatarIcon");
        p.stateMessage = extractTag(xml, "stateMessage");
        String hours = extractTag(xml, "hoursPlayed2Wk");
        if (!TextUtils.isEmpty(hours) && !"0.0".equals(hours) && !"0".equals(hours)) {
            p.gameHours2Weeks = hours;
        }

        String onlineState = extractTag(xml, "onlineState");

        // In-game info block
        if (xml.contains("<inGameInfo>")) {
            p.isInGame = true;
            p.gameName = extractTag(xml, "gameName");
            p.gameIconUrl = extractTag(xml, "gameIcon");
            String gameLink = extractTag(xml, "gameLink");
            if (!TextUtils.isEmpty(gameLink)) {
                Matcher m = Pattern.compile("app/(\\d+)").matcher(gameLink);
                if (m.find()) {
                    p.gameId = m.group(1);
                }
            }
        } else if ("in-game".equalsIgnoreCase(onlineState) || (p.stateMessage != null && p.stateMessage.contains("In-Game"))) {
            p.isInGame = true;
            if (p.stateMessage != null && p.stateMessage.contains("<br/>")) {
                p.gameName = p.stateMessage.substring(p.stateMessage.indexOf("<br/>") + 5).replace("<![CDATA[", "").replace("]]>", "").trim();
            }
        } else {
            p.isInGame = false;
        }

        // If not actively in a match, check most played games to display favorite game
        if (!p.isInGame && xml.contains("<mostPlayedGame>")) {
            int start = xml.indexOf("<mostPlayedGame>");
            int end = xml.indexOf("</mostPlayedGame>", start);
            if (start != -1 && end != -1) {
                String sub = xml.substring(start, end);
                p.mostPlayedGame = extractTag(sub, "gameName");
                p.mostPlayedHours = extractTag(sub, "hoursOnRecord");
                String gLink = extractTag(sub, "gameLink");
                if (!TextUtils.isEmpty(gLink)) {
                    Matcher m = Pattern.compile("app/(\\d+)").matcher(gLink);
                    if (m.find()) {
                        p.mostPlayedGameId = m.group(1);
                    }
                }
                // NOTE: most-played stays in mostPlayedGame* only. gameName/gameId
                // always describe the LIVE session, otherwise idle users broadcast
                // their favorite as "currently playing".
            }
        }

        String customUrl = extractTag(xml, "customURL");
        if (!TextUtils.isEmpty(customUrl)) {
            p.profileUrl = "https://steamcommunity.com/id/" + customUrl;
        } else if (!TextUtils.isEmpty(p.steamId)) {
            p.profileUrl = "https://steamcommunity.com/profiles/" + p.steamId;
        }
        p.lastUpdated = System.currentTimeMillis();
        return p;
    }

    private String extractTag(String xml, String tag) {
        Pattern pattern = Pattern.compile("<" + tag + ">(?:<\\!\\[CDATA\\[)?(.*?)(?:\\]\\]>)?<\\/" + tag + ">", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(xml);
        if (matcher.find()) {
            String val = matcher.group(1).trim();
            return val.replace("&amp;", "&")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                    .replace("&#39;", "'");
        }
        return "";
    }

    /**
     * Publishes self profile to Supabase.
     */
    public void syncSelfToCloud(long userId, SteamProfile profile, Runnable onDone) {
        if (userId == 0 || profile == null || !isBroadcastEnabled()) {
            if (onDone != null) onDone.run();
            return;
        }

        profile.userId = userId;
        synchronized (profileCache) {
            profileCache.put(userId, profile);
        }

        // Persist locally
        saveSelfToCache(profile);

        // Sync via unified Cloud Presence to Supabase (change-guarded inside).
        app.miogram.bridge.presence.MiogramPresenceRefresher.pushIfChanged();

        if (onDone != null) {
            AndroidUtilities.runOnUIThread(onDone);
        }
    }

    /**
     * Retrieves steam profile for any user from Supabase and live Steam XML.
     */
    public void getProfile(long userId, ProfileCallback callback) {
        if (userId == 0) {
            if (callback != null) callback.onProfileLoaded(null);
            return;
        }

        // Return memory cached profile immediately if recent
        synchronized (profileCache) {
            SteamProfile cached = profileCache.get(userId);
            Long lastFetch = lastFetchTime.get(userId);
            long now = SystemClock.elapsedRealtime();
            if (cached != null && lastFetch != null && (now - lastFetch < 60000)) {
                if (callback != null) callback.onProfileLoaded(cached);
                return;
            }
        }

        // Fetch user's Steam ID from Supabase presence and resolve live Steam details
        app.miogram.bridge.badge.MiogramSupabaseBridge.fetchUserPresence(userId, cloud -> {
            if (cloud != null && !TextUtils.isEmpty(cloud.steamId)) {
                resolvePublicSteam(cloud.steamId, p -> {
                    if (p != null) {
                        synchronized (profileCache) {
                            profileCache.put(userId, p);
                            lastFetchTime.put(userId, SystemClock.elapsedRealtime());
                        }
                    }
                    if (callback != null) callback.onProfileLoaded(p);
                });
            } else {
                if (callback != null) callback.onProfileLoaded(null);
            }
        });
    }

    public void openGame(Context context, String gameId) {
        if (context == null || TextUtils.isEmpty(gameId)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("steam://run/" + gameId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            // Web fallback
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://store.steampowered.com/app/" + gameId));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Throwable ignore) {}
        }
    }

    public void addFriend(Context context, String steamId) {
        if (context == null || TextUtils.isEmpty(steamId)) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("steam://friends/add/" + steamId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            // Web fallback
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://steamcommunity.com/profiles/" + steamId));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Throwable ignore) {}
        }
    }

    public void openProfile(Context context, String profileUrl, String steamId) {
        if (context == null) return;
        String url = !TextUtils.isEmpty(profileUrl) ? profileUrl : ("https://steamcommunity.com/profiles/" + steamId);
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("steam://url/SteamIDPage/" + steamId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(webIntent);
            } catch (Throwable ignore) {}
        }
    }

    private void clearSelfCache() {
        SharedPreferences.Editor ed = getPrefs().edit();
        ed.remove("self_steam_id");
        ed.remove("self_persona_name");
        ed.remove("self_avatar_url");
        ed.remove("self_game_id");
        ed.remove("self_game_name");
        ed.remove("self_game_icon");
        ed.remove("self_game_hours");
        ed.remove("self_is_in_game");
        ed.apply();
    }

    private void saveSelfToCache(SteamProfile p) {
        if (p == null) return;
        SharedPreferences.Editor ed = getPrefs().edit();
        ed.putString("self_steam_id", p.steamId);
        ed.putString("self_persona_name", p.personaName);
        ed.putString("self_avatar_url", p.avatarUrl);
        ed.putString("self_game_id", p.gameId);
        ed.putString("self_game_name", p.gameName);
        ed.putString("self_game_icon", p.gameIconUrl);
        ed.putString("self_game_hours", p.gameHours2Weeks);
        ed.putBoolean("self_is_in_game", p.isInGame);
        ed.putLong("self_game_updated", System.currentTimeMillis());
        ed.apply();
    }

    private void loadSelfFromCache() {
        SharedPreferences p = getPrefs();
        String steamId = p.getString("self_steam_id", "");
        if (TextUtils.isEmpty(steamId)) return;

        SteamProfile self = new SteamProfile();
        self.steamId = steamId;
        self.personaName = p.getString("self_persona_name", "");
        self.avatarUrl = p.getString("self_avatar_url", "");
        long lastUpdated = p.getLong("self_game_updated", 0);
        boolean inGame = p.getBoolean("self_is_in_game", false);
        // Expire cached in-game status after 15 minutes
        if (inGame && (System.currentTimeMillis() - lastUpdated > 15 * 60 * 1000L)) {
            inGame = false;
        }
        self.isInGame = inGame;
        if (inGame) {
            self.gameId = p.getString("self_game_id", "");
            self.gameName = p.getString("self_game_name", "");
            self.gameIconUrl = p.getString("self_game_icon", "");
            self.gameHours2Weeks = p.getString("self_game_hours", "");
        }

        long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (myId != 0) {
            synchronized (profileCache) {
                profileCache.put(myId, self);
            }
        }
    }
}
