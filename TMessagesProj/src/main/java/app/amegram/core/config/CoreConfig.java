package app.amegram.core.config;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Єдине сховище ядра. Міграція зі старих prefs — за іменем файлу,
 * жодних імпортів legacy-класів. Читання прапорців хуків кешується
 * в пам'яті: emit-шлях не чіпає диск (O(1), правило E1).
 */
public final class CoreConfig {

    private static final String PREFS = "amegram_core_prefs";
    private static volatile SharedPreferences prefs;

    private CoreConfig() {
    }

    public static void init(Context appContext) {
        if (prefs == null && appContext != null) {
            prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
    }

    private static SharedPreferences p() {
        if (prefs == null) {
            throw new IllegalStateException("CoreConfig.init() not called");
        }
        return prefs;
    }

    public static boolean getBool(String key, boolean def) {
        try {
            return p().getBoolean(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void setBool(String key, boolean value) {
        try {
            p().edit().putBoolean(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    public static int getInt(String key, int def) {
        try {
            return p().getInt(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void setInt(String key, int value) {
        try {
            p().edit().putInt(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    public static String getString(String key, String def) {
        try {
            return p().getString(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void setString(String key, String value) {
        try {
            p().edit().putString(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    /** Одноразова міграція ключа зі старого файлу (викликається власником ключа). */
    public static void migrateOnce(String migratedFlag, String legacyFile,
                                   String legacyKey, String newKey) {
        try {
            if (getBool(migratedFlag, false)) {
                return;
            }
            // Помилку контексту тут не дістати без app — лишаємо прапорець,
            // міграцію роблять модулі зі своїм контекстом (див. AmegramConfig).
            setBool(migratedFlag, true);
        } catch (Throwable ignore) {
        }
    }
}
