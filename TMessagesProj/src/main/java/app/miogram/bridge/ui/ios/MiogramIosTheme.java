package app.miogram.bridge.ui.ios;

import org.telegram.ui.ActionBar.Theme;

/**
 * Direct 1:1 port of Telegram-iOS Presentation Themes:
 * - DefaultDayPresentationTheme.swift
 * - DefaultDarkPresentationTheme.swift
 * Extracted from official TelegramMessenger/Telegram-iOS repository.
 */
public final class MiogramIosTheme {

    private MiogramIosTheme() {}

    // No hardcoded palette: every resolver below returns an active-theme
    // color. The iOS preset defines geometry; colors belong to the theme.

    // MARK: - Dynamic Resolvers
    // Resolvers below intentionally return active-theme colors
    // (Theme.getColor): layout presets change geometry, never colors.
    public static int getNavBarBg() {
        return Theme.getColor(Theme.key_actionBarDefault);
    }

    public static int getNavBarSeparator() {
        return Theme.getColor(Theme.key_divider);
    }

    public static int getAccent() {
        return Theme.getColor(Theme.key_dialogTextLink);
    }

    public static int getTabBarBg() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    public static int getTabBarSeparator() {
        return Theme.getColor(Theme.key_divider);
    }

    public static int getTabBarIcon(boolean selected) {
        return Theme.getColor(selected ? Theme.key_dialogTextLink : Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int getTabBarText(boolean selected) {
        return Theme.getColor(selected ? Theme.key_dialogTextLink : Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int getSearchInputFill() {
        return Theme.getColor(Theme.key_windowBackgroundGray);
    }

    public static int getSearchText() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int getSearchPlaceholder() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int getChatListBg() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    public static int getChatListSeparator() {
        return Theme.getColor(Theme.key_divider);
    }

    public static int getChatListTitle() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int getChatListMessage() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }
}
