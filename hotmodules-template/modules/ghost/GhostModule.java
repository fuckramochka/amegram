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
        syncToCore();
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.GHOST);
        host = null;
        try {
            app.amegram.module.AmegramConfig.setBool("ghost_enabled", false);
        } catch (Throwable ignore) {
        }
        try {
            xyz.nextalone.nagram.NaConfig.INSTANCE.getEnableSaveDeletedMessages().setConfigBool(false);
            xyz.nextalone.nagram.NaConfig.INSTANCE.getEnableSaveEditsHistory().setConfigBool(false);
        } catch (Throwable ignore) {
        }
    }

    /** Одне ціле: хот-префи → ядро мережі (AmegramConfig) + сховище видалених (NaConfig). */
    private void syncToCore() {
        try {
            app.amegram.module.AmegramConfig.setBool("ghost_enabled", true);
            app.amegram.module.AmegramConfig.setBool("ghost_hide_read", hideRead());
            app.amegram.module.AmegramConfig.setBool("ghost_hide_online", hideOnline());
            app.amegram.module.AmegramConfig.setBool("ghost_hide_typing", hideTyping());
        } catch (Throwable ignore) {
        }
        try {
            xyz.nextalone.nagram.NaConfig.INSTANCE.getEnableSaveDeletedMessages()
                    .setConfigBool(isSaveDeletedMessages());
            xyz.nextalone.nagram.NaConfig.INSTANCE.getEnableSaveEditsHistory()
                    .setConfigBool(isSaveEditHistory());
            xyz.nextalone.nagram.NaConfig.INSTANCE.getMessageSavingSaveMedia()
                    .setConfigBool(isSaveDeletedMedia());
        } catch (Throwable ignore) {
        }
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
    public boolean isSaveDeletedMessages() {
        return host != null && host.getBool("save_deleted_messages", true);
    }

    @Override
    public boolean isSaveEditHistory() {
        return host != null && host.getBool("save_edit_history", true);
    }

    @Override
    public boolean isSaveDeletedMedia() {
        return host != null && host.getBool("save_deleted_media", true);
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
        rows.add(HotRow.header("Видалені повідомлення (одне ціле з привидом)"));
        rows.add(HotRow.switchRow("save_deleted_messages", "Зберігати видалені",
                "Повідомлення залишаються видимими після видалення", isSaveDeletedMessages()));
        rows.add(HotRow.switchRow("save_edit_history", "Історія редагувань",
                "Збереження початкового тексту відредагованих", isSaveEditHistory()));
        rows.add(HotRow.switchRow("save_deleted_media", "Зберігати медіа видалених",
                "Фото/відео з видалених залишаються", isSaveDeletedMedia()));
        rows.add(HotRow.info("Привид + анти-видалення — один модуль. Працює на рівні мережі: пакети read/typing ріжуться, "
                + "online підміняється на offline, видалені ховаються локально. Діє одразу, без перезапуску."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
        syncToCore();
    }
}
