package app.amegram.hot.api;

/**
 * Сервіс канальної стрічки новин (Feed).
 */
public interface HotFeed {
    boolean isEnabled();
    void openFeed();
}
