package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import app.amegram.theme.YumiMd3;
import app.amegram.theme.YumiSwitch;

/**
 * A real Yumi list row. It owns its tonal surface, independent corners,
 * 48dp trailing targets and optional switch. Grouping lives in
 * {@link YumiGroupCard}, which tells each row whether it is first/middle/last.
 */
public class YumiCell extends LinearLayout {

    private static final int MIN_HEIGHT_DP = 60;

    private final FrameLayout iconFrame;
    private final ImageView iconView;
    private final LinearLayout textBlock;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;
    private final TextView badgeView;
    private final ImageView chevronView;
    private final YumiSwitch switchView;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final YumiRoundRectDrawable surfaceDrawable;

    private boolean showDivider = true;
    private boolean hasIcon;
    private boolean last;
    private int accent = YumiMd3.primary();

    public YumiCell(@NonNull Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(YumiUi.dp(MIN_HEIGHT_DP));
        setPadding(YumiUi.dp(16), 0, YumiUi.dp(12), 0);
        setClickable(true);
        setFocusable(true);
        setWillNotDraw(false);

        dividerPaint.setColor(YumiMd3.hairline());
        dividerPaint.setStrokeWidth(Math.max(1, YumiUi.dp(0.7f)));

        surfaceDrawable = new YumiRoundRectDrawable(YumiMd3.surfaceContainerLow(),
                YumiMd3.RADIUS_XXL_DP);
        setBackground(YumiUi.ripple(YumiMd3.withAlpha(YumiMd3.onSurface(), 0.08f), surfaceDrawable));

        iconFrame = new FrameLayout(context);
        iconFrame.setVisibility(GONE);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconFrame.addView(iconView, new FrameLayout.LayoutParams(YumiUi.dp(20), YumiUi.dp(20), Gravity.CENTER));
        final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(YumiUi.dp(36), YumiUi.dp(36));
        iconLp.setMarginEnd(YumiUi.dp(12));
        addView(iconFrame, iconLp);

        textBlock = new LinearLayout(context);
        textBlock.setOrientation(VERTICAL);
        textBlock.setGravity(Gravity.CENTER_VERTICAL);
        addView(textBlock, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        titleView = YumiUi.text(context, "", 15f, YumiMd3.onSurface(), YumiMd3.REGULAR);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textBlock.addView(titleView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        subtitleView = YumiUi.text(context, "", 12.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setVisibility(GONE);
        final LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = YumiUi.dp(2);
        textBlock.addView(subtitleView, subtitleLp);

        valueView = YumiUi.text(context, "", YumiMd3.LABEL, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        valueView.setSingleLine(true);
        valueView.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        valueView.setVisibility(GONE);
        final LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        valueLp.setMarginStart(YumiUi.dp(8));
        addView(valueView, valueLp);

        badgeView = YumiUi.text(context, "", 11f, YumiMd3.onPrimary(), YumiMd3.MEDIUM);
        badgeView.setGravity(Gravity.CENTER);
        badgeView.setMinWidth(YumiUi.dp(22));
        badgeView.setPadding(YumiUi.dp(6), 0, YumiUi.dp(6), 0);
        badgeView.setBackground(YumiUi.rounded(YumiMd3.primary(), YumiMd3.RADIUS_FULL_DP));
        badgeView.setVisibility(GONE);
        final LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, YumiUi.dp(22));
        badgeLp.setMarginStart(YumiUi.dp(8));
        addView(badgeView, badgeLp);

        chevronView = new ImageView(context);
        chevronView.setImageResource(org.telegram.messenger.R.drawable.yumi_ic_chevron_right);
        chevronView.setScaleType(ImageView.ScaleType.CENTER);
        chevronView.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        chevronView.setVisibility(GONE);
        final LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(YumiUi.dp(24), YumiUi.dp(48));
        chevronLp.setMarginStart(YumiUi.dp(4));
        addView(chevronView, chevronLp);

        switchView = new YumiSwitch(context);
        switchView.setVisibility(GONE);
        final LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(YumiUi.dp(48), YumiUi.dp(48));
        switchLp.setMarginStart(YumiUi.dp(8));
        addView(switchView, switchLp);

        setOnClickListener(v -> {
            if (switchView.getVisibility() == VISIBLE) {
                switchView.performClick();
            }
        });
        setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                YumiMotion.press(v, true);
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                YumiMotion.press(v, false);
            }
            return false;
        });
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
        updateContentDescription();
    }

    public void setSubtitle(CharSequence subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
        updateContentDescription();
    }

    /** Exposes subtitle spans for phone numbers, usernames, and other links. */
    public TextView getSubtitleView() {
        return subtitleView;
    }

    public void setSubtitleMovementMethod(android.text.method.MovementMethod method) {
        subtitleView.setMovementMethod(method);
        subtitleView.setFocusable(method != null);
    }

