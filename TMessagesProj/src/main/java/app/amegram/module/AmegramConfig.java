package app.amegram.module;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The ONLY SharedPreferences facade of the clean module.
 * Replaces 10 legacy files (naconfig, appearance_config, miogram_* ...).
 * Old keys are migrated once, then legacy files are never read again.
 */
public final class AmegramConfig {

    private static final String PREFS = "amegram_module_prefs";
    private static volatile SharedPreferences prefs;

    private AmegramConfig() {
    }

    public static void init(Context appContext) {
        if (prefs == null && appContext != null) {
            prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            migrateOnce(appContext);
        }
    }

    private static void migrateOnce(Context ctx) {
        if (prefs.getBoolean(AmegramModule.PREFS_MIGRATED_KEY, false)) {
            return;
        }
        // Best-effort one-way migration of the few keys worth keeping.
        // Legacy files stay on disk untouched; we just copy values over.
        try {
            copyBool(ctx, "naconfig", "sendReadMessagePackets", "ghost_hide_read", true);
            copyBool(ctx, "naconfig", "sendOnlinePackets", "ghost_hide_online", true);
            copyBool(ctx, "miogram_ui_prefs", "discord_enabled", "layout_discord", false);
        } catch (Throwable ignore) {
        }
        prefs.edit().putBoolean(AmegramModule.PREFS_MIGRATED_KEY, true).apply();
    }

    private static void copyBool(Context ctx, String file, String from, String to, boolean fallbackRead) {
        try {
            SharedPreferences legacy = ctx.getSharedPreferences(file, Context.MODE_PRIVATE);
            if (legacy.contains(from)) {
                prefs.edit().putBoolean(to, legacy.getBoolean(from, fallbackRead)).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    private static SharedPreferences p() {
        if (prefs == null) {
            throw new IllegalStateException("AmegramConfig.init() not called");
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

    public static float getFloat(String key, float def) {
        try {
            return p().getFloat(key, def);
        } catch (Throwable t) {
            return def;
        }
    }

    public static void setFloat(String key, float value) {
        try {
            p().edit().putFloat(key, value).apply();
        } catch (Throwable ignore) {
        }
    }
}
