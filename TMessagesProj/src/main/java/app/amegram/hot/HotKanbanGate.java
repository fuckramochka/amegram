package app.amegram.hot;

import app.amegram.hot.api.HotKanban;
import app.amegram.hot.api.HotServices;

/**
 * Гейт Канбан-дошки в ядрі.
 */
public final class HotKanbanGate {

    private HotKanbanGate() {
    }

    private static HotKanban service() {
        try {
            return HotModulesManager.getService(HotServices.KANBAN);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isEnabled() {
        HotKanban s = service();
        return s != null && s.isEnabled();
    }

    public static void openKanban(long dialogId) {
        HotKanban s = service();
        if (s != null) {
            s.openKanban(dialogId);
        }
    }

    public static int getTaskCount(long dialogId) {
        HotKanban s = service();
        return s != null ? s.getTaskCount(dialogId) : 0;
    }
}
