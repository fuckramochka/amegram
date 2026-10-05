package app.amegram.hot;

import android.content.Context;

import app.amegram.hot.api.HotPlayer;
import app.amegram.hot.api.HotServices;

/**
 * Гейт плеєра в ядрі.
 * Дозволяє викликати глобальний пошук музики з 6 сервісів
 * та управляти кастомним дизайном плеєра, візуалізатором і текстами.
 * При відсутності модуля працює стоковий плеєр Telegram.
 */
public final class HotPlayerGate {

    private HotPlayerGate() {
    }

    private static HotPlayer service() {
        try {
            return HotModulesManager.getService(HotServices.PLAYER);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isModernLayoutEnabled() {
        HotPlayer p = service();
        return p != null && p.isModernLayoutEnabled();
    }

    public static boolean isVisualizerEnabled() {
        HotPlayer p = service();
        return p != null && p.isVisualizerEnabled();
    }

    public static boolean isLyricsEnabled() {
        HotPlayer p = service();
        return p != null && p.isLyricsEnabled();
    }

    public static boolean isMusicSearchAvailable() {
        HotPlayer p = service();
        return p != null || HotModulesManager.isModuleInstalled("player");
    }

    public static void openMusicSearch(Context context) {
        HotPlayer p = service();
        if (p != null) {
            p.openMusicSearch(context);
        } else {
            HotModulesManager.openModuleScreen("player", "search");
        }
    }
}
