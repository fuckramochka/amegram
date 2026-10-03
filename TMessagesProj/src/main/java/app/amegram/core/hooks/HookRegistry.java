package app.amegram.core.hooks;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Реєстр хуків ядра. Правила перформансу:
 * - emit() — нуль аллокацій (ітерація напряму по CopyOnWriteArrayList),
 *   нуль I/O, нуль синхронізації в гарячому шляху;
 * - виняток в одному слухачі не ламає інших і не падає вгору.
 */
public final class HookRegistry {

    public interface Listener {
        /** Повертає true, якщо подію поглинуто (ланцюг зупиняється). */
        boolean onHook(HookPoint point, Object payload);
    }

    private static final Map<HookPoint, CopyOnWriteArrayList<Listener>> LISTENERS =
            new EnumMap<>(HookPoint.class);
    private static volatile boolean initialized;

    private HookRegistry() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        for (HookPoint p : HookPoint.values()) {
            LISTENERS.put(p, new CopyOnWriteArrayList<>());
        }
    }

    public static void register(HookPoint point, Listener listener) {
        if (point == null || listener == null) {
            return;
        }
        CopyOnWriteArrayList<Listener> list = LISTENERS.get(point);
        if (list != null && !list.contains(listener)) {
            list.add(listener);
        }
    }

    public static void unregister(HookPoint point, Listener listener) {
        CopyOnWriteArrayList<Listener> list = point == null ? null : LISTENERS.get(point);
        if (list != null) {
            list.remove(listener);
        }
    }

    /** Гарячий шлях: нуль аллокацій, нуль I/O, нуль винятків назовні. */
    public static boolean emit(HookPoint point, Object payload) {
        CopyOnWriteArrayList<Listener> list = LISTENERS.get(point);
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (int i = 0, n = list.size(); i < n; i++) {
            try {
                if (list.get(i).onHook(point, payload)) {
                    return true;
                }
            } catch (Throwable ignore) {
            }
        }
        return false;
    }

    /** Для тестів і хаба: скільки точок реально кимось зайнято. */
    public static int livePoints() {
        int n = 0;
        for (CopyOnWriteArrayList<Listener> list : LISTENERS.values()) {
            if (list != null && !list.isEmpty()) {
                n++;
            }
        }
        return n;
    }
}
