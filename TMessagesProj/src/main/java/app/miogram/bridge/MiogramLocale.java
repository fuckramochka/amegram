package app.miogram.bridge;

import org.telegram.messenger.LocaleController;
import java.util.Locale;

/**
 * Clean and lightweight dynamic localization helper for Miogram components.
 * Automatically adapts to user's Telegram language settings (Ukrainian, Russian, English/Default).
 */
public class MiogramLocale {

    public static boolean isUkrainian() {
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            if (locale == null) {
                locale = Locale.getDefault();
            }
            return locale.getLanguage().toLowerCase().startsWith("uk");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String getLanguageCode() {
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            if (locale == null) {
                locale = Locale.getDefault();
            }
            String lang = locale.getLanguage().toLowerCase();
            if (lang.startsWith("uk")) return "uk";
            if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) return "ru";
        } catch (Throwable ignored) {}
        return "en";
    }

    public static String get(String uk, String ru, String en) {
        try {
            Locale locale = LocaleController.getInstance().getCurrentLocale();
            if (locale == null) {
                locale = Locale.getDefault();
            }
            String lang = locale.getLanguage().toLowerCase();
            if (lang.startsWith("uk")) {
                return uk;
            } else if (lang.startsWith("ru") || lang.startsWith("be") || lang.startsWith("kk")) {
                return ru;
            }
        } catch (Throwable ignored) {}
        return en;
    }

    public static String format(String uk, String ru, String en, Object... args) {
        String template = get(uk, ru, en);
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
        }
    }

    /**
     * Correct plural form for Ukrainian, Russian and English counts.
     * Pass three forms for uk, three for ru and two for en.
     */
    public static String plural(int count, String ukOne, String ukFew, String ukMany,
                                String ruOne, String ruFew, String ruMany,
                                String enOne, String enMany) {
        String language = getLanguageCode();
        if ("uk".equals(language) || "ru".equals(language)) {
            boolean one = count % 10 == 1 && count % 100 != 11;
            boolean few = count % 10 >= 2 && count % 10 <= 4
                    && (count % 100 < 12 || count % 100 > 14);
            if (one) {
                return "uk".equals(language) ? ukOne : ruOne;
            }
            if (few) {
                return "uk".equals(language) ? ukFew : ruFew;
            }
            return "uk".equals(language) ? ukMany : ruMany;
        }
        return count == 1 ? enOne : enMany;
    }
}
