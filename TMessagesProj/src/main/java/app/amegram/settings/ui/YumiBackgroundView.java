package app.amegram.settings.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Random;

import app.amegram.theme.YumiMd3;

/**
 * A quiet, atmospheric Yumi backdrop: tonal base, dynamic theme glows,
 * subtle hairlines and barely visible grain. Adapts dynamically to
 * the active Telegram theme accent.
 */
public class YumiBackgroundView extends View {

    private static Bitmap darkNoise;
    private static Bitmap lightNoise;

    private final Paint basePaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bottomFadePaint = new Paint();

    private RadialGradient primaryGlow;
    private RadialGradient secondaryGlow;
    private RadialGradient tertiaryGlow;
    private LinearGradient bottomFade;

    public YumiBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(YumiMd3.dp(1));
        basePaint.setColor(YumiMd3.surface());
        Bitmap noise = noiseBitmap(YumiMd3.isDark());
        noisePaint.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        noisePaint.setAlpha(YumiMd3.isDark() ? 28 : 18);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        basePaint.setColor(YumiMd3.surface());

        int primary = YumiMd3.primary();
        int secondary = YumiMd3.secondary();
        int tertiary = YumiMd3.tertiary();

        float pAlpha = YumiMd3.isDark() ? 0.16f : 0.11f;
        float sAlpha = YumiMd3.isDark() ? 0.13f : 0.08f;
        float tAlpha = YumiMd3.isDark() ? 0.06f : 0.05f;

        primaryGlow = new RadialGradient(
                w * 0.88f, h * 0.08f, Math.max(w * 0.78f, YumiMd3.dp(280)),
                new int[]{
                        YumiMd3.withAlpha(primary, pAlpha),
                        YumiMd3.withAlpha(primary, pAlpha * 0.35f),
                        0x00000000
                },
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        secondaryGlow = new RadialGradient(
                w * 0.02f, h * 0.34f, Math.max(w * 0.82f, YumiMd3.dp(300)),
                new int[]{
                        YumiMd3.withAlpha(secondary, sAlpha),
                        YumiMd3.withAlpha(secondary, sAlpha * 0.28f),
                        0x00000000
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        tertiaryGlow = new RadialGradient(
                w * 0.92f, h * 0.86f, Math.max(w * 0.7f, YumiMd3.dp(240)),
                new int[]{
                        YumiMd3.withAlpha(tertiary, tAlpha),
                        YumiMd3.withAlpha(tertiary, tAlpha * 0.2f),
                        0x00000000
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        bottomFade = new LinearGradient(
                0, h * 0.72f, 0, h,
                new int[]{0x00000000, YumiMd3.withAlpha(YumiMd3.surface(), 0.46f)},
                null, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, basePaint);

        if (primaryGlow != null) {
            glowPaint.setShader(primaryGlow);
            canvas.drawCircle(w * 0.88f, h * 0.08f, Math.max(w * 0.78f, YumiMd3.dp(280)), glowPaint);
            glowPaint.setShader(secondaryGlow);
            canvas.drawCircle(w * 0.02f, h * 0.34f, Math.max(w * 0.82f, YumiMd3.dp(300)), glowPaint);
            glowPaint.setShader(tertiaryGlow);
            canvas.drawCircle(w * 0.92f, h * 0.86f, Math.max(w * 0.7f, YumiMd3.dp(240)), glowPaint);
            glowPaint.setShader(null);
        }

        linePaint.setColor(YumiMd3.withAlpha(YumiMd3.primary(),
                YumiMd3.isDark() ? 0.07f : 0.05f));
        canvas.drawCircle(w * 0.92f, h * 0.12f, w * 0.36f, linePaint);
        linePaint.setColor(YumiMd3.withAlpha(YumiMd3.secondary(),
                YumiMd3.isDark() ? 0.055f : 0.04f));
        canvas.drawCircle(w * -0.05f, h * 0.46f, w * 0.30f, linePaint);

        if (bottomFade != null) {
            bottomFadePaint.setShader(bottomFade);
            canvas.drawRect(0, h * 0.72f, w, h, bottomFadePaint);
            bottomFadePaint.setShader(null);
        }

        if (noisePaint.getShader() != null) {
            canvas.drawRect(0, 0, w, h, noisePaint);
        }
    }

    private static Bitmap noiseBitmap(boolean dark) {
        if (dark && darkNoise != null) {
            return darkNoise;
        }
        if (!dark && lightNoise != null) {
            return lightNoise;
        }
        final int size = 112;
        int[] pixels = new int[size * size];
        Random random = new Random(0xA3F19B);
        for (int i = 0; i < pixels.length; i++) {
            int value = random.nextInt(100);
            int alpha;
            if (dark) {
                alpha = value < 10 ? 24 + random.nextInt(20) : 0;
            } else {
                alpha = value < 9 ? 16 + random.nextInt(14) : 0;
            }
            pixels[i] = (alpha << 24) | (dark ? 0xFFFFFFFF : 0xFF000000);
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
        if (dark) {
            darkNoise = bitmap;
        } else {
            lightNoise = bitmap;
        }
        return bitmap;
    }
}
