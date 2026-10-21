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
 * are only used when the client is older than the token file. They are not a
 * second palette.</p>
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
    public int error;
    public int onError;

    private static final Map<String, Method> TOKENS = new HashMap<>();

    public YumiPalette(Context context) {
        refresh(context);
    }

    public void refresh(Context context) {
        final boolean dark = isDark(context);
        if (dark) {
            surface = token("surface", 0xFF141114, true);
            surfaceContainerLow = token("surfaceContainerLow", 0xFF211C20, true);
            primary = token("primary", 0xFFFFB1C8, true);
            primaryContainer = token("primaryContainer", 0xFF7B0040, true);
            onPrimary = token("onPrimary", 0xFF3F0020, true);
            onSurface = token("onSurface", 0xFFF4EEF1, true);
            onSurfaceVariant = token("onSurfaceVariant", 0xFFD7C2C9, true);
            outline = token("outline", 0xFFA08C93, true);
            navSurface = token("navSurface", 0xFF1D181C, true);
            navIndicator = token("navIndicator", 0xFF4A2432, true);
            navActive = token("navActive", 0xFFFFB1C8, true);
            navInactive = token("navInactive", 0xFFD7C2C9, true);
            navBorder = token("navBorder", 0xFF3A2F34, true);
            error = token("error", 0xFFFFB4AB, true);
            onError = token("onError", 0xFF690005, true);
        } else {
            surface = token("surface", 0xFFFFFBF8, false);
            surfaceContainerLow = token("surfaceContainerLow", 0xFFF9F1F3, false);
            primary = token("primary", 0xFFB90063, false);
            primaryContainer = token("primaryContainer", 0xFFFFD9E4, false);
            onPrimary = token("onPrimary", 0xFFFFFFFF, false);
            onSurface = token("onSurface", 0xFF1D1B1E, false);
            onSurfaceVariant = token("onSurfaceVariant", 0xFF514349, false);
            outline = token("outline", 0xFF857377, false);
            navSurface = token("navSurface", 0xFFFFF8FA, false);
            navIndicator = token("navIndicator", 0xFFF8D7E2, false);
            navActive = token("navActive", 0xFFB90063, false);
            navInactive = token("navInactive", 0xFF514349, false);
            navBorder = token("navBorder", 0xFFE8D8DD, false);
            error = token("error", 0xFFB3261E, false);
            onError = token("onError", 0xFFFFFFFF, false);
        }
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
}
