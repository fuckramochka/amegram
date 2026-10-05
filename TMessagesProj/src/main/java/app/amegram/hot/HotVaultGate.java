package app.amegram.hot;

import java.io.File;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotVault;

/**
 * Гейт хмарного сховища у ядрі.
 * Дозволяє модулю 'vault' керувати завантаженням та шифруванням файлів.
 */
public final class HotVaultGate {

    private HotVaultGate() {
    }

    private static HotVault service() {
        try {
            return HotModulesManager.getService(HotServices.VAULT);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isLinked(int account) {
        HotVault v = service();
        return v != null && v.isLinked(account);
    }

    public static void upload(int account, File file, String fileName, String mimeType, HotVault.Callback callback) {
        HotVault v = service();
        if (v != null) {
            v.upload(account, file, fileName, mimeType, callback);
        } else if (callback != null) {
            callback.onDone(false, "Модуль хмарного сховища не підключено");
        }
    }

    public static void openBrowser() {
        HotModulesManager.openModuleScreen("vault", "browser");
    }
}
