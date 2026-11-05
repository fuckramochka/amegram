package com.amegram.mods.ui;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Interpolator;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import app.amegram.hot.api.HotHook;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotUi;

/**
 * Yumi Expressive visual layer: independent chat cards, tonal navigation,
 * custom list-screen chrome and restrained spring feedback.
 */
public class UiModule implements HotModule, HotUi {

    private static final String NEKO_PREFS = "nkmrcfg";

    // AppearanceConfig keys
    private static final String KEY_STYLE = "OEAppearanceUiStyleMode";
    private static final String KEY_TOUCHED = "OEAppearanceUiStyleModeTouched";
    private static final String KEY_SQUARE_FAB = "OEAppearanceSquareFab";
    private static final String KEY_GLASS_MENU = "OEAppearanceGlassMessageMenu";
    private static final String KEY_MINI_PLAYER = "OEAppearanceMd3MiniPlayer";
    private static final String KEY_LOADING = "OEAppearanceNewLoadingStyle";
    public static final String KEY_YUMI_LOGIN = "ui_yumi_login";
    public static final String KEY_PASSCODE_MUSIC = "ui_yumi_passcode_music";

    // Chat list display keys
    public static final String KEY_CHAT_STYLE = "OEAppearanceChatListStyle";
    public static final String KEY_CHAT_PINNED_ACCENT = "OEAppearanceChatPinnedAccent";
    public static final String KEY_CHAT_SQUIRCLE_AVATARS = "OEAppearanceChatSquircleAvatars";
    public static final String KEY_CHAT_CARD_OUTLINE = "OEAppearanceChatCardOutline";
    public static final String KEY_CHAT_CARDS_TOUCH = "OEAppearanceChatCardsTouch";

    public static final int CHAT_STYLE_CARDS = 0;
    public static final int CHAT_STYLE_ISLANDS = 1;
    public static final int CHAT_STYLE_FLAT = 2;

    // Sticky animations configuration keys
    public static final String KEY_STICKY_ENABLED = "OEAppearanceStickyAnimations";
    public static final String KEY_STICKY_THEME_ADAPTIVE = "OEAppearanceStickyThemeAdaptive";
    public static final String KEY_STICKY_STYLE = "OEAppearanceStickyStyle"; // 0 = Subtle, 1 = Expressive, 2 = Ultra Bouncy

    // Custom accent modes (0: Авто Тема TG, 1: Сакура, 2: Синій, 3: Смарагд, 4: Лаванда, 5: Захід Сонця)
    public static final String KEY_ACCENT_MODE = "ui_accent_mode";

    // Geometry and dimensions
    public static final String KEY_CARD_RADIUS = "ui_card_radius";
    public static final String KEY_CARD_MARGIN_SIDE = "ui_card_margin_side";
    public static final String KEY_CARD_MARGIN_V = "ui_card_margin_v";
    public static final String KEY_NAV_RADIUS = "ui_nav_radius";
    public static final String KEY_NAV_HEIGHT = "ui_nav_height";

    // Placement and visibility
    public static final String KEY_TIME_BADGE_STYLE = "ui_time_badge_style";
    public static final String KEY_NAV_EXPAND_PILL = "ui_nav_expand_pill";
    public static final String KEY_NAV_FLOATING = "ui_nav_floating";
    public static final String KEY_BLUR_FOLDERS = "ui_blur_folders";
    public static final String KEY_BLUR_NAV = "ui_blur_nav";

    // Physics modes
    public static final int STYLE_SUBTLE = 0;
    public static final int STYLE_EXPRESSIVE = 1;
    public static final int STYLE_ULTRA_BOUNCY = 2;

    private HotHost host;
    private YumiNavController navController;
    private YumiChatRowRenderer chatRowRenderer;
    private YumiScreenChrome screenChrome;
    private final List<HotHook.Unhook> activeHooks = new ArrayList<>();
    private final Set<View> animatedViews = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    // Theme state
    private volatile boolean isThemeDark = false;
    private volatile boolean isThemeMonet = false;
    private volatile boolean isThemeAmoled = false;

    // Pre-computed physics parameters (швидкі, зібрані, без желе-ефекту)
    private volatile float currentDamping = 12.0f;
    private volatile float currentFreq = 10.5f;
    private volatile long currentDuration = 190L;
    private volatile float currentButtonPressScale = 0.965f;
    private volatile float currentCardPressScale = 0.985f;

    // Cached pre-allocated interpolators
    private volatile Interpolator cachedSpringInterpolator;
    private final Interpolator cachedCompressInterpolator = new StickyCompressInterpolator();
    private final Interpolator cachedSheetInterpolator = new SmoothSettleInterpolator();

    public UiModule() {
        rebuildPhysicsParameters();
    }

    @Override
    public String moduleId() {
        return "ui";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        try {
            host.registerService(HotServices.UI, this);
        } catch (Throwable ignore) {
        }
        navController = new YumiNavController(this, host);
        chatRowRenderer = new YumiChatRowRenderer(this, host);
        screenChrome = new YumiScreenChrome(this, host);
        updateThemeState();
        setupThemeListener();
        setupHooks();
        try {
            host.log("ui v1.0.6 attached with MD3 Expressive navigation, chat cards and chrome");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void onDetach() {
        try {
            if (host != null) {
                host.unregisterService(HotServices.UI);
            }
        } catch (Throwable ignore) {
        }
        for (HotHook.Unhook u : activeHooks) {
            try {
                u.unhook();
            } catch (Throwable ignore) {
            }
        }
        activeHooks.clear();
        try {
            if (navController != null) {
                navController.restore();
                navController = null;
            }
        } catch (Throwable ignore) {
        }
        chatRowRenderer = null;
        try {
            if (screenChrome != null) {
                screenChrome.restoreAll();
                screenChrome = null;
            }
        } catch (Throwable ignore) {
        }
        synchronized (animatedViews) {
            animatedViews.clear();
        }
        host = null;
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return t("Інтерфейс Yumi", "Интерфейс Yumi", "Yumi Interface");
    }

    private static String t(String uk, String ru, String en) {
        return YumiText.tr(uk, ru, en);
    }

    private SharedPreferences prefs() {
        try {
            Context ctx = host != null ? host.context() : null;
            if (ctx != null) return ctx.getSharedPreferences(NEKO_PREFS, Context.MODE_PRIVATE);
        } catch (Throwable ignore) {
        }
        return null;
    }

    private int readInt(String key, int def) {
        try {
            if (host != null) return host.getInt(key, def);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) return p.getInt(key, def);
        } catch (Throwable ignore) {
        }
        return def;
    }

    private boolean readBool(String key, boolean def) {
        try {
            if (host != null) return host.getBool(key, def);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) return p.getBoolean(key, def);
        } catch (Throwable ignore) {
        }
        return def;
    }

