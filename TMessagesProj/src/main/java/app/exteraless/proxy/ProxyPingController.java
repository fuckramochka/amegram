package app.exteraless.proxy;

import android.os.SystemClock;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;

public final class ProxyPingController {

    private static final long INTERVAL_MS = 10_000L;
    private static final Runnable PING = ProxyPingController::ping;

    private ProxyPingController() {
    }

    public static void init() {
        schedule();
    }

    private static void ping() {
        SharedConfig.ProxyInfo proxy = SharedConfig.currentProxy;
        if (SharedConfig.isProxyEnabled() && proxy != null) {
            int ping = ConnectionsManager.native_getCurrentPingTime(UserConfig.selectedAccount);
            if (ping > 0) {
                proxy.ping = ping;
                proxy.available = true;
                proxy.availableCheckTime = SystemClock.elapsedRealtime();
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyPingUpdated, ping);
            }
        }
        schedule();
    }

    private static void schedule() {
        AndroidUtilities.cancelRunOnUIThread(PING);
        AndroidUtilities.runOnUIThread(PING, INTERVAL_MS);
    }
}
