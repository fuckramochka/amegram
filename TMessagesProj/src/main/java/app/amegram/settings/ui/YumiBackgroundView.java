package app.amegram.settings.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import app.amegram.theme.YumiMd3;

/**
 * Authentic Material Design 3 background surface.
 * Calm, tonal base conforming to Google Material You guidelines.
 */
public class YumiBackgroundView extends View {

    private final Paint basePaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private RadialGradient ambientGlow;

    public YumiBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        basePaint.setColor(YumiMd3.surface());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        basePaint.setColor(YumiMd3.surface());

        int primary = YumiMd3.primary();
        float pAlpha = YumiMd3.isDark() ? 0.06f : 0.035f;

        ambientGlow = new RadialGradient(
                w * 0.85f, h * 0.10f, Math.max(w * 0.9f, YumiMd3.dp(320)),
                new int[]{
                        YumiMd3.withAlpha(primary, pAlpha),
                        YumiMd3.withAlpha(primary, pAlpha * 0.25f),
                        0x00000000
                },
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0, 0, w, h, basePaint);

        if (ambientGlow != null) {
            glowPaint.setShader(ambientGlow);
            canvas.drawRect(0, 0, w, h, glowPaint);
            glowPaint.setShader(null);
        }
    }
}
