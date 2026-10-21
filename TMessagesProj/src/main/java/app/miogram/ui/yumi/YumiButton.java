package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.amegram.theme.YumiMd3;

/** Filled, tonal, outlined and text Yumi buttons with a 52dp target. */
public class YumiButton extends TextView {

    public static final int STYLE_FILLED = 0;
    public static final int STYLE_TONAL = 1;
    public static final int STYLE_OUTLINED = 2;
    public static final int STYLE_TEXT = 3;
    public static final int STYLE_DANGER = 4;

    private int style;
    private int accent = YumiMd3.primary();
    private Drawable icon;
    private int iconRes;

    public YumiButton(@NonNull Context context) {
        this(context, STYLE_FILLED);
    }

    public YumiButton(@NonNull Context context, int style) {
        super(context);
        setGravity(Gravity.CENTER);
        setMinHeight(YumiUi.dp(52));
        setPadding(YumiUi.dp(20), YumiUi.dp(10), YumiUi.dp(20), YumiUi.dp(10));
        setTextSize(TypedValue.COMPLEX_UNIT_SP, YumiMd3.LABEL_LARGE);
        setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        setIncludeFontPadding(false);
        setClickable(true);
        setFocusable(true);
        setMaxLines(1);
        this.style = style;
        updateBackground();
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                YumiMotion.press(v, true);
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                YumiMotion.press(v, false);
            }
            return false;
        });
    }

    public void setStyle(int style) {
        this.style = style;
        updateBackground();
    }

    public void setAccent(int color) {
        accent = color;
        updateBackground();
    }

    public void setIcon(@DrawableRes int iconRes) {
        this.iconRes = iconRes;
        this.icon = iconRes == 0 ? null : tinted(ContextCompat.getDrawable(getContext(), iconRes));
        updateCompoundDrawables();
    }

    public void setButtonText(CharSequence text) {
        setText(text);
        setContentDescription(text);
    }

    private Drawable tinted(Drawable drawable) {
        if (drawable == null) {
            return null;
        }
        drawable = drawable.mutate();
        drawable.setColorFilter(new PorterDuffColorFilter(contentColor(), PorterDuff.Mode.SRC_IN));
        return drawable;
    }

    private int contentColor() {
        switch (style) {
            case STYLE_FILLED:
                return YumiMd3.onActionGradient();
            case STYLE_TONAL:
                return YumiMd3.onPrimaryContainer();
            case STYLE_OUTLINED:
                return YumiMd3.primary();
            case STYLE_DANGER:
                return YumiMd3.onErrorContainer();
            case STYLE_TEXT:
            default:
                return YumiMd3.primary();
        }
    }

    private int containerColor() {
        switch (style) {
            case STYLE_FILLED:
                return YumiMd3.actionGradientEnd();
            case STYLE_TONAL:
                return YumiMd3.primaryContainer();
            case STYLE_OUTLINED:
            case STYLE_TEXT:
                return 0;
            case STYLE_DANGER:
                return YumiMd3.errorContainer();
            default:
                return 0;
        }
    }

    private int strokeColor() {
        return style == STYLE_OUTLINED ? YumiMd3.outlineStrong() : 0;
    }

    private void updateBackground() {
        setTextColor(contentColor());
        if (style == STYLE_FILLED) {
            final GradientDrawable content = new GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    new int[]{YumiMd3.actionGradientStart(), accent});
            content.setCornerRadius(YumiUi.dp(YumiMd3.RADIUS_XL_DP));
            final GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadius(YumiUi.dp(YumiMd3.RADIUS_XL_DP));
            setBackground(new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(YumiMd3.withAlpha(0xFFFFFFFF, 0.22f)),
                    content, mask));
            setElevation(YumiUi.dp(1));
        } else {
            final GradientDrawable content = new GradientDrawable();
            content.setColor(containerColor());
            content.setCornerRadius(YumiUi.dp(YumiMd3.RADIUS_XL_DP));
            if (strokeColor() != 0) {
                content.setStroke(Math.max(1, YumiUi.dp(1)), strokeColor());
            }
            final GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadius(YumiUi.dp(YumiMd3.RADIUS_XL_DP));
            setBackground(new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(
                            YumiMd3.withAlpha(contentColor(), 0.12f)),
                    content, mask));
            setElevation(0);
        }
        if (iconRes != 0) {
            icon = tinted(ContextCompat.getDrawable(getContext(), iconRes));
            updateCompoundDrawables();
        }
    }

    private void updateCompoundDrawables() {
        if (icon != null) {
            final int size = YumiUi.dp(18);
            icon.setBounds(0, 0, size, size);
            setCompoundDrawablePadding(YumiUi.dp(8));
            setCompoundDrawablesRelative(icon, null, null, null);
        } else {
            setCompoundDrawablesRelative(null, null, null, null);
        }
    }

    public void updateColors() {
        updateBackground();
        if (iconRes != 0) {
            icon = tinted(ContextCompat.getDrawable(getContext(), iconRes));
            updateCompoundDrawables();
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setAlpha(enabled ? 1f : 0.45f);
    }
}
