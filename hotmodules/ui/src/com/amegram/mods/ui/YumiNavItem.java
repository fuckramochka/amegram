package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Real Yumi navigation item, owned by the UI hot module.
 *
 * <p>Matches Material 3 Expressive pill dock: active tab expands into a solid
 * tonal pill capsule containing icon, counter badge and label ("2 Чати"), while
 * unselected tabs render only centered icons without labels.</p>
 */
final class YumiNavItem extends FrameLayout {

    private YumiPalette palette;
    private final LinearLayout row;
    private final FrameLayout iconBox;
    private final ImageView iconView;
    private final ImageView avatarView;
    private final TextView labelView;
    private final TextView counterView;
    private final UiModule owner;

    private boolean selected;
    private boolean avatarMode;
    private CharSequence rawLabel = "";
    private String counterText;

    YumiNavItem(Context context, YumiPalette palette, String iconName, boolean avatarMode, UiModule owner) {
        super(context);
        this.palette = palette;
        this.avatarMode = avatarMode;
        this.owner = owner;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(row, new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        iconBox = new FrameLayout(context);
        iconBox.setClipChildren(false);
        iconBox.setClipToPadding(false);
        iconBox.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(26), dp(26)));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconBox.addView(iconView, new LayoutParams(dp(22), dp(22), Gravity.CENTER));

        avatarView = new ImageView(context);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setVisibility(GONE);
        avatarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(palette.primaryContainer);
        avatarView.setBackground(avatarBg);
        avatarView.setClipToOutline(true);
        iconBox.addView(avatarView, new LayoutParams(dp(22), dp(22), Gravity.CENTER));

        final int iconRes = context.getResources().getIdentifier(
                iconName, "drawable", context.getPackageName());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
        }
        iconView.setColorFilter(new PorterDuffColorFilter(palette.navInactive, PorterDuff.Mode.SRC_IN));

        counterView = new TextView(context);
        counterView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 9f);
        counterView.setTextColor(palette.onPrimary);
        counterView.setGravity(Gravity.CENTER);
        counterView.setMinWidth(dp(16));
        counterView.setMinHeight(dp(16));
        counterView.setPadding(dp(4), 0, dp(4), 0);
        counterView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        counterView.setVisibility(GONE);
        counterView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final LayoutParams counterLp = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(16), Gravity.TOP | Gravity.END);
        counterLp.topMargin = -dp(4);
        counterLp.setMarginEnd(-dp(6));
        iconBox.addView(counterView, counterLp);

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER_VERTICAL);
        labelView.setIncludeFontPadding(false);
        labelView.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.BOLD));
        labelView.setTextColor(palette.navActive);
        labelView.setPaddingRelative(dp(6), 0, 0, 0);
        labelView.setVisibility(GONE);
        labelView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (avatarMode) {
            iconView.setVisibility(GONE);
            avatarView.setVisibility(VISIBLE);
        }
        applyItemState(false);
    }

    void setLabel(CharSequence text) {
        if (TextUtils.equals(rawLabel, text)) return;
        rawLabel = text != null ? text : "";
        setContentDescription(rawLabel);
        updateEffectiveLabel();
        requestLayout();
    }

    void setSelectedState(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        applyItemState(true);
    }

    void setCounter(String text, boolean error) {
        if (TextUtils.equals(counterText, text)) return;
        counterText = text;
        updateEffectiveLabel();
        updateCounterView();
    }

    void setAvatar(Drawable drawable) {
        if (drawable != null) {
            avatarView.setImageDrawable(drawable);
            avatarMode = true;
            iconView.setVisibility(GONE);
            avatarView.setVisibility(VISIBLE);
        }
    }

    void refreshColors(YumiPalette updated) {
        if (updated != null) {
            this.palette = updated;
        }
        iconView.setColorFilter(new PorterDuffColorFilter(
                selected ? palette.navActive : palette.navInactive, PorterDuff.Mode.SRC_IN));
        labelView.setTextColor(palette.navActive);
        counterView.setTextColor(palette.onPrimary);
        GradientDrawable bg = (GradientDrawable) avatarView.getBackground();
        if (bg != null) {
            bg.setColor(palette.primaryContainer);
        }
        applyItemState(false);
        invalidate();
    }

    private void updateEffectiveLabel() {
        if (TextUtils.isEmpty(rawLabel)) {
            labelView.setText("");
            return;
        }
        if (!TextUtils.isEmpty(counterText)) {
            labelView.setText(counterText + " " + rawLabel);
        } else {
            labelView.setText(rawLabel);
        }
    }

    private void updateCounterView() {
        if (TextUtils.isEmpty(counterText) || selected) {
            counterView.setVisibility(GONE);
        } else {
            counterView.setText(counterText);
            final GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.RECTANGLE);
            bg.setCornerRadius(dp(8));
            bg.setColor(counterText != null && counterText.equals("!")
                    ? palette.error : palette.primary);
            counterView.setBackground(bg);
            counterView.setVisibility(VISIBLE);
        }
    }

    private void applyItemState(boolean animate) {
        final boolean expand = owner == null || owner.isNavExpandActivePillEnabled();
        if (selected) {
            final GradientDrawable pill = new GradientDrawable();
            pill.setShape(GradientDrawable.RECTANGLE);
            pill.setCornerRadius(dp(expand ? 20 : 16));
            pill.setColor(palette.navIndicator);
            row.setBackground(pill);
            if (expand) {
                row.setPadding(dp(13), dp(7), dp(13), dp(7));
                labelView.setVisibility(VISIBLE);
                labelView.setTextColor(palette.navActive);
                iconView.setColorFilter(new PorterDuffColorFilter(palette.navActive, PorterDuff.Mode.SRC_IN));
                counterView.setVisibility(GONE);
                updateEffectiveLabel();
            } else {
                row.setPadding(dp(10), dp(7), dp(10), dp(7));
                labelView.setVisibility(GONE);
                iconView.setColorFilter(new PorterDuffColorFilter(palette.navActive, PorterDuff.Mode.SRC_IN));
                updateCounterView();
            }
        } else {
            row.setBackground(null);
            row.setPadding(dp(8), dp(7), dp(8), dp(7));
            labelView.setVisibility(GONE);
            iconView.setColorFilter(new PorterDuffColorFilter(palette.navInactive, PorterDuff.Mode.SRC_IN));
            updateCounterView();
        }
        if (animate) {
            row.animate().scaleX(selected ? 1.03f : 1.0f).scaleY(selected ? 1.03f : 1.0f)
                    .setDuration(180L).start();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
