package com.amegram.mods.ame;

import org.telegram.messenger.LocaleController;

import java.util.Locale;

/** Тримовна обгортка (заміна MiogramLocale, без залежностей). */
public final class T {

    private T() {
    }

    public static String get(String uk, String ru, String en) {
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            if (locale == null) locale = Locale.getDefault();
            String lang = locale.getLanguage().toLowerCase();
            if (lang.startsWith("uk")) return uk;
            if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) return ru;
        } catch (Throwable ignore) {
        }
        return en;
    }
}
