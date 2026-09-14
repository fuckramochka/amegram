package app.miogram.bridge.fun;

import android.content.Context;

import java.util.Locale;

/**
 * Easter egg: "rules" — same blackout player as musordrop, but plays
 * rule.mp4 (bundled asset, Downloads fallback) via tg://rules link.
 */
public final class MiogramRuleDrop {

    public static final String TRIGGER_URL = "tg://rules";

    private MiogramRuleDrop() {}

    /**
     * Checks if the given URL triggers the rules easter egg.
     */
    public static boolean isTrigger(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(Locale.US);
        while (u.endsWith("/") || u.endsWith(" ") || u.endsWith("?")) {
            u = u.substring(0, u.length() - 1);
        }
        return u.equals("tg://rules") || u.equals("tg:rules");
    }

    /**
     * Attempts to handle and display the easter egg.
     * @return true when handled (caller should suppress default navigation/sending).
     */
    public static boolean tryHandle(Context context) {
        return MiogramMusorDrop.tryHandleMedia(context, "rule.mp4",
                "RULES НЕ ЗНАЙДЕНО\n\nПокладіть rule.mp4\nу папку Завантаження (Downloads)");
    }
}
