package app.miogram.bridge.wall;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

/**
 * Налаштування бета-модуля «Стіна профілю».
 * Усе локальне: жодних мережевих ключів, лише прапорці та дрібні UI-налаштування.
 */
public final class WallPrefs {

    public static final String PREFS = "wall_prefs";

    /** Вбудований бета-модуль увімкнено (працює навіть без .hmod). */
    public static final String KEY_BETA_ENABLED = "wall_beta_enabled";
    /** Онбординг першого відкриття вже показано. */
    public static final String KEY_ONBOARDED = "wall_onboarded";
    /** Останній відкритий власник стіни (щоб швидко повертатися). */
    public static final String KEY_LAST_OWNER = "wall_last_owner";
    /** Анімації аврори (може вимкнути для слабких пристроїв). */
    public static final String KEY_ANIMATIONS = "wall_animations";
    /** Звук/тактильний відгук. */
    public static final String KEY_HAPTICS = "wall_haptics";
    /** Показувати розширену статистику в шапці стіни. */
    public static final String KEY_SHOW_STATS = "wall_show_stats";

    private WallPrefs() {
    }

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) {
            return null;
        }
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isBetaEnabled() {
        SharedPreferences p = prefs();
        // За замовчуванням увімкнено: це фішка клієнта, її видно з коробки.
        return p == null || p.getBoolean(KEY_BETA_ENABLED, true);
    }

    public static void setBetaEnabled(boolean enabled) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(KEY_BETA_ENABLED, enabled).apply();
        }
    }

    public static boolean isOnboarded() {
        SharedPreferences p = prefs();
        return p != null && p.getBoolean(KEY_ONBOARDED, false);
    }

    public static void setOnboarded(boolean value) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putBoolean(KEY_ONBOARDED, value).apply();
        }
    }

    public static boolean animationsEnabled() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean(KEY_ANIMATIONS, true);
    }

    public static boolean hapticsEnabled() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean(KEY_HAPTICS, true);
    }

    public static boolean showStats() {
        SharedPreferences p = prefs();
        return p == null || p.getBoolean(KEY_SHOW_STATS, true);
    }

    public static long lastOwner() {
        SharedPreferences p = prefs();
        return p == null ? 0 : p.getLong(KEY_LAST_OWNER, 0);
    }

    public static void setLastOwner(long ownerId) {
        SharedPreferences p = prefs();
        if (p != null) {
            p.edit().putLong(KEY_LAST_OWNER, ownerId).apply();
        }
    }
}
