package app.miogram.ui.yumi;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;

import app.amegram.theme.YumiMd3;

/** A Material 3 expressive dialog with Yumi surfaces, scrim and spring entrance. */
public class YumiDialog extends Dialog {

    public interface OnActionListener {
        void onAction(YumiDialog dialog);
    }

    private final LinearLayout card;
    private final FrameLayout iconFrame;
    private final ImageView iconView;
    private final TextView eyebrowView;
    private final TextView titleView;
    private final TextView messageView;
    private final LinearLayout contentContainer;
    private final LinearLayout actions;
    private final YumiRoundRectDrawable cardBackground;

    private OnActionListener positiveListener;
    private OnActionListener negativeListener;
    private CharSequence positiveText;
    private CharSequence negativeText;

    public YumiDialog(@NonNull Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.42f);
            window.setGravity(Gravity.CENTER);
            window.setWindowAnimations(0);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        final FrameLayout root = new FrameLayout(context);
        root.setPadding(YumiUi.dp(28), YumiUi.dp(24), YumiUi.dp(28), YumiUi.dp(24));
        root.setClipChildren(false);

        cardBackground = new YumiRoundRectDrawable(YumiMd3.dialogSurface(), YumiMd3.RADIUS_XXL_DP);
        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(YumiUi.dp(24), YumiUi.dp(24), YumiUi.dp(24), YumiUi.dp(16));
        card.setBackground(cardBackground);
        card.setElevation(YumiUi.dp(8));
        root.addView(card, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        iconFrame = new FrameLayout(context);
        iconFrame.setVisibility(View.GONE);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onPrimaryContainer(), PorterDuff.Mode.SRC_IN));
        iconFrame.addView(iconView, new FrameLayout.LayoutParams(
                YumiUi.dp(24), YumiUi.dp(24), Gravity.CENTER));
        final LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                YumiUi.dp(48), YumiUi.dp(48));
        iconLp.bottomMargin = YumiUi.dp(16);
        card.addView(iconFrame, iconLp);

        eyebrowView = YumiUi.text(context, "", YumiMd3.CAPTION,
                YumiMd3.primary(), YumiMd3.MEDIUM);
        eyebrowView.setLetterSpacing(0.12f);
        eyebrowView.setAllCaps(true);
        eyebrowView.setVisibility(View.GONE);
        card.addView(eyebrowView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        titleView = YumiUi.text(context, "", YumiMd3.TITLE_LARGE,
                YumiMd3.onSurface(), YumiMd3.MEDIUM);
        titleView.setLineSpacing(0, 1.05f);
        final LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = YumiUi.dp(4);
        card.addView(titleView, titleLp);
        ViewCompat.setAccessibilityHeading(titleView, true);

        messageView = YumiUi.text(context, "", YumiMd3.BODY,
                YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        messageView.setLineSpacing(YumiUi.dp(4), 1.15f);
        final LinearLayout.LayoutParams messageLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageLp.topMargin = YumiUi.dp(10);
        card.addView(messageView, messageLp);

        contentContainer = new LinearLayout(context);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        contentLp.topMargin = YumiUi.dp(12);
        card.addView(contentContainer, contentLp);

        actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        final LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = YumiUi.dp(24);
        card.addView(actions, actionsLp);

        setContentView(root);
        setCanceledOnTouchOutside(true);
    }

    public YumiDialog setIcon(int iconRes) {
        if (iconRes == 0) {
            iconFrame.setVisibility(View.GONE);
        } else {
            iconView.setImageResource(iconRes);
            iconFrame.setVisibility(View.VISIBLE);
            iconFrame.setBackground(YumiUi.rounded(
                    YumiMd3.primaryContainer(), YumiMd3.RADIUS_M_DP));
            iconFrame.setPadding(YumiUi.dp(12), YumiUi.dp(12), YumiUi.dp(12), YumiUi.dp(12));
        }
        return this;
    }

    public YumiDialog setEyebrow(CharSequence eyebrow) {
        eyebrowView.setText(eyebrow);
        eyebrowView.setVisibility(TextUtils.isEmpty(eyebrow) ? View.GONE : View.VISIBLE);
        return this;
    }

    public YumiDialog setYumiTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public YumiDialog setMessage(CharSequence message) {
        messageView.setText(message);
        messageView.setVisibility(TextUtils.isEmpty(message) ? View.GONE : View.VISIBLE);
        return this;
    }

    public YumiDialog setContent(View view) {
        contentContainer.removeAllViews();
        if (view != null) {
            contentContainer.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return this;
    }

    public YumiDialog setPositive(CharSequence text, OnActionListener listener) {
        positiveText = text;
        positiveListener = listener;
        rebuildActions();
        return this;
    }

    public YumiDialog setNegative(CharSequence text, OnActionListener listener) {
        negativeText = text;
        negativeListener = listener;
        rebuildActions();
        return this;
    }

    private void rebuildActions() {
        actions.removeAllViews();
        if (!TextUtils.isEmpty(negativeText)) {
            final YumiButton button = new YumiButton(getContext(), YumiButton.STYLE_TEXT);
            button.setButtonText(negativeText);
            button.setOnClickListener(v -> {
                if (negativeListener != null) {
                    negativeListener.onAction(this);
                } else {
                    dismiss();
                }
            });
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(YumiUi.dp(8));
            actions.addView(button, lp);
        }
        if (!TextUtils.isEmpty(positiveText)) {
            final YumiButton button = new YumiButton(getContext(), YumiButton.STYLE_TONAL);
            button.setButtonText(positiveText);
            button.setOnClickListener(v -> {
                if (positiveListener != null) {
                    positiveListener.onAction(this);
                } else {
                    dismiss();
                }
            });
            actions.addView(button, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (actions.getChildCount() == 0) {
            actions.setVisibility(View.GONE);
        } else {
            actions.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void show() {
        super.show();
        final Window window = getWindow();
        if (window != null) {
            final int width = Math.min(AndroidUtilities.displaySize.x - YumiUi.dp(48), YumiUi.dp(420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        if (YumiPowerSave.animations()) {
            card.setAlpha(0f);
            card.setScaleX(0.94f);
            card.setScaleY(0.94f);
            card.setTranslationY(YumiUi.dp(12));
            card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0f)
                    .setDuration(YumiMd3.DURATION_STANDARD_MS)
                    .setInterpolator(YumiMotion.EMPHASIZED)
                    .start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    public void updateColors() {
        cardBackground.setFillColor(YumiMd3.dialogSurface());
        titleView.setTextColor(YumiMd3.onSurface());
        messageView.setTextColor(YumiMd3.onSurfaceVariant());
        eyebrowView.setTextColor(YumiMd3.primary());
        iconView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onPrimaryContainer(), PorterDuff.Mode.SRC_IN));
        iconFrame.setBackground(YumiUi.rounded(
                YumiMd3.primaryContainer(), YumiMd3.RADIUS_M_DP));
        for (int i = 0; i < actions.getChildCount(); i++) {
            ((YumiButton) actions.getChildAt(i)).updateColors();
        }
    }
}
