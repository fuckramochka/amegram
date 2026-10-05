package com.amegram.mods.vault;

import android.content.Context;

import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotVault;

/**
 * Хмарне сховище: AES-256-GCM + форум-супергрупа як диск + автоматизація автосинхронізації.
 * Портовано з cloudvault.
 */
public class VaultModule implements HotModule, HotVault {

    private HotHost host;

    @Override
    public String moduleId() {
        return "vault";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.VAULT, this);
        if (host.getBool("auto_sync", false)) {
            VaultAutoSync.start(appContext, 30);
        }
    }

    @Override
    public void onDetach() {
        VaultAutoSync.stop();
        if (host != null) host.unregisterService(HotServices.VAULT);
        host = null;
    }

    private static int account() {
        try {
            return UserConfig.selectedAccount;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    @Override
    public boolean isLinked(int account) {
        try {
            return VaultEngine.hasVault(account);
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void upload(int account, File file, String fileName, String mimeType, Callback callback) {
        try {
            VaultEngine.uploadFileToVault(account, file, fileName, mimeType,
                    null,
                    result -> callback.onDone(true, result != null ? result.name : "ok"),
                    error -> callback.onDone(false, error));
        } catch (Exception e) {
            callback.onDone(false, e.getMessage());
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Хмарне сховище";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean linked;
        try {
            linked = VaultEngine.hasVault(account());
        } catch (Throwable e) {
            linked = false;
        }
        rows.add(HotRow.header("Сховище"));
        rows.add(HotRow.button("open_browser", "Відкрити сховище",
                linked ? "прив'язано" : "не прив'язано"));
        rows.add(HotRow.info("Шифроване AES-256-GCM, чанки у форум-супергрупі. "
                + "Відкрий браузер і прив'яжи чат-сховище."));

        rows.add(HotRow.header("Автоматизація"));
        boolean autoSync = host != null && host.getBool("auto_sync", false);
        boolean autoBackup = host != null && host.getBool("auto_backup_saved", false);
        rows.add(HotRow.switchRow("auto_sync", "Фонова автосинхронізація",
                "Автоматично оновлювати кеш файлів кожні 30 хвилин", autoSync));
        rows.add(HotRow.switchRow("auto_backup_saved", "Автобекап Обраного",
                "Автоматично дублювати файли з 'Збережених' у хмару", autoBackup));
        rows.add(HotRow.button("sync_now", "Синхронізувати зараз", "Перевірити нові чанки та оновити диск"));

        rows.add(HotRow.header("Ключ шифрування"));
        String hex = "";
        try {
            hex = VaultEngine.getMasterKeyHex();
        } catch (Throwable ignore) {
        }
        rows.add(HotRow.inputRow("master_key", "Master-ключ (hex)", hex != null ? hex : ""));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
        if ("auto_sync".equals(key)) {
            if (value && host != null) {
                VaultAutoSync.start(host.context(), 30);
            } else {
                VaultAutoSync.stop();
            }
        }
    }

    @Override
    public void onSettingsInput(String key, String value) {
        if ("master_key".equals(key) && value != null && !value.isEmpty()) {
            try {
                VaultEngine.setMasterKeyHex(value);
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_browser".equals(rowId) && host != null) {
            host.openModuleScreen("browser");
        } else if ("sync_now".equals(rowId)) {
            VaultAutoSync.syncNow();
            if (host != null) host.toast("Синхронізацію запущено");
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("vault_open", "Сховище", "Шифрований диск"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("vault_open".equals(rowId) && host != null) {
            host.openModuleScreen("browser");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        if ("browser".equals(screenId)) return new VaultBrowser();
        return null;
    }
}
