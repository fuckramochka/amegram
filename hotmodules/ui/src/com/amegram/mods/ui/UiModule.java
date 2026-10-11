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
 * Модуль інтерфейсу Yumi: Yougram Expressive ↔ Classic на льоту,
 * оптимізовані липкі пружинні анімації у стилі PixelPlayer (Material 3 Expressive)
 * та динамічна адаптація фізики під активну тему Telegram (Світла / Темна / AMOLED / Monet).
 *
 * Оптимізації:
 * 1. Zero-Jank: списки та чати скроляться на 100% нативній швидкості без деформації та розмиття рядків.
 * 2. Безпечний StateListAnimator для інтерактивних кнопок (FAB, тулбар, чіпи, таби) — без підміни OnTouchListener.
 * 3. Тактильний мікровідгук HapticFeedbackConstants.KEYBOARD_TAP у момент натискання.
 * 4. Затухаючий гармонійний осцилятор з вивіреним овершутом 3.5%-5.0% без комичного вильоту за екран.
 * 5. Динамічна реакція на NotificationCenter Telegram (didSetNewTheme / didApplyNewTheme).
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

    // Pre-computed physics parameters
    private volatile float currentDamping = 8.0f;
    private volatile float currentFreq = 8.0f;
    private volatile long currentDuration = 310L;
    private volatile float currentButtonPressScale = 0.93f;

    // Cached pre-allocated interpolators
    private volatile Interpolator cachedSpringInterpolator;
    private final Interpolator cachedCompressInterpolator = new StickyCompressInterpolator();

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
            host.log("ui attached with theme-adaptive PixelPlayer spring animations");
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
            if (host != null) host.emit("ui_changed", "{\"style\":" + styleMode() + ",\"sticky\":" + isStickyEnabled() + "}");
        } catch (Throwable ignore) {
        }
    }

    @Override
    public int styleMode() {
        int v = readInt(KEY_STYLE, readInt("style", 1));
        return v == 0 ? 0 : 1;
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
                // Pixel Material 3 Expressive (чистий стиль PixelPlayer: овершут ~4.7%, 310ms)
                currentDamping = 8.0f;
                currentFreq = 8.0f;
                currentDuration = 310L;
                currentButtonPressScale = 0.93f;
            } else if (isThemeAmoled) {
                // AMOLED Pure Black: ультра-чіткий відгук (270ms, без змазування OLED)
                currentDamping = 9.5f;
                currentFreq = 8.5f;
                currentDuration = 270L;
                currentButtonPressScale = 0.95f;
            } else if (isThemeDark) {
                // Темна тема: зібраний, швидкий тактильний відгук (290ms)
                currentDamping = 8.5f;
                currentFreq = 8.2f;
                currentDuration = 290L;
                currentButtonPressScale = 0.94f;
            } else {
                // Світла тема: плавний, органічний спрінг (330ms, овершут ~5.5%)
                currentDamping = 7.5f;
                currentFreq = 8.0f;
                currentDuration = 330L;
                currentButtonPressScale = 0.93f;
            }
        } else {
            switch (stickyStyle()) {
                case STYLE_SUBTLE:
                    currentDamping = 10.0f;
                    currentFreq = 9.0f;
                    currentDuration = 250L;
                    currentButtonPressScale = 0.96f;
                    break;
                case STYLE_ULTRA_BOUNCY:
                    currentDamping = 6.5f;
                    currentFreq = 7.5f;
                    currentDuration = 380L;
                    currentButtonPressScale = 0.91f;
                    break;
                case STYLE_EXPRESSIVE:
                default:
                    currentDamping = 8.0f;
                    currentFreq = 8.0f;
                    currentDuration = 310L;
                    currentButtonPressScale = 0.93f;
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
        if (isThemeMonet) return "PixelPlayer M3 Expressive (динамічні кольори Monet, 310ms)";
        if (isThemeAmoled) return "Ультра-чіткий відгук (270ms, без OLED ghosting)";
        if (isThemeDark) return "Преміальний швидкий відгук (290ms, темна фізика)";
        return "Органічний текучий спрінг (330ms, тактильний овершут 1.05x)";
    }

    // ==================== Optimized Spring Physics ====================

    /**
     * Фізика затухаючого гармонійного осцилятора:
     * progress(t) = 1 - e^(-damping * t) * (cos(freq * t) + (damping / (2 * freq)) * sin(freq * t))
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

    /**
     * Перевірка: чи є елемент інтерактивною кнопкою / іконкою / чіпом.
     * Списки (чати, повідомлення, налаштування) та великі контейнери СУВОРО ігноруються.
     */
    private static boolean isInteractiveButton(View v) {
        if (v == null) return false;
        try {
            // Перевірка батьківських контейнерів: ніколи не чіпати елементи списків
            Object parent = v.getParent();
            while (parent instanceof View) {
                String pName = parent.getClass().getName();
                if (pName.contains("Recycler") || pName.contains("ListView") || pName.contains("Cell") || pName.contains("Row")) {
                    return false;
                }
                parent = ((View) parent).getParent();
            }

            String name = v.getClass().getName();
            // Суворе виключення клітинок, рядків та повнорозмірних контейнерів
            if (name.contains("Cell") || name.contains("Row") || name.contains("Message")
                    || name.contains("Recycler") || name.contains("Activity")
                    || name.contains("Content") || name.contains("Container") || name.contains("Pager")) {
                return false;
            }

            // Виключення контейнерів на всю ширину екрану
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp != null && lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                return false;
            }

            // Відомі класи кнопок та інтерактивних елементів
            if (name.contains("Button") || name.contains("Fab") || name.contains("FAB")
                    || name.contains("Chip") || name.contains("MenuItem")
                    || name.contains("PlayPause") || name.contains("GlassTabView")
                    || name.contains("ActionView")) {
                return true;
            }

            // Звичайні кнопки та клікабельні іконки
            if (v instanceof android.widget.Button || v instanceof android.widget.ImageButton || v instanceof android.widget.ImageView) {
                return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
    }

    /**
     * Підключення нативного StateListAnimator до кнопки з пружинною фізикою та тактильністю.
     * Не підміняє OnTouchListener, не збиває скрол і працює на апаратному прискоренні.
     */
    private void applySpringStateListAnimator(final View v) {
        if (v == null || !isStickyEnabled()) return;
        synchronized (animatedViews) {
            if (animatedViews.contains(v)) return;
            animatedViews.add(v);
        }

        try {
            StateListAnimator sla = new StateListAnimator();

            // 1. Стан натискання (state_pressed): швидке стискання 80ms + чіткий тактильний вібровідгук
            AnimatorSet pressedSet = new AnimatorSet();
            ObjectAnimator scaleXDown = ObjectAnimator.ofFloat(v, View.SCALE_X, currentButtonPressScale);
            ObjectAnimator scaleYDown = ObjectAnimator.ofFloat(v, View.SCALE_Y, currentButtonPressScale);
            pressedSet.playTogether(scaleXDown, scaleYDown);
            pressedSet.setDuration(80L);
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

            // 2. Стан відпускання: пружинне повернення з природним овершутом
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

    // ==================== Dynamic ART Hooks ====================

    private void setupHooks() {
        if (host == null) return;
        ClassLoader cl = host.context() != null ? host.context().getClassLoader() : getClass().getClassLoader();

        // 1. View.setOnClickListener: пружинний відгук для кнопок, FAB, чіпів та іконок
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

        // 2. BottomSheet: плавний пружинний інтерполятор появи шторок
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
                            Interpolator interp = cachedSpringInterpolator;
                            if (interp == null) interp = new DampedSpringInterpolator(currentDamping, currentFreq);
                            f.set(sheet, interp);
                        } catch (Throwable ignore) {
                        }
                    }
                }
            });
            if (sheetHooks != null) activeHooks.addAll(sheetHooks);
            host.log("ui: hooked BottomSheet.show for spring sheet entrance");
        } catch (Throwable t) {
            host.log("ui: hook BottomSheet failed: " + t.getMessage());
        }

        // 3. ActionBarMenuItem: пружинний відгук для іконок верхнього тулбара (пошук, назад, три крапки)
        try {
            Class<?> itemClass = Class.forName("org.telegram.ui.ActionBar.ActionBarMenuItem", true, cl);
            Method m = itemClass.getDeclaredMethod("onTouchEvent", MotionEvent.class);
            HotHook.Unhook u = host.hook(m, new HotHook.Callback() {
                @Override
                public void before(HotHook.Call call) throws Throwable {
                    if (!isStickyEnabled()) return;
                    Object target = call.getThis();
                    if (target instanceof View) {
                        applySpringStateListAnimator((View) target);
                    }
                }
            });
            if (u != null) activeHooks.add(u);
            host.log("ui: hooked ActionBarMenuItem.onTouchEvent");
        } catch (Throwable t) {
            host.log("ui: hook ActionBarMenuItem failed: " + t.getMessage());
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

        rows.add(HotRow.header("Пружинна тактильність (PixelPlayer)"));
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
        if ("apply_live".equals(rowId) || "go_expressive".equals(rowId)) {
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
