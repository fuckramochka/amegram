package app.amegram.hot;

import android.content.Context;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.ui.ActionBar.BaseFragment;

import app.amegram.hot.api.HotKpm;
import app.amegram.hot.api.HotServices;
import app.exteraless.plugins.PluginsController;
import app.exteraless.plugins.PythonPluginsEngine;

/**
 * Гейт до автентичного менеджера плагінів Kangel Plugins Manager (KPM).
 * Забезпечує пряме відкриття каталогу KPM Store та обробку deep-link'ів
 * через офіційний Python-рушій KPM.
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
        return true;
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
        return openStoreDirect(fragment, context);
    }

    public static boolean openStoreDirect(BaseFragment fragment, Context context) {
        Context ctx = context != null ? context : (fragment != null ? fragment.getParentActivity() : ApplicationLoader.applicationContext);
        PythonPluginsEngine engine = PythonPluginsEngine.getInstance();
        if (!engine.isStarted()) {
            engine.ensureStarted(ctx != null ? ctx : ApplicationLoader.applicationContext, ok -> {
                if (ok) {
                    AndroidUtilities.runOnUIThread(() -> invokePythonStore());
                }
            });
            return true;
        }
        return invokePythonStore();
    }

    private static boolean invokePythonStore() {
        try {
            // First ensure kangel_plugins_manager is loaded
            PluginsController controller = PluginsController.getInstance();
            if (controller != null) {
                app.exteraless.plugins.Plugin p = controller.getPlugin("kangel_plugins_manager");
                if (p != null && !p.loaded) {
                    PythonPluginsEngine.getInstance().loadPlugin(p);
                }
            }

            com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
            com.chaquo.python.PyObject bridge = py.getModule("kpm_bridge");
            com.chaquo.python.PyObject res = bridge.callAttr("open_store");
            return res != null && res.toBoolean();
        } catch (Throwable t) {
            FileLog.e("HotKpmGate: invokePythonStore failed", t);
            return false;
        }
    }

    public static boolean handleDeepLink(BaseFragment fragment, String url) {
        if (url == null || url.isEmpty()) return false;
        HotKpm kpm = get();
        if (kpm != null) {
            try {
                if (kpm.handleDeepLink(fragment, url)) {
                    return true;
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        try {
            PythonPluginsEngine engine = PythonPluginsEngine.getInstance();
            if (!engine.isStarted()) {
                Context ctx = fragment != null ? fragment.getParentActivity() : ApplicationLoader.applicationContext;
                engine.ensureStarted(ctx != null ? ctx : ApplicationLoader.applicationContext, ok -> {
                    if (ok) {
                        AndroidUtilities.runOnUIThread(() -> invokePythonDeepLink(url));
                    }
                });
                return true;
            }
            return invokePythonDeepLink(url);
        } catch (Throwable t) {
            FileLog.e("HotKpmGate: handleDeepLink failed", t);
            return false;
        }
    }

    private static boolean invokePythonDeepLink(String url) {
        try {
            PluginsController controller = PluginsController.getInstance();
            if (controller != null) {
                app.exteraless.plugins.Plugin p = controller.getPlugin("kangel_plugins_manager");
                if (p != null && !p.loaded) {
                    PythonPluginsEngine.getInstance().loadPlugin(p);
                }
            }

            com.chaquo.python.Python py = com.chaquo.python.Python.getInstance();
            com.chaquo.python.PyObject bridge = py.getModule("kpm_bridge");
            com.chaquo.python.PyObject res = bridge.callAttr("handle_deep_link", url);
            return res != null && res.toBoolean();
        } catch (Throwable t) {
            FileLog.e("HotKpmGate: invokePythonDeepLink failed", t);
            return false;
        }
    }
}
