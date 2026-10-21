package com.amegram.mods.ui;

import java.lang.reflect.Method;

/** Small module-side localization bridge to the client MiogramLocale. */
final class YumiText {

    private YumiText() {
    }

    static String tr(String uk, String ru, String en) {
        try {
            final Class<?> locale = Class.forName("app.miogram.bridge.MiogramLocale");
            final Method get = locale.getMethod("get", String.class, String.class, String.class);
            final Object value = get.invoke(null, uk, ru, en);
            if (value instanceof String) {
                return (String) value;
            }
        } catch (Throwable ignore) {
        }
        return en;
    }
}
