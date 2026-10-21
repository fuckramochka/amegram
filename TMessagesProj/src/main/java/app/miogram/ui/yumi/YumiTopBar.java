package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;

import app.amegram.theme.YumiMd3;

/**
 * Yumi Material 3 top app bar. The title is always start aligned; there is no
 * iOS-style centered title and no glass layer. The surface becomes opaque as
 * content scrolls under it.
 */
public class YumiTopBar extends FrameLayout {

    public static final int CONTENT_HEIGHT_DP = 60;

    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ImageView backView;
    private final LinearLayout titleBlock;
    private final TextView titleView;
    private final TextView subtitleView;
    private final LinearLayout actions;
    private final int contentHeight;
    private float reveal;
    private boolean showDivider = true;
    private int statusInset;

    public YumiTopBar(@NonNull Context context) {
        this(context, AndroidUtilities.statusBarHeight);
    }

    public YumiTopBar(@NonNull Context context, int statusInset) {
        super(context);
        this.statusInset = Math.max(0, statusInset);
        contentHeight = YumiUi.dp(CONTENT_HEIGHT_DP);
        setWillNotDraw(false);
        setClipToPadding(false);
        setClickable(true);
        setBackground(null);
        setPadding(0, this.statusInset, 0, 0);

        surfacePaint.setColor(YumiMd3.surface());
        dividerPaint.setColor(YumiMd3.outline());
        dividerPaint.setStrokeWidth(Math.max(1, YumiUi.dp(0.7f)));

        backView = new ImageView(context);
        backView.setImageResource(org.telegram.messenger.R.drawable.yumi_ic_arrow_back);
        backView.setScaleType(ImageView.ScaleType.CENTER);
        backView.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurface(), PorterDuff.Mode.SRC_IN));
        backView.setContentDescription(YumiLocale.back());
        backView.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_FULL_DP));
        backView.setVisibility(GONE);
        addView(backView, frame(48, 48, Gravity.TOP | Gravity.START, 8, 6, 0, 0));

        titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        titleBlock.setGravity(Gravity.CENTER_VERTICAL);
        titleBlock.setClipChildren(false);
        addView(titleBlock, frame(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START, 68, 6, 56, 0));

        titleView = YumiUi.text(context, "", YumiMd3.TITLE, YumiMd3.onSurface(), YumiMd3.MEDIUM);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        titleBlock.addView(titleView, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        subtitleView = YumiUi.text(context, "", YumiMd3.CAPTION, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setVisibility(GONE);
        final LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = YumiUi.dp(2);
        titleBlock.addView(subtitleView, subtitleLp);

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        addView(actions, frame(LayoutParams.WRAP_CONTENT, 48,
                Gravity.TOP | Gravity.END, 0, 6, 8, 0));
    }

    private static LayoutParams frame(int width, int height, int gravity,
                                      int left, int top, int right, int bottom) {
        final LayoutParams lp = new LayoutParams(width, height, gravity);
        lp.setMargins(YumiUi.dp(left), YumiUi.dp(top), YumiUi.dp(right), YumiUi.dp(bottom));
        return lp;
    }

    public void setStatusInset(int inset) {
        statusInset = Math.max(0, inset);
        setPadding(0, statusInset, 0, 0);
        requestLayout();
    }

    public void setTitle(CharSequence title) {
        titleView.setText(title);
        setContentDescription(title);
    }

    public void setSubtitle(CharSequence subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
    }

    public TextView getTitleView() {
        return titleView;
    }

    public void setOnBackClickListener(OnClickListener listener) {
        backView.setVisibility(listener == null ? GONE : VISIBLE);
        backView.setOnClickListener(listener);
    }

    public void setBackVisible(boolean visible) {
        backView.setVisibility(visible ? VISIBLE : GONE);
    }

    public void setShowDivider(boolean show) {
        showDivider = show;
        invalidate();
    }

    /** 0 = transparent over content, 1 = opaque top app bar. */
    public void setReveal(float value) {
        value = Math.max(0f, Math.min(1f, value));
        if (Math.abs(reveal - value) < 0.001f) {
            return;
        }
        reveal = value;
        titleView.setAlpha(0.72f + 0.28f * value);
        subtitleView.setAlpha(0.72f + 0.28f * value);
        invalidate();
    }

    public View addAction(@DrawableRes int iconRes, CharSequence contentDescription, OnClickListener listener) {
        final ImageView action = new ImageView(getContext());
        action.setImageResource(iconRes);
        action.setScaleType(ImageView.ScaleType.CENTER);
        action.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        action.setContentDescription(contentDescription);
        action.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_FULL_DP));
        action.setOnClickListener(listener);
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(YumiUi.dp(48), YumiUi.dp(48));
        lp.setMarginStart(YumiUi.dp(2));
        actions.addView(action, lp);
        return action;
    }

    public void clearActions() {
        actions.removeAllViews();
    }

    public void updateColors() {
        surfacePaint.setColor(YumiMd3.surface());
        dividerPaint.setColor(YumiMd3.outline());
        titleView.setTextColor(YumiMd3.onSurface());
        subtitleView.setTextColor(YumiMd3.onSurfaceVariant());
        backView.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurface(), PorterDuff.Mode.SRC_IN));
        for (int i = 0; i < actions.getChildCount(); i++) {
            final Drawable drawable = ((ImageView) actions.getChildAt(i)).getDrawable();
            if (drawable != null) {
                drawable.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            }
        }
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int height = statusInset + contentHeight;
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int alpha = Math.round(255 * (0.20f + 0.80f * reveal));
        surfacePaint.setAlpha(alpha);
        canvas.drawRect(0, 0, getWidth(), getHeight(), surfacePaint);
        if (showDivider && reveal > 0.01f) {
            dividerPaint.setAlpha(Math.round(255 * reveal));
            canvas.drawLine(0, getHeight() - dividerPaint.getStrokeWidth() / 2f,
                    getWidth(), getHeight() - dividerPaint.getStrokeWidth() / 2f, dividerPaint);
        }
    }
}
