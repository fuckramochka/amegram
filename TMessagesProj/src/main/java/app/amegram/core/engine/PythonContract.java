package app.amegram.core.engine;

import app.amegram.core.security.AuditLog;

/**
 * Контракт Python-движка, який НЕ змінюється: формат плагіна, BasePlugin,
 * події, меню, хуки запитів, словник інстансів, потокова модель
 * (фоновий старт, синхронні хуки, GIL серіалізує, движок не встав —
 * хук повертає DEFAULT).
 *
 * Цей клас — тонкий міст без compile-залежностей від legacy-движка:
 * усі виклики йдуть рефлексією. Коли новий движок буде готовий,
 * рефлексивні цілі заміняться прямими — підписи методів уже зафіксовані тут.
 */
public final class PythonContract {

    /** Імена, які зобов'язаний надавати будь-який движок-реалізація. */
    public static final String ENGINE_CLASS =
            "app.exteraless.plugins.PythonPluginsEngine";
    public static final String METHOD_START = "startAsync";
    public static final String METHOD_IS_STARTED = "isStarted";
    public static final String METHOD_DISPATCH = "dispatchMessageReceived";

    private PythonContract() {
    }

    public static boolean isEngineStarted() {
        try {
            Class<?> engine = Class.forName(ENGINE_CLASS);
            Object inst = engine.getMethod("getInstance").invoke(null);
            Object started = engine.getMethod(METHOD_IS_STARTED).invoke(inst);
            return Boolean.TRUE.equals(started);
        } catch (Throwable t) {
            return false;
        }
    }

    public static void dispatchMessage(int account, Object messageObject) {
        try {
            Class<?> engine = Class.forName(ENGINE_CLASS);
            Object inst = engine.getMethod("getInstance").invoke(null);
            java.lang.reflect.Method target = null;
            for (java.lang.reflect.Method m : engine.getMethods()) {
                if (m.getName().equals(METHOD_DISPATCH)
                        && m.getParameterTypes().length == 2
                        && m.getParameterTypes()[0] == int.class) {
                    target = m;
                    break;
                }
            }
            if (target == null) {
                return;
            }
            target.invoke(inst, account, messageObject);
            AuditLog.record("python", "dispatch account=" + account);
        } catch (Throwable ignore) {
            // Движок відсутній або несумісний — мовчки пропускаємо, апка живе.
        }
    }
}
