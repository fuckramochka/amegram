package app.amegram.hot;

import android.content.Context;

import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;

import app.amegram.hot.api.HotKpm;
import app.amegram.hot.api.HotServices;

/**
 * Гейт до модуля Kangel Plugins Manager (KPM).
 * Забезпечує доступ до каталогу KPM Store та обробки deep-link'ів
 * без конфліктних байткод-хуків стороннього плагіна.
 */
public final class HotKpmGate {

    private HotKpmGate() {
    }

    public static HotKpm get() {
        try {
            return HotModulesManager.getService(HotServices.KPM);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static boolean openStore(BaseFragment fragment, Context context) {
        HotKpm kpm = get();
        if (kpm != null) {
            try {
                kpm.openStore(fragment, context);
                return true;
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return false;
    }

    public static boolean handleDeepLink(BaseFragment fragment, String url) {
        if (url == null || url.isEmpty()) return false;
        HotKpm kpm = get();
        if (kpm != null) {
            try {
                return kpm.handleDeepLink(fragment, url);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return false;
    }
}
