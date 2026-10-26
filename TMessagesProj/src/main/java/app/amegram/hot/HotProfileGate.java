package app.amegram.hot;

import app.amegram.hot.api.HotProfile;
import app.amegram.hot.api.HotServices;

/**
 * Гейт кастомізації профілю (AmeProfile) у ядрі.
 */
public final class HotProfileGate {

    private HotProfileGate() {
    }

    private static HotProfile service() {
        try {
            return HotModulesManager.getService(HotServices.PROFILE);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isEnabled() {
        HotProfile s = service();
        return s != null && s.isEnabled();
    }

    public static String getBadgeText(long userId) {
        HotProfile s = service();
        return s != null ? s.getBadgeText(userId) : null;
    }

    public static int getBadgeColor(long userId) {
        HotProfile s = service();
        return s != null ? s.getBadgeColor(userId) : 0;
    }

    public static String getBannerUrl(long userId) {
        HotProfile s = service();
        return s != null ? s.getBannerUrl(userId) : null;
    }

    public static int getNickColor(long userId) {
        HotProfile s = service();
        return s != null ? s.getNickColor(userId) : 0;
    }
}
