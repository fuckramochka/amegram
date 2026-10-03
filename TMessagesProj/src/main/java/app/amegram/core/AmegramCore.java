package app.amegram.core;

import android.content.Context;

/**
 * Amegram Core v2 — нове власне ядро. Єдина точка входу.
 * Нуль залежностей від legacy-пакетів: тільки android.* + java.*.
 * Важкі підсистеми (Python, модулі) стартують ліниво.
 */
public final class AmegramCore {

    public static final String CORE_VERSION = "2.0.0";
    public static final int CORE_ABI = 1;

    private static volatile boolean initialized;
    private static volatile Context appContext;

    private AmegramCore() {
    }

    public static synchronized void init(Context context) {
        if (initialized || context == null) {
            return;
        }
        appContext = context.getApplicationContext();
        app.amegram.core.config.CoreConfig.init(appContext);
        app.amegram.core.hooks.HookRegistry.init();
        app.amegram.core.modules.ModuleManager.init(appContext);
        initialized = true;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static Context appContext() {
        return appContext;
    }
}
