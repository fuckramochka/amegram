package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.NonNull;

import app.amegram.theme.YumiMd3;

/** Material 3 expressive slider, drawn without per-frame allocations. */
public class YumiSlider extends View {

    public interface OnValueChangeListener {
        void onValueChanged(YumiSlider slider, float value, boolean fromUser);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint activePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF activeRect = new RectF();

    private float value = 0.5f;
    private int steps;
    private int accent = YumiMd3.primary();
    private boolean pressed;
    private OnValueChangeListener listener;
    private CharSequence label = "";

    public YumiSlider(@NonNull Context context) {
        super(context);
        setFocusable(true);
        setClickable(true);
        setMinimumHeight(YumiUi.dp(48));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        trackPaint.setColor(YumiMd3.sliderTrack());
        activePaint.setColor(accent);
        thumbPaint.setColor(accent);
        haloPaint.setColor(YumiMd3.withAlpha(accent, 0.16f));
        setContentDescription(label);
    }

    public void setValue(float value) {
        setValue(value, false);
    }

    public void setValue(float value, boolean fromUser) {
        value = Math.max(0f, Math.min(1f, value));
        if (steps > 0) {
            value = Math.round(value * steps) / (float) steps;
        }
        if (Math.abs(this.value - value) < 0.0001f) {
            return;
        }
        this.value = value;
        invalidate();
        if (listener != null) {
            listener.onValueChanged(this, value, fromUser);
        }
        if (fromUser) {
            sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);
        }
    }

    public float getValue() {
        return value;
    }

    public void setSteps(int steps) {
        this.steps = Math.max(0, steps);
    }

    public void setAccent(int accent) {
        this.accent = accent;
        activePaint.setColor(accent);
        thumbPaint.setColor(accent);
        haloPaint.setColor(YumiMd3.withAlpha(accent, 0.16f));
        invalidate();
    }

    public void setLabel(CharSequence label) {
        this.label = label;
        setContentDescription(label);
    }

    public void setOnValueChangeListener(OnValueChangeListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, Math.max(YumiUi.dp(48), getSuggestedMinimumHeight()));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final float width = getWidth();
        final float centerY = getHeight() / 2f;
        final float trackH = YumiUi.dp(6);
        final float thumbR = YumiUi.dp(10);
        final float start = YumiUi.dp(12) + thumbR;
        final float end = width - YumiUi.dp(12) - thumbR;
        final float usable = Math.max(1f, end - start);
        final float fraction = YumiUi.isRtl() ? 1f - value : value;
        final float thumbX = start + usable * fraction;

        trackRect.set(start, centerY - trackH / 2f, end, centerY + trackH / 2f);
        canvas.drawRoundRect(trackRect, trackH / 2f, trackH / 2f, trackPaint);

        if (YumiUi.isRtl()) {
            activeRect.set(thumbX, centerY - trackH / 2f, end, centerY + trackH / 2f);
        } else {
            activeRect.set(start, centerY - trackH / 2f, thumbX, centerY + trackH / 2f);
        }
        canvas.drawRoundRect(activeRect, trackH / 2f, trackH / 2f, activePaint);

        if (pressed) {
            canvas.drawCircle(thumbX, centerY, thumbR + YumiUi.dp(8), haloPaint);
        }
        canvas.drawCircle(thumbX, centerY, thumbR, thumbPaint);
    }

    private void updateFromTouch(float x) {
        final float thumbR = YumiUi.dp(10);
        final float start = YumiUi.dp(12) + thumbR;
        final float end = getWidth() - YumiUi.dp(12) - thumbR;
        final float fraction = Math.max(0f, Math.min(1f, (x - start) / Math.max(1f, end - start)));
        setValue(YumiUi.isRtl() ? 1f - fraction : fraction, true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressed = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                YumiUi.haptic(this);
                updateFromTouch(event.getX());
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                updateFromTouch(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            final float step = steps > 0 ? 1f / steps : 0.05f;
            final boolean increase = YumiUi.isRtl()
                    ? keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    : keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
            setValue(value + (increase ? step : -step), true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(
                AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_PERCENT, 0f, 1f, value));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) {
            final float step = steps > 0 ? 1f / steps : 0.05f;
            setValue(value + step, true);
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            final float step = steps > 0 ? 1f / steps : 0.05f;
            setValue(value - step, true);
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    public void updateColors() {
        trackPaint.setColor(YumiMd3.sliderTrack());
        setAccent(accent);
    }
}
