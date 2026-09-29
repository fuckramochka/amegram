package app.miogram.bridge.updater;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ui.MiogramUpdateBottomSheet;

/**
 * Intelligent, battery-friendly and non-intrusive Updater for Amegram:
 * - Checks GitHub releases safely without burning API rate limits
 * - Strict throttling (at most once every 12-24h for background checks)
 * - Honors 24-hour snooze when dismissed ("Later")
 * - User toggles: Auto-check (on/off), Wi-Fi only, Channel (Beta/Stable)
 * - Accurate semantic version & versionCode comparison
 */
public class MiogramUpdater {

    public static final String[] GITHUB_REPOS = new String[]{
            "fuckramochka/amegram"
    };
    public static final String CHANNEL_USERNAME = "dkamegram";
    public static final String FALLBACK_CHANNEL_USERNAME = "dkmiogram";

    private static final String PREFS_NAME = "miogram_updater_prefs";
    private static final String KEY_LAST_SEEN_TAG = "last_seen_tag";
    private static final String KEY_LAST_SEEN_TIME = "last_seen_time";
    private static final String KEY_AUTO_CHECK = "auto_check_enabled";
    private static final String KEY_WIFI_ONLY = "update_wifi_only";
    private static final String KEY_UPDATE_CHANNEL = "update_channel"; // "beta" (default) | "stable"
    private static final String KEY_LAST_CHECK_TIME = "last_check_timestamp";
    private static final String KEY_LAST_ENTRY_CHECK = "last_entry_check_timestamp";

    /** Dismissed update re-prompts after 24 hours instead of immediately. */
    public static final long DISMISS_SNOOZE_MS = 24L * 60 * 60 * 1000L;
    /** Background launch check cooldown (12 hours). */
    private static final long CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000L;
    /** On-resume entry check cooldown (12 hours). */
    private static final long ENTRY_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000L;

    public static final String CHANNEL_BETA = "beta";
    public static final String CHANNEL_STABLE = "stable";

    private static final String KEY_CHANNEL_CHOICE_VERSION = "channel_choice_version";
    private static final String KEY_PROMO_VERSION = "channel_promo_version";
    private static final String KEY_CHANNEL_CHOSEN_ONCE = "channel_chosen_once";
    private static final String KEY_PROMO_DONE_ONCE = "promo_done_once";

    private static volatile boolean autoUpdateStarted = false;

