package app.miogram.bridge.presence;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;

import app.miogram.bridge.github.MiogramGitHubManager;
import app.miogram.bridge.roblox.MiogramRobloxManager;
import app.miogram.bridge.spotify.MiogramSpotifyManager;
import app.miogram.bridge.steam.MiogramSteamManager;

/**
 * Self-side periodic presence engine.
 *
 * The old pipeline pushed presence only on discrete events (link/unlink,
 * track change), so a game launched afterwards never appeared and a stuck
 * game stayed forever. This ticker re-fetches live sources every 90 seconds
 * while the process is alive and pushes to Supabase only when the payload
 * actually changed.
 *
 * Honest limits: when the app process is dead (swiped away + Doze), nothing
 * runs — updates resume on next launch/push wake. Steam community XML itself
 * lags a few minutes server-side, so a freshly launched game appears with
 * a small delay.
 */
public class MiogramPresenceRefresher {

    private static final long INTERVAL_MS = 90_000;
    private static boolean started;
    private static String lastPushedJson = "";

    private static final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            try {
                tick();
            } catch (Throwable t) {
                FileLog.e(t);
            }
            AndroidUtilities.runOnUIThread(ticker, INTERVAL_MS);
        }
    };

    public static synchronized void start() {
        if (started) return;
        started = true;
        // Immediate first pass, then periodic.
        AndroidUtilities.runOnUIThread(() -> {
            try {
                tick();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
        AndroidUtilities.runOnUIThread(ticker, INTERVAL_MS);
    }

    public static void invalidate() {
        lastPushedJson = "";
    }

    private static void tick() {
        long myId;
        try {
            myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignore) {
            return;
        }
        if (myId == 0) return;

        boolean needSteam = false;
        boolean needGithub = false;
        boolean needRoblox = false;
        try {
            needSteam = MiogramSteamManager.getInstance().isLinked();
            needGithub = MiogramGitHubManager.getInstance().isLinked();
            needRoblox = MiogramRobloxManager.getInstance().isLinked();
        } catch (Throwable ignore) {}

        // Spotify broadcasts stop if the runtime receiver was never armed in
        // this process (e.g. after cold start) — re-arm every tick, it's cheap.
        try {
            MiogramSpotifyManager.getInstance().ensureRegistered(ApplicationLoader.applicationContext);
        } catch (Throwable ignore) {}

        if (needSteam) {
            try {
                final long fId = myId;
                String sid = MiogramSteamManager.getInstance().getLinkedSteamId();
                MiogramSteamManager.getInstance().resolvePublicSteam(sid, p -> {
                    if (p != null) {
                        MiogramSteamManager.getInstance().syncSelfToCloud(fId, p, MiogramPresenceRefresher::pushIfChanged);
                    } else {
                        pushIfChanged();
                    }
                });
            } catch (Throwable ignore) {
                pushIfChanged();
            }
        }
        if (needGithub) {
            try {
                MiogramGitHubManager.getInstance().fetchUser(true, u -> pushIfChanged());
            } catch (Throwable ignore) {}
        }
        if (needRoblox) {
            try {
                MiogramRobloxManager.getInstance().refreshSelf(p -> pushIfChanged());
            } catch (Throwable ignore) {}
        }
        if (!needSteam && !needGithub && !needRoblox) {
            // Spotify-only snapshot (local state, no network fetch needed).
            pushIfChanged();
        }
    }

    /**
     * Builds the self snapshot and pushes it to Supabase only when the
     * payload actually changed (quota + spam guard, also the retry path).
     */
    public static void pushIfChanged() {
        try {
            long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            if (myId == 0) return;
            MiogramCloudPresence p = MiogramCloudPresence.buildSelfPresence(myId);
            String json = p.toJson().toString();
            if (json.equals(lastPushedJson)) return;
            lastPushedJson = json;
            app.miogram.bridge.badge.MiogramSupabaseBridge.syncPresenceToCloud(myId, p, null);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }
}
