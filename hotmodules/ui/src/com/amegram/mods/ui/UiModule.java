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
 * Модуль інтерфейсу Yumi v1.0.5:
 * Yougram Expressive ↔ Classic на льоту, преміальна пружинна тактильність (Material 3 Expressive),
 * плавний тактильний відгук на картках чатів (DialogCell 0.985x), шовкове відкриття шторок (BottomSheet)
 * та реактивні мікро-анімації кнопок (0.965x) без затримок і желе-ефекту.
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
        return "Інтерфейс Yumi";
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

    public boolean isChatSquircleAvatars() {
        return readBool(KEY_CHAT_SQUIRCLE_AVATARS, false);
    }

    public boolean isChatCardOutline() {
        return readBool(KEY_CHAT_CARD_OUTLINE, false);
    }

    public boolean isChatCardsTouch() {
        return readBool(KEY_CHAT_CARDS_TOUCH, true);
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
                int themeListUpdated = ncClass.getField("themeListUpdated").getInt(null);
                host.subscribeNotification(themeListUpdated, observer);
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

            rebuildPhysicsParameters();
            if (navController != null) {
                navController.refreshPalette(host != null ? host.context() : null);
            }
            if (chatRowRenderer != null) {
                chatRowRenderer.refreshPalette(host != null ? host.context() : null);
                chatRowRenderer.invalidateAll();
            }
            if (screenChrome != null) {
                screenChrome.refreshPalette(host != null ? host.context() : null);
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
        if (isThemeDark) return "Темна тема";
        return "Світла тема";
    }

    private String getAdaptiveProfileDescription() {
        if (isThemeMonet) return "Pixel M3 Expressive (динамічні кольори Monet, 190ms)";
        if (isThemeAmoled) return "Ультра-чіткий відгук (170ms, без OLED ghosting)";
        if (isThemeDark) return "Преміальний швидкий відгук (180ms, темна фізика)";
        return "Органічний текучий спрінг (200ms, тактильний мікро-овершут)";
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
    private void applySpringStateListAnimator(final View v) {
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

            final Method onLayout = tabClass.getDeclaredMethod("onLayout",
                    boolean.class, int.class, int.class, int.class, int.class);
            u = host.hook(onLayout, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (navController == null) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        navController.onLayout((View) target);
                    }
                }
            });
            if (u != null) activeHooks.add(u);

            final Method updateColors = tabClass.getDeclaredMethod("updateColorsLottie");
            u = host.hook(updateColors, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isExpressive() || navController == null) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        navController.onColors((View) target);
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

        rows.add(HotRow.header("Стиль інтерфейсу"));
        rows.add(HotRow.switchRow("expressive",
                "Yougram Expressive (MD3)",
                "Плавна Material Design 3 навігація, плаваючі панелі та скруглення",
                exp));
        rows.add(HotRow.switchRow("theme_tonal_adaptation",
                "Адаптація під активну тему",
                "Гармонійно наслідувати відтінки та акценти поточної теми користувача",
                readBool("theme_tonal_adaptation", true)));

        rows.add(HotRow.header("Список чатів"));
        rows.add(HotRow.switchRow("chat_card_style",
                "Картковий стиль чатів",
                "Візуально виділяти діалоги в м'які плитки (вимкнено — чистий плаский MD3)",
                chatListStyle() == CHAT_STYLE_CARDS));
        rows.add(HotRow.switchRow(KEY_CHAT_PINNED_ACCENT,
                "Підсвічування закріплених чатів",
                "Тональний колірний акорд для важливих діалогів",
                isChatPinnedAccent()));
        rows.add(HotRow.switchRow(KEY_CHAT_SQUIRCLE_AVATARS,
                "Скруглені аватари (Squircle)",
                "Форма аватарок чатів у стилі Material You",
                isChatSquircleAvatars()));
        rows.add(HotRow.switchRow(KEY_CHAT_CARDS_TOUCH,
                "Пружинний відгук на дотик",
                "Тактильна мікро-анімація при натисканні на діалог",
                isChatCardsTouch()));

        rows.add(HotRow.header("Тактильність та анімації"));
        rows.add(HotRow.switchRow(KEY_STICKY_ENABLED,
                "Пружинна фізика та вібровідгук",
                "Плавні переходи, відкриття шторок та мікро-анімації кнопок",
                isStickyEnabled()));

        rows.add(HotRow.header("Компоненти"));
        rows.add(HotRow.switchRow(KEY_MINI_PLAYER,
                "MD3 Міні-плеєр",
                "Компактна плашка аудіоплеєра над списком чатів",
                readBool(KEY_MINI_PLAYER, true)));
        rows.add(HotRow.switchRow(KEY_GLASS_MENU,
                "Скляне контекстне меню",
                "Напівпрозоре розмите меню дій для повідомлень",
                readBool(KEY_GLASS_MENU, true)));
        rows.add(HotRow.switchRow(KEY_LOADING,
                "M3 індикатори завантаження",
                "Сучасні плавні прогрес-бари Material You",
                readBool(KEY_LOADING, true)));

        rows.add(HotRow.header("Дії"));
        rows.add(HotRow.button("apply_live",
                "Оновити інтерфейс",
                "Миттєво застосувати всі параметри на льоту"));
        rows.add(HotRow.button("reset_defaults",
                "Скинути до рекомендованих",
                "Повернути чистий стандартний стиль Material 3"));
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
        writeBool(key, value);
        markTouched();
        if (key != null && (key.startsWith("chat_") || key.startsWith("theme_") || key.startsWith("OEAppearanceChat"))) {
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("apply_live".equals(rowId) || "go_expressive".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (host != null) host.toast("Інтерфейс оновлено");
        } else if ("go_classic".equals(rowId)) {
            writeInt(KEY_STYLE, 0);
            writeInt("style", 0);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (screenChrome != null) screenChrome.restoreAll();
            if (host != null) host.toast("Класичний стиль застосовано");
        } else if ("reset_defaults".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            writeInt(KEY_CHAT_STYLE, CHAT_STYLE_FLAT);
            writeBool(KEY_CHAT_CARDS_TOUCH, true);
            writeBool(KEY_CHAT_PINNED_ACCENT, true);
            writeBool(KEY_CHAT_SQUIRCLE_AVATARS, false);
            writeBool(KEY_CHAT_CARD_OUTLINE, false);
            writeBool(KEY_STICKY_ENABLED, true);
            writeBool(KEY_STICKY_THEME_ADAPTIVE, true);
            writeBool(KEY_MINI_PLAYER, true);
            writeBool(KEY_GLASS_MENU, true);
            writeBool(KEY_LOADING, true);
            writeBool("theme_tonal_adaptation", true);
            markTouched();
            if (navController != null) navController.refresh();
            if (chatRowRenderer != null) chatRowRenderer.invalidateAll();
            if (host != null) host.toast("Встановлено чистий стиль Material 3");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }
}
