package app.exteraless.appearance;

import android.util.SparseIntArray;

import androidx.core.graphics.ColorUtils;

import org.telegram.ui.ActionBar.Theme;

/**
 * Yougram Expressive — перенос дизайн-языка Yougram (Material 3 Expressive glass)
 * на View-интерфейс форка.
 *
 * Tonal Theme Engine:
 * - Повністю адаптується до поточної теми користувача (темно-рожева, Monet, темна, світла тощо).
 * - Якщо тема має власний насичений відтінок (наприклад, темно-рожевий) — поверхні, фон чату,
 *   бульбашки та скляна панель насичуються тональними відтінками теми, а не перетворюються на сірий.
 * - Чисті скруглені бульбашки (радіус 24dp, без хвостиків).
 * - Скляна пігулка нижніх вкладок та верхньої панелі з адаптивним тонуванням.
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

    // ---- Список чатів Yumi ----

    public static int chatListStyle() {
        return enabled() ? AppearanceConfig.chatListStyle() : AppearanceConfig.CHAT_LIST_STYLE_FLAT;
    }

    public static boolean chatPinnedAccent() {
        return enabled() && AppearanceConfig.chatPinnedAccent();
    }

    public static boolean chatSquircleAvatars() {
        return enabled() && AppearanceConfig.chatSquircleAvatars();
    }

    public static boolean chatCardOutline() {
        return enabled() && AppearanceConfig.chatCardOutline();
    }

    // ---- Фон чата ----

    private static volatile int currentChatBg = 0;
    private static android.graphics.drawable.ColorDrawable cachedWallpaper;

    /**
     * Плоский фон чата в тональній палітрі активної теми. Theme.getCachedWallpaperNonBlocking
     * отдаёт этот drawable целиком; ColorDrawable явно поддержан во всех
     * потребителях (WallpaperBitmapProvider, calcDrawableColor, setBackgroundImage).
     */
    public static android.graphics.drawable.Drawable chatWallpaper(boolean dark) {
        final int color = currentChatBg != 0 ? currentChatBg : (dark ? DARK_CHAT_BG : LIGHT_CHAT_BG);
        if (cachedWallpaper == null) {
            cachedWallpaper = new android.graphics.drawable.ColorDrawable(color);
        } else if (cachedWallpaper.getColor() != color) {
            cachedWallpaper.setColor(color);
        }
        return cachedWallpaper;
    }

    // ---- Базові нейтральні палітри для розрахунку тональних поверхонь ----

    private static final int DARK_BG = 0xFF121214;
    private static final int DARK_SURFACE = 0xFF1C1D21;
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
     * Застосовує палітру Yougram Expressive поверх поточної теми з тональною адаптацією.
     * Якщо у користувача активна кольорова тема (наприклад, темно-рожева), кольори поверхонь,
     * чату та скла підтягують відтінок теми замість того, щоб ставати холодним сірим.
     */
    public static void applyOverrides(SparseIntArray colors, boolean dark) {
        if (!enabled() || colors == null) {
            return;
        }
        final int accent = accentColor(colors);

        int origBg = colors.get(Theme.key_windowBackgroundGray, 0);
        int origSurface = colors.get(Theme.key_windowBackgroundWhite, 0);

        float[] surfHsl = new float[3];
        if (origSurface != 0) {
            ColorUtils.colorToHSL(origSurface, surfHsl);
        }
        float[] bgHsl = new float[3];
        if (origBg != 0) {
            ColorUtils.colorToHSL(origBg, bgHsl);
        }
        float[] accentHsl = new float[3];
        ColorUtils.colorToHSL(accent, accentHsl);

        final int surface;
        final int bg;
        final int chatBg;
        final int inBubble;
        final int outBubble;

        boolean hasCustomColorTheme = (surfHsl[1] > 0.04f) || (bgHsl[1] > 0.04f);

        if (dark) {
            if (hasCustomColorTheme) {
                // Тема вже має власний насичений відтінок (наприклад, темно-рожевий) —
                // зберігаємо оригінальні кольори теми, щоб не робити інтерфейс сірим!
                surface = (origSurface != 0 && surfHsl[1] > 0.04f) ? origSurface : lerp(DARK_SURFACE, accent, 0.20f);
                bg = (origBg != 0 && bgHsl[1] > 0.04f) ? origBg : (origBg != 0 ? origBg : lerp(surface, 0xFF000000, 0.15f));
                int origChat = colors.get(Theme.key_chat_wallpaper, 0);
                chatBg = (origChat != 0 && (origChat & 0xFF000000) != 0) ? origChat : bg;
                int origIn = colors.get(Theme.key_chat_inBubble, 0);
                inBubble = (origIn != 0 && (origIn & 0xFF000000) != 0) ? origIn : lerp(surface, accent, 0.14f);
                int origOut = colors.get(Theme.key_chat_outBubble, 0);
                outBubble = (origOut != 0 && (origOut & 0xFF000000) != 0) ? origOut : lerp(DARK_OUT_BUBBLE_BASE, accent, 0.45f);
            } else if (accentHsl[1] > 0.10f) {
                // Тональна M3 елевація за акцентом теми (рожевий, Monet, тощо)
                bg = lerp(DARK_BG, accent, 0.12f);
                surface = lerp(DARK_SURFACE, accent, 0.16f);
                chatBg = lerp(DARK_CHAT_BG, accent, 0.12f);
                inBubble = lerp(DARK_IN_BUBBLE, accent, 0.16f);
                outBubble = lerp(DARK_OUT_BUBBLE_BASE, accent, 0.42f);
            } else {
                // Монохромна темна тема
                bg = DARK_BG;
                surface = DARK_SURFACE;
                chatBg = DARK_CHAT_BG;
                inBubble = DARK_IN_BUBBLE;
                outBubble = DARK_OUT_BUBBLE_BASE;
            }
        } else {
            if (hasCustomColorTheme) {
                surface = (origSurface != 0 && surfHsl[1] > 0.04f) ? origSurface : LIGHT_SURFACE;
                bg = (origBg != 0 && bgHsl[1] > 0.04f) ? origBg : (origBg != 0 ? origBg : LIGHT_GRAY_BG);
                int origChat = colors.get(Theme.key_chat_wallpaper, 0);
                chatBg = (origChat != 0 && (origChat & 0xFF000000) != 0) ? origChat : bg;
                int origIn = colors.get(Theme.key_chat_inBubble, 0);
                inBubble = (origIn != 0 && (origIn & 0xFF000000) != 0) ? origIn : lerp(surface, accent, 0.10f);
                int origOut = colors.get(Theme.key_chat_outBubble, 0);
                outBubble = (origOut != 0 && (origOut & 0xFF000000) != 0) ? origOut : lerp(LIGHT_OUT_BUBBLE_BASE, accent, 0.35f);
            } else if (accentHsl[1] > 0.10f) {
                bg = lerp(LIGHT_BG, accent, 0.04f);
                surface = LIGHT_SURFACE;
                chatBg = lerp(LIGHT_CHAT_BG, accent, 0.06f);
                inBubble = lerp(LIGHT_IN_BUBBLE, accent, 0.10f);
                outBubble = lerp(LIGHT_OUT_BUBBLE_BASE, accent, 0.35f);
            } else {
                bg = LIGHT_BG;
                surface = LIGHT_SURFACE;
                chatBg = LIGHT_CHAT_BG;
                inBubble = LIGHT_IN_BUBBLE;
                outBubble = LIGHT_OUT_BUBBLE_BASE;
            }
        }

        currentChatBg = chatBg;

        // ---- Поверхні і текст ----
        put(colors, Theme.key_windowBackgroundWhite, surface);
        put(colors, Theme.key_windowBackgroundGray, bg);
        put(colors, Theme.key_graySection, bg);
        put(colors, Theme.key_dialogBackground, surface);
        put(colors, Theme.key_dialogBackgroundGray, bg);

        int origActionBar = colors.get(Theme.key_actionBarDefault, 0);
        float[] abHsl = new float[3];
        if (origActionBar != 0) {
            ColorUtils.colorToHSL(origActionBar, abHsl);
        }
        int actionBarColor = (origActionBar != 0 && abHsl[1] > 0.04f) ? origActionBar : bg;
        put(colors, Theme.key_actionBarDefault, actionBarColor);
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

        // ---- Скло: пігулка вкладок і панелі з легким тонуванням кольором теми ----
        final int glassSurface = dark
                ? Theme.blendOver(0xD9000000 | (surface & 0x00FFFFFF), Theme.multAlpha(accent, 0.16f))
                : Theme.blendOver(0xE6FCFCFC, Theme.multAlpha(accent, 0.08f));
        put(colors, Theme.key_glass_targetMainTabs, glassSurface);
        put(colors, Theme.key_glass_targetMainTopPanel, glassSurface);

        // Селектор виділеної вкладки: сяє акцентом теми (наприклад, рожевим)
        put(colors, Theme.key_glass_tabSelected, Theme.multAlpha(accent, dark ? 0.28f : 0.20f));
        put(colors, Theme.key_glass_tabSelectedText, dark ? 0xFFFFFFFF : 0xFF111111);
        put(colors, Theme.key_glass_defaultIcon, dark ? lerp(0xFFD0D0D0, accent, 0.12f) : lerp(0xFF444444, accent, 0.12f));
        put(colors, Theme.key_glass_defaultText, dark ? lerp(0xFFC4C4C4, accent, 0.12f) : lerp(0xFF555555, accent, 0.12f));

        // ---- Чат: тональний фон і виразні бульбашки Yougram ----
        put(colors, Theme.key_chat_wallpaper, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to1, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to2, chatBg);
        put(colors, Theme.key_chat_wallpaper_gradient_to3, chatBg);

        put(colors, Theme.key_chat_inBubble, inBubble);
        put(colors, Theme.key_chat_outBubble, outBubble);
        put(colors, Theme.key_chat_outBubbleGradient1, outBubble);
        put(colors, Theme.key_chat_outBubbleGradient2, outBubble);
        put(colors, Theme.key_chat_outBubbleGradient3, outBubble);
        put(colors, Theme.key_chat_outBubbleGradientAnimated, 0);

        put(colors, Theme.key_chat_inBubbleSelected,
                Theme.blendOver(inBubble, colors.get(Theme.key_chat_inBubbleSelectedOverlay, 0x14000000)));
        put(colors, Theme.key_chat_outBubbleSelected,
                Theme.blendOver(outBubble, colors.get(Theme.key_chat_outBubbleSelectedOverlay, 0x14000000)));

        // Контраст тексту
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
            accent = colors.get(Theme.key_chats_actionBackground, 0);
        }
        if (accent == 0 || (accent & 0xff000000) == 0) {
            accent = colors.get(Theme.key_featuredStickers_addButton, 0);
        }
        if (accent == 0 || (accent & 0xff000000) == 0) {
            accent = 0xFFE5486B; // Yumi soft pink default
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
