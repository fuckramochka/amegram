package app.miogram.bridge.ecosystem;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import app.miogram.bridge.badge.MiogramBadgeManager;
import app.miogram.bridge.badge.MiogramBadgeType;

/**
 * Amegram Ecosystem Provider.
 *
 * High-speed, secure inter-process communication (IPC) channel connecting
 * Amegram (Telegram) with TikTok MI (@fuckramochka).
 *
 * Capabilities:
 * - 1-Click Theme & Accent color query & mutation (<2ms latency).
 * - Cross-app VIP / Donor / Architect badge verification.
 * - ClipVault: seamless background clipboard buffer for media links.
 */
public class AmegramEcosystemProvider extends ContentProvider {

    public static final String AUTHORITY = "app.amegram.ecosystem";
    public static final String PREFS_NAME = "amegram_ecosystem_prefs";

    private static final Set<String> TRUSTED_PACKAGES = new HashSet<>(Arrays.asList(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.ss.android.ugc.aweme",
            "app.amegram",
            "app.miogram",
            "org.telegram.messenger"
    ));

    // ClipVault memory cache
    public static volatile ClipVaultItem lastClipVaultItem = null;

    public static class ClipVaultItem {
        public final String url;
        public final String title;
        public final String author;
        public final boolean isVideo;
        public final long timestamp;

        public ClipVaultItem(String url, String title, String author, boolean isVideo, long timestamp) {
            this.url = url;
            this.title = title;
            this.author = author;
            this.isVideo = isVideo;
            this.timestamp = timestamp;
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context != null) {
            restoreClipVaultFromPrefs(context);
        }
        return true;
    }

    private boolean isCallerAuthorized() {
        int callingUid = Binder.getCallingUid();
        int myUid = Process.myUid();
        if (callingUid == myUid) {
            return true; // Same app process
        }

        Context context = getContext();
        if (context == null) return false;

        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(callingUid);
        if (packages != null) {
            for (String pkg : packages) {
                if (TRUSTED_PACKAGES.contains(pkg)) {
                    return true;
                }
            }
        }

        // Signature check fallback
        try {
            if (pm.checkSignatures(callingUid, myUid) == PackageManager.SIGNATURE_MATCH) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        return false;
    }

    @Nullable
    @Override
    public Bundle call(@NonNull String method, @Nullable String arg, @Nullable Bundle extras) {
        if (!isCallerAuthorized()) {
            FileLog.e("AmegramEcosystemProvider: Unauthorized IPC call from UID " + Binder.getCallingUid());
            return null;
        }

        Context context = getContext();
        if (context == null) context = ApplicationLoader.applicationContext;

        switch (method) {
            case "getTheme": {
                Bundle res = new Bundle();
                try {
                    int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
                    if (accent == 0) accent = Theme.getColor(Theme.key_actionBarDefault);
                    boolean isDark = Theme.isCurrentThemeDark();
                    boolean isAmoled = isDark && (Theme.getColor(Theme.key_windowBackgroundWhite) == 0xFF000000);

                    res.putInt("accent", accent);
                    res.putBoolean("dark", isDark);
                    res.putBoolean("amoled", isAmoled);
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                return res;
            }

            case "setTheme": {
                if (extras == null) return null;
                final int accent = extras.getInt("accent", 0);
                final boolean dark = extras.getBoolean("dark", true);
                final boolean amoled = extras.getBoolean("amoled", false);

                if (accent != 0) {
                    AndroidUtilities.runOnUIThread(() -> applyEcosystemTheme(accent, dark, amoled));
                }
                Bundle res = new Bundle();
                res.putBoolean("success", true);
                return res;
            }

            case "getBadgeStatus": {
                Bundle res = new Bundle();
                try {
                    int currentAccount = UserConfig.selectedAccount;
                    long userId = UserConfig.getInstance(currentAccount).getClientUserId();
                    boolean hasBadge = MiogramBadgeManager.hasArrow(userId);
                    boolean isFounder = (userId == MiogramBadgeManager.FOUNDER_USER_ID);
                    MiogramBadgeType type = MiogramBadgeManager.getBadgeType(userId);

                    res.putLong("userId", userId);
                    res.putBoolean("isDonor", hasBadge);
                    res.putBoolean("isFounder", isFounder);
                    res.putString("badgeCode", type != null ? type.getCode() : "original");
                    res.putString("badgeTitle", MiogramBadgeManager.getBadgeTitle(userId));
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                return res;
            }

            case "pushClipVault": {
                if (extras == null) return null;
                String url = extras.getString("url", "");
                String title = extras.getString("title", "");
                String author = extras.getString("author", "");
                boolean isVideo = extras.getBoolean("isVideo", true);

                if (!TextUtils.isEmpty(url)) {
                    ClipVaultItem item = new ClipVaultItem(url, title, author, isVideo, System.currentTimeMillis());
                    lastClipVaultItem = item;
                    saveClipVaultToPrefs(context, item);

                    // Notify UI that a new ClipVault media is ready
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getGlobalInstance().postNotificationName(
                                NotificationCenter.fileLoaded, "clipvault_" + item.url, null);
                    });
                }
                Bundle res = new Bundle();
                res.putBoolean("success", true);
                return res;
            }

            case "getClipVault": {
                Bundle res = new Bundle();
                ClipVaultItem item = lastClipVaultItem;
                if (item != null) {
                    res.putString("url", item.url);
                    res.putString("title", item.title);
                    res.putString("author", item.author);
                    res.putBoolean("isVideo", item.isVideo);
                    res.putLong("timestamp", item.timestamp);
                }
                return res;
            }

            default:
                return null;
        }
    }

    /**
     * Applies new accent and dark/amoled theme across Amegram seamlessly.
     */
    private static void applyEcosystemTheme(int accentColor, boolean dark, boolean amoled) {
        try {
            Theme.ThemeInfo themeInfo = dark ? Theme.getTheme(amoled ? "Night" : "Dark Blue") : Theme.getTheme("Day");
            if (themeInfo == null) {
                themeInfo = dark ? Theme.getCurrentNightTheme() : Theme.getCurrentTheme();
            }
            if (themeInfo == null) return;

            Theme.ThemeAccent accent = themeInfo.getAccent(true);
            if (accent != null) {
                accent.accentColor = accentColor;
                accent.myMessagesAccentColor = accentColor;
                themeInfo.setCurrentAccentId(accent.id);
            }

            Theme.applyTheme(themeInfo, true, dark);
            NotificationCenter.getGlobalInstance().postNotificationName(
                    NotificationCenter.needSetDayNightTheme, themeInfo, dark, null, -1);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void saveClipVaultToPrefs(Context context, ClipVaultItem item) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString("last_url", item.url)
                    .putString("last_title", item.title)
                    .putString("last_author", item.author)
                    .putBoolean("last_is_video", item.isVideo)
                    .putLong("last_timestamp", item.timestamp)
                    .apply();
        } catch (Throwable ignored) {
        }
    }

    private static void restoreClipVaultFromPrefs(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String url = prefs.getString("last_url", null);
            if (!TextUtils.isEmpty(url)) {
                lastClipVaultItem = new ClipVaultItem(
                        url,
                        prefs.getString("last_title", ""),
                        prefs.getString("last_author", ""),
                        prefs.getBoolean("last_is_video", true),
                        prefs.getLong("last_timestamp", 0)
                );
            }
        } catch (Throwable ignored) {
        }
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        return null;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return "vnd.android.cursor.dir/vnd.app.amegram.ecosystem";
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        return 0;
    }
}
