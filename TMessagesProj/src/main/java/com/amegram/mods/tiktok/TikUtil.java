package com.amegram.mods.tiktok;

import java.text.DecimalFormat;

/** formatCount з AmegramTikTokManager (чистий). */
public final class TikUtil {

    private TikUtil() {
    }

    public static String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1_000_000) {
            double v = count / 1000.0;
            return new DecimalFormat("#.#K").format(v);
        }
        if (count < 1_000_000_000) {
            double v = count / 1_000_000.0;
            return new DecimalFormat("#.#M").format(v);
        }
        double v = count / 1_000_000_000.0;
        return new DecimalFormat("#.#B").format(v);
    }
}
