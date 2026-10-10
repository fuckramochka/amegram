package app.amegram.hot.api;

import android.content.Context;

import java.util.List;

/**
 * Контракт хот-модуля. Entry-класс из manifest.json обязан реализовать его,
 * иметь публичный конструктор без аргументов и НЕ включать api-классы в dex
 * (в build.gradle модуля они — compileOnly/provided).
 *
 * Потоки: onAttach вызывается в фоне (не блокировать надолго),
 * fillSettings/onSettings* — на UI-потоке.
 */
public interface HotModule {

    /** Должен совпадать с id из manifest.json и каталога. */
    String moduleId();

    void onAttach(Context appContext, HotHost host);

    void onDetach();

    // ---- Вкладка настроек в разделе Yumigram (опционально) ----

    default boolean hasSettings() {
        return false;
    }

    default String settingsTitle() {
        return "";
    }

    /** Хосту: отдать декларативные строки (см. {@link HotRow}). */
    default void fillSettings(List<HotRow> rows) {
    }

    /** SWITCH toggled — значение уже сохранено хостом. */
    default void onSettingsToggle(String key, boolean value) {
    }

    /** BUTTON нажат. */
    default void onSettingsAction(String rowId) {
    }

    /** INPUT подтверждён (значение уже сохранено хостом). */
    default void onSettingsInput(String key, String value) {
    }

    /** Екрани модуля (чат компаньйона, браузер сховища…). Хост показує через openModuleScreen. */
    default Object createScreen(String screenId) {
        return null;
    }

    // ---- Свои строки прямо в хабе Yumigram (опционально) ----

    default void fillHubRows(List<HotRow> rows) {
    }

    default void onHubAction(String rowId) {
    }
}
