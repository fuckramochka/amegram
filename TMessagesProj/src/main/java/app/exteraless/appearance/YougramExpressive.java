package app.exteraless.appearance;

import android.util.SparseIntArray;

import org.telegram.ui.ActionBar.Theme;

/**
 * Yougram Expressive — перенос дизайн-языка Yougram (Material 3 Expressive glass)
 * на View-интерфейс форка.
 *
 * Yougram — самостоятельный Compose-клиент, общего кода с форком нет, поэтому
 * переносятся значения палитры (yougram core/ui/theme/Theme.kt + BubbleColors.kt):
 *
 *  - тёмная тема: true dark #121212, варианты #242424, вторичный текст #C4C4C4,
 *    основной текст #F2F2F2, контейнеры — лерп акцента к чёрному/белому;
 *  - светлая тема: фон #FCFCFC, вторичный текст #4A4A4A;
 *  - чат: фон #17181B / #F4F5F7, входящие #2A2C31 / #E9EAEE, исходящие —
 *    лерп базового серого к акценту на 12% / 10%, текст всегда читаемый;
 *  - геометрия пузырей: радиус 24dp, «внутренний» угол 6dp, без хвостиков.
 *
 * Класс — единственная точка правды для режима: палитра применяется одним вызовом
 * из Theme.refreshThemeColors (после fillAccentColors, до расчётных таблиц), поэтому
 * все зависимые краски (applyChatTheme и т.д.) получают уже скорректированные ключи.
 */
public final class YougramExpressive {

    private YougramExpressive() {
    }

