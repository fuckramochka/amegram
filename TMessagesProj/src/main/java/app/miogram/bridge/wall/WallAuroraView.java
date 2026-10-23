package app.miogram.bridge.wall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

import org.telegram.messenger.AndroidUtilities;

import java.util.Random;

/**
 * Атмосферний фон стіни: нічний градієнт + повільні неонові аврори + зорі.
 * Без алокацій у onDraw: усі Paint/градієнти створюються в onSizeChanged.
 */
public class WallAuroraView extends View {

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint starPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Shader bgShader;
    private Shader blob1, blob2, blob3, ribbon;
    private float[] starX = new float[0];
    private float[] starY = new float[0];
    private float[] starR = new float[0];
    private int[] starA = new int[0];

    private float phase = 0f;
    private ValueAnimator animator;

    private int width;
    private int height;

    public WallAuroraView(Context context) {
        super(context);
        setWillNotDraw(false);
        setLayerType(LAYER_TYPE_HARDWARE, null);
        starPaint.setColor(Color.WHITE);
        glowPaint.setStyle(Paint.Style.FILL);
    }

    public void start() {
        if (!WallPrefs.animationsEnabled()) {
            invalidate();
            return;
        }
        if (animator != null && animator.isRunning()) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(24000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = Math.max(1, w);
        height = Math.max(1, h);
        boolean dark = WallUi.dark();
        if (dark) {
            bgShader = new LinearGradient(0, 0, width, height,
                    new int[]{0xFF080512, 0xFF120A24, 0xFF070C18},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        } else {
            bgShader = new LinearGradient(0, 0, width, height,
                    new int[]{0xFFFFF5FB, 0xFFF5F1FF, 0xFFEFFBFF},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        }
        bgPaint.setShader(bgShader);

        float r1 = width * 0.9f;
        blob1 = new RadialGradient(0, 0, r1,
                new int[]{dark ? 0x66FF4D8D : 0x44FF4D8D, dark ? 0x22FF4D8D : 0x11FF4D8D, 0x00FF4D8D},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        blob2 = new RadialGradient(0, 0, width * 0.85f,
                new int[]{dark ? 0x558A5CFF : 0x338A5CFF, dark ? 0x1F8A5CFF : 0x0D8A5CFF, 0x008A5CFF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        blob3 = new RadialGradient(0, 0, width * 0.7f,
                new int[]{dark ? 0x4435E0FF : 0x2A35E0FF, dark ? 0x1835E0FF : 0x0A35E0FF, 0x0035E0FF},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);

        ribbon = new LinearGradient(0, 0, width, 0,
                new int[]{0x00FF4D8D, 0x33FF4D8D, 0x338A5CFF, 0x0035E0FF},
                new float[]{0f, 0.3f, 0.7f, 1f}, Shader.TileMode.CLAMP);

        Random rnd = new Random(42);
        int stars = Math.max(28, width * height / 26000);
        starX = new float[stars];
        starY = new float[stars];
        starR = new float[stars];
        starA = new int[stars];
        for (int i = 0; i < stars; i++) {
            starX[i] = rnd.nextFloat() * width;
            starY[i] = rnd.nextFloat() * height;
            starR[i] = AndroidUtilities.dp(0.6f + rnd.nextFloat() * 1.4f);
            starA[i] = 30 + rnd.nextInt(90);
        }
    }

    private float pulse(float offset) {
        return (float) (0.5 + 0.5 * Math.sin((phase * Math.PI * 2) + offset));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRect(0, 0, width, height, bgPaint);

        float t = phase * (float) (Math.PI * 2);
        float cx1 = width * (0.18f + 0.06f * (float) Math.sin(t));
        float cy1 = height * (0.12f + 0.05f * (float) Math.cos(t * 0.9f));
        canvas.save();
        canvas.translate(cx1, cy1);
        blobPaint.setShader(blob1);
        canvas.drawCircle(0, 0, width * 0.9f, blobPaint);
        canvas.restore();

        float cx2 = width * (0.9f + 0.05f * (float) Math.cos(t * 0.8f));
        float cy2 = height * (0.32f + 0.08f * (float) Math.sin(t * 0.7f));
        canvas.save();
        canvas.translate(cx2, cy2);
        blobPaint.setShader(blob2);
        canvas.drawCircle(0, 0, width * 0.85f, blobPaint);
        canvas.restore();

        float cx3 = width * (0.55f + 0.08f * (float) Math.sin(t * 0.6f + 1.7f));
        float cy3 = height * (0.78f + 0.06f * (float) Math.cos(t * 0.5f));
        canvas.save();
        canvas.translate(cx3, cy3);
        blobPaint.setShader(blob3);
        canvas.drawCircle(0, 0, width * 0.7f, blobPaint);
        canvas.restore();

        // тонка неонова стрічка-хвиля
        glowPaint.setShader(ribbon);
        glowPaint.setAlpha(90 + (int) (40 * pulse(0.4f)));
        canvas.save();
        canvas.translate(0, height * 0.42f + (float) Math.sin(t) * AndroidUtilities.dp(10));
        canvas.drawRect(0, 0, width, AndroidUtilities.dp(1.2f), glowPaint);
        canvas.restore();

        for (int i = 0; i < starX.length; i++) {
            float twinkle = 0.55f + 0.45f * pulse(i * 0.7f);
            starPaint.setAlpha((int) (starA[i] * twinkle));
            canvas.drawCircle(starX[i], starY[i], starR[i], starPaint);
        }
    }
}