    private void writeInt(String key, int value) {
        try {
            if (host != null) host.putInt(key, value);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) p.edit().putInt(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private void writeBool(String key, boolean value) {
        try {
            if (host != null) host.putBool(key, value);
        } catch (Throwable ignore) {
        }
        try {
            SharedPreferences p = prefs();
            if (p != null) p.edit().putBoolean(key, value).apply();
        } catch (Throwable ignore) {
        }
    }

    private void markTouched() {
        writeBool(KEY_TOUCHED, true);
        try {
            if (host != null) {
                host.emit("ui_changed", "{\"style\":" + styleMode() + ",\"chatStyle\":" + chatListStyle() + ",\"sticky\":" + isStickyEnabled() + "}");
            }
        } catch (Throwable ignore) {
        }
        try {
            ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
            Class<?> ncClass = Class.forName("org.telegram.messenger.NotificationCenter", true, cl);
            Method mGetGlobal = ncClass.getMethod("getGlobalInstance");
            Object nc = mGetGlobal.invoke(null);
            int updateInterfaces = ncClass.getField("updateInterfaces").getInt(null);
            Method mPost = ncClass.getMethod("postNotificationName", int.class, Object[].class);
            mPost.invoke(nc, updateInterfaces, new Object[]{127});
        } catch (Throwable ignore) {
        }
    }

    @Override
    public int styleMode() {
        int v = readInt(KEY_STYLE, readInt("style", 1));
        return v == 0 ? 0 : 1;
    }

    public int chatListStyle() {
        return readInt(KEY_CHAT_STYLE, CHAT_STYLE_FLAT);
    }

    public boolean isChatPinnedAccent() {
        return readBool(KEY_CHAT_PINNED_ACCENT, true);
    }

    public boolean isYumiLogin() {
        return readBool(KEY_YUMI_LOGIN, true);
    }

    public boolean isPasscodeMusicEnabled() {
        return readBool(KEY_PASSCODE_MUSIC, true);
    }

    public boolean isChatSquircleAvatars() {
        return readBool(KEY_CHAT_SQUIRCLE_AVATARS, true);
    }

    public boolean isChatCardOutline() {
        return readBool(KEY_CHAT_CARD_OUTLINE, true);
    }

    public boolean isChatCardsTouch() {
        return readBool(KEY_CHAT_CARDS_TOUCH, true);
    }

    public int accentMode() {
        return readInt(KEY_ACCENT_MODE, 0);
    }

    public int getCustomAccentColor(boolean dark) {
        int mode = accentMode();
        switch (mode) {
            case 1: return dark ? 0xFFFF8FB7 : 0xFFB83268; // Sakura Pink
            case 2: return dark ? 0xFF8AB4F8 : 0xFF2A5BB8; // Sky Blue
            case 3: return dark ? 0xFF89E3AC : 0xFF127240; // Mint Emerald
            case 4: return dark ? 0xFFC8B8FF : 0xFF6544C4; // Lavender Violet
            case 5: return dark ? 0xFFFFBE8C : 0xFFA93E0B; // Sunset Coral
            default: return 0; // 0 = Auto Telegram theme
        }
    }

    public float cardRadius() {
        return readInt(KEY_CARD_RADIUS, 22);
    }

    public float cardSideMargin() {
        return readInt(KEY_CARD_MARGIN_SIDE, 12);
    }

    public float cardVerticalMargin() {
        int v = readInt(KEY_CARD_MARGIN_V, 35);
        return v / 10f;
    }

    public float navRadius() {
        return readInt(KEY_NAV_RADIUS, 24);
    }

    public float navHeight() {
        return readInt(KEY_NAV_HEIGHT, 58);
    }

    public boolean isTimePillEnabled() {
        return readInt(KEY_TIME_BADGE_STYLE, 0) == 0;
    }

    public boolean isNavExpandActivePillEnabled() {
        return readBool(KEY_NAV_EXPAND_PILL, true);
    }

    public boolean isNavFloating() {
        return readBool(KEY_NAV_FLOATING, true);
    }

    public boolean isBlurFoldersEnabled() {
        return readBool(KEY_BLUR_FOLDERS, false);
    }

    public boolean isBlurNavEnabled() {
        return readBool(KEY_BLUR_NAV, false);
    }

    @Override
    public int getColor(String token, int fallback) {
        if (chatRowRenderer != null && chatRowRenderer.palette != null) {
            return chatRowRenderer.palette.getColorToken(token, fallback);
        }
        return fallback;
    }

    @Override
    public float getDimension(String token, float fallbackDp) {
        if (HotUi.DIMEN_CARD_RADIUS.equals(token) || "card_radius".equals(token) || "radius_l".equals(token)) {
            return cardRadius();
        }
        if (HotUi.DIMEN_CARD_MARGIN_SIDE.equals(token) || "card_margin_side".equals(token)) {
            return cardSideMargin();
        }
        if (HotUi.DIMEN_CARD_MARGIN_V.equals(token) || "card_margin_v".equals(token)) {
            return cardVerticalMargin();
        }
        if (HotUi.DIMEN_NAV_RADIUS.equals(token) || "nav_radius".equals(token)) {
            return navRadius();
        }
        if (HotUi.DIMEN_NAV_HEIGHT.equals(token) || "nav_height".equals(token)) {
            return navHeight();
        }
        return fallbackDp;
    }

    @Override
    public int getPlacement(String token, int fallback) {
        if (HotUi.PLACEMENT_TIME_BADGE_STYLE.equals(token) || "time_badge_style".equals(token)) {
            return isTimePillEnabled() ? 0 : 1;
        }
        if (HotUi.PLACEMENT_NAV_EXPAND_PILL.equals(token) || "nav_expand_pill".equals(token)) {
            return isNavExpandActivePillEnabled() ? 1 : 0;
        }
        if (HotUi.PLACEMENT_NAV_FLOATING.equals(token) || "nav_floating".equals(token)) {
            return isNavFloating() ? 1 : 0;
        }
        if (HotUi.PLACEMENT_BLUR_FOLDERS.equals(token) || "blur_folders".equals(token)) {
            return isBlurFoldersEnabled() ? 1 : 0;
        }
        if (HotUi.PLACEMENT_BLUR_NAV.equals(token) || "blur_nav".equals(token)) {
            return isBlurNavEnabled() ? 1 : 0;
        }
        return fallback;
    }

    @Override
    public boolean isPlacementEnabled(String token, boolean fallback) {
        int v = getPlacement(token, fallback ? 1 : 0);
        return v != 0;
    }

    @Override
    public View createSlotView(String slotId, Context context, Object... args) {
        return null;
    }

    public boolean isStickyEnabled() {
        return readBool(KEY_STICKY_ENABLED, true);
    }

    public boolean isThemeAdaptive() {
        return readBool(KEY_STICKY_THEME_ADAPTIVE, true);
    }

    public int stickyStyle() {
        return readInt(KEY_STICKY_STYLE, STYLE_EXPRESSIVE);
    }

    // ==================== Dynamic Theme Adaptation ====================

    private void setupThemeListener() {
        if (host == null) return;
        try {
            ClassLoader cl = host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
            Class<?> ncClass = Class.forName("org.telegram.messenger.NotificationCenter", true, cl);
            HotHost.NotificationObserver observer = new HotHost.NotificationObserver() {
                @Override
                public void onNotification(int id, int account, Object... args) {
                    updateThemeState();
                }
            };

            int didSetNewTheme = ncClass.getField("didSetNewTheme").getInt(null);
            host.subscribeNotification(didSetNewTheme, observer);

            try {
                int didApplyNewTheme = ncClass.getField("didApplyNewTheme").getInt(null);
                host.subscribeNotification(didApplyNewTheme, observer);
            } catch (Throwable ignore) {
            }

            try {
                int needSetDayNightTheme = ncClass.getField("needSetDayNightTheme").getInt(null);
                host.subscribeNotification(needSetDayNightTheme, observer);
            } catch (Throwable ignore) {
            }

            try {
                int themeListUpdated = ncClass.getField("themeListUpdated").getInt(null);
                host.subscribeNotification(themeListUpdated, observer);
            } catch (Throwable ignore) {
            }

            try {
                int reloadInterface = ncClass.getField("reloadInterface").getInt(null);
                host.subscribeNotification(reloadInterface, observer);
            } catch (Throwable ignore) {
            }

            host.log("ui: subscribed to Telegram theme changes");
        } catch (Throwable t) {
            if (host != null) host.log("ui: failed to subscribe to theme notifications: " + t.getMessage());
        }
    }

    private void updateThemeState() {
        try {
            ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme", true, cl);

            Method mDark = themeClass.getMethod("isCurrentThemeDark");
            isThemeDark = (Boolean) mDark.invoke(null);

            try {
                Method mMonet = themeClass.getMethod("isCurrentThemeMonet");
                isThemeMonet = (Boolean) mMonet.invoke(null);
            } catch (Throwable ignore) {
                isThemeMonet = false;
            }

            try {
                Field fTheme = themeClass.getDeclaredField("currentTheme");
                fTheme.setAccessible(true);
                Object curTheme = fTheme.get(null);
                if (curTheme != null) {
                    try {
                        Method mAmoled = curTheme.getClass().getMethod("isAmoled");
                        isThemeAmoled = (Boolean) mAmoled.invoke(curTheme);
                    } catch (Throwable ignore) {
                        isThemeAmoled = false;
                    }
                }
            } catch (Throwable ignore) {
                isThemeAmoled = false;
            }

            // Invalidate YumiMd3 cache so it recomputes colors from current theme
            try {
                Class<?> md3Class = Class.forName("app.amegram.theme.YumiMd3", true, cl);
                md3Class.getMethod("invalidateCache").invoke(null);
            } catch (Throwable ignore) {
            }

            rebuildPhysicsParameters();
            Context ctx = host != null ? host.context() : null;
            if (chatRowRenderer != null) {
                chatRowRenderer.refreshPalette(ctx);
                chatRowRenderer.invalidateAll();
            }
            if (navController != null) {
                navController.refreshPalette(ctx);
            }
            if (screenChrome != null) {
                screenChrome.refreshPalette(ctx);
            }
            if (host != null) {
                host.log("ui: theme updated -> dark=" + isThemeDark + ", monet=" + isThemeMonet + ", amoled=" + isThemeAmoled);
            }
        } catch (Throwable t) {
            if (host != null) host.log("ui: updateThemeState error: " + t.getMessage());
        }
    }

    private void rebuildPhysicsParameters() {
        if (isThemeAdaptive()) {
            if (isThemeMonet) {
                // Pixel Material 3 Expressive (чистий стиль Pixel: овершут ~2.2%, 190ms)
                currentDamping = 12.0f;
                currentFreq = 10.5f;
                currentDuration = 190L;
                currentButtonPressScale = 0.965f;
                currentCardPressScale = 0.985f;
            } else if (isThemeAmoled) {
                // AMOLED Pure Black: надчіткий миттєвий відгук (170ms, нуль змазування OLED)
                currentDamping = 13.5f;
                currentFreq = 11.0f;
                currentDuration = 170L;
                currentButtonPressScale = 0.970f;
                currentCardPressScale = 0.987f;
            } else if (isThemeDark) {
                // Темна тема: преміальний швидкий тактильний відгук (180ms)
                currentDamping = 12.5f;
                currentFreq = 10.8f;
                currentDuration = 180L;
                currentButtonPressScale = 0.965f;
                currentCardPressScale = 0.985f;
            } else {
                // Світла тема: м'який зібраний спрінг (200ms)
                currentDamping = 11.5f;
                currentFreq = 10.0f;
                currentDuration = 200L;
                currentButtonPressScale = 0.965f;
                currentCardPressScale = 0.985f;
            }
        } else {
            switch (stickyStyle()) {
                case STYLE_SUBTLE:
                    currentDamping = 14.0f;
                    currentFreq = 11.5f;
                    currentDuration = 160L;
                    currentButtonPressScale = 0.975f;
                    currentCardPressScale = 0.988f;
                    break;
                case STYLE_ULTRA_BOUNCY:
                    currentDamping = 10.5f;
                    currentFreq = 9.5f;
                    currentDuration = 230L;
                    currentButtonPressScale = 0.950f;
                    currentCardPressScale = 0.980f;
                    break;
                case STYLE_EXPRESSIVE:
                default:
                    currentDamping = 12.0f;
                    currentFreq = 10.5f;
                    currentDuration = 190L;
                    currentButtonPressScale = 0.965f;
                    currentCardPressScale = 0.985f;
                    break;
            }
        }
        cachedSpringInterpolator = new DampedSpringInterpolator(currentDamping, currentFreq);
    }

    private String getThemeSummary() {
        if (isThemeMonet) return "Material You Monet";
        if (isThemeAmoled) return "AMOLED Pure Black";
        if (isThemeDark) return t("Темна тема", "Темная тема", "Dark Theme");
        return t("Світла тема", "Светлая тема", "Light Theme");
    }

    private String getAdaptiveProfileDescription() {
        if (isThemeMonet) return t("Pixel M3 Expressive (динамічні кольори Monet, 190ms)", "Pixel M3 Expressive (динамические цвета Monet, 190ms)", "Pixel M3 Expressive (Monet dynamic colors, 190ms)");
        if (isThemeAmoled) return t("Ультра-чіткий відгук (170ms, без OLED ghosting)", "Ультра-четкий отклик (170ms, без OLED ghosting)", "Ultra-crisp response (170ms, no OLED ghosting)");
        if (isThemeDark) return t("Преміальний швидкий відгук (180ms, темна фізика)", "Премиальный быстрый отклик (180ms, темная физика)", "Premium fast response (180ms, dark physics)");
        return t("Органічний текучий спрінг (200ms, тактильний мікро-овершут)", "Органический текучий спринг (200ms, тактильный микро-овершут)", "Organic fluid spring (200ms, tactile micro-overshoot)");
    }

    // ==================== Optimized Spring Physics ====================

    /**
     * Фізика затухаючого гармонійного осцилятора (збалансована, без желе-ефекту).
     */
    public static class DampedSpringInterpolator implements Interpolator {
        private final float damping;
        private final float freq;

        public DampedSpringInterpolator(float damping, float freq) {
            this.damping = damping;
            this.freq = freq;
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            float decay = (float) Math.exp(-damping * t);
            return 1f - decay * ((float) Math.cos(freq * t) + (damping / (2f * freq)) * (float) Math.sin(freq * t));
        }
    }

    /** Швидка крива стискання під пальцем (квадратичний ease-out). */
    public static class StickyCompressInterpolator implements Interpolator {
        @Override
        public float getInterpolation(float t) {
            float inv = 1f - t;
            return 1f - inv * inv;
        }
    }

    /** Плавне доведення для шторок (Quartic Ease-Out — швидкий старт і шовкова зупинка без відскоку). */
    public static class SmoothSettleInterpolator implements Interpolator {
        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            float inv = 1f - t;
            return 1f - inv * inv * inv * inv;
        }
    }

    /**
     * Перевірка: чи є елемент інтерактивною кнопкою / іконкою / чіпом.
     * Списки (чати, повідомлення, налаштування) та великі контейнери обробляються окремими хуками.
     */
    private static boolean isInteractiveButton(View v) {
        if (v == null) return false;
        try {
            Object parent = v.getParent();
            while (parent instanceof View) {
                String pName = parent.getClass().getName();
                if (pName.contains("Recycler") || pName.contains("ListView") || pName.contains("Row")) {
                    return false;
                }
                parent = ((View) parent).getParent();
            }

            String name = v.getClass().getName();
            if (name.contains("Cell") || name.contains("Row") || name.contains("Message")
                    || name.contains("Recycler") || name.contains("Activity")
                    || name.contains("Content") || name.contains("Container") || name.contains("Pager")) {
                return false;
            }

            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                return false;
            }

            if (name.contains("Button") || name.contains("Fab") || name.contains("FAB")
                    || name.contains("Chip") || name.contains("MenuItem")
                    || name.contains("PlayPause") || name.contains("GlassTabView")
                    || name.contains("ActionView")) {
                return true;
            }

            if (v instanceof android.widget.Button || v instanceof android.widget.ImageButton || v instanceof android.widget.ImageView) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * Підключення нативного StateListAnimator до кнопки з точною фізикою та вібровідгуком.
     */
    void applySpringStateListAnimator(final View v) {
        if (v == null || !isStickyEnabled()) return;
        synchronized (animatedViews) {
            if (animatedViews.contains(v)) return;
            animatedViews.add(v);
        }

        try {
            StateListAnimator sla = new StateListAnimator();

            // 1. Стан натискання (state_pressed): швидке стискання 60ms + тактильний клік
            AnimatorSet pressedSet = new AnimatorSet();
            ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(v, View.SCALE_X, currentButtonPressScale);
            ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(v, View.SCALE_Y, currentButtonPressScale);
            pressedSet.playTogether(scaleXDown, scaleYDown);
            pressedSet.setDuration(60L);
            pressedSet.setInterpolator(cachedCompressInterpolator);
            pressedSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    try {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    } catch (Throwable ignore) {
                    }
                }
            });

            // 2. Стан відпускання: природне пружинне повернення (190ms)
            AnimatorSet releaseSet = new AnimatorSet();
            ObjectAnimator scaleXUp = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f);
            ObjectAnimator scaleYUp = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f);
            releaseSet.playTogether(scaleXUp, scaleYUp);
            releaseSet.setDuration(currentDuration);
            Interpolator springInterp = cachedSpringInterpolator;
            if (springInterp == null) {
                springInterp = new DampedSpringInterpolator(currentDamping, currentFreq);
            }
            releaseSet.setInterpolator(springInterp);

            sla.addState(new int[]{android.R.attr.state_pressed}, pressedSet);
            sla.addState(new int[0], releaseSet);

            v.setStateListAnimator(sla);
        } catch (Throwable ignore) {
        }
    }

    /**
     * Тактильний відгук для карток чатів (DialogCell): м'яке вдавлювання на 0.985x при тапі.
     */
    private void handleCardTouch(final View view, MotionEvent ev) {
        if (view == null) return;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            view.animate().cancel();
            view.animate()
                    .scaleX(currentCardPressScale)
                    .scaleY(currentCardPressScale)
                    .setDuration(60L)
                    .setInterpolator(cachedCompressInterpolator)
                    .start();
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Throwable ignore) {
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.animate().cancel();
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(currentDuration)
                    .setInterpolator(cachedSpringInterpolator != null ? cachedSpringInterpolator : new DampedSpringInterpolator(currentDamping, currentFreq))
                    .start();
        }
    }

    /**
     * Прямий відгук для іконок тулбара (ActionBarMenuItem): гарантована анімація вже на першому тапі.
     */
    private void handleMenuItemTouch(final View view, MotionEvent ev) {
        if (view == null) return;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            view.animate().cancel();
            view.animate()
                    .scaleX(currentButtonPressScale)
                    .scaleY(currentButtonPressScale)
                    .setDuration(55L)
                    .setInterpolator(cachedCompressInterpolator)
                    .start();
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Throwable ignore) {
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.animate().cancel();
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(currentDuration)
                    .setInterpolator(cachedSpringInterpolator != null ? cachedSpringInterpolator : new DampedSpringInterpolator(currentDamping, currentFreq))
                    .start();
        }
    }

    /**
     * Тактильний відгук для нижніх табів (GlassTabView).
     */
    private void handleTabTouch(final View view, MotionEvent ev) {
        if (view == null) return;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            view.animate().cancel();
            view.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(55L)
                    .setInterpolator(cachedCompressInterpolator)
                    .start();
            try {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            } catch (Throwable ignore) {
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            view.animate().cancel();
            view.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(currentDuration)
                    .setInterpolator(cachedSpringInterpolator != null ? cachedSpringInterpolator : new DampedSpringInterpolator(currentDamping, currentFreq))
                    .start();
        }
    }

    // ==================== Dynamic ART Hooks ====================

    private void setupHooks() {
        if (host == null) return;
        ClassLoader cl = host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();

        // 1. BottomSheet: шовкове плавне відкриття шторок без батутного дрижання
        try {
            final Class<?> bsClass = Class.forName("org.telegram.ui.ActionBar.BottomSheet", true, cl);
            List<HotHook.Unhook> sheetHooks = host.hookAll(bsClass, "show", new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled()) return;
                    Object sheet = call.getThis();
                    if (sheet != null) {
                        try {
                            Field f = bsClass.getField("openInterpolator");
                            f.set(sheet, cachedSheetInterpolator);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            });
            if (sheetHooks != null) activeHooks.addAll(sheetHooks);
            host.log("ui: hooked BottomSheet.show for smooth settle entrance");
        } catch (Throwable t) {
            host.log("ui: hook BottomSheet failed: " + t.getMessage());
        }

        // 2. Main navigation is rendered by the module. The client activity is
        // untouched; we only install the Yumi visual layer after it creates its
        // normal tabs. Touch, long-press and tab selection remain native.
        try {
            final Class<?> mainTabs = Class.forName("org.telegram.ui.MainTabsActivity", true, cl);
            final Method createView = mainTabs.getMethod("createView", Context.class);
            final HotHook.Unhook u = host.hook(createView, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    navController.apply(call.getThis());
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked MainTabsActivity.createView for module navigation");
        } catch (Throwable t) {
            host.log("ui: hook MainTabsActivity failed: " + t.getMessage());
        }

        try {
            final Class<?> tabClass = Class.forName("org.telegram.ui.Components.glass.GlassTabView", true, cl);
            final Method setCounter = tabClass.getDeclaredMethod("setCounter", String.class, boolean.class, boolean.class);
            HotHook.Unhook u = host.hook(setCounter, new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length >= 2) {
                        navController.onCounter((View) target,
                                args[0] instanceof String ? (String) args[0] : null,
                                args[1] instanceof Boolean && (Boolean) args[1]);
                        call.setResult(null);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked GlassTabView.setCounter");

            final Method setSelected = tabClass.getDeclaredMethod("setSelected", boolean.class, boolean.class);
            u = host.hook(setSelected, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length > 0) {
                        navController.onSelected((View) target, args[0] instanceof Boolean && (Boolean) args[0]);
                    }
                }
            });
            if (u != null) activeHooks.add(u);

            final Method setText = tabClass.getDeclaredMethod("setText", CharSequence.class);
            u = host.hook(setText, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length > 0) {
                        navController.onText((View) target, (CharSequence) args[0]);
                    }
                }
            });
            if (u != null) activeHooks.add(u);

            final Method updateAvatar = tabClass.getDeclaredMethod("updateUserAvatar", int.class);
            u = host.hook(updateAvatar, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        navController.onAvatar((View) target);
                    }
                }
            });
            if (u != null) activeHooks.add(u);

            host.log("ui: hooked GlassTabView visual state for module navigation");
        } catch (Throwable t) {
            host.log("ui: hook GlassTabView visual state failed: " + t.getMessage());
        }

        // 3. Module-owned chrome for official list screens. The fragments and
        // adapters are untouched; a Yumi top bar / search field is inserted and
        // the original ActionBar stays measured but invisible.
        try {
            hookScreen("org.telegram.ui.ContactsActivity", cl, YumiScreenChrome.KIND_CONTACTS);
            hookScreen("org.telegram.ui.CallLogActivity", cl, YumiScreenChrome.KIND_CALLS);
            hookScreen("org.telegram.ui.FiltersSetupActivity", cl, YumiScreenChrome.KIND_FOLDERS);

            final Class<?> actionBar = Class.forName("org.telegram.ui.ActionBar.ActionBar", true, cl);
            final Method showActionMode = actionBar.getDeclaredMethod("showActionMode",
                    boolean.class, View.class, View.class, View[].class, boolean[].class, View.class, int.class);
            HotHook.Unhook u = host.hook(showActionMode, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (screenChrome == null) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        screenChrome.onActionMode((View) target, true);
                    }
                }
            });
            if (u != null) activeHooks.add(u);

            final Method hideActionMode = actionBar.getDeclaredMethod("hideActionMode");
            u = host.hook(hideActionMode, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (screenChrome == null) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        screenChrome.onActionMode((View) target, false);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked Contacts/Calls/Folders chrome + ActionBar mode");
        } catch (Throwable t) {
            host.log("ui: hook list screen chrome failed: " + t.getMessage());
        }

        try {
            final Class<?> loginCls = Class.forName("org.telegram.ui.LoginActivity", true, cl);
            final Method createView = loginCls.getMethod("createView", Context.class);
            final HotHook.Unhook u = host.hook(createView, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || !isYumiLogin()) return;
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked LoginActivity.createView for Yumi login");
        } catch (Throwable t) {
            host.log("ui: hook LoginActivity failed: " + t.getMessage());
        }
    }

    private void hookScreen(String className, ClassLoader cl, final String kind) {
        try {
            final Class<?> cls = Class.forName(className, true, cl);
            final Method createView = cls.getMethod("createView", Context.class);
            final HotHook.Unhook u = host.hook(createView, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || screenChrome == null) return;
                    Object[] args = call.getArgs();
                    if (args != null && args.length > 0 && args[0] instanceof Context) {
                        screenChrome.apply(call.getThis(), (Context) args[0], kind);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked " + className + ".createView (" + kind + ")");
        } catch (Throwable t) {
            host.log("ui: hook " + className + " failed: " + t.getMessage());
        }
    }

    // ==================== Settings UI ====================

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean exp = styleMode() == 1;

        rows.add(HotRow.header(t("Стиль інтерфейсу", "Стиль интерфейса", "Interface Style")));
        rows.add(HotRow.switchRow("expressive",
                t("Виразний інтерфейс Yumi", "Выразительный интерфейс Yumi", "Yumi Expressive"),
                t("Картки Yumi, власний док навігації та оновлені заголовки списків",
                  "Карточки Yumi, собственный док навигации и обновленные заголовки списков",
                  "Yumi cards, custom navigation dock and updated list headers"),
                exp));
        rows.add(HotRow.switchRow("theme_tonal_adaptation",
                t("Адаптація під активну тему", "Адаптация под активную тему", "Adaptive Theme Palette"),
                t("Гармонійно наслідувати відтінки та акценти поточної теми користувача",
                  "Гармонично следовать оттенкам и акцентам текущей темы пользователя",
                  "Harmoniously follow tones and accents of the active user theme"),
                readBool("theme_tonal_adaptation", true)));

        rows.add(HotRow.header(t("Авторизація та екран блокування", "Авторизация и экран блокировки", "Auth & Lock Screen")));
        rows.add(HotRow.switchRow(KEY_YUMI_LOGIN,
                t("Виразний екран входу Yumi", "Выразительный экран входа Yumi", "Yumi Expressive Login"),
                t("Фірмовий бренд-хедер, об'єднана картка номера, чіп коду країни, швидкий QR-вхід та оновлена клавіатура",
                  "Фирменный бренд-хедер, объединенная карточка номера, чип кода страны, быстрый QR-вход и обновленная клавиатура",
                  "Brand hero header, unified phone card, country code chip, quick QR login and updated keypad"),
                isYumiLogin()));
        rows.add(HotRow.switchRow(KEY_PASSCODE_MUSIC,
                t("Острівець музики на екрані пін-коду", "Островок музыки на экране пин-кода", "Passcode Music Capsule"),
                t("Компактна Dynamic Island пігулка з еквалайзером, коли відтворюється музика (без зайвих заглушок)",
                  "Компактная Dynamic Island пилюля с эквалайзером, когда играет музыка (без лишних заглушек)",
                  "Compact Dynamic Island pill with equalizer when music is playing (no empty placeholders)"),
                isPasscodeMusicEnabled()));

        rows.add(HotRow.header(t("Список чатів", "Список чатов", "Chat List")));
        rows.add(HotRow.choice("chat_card_style_choice",
                t("Стиль списку чатів", "Стиль списка чатов", "Chat List Style"),
                t("Оберіть окремий вигляд рядків і відступів", "Выберите отдельный вид строк и отступов", "Choose distinct row style and spacing"),
                new String[]{
                        t("Картки Yumi", "Карточки Yumi", "Yumi Cards"),
                        t("М'які острівці", "Мягкие островки", "Soft Islands"),
                        t("Плаский список", "Плоский список", "Flat List")
                },
                chatListStyle() == CHAT_STYLE_CARDS ? 0 : (chatListStyle() == CHAT_STYLE_ISLANDS ? 1 : 2)));
        rows.add(HotRow.switchRow(KEY_CHAT_CARD_OUTLINE,
                t("Контур карток чатів", "Контур карточек чатов", "Chat Card Outline"),
                t("Легкий контур навколо кожної картки", "Легкий контур вокруг каждой карточки", "Subtle outline around each card"),
                isChatCardOutline()));
        rows.add(HotRow.slider("avatar_corners_slider",
                t("Закруглення аватарок", "Закругление аватаров", "Avatar Corner Radius"),
                0, 28,
                readInt("OEAppearanceAvatarCorners", 28),
                "dp"));
        rows.add(HotRow.switchRow(KEY_CHAT_PINNED_ACCENT,
                t("Підсвічування закріплених чатів", "Подсветка закрепленных чатов", "Accent Pinned Chats"),
                t("Виділяє закріплені розмови акцентним кольором", "Выделяет закрепленные беседы акцентным цветом", "Highlights pinned chats with the accent color"),
                isChatPinnedAccent()));
        rows.add(HotRow.switchRow(KEY_CHAT_SQUIRCLE_AVATARS,
                t("Заокруглені аватари", "Скругленные аватары", "Rounded Avatars"),
                t("М'яка заокруглена форма аватарок", "Мягкая скругленная форма аватаров", "Soft rounded avatar shape"),
                isChatSquircleAvatars()));
        rows.add(HotRow.switchRow(KEY_CHAT_CARDS_TOUCH,
                t("Пружинний відгук на дотик", "Пружинный отклик на касание", "Touch Spring Feedback"),
                t("Тактильна мікро-анімація при натисканні на діалог", "Тактильная микро-анимация при нажатии на диалог", "Tactile micro-animation when tapping conversations"),
                isChatCardsTouch()));

        rows.add(HotRow.header(t("Повідомлення та чат", "Сообщения и чат", "Messages & Chat")));
        rows.add(HotRow.switchRow("tonal_chat_bg",
                t("Монохромний фон замість шпалер", "Монохромный фон вместо обоев", "Monochrome Chat Background"),
                t("Використовувати мінімалістичну тональну заливку замість призначених шпалер чату (за замовчуванням вимкнено — відображаються реальні шпалери)",
                  "Использовать минималистичную тональную заливку вместо установленных обоев чата (по умолчанию выключено — отображаются реальные обои)",
                  "Use minimalist tonal background instead of set chat wallpapers (disabled by default — real wallpapers are shown)"),
                readBool("tonal_chat_bg", false)));
        rows.add(HotRow.slider("bubble_radius",
                t("Закруглення бульбашок повідомлень", "Скругление пузырей сообщений", "Message Bubble Radius"),
                10, 32,
                readInt("bubble_radius", 24),
                "dp"));
        rows.add(HotRow.switchRow("remove_tails",
                t("Приховати хвостики повідомлень", "Скрыть хвостики сообщений", "Hide Message Tails"),
                t("Чисті скруглені бульбашки без кутових хвостиків",
                  "Чистые скругленные пузыри без угловых хвостиков",
                  "Clean rounded message bubbles without pointer tails"),
                readBool("remove_tails", true)));

        rows.add(HotRow.header(t("Кольори та палітра", "Цвета и палитра", "Colors & Palette")));
        rows.add(HotRow.choice(KEY_ACCENT_MODE,
                t("Акцентний колір", "Акцентный цвет", "Accent Color"),
                t("Оберіть відтінок акценту інтерфейсу або залиште авто-слідування за темою",
                  "Выберите оттенок акцента интерфейса или оставьте авто-следование за темой",
                  "Choose interface accent shade or follow active Telegram theme"),
                new String[]{
                        t("Тема Telegram (Авто)", "Тема Telegram (Авто)", "Telegram Theme (Auto)"),
                        t("Рожева сакура", "Розовая сакура", "Sakura Pink"),
                        t("Небесно-блакитний", "Небесно-голубой", "Sky Blue"),
                        t("М'ятний смарагд", "Мятный изумруд", "Mint Emerald"),
                        t("Лавандовий", "Лавандовый", "Lavender"),
                        t("Захід сонця", "Закат", "Sunset Coral")
                },
                accentMode()));

        rows.add(HotRow.header(t("Геометрія та розміри", "Геометрия и размеры", "Geometry & Sizes")));
        rows.add(HotRow.slider(KEY_CARD_RADIUS,
                t("Радіус карток чатів", "Радиус карточек чатов", "Chat Card Radius"),
                12, 28,
                (int) cardRadius(),
                "dp"));
        rows.add(HotRow.slider(KEY_CARD_MARGIN_SIDE,
                t("Боковий відступ карток", "Боковой отступ карточек", "Card Side Margin"),
                4, 18,
                (int) cardSideMargin(),
                "dp"));
        rows.add(HotRow.slider(KEY_CARD_MARGIN_V,
                t("Вертикальний інтервал карток", "Вертикальный интервал карточек", "Card Vertical Spacing"),
                10, 80,
                readInt(KEY_CARD_MARGIN_V, 35),
                "·0.1dp"));
        rows.add(HotRow.slider(KEY_NAV_RADIUS,
                t("Закруглення панелі навігації", "Закругление панели навигации", "Navigation Bar Radius"),
                14, 36,
                (int) navRadius(),
                "dp"));

        rows.add(HotRow.header(t("Розташування та мітки", "Расположение и метки", "Placement & Badges")));
        rows.add(HotRow.choice(KEY_TIME_BADGE_STYLE,
                t("Стиль мітки часу", "Стиль метки времени", "Time Badge Style"),
                t("Виберіть спосіб відображення часу в списку повідомлень",
                  "Выберите способ отображения времени в списке сообщений",
                  "Choose time display style in conversation list"),
                new String[]{
                        t("Тональна капсула", "Тональная капсула", "Tonal Pill"),
                        t("Звичайний текст", "Обычный текст", "Native Text")
                },
                isTimePillEnabled() ? 0 : 1));
        rows.add(HotRow.switchRow(KEY_NAV_EXPAND_PILL,
                t("Розширена капсула вкладки", "Расширенная капсула вкладки", "Expanded Active Tab Pill"),
                t("Активна вкладка розгортається у пігулку з назвою та лічильником («2 Чати»)",
                  "Активная вкладка раскрывается в пилюлю с названием и счетчиком («2 Чата»)",
                  "Active tab expands into a pill with label and counter («2 Chats»)"),
                isNavExpandActivePillEnabled()));
        rows.add(HotRow.switchRow(KEY_NAV_FLOATING,
                t("Плаваюча панель навігації", "Плавающая панель навигации", "Floating Navigation Bar"),
                t("М'який плаваючий док з відступом над нижнім краєм екрана",
                  "Мягкий плавающий док с отступом над нижним краем экрана",
                  "Soft floating dock detached from bottom screen edge"),
                isNavFloating()));
        rows.add(HotRow.switchRow(KEY_BLUR_FOLDERS,
                t("Смуга розмиття під папками", "Полоса размытия под папками", "Blur Under Folders"),
                t("М'яке розмиття фону списку чатів під верхньою панеллю папок",
                  "Мягкое размытие фона списка чатов под верхней панелью папок",
                  "Soft background blur beneath top folder bar"),
                isBlurFoldersEnabled()));
        rows.add(HotRow.switchRow(KEY_BLUR_NAV,
                t("Смуга розмиття над навігацією", "Полоса размытия над навигацией", "Blur Over Navigation"),
                t("М'яке розмиття фону списку чатів над нижньою панеллю навігації",
                  "Мягкое размытие фона списка чатов над нижней панелью навигации",
                  "Soft background blur above bottom navigation dock"),
                isBlurNavEnabled()));

        rows.add(HotRow.header(t("Тактильність та анімації", "Тактильность и анимации", "Physics & Haptics")));
        rows.add(HotRow.switchRow(KEY_STICKY_ENABLED,
                t("Пружинна фізика та вібровідгук", "Пружинная физика и виброотклик", "Spring Physics & Haptics"),
                t("Плавні переходи, відкриття шторок та мікро-анімації кнопок",
                  "Плавные переходы, открытие шторок и микро-анимации кнопок",
                  "Smooth transitions, sheet settling and tactile button animations"),
                isStickyEnabled()));

        rows.add(HotRow.header(t("Компоненти", "Компоненты", "Components")));
        rows.add(HotRow.switchRow(KEY_MINI_PLAYER,
                t("Компактний міні-плеєр", "Компактный мини-плеер", "Compact Mini Player"),
                t("Компактна панель аудіоплеєра над списком чатів",
                  "Компактная плашка аудиоплеера над списком чатов",
                  "Compact audio player plate above chat list"),
                readBool(KEY_MINI_PLAYER, true)));
        rows.add(HotRow.switchRow(KEY_GLASS_MENU,
                t("Скляне контекстне меню", "Стеклянное контекстное меню", "Glass Message Menu"),
                t("Напівпрозоре розмите меню дій для повідомлень",
                  "Полупрозрачное размытое меню действий для сообщений",
                  "Translucent blurred context menu for messages"),
                readBool(KEY_GLASS_MENU, true)));
        rows.add(HotRow.switchRow(KEY_LOADING,
                t("Сучасні індикатори завантаження", "Современные индикаторы загрузки", "Modern Loading Indicators"),
                t("Плавні індикатори прогресу в стилі Material Design",
                  "Плавные индикаторы прогресса в стиле Material Design",
                  "Smooth Material Design progress bars"),
                readBool(KEY_LOADING, true)));

        rows.add(HotRow.header(t("Дії", "Действия", "Actions")));
        rows.add(HotRow.button("apply_live",
                t("Оновити інтерфейс", "Обновить интерфейс", "Refresh Interface"),
                t("Миттєво застосувати всі параметри на льоту",
                  "Мгновенно применить все параметры на лету",
                  "Instantly re-apply all parameters live")));
        rows.add(HotRow.button("reset_defaults",
                t("Скинути до рекомендованих", "Сбросить к рекомендованным", "Reset to Defaults"),
                t("Повернути чистий стандартний стиль інтерфейсу",
                  "Вернуть чистый стандартный стиль интерфейса",
                  "Restore clean standard interface style")));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if ("expressive".equals(key)) {
            writeInt(KEY_STYLE, value ? 1 : 0);
            writeInt("style", value ? 1 : 0);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (screenChrome != null && !isExpressive()) screenChrome.restoreAll();
            return;
        }
        if ("chat_card_style".equals(key)) {
            writeInt(KEY_CHAT_STYLE, value ? CHAT_STYLE_CARDS : CHAT_STYLE_FLAT);
            writeBool(KEY_CHAT_CARD_OUTLINE, false);
            markTouched();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            return;
        }
        if (KEY_YUMI_LOGIN.equals(key)) {
            writeBool(KEY_YUMI_LOGIN, value);
            markTouched();
            return;
        }
        if (KEY_PASSCODE_MUSIC.equals(key)) {
            writeBool(KEY_PASSCODE_MUSIC, value);
            markTouched();
            return;
        }
        if (KEY_NAV_EXPAND_PILL.equals(key) || KEY_NAV_FLOATING.equals(key)) {
            writeBool(key, value);
            markTouched();
            if (navController != null) navController.refresh();
            return;
        }
        if (KEY_BLUR_FOLDERS.equals(key) || KEY_BLUR_NAV.equals(key)) {
            writeBool(key, value);
            markTouched();
            try {
                ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
                Class<?> gate = Class.forName("app.amegram.hot.HotUiGate", true, cl);
                gate.getMethod("applyLive").invoke(null);
            } catch (Throwable ignore) {
            }
            return;
        }
        if ("tonal_chat_bg".equals(key)) {
            writeBool("tonal_chat_bg", value);
            markTouched();
            notifyThemeChange();
            return;
        }
        if ("remove_tails".equals(key)) {
            writeBool("remove_tails", value);
            markTouched();
            notifyThemeChange();
            return;
        }
        writeBool(key, value);
        markTouched();
        if (key != null && (key.startsWith("chat_") || key.startsWith("theme_") || key.startsWith("OEAppearanceChat"))) {
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
        }
    }

    @Override
    public void onSettingsChoice(String key, int index) {
        if ("chat_card_style_choice".equals(key)) {
            int style = (index == 0) ? CHAT_STYLE_CARDS : (index == 1 ? CHAT_STYLE_ISLANDS : CHAT_STYLE_FLAT);
            writeInt(KEY_CHAT_STYLE, style);
            markTouched();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            return;
        }
        if (KEY_ACCENT_MODE.equals(key)) {
            writeInt(KEY_ACCENT_MODE, index);
            markTouched();
            if (chatRowRenderer != null) {
                chatRowRenderer.palette.refresh(null);
                chatRowRenderer.invalidateAll();
            }
            if (navController != null) navController.refresh();
            try {
                ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
                Class<?> gate = Class.forName("app.amegram.hot.HotUiGate", true, cl);
                gate.getMethod("applyLive").invoke(null);
            } catch (Throwable ignore) {
            }
            return;
        }
        if (KEY_TIME_BADGE_STYLE.equals(key)) {
            writeInt(KEY_TIME_BADGE_STYLE, index);
            markTouched();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            return;
        }
        writeInt(key, index);
        markTouched();
    }

    @Override
    public void onSettingsSlider(String key, int value) {
        if ("bubble_radius".equals(key)) {
            writeInt("bubble_radius", value);
            markTouched();
            notifyThemeChange();
            return;
        }
        if ("avatar_corners_slider".equals(key)) {
            writeInt("OEAppearanceAvatarCorners", value);
            markTouched();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            return;
        }
        if (KEY_CARD_RADIUS.equals(key) || KEY_CARD_MARGIN_SIDE.equals(key) || KEY_CARD_MARGIN_V.equals(key)) {
            writeInt(key, value);
            markTouched();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            return;
        }
        if (KEY_NAV_RADIUS.equals(key) || KEY_NAV_HEIGHT.equals(key)) {
            writeInt(key, value);
            markTouched();
            if (navController != null) navController.refresh();
            return;
        }
        writeInt(key, value);
        markTouched();
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("apply_live".equals(rowId) || "go_expressive".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (host != null) host.toast(t("Інтерфейс оновлено", "Интерфейс обновлен", "Interface updated"));
        } else if ("go_classic".equals(rowId)) {
            writeInt(KEY_STYLE, 0);
            writeInt("style", 0);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (screenChrome != null) screenChrome.restoreAll();
            if (host != null) host.toast(t("Класичний стиль застосовано", "Классический стиль применен", "Classic style applied"));
        } else if ("reset_defaults".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            writeInt(KEY_CHAT_STYLE, CHAT_STYLE_FLAT);
            writeBool(KEY_CHAT_CARDS_TOUCH, true);
            writeBool(KEY_CHAT_PINNED_ACCENT, true);
            writeBool(KEY_CHAT_SQUIRCLE_AVATARS, true);
            writeBool(KEY_CHAT_CARD_OUTLINE, true);
            writeBool(KEY_STICKY_ENABLED, true);
            writeBool(KEY_STICKY_THEME_ADAPTIVE, true);
            writeBool(KEY_MINI_PLAYER, true);
            writeBool(KEY_GLASS_MENU, true);
            writeBool(KEY_LOADING, true);
            writeBool("theme_tonal_adaptation", true);
            writeInt(KEY_ACCENT_MODE, 0);
            writeInt(KEY_CARD_RADIUS, 22);
            writeInt(KEY_CARD_MARGIN_SIDE, 12);
            writeInt(KEY_CARD_MARGIN_V, 35);
            writeInt(KEY_NAV_RADIUS, 24);
            writeInt(KEY_NAV_HEIGHT, 58);
            writeInt(KEY_TIME_BADGE_STYLE, 0);
            writeBool(KEY_NAV_EXPAND_PILL, true);
            writeBool(KEY_NAV_FLOATING, true);
            writeBool(KEY_BLUR_FOLDERS, false);
            writeBool(KEY_BLUR_NAV, false);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (host != null) host.toast(t("Параметри інтерфейсу повернуто до рекомендованих", "Параметры интерфейса сброшены к рекомендованным", "Interface settings restored to defaults"));
        }
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }

    private void notifyThemeChange() {
        try {
            ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
            Class<?> ncClass = Class.forName("org.telegram.messenger.NotificationCenter", true, cl);
            Object inst = ncClass.getMethod("getGlobalInstance").invoke(null);
            int didApplyNewTheme = ncClass.getField("didApplyNewTheme").getInt(null);
            ncClass.getMethod("postNotificationName", int.class, Object[].class).invoke(inst, didApplyNewTheme, new Object[0]);
        } catch (Throwable ignore) {
        }
        try {
            ClassLoader cl = host != null && host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();
            Class<?> gate = Class.forName("app.amegram.hot.HotUiGate", true, cl);
            gate.getMethod("applyLive").invoke(null);
        } catch (Throwable ignore) {
        }
    }
}
