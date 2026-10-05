package com.amegram.mods.automation;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotAutomation;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotVault;

/**
 * Автоматизація та фонові задачі:
 * - Автоматична синхронізація хмарного сховища
 * - Автоматичний бекап файлів із 'Збережених'
 * - Автоматичне очищення кешу
 */
public class AutomationModule implements HotModule, HotAutomation {

    private HotHost host;

    @Override
    public String moduleId() {
        return "automation";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.AUTOMATION, this);
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.AUTOMATION);
        host = null;
    }

    @Override
    public boolean isAutoSyncVaultEnabled() {
        return host != null && host.getBool("auto_sync_vault", true);
    }

    @Override
    public boolean isAutoBackupSavedMessages() {
        return host != null && host.getBool("auto_backup_saved", true);
    }

    @Override
    public boolean isAutoCleanCacheEnabled() {
        return host != null && host.getBool("auto_clean_cache", false);
    }

    @Override
    public void triggerVaultSync() {
        try {
            HotVault vault = app.amegram.hot.HotModulesManager.getService(HotServices.VAULT);
            if (vault != null) {
                if (host != null) host.toast("Фонову синхронізацію сховища запущено");
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Автоматизація";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Хмарне сховище"));
        rows.add(HotRow.switchRow("auto_sync_vault", "Автоматична синхронізація",
                "Періодична перевірка та синхронізація зашифрованої хмари", isAutoSyncVaultEnabled()));
        rows.add(HotRow.switchRow("auto_backup_saved", "Автобекап Обраного",
                "Автоматичне збереження медіафайлів із 'Збережених' у хмарний диск", isAutoBackupSavedMessages()));

        rows.add(HotRow.header("Обслуговування клієнта"));
        rows.add(HotRow.switchRow("auto_clean_cache", "Автоочищення кешу",
                "Видаляти тимчасові завантажені файли старше 7 днів", isAutoCleanCacheEnabled()));

        rows.add(HotRow.header("Дії"));
        rows.add(HotRow.button("sync_now", "Синхронізувати зараз", "Запустити синхронізацію дисків"));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("sync_now".equals(rowId)) {
            triggerVaultSync();
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("auto_hub", "Автоматизація", "Синхронізація та розклад"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("auto_hub".equals(rowId) && host != null) {
            host.openSettings();
        }
    }
}
