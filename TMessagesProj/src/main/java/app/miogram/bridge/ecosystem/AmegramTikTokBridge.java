package app.miogram.bridge.ecosystem;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.miogram.bridge.MiogramLocale;

/**
 * Ecosystem Bridge facade for Amegram to interact with TikTok MI.
 * Designed for non-intrusive, seamless, high-speed ecosystem integration.
 */
public class AmegramTikTokBridge {

    public static final String[] TIKTOK_PACKAGES = new String[]{
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme"
    };

    public static final String PREFS_NAME = "amegram_ecosystem_prefs";
    public static final String KEY_OPEN_DIRECT = "tiktok_open_direct";
    public static final String KEY_PLAY_IN_APP = "tiktok_play_in_app";
    public static final String KEY_CLEAN_URLS = "tiktok_clean_urls";
    public static final String KEY_CLIPVAULT = "tiktok_clipvault";
    public static final String KEY_THEME_SYNC = "tiktok_theme_sync";
    public static final String KEY_SOUNDBOARD = "tiktok_soundboard";

    public static final String KEY_LAST_WATCHED_URL = "tiktok_last_url";
    public static final String KEY_LAST_WATCHED_TITLE = "tiktok_last_title";
    public static final String KEY_LAST_WATCHED_AUTHOR = "tiktok_last_author";
    public static final String KEY_LAST_WATCHED_COVER = "tiktok_last_cover";
    public static final String KEY_LAST_WATCHED_TIME = "tiktok_last_time";

    public static final String ACTION_TIKTOKMI_THEME = "mi.tiktokmi.ACTION_THEME_CHANGED";
    public static final String ACTION_TIKTOK_WATCHING = "app.amegram.ACTION_TIKTOK_WATCHING";
    /** Broadcast the mod can send to ask TikTok MI for the logged-in account. */
    public static final String ACTION_TIKTOKMI_ACCOUNT_REQUEST = "app.amegram.ACTION_ACCOUNT_SYNC";
    /** Extra key for a custom TikTok MI mod package (e.g. a fork build). */
    public static final String KEY_CUSTOM_TIKTOK_PACKAGE = "tiktok_custom_package";

    private static final Pattern VIDEO_ID_PATTERN = Pattern.compile("/video/(\\d+)");
    private static final Pattern USER_PATTERN = Pattern.compile("/@([a-zA-Z0-9_.-]+)");

    // ------------------------------------------------------------- Preferences

