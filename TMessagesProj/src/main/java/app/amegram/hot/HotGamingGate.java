package app.amegram.hot;

import app.amegram.hot.api.HotGaming;
import app.amegram.hot.api.HotServices;

/**
 * Гейт ігрових статусів у ядрі.
 * Якщо модуль gaming активний — бере дані з нього, інакше no-op.
 */
public final class HotGamingGate {

    private HotGamingGate() {
    }

    private static HotGaming service() {
        try {
            return HotModulesManager.getService(HotServices.GAMING);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isEnabled() {
        HotGaming s = service();
        return s != null && s.isEnabled();
    }

    public static String getGameTitle(long userId) {
        HotGaming s = service();
        return s != null ? s.getGameTitle(userId) : null;
    }

    public static String getGamePlatform(long userId) {
        HotGaming s = service();
        return s != null ? s.getGamePlatform(userId) : null;
    }

    public static String getGameDetails(long userId) {
        HotGaming s = service();
        return s != null ? s.getGameDetails(userId) : null;
    }

    public static String getGameIconUrl(long userId) {
        HotGaming s = service();
        return s != null ? s.getGameIconUrl(userId) : null;
    }
}
