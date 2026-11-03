package com.amegram.mods.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Module-side bridge to the client Yumi tokens.
 *
 * <p>The expressive UI is shipped as a hot module, so it must not link against
 * client implementation classes at build time. Colors are read from
 * {@code app.amegram.theme.YumiMd3} reflectively and cached; the fallback values
 * are only used when the client is older than the token file. If the module has
 * an active accent mode selected, it overrides the client tokens.</p>
 */
public final class YumiPalette {

    public int surface;
    public int surfaceContainerLow;
    public int primary;
    public int primaryContainer;
    public int onPrimary;
    public int onSurface;
    public int onSurfaceVariant;
    public int outline;
    public int navSurface;
    public int navIndicator;
    public int navActive;
    public int navInactive;
    public int navBorder;
    public int navGlass;
    public int navRim;
    public int cardSurface;
    public int cardRim;
    public int separator;
    public int secondaryFill;
    public int textSecondary;
    public int error;
    public int onError;

    private final UiModule owner;
    private static final Map<String, Method> TOKENS = new HashMap<>();

    public YumiPalette(Context context) {
        this(context, null);
    }

    public YumiPalette(Context context, UiModule owner) {
        this.owner = owner;
        refresh(context);
    }

    public void refresh(Context context) {
        final boolean dark = isDark(context);
        final int customAccent = owner != null ? owner.getCustomAccentColor(dark) : 0;

        if (dark) {
            surface = token("surface", 0xFF141114, true);
            surfaceContainerLow = token("surfaceContainerLow", 0xFF1A171A, true);
            primary = customAccent != 0 ? customAccent : token("primary", 0xFFFF8FB7, true);
            primaryContainer = customAccent != 0
                    ? blend(surface, primary, 0.28f)
                    : token("primaryContainer", 0xFF5C1026, true);
            onPrimary = customAccent != 0
                    ? (calculateLuminance(primary) > 0.5f ? 0xFF000000 : 0xFFFFFFFF)
                    : token("onPrimary", 0xFFFFFFFF, true);
            onSurface = token("onSurface", 0xFFF4EEF1, true);
            onSurfaceVariant = token("onSurfaceVariant", 0xFFC4B8BC, true);
            outline = token("outline", 0xFF8F8287, true);
            navSurface = token("navSurface", 0xFF1C171A, true);
            navIndicator = customAccent != 0
                    ? withAlpha(primary, 0.26f)
                    : token("navIndicator", 0x3DE5486B, true);
            navActive = customAccent != 0
                    ? primary
                    : token("navActive", 0xFFFF8FB7, true);
            navInactive = token("navInactive", 0xFFC4B8BC, true);
            navBorder = customAccent != 0
                    ? withAlpha(primary, 0.20f)
                    : token("navBorder", 0x2AE5486B, true);
            navGlass = Color.argb(230, 22, 25, 32);
            navRim = Color.argb(26, 255, 255, 255);
            cardSurface = surfaceContainerLow != 0 ? surfaceContainerLow : 0xFF1A171A;
            cardRim = Color.argb(0, 0, 0, 0);
            separator = Color.argb(24, 255, 255, 255);
            secondaryFill = token("surfaceContainerHigh", 0xFF21252F, true);
            textSecondary = onSurfaceVariant != 0 ? onSurfaceVariant : 0xFF9299A8;
            error = token("error", 0xFFFFB4AB, true);
            onError = token("onError", 0xFF690005, true);
        } else {
            surface = token("surface", 0xFFFFFBF8, false);
            surfaceContainerLow = token("surfaceContainerLow", 0xFFF9F1F3, false);
            primary = customAccent != 0 ? customAccent : token("primary", 0xFFB83268, false);
            primaryContainer = customAccent != 0
                    ? blend(0xFFFFFFFF, primary, 0.16f)
                    : token("primaryContainer", 0xFFFFD9E4, false);
            onPrimary = customAccent != 0
                    ? (calculateLuminance(primary) > 0.5f ? 0xFF000000 : 0xFFFFFFFF)
                    : token("onPrimary", 0xFFFFFFFF, false);
            onSurface = token("onSurface", 0xFF1D1B1E, false);
            onSurfaceVariant = token("onSurfaceVariant", 0xFF514349, false);
            outline = token("outline", 0xFF857377, false);
            navSurface = token("navSurface", 0xFFFFF8FA, false);
            navIndicator = customAccent != 0
                    ? withAlpha(primary, 0.14f)
                    : token("navIndicator", 0x2AD81B60, false);
            navActive = customAccent != 0
                    ? primary
                    : token("navActive", 0xFFB83268, false);
            navInactive = token("navInactive", 0xFF514349, false);
            navBorder = customAccent != 0
                    ? withAlpha(primary, 0.16f)
                    : token("navBorder", 0x2AB83268, false);
            navGlass = Color.argb(230, 248, 249, 250);
            navRim = Color.argb(24, 0, 0, 0);
            cardSurface = 0xFFFFFFFF;
            cardRim = Color.argb(0, 0, 0, 0);
            separator = Color.argb(20, 0, 0, 0);
            secondaryFill = token("surfaceContainerHigh", 0xFFE0E2EC, false);
            textSecondary = onSurfaceVariant != 0 ? onSurfaceVariant : 0xFF44474E;
            error = token("error", 0xFFBA1A1A, false);
            onError = token("onError", 0xFFFFFFFF, false);
        }
    }

    public int getColorToken(String token, int fallback) {
        final boolean dark = isDark(null);
        final int customAccent = owner != null ? owner.getCustomAccentColor(dark) : 0;
        if (customAccent != 0) {
            if ("primary".equals(token) || "navActive".equals(token)) return primary;
            if ("primaryContainer".equals(token)) return primaryContainer;
            if ("onPrimary".equals(token)) return onPrimary;
            if ("navIndicator".equals(token)) return navIndicator;
            if ("navBorder".equals(token)) return navBorder;
        }
        return fallback;
    }

    private int token(String method, int fallback, boolean dark) {
        final String key = (dark ? "d:" : "l:") + method;
        try {
            Method cached = TOKENS.get(key);
            if (cached == null) {
                Class<?> cls = Class.forName("app.amegram.theme.YumiMd3");
                cached = cls.getMethod(method);
                TOKENS.put(key, cached);
            }
            final Object value = cached.invoke(null);
            return value instanceof Integer ? (Integer) value : fallback;
        } catch (Throwable ignore) {
            return fallback;
        }
    }

    private static boolean isDark(Context context) {
        try {
            Class<?> theme = Class.forName("org.telegram.ui.ActionBar.Theme");
            Method method = theme.getMethod("isCurrentThemeDark");
            Object value = method.invoke(null);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
        } catch (Throwable ignore) {
        }
        if (context != null) {
            final int mode = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    public static int alpha(int color, float alpha) {
        return Color.argb(Math.round(255 * alpha), Color.red(color),
                Color.green(color), Color.blue(color));
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int blend(int first, int second, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        float ir = 1f - r;
        int a = Math.round(Color.alpha(first) * ir + Color.alpha(second) * r);
        int red = Math.round(Color.red(first) * ir + Color.red(second) * r);
        int green = Math.round(Color.green(first) * ir + Color.green(second) * r);
        int blue = Math.round(Color.blue(first) * ir + Color.blue(second) * r);
        return Color.argb(a, red, green, blue);
    }

    public static float calculateLuminance(int color) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }
}
