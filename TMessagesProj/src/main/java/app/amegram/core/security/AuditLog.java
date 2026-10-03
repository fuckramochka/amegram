package app.amegram.core.security;

/**
 * Кільцевий журнал аудиту: хто, що, коли. Без I/O в гарячому шляху —
 * тільки пам'ять (останні 256 подій), злив за запитом екрана безпеки.
 */
public final class AuditLog {

    private static final int CAPACITY = 256;
    private static final String[] RING = new String[CAPACITY];
    private static int cursor;
    private static int count;

    private AuditLog() {
    }

    public static synchronized void record(String actor, String action) {
        if (actor == null || action == null) {
            return;
        }
        RING[cursor] = System.currentTimeMillis() + " " + actor + " " + action;
        cursor = (cursor + 1) % CAPACITY;
        if (count < CAPACITY) {
            count++;
        }
    }

    public static synchronized String[] snapshot() {
        String[] out = new String[count];
        for (int i = 0; i < count; i++) {
            out[i] = RING[(cursor - count + i + CAPACITY * 2) % CAPACITY];
        }
        return out;
    }

    public static synchronized void clear() {
        for (int i = 0; i < CAPACITY; i++) {
            RING[i] = null;
        }
        cursor = 0;
        count = 0;
    }
}
