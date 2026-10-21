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
        setWillNotDraw(true);
        setClipChildren(false);
        setClipToPadding(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
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
        row.addView(iconBox, new LinearLayout.LayoutParams(dp(24), dp(24)));

        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        iconBox.addView(iconView, new LayoutParams(dp(24), dp(24), Gravity.CENTER));

        avatarView = new ImageView(context);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatarView.setVisibility(GONE);
        avatarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        final GradientDrawable avatarBg = new GradientDrawable();
        avatarBg.setShape(GradientDrawable.OVAL);
        avatarBg.setColor(palette.primaryContainer);
        avatarView.setBackground(avatarBg);
        avatarView.setClipToOutline(true);
        iconBox.addView(avatarView, new LayoutParams(dp(24), dp(24), Gravity.CENTER));

        final int iconRes = context.getResources().getIdentifier(
                iconName, "drawable", context.getPackageName());
        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
        }
        iconView.setColorFilter(new PorterDuffColorFilter(palette.navInactive, PorterDuff.Mode.SRC_IN));

        labelView = new TextView(context);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        labelView.setSingleLine(true);
        labelView.setEllipsize(TextUtils.TruncateAt.END);
        labelView.setGravity(Gravity.CENTER);
        labelView.setIncludeFontPadding(false);
        labelView.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        labelView.setTextColor(palette.navActive);
        labelView.setPaddingRelative(dp(6), 0, 0, 0);
        labelView.setVisibility(GONE);
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
        counterLp.topMargin = -dp(5);
        counterLp.setMarginEnd(-dp(9));
        iconBox.addView(counterView, counterLp);

        if (avatarMode) {
            iconView.setVisibility(GONE);
            avatarView.setVisibility(VISIBLE);
        }
        updateCounterBackground();
    }

    void setLabel(CharSequence text) {
        labelView.setText(text);
        setContentDescription(text);
        requestLayout();
    }

    void setSelectedState(boolean selected) {
        this.selected = selected;
        labelView.setVisibility(selected ? VISIBLE : GONE);
        iconView.setColorFilter(new PorterDuffColorFilter(
                selected ? palette.navActive : palette.navInactive, PorterDuff.Mode.SRC_IN));
        invalidate();
        requestLayout();
    }

    void setCounter(String text, boolean error) {
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
        labelView.setTextColor(updated.navActive);
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

    @Override
    protected void onDraw(Canvas canvas) {
        if (selected) {
            final int rowWidth = row.getMeasuredWidth();
            final float pillWidth = Math.min(getWidth() - dp(4), rowWidth + dp(20));
            final float pillHeight = dp(40);
            final float cx = getWidth() / 2f;
            final float cy = getHeight() / 2f;
            pillRect.set(cx - pillWidth / 2f, cy - pillHeight / 2f,
                    cx + pillWidth / 2f, cy + pillHeight / 2f);
            pillPaint.setColor(palette.navIndicator);
            canvas.drawRoundRect(pillRect, pillHeight / 2f, pillHeight / 2f, pillPaint);
        }
        super.onDraw(canvas);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
