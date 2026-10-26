package app.amegram.hot.api;

/**
 * Сервіс Канбан-дошки та задач.
 */
public interface HotKanban {
    boolean isEnabled();
    void openKanban(long dialogId);
    int getTaskCount(long dialogId);
}
