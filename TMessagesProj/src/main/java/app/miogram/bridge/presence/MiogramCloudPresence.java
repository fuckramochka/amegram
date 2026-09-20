package app.miogram.bridge.presence;

import android.text.TextUtils;
import android.util.LongSparseArray;

import org.json.JSONObject;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import app.miogram.bridge.badge.MiogramSupabaseBridge;
import app.miogram.bridge.discord.MiogramDiscordManager;
import app.miogram.bridge.github.MiogramGitHubManager;
import app.miogram.bridge.roblox.MiogramRobloxManager;
import app.miogram.bridge.spotify.MiogramSpotifyManager;
import app.miogram.bridge.steam.MiogramSteamManager;

/**
 * Unified Cloud Presence model and caching engine for Miogram users.
 * Synchronizes connected platforms (Steam, GitHub, Discord, Spotify, Roblox)
 * through the Supabase miogram_badges table using client_version payload.
 */
public class MiogramCloudPresence {

    public static final String PRESENCE_TAG = "#PRESENCE#";

    public long userId;
    public String steamId = "";
    public String steamName = "";
    public String steamAvatar = "";
    public String steamGame = "";
    public String steamGameId = "";
    public String githubUser = "";
    public String discordId = "";
    public String spotifyUser = "";
    public String spotifyTrack = "";
    public String spotifyArtist = "";
    public String spotifyAlbum = "";
    public String spotifyArtwork = "";
    public boolean spotifyPlaying = false;
    public String robloxUser = "";
    public String robloxId = "";
    public String robloxGame = "";
    public String robloxUniverse = "";
    public int robloxState = -1;
    public String playerPreset = "";
    public long lastUpdated = 0;

    private static final LongSparseArray<MiogramCloudPresence> presenceCache = new LongSparseArray<>();

    public MiogramCloudPresence() {
    }

    public MiogramCloudPresence(long userId) {
        this.userId = userId;
    }

    public boolean isAnyLinked() {
        return !TextUtils.isEmpty(steamId) ||
                !TextUtils.isEmpty(githubUser) ||
                !TextUtils.isEmpty(discordId) ||
                !TextUtils.isEmpty(spotifyUser) ||
                !TextUtils.isEmpty(spotifyTrack) ||
                !TextUtils.isEmpty(robloxUser) ||
                !TextUtils.isEmpty(robloxId) ||
                (!TextUtils.isEmpty(playerPreset) && !app.miogram.bridge.player.MiogramPlayerPrefs.PRESET_DEFAULT.equals(playerPreset));
    }

    public static void putPresence(long userId, MiogramCloudPresence presence) {
        if (userId == 0 || presence == null) return;
        synchronized (presenceCache) {
            presenceCache.put(userId, presence);
        }
    }

    public static MiogramCloudPresence getPresence(long userId) {
        if (userId == 0) return null;
        synchronized (presenceCache) {
            return presenceCache.get(userId);
        }
    }

