package app.amegram.hot.api;

/**
 * Сервіс кастомізації профілю (бейджі, банери, ефекти AmeProfile).
 */
public interface HotProfile {
    boolean isEnabled();
    String getBadgeText(long userId);
    int getBadgeColor(long userId);
    String getBannerUrl(long userId);
    int getNickColor(long userId);
}
