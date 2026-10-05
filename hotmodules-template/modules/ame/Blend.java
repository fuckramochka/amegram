package com.amegram.mods.ame;

/** blendARGB без androidx (заміна ColorUtils). */
public final class Blend {

    private Blend() {
    }

    public static int setAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int blendARGB(int color1, int color2, float ratio) {
        float inv = 1f - ratio;
        int a = (int) (android.graphics.Color.alpha(color1) * inv + android.graphics.Color.alpha(color2) * ratio);
        int r = (int) (android.graphics.Color.red(color1) * inv + android.graphics.Color.red(color2) * ratio);
        int g = (int) (android.graphics.Color.green(color1) * inv + android.graphics.Color.green(color2) * ratio);
        int b = (int) (android.graphics.Color.blue(color1) * inv + android.graphics.Color.blue(color2) * ratio);
        return android.graphics.Color.argb(a, r, g, b);
    }
}
