package app.amegram.core.net;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Per-chat ghost exclusions, owned by the core. Migrates legacy Ayu keys
 * ("ghostModeReadExclusion_*") from the Neko prefs file ONCE, then never
 * touches legacy storage again.
 */
public final class GhostExclusions {

    private static final String PREFS = "amegram_ghost_excl";
    private static final String READ_PREFIX = "read_";
    private static final String TYPING_PREFIX = "typing_";
    private static final String LEGACY_FILE = "nkmrcfg";
    private static final String LEGACY_READ_PREFIX = "ghostModeReadExclusion_";
    private static final String LEGACY_TYPING_PREFIX = "ghostModeTypingExclusion_";
    private static final String KEY_MIGRATED = "migrated_v1";

    private GhostExclusions() {
    }

    private static SharedPreferences prefs() {
        try {
            Context ctx = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (ctx != null) {
                return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    public static boolean getReadExclusion(long chatId) {
        migrateOnce();
        try {
            SharedPreferences p = prefs();
            return p != null && p.getBoolean(READ_PREFIX + Math.abs(chatId), false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setReadExclusion(long chatId, boolean value) {
        try {
            SharedPreferences p = prefs();
            if (p != null) {
                p.edit().putBoolean(READ_PREFIX + Math.abs(chatId), value).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    public static boolean getTypingExclusion(long chatId) {
        migrateOnce();
        try {
            SharedPreferences p = prefs();
            return p != null && p.getBoolean(TYPING_PREFIX + Math.abs(chatId), false);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void setTypingExclusion(long chatId, boolean value) {
        try {
            SharedPreferences p = prefs();
            if (p != null) {
                p.edit().putBoolean(TYPING_PREFIX + Math.abs(chatId), value).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    private static void migrateOnce() {
        try {
            SharedPreferences p = prefs();
            if (p == null || p.getBoolean(KEY_MIGRATED, false)) {
                return;
            }
            Context ctx = org.telegram.messenger.ApplicationLoader.applicationContext;
            if (ctx == null) {
                return;
            }
            SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_FILE, Context.MODE_PRIVATE);
            java.util.Map<String, ?> all = legacy.getAll();
            android.content.SharedPreferences.Editor ed = p.edit();
            boolean touched = false;
            if (all != null) {
                for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                    String k = e.getKey();
                    if (!(e.getValue() instanceof Boolean)) {
                        continue;
                    }
                    if (k.startsWith(LEGACY_READ_PREFIX)) {
                        ed.putBoolean(READ_PREFIX + k.substring(LEGACY_READ_PREFIX.length()),
                                (Boolean) e.getValue());
                        touched = true;
                    } else if (k.startsWith(LEGACY_TYPING_PREFIX)) {
                        ed.putBoolean(TYPING_PREFIX + k.substring(LEGACY_TYPING_PREFIX.length()),
                                (Boolean) e.getValue());
                        touched = true;
                    }
                }
            }
            ed.putBoolean(KEY_MIGRATED, true).apply();
            if (touched) {
                android.util.Log.d("GhostExclusions", "migrated legacy exclusions");
            }
        } catch (Throwable ignore) {
        }
    }

    /** For tests: parse check without Android. */
    static boolean isReadKey(String key) {
        return key != null && key.startsWith(READ_PREFIX);
    }
}
