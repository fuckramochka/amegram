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
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * A compact Material 3 switch with an expressive springy thumb.
 * The platform / Telegram switches are theme-bound; this one accepts a
 * surface palette so it can live on the tinted settings cards.
 */
public class YumiSwitch extends View {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(YumiSwitch view, boolean checked);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint outlinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
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

        checkPaint.setStyle(Paint.Style.STROKE);
        checkPaint.setStrokeCap(Paint.Cap.ROUND);
        checkPaint.setStrokeJoin(Paint.Join.ROUND);
        checkPaint.setStrokeWidth(YumiMd3.dp(2.2f));

        outlinePaint.setStyle(Paint.Style.STROKE);
        outlinePaint.setStrokeWidth(YumiMd3.dp(1.4f));
        outlinePaint.setStrokeCap(Paint.Cap.ROUND);

        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setBackground(YumiMd3.transparentRipple(18, YumiMd3.defaultRippleColor()));
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
        animator.setDuration(280);
        animator.setInterpolator(new OvershootInterpolator(1.15f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = resolveSize(YumiMd3.dp(54), widthMeasureSpec);
        int height = resolveSize(YumiMd3.dp(36), heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        float trackWidth = Math.min(width, YumiMd3.dp(52));
        float trackHeight = YumiMd3.dp(32);
        float left = (width - trackWidth) / 2f;
        float top = (height - trackHeight) / 2f;
        trackRect.set(left, top, left + trackWidth, top + trackHeight);

        float amount = Math.max(0f, Math.min(1f, progress));
        int offTrack = YumiMd3.blend(YumiMd3.surfaceContainerHighest(), YumiMd3.outline(), 0.35f);
        int trackColor = YumiMd3.blend(offTrack, accent, amount);
        trackPaint.setColor(trackColor);
        canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, trackPaint);

        if (amount < 0.98f) {
            outlinePaint.setColor(YumiMd3.withAlpha(YumiMd3.outlineStrong(), 0.44f * (1f - amount)));
            canvas.drawRoundRect(trackRect, trackHeight / 2f, trackHeight / 2f, outlinePaint);
        }

        float thumbRadius = YumiMd3.dp(11);
        float fromX = left + YumiMd3.dp(5) + thumbRadius;
        float toX = left + trackWidth - YumiMd3.dp(5) - thumbRadius;
        if (getLayoutDirection() == LAYOUT_DIRECTION_RTL) {
            float swap = fromX;
            fromX = toX;
            toX = swap;
        }
        float thumbCx = fromX + (toX - fromX) * Math.max(0f, Math.min(1.12f, progress));
        float thumbCy = top + trackHeight / 2f;

        shadowPaint.setColor(0x2A000000);
        canvas.drawCircle(thumbCx, thumbCy + YumiMd3.dp(1), thumbRadius + YumiMd3.dp(0.5f), shadowPaint);

        thumbPaint.setColor(YumiMd3.isDark() ? 0xFFFFFFFF : 0xFFFFFFFF);
        canvas.drawCircle(thumbCx, thumbCy, thumbRadius, thumbPaint);

        float checkAmount = Math.max(0f, Math.min(1f, (progress - 0.55f) / 0.45f));
        if (checkAmount > 0.01f) {
            // На светлом thumb в тёмной теме акцент должен быть затемнён,
            // иначе галочка растворяется в белом.
            int checkColor = YumiMd3.isDark()
                    ? YumiMd3.blend(accent, 0xFF000000, 0.55f)
                    : accent;
            checkPaint.setColor(YumiMd3.withAlpha(checkColor, checkAmount));
            float cx = thumbCx;
            float cy = thumbCy;
            float unit = YumiMd3.dp(3.5f) * (0.75f + checkAmount * 0.25f);
            checkPath.reset();
            checkPath.moveTo(cx - unit, cy);
            checkPath.lineTo(cx - unit * 0.15f, cy + unit * 0.8f);
            checkPath.lineTo(cx + unit, cy - unit * 0.7f);
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
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(110).start();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pressed && (Math.abs(event.getX() - downX) > touchSlop
                        || Math.abs(event.getY() - downY) > touchSlop)) {
                    pressed = false;
                    setPressed(false);
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                return true;
            case MotionEvent.ACTION_UP:
                setPressed(false);
                animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                if (pressed) {
                    pressed = false;
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                setPressed(false);
                animate().scaleX(1f).scaleY(1f).setDuration(120).start();
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
