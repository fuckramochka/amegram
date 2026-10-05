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
        try {
            return app.miogram.bridge.player.MiogramPlayerPrefs.isVisualizerEnabled();
        } catch (Throwable ignore) {
            return host != null && host.getBool("visualizer_enabled", true);
        }
    }

    @Override
    public boolean isLyricsEnabled() {
        return host != null && host.getBool("lyrics_enabled", true);
    }

    private String bgModeName() {
        try {
            int m = app.miogram.bridge.player.MiogramPlayerPrefs.getBackgroundMode();
            switch (m) {
                case 1: return "Градієнт";
                case 2: return "Суцільний колір";
                case 3: return "Прозорий";
                case 4: return "Своє фото";
                case 5: return "Своє відео";
                default: return "Блур обкладинки";
            }
        } catch (Throwable ignore) {
            return "";
        }
    }

    private String blurName() {
        try {
            return "Блур фону: " + app.miogram.bridge.player.MiogramPlayerPrefs.getBgBlur() + " px (тап — змінити)";
        } catch (Throwable ignore) {
            return "Блур фону";
        }
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
                "Розширені кнопки, оновлена обкладинка та статус. Вимкнено = стоковий плеєр TG", isModernLayoutEnabled()));
        rows.add(HotRow.switchRow("visualizer_enabled", "Аудіовізуалізатор",
                "Жива хвильова анімація басів", isVisualizerEnabled()));
        rows.add(HotRow.button("bg_mode", "Фон плеєра: " + bgModeName(),
                "Тап — наступний режим (блур/градієнт/колір/прозорий)"));
        rows.add(HotRow.button("bg_blur", blurName(),
                "Тап — 0 → 10 → 15 → 25 → 30 px. Діє в режимі блуру обкладинки."));

        rows.add(HotRow.header("Тексти пісень"));
        rows.add(HotRow.info("Шрифт, караоке, сяйво — олівець у плеєрі → Секції → Текст пісні."
                + " Там же кнопки, неон і порядок елементів."));

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
        try {
            if ("visualizer_enabled".equals(key)) {
                app.miogram.bridge.player.MiogramPlayerPrefs.setVisualizerEnabled(value);
            }
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("open_search".equals(rowId) && host != null) {
            host.openModuleScreen("search");
        } else if ("bg_mode".equals(rowId)) {
            try {
                int m = app.miogram.bridge.player.MiogramPlayerPrefs.getBackgroundMode();
                int next = (m + 1) % 4;
                app.miogram.bridge.player.MiogramPlayerPrefs.setBackgroundMode(next);
            } catch (Throwable ignore) {
            }
        } else if ("bg_blur".equals(rowId)) {
            try {
                int cur = app.miogram.bridge.player.MiogramPlayerPrefs.getBgBlur();
                int next = cur <= 0 ? 10 : cur <= 10 ? 15 : cur <= 15 ? 25 : cur < 30 ? 30 : 0;
                app.miogram.bridge.player.MiogramPlayerPrefs.setBgBlur(next);
            } catch (Throwable ignore) {
            }
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
