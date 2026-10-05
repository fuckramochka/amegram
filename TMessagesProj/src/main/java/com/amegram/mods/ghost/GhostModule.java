package com.amegram.mods.ghost;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotGhost;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/** Режим привида: ріже read/stories/online/typing. Портовано з AmegramGhost*. */
public class GhostModule implements HotModule, HotGhost {

    private HotHost host;

    @Override
    public String moduleId() {
        return "ghost";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.GHOST, this);
        host.log("attached");
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.GHOST);
        host = null;
    }

    private boolean hide(String key) {
        return host != null && host.getBool(key, true);
    }

    @Override
    public boolean hideRead() {
        return hide("hide_read");
    }

    @Override
    public boolean hideStories() {
        return hide("hide_stories");
    }

    @Override
    public boolean hideOnline() {
        return hide("hide_online");
    }

    @Override
    public boolean hideTyping() {
        return hide("hide_typing");
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Режим привида";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Невидимість"));
        rows.add(HotRow.switchRow("hide_read", "Не відмічати прочитане",
                "Співрозмовник не бачить галочки", hide("hide_read")));
        rows.add(HotRow.switchRow("hide_stories", "Не відмічати сторіс",
                "Перегляди історій не відправляються", hide("hide_stories")));
        rows.add(HotRow.switchRow("hide_online", "Завжди офлайн",
                "Статус online не відправляється", hide("hide_online")));
        rows.add(HotRow.switchRow("hide_typing", "Ховати «друкує…»",
                "Співрозмовник не бачить набір тексту", hide("hide_typing")));
        rows.add(HotRow.info("Працює на рівні мережі: пакети read/typing ріжуться, "
                + "online підміняється на offline. Діє одразу, без перезапуску."));
    }
}
