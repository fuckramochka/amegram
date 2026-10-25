package app.amegram.theme;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Authentic Google Material Design 3 switch (Android 14/15 Pixel style).
 *
 * Dimensions: 52dp track width, 32dp track height, 16dp corner radius.
 * Unchecked: outline track with surfaceContainerHighest fill, 16dp outline thumb.
 * Checked: primary solid fill, 24dp onPrimary thumb with primary checkmark.
 */
public class YumiSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(YumiSwitch view, boolean checked);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path checkPath = new Path();
    private final RectF trackRect = new RectF();

    private final int touchSlop;
    private float downX;
    private float downY;
    private boolean pressed;

    private boolean checked;
    private float progress;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;
    private int accent;

    public YumiSwitch(Context context) {
        super(context);
        accent = YumiMd3.primary();
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(YumiMd3.dp(2f));

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStrokeWidth(YumiMd3.dp(2f));

        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBackground(YumiMd3.transparentRipple(16, YumiMd3.defaultRippleColor()));
    }

    public void setAccent(int color) {
        accent = color;
        invalidate();
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value, boolean animated) {
        if (checked == value) {
            if (!animated) {
                progress = value ? 1f : 0f;
                invalidate();
            }
            return;
        }
        checked = value;
        if (animator != null) {
            animator.cancel();
        }
        if (!animated || !isAttachedToWindow()) {
            progress = value ? 1f : 0f;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(progress, value ? 1f : 0f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int wMode = MeasureSpec.getMode(widthMeasureSpec);
        int wSize = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int hSize = MeasureSpec.getSize(heightMeasureSpec);

        int desiredW = YumiMd3.dp(52);
        int desiredH = YumiMd3.dp(32);

        int width = (wMode == MeasureSpec.EXACTLY) ? Math.min(wSize, desiredW + YumiMd3.dp(8)) : desiredW;
        int height = (hMode == MeasureSpec.EXACTLY) ? Math.min(hSize, desiredH + YumiMd3.dp(8)) : desiredH;
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float trackWidth = YumiMd3.dp(52);
        float trackHeight = YumiMd3.dp(32);
        float left = (width - trackWidth) / 2f;
        float top = (height - trackHeight) / 2f;
        trackRect.set(left, top, left + trackWidth, top + trackHeight);

        float p = Math.max(0f, Math.min(1f, progress));
        int currentAccent = accent != 0 ? accent : YumiMd3.primary();

        // Track fill
        int offTrack = YumiMd3.surfaceContainerHighest();
        int onTrack = currentAccent;
        int trackColor = YumiMd3.blend(offTrack, onTrack, p);
        trackPaint.setColor(trackColor);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint);

        // Track outline (only when unchecked or transitioning)
        if (p < 0.99f) {
            int outlineColor = YumiMd3.withAlpha(YumiMd3.outline(), (1f - p));
            outlinePaint.setColor(outlineColor);
            float inset = YumiMd3.dp(1f);
            trackRect.inset(inset, inset);
            canvas.drawRoundRect(trackRect, (trackHeight - inset * 2) / 2f, (trackHeight - inset * 2) / 2f, outlinePaint);
            trackRect.inset(-inset, -inset);
        }

        // Thumb: 16dp unchecked (radius 8dp), 24dp checked (radius 12dp)
        float offRadius = YumiMd3.dp(8f);
        float onRadius = YumiMd3.dp(12f);
        float thumbRadius = offRadius + (onRadius - offRadius) * p;

        float fromX = left + YumiMd3.dp(16f);
        float toX = left + trackWidth - YumiMd3.dp(16f);
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            float swap = fromX;
            fromX = toX;
            toX = swap;
        }
        float thumbCx = fromX + (toX - fromX) * p;
        float thumbCy = top + trackHeight / 2f;

        int offThumb = YumiMd3.outline();
        int onThumb = androidx.core.graphics.ColorUtils.calculateLuminance(currentAccent) > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
        int thumbColor = YumiMd3.blend(offThumb, onThumb, p);
        thumbPaint.setColor(thumbColor);
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint);

        // Checkmark inside thumb when checked
        if (p > 0.4f) {
            float checkAlpha = (p - 0.4f) / 0.6f;
            checkPaint.setColor(YumiMd3.withAlpha(currentAccent, checkAlpha));
            float unit = YumiMd3.dp(3.2f);
            checkPath.reset();
            checkPath.moveTo(thumbCx - unit, thumbCy);
            checkPath.lineTo(thumbCx - unit * 0.2f, thumbCy + unit * 0.8f);
            checkPath.lineTo(thumbCx + unit, thumbCy - unit * 0.65f);
            canvas.drawPath(checkPath, checkPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                downX = event.getX();
                downY = event.getY();
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    pressed = false;
                    setPressed(false);
                }
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                if (pressed) {
                    pressed = false;
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                setPressed(false);
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        boolean next = !checked;
        setChecked(next, true);
        MiogramHaptic.toggle(this, next);
        if (listener != null) {
            listener.onCheckedChanged(this, next);
        }
        return true;
    }

    @Override
    public CharSequence getAccessibilityClassName() {
        return "android.widget.Switch";
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.Switch");
        info.setCheckable(true);
        info.setChecked(checked);
        if (Build.VERSION.SDK_INT >= 30) {
            info.setStateDescription(checked
                    ? MiogramLocale.get("Увімкнено", "Включено", "On")
                    : MiogramLocale.get("Вимкнено", "Выключено", "Off"));
        }
    }
}