    public JSONObject toJson() {
        JSONObject obj = new JSONObject();
        try {
            if (!TextUtils.isEmpty(steamId)) obj.put("s", steamId);
            if (!TextUtils.isEmpty(steamName)) obj.put("sn", steamName);
            if (!TextUtils.isEmpty(steamAvatar)) obj.put("sa", steamAvatar);
            if (!TextUtils.isEmpty(steamGame)) obj.put("sg", steamGame);
            if (!TextUtils.isEmpty(steamGameId)) obj.put("sgi", steamGameId);
            if (!TextUtils.isEmpty(githubUser)) obj.put("g", githubUser);
            if (!TextUtils.isEmpty(discordId)) obj.put("d", discordId);
            if (!TextUtils.isEmpty(spotifyUser)) obj.put("sp", spotifyUser);
            if (!TextUtils.isEmpty(spotifyTrack)) obj.put("spt", spotifyTrack);
            if (!TextUtils.isEmpty(spotifyArtist)) obj.put("spa", spotifyArtist);
            if (!TextUtils.isEmpty(spotifyAlbum)) obj.put("spb", spotifyAlbum);
            if (!TextUtils.isEmpty(spotifyArtwork)) obj.put("spw", spotifyArtwork);
            if (spotifyPlaying) obj.put("spp", true);
            if (!TextUtils.isEmpty(robloxUser)) obj.put("rbu", robloxUser);
            if (!TextUtils.isEmpty(robloxId)) obj.put("rbi", robloxId);
            if (!TextUtils.isEmpty(robloxGame)) obj.put("rbg", robloxGame);
            if (!TextUtils.isEmpty(robloxUniverse)) obj.put("rbgi", robloxUniverse);
            if (robloxState >= 0) obj.put("rbs", robloxState);
            if (!TextUtils.isEmpty(playerPreset)) obj.put("pp", playerPreset);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return obj;
    }

    public static MiogramCloudPresence fromJson(long userId, JSONObject obj) {
        if (obj == null) return null;
        MiogramCloudPresence p = new MiogramCloudPresence(userId);
        p.steamId = obj.optString("s", obj.optString("steam", ""));
        p.steamName = obj.optString("sn", obj.optString("steam_name", ""));
        p.steamAvatar = obj.optString("sa", obj.optString("steam_avatar", ""));
        p.steamGame = obj.optString("sg", obj.optString("steam_game", ""));
        p.steamGameId = obj.optString("sgi", obj.optString("steam_game_id", ""));
        p.githubUser = obj.optString("g", obj.optString("github", ""));
        p.discordId = obj.optString("d", obj.optString("discord", ""));
        p.spotifyUser = obj.optString("sp", obj.optString("spotify", ""));
        p.spotifyTrack = obj.optString("spt", obj.optString("spotify_track", ""));
        p.spotifyArtist = obj.optString("spa", obj.optString("spotify_artist", ""));
        p.spotifyAlbum = obj.optString("spb", obj.optString("spotify_album", ""));
        p.spotifyArtwork = obj.optString("spw", obj.optString("spotify_artwork", ""));
        p.spotifyPlaying = obj.optBoolean("spp", obj.optBoolean("spotify_playing", false));
        p.robloxUser = obj.optString("rbu", obj.optString("roblox_user", ""));
        p.robloxId = obj.optString("rbi", obj.optString("roblox_id", ""));
        p.robloxGame = obj.optString("rbg", obj.optString("roblox_game", ""));
        p.robloxUniverse = obj.optString("rbgi", obj.optString("roblox_universe", ""));
        p.robloxState = obj.optInt("rbs", obj.optInt("roblox_state", -1));
        p.playerPreset = obj.optString("pp", "");
        p.lastUpdated = System.currentTimeMillis();
        return p;
    }

    public static MiogramCloudPresence extractPresence(long userId, String clientVersion) {
        if (TextUtils.isEmpty(clientVersion) || !clientVersion.contains(PRESENCE_TAG)) {
            return null;
        }
        try {
            int idx = clientVersion.indexOf(PRESENCE_TAG);
            String jsonPart = clientVersion.substring(idx + PRESENCE_TAG.length()).trim();
            if (!TextUtils.isEmpty(jsonPart)) {
                JSONObject obj = new JSONObject(jsonPart);
                return fromJson(userId, obj);
            }
        } catch (Throwable t) {
            FileLog.e("MiogramCloudPresence: parse error", t);
        }
        return null;
    }

    public static String encodeClientVersion(MiogramCloudPresence presence) {
        String base = "Miogram " + BuildVars.BUILD_VERSION_STRING;
        if (presence == null || !presence.isAnyLinked()) {
            return base;
        }
        return base + PRESENCE_TAG + presence.toJson().toString();
    }

    /**
     * Builds presence snapshot for the current self account based on local managers.
     */
    public static MiogramCloudPresence buildSelfPresence(long userId) {
        MiogramCloudPresence p = new MiogramCloudPresence(userId);

        if (MiogramSteamManager.getInstance().isActiveFor(userId)) {
            p.steamId = MiogramSteamManager.getInstance().getLinkedSteamId();
            MiogramSteamManager.SteamProfile sp = MiogramSteamManager.getInstance().getSelfProfile();
            if (sp != null) {
                p.steamName = sp.personaName;
                p.steamAvatar = sp.avatarUrl;
                // Broadcast only the LIVE game. Most-played stays card-only,
                // otherwise idle users look like they play their favorite 24/7.
                if (sp.isLiveGame()) {
                    p.steamGame = sp.gameName;
                    p.steamGameId = sp.gameId;
                }
            }
        }

        if (MiogramGitHubManager.getInstance().isActiveFor(userId)) {
            p.githubUser = MiogramGitHubManager.getInstance().getLinkedUsername();
        }

        if (MiogramDiscordManager.getInstance().isActiveFor(userId)) {
            p.discordId = MiogramDiscordManager.getInstance().getLinkedUserId();
        }

        if (MiogramSpotifyManager.getInstance().isActiveFor(userId)) {
            MiogramSpotifyManager sm = MiogramSpotifyManager.getInstance();
            sm.dropStaleTrack();
            p.spotifyUser = sm.getLinkedUsername();
            if (TextUtils.isEmpty(p.spotifyUser) && sm.isBridgeEnabled()) {
                p.spotifyUser = "live";
            }
            if (sm.hasTrack()) {
                p.spotifyTrack = sm.getCurrentTrack();
                p.spotifyArtist = sm.getCurrentArtist();
                p.spotifyAlbum = sm.getCurrentAlbum();
                p.spotifyArtwork = sm.getCurrentAlbumArtUrl();
                p.spotifyPlaying = sm.isPlaying();
            }
        }

        if (MiogramRobloxManager.getInstance().isActiveFor(userId)) {
            MiogramRobloxManager rm = MiogramRobloxManager.getInstance();
            p.robloxUser = rm.getDisplayName();
            p.robloxId = String.valueOf(rm.getLinkedUserId());
            MiogramRobloxManager.RobloxPresence rp = rm.getSelfPresence();
            if (rp != null) {
                p.robloxState = rp.type;
                // Broadcast the game only while actually in-game (same rule as Steam).
                if (rp.isInGame()) {
                    p.robloxGame = rp.gameName;
                    p.robloxUniverse = String.valueOf(rp.universeId);
                }
            }
        }

        p.playerPreset = app.miogram.bridge.player.MiogramPlayerPrefs.getLayoutPreset();
        p.lastUpdated = System.currentTimeMillis();
        putPresence(userId, p);
        return p;
    }

    /**
     * Synchronizes current self presence snapshot to Supabase.
     * Lazily starts the periodic refresher so live sources keep updating.
     */
    public static void syncSelfToCloud(long userId) {
        try {
            MiogramPresenceRefresher.start();
        } catch (Throwable ignore) {}
        if (userId == 0) {
            try {
                userId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            } catch (Throwable ignore) {}
        }
        if (userId == 0) return;

        MiogramCloudPresence presence = buildSelfPresence(userId);
        MiogramSupabaseBridge.syncPresenceToCloud(userId, presence, null);
    }
}
