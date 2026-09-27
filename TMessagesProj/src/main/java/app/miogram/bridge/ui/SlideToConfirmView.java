package app.miogram.bridge.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Slide-to-Confirm Swipe Widget.
 * Minimalist, Durov-grade slider with smooth haptics and spring animations.
 */
public class SlideToConfirmView extends FrameLayout {

    public interface OnConfirmListener {
        void onConfirmed();
    }

    private final FrameLayout thumb;
    private final TextView thumbIcon;
    private final TextView hintText;
    private final Paint trackFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackFillRect = new RectF();

    private OnConfirmListener onConfirmListener;
    private float startX = 0f;
    private float currentTranslationX = 0f;
    private boolean isDragging = false;
    private boolean isConfirmed = false;

    private int trackColor = 0x22FFFFFF;
    private int accentColor1 = 0xFFFF70A6; // Amegram Neon Pink
    private int accentColor2 = 0xFF00F2FE; // Cyber Cyan

    public SlideToConfirmView(Context context) {
        this(context, null);
    }

    public SlideToConfirmView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);

        int heightPx = AndroidUtilities.dp(54);
        int cornerRadius = heightPx / 2;

        // Background pill
        GradientDrawable pillBg = new GradientDrawable();
        pillBg.setCornerRadius(cornerRadius);
        pillBg.setColor(trackColor);
        pillBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        setBackground(pillBg);

        // Center Hint Text
        hintText = new TextView(context);
        hintText.setText("Протягніть для підтвердження ›››");
        hintText.setTextColor(0xAAFFFFFF);
        hintText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hintText.setTypeface(AndroidUtilities.bold());
        hintText.setGravity(Gravity.CENTER);
        addView(hintText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        // Slider Thumb
        int thumbSize = AndroidUtilities.dp(44);
        int margin = AndroidUtilities.dp(5);

        thumb = new FrameLayout(context);
        GradientDrawable thumbBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{accentColor1, 0xFFE040FB}
        );
        thumbBg.setShape(GradientDrawable.OVAL);
        thumb.setBackground(thumbBg);
        thumb.setElevation(AndroidUtilities.dp(4));

        thumbIcon = new TextView(context);
        thumbIcon.setText("››");
        thumbIcon.setTextColor(Color.WHITE);
        thumbIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        thumbIcon.setTypeface(AndroidUtilities.bold());
        thumbIcon.setGravity(Gravity.CENTER);
        thumb.addView(thumbIcon, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(thumbSize, thumbSize);
        thumbLp.gravity = Gravity.CENTER_VERTICAL | Gravity.START;
        thumbLp.leftMargin = margin;
        addView(thumb, thumbLp);
    }

    public void setHint(String text) {
        if (hintText != null && text != null) {
            hintText.setText(text);
        }
    }

    public void setOnConfirmListener(OnConfirmListener listener) {
        this.onConfirmListener = listener;
    }

    public void reset() {
        isConfirmed = false;
        isDragging = false;
        animateTo(0f);
        if (thumbIcon != null) {
            thumbIcon.setText("››");
        }
    }

    private float getMaxTranslation() {
        int thumbMargin = AndroidUtilities.dp(5);
        int thumbSize = AndroidUtilities.dp(44);
        return Math.max(0, getWidth() - thumbSize - (thumbMargin * 2));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float max = getMaxTranslation();
        if (max > 0 && currentTranslationX > 0) {
            float progress = Math.min(1f, currentTranslationX / max);
            int thumbMargin = AndroidUtilities.dp(5);
            int thumbSize = AndroidUtilities.dp(44);

            trackFillRect.set(
                    thumbMargin,
                    thumbMargin,
                    thumbMargin + thumbSize + currentTranslationX,
                    getHeight() - thumbMargin
            );

            trackFillPaint.setShader(new LinearGradient(
                    trackFillRect.left, 0, trackFillRect.right, 0,
                    accentColor1, accentColor2, Shader.TileMode.CLAMP
            ));
            trackFillPaint.setAlpha((int) (200 * progress));

            float r = (getHeight() - (thumbMargin * 2)) / 2f;
            canvas.drawRoundRect(trackFillRect, r, r, trackFillPaint);

            if (hintText != null) {
                hintText.setAlpha(Math.max(0f, 1f - (progress * 1.6f)));
            }
        } else if (hintText != null) {
            hintText.setAlpha(1f);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (isConfirmed || !isEnabled()) return super.onTouchEvent(event);

        float max = getMaxTranslation();
        if (max <= 0) return super.onTouchEvent(event);

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                float x = event.getX();
                float thumbStart = thumb.getLeft() + currentTranslationX;
                float thumbEnd = thumbStart + thumb.getWidth();

                if (x >= thumbStart - AndroidUtilities.dp(16) && x <= thumbEnd + AndroidUtilities.dp(16)) {
                    isDragging = true;
                    startX = x - currentTranslationX;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (!isDragging) return false;
                float newTranslation = event.getX() - startX;
                newTranslation = Math.max(0f, Math.min(max, newTranslation));
                currentTranslationX = newTranslation;
                thumb.setTranslationX(currentTranslationX);
                invalidate();
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!isDragging) return false;
                isDragging = false;
                getParent().requestDisallowInterceptTouchEvent(false);

                if (currentTranslationX >= max * 0.82f) {
                    // Confirmed!
                    isConfirmed = true;
                    animateTo(max, () -> {
                        MiogramHaptic.perform(this, MiogramHaptic.KEYBOARD_TAP);
                        if (thumbIcon != null) {
                            thumbIcon.setText("✓");
                        }
                        if (onConfirmListener != null) {
                            onConfirmListener.onConfirmed();
                        }
                    });
                } else {
                    // Snap back
                    animateTo(0f);
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private void animateTo(float target) {
        animateTo(target, null);
    }

    private void animateTo(float target, Runnable onEnd) {
        ValueAnimator anim = ValueAnimator.ofFloat(currentTranslationX, target);
        anim.setDuration(220);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(animation -> {
            currentTranslationX = (float) animation.getAnimatedValue();
            thumb.setTranslationX(currentTranslationX);
            invalidate();
        });
        if (onEnd != null) {
            anim.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    onEnd.run();
                }
            });
        }
        anim.start();
    }
}
