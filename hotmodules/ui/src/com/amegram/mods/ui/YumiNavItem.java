package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
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
 * <p>It is attached as the only visual child of a legacy {@code GlassTabView}.
 * The legacy view stays the touch target so MainTabsLayout, gestures, long
 * presses and view-pager selection keep working unchanged. All pixels, labels,
 * counters and the active pill come from this module.</p>
 */
final class YumiNavItem extends FrameLayout {

    private final YumiPalette palette;
    private final LinearLayout row;
    private final FrameLayout iconBox;
    private final ImageView iconView;
    private final ImageView avatarView;
    private final TextView labelView;
    private final TextView counterView;
    private final Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pillRect = new RectF();

    private boolean selected;
    private boolean avatarMode;
    private String counterText;

    YumiNavItem(Context context, YumiPalette palette, String iconName, boolean avatarMode) {
        super(context);
        this.palette = palette;
        this.avatarMode = avatarMode;
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
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
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(32), dp(32)));

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

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10f);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        labelView.setIncludeFontPadding(false);
        labelView.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        labelView.setTextColor(palette.navInactive);
        labelView.setAlpha(0.85f);
        labelView.setPaddingRelative(0, dp(2), 0, 0);
        labelView.setVisibility(VISIBLE);
        labelView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(labelView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

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
        counterLp.setMarginEnd(-dp(8));
        iconBox.addView(counterView, counterLp);

        if (avatarMode) {
            iconView.setVisibility(GONE);
            avatarView.setVisibility(VISIBLE);
        }
        updateCounterBackground();
    }

    void setLabel(CharSequence text) {
        if (TextUtils.equals(labelView.getText(), text)) return;
        labelView.setText(text);
        setContentDescription(text);
        requestLayout();
    }

    void setSelectedState(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        labelView.setTextColor(selected ? palette.navActive : palette.navInactive);
        labelView.setAlpha(selected ? 1.0f : 0.85f);
        iconView.setColorFilter(new PorterDuffColorFilter(
                selected ? palette.navActive : palette.navInactive, PorterDuff.Mode.SRC_IN));
        updateIconBackground();
        row.animate().scaleX(selected ? 1.04f : 1.0f).scaleY(selected ? 1.04f : 1.0f)
                .setDuration(200L).start();
        invalidate();
    }

    void setCounter(String text, boolean error) {
        if (TextUtils.equals(counterText, text)) return;
        counterText = text;
        if (TextUtils.isEmpty(text)) {
            counterView.setVisibility(GONE);
        } else {
            counterView.setText(text);
            updateCounterBackground();
            counterView.setVisibility(VISIBLE);
            counterView.setScaleX(0.7f);
            counterView.setScaleY(0.7f);
            counterView.animate().scaleX(1f).scaleY(1f)
                    .setDuration(180L)
                    .start();
        }
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
        // Palette fields are mutable; the module updates the same instance.
        pillPaint.setColor(updated.navIndicator);
        iconView.setColorFilter(new PorterDuffColorFilter(
                selected ? updated.navActive : updated.navInactive, PorterDuff.Mode.SRC_IN));
        labelView.setTextColor(selected ? updated.navActive : updated.navInactive);
        labelView.setAlpha(selected ? 1.0f : 0.85f);
        updateIconBackground();
        counterView.setTextColor(updated.onPrimary);
        GradientDrawable bg = (GradientDrawable) avatarView.getBackground();
        if (bg != null) {
            bg.setColor(updated.primaryContainer);
        }
        updateCounterBackground();
        invalidate();
    }

    private void updateCounterBackground() {
        final GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(8));
        bg.setColor(counterText != null && counterText.equals("!")
                ? palette.error : palette.primary);
        counterView.setBackground(bg);
    }

    private void updateIconBackground() {
        if (!selected || avatarMode) {
            iconBox.setBackground(null);
            return;
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(palette.navIndicator);
        iconBox.setBackground(bg);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (selected) {
            final float pillW = dp(28);
            final float pillH = dp(3);
            final float left = (getWidth() - pillW) / 2f;
            final float top = getHeight() - dp(3);
            pillRect.set(left, top, left + pillW, top + pillH);
            pillPaint.setColor(palette.navActive);
            canvas.drawRoundRect(pillRect, dp(2), dp(2), pillPaint);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
