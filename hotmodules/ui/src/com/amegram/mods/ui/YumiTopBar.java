package com.amegram.mods.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Module-owned top app bar used by official list fragments. */
public final class YumiTopBar extends FrameLayout {

    private final YumiPalette palette;
    private final TextView titleView;
    private final ImageView backView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<ImageView> actions = new ArrayList<>();
    private final LinearLayout row;
    private final int statusInset;
    private float dividerAlpha = 0.5f;

    public YumiTopBar(Context context, YumiPalette palette, boolean showBack) {
        super(context);
        this.palette = palette;
        statusInset = statusBarHeight(context);
        setWillNotDraw(false);
        setBackgroundColor(palette.surface);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPaddingRelative(dp(12), statusInset, dp(12), 0);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        backView = new ImageView(context);
        backView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        final int backRes = res(context, "yumi_ic_arrow_back");
        if (backRes != 0) {
            backView.setImageResource(backRes);
        }
        backView.setColorFilter(new PorterDuffColorFilter(
                palette.onSurface, PorterDuff.Mode.SRC_IN));
        backView.setContentDescription(YumiText.tr("Назад", "Назад", "Back"));
        backView.setVisibility(showBack ? VISIBLE : GONE);
        backView.setClickable(true);
        backView.setFocusable(true);
        backView.setBackground(null);
        if (showBack && isRtl(context)) {
            backView.setScaleX(-1f);
        }
        row.addView(backView, new LinearLayout.LayoutParams(dp(48), dp(48)));

        titleView = new TextView(context);
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22f);
        titleView.setTypeface(android.graphics.Typeface.create("sans-serif-medium",
                android.graphics.Typeface.NORMAL));
        titleView.setTextColor(palette.onSurface);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setIncludeFontPadding(false);
        titleView.setPaddingRelative(showBack ? dp(6) : dp(10), 0, 0, 0);
        row.addView(titleView, new LinearLayout.LayoutParams(
                0, LayoutParams.MATCH_PARENT, 1f));

        dividerPaint.setStrokeWidth(Math.max(1, dp(0.75f)));
        dividerPaint.setColor(YumiPalette.alpha(palette.navBorder, dividerAlpha));
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
        setContentDescription(title);
    }

    public void addAction(int drawableRes, CharSequence description, OnClickListener listener) {
        final ImageView action = new ImageView(getContext());
        action.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        if (drawableRes != 0) {
            action.setImageResource(drawableRes);
        }
        action.setColorFilter(new PorterDuffColorFilter(
                palette.onSurface, PorterDuff.Mode.SRC_IN));
        action.setContentDescription(description);
        action.setClickable(true);
        action.setFocusable(true);
        action.setOnClickListener(listener);
        row.addView(action, new LinearLayout.LayoutParams(dp(48), dp(48)));
        actions.add(action);
    }

    public void setOnBackClickListener(OnClickListener listener) {
        backView.setOnClickListener(listener);
        backView.setVisibility(listener == null ? GONE : VISIBLE);
    }

    public void setBackVisible(boolean visible) {
        backView.setVisibility(visible ? VISIBLE : GONE);
    }

    public void refreshColors(YumiPalette updated) {
        setBackgroundColor(updated.surface);
        titleView.setTextColor(updated.onSurface);
        backView.setColorFilter(new PorterDuffColorFilter(
                updated.onSurface, PorterDuff.Mode.SRC_IN));
        for (ImageView action : actions) {
            action.setColorFilter(new PorterDuffColorFilter(
                    updated.onSurface, PorterDuff.Mode.SRC_IN));
        }
        dividerPaint.setColor(YumiPalette.alpha(updated.navBorder, dividerAlpha));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, getHeight() - dividerPaint.getStrokeWidth(),
                getWidth(), getHeight() - dividerPaint.getStrokeWidth(), dividerPaint);
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    public static int topBarHeight(Context context) {
        return statusBarHeight(context) + dp(context, 56);
    }

    public static int statusBarHeight(Context context) {
        int result = 0;
        try {
            final int id = context.getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            if (id != 0) {
                result = context.getResources().getDimensionPixelSize(id);
            }
        } catch (Throwable ignore) {
        }
        if (result <= 0) {
            result = dp(context, 24);
        }
        return result;
    }

    private static boolean isRtl(Context context) {
        return context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL;
    }

    private static int res(Context context, String name) {
        return context.getResources().getIdentifier(name, "drawable", context.getPackageName());
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
