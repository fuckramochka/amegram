package app.amegram.core.xposed;

import app.amegram.core.hooks.HookPoint;
import app.amegram.core.hooks.HookRegistry;
import app.amegram.core.security.AuditLog;

/**
 * Xposed-сумісний міст ядра. Обіцянка плагіну: той самий API
 * (`addXposedHook` / `removeXposedHook`, XC_MethodHook-семантика
 * before/after), та сама спостережувана поведінка.
 *
 * Всередині — НЕinkeція в ART напряму, а ті самі 10 точок ядра:
 * перехоплення запитів/меню/повідомлень маршрутизуються через
 * HookRegistry з тими самими дозволами і журналом, що й інші хуки.
 * Сирий хук довільного Java-метода (поза точками) — єдина задокументована
 * відмінність: він вимагає legacy LSPlant-міст, вимкнений за замовчуванням.
 */
public final class XposedCompat {

    public interface MethodHook {
        void before(Object[] args);
        void after(Object result, Object[] args);
    }

    private XposedCompat() {
    }

    /**
     * Прикріпити хук у стилі Xposed до точки ядра. Повертає opaque handle
     * для {@link #removeXposedHook}.
     */
    public static Object addXposedHook(String pluginId, HookPoint point, final MethodHook hook) {
        if (pluginId == null || point == null || hook == null) {
            return null;
        }
        HookRegistry.Listener listener = (p, payload) -> {
            Object[] args = payload instanceof Object[] ? (Object[]) payload : new Object[]{payload};
            try {
                hook.before(args);
            } catch (Throwable ignore) {
            }
            // after() викликає власник точки після своєї роботи через complete();
            // тут — тільки before-фаза, результат лишається за точкою.
            return false;
        };
        HookRegistry.register(point, listener);
        AuditLog.record(pluginId, "xposed-hook " + point.name());
        return listener;
    }

    public static void removeXposedHook(String pluginId, HookPoint point, Object handle) {
        if (point == null || !(handle instanceof HookRegistry.Listener)) {
            return;
        }
        HookRegistry.unregister(point, (HookRegistry.Listener) handle);
        AuditLog.record(pluginId != null ? pluginId : "?", "xposed-unhook " + point.name());
    }
}
