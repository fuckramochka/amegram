package app.amegram.hot.api;

import android.content.Context;

/**
 * Хост для хот-модуля: префы, тосты, лог, сервисы, события.
 * Реализация живёт в приложении, модуль видит только этот интерфейс.
 */
public interface HotHost {

    Context context();

    String moduleId();

    boolean getBool(String key, boolean def);

    void putBool(String key, boolean value);

    default void setBool(String key, boolean value) {
        putBool(key, value);
    }

    String getString(String key, String def);

    void putString(String key, String value);

    default void setString(String key, String value) {
        putString(key, value);
    }

    int getInt(String key, int def);

    void putInt(String key, int value);

    default void setInt(String key, int value) {
        putInt(key, value);
    }

    void toast(String text);

    void log(String text);

    /** Зарегистрировать нативный сервис (см. {@link HotServices}). */
    void registerService(String name, Object service);

    /** Убрать свой сервис при detach. */
    void unregisterService(String name);

    <T> T getService(String name);

    /** Шина событий "модуль -> ядро/др. модули". Без подписчиков — no-op. */
    void emit(String event, String jsonPayload);

    /** Відкрити екран модуля (див. {@link HotModule#createScreen}). */
    void openModuleScreen(String screenId);

    default void openSettings() {
        openModuleScreen("settings");
    }
}
