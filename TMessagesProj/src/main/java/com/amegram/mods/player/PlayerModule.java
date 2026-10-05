package com.amegram.mods.player;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotPlayer;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/**
 * Плеєр + Пошук музики (Telegram/YouTube/Deezer/iTunes/Jamendo/Audius).
 * Реалізує кастомний дизайн, тексти (LRC), візуалізатор та глобальний пошук.
 */
public class PlayerModule implements HotModule, HotPlayer {

    private HotHost host;

    @Override
    public String moduleId() {
        return "player";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.PLAYER, this);
        host.log("attached player module");
    }

    @Override
    public void onDetach() {
        if (host != null) {
            host.unregisterService(HotServices.PLAYER);
        }
        host = null;
    }

    @Override
    public boolean isModernLayoutEnabled() {
        return host != null && host.getBool("modern_layout", true);
    }

    @Override
    public boolean isVisualizerEnabled() {
        return host != null && host.getBool("visualizer_enabled", true);
    }

    @Override
    public boolean isLyricsEnabled() {
        return host != null && host.getBool("lyrics_enabled", true);
    }

    @Override
    public void openMusicSearch(Context context) {
        if (host != null) {
            host.openModuleScreen("search");
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Музика та плеєр";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        rows.add(HotRow.header("Плеєр та вигляд"));
        rows.add(HotRow.switchRow("modern_layout", "Сучасний вигляд плеєра",
                "Розширені кнопки, оновлена обкладинка та статус", isModernLayoutEnabled()));
        rows.add(HotRow.switchRow("visualizer_enabled", "Аудіовізуалізатор",
                "Жива хвильова анімація басів", isVisualizerEnabled()));
        rows.add(HotRow.switchRow("lyrics_enabled", "Синхронізовані тексти пісень",
                "Караоке LRC-тексти під час відтворення", isLyricsEnabled()));

        rows.add(HotRow.header("Пошук музики"));
        rows.add(HotRow.button("open_search", "Глобальний пошук музики",
                "Telegram • YouTube • Deezer • iTunes • Jamendo • Audius"));
        rows.add(HotRow.info("Пошук здійснюється одночасно по 6 джерелах. Знайдені треки можна слухати онлайн, завантажувати у файл або надсилати у будь-який чат."));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_search".equals(rowId) && host != null) {
            host.openModuleScreen("search");
        }
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("player_search", "Пошук музики", "6 джерел (Telegram, YouTube, Deezer...)"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("player_search".equals(rowId) && host != null) {
            host.openModuleScreen("search");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        if ("search".equals(screenId)) {
            return new MusicSearchScreen();
        }
        return null;
    }
}
