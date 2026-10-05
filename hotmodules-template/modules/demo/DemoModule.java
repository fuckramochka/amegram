package com.amegram.mods.demo;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;

/**
 * Пример хот-модуля. Показывает все механики:
 * attach в фоне, вкладка настроек, своя строка в хабе.
 * Собирается build.sh в demo-1.0.0.hmod.
 */
public class DemoModule implements HotModule {

    private HotHost host;

    @Override
    public String moduleId() {
        return "demo";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.log("attached, demo_enabled=" + host.getBool("demo_enabled", false));
        // Пример нативного сервиса: раскомментируй и реализуй HotTranslator —
        // ядро подхватит его вместо стокового поведения.
        // host.registerService(HotServices.TRANSLATOR, new DemoTranslator());
    }

    @Override
    public void onDetach() {
        // host.unregisterService(HotServices.TRANSLATOR);
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Демо-модуль";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean enabled = host != null && host.getBool("demo_enabled", false);
        rows.add(HotRow.header("Демо"));
        rows.add(HotRow.switchRow("demo_enabled", "Включить демо-режим",
                "Модуль что-то меняет в клиенте", enabled));
        rows.add(HotRow.button("ping", "Пинг", "Проверить что модуль жив"));
        rows.add(HotRow.info("Настройки хранятся в hotmod_demo, переживают обновления модуля."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) host.log(key + "=" + value);
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("ping".equals(rowId) && host != null) {
            host.toast("Демо-модуль жив: v1.0.0");
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("demo_ping", "Демо-пинг", "Строка модуля прямо в хабе"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("demo_ping".equals(rowId) && host != null) {
            host.toast("Пинг из хаба");
        }
    }
}
