package app.amegram.hot;

import android.content.Context;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotUi;

/**
 * Гейт стилю інтерфейсу: модуль "ui" — єдина точка перемикання
 * Yougram Expressive ↔ Classic на льоту, без перевстановлення APK.
 * Без модуля — прозорий фолбек на AppearanceConfig.
 */
public final class HotUiGate {

    private HotUiGate() {
    }

    public static HotUi get() {
        try {
            return HotModulesManager.getService(HotServices.UI);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    /** Поточний стиль: 1 = Expressive, 0 = Classic. Без модуля — завжди Classic (0). */
    public static int styleMode() {
        try {
            if (!HotModulesManager.isModuleActive("ui")) {
                return 0;
            }
        } catch (Throwable ignore) {
            return 0;
        }
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.styleMode();
            } catch (Throwable ignore) {
            }
        }
        try {
            return app.exteraless.appearance.AppearanceConfig.uiStyleMode();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static boolean isExpressive() {
        return styleMode() == 1;
    }

    // ---- Динамічні кольори з модуля ----

    public static int getColor(String token, int fallback) {
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.getColor(token, fallback);
            } catch (Throwable ignore) {
            }
        }
        return fallback;
    }

    // ---- Динамічні розміри з модуля ----

    public static float getDimension(String token, float fallbackDp) {
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.getDimension(token, fallbackDp);
            } catch (Throwable ignore) {
            }
        }
        return fallbackDp;
    }

    // ---- Динамічне розташування та видимість з модуля ----

    public static int getPlacement(String token, int fallback) {
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.getPlacement(token, fallback);
            } catch (Throwable ignore) {
            }
        }
        return fallback;
    }

    public static boolean isPlacementEnabled(String token, boolean fallback) {
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.isPlacementEnabled(token, fallback);
            } catch (Throwable ignore) {
            }
        }
        return fallback;
    }

    // ---- Слотна архітектура UI ----

    public static android.view.View createSlotView(String slotId, Context context, Object... args) {
        HotUi ui = get();
        if (ui != null) {
            try {
                return ui.createSlotView(slotId, context, args);
            } catch (Throwable ignore) {
            }
        }
        return null;
    }

    /** Перемкнути стиль і застосувати на льоту (rebuild фрагментів, без APK). */
    public static void setExpressive(boolean expressive) {
        try {
            app.exteraless.appearance.AppearanceConfig.uiStyleMode.setConfigInt(expressive ? 1 : 0);
            app.exteraless.appearance.AppearanceConfig.markUiStyleModeTouched();
        } catch (Throwable ignore) {
        }
        try {
            HotModulesManager.putInt("ui", "style", expressive ? 1 : 0);
        } catch (Throwable ignore) {
        }
        applyLive();
    }

    /** Застосувати поточний стиль на льоту: перебудова фрагментів + перезавантаження діалогів. */
    public static void applyLive() {
        try {
            app.exteraless.appearance.AppearanceConfig.invalidateExpressiveCache();
            app.exteraless.appearance.AppearanceConfig.invalidateDividerStyle();
            org.telegram.ui.MainTabsLayout.resetFloatingCache();
        } catch (Throwable ignore) {
        }
        try {
            org.telegram.messenger.NotificationCenter.getGlobalInstance()
                    .postNotificationName(org.telegram.messenger.NotificationCenter.dialogsNeedReload);
        } catch (Throwable ignore) {
        }
        try {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    if (org.telegram.ui.LaunchActivity.instance != null
                            && !org.telegram.ui.LaunchActivity.instance.isFinishing()) {
                        org.telegram.ui.LaunchActivity.instance.rebuildAllFragments(true);
                    }
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable ignore) {
        }
    }

    public static String styleTitle(Context ctx) {
        boolean exp = isExpressive();
        if (ctx == null) return exp ? "Yougram Expressive" : "Classic";
        try {
            return exp
                    ? app.miogram.bridge.MiogramLocale.get("Yougram Expressive (на льоту, модуль ui)",
                            "Yougram Expressive (на лету, модуль ui)", "Yougram Expressive (live, ui module)")
                    : app.miogram.bridge.MiogramLocale.get("Класичний (на льоту, модуль ui)",
                            "Классический (на лету, модуль ui)", "Classic (live, ui module)");
        } catch (Throwable ignore) {
            return exp ? "Yougram Expressive" : "Classic";
        }
    }
}
