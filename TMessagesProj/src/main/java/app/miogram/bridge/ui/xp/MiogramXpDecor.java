package app.miogram.bridge.ui.xp;

import org.telegram.ui.ActionBar.ActionBar;

/**
 * Applies the Luna chrome to stock Telegram surfaces when the XP preset is
 * active. Everything is additive (background drawable + text/icon colors),
 * so layout and menus keep working exactly as before.
 */
public final class MiogramXpDecor {

    private MiogramXpDecor() {}

    /** Luna gradient title bar with white title, pale subtitle and white icons. */
    public static void styleActionBar(ActionBar bar) {
        if (bar == null || !MiogramXpTheme.isXpActive()) return;
        try {
            bar.setBackgroundDrawable(MiogramXpTheme.titleBarBackground());
        } catch (Throwable ignore) {}
        try {
            bar.setTitleColor(MiogramXpTheme.TITLE_TEXT);
        } catch (Throwable ignore) {}
        try {
            bar.setSubtitleColor(MiogramXpTheme.TITLE_SUBTEXT);
        } catch (Throwable ignore) {}
        try {
            bar.setItemsColor(MiogramXpTheme.TITLE_TEXT, false);
        } catch (Throwable ignore) {}
    }
}