    private static SharedPreferences getPrefs(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isPlayInAppEnabled() {
        return getPrefs(null).getBoolean(KEY_PLAY_IN_APP, true);
    }

    public static void setPlayInAppEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_PLAY_IN_APP, enabled).apply();
    }

    public static boolean isOpenDirectEnabled() {
        return getPrefs(null).getBoolean(KEY_OPEN_DIRECT, true);
    }

    public static void setOpenDirectEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_OPEN_DIRECT, enabled).apply();
    }

    public static boolean isCleanUrlsEnabled() {
        return getPrefs(null).getBoolean(KEY_CLEAN_URLS, true);
    }

    public static void setCleanUrlsEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_CLEAN_URLS, enabled).apply();
    }

    public static boolean isClipVaultEnabled() {
        return getPrefs(null).getBoolean(KEY_CLIPVAULT, true);
    }

    public static void setClipVaultEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_CLIPVAULT, enabled).apply();
    }

    public static boolean isThemeSyncEnabled() {
        // Amegram: theme sync with TikTok MI is OFF by default — no silent re-theming.
        return getPrefs(null).getBoolean(KEY_THEME_SYNC, false);
    }

    public static void setThemeSyncEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_THEME_SYNC, enabled).apply();
    }

    public static boolean isSoundboardEnabled() {
        return getPrefs(null).getBoolean(KEY_SOUNDBOARD, true);
    }

    public static void setSoundboardEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_SOUNDBOARD, enabled).apply();
    }

    public static class WatchingVideo {
        public String url;
        public String title;
        public String author;
        public String coverUrl;
        public long timestamp;

        public boolean isRecent() {
            return System.currentTimeMillis() - timestamp < 24 * 60 * 60 * 1000L;
        }
    }

    public static void setCurrentlyWatching(String url, String title, String author, String coverUrl) {
        if (TextUtils.isEmpty(url)) return;
        getPrefs(null).edit()
                .putString(KEY_LAST_WATCHED_URL, url)
                .putString(KEY_LAST_WATCHED_TITLE, title != null ? title : "")
                .putString(KEY_LAST_WATCHED_AUTHOR, author != null ? author : "")
                .putString(KEY_LAST_WATCHED_COVER, coverUrl != null ? coverUrl : "")
                .putLong(KEY_LAST_WATCHED_TIME, System.currentTimeMillis())
                .apply();
    }

    public static WatchingVideo getCurrentlyWatching() {
        SharedPreferences p = getPrefs(null);
        String url = p.getString(KEY_LAST_WATCHED_URL, null);
        if (TextUtils.isEmpty(url)) return null;
        WatchingVideo v = new WatchingVideo();
        v.url = url;
        v.title = p.getString(KEY_LAST_WATCHED_TITLE, "");
        v.author = p.getString(KEY_LAST_WATCHED_AUTHOR, "");
        v.coverUrl = p.getString(KEY_LAST_WATCHED_COVER, "");
        v.timestamp = p.getLong(KEY_LAST_WATCHED_TIME, 0);
        return v;
    }

    // ------------------------------------------------------------- Package & App Status

    /**
     * Checks if TikTok MI (or TikTok) is installed on the device.
     */
    public static boolean isTikTokMiInstalled(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return false;
        String custom = getCustomTikTokPackage();
        if (!TextUtils.isEmpty(custom) && isPackageInstalled(context, custom)) return true;
        PackageManager pm = context.getPackageManager();
        for (String pkg : TIKTOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean isPackageInstalled(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Custom TikTok MI mod package name (empty = none). Set from settings or auto-detect. */
    public static String getCustomTikTokPackage() {
        try {
            return getPrefs(null).getString(KEY_CUSTOM_TIKTOK_PACKAGE, "");
        } catch (Throwable ignore) {
            return "";
        }
    }

    public static void setCustomTikTokPackage(String pkg) {
        try {
            getPrefs(null).edit().putString(KEY_CUSTOM_TIKTOK_PACKAGE,
                    pkg != null ? pkg.trim() : "").apply();
        } catch (Throwable ignore) {}
    }

    /**
     * Asks the installed TikTok MI app for the currently logged-in account.
     * Forward-compatible: returns null on MI builds that don't expose it yet —
     * callers must fall back to cloud snapshot / manual link.
     * Expected keys: username, nickname, avatar, followers, following, likes, bio.
     */
    public static Bundle queryTikTokMiAccount(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return null;
        String installed = getInstalledTikTokPackage(context);
        String[] authorities;
        if (installed != null) {
            authorities = new String[]{installed + ".ecosystem", "app.amegram.ecosystem", "mi.tiktokmi.ecosystem"};
        } else {
            authorities = new String[]{"app.amegram.ecosystem", "mi.tiktokmi.ecosystem"};
        }
        for (String auth : authorities) {
            try {
                Uri uri = Uri.parse("content://" + auth);
                Bundle res = context.getContentResolver().call(uri, "getAccount", null, null);
                if (res != null && !TextUtils.isEmpty(res.getString("username", ""))) {
                    return res;
                }
            } catch (Throwable ignored) {
            }
        }
        // Nudge MI builds that listen for explicit account requests.
        try {
            Intent req = new Intent(ACTION_TIKTOKMI_ACCOUNT_REQUEST);
            req.setPackage(installed != null ? installed : "com.zhiliaoapp.musically");
            context.sendBroadcast(req);
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * Gets the active installed TikTok package name.
     */
    public static String getInstalledTikTokPackage(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return null;
        String custom = getCustomTikTokPackage();
        if (!TextUtils.isEmpty(custom) && isPackageInstalled(context, custom)) return custom;
        PackageManager pm = context.getPackageManager();
        for (String pkg : TIKTOK_PACKAGES) {
            try {
                pm.getPackageInfo(pkg, 0);
                return pkg;
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /**
     * Cleans tracking parameters from TikTok URLs (e.g. ?_t=..., &is_from_webapp=1, etc.).
     */
    public static String cleanTikTokUrl(String url) {
        if (TextUtils.isEmpty(url)) return url;
        try {
            int qIndex = url.indexOf('?');
            if (qIndex != -1) {
                return url.substring(0, qIndex);
            }
        } catch (Throwable ignored) {
        }
        return url;
    }

    /**
     * Opens a TikTok video or profile URL directly inside TikTok MI without any dialogs.
     */
    public static boolean openInTikTokMi(Context context, String url) {
        if (context == null || TextUtils.isEmpty(url)) return false;

        String pkg = getInstalledTikTokPackage(context);
        if (pkg == null) return false;

        try {
            String targetUrl = isCleanUrlsEnabled() ? cleanTikTokUrl(url) : url;

            // 1. Try extracting video ID for direct aweme detail deep link
            Matcher mVideo = VIDEO_ID_PATTERN.matcher(targetUrl);
            if (mVideo.find()) {
                String videoId = mVideo.group(1);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1233://aweme/detail/" + videoId));
                intent.setPackage(pkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }

            // 2. Try extracting user handle for direct profile deep link
            Matcher mUser = USER_PATTERN.matcher(targetUrl);
            if (mUser.find()) {
                String username = mUser.group(1);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1233://user/profile/" + username));
                intent.setPackage(pkg);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return true;
            }

            // 3. Fallback: package view with target url
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl));
            intent.setPackage(pkg);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable error) {
            try {
                Intent fallback = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                fallback.setPackage(pkg);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(fallback);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    /**
     * Broadcasts Amegram's current theme/accent to TikTok MI.
     */
    public static void syncThemeToTikTokMi(Context context, int accentColor, boolean isDark, boolean isAmoled) {
        if (!isThemeSyncEnabled()) return;
        if (context == null) context = ApplicationLoader.applicationContext;
        String pkg = getInstalledTikTokPackage(context);
        if (pkg == null) return;

        try {
            Intent broadcast = new Intent(ACTION_TIKTOKMI_THEME);
            broadcast.setPackage(pkg);
            broadcast.putExtra("accent", accentColor);
            broadcast.putExtra("dark", isDark);
            broadcast.putExtra("amoled", isAmoled);
            context.sendBroadcast(broadcast);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Lists recently downloaded TikTok sound tracks from storage for the Soundboard integration.
     */
    public static List<File> getRecentSoundTracks(Context context) {
        List<File> tracks = new ArrayList<>();
        try {
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File ttmiDir = new File(downloadDir, "TikTok MI");
            if (ttmiDir.exists() && ttmiDir.isDirectory()) {
                File[] files = ttmiDir.listFiles((dir, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".mp3") || lower.endsWith(".m4a") || lower.endsWith(".aac");
                });
                if (files != null) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int i = 0; i < Math.min(files.length, 30); i++) {
                        tracks.add(files[i]);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return tracks;
    }

    private static volatile boolean watchingReceiverRegistered = false;

    /**
     * Initializes dynamic broadcast receiver for incoming TikTok MI watching status updates.
     */
    public static void initWatchingReceiver(Context context) {
        if (watchingReceiverRegistered) return;
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return;

        try {
            android.content.IntentFilter filter = new android.content.IntentFilter(ACTION_TIKTOK_WATCHING);
            android.content.BroadcastReceiver receiver = new android.content.BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (intent == null) return;
                    String url = intent.getStringExtra("url");
                    String title = intent.getStringExtra("title");
                    String author = intent.getStringExtra("author");
                    String cover = intent.getStringExtra("coverUrl");
                    if (!TextUtils.isEmpty(url)) {
                        setCurrentlyWatching(url, title, author, cover);
                    }
                }
            };
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            watchingReceiverRegistered = true;
        } catch (Throwable ignored) {
        }
    }

    public static boolean openTikTokMi(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        String pkg = getInstalledTikTokPackage(context);
        if (pkg != null) {
            try {
                Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    return true;
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }
}
