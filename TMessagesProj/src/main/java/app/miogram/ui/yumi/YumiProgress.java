package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import app.amegram.theme.YumiMd3;

/** Linear/circular Yumi progress indicator. Drawing is allocation-free. */
public class YumiProgress extends View {

    public static final int MODE_LINEAR = 0;
    public static final int MODE_CIRCULAR = 1;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();

    private int mode = MODE_LINEAR;
    private boolean indeterminate;
    private float progress;
    private int accent = YumiMd3.primary();
    private long animationStart;

    public YumiProgress(Context context) {
        this(context, MODE_LINEAR);
    }

    public YumiProgress(Context context, int mode) {
        super(context);
        this.mode = mode;
        trackPaint.setColor(YumiMd3.sliderTrack());
        progressPaint.setColor(accent);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setContentDescription(YumiLocale.loading());
    }

    public void setMode(int mode) {
        this.mode = mode;
        requestLayout();
        invalidate();
    }

    public void setProgress(float progress) {
        this.progress = Math.max(0f, Math.min(1f, progress));
        if (!indeterminate) {
            invalidate();
        }
    }

    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate == indeterminate) {
            return;
        }
        this.indeterminate = indeterminate;
        animationStart = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setAccent(int accent) {
        this.accent = accent;
        progressPaint.setColor(accent);
        invalidate();
    }

    public void setTrackColor(int color) {
        trackPaint.setColor(color);
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (mode == MODE_CIRCULAR) {
            final int size = Math.min(MeasureSpec.getSize(widthMeasureSpec), YumiUi.dp(48));
            setMeasuredDimension(size, size);
        } else {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, YumiUi.dp(8));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mode == MODE_CIRCULAR) {
            drawCircular(canvas);
        } else {
            drawLinear(canvas);
        }
    }

    private void drawLinear(Canvas canvas) {
        final float h = YumiUi.dp(6);
        final float y = getHeight() / 2f;
        trackPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(0, y - h / 2f, getWidth(), y + h / 2f, h / 2f, h / 2f, trackPaint);
        float start;
        float end;
        if (indeterminate) {
            if (!YumiPowerSave.animations()) {
                start = 0;
                end = getWidth() * 0.35f;
            } else {
                final float cycle = ((SystemClock.uptimeMillis() - animationStart) % 1400L) / 1400f;
                final float head = cycle * 1.4f - 0.2f;
                start = Math.max(0, head - 0.35f) * getWidth();
                end = Math.min(1f, head) * getWidth();
                postInvalidateOnAnimation();
            }
        } else {
            start = 0;
            end = getWidth() * progress;
        }
        if (end > start) {
            canvas.drawRoundRect(start, y - h / 2f, end, y + h / 2f, h / 2f, h / 2f, progressPaint);
        }
    }

    private void drawCircular(Canvas canvas) {
        final float stroke = YumiUi.dp(4);
        final float inset = stroke / 2f + YumiUi.dp(2);
        arcRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(stroke);
        progressPaint.setStrokeWidth(stroke);
        canvas.drawArc(arcRect, 0, 360, false, trackPaint);
        if (indeterminate) {
            float rotation;
            if (!YumiPowerSave.animations()) {
                rotation = -90f;
            } else {
                rotation = ((SystemClock.uptimeMillis() - animationStart) % 1000L) / 1000f * 360f - 90f;
                postInvalidateOnAnimation();
            }
            canvas.drawArc(arcRect, rotation, 100, false, progressPaint);
        } else {
            canvas.drawArc(arcRect, -90, 360 * progress, false, progressPaint);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animationStart = SystemClock.uptimeMillis();
        if (indeterminate) {
            invalidate();
        }
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.ProgressBar");
        if (!indeterminate) {
            info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                    AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 1f, progress));
        }
    }

    public void updateColors() {
        trackPaint.setColor(YumiMd3.sliderTrack());
        progressPaint.setColor(accent);
        invalidate();
    }
}
