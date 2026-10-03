package app.amegram.module;

import android.content.Context;

/**
 * Single entry point of the clean Amegram Module.
 * Designed to be dropped onto vanilla DrKLO/Telegram with ~5 one-line hooks
 * (see {@link AmegramHooks}). No dependency on app.miogram / app.exteraless.
 */
public final class AmegramModule {

    public static final String MODULE_VERSION = "1.0.0";
    public static final String PREFS_MIGRATED_KEY = "amegram_module_migrated_v1";

    private static volatile boolean initialized;

    private AmegramModule() {
    }

    public static synchronized void init(Context context) {
        if (initialized || context == null) {
            return;
        }
        initialized = true;
        AmegramConfig.init(context.getApplicationContext());
        AmegramFeatureManager.init(context.getApplicationContext());
        try {
            AmegramHooks.onAppCreate(context.getApplicationContext());
        } catch (Throwable ignore) {
        }
    }

    public static boolean isInitialized() {
        return initialized;
    }
}