    /** Быстрый выход для горячих путей отрисовки. */
    public static boolean enabled() {
        try {
            return AppearanceConfig.isYougramExpressive();
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Пузыри ----

    /** Радиус пузыря, dp; -1 когда режим выключен. */
    public static int bubbleRadiusDp() {
        return enabled() ? 24 : -1;
    }

    /** Радиус медиа внутри пузыря (на 2dp меньше пузыря), dp; -1 когда режим выключен. */
    public static int bubbleMediaRadiusDp() {
        return enabled() ? 22 : -1;
    }

    /** В режиме — чистые скруглённые пузыри без «хвостиков», как в Yougram. */
    public static boolean removeMessageTail() {
        return enabled();
    }

    // ---- Палитра ----

    private static final int DARK_BG = 0xFF121212;
    private static final int DARK_SURFACE = 0xFF1C1C1F;
    private static final int DARK_TEXT = 0xFFF2F2F2;
    private static final int DARK_SECONDARY = 0xFFC4C4C4;
    private static final int DARK_MUTED = 0xFF85858A;

    private static final int LIGHT_BG = 0xFFFCFCFC;
    private static final int LIGHT_GRAY_BG = 0xFFF4F5F7;
    private static final int LIGHT_SURFACE = 0xFFFFFFFF;
    private static final int LIGHT_TEXT = 0xFF111111;
    private static final int LIGHT_SECONDARY = 0xFF4A4A4A;
    private static final int LIGHT_MUTED = 0xFF7A7A7E;

    private static final int DARK_CHAT_BG = 0xFF17181B;
    private static final int DARK_IN_BUBBLE = 0xFF2A2C31;
    private static final int DARK_OUT_BUBBLE_BASE = 0xFF3B3F46;

    private static final int LIGHT_CHAT_BG = 0xFFF4F5F7;
    private static final int LIGHT_IN_BUBBLE = 0xFFE9EAEE;
    private static final int LIGHT_OUT_BUBBLE_BASE = 0xFFDDE8F7;

    /**
     * Применяет палитру Yougram поверх текущей темы.
     *
     * Вызывается из {@link Theme#refreshThemeColors(boolean, boolean)} сразу после
     * accent.fillAccentColors — до расчётных таблиц и applyChatTheme, чтобы все
     * производные краски считались уже от скорректированных ключей.
     *
     * Акцент берётся из уже залитых акцентных ключей (Monet-акцент или акцент темы) —
     * так исходящие пузыри и контейнеры остаются привязаны к выбору пользователя.
     */
    public static void applyOverrides(SparseIntArray colors, boolean dark) {
        if (!enabled() || colors == null) {
            return;
        }
        final int accent = accentColor(colors);

        // ---- Поверхности и текст ----
        put(colors, Theme.key_windowBackgroundWhite, dark ? DARK_BG : LIGHT_BG);
        put(colors, Theme.key_windowBackgroundGray, dark ? DARK_BG : LIGHT_GRAY_BG);
        put(colors, Theme.key_graySection, dark ? DARK_BG : LIGHT_BG);
        put(colors, Theme.key_dialogBackground, dark ? DARK_SURFACE : LIGHT_SURFACE);
        put(colors, Theme.key_dialogBackgroundGray, dark ? DARK_BG : LIGHT_GRAY_BG);
        put(colors, Theme.key_actionBarDefault, dark ? DARK_BG : LIGHT_BG);
        put(colors, Theme.key_actionBarDefaultTitle, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_actionBarDefaultSubtitle, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_actionBarDefaultIcon, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_windowBackgroundWhiteBlackText, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_dialogTextBlack, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_windowBackgroundWhiteGrayText, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_windowBackgroundWhiteGrayText2, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_windowBackgroundWhiteGrayText3, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_windowBackgroundWhiteGrayText4, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_windowBackgroundWhiteGrayText5, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_windowBackgroundWhiteGrayText6, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_windowBackgroundWhiteGrayText7, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_windowBackgroundWhiteGrayText8, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_dialogTextGray, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_dialogTextGray2, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_dialogTextGray3, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_dialogTextGray4, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_graySectionText, dark ? DARK_MUTED : LIGHT_MUTED);
        put(colors, Theme.key_chats_name, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_chats_nameMessage, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_chats_nameMessage_threeLines, dark ? DARK_TEXT : LIGHT_TEXT);
        put(colors, Theme.key_chats_message, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_chats_message_threeLines, dark ? DARK_SECONDARY : LIGHT_SECONDARY);
        put(colors, Theme.key_chats_date, dark ? DARK_MUTED : LIGHT_MUTED);

        // ---- Чат: плоский фон и пузыри Yougram ----
        final int chatBg = dark ? DARK_CHAT_BG : LIGHT_CHAT_BG;
        put(colors, Theme.key_chat_wallpaper, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to1, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to2, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to3, chatBg);

        final int inBubble = dark ? DARK_IN_BUBBLE : LIGHT_IN_BUBBLE;
        final int outBubble = lerp(
                dark ? DARK_OUT_BUBBLE_BASE : LIGHT_OUT_BUBBLE_BASE, accent,
                dark ? 0.12f : 0.10f);
        put(colors, Theme.key_chat_inBubble, inBubble);
        put(colors, Theme.key_chat_outBubble, outBubble);
        // плоский пузырь вместо градиентного
        put(colors, Theme.key_chat_outBubbleGradient1, outBubble);
        put(colors, Theme.key_chat_outBubbleGradient2, outBubble);
        put(colors, Theme.key_chat_outBubbleGradient3, outBubble);
        put(colors, Theme.key_chat_outBubbleGradientAnimated, 0);

        // пересчитать selected-варианты поверх новых пузырей (fillAccentColors делал это со старыми)
        put(colors, Theme.key_chat_inBubbleSelected,
                Theme.blendOver(inBubble, colors.get(Theme.key_chat_inBubbleSelectedOverlay, 0x14000000)));
        put(colors, Theme.key_chat_outBubbleSelected,
                Theme.blendOver(outBubble, colors.get(Theme.key_chat_outBubbleSelectedOverlay, 0x14000000)));

        // контраст текста: на тёмных пузырях — всегда белый, на светлых — почти чёрный
        final int bubbleText = dark ? 0xFFFFFFFF : 0xFF1A1A1C;
        final int bubbleMeta = dark ? 0x99FFFFFF : 0x96000000;
        put(colors, Theme.key_chat_messageTextIn, bubbleText);
        put(colors, Theme.key_chat_messageTextOut, bubbleText);
        put(colors, Theme.key_chat_inTimeText, bubbleMeta);
        put(colors, Theme.key_chat_outTimeText, bubbleMeta);
        put(colors, Theme.key_chat_inReplyNameText, accent);
        put(colors, Theme.key_chat_outReplyNameText, dark ? 0xFFFFFFFF : 0xFF1A1A1C);
        put(colors, Theme.key_chat_outSentCheck, bubbleMeta);
        put(colors, Theme.key_chat_outSentCheckRead, bubbleText);
    }

    private static void put(SparseIntArray colors, int key, int value) {
        colors.put(key, value);
    }

    private static int accentColor(SparseIntArray colors) {
        int accent = colors.get(Theme.key_windowBackgroundWhiteBlueText, 0);
        if (accent == 0 || (accent & 0xff000000) == 0) {
            accent = 0xFF3F6FD8;
        }
        return accent;
    }

    private static int lerp(int from, int to, float amount) {
        final int a = Math.round((from >> 16 & 0xff) * (1f - amount) + (to >> 16 & 0xff) * amount);
        final int b = Math.round((from >> 8 & 0xff) * (1f - amount) + (to >> 8 & 0xff) * amount);
        final int c = Math.round((from & 0xff) * (1f - amount) + (to & 0xff) * amount);
        return 0xff000000 | (a << 16) | (b << 8) | c;
    }
}