    public static boolean isAutoCheckEnabled() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return true;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTO_CHECK, true);
        } catch (Throwable ignore) {
            return true;
        }
    }

    public static void setAutoCheckEnabled(boolean enabled) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_AUTO_CHECK, enabled).apply();
        } catch (Throwable ignore) {}
    }

    public static boolean isWifiOnlyEnabled() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return false;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_WIFI_ONLY, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setWifiOnlyEnabled(boolean wifiOnly) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
        } catch (Throwable ignore) {}
    }

    public static String getUpdateChannel() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return CHANNEL_BETA;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_UPDATE_CHANNEL, CHANNEL_BETA);
        } catch (Throwable ignore) {
            return CHANNEL_BETA;
        }
    }

    public static String getUpdateChannelName() {
        return CHANNEL_STABLE.equals(getUpdateChannel()) ? "Stable" : "Beta";
    }

    public static void setUpdateChannel(String channel) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_UPDATE_CHANNEL, CHANNEL_STABLE.equals(channel) ? CHANNEL_STABLE : CHANNEL_BETA).apply();
        } catch (Throwable ignore) {}
    }

    public static long getLastCheckTimestamp() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return 0L;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_LAST_CHECK_TIME, 0L);
        } catch (Throwable ignore) {
            return 0L;
        }
    }

    public static String getLastCheckTimeFormatted() {
        long t = getLastCheckTimestamp();
        if (t <= 0) {
            return MiogramLocale.get("Ще не перевірялось", "Еще не проверялось", "Never checked");
        }
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
            return sdf.format(new Date(t));
        } catch (Throwable ignore) {
            return "";
        }
    }

    public static void snoozeUpdate(String tag) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_SEEN_TAG, tag != null ? tag : "")
                    .putLong(KEY_LAST_SEEN_TIME, System.currentTimeMillis()).apply();
        } catch (Throwable ignore) {}
    }

    public static boolean isWifiConnected(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return true;
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                    return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            } else {
                NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.getType() == ConnectivityManager.TYPE_WIFI && info.isConnected();
            }
        } catch (Throwable ignored) {}
        return true;
    }

    /**
     * Battery-friendly background startup check (at most once every 12-24 hours).
     */
    public static synchronized void initAutoUpdate(Context context) {
        if (autoUpdateStarted) return;
        autoUpdateStarted = true;

        Utilities.globalQueue.postRunnable(() -> {
            try {
                Context ctx = ApplicationLoader.applicationContext != null ? ApplicationLoader.applicationContext : context;
                if (ctx == null) return;
                MiogramDownloadManager.cleanOldUpdateApks(ctx);

                if (!isAutoCheckEnabled()) return;
                if (isWifiOnlyEnabled() && !isWifiConnected(ctx)) return;

                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L);
                long now = System.currentTimeMillis();
                if (now - lastCheck < CHECK_INTERVAL_MS) {
                    return;
                }
                performBackgroundCheck();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 12000L); // 12s delay so app launch remains ultra smooth
    }

    /**
     * Checks for updates quietly upon app entry/resume (throttled to 12h).
     */
    public static void checkOnEntry(Context context) {
        try {
            Context ctx = ApplicationLoader.applicationContext != null ? ApplicationLoader.applicationContext : context;
            if (ctx == null) return;

            if (!isAutoCheckEnabled()) return;
            if (isWifiOnlyEnabled() && !isWifiConnected(ctx)) return;

            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            long lastEntry = prefs.getLong(KEY_LAST_ENTRY_CHECK, 0L);
            long now = System.currentTimeMillis();
            if (now - lastEntry < ENTRY_CHECK_INTERVAL_MS) {
                return;
            }
            prefs.edit().putLong(KEY_LAST_ENTRY_CHECK, now).apply();
        } catch (Throwable ignore) {}

        Utilities.globalQueue.postRunnable(() -> {
            try {
                performBackgroundCheck();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 3000L);
    }

    private static void performBackgroundCheck() {
        LaunchActivity act = LaunchActivity.instance;
        if (act == null || act.isFinishing()) return;

        BaseFragment fragment = act.getSafeLastFragment();
        if (fragment == null) return;

        fetchLatestRelease((hasUpdate, version, changelog, apkUrl, apkSize) -> {
            try {
                if (ApplicationLoader.applicationContext != null) {
                    ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply();
                }
            } catch (Throwable ignore) {}

            if (hasUpdate) {
                Context ctx = ApplicationLoader.applicationContext;
                if (ctx == null) return;
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String lastSeen = prefs.getString(KEY_LAST_SEEN_TAG, "");
                long lastSeenTime = prefs.getLong(KEY_LAST_SEEN_TIME, 0L);
                boolean dismissed = version != null && version.equalsIgnoreCase(lastSeen);

                // Respect 24-hour snooze! Never disturb user if snoozed recently
                if (dismissed && (System.currentTimeMillis() - lastSeenTime < DISMISS_SNOOZE_MS)) {
                    return;
                }

                new Handler(Looper.getMainLooper()).post(() -> {
                    LaunchActivity currentAct = LaunchActivity.instance;
                    if (currentAct != null && !currentAct.isFinishing()) {
                        BaseFragment currentFrag = currentAct.getSafeLastFragment();
                        if (currentFrag != null) {
                            MiogramUpdateBottomSheet sheet = new MiogramUpdateBottomSheet(currentFrag, true, version, changelog, apkUrl, apkSize);
                            sheet.show();
                        }
                    }
                });
            }
        });
    }

    public static void checkAndShowUpdate(BaseFragment fragment, boolean manualCheck) {
        if (fragment == null || fragment.getParentActivity() == null) return;

        if (manualCheck) {
            Toast.makeText(fragment.getParentActivity(), MiogramLocale.get("Перевірка оновлень Amegram...", "Проверка обновлений Amegram...", "Checking for Amegram updates..."), Toast.LENGTH_SHORT).show();
        } else {
            if (!isAutoCheckEnabled()) return;
            if (isWifiOnlyEnabled() && !isWifiConnected(fragment.getParentActivity())) return;
        }

        fetchLatestRelease((hasUpdate, version, changelog, apkUrl, apkSize) -> {
            try {
                if (ApplicationLoader.applicationContext != null) {
                    ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                            .edit().putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis()).apply();
                }
            } catch (Throwable ignore) {}

            new Handler(Looper.getMainLooper()).post(() -> {
                if (fragment.getParentActivity() == null || fragment.getParentActivity().isFinishing()) {
                    return;
                }
                final String finalVer = (version != null && !version.isEmpty()) ? version : getCurrentAppVersion();
                if (!hasUpdate) {
                    if (manualCheck) {
                        Toast.makeText(fragment.getParentActivity(), MiogramLocale.format("Встановлена остання версія (%s)", "Установлена последняя версия (%s)", "Latest version installed (%s)", finalVer), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }

                // If background check, enforce 24h snooze
                if (!manualCheck) {
                    Context ctx = ApplicationLoader.applicationContext;
                    if (ctx != null) {
                        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                        String lastSeen = prefs.getString(KEY_LAST_SEEN_TAG, "");
                        long lastSeenTime = prefs.getLong(KEY_LAST_SEEN_TIME, 0L);
                        boolean dismissed = version != null && version.equalsIgnoreCase(lastSeen);
                        if (dismissed && (System.currentTimeMillis() - lastSeenTime < DISMISS_SNOOZE_MS)) {
                            return; // Snoozed
                        }
                    }
                }

                MiogramUpdateBottomSheet sheet = new MiogramUpdateBottomSheet(fragment, true, finalVer, changelog, apkUrl, apkSize);
                sheet.show();
            });
        }, manualCheck);
    }

    public interface UpdateCallback {
        void onResult(boolean hasUpdate, String version, String changelog, String apkUrl, long apkSize);
    }

    public static class ReleaseCandidate {
        public final String repo;
        public final String version;
        public final String tag;
        public final String body;
        public final String apkUrl;
        public final long apkSize;

        public ReleaseCandidate(String repo, String version, String tag, String body, String apkUrl, long apkSize) {
            this.repo = repo;
            this.version = version;
            this.tag = tag;
            this.body = body;
            this.apkUrl = apkUrl;
            this.apkSize = apkSize;
        }
    }

    private static void fetchLatestRelease(UpdateCallback callback) {
        fetchLatestRelease(callback, false);
    }

    private static void fetchLatestRelease(UpdateCallback callback, boolean bypassCache) {
        final boolean beta = !CHANNEL_STABLE.equals(getUpdateChannel());
        new Thread(() -> {
            ReleaseCandidate bestCandidate = null;
            for (String repo : GITHUB_REPOS) {
                try {
                    String endpoint = beta
                            ? "https://api.github.com/repos/" + repo + "/releases"
                            : "https://api.github.com/repos/" + repo + "/releases/latest";
                    if (bypassCache) {
                        endpoint += (endpoint.contains("?") ? "&" : "?") + "nocache=" + System.currentTimeMillis();
                    }
                    URL url = new URL(endpoint);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                    conn.setRequestProperty("User-Agent", "Amegram-Android/" + getCurrentAppVersion());
                    conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        JSONObject json;
                        if (beta) {
                            json = pickBetaRelease(new JSONArray(sb.toString()));
                            if (json == null) continue;
                        } else {
                            json = new JSONObject(sb.toString());
                        }

                        String tag = json.optString("tag_name", "");
                        String body = json.optString("body", "");
                        String apkUrl = "";
                        long apkSize = 0L;

                        JSONArray assets = json.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                String name = asset.optString("name", "");
                                if (name.endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", "");
                                    apkSize = asset.optLong("size", 0L);
                                    break;
                                }
                            }
                        }

                        if (!TextUtils.isEmpty(apkUrl) && !TextUtils.isEmpty(tag)) {
                            String ver = tag.replace("v", "").replace("V", "").trim();
                            ReleaseCandidate candidate = new ReleaseCandidate(repo, ver, tag, body, apkUrl, apkSize);
                            if (bestCandidate == null) {
                                bestCandidate = candidate;
                            } else {
                                if (isNewerVersion(bestCandidate.version, candidate.version, candidate.tag, candidate.body)) {
                                    bestCandidate = candidate;
                                }
                            }
                            if ("fuckramochka/amegram".equalsIgnoreCase(repo)) {
                                break;
                            }
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }

            // Fallback 1: GitHub Atom Feed (Instant releases, zero 60-req/hr rate limiting)
            if (bestCandidate == null) {
                try {
                    String atomUrl = "https://github.com/fuckramochka/amegram/releases.atom?t=" + System.currentTimeMillis();
                    URL url = new URL(atomUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    conn.setUseCaches(false);
                    conn.setConnectTimeout(8000);
                    conn.setReadTimeout(8000);
                    if (conn.getResponseCode() == 200) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        reader.close();
                        String content = sb.toString();

                        java.util.regex.Pattern pTag = java.util.regex.Pattern.compile("<link\\s+rel=\"alternate\"\\s+type=\"text/html\"\\s+href=\"https://github\\.com/[^/]+/[^/]+/releases/tag/([^\"]+)\"");
                        java.util.regex.Matcher mTag = pTag.matcher(content);
                        if (mTag.find()) {
                            String tag = mTag.group(1).trim();
                            String ver = tag.replace("v", "").replace("V", "").trim();

                            String body = "Amegram " + tag;
                            java.util.regex.Pattern pTitle = java.util.regex.Pattern.compile("<title>([^<]+)</title>");
                            java.util.regex.Matcher mTitle = pTitle.matcher(content);
                            if (mTitle.find() && mTitle.find()) {
                                body = mTitle.group(1).trim();
                            }

                            String apkUrl = fetchApkUrlFromExpandedAssets(tag);
                            if (TextUtils.isEmpty(apkUrl)) {
                                String runNum = ver.contains(".") ? ver.substring(ver.lastIndexOf('.') + 1) : ver;
                                int calcCode = 0;
                                try { calcCode = 126100 + Integer.parseInt(runNum); } catch (Exception ignored) {}
                                if (calcCode > 0) {
                                    apkUrl = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + "." + calcCode + ".apk";
                                } else {
                                    apkUrl = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + ".apk";
                                }
                            }
                            bestCandidate = new ReleaseCandidate("fuckramochka/amegram", ver, tag, body, apkUrl, 0L);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }

            // Fallback 2: releases/latest redirect inspection
            if (bestCandidate == null) {
                try {
                    URL fallbackUrl = new URL("https://github.com/fuckramochka/amegram/releases/latest");
                    HttpURLConnection fbConn = (HttpURLConnection) fallbackUrl.openConnection();
                    fbConn.setInstanceFollowRedirects(false);
                    fbConn.setRequestMethod("HEAD");
                    fbConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
                    fbConn.setRequestProperty("Cache-Control", "no-cache");
                    fbConn.setUseCaches(false);
                    fbConn.setConnectTimeout(8000);
                    fbConn.setReadTimeout(8000);
                    int resp = fbConn.getResponseCode();
                    if (resp == 301 || resp == 302 || resp == 307 || resp == 308) {
                        String loc = fbConn.getHeaderField("Location");
                        if (loc != null && loc.contains("/tag/")) {
                            String tag = loc.substring(loc.lastIndexOf("/tag/") + 5).trim();
                            String ver = tag.replace("v", "").replace("V", "").trim();
                            String apkUrl = fetchApkUrlFromExpandedAssets(tag);
                            if (TextUtils.isEmpty(apkUrl)) {
                                String runNum = ver.contains(".") ? ver.substring(ver.lastIndexOf('.') + 1) : ver;
                                int calcCode = 0;
                                try { calcCode = 126100 + Integer.parseInt(runNum); } catch (Exception ignored) {}
                                if (calcCode > 0) {
                                    apkUrl = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + "." + calcCode + ".apk";
                                } else {
                                    apkUrl = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + ".apk";
                                }
                            }
                            bestCandidate = new ReleaseCandidate("fuckramochka/amegram", ver, tag, "Останнє оновлення Amegram на GitHub: " + tag, apkUrl, 0L);
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }

            if (bestCandidate != null) {
                final String currentVersion = getCurrentAppVersion();
                boolean isNewer = isNewerVersion(currentVersion, bestCandidate.version, bestCandidate.tag, bestCandidate.body);
                callback.onResult(isNewer, bestCandidate.version, bestCandidate.body, bestCandidate.apkUrl, bestCandidate.apkSize);
            } else {
                callback.onResult(false, getCurrentAppVersion(), null, null, 0L);
            }
        }).start();
    }

    public static String fetchApkUrlFromExpandedAssets(String tag) {
        try {
            URL url = new URL("https://github.com/fuckramochka/amegram/releases/expanded_assets/" + tag + "?t=" + System.currentTimeMillis());
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
            conn.setRequestProperty("Cache-Control", "no-cache");
            conn.setUseCaches(false);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("href=\"(/fuckramochka/amegram/releases/download/[^\"]+?\\.apk)\"");
                while ((line = reader.readLine()) != null) {
                    java.util.regex.Matcher m = p.matcher(line);
                    if (m.find()) {
                        reader.close();
                        return "https://github.com" + m.group(1);
                    }
                }
                reader.close();
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private static JSONObject pickBetaRelease(JSONArray releases) {
        if (releases == null) return null;
        for (int i = 0; i < releases.length(); i++) {
            try {
                JSONObject r = releases.getJSONObject(i);
                if (r.optBoolean("draft", false)) continue;
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int j = 0; j < assets.length(); j++) {
                    if (assets.getJSONObject(j).optString("name", "").endsWith(".apk")) {
                        return r;
                    }
                }
            } catch (Throwable ignore) {}
        }
        return null;
    }

    public static String getCurrentAppVersion() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : BuildVars.BUILD_VERSION_STRING;
        } catch (Exception e) {
            return BuildVars.BUILD_VERSION_STRING != null ? BuildVars.BUILD_VERSION_STRING : "12.11.0";
        }
    }

    public static int getCurrentAppVersionCode() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pInfo.versionCode;
        } catch (Exception e) {
            return 1261;
        }
    }

    public static boolean isNewerVersion(String currentVersion, String remoteVersion, String remoteTag, String changelog) {
        if (TextUtils.isEmpty(remoteVersion)) return false;

        String c = currentVersion != null ? currentVersion.replace("v", "").replace("V", "").trim() : "";
        String r = remoteVersion.replace("v", "").replace("V", "").trim();

        if (c.equalsIgnoreCase(r)) return false;

        // Separate base numbers from build/commit metadata
        String cBase = c;
        int cParen = cBase.indexOf('(');
        if (cParen > 0) cBase = cBase.substring(0, cParen).trim();
        int cDash = cBase.indexOf('-');
        if (cDash > 0) cBase = cBase.substring(0, cDash).trim();

        String rBase = r;
        int rParen = rBase.indexOf('(');
        if (rParen > 0) rBase = rBase.substring(0, rParen).trim();
        int rDash = rBase.indexOf('-');
        if (rDash > 0) rBase = rBase.substring(0, rDash).trim();

        String[] cParts = cBase.split("[^0-9]+");
        String[] rParts = rBase.split("[^0-9]+");

        int len = Math.max(cParts.length, rParts.length);
        for (int i = 0; i < len; i++) {
            int cVal = 0;
            int rVal = 0;
            if (i < cParts.length && !cParts[i].isEmpty()) {
                try { cVal = Integer.parseInt(cParts[i]); } catch (Exception ignored) {}
            }
            if (i < rParts.length && !rParts[i].isEmpty()) {
                try { rVal = Integer.parseInt(rParts[i]); } catch (Exception ignored) {}
            }
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }

        // If numerical components are identical, check if local was a dirty/dev build and remote is clean release
        if (c.contains("-") && !r.contains("-")) {
            return true;
        }

        // Compare versionCode if embedded in string
        int cCode = extractVersionCode(c);
        int rCode = extractVersionCode(r);
        if (rCode > 0 && cCode > 0) {
            return rCode > cCode;
        }

        return false;
    }

    private static int extractVersionCode(String str) {
        if (str == null) return 0;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("[\\(.](\\d{5,7})[\\)]?").matcher(str);
            if (m.find()) {
                return Integer.parseInt(m.group(1));
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
