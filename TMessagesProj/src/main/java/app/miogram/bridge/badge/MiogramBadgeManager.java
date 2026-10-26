package app.miogram.bridge.badge;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.Nullable;

import org.telegram.ui.ActionBar.BaseFragment;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.api.HotBadges;
import app.amegram.hot.api.HotServices;
import app.miogram.bridge.MiogramLocale;

/**
 * Manager for prestigious Miogram badges and community statuses.
 * Delegates to the system hot module {@link HotBadges} (badges) with offline fallback.
 */
public class MiogramBadgeManager {

    public static final long FOUNDER_USER_ID = 8011880648L;

    @Nullable
    public static HotBadges getService() {
        try {
            Object s = HotModulesManager.getService(HotServices.BADGES);
            if (s instanceof HotBadges) {
                return (HotBadges) s;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    public static boolean hasArrow(long userId) {
        if (userId <= 0 || !isModuleEnabled()) {
            return false;
        }
        HotBadges service = getService();
        if (service != null) {
            return service.hasBadge(userId);
        }
        return MiogramSupabaseBridge.hasCloudBadge(userId);
    }

    public static boolean isModuleEnabled() {
        try {
            android.content.Context ctx =
                    org.telegram.messenger.ApplicationLoader.applicationContext;
            if (ctx != null) {
                return ctx.getSharedPreferences("amegram_module_prefs",
                        android.content.Context.MODE_PRIVATE).getBoolean("badges_enabled", true);
            }
        } catch (Throwable ignore) {
        }
        return true;
    }

    public static void setModuleEnabled(boolean enabled) {
        try {
            android.content.Context ctx =
                    org.telegram.messenger.ApplicationLoader.applicationContext;
            if (ctx != null) {
                ctx.getSharedPreferences("amegram_module_prefs",
                        android.content.Context.MODE_PRIVATE).edit().putBoolean("badges_enabled", enabled).apply();
            }
        } catch (Throwable ignore) {
        }
        org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
    }

    public static MiogramBadgeType getBadgeType(long userId) {
        return MiogramSupabaseBridge.getCachedBadgeType(userId);
    }

    @Nullable
    public static MiogramSupabaseBridge.BadgeRecord getBadgeRecord(long userId) {
        if (userId <= 0) {
            return null;
        }
        return MiogramSupabaseBridge.getBadgeRecord(userId);
    }

    public static Drawable getArrowDrawable(long userId) {
        return getArrowDrawable(userId, 16);
    }

    public static Drawable getArrowDrawable(long userId, int sizeDp) {
        HotBadges service = getService();
        if (service != null) {
            Drawable d = service.getBadgeDrawable(userId, sizeDp);
            if (d != null) return d;
        }
        return new MiogramArrowDrawable(sizeDp, getBadgeType(userId));
    }

    public static String getBadgeTitle(long userId) {
        HotBadges service = getService();
        if (service != null) {
            String title = service.getBadgeTitle(userId);
            if (title != null && !title.isEmpty()) return title;
        }
        MiogramSupabaseBridge.BadgeRecord record = getBadgeRecord(userId);
        if (record != null && record.title != null) {
            return record.title;
        }
        MiogramBadgeType type = getBadgeType(userId);
        if (userId == FOUNDER_USER_ID) {
            return MiogramLocale.get("Засновник & Архітектор Miyumi ໒꒱", "Создатель и Архитектор Miyumi ໒꒱", "Miyumi Founder & Architect ໒꒱");
        }
        return MiogramLocale.get("Спільнота Miyumi ໒꒱", "Сообщество Miyumi ໒꒱", "Miyumi Community ໒꒱") + " (" + type.getCode() + ")";
    }

    public static void showBadgeSheet(Context context, long userId) {
        HotBadges service = getService();
        if (service != null) {
            service.showBadgeSheet(context, userId);
            return;
        }
        MiogramBadgeBottomSheet.show(context, userId);
    }

    public static void openBadgesSettings(BaseFragment fragment) {
        HotBadges service = getService();
        if (service != null) {
            service.openBadgesSettings(fragment);
            return;
        }
        fragment.presentFragment(new app.amegram.settings.AmegramBadgesActivity());
    }

    public static void refreshBadges(Runnable onComplete) {
        HotBadges service = getService();
        if (service != null) {
            service.refreshBadges(onComplete);
        } else {
            MiogramSupabaseBridge.fetchBadgesFromCloud(onComplete);
        }
    }
}
