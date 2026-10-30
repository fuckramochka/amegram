package app.amegram.hot;

import android.content.Context;

import app.amegram.hot.api.HotPlayer;
import app.amegram.hot.api.HotServices;

/** Connects MD3's optional music search action to the music-provider module. */
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

    /** Чи активний кастомний сучасний плеєр (модуль player + його перемикач). */
    public static boolean isModernLayoutEnabled() {
        HotPlayer p = service();
        return p != null && p.isModernLayoutEnabled();
    }

    public static boolean isMusicSearchAvailable() {
        HotPlayer p = service();
        return p != null;
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
