package app.miogram.bridge.player;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.CubicBezierInterpolator;

/**
 * Vinyl 2.0 Audio Display: Realistic rotating vinyl record with groove textures,
 * dynamic radial shine, and an animated physical tonearm (needle).
 */
public class YumiVinylView extends View {

    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint groovePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint armPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap coverBitmap;
    private float discRotation = 0f;
    private float armAngle = -15f; // Rest position: -15 deg, Playing position: 18 deg
    private boolean isPlaying = false;

    private ValueAnimator spinAnimator;
    private ValueAnimator armAnimator;

    public YumiVinylView(Context context) {
        super(context);
        init();
    }

    private void init() {
        discPaint.setColor(0xFF111215);

        groovePaint.setStyle(Paint.Style.STROKE);
        groovePaint.setStrokeWidth(1.2f);
        groovePaint.setColor(0x1AFFFFFF);

        armPaint.setColor(0xFFCCCCCC);
        armPaint.setStyle(Paint.Style.STROKE);
        armPaint.setStrokeWidth(AndroidUtilities.dp(3.5f));
        armPaint.setStrokeCap(Paint.Cap.ROUND);

        headPaint.setColor(0xFFE5A93C); // Golden-orange cartridge
        headPaint.setStyle(Paint.Style.FILL);

        spinAnimator = ValueAnimator.ofFloat(0f, 360f);
        spinAnimator.setDuration(12000);
        spinAnimator.setRepeatCount(ValueAnimator.INFINITE);
        spinAnimator.setInterpolator(new LinearInterpolator());
        spinAnimator.addUpdateListener(animation -> {
            if (isPlaying) {
                discRotation = (discRotation + 0.8f) % 360f;
                invalidate();
            }
        });
    }

    public void setCoverBitmap(Bitmap bmp) {
        this.coverBitmap = bmp;
        invalidate();
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying == playing) return;
        this.isPlaying = playing;

        if (armAnimator != null) {
            armAnimator.cancel();
        }
        float target = playing ? 18f : -15f;
        armAnimator = ValueAnimator.ofFloat(armAngle, target);
        armAnimator.setDuration(550);
        armAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        armAnimator.addUpdateListener(animation -> {
            armAngle = (float) animation.getAnimatedValue();
            invalidate();
        });
        armAnimator.start();

        if (playing && !spinAnimator.isRunning()) {
            spinAnimator.start();
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (spinAnimator != null && !spinAnimator.isRunning()) {
            spinAnimator.start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (spinAnimator != null) spinAnimator.cancel();
        if (armAnimator != null) armAnimator.cancel();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.min(w, h) * 0.82f;
        float cx = w * 0.44f;
        float cy = h * 0.52f;
        float radius = size / 2f;

        // 1. Shadow underneath the vinyl disc
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x55000000);
        canvas.drawCircle(cx, cy + AndroidUtilities.dp(8), radius, shadowPaint);

        // 2. Vinyl disc body (Rotating)
        canvas.save();
        canvas.rotate(discRotation, cx, cy);

        canvas.drawCircle(cx, cy, radius, discPaint);

        // Concentric Grooves
        for (float r = radius * 0.45f; r < radius * 0.94f; r += AndroidUtilities.dp(4.5f)) {
            canvas.drawCircle(cx, cy, r, groovePaint);
        }

        // Specular sheen / reflections
        RadialGradient shine = new RadialGradient(cx, cy, radius,
                new int[]{0x00FFFFFF, 0x1FFFFFFF, 0x00FFFFFF, 0x18FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.35f, 0.55f, 0.78f, 1f}, Shader.TileMode.CLAMP);
        shinePaint.setShader(shine);
        canvas.drawCircle(cx, cy, radius, shinePaint);

        // Center Album Art Sticker (36% of radius)
        float stickerR = radius * 0.36f;
        if (coverBitmap != null) {
            Path clip = new Path();
            clip.addCircle(cx, cy, stickerR, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(clip);
            Rect src = new Rect(0, 0, coverBitmap.getWidth(), coverBitmap.getHeight());
            RectF dst = new RectF(cx - stickerR, cy - stickerR, cx + stickerR, cy + stickerR);
            Paint bmpPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(coverBitmap, src, dst, bmpPaint);
            canvas.restore();
        } else {
            Paint stickerBg = new Paint(Paint.ANTI_ALIAS_FLAG);
            stickerBg.setColor(0xFF5B8DEF);
            canvas.drawCircle(cx, cy, stickerR, stickerBg);
        }

        // Center spindle hole
        Paint spindlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        spindlePaint.setColor(0xFF000000);
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(5), spindlePaint);
        spindlePaint.setColor(0xFFCCCCCC);
        spindlePaint.setStyle(Paint.Style.STROKE);
        spindlePaint.setStrokeWidth(AndroidUtilities.dp(1.5f));
        canvas.drawCircle(cx, cy, AndroidUtilities.dp(5), spindlePaint);

        canvas.restore();

        // 3. Physical Tonearm (Pivot at top-right)
        if (MiogramPlayerPrefs.isVinylArmEnabled()) {
            float pivotX = cx + radius * 0.92f;
            float pivotY = cy - radius * 0.85f;

            // Pivot Base
            Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
            base.setColor(0xFF444448);
            canvas.drawCircle(pivotX, pivotY, AndroidUtilities.dp(12), base);
            base.setColor(0xFF888890);
            canvas.drawCircle(pivotX, pivotY, AndroidUtilities.dp(6), base);

            canvas.save();
            canvas.rotate(armAngle, pivotX, pivotY);

            // Arm rod
            float armLen = radius * 1.05f;
            canvas.drawLine(pivotX, pivotY, pivotX - armLen * 0.65f, pivotY + armLen * 0.75f, armPaint);
            canvas.drawLine(pivotX - armLen * 0.65f, pivotY + armLen * 0.75f, pivotX - armLen * 0.85f, pivotY + armLen * 0.95f, armPaint);

            // Headshell & Needle cartridge
            float headX = pivotX - armLen * 0.85f;
            float headY = pivotY + armLen * 0.95f;
            RectF headRect = new RectF(headX - AndroidUtilities.dp(12), headY - AndroidUtilities.dp(5),
                    headX + AndroidUtilities.dp(4), headY + AndroidUtilities.dp(7));
            canvas.drawRoundRect(headRect, 4, 4, headPaint);

            canvas.restore();
        }
    }
}
