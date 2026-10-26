package app.amegram.hot;

import app.amegram.hot.api.HotFeed;
import app.amegram.hot.api.HotServices;

/**
 * Гейт стрічки каналів у ядрі.
 */
public final class HotFeedGate {

    private HotFeedGate() {
    }

    private static HotFeed service() {
        try {
            return HotModulesManager.getService(HotServices.FEED);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isEnabled() {
        HotFeed s = service();
        return s != null && s.isEnabled();
    }

    public static void openFeed() {
        HotFeed s = service();
        if (s != null) {
            s.openFeed();
        }
    }
}
