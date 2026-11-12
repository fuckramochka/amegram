package app.miogram.bridge.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.view.animation.LinearInterpolator;

import androidx.core.graphics.ColorUtils;

/**
 * Living Fluid Mesh Aura background (Apple Music iOS 17 style).
 * Smoothly shifts multiple vibrant color spheres across the canvas, reacting to music playback.
 */
public class YumiFluidAuraView extends View {

    private final Paint paint1 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paint3 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int color1 = 0xFF4A154B;
    private int color2 = 0xFF192A56;
    private int color3 = 0xFF6F1E51;
    private int baseColor = 0xFF0D0E15;

    private float phase = 0f;
    private boolean isPlaying = true;
    private ValueAnimator animator;

    public YumiFluidAuraView(Context context) {
        super(context);
        init();
    }

    private void init() {
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(10000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(animation -> {
            if (getVisibility() == VISIBLE && getAlpha() > 0.05f) {
                float speed = isPlaying ? (MiogramPlayerPrefs.getFluidAuraSpeed() * 0.012f) : 0.003f;
                phase += speed;
                if (phase > 1000f) phase = 0f;
                invalidate();
            }
        });
    }

    public void setColors(int c1, int c2, int c3, int bg) {
        this.color1 = c1;
        this.color2 = c2;
        this.color3 = c3;
        this.baseColor = bg;
        invalidate();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null && !animator.isRunning()) {
            animator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Base background
        basePaint.setColor(baseColor);
        canvas.drawRect(0, 0, w, h, basePaint);

        if (!MiogramPlayerPrefs.isFluidAuraEnabled()) {
            return;
        }

        float cx1 = w * 0.35f + (float) Math.sin(phase * 1.1f) * w * 0.22f;
        float cy1 = h * 0.30f + (float) Math.cos(phase * 0.9f) * h * 0.18f;
        float r1 = Math.max(w, h) * 0.55f;

        RadialGradient g1 = new RadialGradient(cx1, cy1, r1,
                ColorUtils.setAlphaComponent(color1, 0x88), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint1.setShader(g1);
        canvas.drawCircle(cx1, cy1, r1, paint1);

        float cx2 = w * 0.70f + (float) Math.cos(phase * 0.8f) * w * 0.25f;
        float cy2 = h * 0.60f + (float) Math.sin(phase * 1.3f) * h * 0.20f;
        float r2 = Math.max(w, h) * 0.60f;

        RadialGradient g2 = new RadialGradient(cx2, cy2, r2,
                ColorUtils.setAlphaComponent(color2, 0x77), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint2.setShader(g2);
        canvas.drawCircle(cx2, cy2, r2, paint2);

        float cx3 = w * 0.50f + (float) Math.sin(phase * 1.4f) * w * 0.20f;
        float cy3 = h * 0.80f + (float) Math.cos(phase * 1.1f) * h * 0.15f;
        float r3 = Math.max(w, h) * 0.50f;

        RadialGradient g3 = new RadialGradient(cx3, cy3, r3,
                ColorUtils.setAlphaComponent(color3, 0x66), Color.TRANSPARENT, Shader.TileMode.CLAMP);
        paint3.setShader(g3);
        canvas.drawCircle(cx3, cy3, r3, paint3);
    }
}
