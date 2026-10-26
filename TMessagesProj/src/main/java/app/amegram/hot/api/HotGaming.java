package app.amegram.hot.api;

/**
 * Сервіс ігрових статусів та Rich Presence (Steam, Discord, Roblox).
 */
public interface HotGaming {
    boolean isEnabled();
    String getGameTitle(long userId);
    String getGamePlatform(long userId);
    String getGameDetails(long userId);
    String getGameIconUrl(long userId);
}
