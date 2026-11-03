package com.amegram.mods.ui;

import java.lang.reflect.Method;
import java.util.Locale;

/** Динамічна локалізація: автоматична адаптація під активну мову Telegram (UK / RU / EN). */
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
        try {
            Class<?> lc = Class.forName("org.telegram.messenger.LocaleController");
            Object inst = lc.getMethod("getInstance").invoke(null);
            Locale current = (Locale) lc.getMethod("getCurrentLocale").invoke(inst);
            if (current != null) {
                String lang = current.getLanguage().toLowerCase();
                if (lang.startsWith("uk")) return uk;
                if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) return ru;
            }
        } catch (Throwable ignore) {
        }
        return uk != null ? uk : (en != null ? en : "");
    }
}