    public void setValue(CharSequence value) {
        valueView.setText(value);
        valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
        updateContentDescription();
    }

    public void setBadge(CharSequence badge) {
        badgeView.setText(badge);
        badgeView.setVisibility(TextUtils.isEmpty(badge) ? GONE : VISIBLE);
        updateContentDescription();
    }

    public void setIcon(@DrawableRes int iconRes) {
        hasIcon = iconRes != 0;
        iconFrame.setVisibility(hasIcon ? VISIBLE : GONE);
        iconView.setImageResource(iconRes);
        iconView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSecondaryContainer(), PorterDuff.Mode.SRC_IN));
        iconFrame.setBackground(YumiUi.rounded(
                YumiMd3.secondaryContainer(), YumiMd3.RADIUS_S_DP));
        iconFrame.setPadding(YumiUi.dp(8), YumiUi.dp(8), YumiUi.dp(8), YumiUi.dp(8));
        iconView.setImageTintList(null);
        invalidate();
    }

    public void setIconTint(int color) {
        iconView.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
    }

    public void setIconBackground(int color) {
        iconFrame.setBackground(YumiUi.rounded(color, YumiMd3.RADIUS_S_DP));
    }

    public TextView getTitleView() {
        return titleView;
    }

    public TextView getValueView() {
        return valueView;
    }

    public YumiSwitch getSwitch() {
        return switchView;
    }

    public void setSwitchVisible(boolean visible) {
        switchView.setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            chevronView.setVisibility(GONE);
            valueView.setVisibility(GONE);
        }
        updateContentDescription();
    }

    public void setChecked(boolean checked, boolean animated) {
        switchView.setChecked(checked, animated);
        updateContentDescription();
    }

    public boolean isChecked() {
        return switchView.isChecked();
    }

    public void setOnCheckedChangeListener(YumiSwitch.OnCheckedChangeListener listener) {
        switchView.setOnCheckedChangeListener(listener);
    }

    public void setChevronVisible(boolean visible) {
        chevronView.setVisibility(visible ? VISIBLE : GONE);
        if (visible) {
            switchView.setVisibility(GONE);
        }
        updateContentDescription();
    }

    public void setAccent(int color) {
        accent = color;
        switchView.setAccent(color);
        badgeView.setBackground(YumiUi.rounded(color, YumiMd3.RADIUS_FULL_DP));
    }

    public void setShowDivider(boolean show) {
        showDivider = show;
        invalidate();
    }

    public void setGroupPosition(boolean first, boolean middle, boolean last) {
        this.last = last;
        final float r = YumiMd3.RADIUS_XXL_DP;
        surfaceDrawable.setCornerRadiiDp(
                first ? r : 0f,
                first ? r : 0f,
                last ? r : 0f,
                last ? r : 0f);
        if (middle) {
            surfaceDrawable.setUniformRadiusDp(0f);
        }
        setShowDivider(!last);
        invalidate();
    }

    public void updateColors() {
        titleView.setTextColor(YumiMd3.onSurface());
        subtitleView.setTextColor(YumiMd3.onSurfaceVariant());
        valueView.setTextColor(YumiMd3.onSurfaceVariant());
        dividerPaint.setColor(YumiMd3.hairline());
        surfaceDrawable.setFillColor(YumiMd3.surfaceContainerLow());
        badgeView.setTextColor(YumiMd3.onPrimary());
        badgeView.setBackground(YumiUi.rounded(accent, YumiMd3.RADIUS_FULL_DP));
        iconView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSecondaryContainer(), PorterDuff.Mode.SRC_IN));
        iconFrame.setBackground(YumiUi.rounded(
                YumiMd3.secondaryContainer(), YumiMd3.RADIUS_S_DP));
        chevronView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        switchView.setAccent(accent);
        invalidate();
    }

    private void updateContentDescription() {
        final StringBuilder builder = new StringBuilder();
        builder.append(titleView.getText());
        if (subtitleView.getVisibility() == VISIBLE) {
            builder.append(", ").append(subtitleView.getText());
        }
        if (valueView.getVisibility() == VISIBLE) {
            builder.append(", ").append(valueView.getText());
        }
        if (switchView.getVisibility() == VISIBLE) {
            builder.append(", ").append(switchView.isChecked() ? YumiLocale.on() : YumiLocale.off());
        }
        setContentDescription(builder.toString());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (showDivider) {
            final int start = YumiUi.dp(16) + (hasIcon ? YumiUi.dp(48) : 0);
            final int end = getWidth() - YumiUi.dp(12);
            final float y = getHeight() - dividerPaint.getStrokeWidth() / 2f;
            canvas.drawLine(start, y, end, y, dividerPaint);
        }
    }
}
