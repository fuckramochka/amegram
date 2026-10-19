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
        updateThemeState();
        setupThemeListener();
        setupHooks();
        try {
            host.log("ui v1.0.5 attached with refined M3 Expressive physics & card haptics");
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
        return readInt(KEY_CHAT_STYLE, CHAT_STYLE_CARDS);
    }

    public boolean isChatPinnedAccent() {
        return readBool(KEY_CHAT_PINNED_ACCENT, true);
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

        // 1. View.setOnClickListener: пружинний відгук для кнопок, FAB та іконок
        try {
            Method m = View.class.getDeclaredMethod("setOnClickListener", View.OnClickListener.class);
            HotHook.Unhook u = host.hook(m, new HotHook.Callback() {
                @Override
                public void after(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled()) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View) {
                        View v = (View) target;
                        View.OnClickListener l = (args != null && args.length > 0) ? (View.OnClickListener) args[0] : null;
                        if (l != null && isInteractiveButton(v)) {
                            applySpringStateListAnimator(v);
                        }
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked View.setOnClickListener for spring buttons");
        } catch (Throwable t) {
            host.log("ui: hook View.setOnClickListener failed: " + t.getMessage());
        }

        // 2. DialogCell.onTouchEvent: тактильне вдавлювання карток чатів (0.985x)
        try {
            Class<?> cellClass = Class.forName("org.telegram.ui.Cells.DialogCell", true, cl);
            Method mTouch = cellClass.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            HotHook.Unhook u = host.hook(mTouch, new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled() || !isChatCardsTouch() || chatListStyle() == CHAT_STYLE_FLAT) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length > 0 && args[0] instanceof MotionEvent) {
                        handleCardTouch((View) target, (MotionEvent) args[0]);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked DialogCell.onTouchEvent for card press physics");
        } catch (Throwable t) {
            host.log("ui: hook DialogCell.onTouchEvent failed: " + t.getMessage());
        }

        // 3. BottomSheet: шовкове плавне відкриття шторок без батутного дрижання
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

        // 4. ActionBarMenuItem.onTouchEvent: реактивний мікровідгук тулбара з першого тапу
        try {
            Class<?> itemClass = Class.forName("org.telegram.ui.ActionBar.ActionBarMenuItem", true, cl);
            Method m = itemClass.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            HotHook.Unhook u = host.hook(m, new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled()) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length > 0 && args[0] instanceof MotionEvent) {
                        handleMenuItemTouch((View) target, (MotionEvent) args[0]);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked ActionBarMenuItem.onTouchEvent");
        } catch (Throwable t) {
            host.log("ui: hook ActionBarMenuItem failed: " + t.getMessage());
        }

        // 5. GlassTabView.onTouchEvent: пружинний відгук для нижніх табів
        try {
            Class<?> tabClass = Class.forName("org.telegram.ui.Components.glass.GlassTabView", true, cl);
            Method m = tabClass.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            HotHook.Unhook u = host.hook(m, new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled()) return;
                    Object target = call.getThis();
                    Object[] args = call.getArgs();
                    if (target instanceof View && args != null && args.length > 0 && args[0] instanceof MotionEvent) {
                        handleTabTouch((View) target, (MotionEvent) args[0]);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked GlassTabView.onTouchEvent");
        } catch (Throwable t) {
            host.log("ui: hook GlassTabView failed: " + t.getMessage());
        }
    }

    // ==================== Settings UI ====================

    @Override
    public void fillSettings(List<HotRow> rows) {
        boolean exp = styleMode() == 1;

        rows.add(HotRow.header("Стиль оформлення"));
        rows.add(HotRow.switchRow("expressive",
                "Yougram Expressive",
                "Рідке скло, плаваюча панель та стиль Material You",
                exp));
        rows.add(HotRow.switchRow("theme_tonal_adaptation",
                "Тональні кольори теми",
                "Зберігає та насичує рожеві, темні та будь-які кастомні тони теми замість сірого",
                readBool("theme_tonal_adaptation", true)));

        rows.add(HotRow.header("Список чатів Yumi"));
        int cStyle = chatListStyle();
        String cStyleName = cStyle == CHAT_STYLE_CARDS ? "Виразні плаваючі картки (M3)"
                : (cStyle == CHAT_STYLE_ISLANDS ? "Сегментовані острівці" : "Сучасний плаский");
        rows.add(HotRow.button("cycle_chat_style",
                "Стиль списку: " + cStyleName,
                "Натисніть для перемикання (Картки → Острівці → Плаский)"));
        rows.add(HotRow.switchRow(KEY_CHAT_CARDS_TOUCH,
                "Тактильність карток чатів",
                "М'яке пружинне притискання (0.985x) при натисканні на діалог",
                isChatCardsTouch()));
        rows.add(HotRow.switchRow(KEY_CHAT_PINNED_ACCENT,
                "Тональні закріплені чати",
                "М'яке підсвічування закріплених чатів акцентним відтінком теми",
                isChatPinnedAccent()));
        rows.add(HotRow.switchRow(KEY_CHAT_SQUIRCLE_AVATARS,
                "Squircle-аватарки (PixelPlayer)",
                "Виразне скруглення аватарок чатів у стилі Material You",
                isChatSquircleAvatars()));
        rows.add(HotRow.switchRow(KEY_CHAT_CARD_OUTLINE,
                "Мікро-контур карток (M3)",
                "Тонка адаптивна обводка карток для ефекту глибини",
                isChatCardOutline()));

        rows.add(HotRow.header("Пружинна тактильність (Pixel M3)"));
        rows.add(HotRow.switchRow(KEY_STICKY_ENABLED,
                "Липкі анімації та вібровідгук",
                "Пружинна фізика кнопок, шторок і меню з адаптацією під тему",
                isStickyEnabled()));

        rows.add(HotRow.header("Елементи інтерфейсу"));
        rows.add(HotRow.switchRow(KEY_MINI_PLAYER,
                "MD3 Міні-плеєр",
                "Плаваюча плашка медіаплеєра над чатами",
                readBool(KEY_MINI_PLAYER, true)));
        rows.add(HotRow.switchRow(KEY_GLASS_MENU,
                "Скляне меню",
                "Ефект рідкого скла для контекстних дій",
                readBool(KEY_GLASS_MENU, true)));
        rows.add(HotRow.switchRow(KEY_LOADING,
                "M3-індикатори",
                "Плавні Material You індикатори завантаження",
                readBool(KEY_LOADING, true)));

        rows.add(HotRow.header("Адаптація під тему"));
        rows.add(HotRow.info("Активний профіль: " + getThemeSummary() + "\n" + getAdaptiveProfileDescription()));

        rows.add(HotRow.header("Дії"));
        rows.add(HotRow.button("apply_live",
                "Оновити інтерфейс",
                "Миттєво перезастосувати кольори та стилі без перезапуску"));
        rows.add(HotRow.button("reset_defaults",
                "Скинути до стандартних",
                "Повернути рекомендовані налаштування інтерфейсу Yumi"));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if ("expressive".equals(key)) {
            writeInt(KEY_STYLE, value ? 1 : 0);
            writeInt("style", value ? 1 : 0);
            markTouched();
            return;
        }
        writeBool(key, value);
        markTouched();
    }

    @Override
    public void onSettingsAction(String rowId) {
        if ("cycle_chat_style".equals(rowId)) {
            int next = (chatListStyle() + 1) % 3;
            writeInt(KEY_CHAT_STYLE, next);
            markTouched();
            String name = next == CHAT_STYLE_CARDS ? "Виразні плаваючі картки"
                    : (next == CHAT_STYLE_ISLANDS ? "Сегментовані острівці" : "Сучасний плаский");
            if (host != null) host.toast("Стиль списку: " + name);
        } else if ("apply_live".equals(rowId) || "go_expressive".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            markTouched();
            if (host != null) host.toast("Інтерфейс Yumi оновлено");
        } else if ("go_classic".equals(rowId)) {
            writeInt(KEY_STYLE, 0);
            writeInt("style", 0);
            markTouched();
            if (host != null) host.toast("Yougram Classic застосовано");
        } else if ("reset_defaults".equals(rowId)) {
            writeInt(KEY_STYLE, 1);
            writeInt("style", 1);
            writeInt(KEY_CHAT_STYLE, CHAT_STYLE_CARDS);
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
            markTouched();
            if (host != null) host.toast("Налаштування скинуто до рекомендованих");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        return null;
    }
}
