package app.amegram.hot;

import org.telegram.tgnet.TLObject;

import app.amegram.hot.api.HotGhost;
import app.amegram.hot.api.HotServices;

/**
 * Гейт привида в ядрі: ріже read/typing на рівні мережі,
 * online — мутацією updateStatus в offline (колбеки цілі).
 * Без модуля ghost — прозорий no-op.
 */
public final class HotGhostGate {

    private HotGhostGate() {
    }

    private static HotGhost service() {
        try {
            return HotModulesManager.getService(HotServices.GHOST);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean hideTyping() {
        HotGhost g = service();
        return g != null && g.hideTyping();
    }

    public static boolean hideOnline() {
        HotGhost g = service();
        return g != null && g.hideOnline();
    }

    public static boolean hideRead() {
        HotGhost g = service();
        return g != null && g.hideRead();
    }

    public static boolean hideStories() {
        HotGhost g = service();
        return g != null && g.hideStories();
    }

    /** Анти-видалення — частина ghost-модуля (єдине ціле). */
    public static boolean isSaveDeletedMessages() {
        HotGhost g = service();
        if (g != null) return g.isSaveDeletedMessages();
        return false;
    }

    public static boolean isSaveEditHistory() {
        HotGhost g = service();
        if (g != null) return g.isSaveEditHistory();
        return false;
    }

    public static boolean isSaveDeletedMedia() {
        HotGhost g = service();
        if (g != null) return g.isSaveDeletedMedia();
        return false;
    }

    /** true = запит мовчки відкинути (токен липовий, колбек не зовемо). */
    public static boolean shouldBlock(TLObject object) {
        if (object == null) return false;
        HotGhost g = service();
        if (g == null) return false;
        String name;
        try {
            name = object.getClass().getSimpleName();
        } catch (Throwable ignore) {
            return false;
        }
        try {
            if (name.contains("readHistory") || name.contains("readMentions")
                    || name.contains("readMessageContents")) {
                return g.hideRead();
            }
            if (name.contains("readStories")) {
                return g.hideStories();
            }
            if (name.contains("setTyping")) {
                return g.hideTyping();
            }
        } catch (Throwable ignore) {
        }
        return false;
    }
}
