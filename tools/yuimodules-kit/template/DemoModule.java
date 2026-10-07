package com.example.hotmod;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;

/**
 * Шаблон хот-модуля. api-класи (HotModule/HotHost/HotRow) — ТІЛЬКИ compileOnly,
 * інакше дублікати в dex зламають ізольований ClassLoader.
 */
public class DemoModule implements HotModule {

    private HotHost host;

    public DemoModule() {
    }

    @Override
    public String moduleId() {
        return "demo";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.log("attached");
        // Важка ініціалізація — у свій потік, onAttach у фоні, але не блокувати надовго.
    }

    @Override
    public void onDetach() {
        // Зняти слухачі, закрити треди. Після цього ClassLoader віддадуть GC.
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Demo";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.switchRow("enabled", "Увімкнено", "", true));
        rows.add(HotRow.button("hello", "Сказати привіт"));
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("hello".equals(rowId) && host != null) {
            host.toast("Привіт з demo-модуля!");
        }
    }
}
