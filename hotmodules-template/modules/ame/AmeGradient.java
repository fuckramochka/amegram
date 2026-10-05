package com.amegram.mods.ame;

import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;

/**
 * ✦ AME GRADIENT HELPER ✦
 * Advanced gradient mathematics and animated sweep engine for Ame Profile Studio:
 * - Arbitrary gradient angles (0° - 360°) mapped to GradientDrawable.Orientation.
 * - Multi-stop gradient generation (start, middle, end colors).
 * - Continuous high-performance animated gradient sweep with configurable speed.
 */
public class AmeGradient {

    private static final Matrix sweepMatrix = new Matrix();

    /**
     * Maps an angle in degrees (0 - 360) to the closest GradientDrawable.Orientation.
     */
    public static GradientDrawable.Orientation getOrientationForAngle(float degrees) {
        float normalized = ((degrees % 360) + 360) % 360;
        if (normalized >= 337.5f || normalized < 22.5f) {
            return GradientDrawable.Orientation.LEFT_RIGHT;
        } else if (normalized >= 22.5f && normalized < 67.5f) {
            return GradientDrawable.Orientation.BL_TR;
        } else if (normalized >= 67.5f && normalized < 112.5f) {
            return GradientDrawable.Orientation.BOTTOM_TOP;
        } else if (normalized >= 112.5f && normalized < 157.5f) {
            return GradientDrawable.Orientation.BR_TL;
        } else if (normalized >= 157.5f && normalized < 202.5f) {
            return GradientDrawable.Orientation.RIGHT_LEFT;
        } else if (normalized >= 202.5f && normalized < 247.5f) {
            return GradientDrawable.Orientation.TR_BL;
        } else if (normalized >= 247.5f && normalized < 292.5f) {
            return GradientDrawable.Orientation.TOP_BOTTOM;
        } else {
            return GradientDrawable.Orientation.TL_BR;
        }
    }

    /**
     * Builds a GradientDrawable using custom angle and color stops.
     */
    public static GradientDrawable createGradient(int color1, int color2, int color3, float angle, float cornerRadiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setOrientation(getOrientationForAngle(angle));

        if (color3 != 0) {
            gd.setColors(new int[]{color1, color2, color3});
        } else if (color1 != 0 && color2 != 0) {
            gd.setColors(new int[]{color1, color2});
        } else if (color1 != 0) {
            gd.setColor(color1);
        } else if (color2 != 0) {
            gd.setColor(color2);
        }

        if (cornerRadiusDp > 0) {
            gd.setCornerRadius(org.telegram.messenger.AndroidUtilities.dp(cornerRadiusDp));
        }
        return gd;
    }

    /**
     * Calculates the animated sweep progress (0.0f - 1.0f) based on time and speed factor.
     * Speed = 1.0f means 1 full sweep every 3 seconds.
     */
    public static float getSweepProgress(float speed) {
        if (speed <= 0) return 0f;
        long period = (long) (3000f / Math.max(0.1f, speed));
        long now = SystemClock.uptimeMillis();
        return (now % period) / (float) period;
    }

    /**
     * Creates a LinearGradient shader with precise angle and animated matrix translation.
     */
    public static LinearGradient createAnimatedShader(int[] colors, float angleDegrees, float width, float height, float speed) {
        if (colors == null || colors.length < 2 || width <= 0 || height <= 0) return null;

        double rad = Math.toRadians(angleDegrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);

        float cx = width / 2f;
        float cy = height / 2f;
        float halfLen = (float) Math.hypot(width, height) / 2f;

        float x0 = cx - cos * halfLen;
        float y0 = cy - sin * halfLen;
        float x1 = cx + cos * halfLen;
        float y1 = cy + sin * halfLen;

        LinearGradient shader = new LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.MIRROR);

        if (speed > 0) {
            float progress = getSweepProgress(speed);
            sweepMatrix.reset();
            sweepMatrix.postTranslate(cos * (width * 2f) * progress, sin * (height * 2f) * progress);
            shader.setLocalMatrix(sweepMatrix);
        }
        return shader;
    }
}
