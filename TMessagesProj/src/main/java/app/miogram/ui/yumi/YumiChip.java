package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.amegram.theme.YumiMd3;

/** A filter/assist chip with a 48dp touch target and a 36dp visual pill. */
public class YumiChip extends FrameLayout {

    public interface OnCheckedChangeListener {
        void onCheckedChanged(YumiChip chip, boolean checked);
    }

    private final TextView label;
    private final YumiRoundRectDrawable background;
    private boolean checked;
    private boolean checkable = true;
    private OnCheckedChangeListener listener;
    private Drawable checkDrawable;

    public YumiChip(@NonNull Context context) {
        super(context);
        setMinimumHeight(YumiUi.dp(48));
        setClipChildren(false);
        setClickable(true);
        setFocusable(true);

        background = new YumiRoundRectDrawable(
                YumiMd3.surfaceContainerHigh(), YumiMd3.RADIUS_S_DP,
                YumiMd3.outline(), 1f);
        label = YumiUi.text(context, "", YumiMd3.LABEL, YumiMd3.onSurfaceVariant(), YumiMd3.MEDIUM);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setMinHeight(YumiUi.dp(36));
        label.setPadding(YumiUi.dp(14), 0, YumiUi.dp(14), 0);
        label.setBackground(YumiUi.ripple(YumiMd3.withAlpha(YumiMd3.onSurface(), 0.10f), background));
        final LayoutParams lp = new LayoutParams(
                LayoutParams.WRAP_CONTENT, YumiUi.dp(36), Gravity.CENTER_VERTICAL);
        addView(label, lp);

        setOnClickListener(v -> {
            if (checkable) {
                YumiUi.haptic(v);
                setChecked(!checked, true);
                if (listener != null) {
                    listener.onCheckedChanged(this, checked);
                }
            }
        });
        applyState(false);
    }

    public void setText(CharSequence text) {
        label.setText(text);
        setContentDescription(text);
    }

    public void setChipIcon(int iconRes) {
        if (iconRes == 0) {
            label.setCompoundDrawablesRelative(null, null, null, null);
        } else {
            Drawable icon = ContextCompat.getDrawable(getContext(), iconRes);
            if (icon != null) {
                icon = icon.mutate();
                icon.setBounds(0, 0, YumiUi.dp(16), YumiUi.dp(16));
                icon.setColorFilter(new PorterDuffColorFilter(
                        checked ? YumiMd3.onSecondaryContainer() : YumiMd3.onSurfaceVariant(),
                        PorterDuff.Mode.SRC_IN));
                label.setCompoundDrawablesRelative(icon, null, null, null);
                label.setCompoundDrawablePadding(YumiUi.dp(6));
            }
        }
    }

    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
        setClickable(checkable);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        setChecked(checked, true);
    }

    public void setChecked(boolean checked, boolean animated) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        applyState(animated);
        sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    private void applyState(boolean animated) {
        if (animated) {
            YumiMotion.press(this, true);
            postDelayed(() -> YumiMotion.press(this, false), YumiMd3.DURATION_PRESS_MS);
        }
        label.setTextColor(checked ? YumiMd3.onSecondaryContainer() : YumiMd3.onSurfaceVariant());
        label.setTypeface(YumiMd3.typeface(checked ? YumiMd3.MEDIUM : YumiMd3.REGULAR));
        background.setFillColor(checked ? YumiMd3.secondaryContainer() : YumiMd3.surfaceContainerHigh());
        background.setStroke(checked ? 0 : YumiMd3.outline(), checked ? 0f : 1f);
        if (checkDrawable == null) {
            checkDrawable = ContextCompat.getDrawable(getContext(),
                    org.telegram.messenger.R.drawable.yumi_ic_check);
        }
        if (checkDrawable != null) {
            final Drawable drawable = checkDrawable.mutate();
            drawable.setBounds(0, 0, YumiUi.dp(16), YumiUi.dp(16));
            drawable.setColorFilter(new PorterDuffColorFilter(
                    YumiMd3.onSecondaryContainer(), PorterDuff.Mode.SRC_IN));
            final Drawable start = label.getCompoundDrawablesRelative()[0];
            if (checked) {
                label.setCompoundDrawablesRelative(drawable, null, null, null);
                label.setCompoundDrawablePadding(YumiUi.dp(6));
            } else if (start != null) {
                // Keep a caller-provided leading icon; only remove the check.
                label.setCompoundDrawablesRelative(null, null, null, null);
                label.setCompoundDrawablePadding(0);
            }
        }
        setSelected(checked);
        setContentDescription(label.getText() + ", "
                + (checked ? YumiLocale.selected() : YumiLocale.notSelected()));
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(@NonNull AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.CheckBox");
        info.setCheckable(checkable);
        info.setChecked(checked);
    }

    public void updateColors() {
        label.setTextColor(checked ? YumiMd3.onSecondaryContainer() : YumiMd3.onSurfaceVariant());
        background.setFillColor(checked ? YumiMd3.secondaryContainer() : YumiMd3.surfaceContainerHigh());
        background.setStroke(checked ? 0 : YumiMd3.outline(), checked ? 0f : 1f);
    }
}
