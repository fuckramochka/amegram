package com.amegram.mods.settings;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.BaseFragment;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotSettingsUi;
import app.amegram.settings.ui.YumiSettingsModel;

/**
 * Системний модуль «Внутрішнього світу Yumi»:
 * керування та динамічна кастомізація інтерфейсу налаштувань Yumigram.
 */
public class SettingsUiModule implements HotModule, HotSettingsUi {

    private HotHost host;

    public SettingsUiModule() {
    }

    @Override
    public String moduleId() {
        return "settings_ui";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.SETTINGS_UI, this);
        } catch (Throwable ignore) {
        }
        try {
            host.log("settings_ui system module attached");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.SETTINGS_UI);
            }
        } catch (Throwable ignore) {
        }
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Інтерфейс налаштувань Yumi";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Системний модуль"));
        rows.add(HotRow.info("Статус: Активний • Керує меню налаштувань"));
        rows.add(HotRow.info("Тип: Внутрішній світ Yumi (Core)"));
        rows.add(HotRow.info("Версія: 1.0.0"));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
    }

    @Override
    public void onSettingsAction(String key) {
    }

    // --- HotSettingsUi implementation ---

    @Override
    public void customizeModel(YumiSettingsModel model) {
        if (model == null) return;
        if (model.hero != null) {
            model.hero.eyebrow = "⚡ YUMI CORE • СИСТЕМНИЙ МОДУЛЬ";
            model.hero.statSecurity = "🔒 Захист Yumi";
        }
    }

    @Override
    public View createCustomSettingsView(BaseFragment fragment, Context context) {
        // null означає використання стандартного реактивного YumiSettingsView
        // з урахуванням виклику customizeModel
        return null;
    }

    @Override
    public String getSettingsEngineName() {
        return "Yumi System Core v1.0.0 (Модуль)";
    }
}
