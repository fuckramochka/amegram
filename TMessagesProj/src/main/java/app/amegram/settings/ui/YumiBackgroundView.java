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
 * A quiet, atmospheric Yumi backdrop: tonal base, three blurred brand glows,
 * two hairlines and a barely visible grain. It is deliberately static — a
 * settings screen should feel calm, not like a screensaver.
 */
public class YumiBackgroundView extends View {

    private static Bitmap darkNoise;
    private static Bitmap lightNoise;

    private final Paint basePaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noisePaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint bottomFadePaint = new Paint();

    private RadialGradient pinkGlow;
    private RadialGradient violetGlow;
    private RadialGradient cyanGlow;
    private LinearGradient bottomFade;

    public YumiBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(YumiMd3.dp(1));
        basePaint.setColor(YumiMd3.surface());
        noisePaint.setAlpha(YumiMd3.isDark() ? 255 : 255);
        Bitmap noise = noiseBitmap(YumiMd3.isDark());
        noisePaint.setShader(new BitmapShader(noise, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
        noisePaint.setAlpha(YumiMd3.isDark() ? 34 : 22);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        basePaint.setColor(YumiMd3.surface());

        int pink = YumiMd3.gradientEnd();
        int violet = YumiMd3.gradientStart();
        int cyan = 0xFF22D3EE;

        float pinkAlpha = YumiMd3.isDark() ? 0.20f : 0.15f;
        float violetAlpha = YumiMd3.isDark() ? 0.17f : 0.11f;
        float cyanAlpha = YumiMd3.isDark() ? 0.075f : 0.07f;

        pinkGlow = new RadialGradient(
                w * 0.88f, h * 0.08f, Math.max(w * 0.78f, YumiMd3.dp(280)),
                new int[]{
                        YumiMd3.withAlpha(pink, pinkAlpha),
                        YumiMd3.withAlpha(pink, pinkAlpha * 0.35f),
                        0x00000000
                },
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP);

        violetGlow = new RadialGradient(
                w * 0.02f, h * 0.34f, Math.max(w * 0.82f, YumiMd3.dp(300)),
                new int[]{
                        YumiMd3.withAlpha(violet, violetAlpha),
                        YumiMd3.withAlpha(violet, violetAlpha * 0.28f),
                        0x00000000
                },
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);

        cyanGlow = new RadialGradient(
                w * 0.92f, h * 0.86f, Math.max(w * 0.7f, YumiMd3.dp(240)),
                new int[]{
                        YumiMd3.withAlpha(cyan, cyanAlpha),
                        YumiMd3.withAlpha(cyan, cyanAlpha * 0.2f),
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

        if (pinkGlow != null) {
            glowPaint.setShader(pinkGlow);
            canvas.drawCircle(w * 0.88f, h * 0.08f, Math.max(w * 0.78f, YumiMd3.dp(280)), glowPaint);
            glowPaint.setShader(violetGlow);
            canvas.drawCircle(w * 0.02f, h * 0.34f, Math.max(w * 0.82f, YumiMd3.dp(300)), glowPaint);
            glowPaint.setShader(cyanGlow);
            canvas.drawCircle(w * 0.92f, h * 0.86f, Math.max(w * 0.7f, YumiMd3.dp(240)), glowPaint);
            glowPaint.setShader(null);
        }

        linePaint.setColor(YumiMd3.withAlpha(YumiMd3.gradientStart(),
                YumiMd3.isDark() ? 0.085f : 0.07f));
        canvas.drawCircle(w * 0.92f, h * 0.12f, w * 0.36f, linePaint);
        linePaint.setColor(YumiMd3.withAlpha(YumiMd3.gradientEnd(),
                YumiMd3.isDark() ? 0.065f : 0.055f));
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
                alpha = value < 10 ? 26 + random.nextInt(22) : 0;
            } else {
                alpha = value < 9 ? 18 + random.nextInt(16) : 0;
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
