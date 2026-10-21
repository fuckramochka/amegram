package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;

import app.amegram.theme.YumiMd3;

/** An in-app Yumi snackbar with an optional action. */
public class YumiSnackbar extends FrameLayout {

    private static final String TAG = "yumi_snackbar";

    private final TextView messageView;
    private final TextView actionView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable dismissRunnable = this::dismiss;

    public YumiSnackbar(@NonNull Context context, CharSequence message,
                        @Nullable CharSequence action, @Nullable Runnable actionRunnable) {
        super(context);
        setTag(TAG);
        setClickable(true);
        setElevation(YumiUi.dp(6));
        setBackground(YumiUi.rounded(YumiMd3.snackbarBackground(), YumiMd3.RADIUS_M_DP));
        setPadding(YumiUi.dp(16), YumiUi.dp(8), YumiUi.dp(8), YumiUi.dp(8));
        setMinimumHeight(YumiUi.dp(52));

        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        messageView = YumiUi.text(context, message, YumiMd3.BODY_SMALL,
                YumiMd3.snackbarText(), YumiMd3.REGULAR);
        messageView.setMaxLines(3);
        row.addView(messageView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        actionView = YumiUi.text(context, action == null ? "" : action,
                YumiMd3.LABEL, YumiMd3.primary(), YumiMd3.MEDIUM);
        actionView.setGravity(Gravity.CENTER);
        actionView.setMinHeight(YumiUi.dp(48));
        actionView.setPadding(YumiUi.dp(12), 0, YumiUi.dp(12), 0);
        actionView.setVisibility(action == null ? GONE : VISIBLE);
        actionView.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_S_DP));
        actionView.setOnClickListener(v -> {
            if (actionRunnable != null) {
                actionRunnable.run();
            }
            dismiss();
        });
        final LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionLp.setMarginStart(YumiUi.dp(8));
        row.addView(actionView, actionLp);

        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setContentDescription(message);
    }

    public static YumiSnackbar show(@NonNull View anchor, CharSequence message) {
        return show(anchor, message, null, null);
    }

    public static YumiSnackbar show(@NonNull View anchor, CharSequence message,
                                    @Nullable CharSequence action, @Nullable Runnable actionRunnable) {
        final ViewGroup content = findContent(anchor);
        if (content == null) {
            return null;
        }
        final View old = content.findViewWithTag(TAG);
        if (old != null) {
            content.removeView(old);
        }
        final YumiSnackbar snackbar = new YumiSnackbar(anchor.getContext(), message, action, actionRunnable);
        final FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        lp.leftMargin = YumiUi.dp(16);
        lp.rightMargin = YumiUi.dp(16);
        lp.bottomMargin = AndroidUtilities.navigationBarHeight + YumiUi.dp(92);
        content.addView(snackbar, lp);
        if (YumiPowerSave.animations()) {
            snackbar.setAlpha(0f);
            snackbar.setTranslationY(YumiUi.dp(20));
            snackbar.animate().alpha(1f).translationY(0f)
                    .setDuration(YumiMd3.DURATION_STANDARD_MS)
                    .setInterpolator(YumiMotion.EMPHASIZED).start();
        }
        snackbar.handler.postDelayed(snackbar.dismissRunnable, 4200L);
        snackbar.announceForAccessibility(message);
        return snackbar;
    }

    @Nullable
    private static ViewGroup findContent(View anchor) {
        View root = anchor.getRootView();
        View content = root.findViewById(android.R.id.content);
        if (content instanceof ViewGroup) {
            return (ViewGroup) content;
        }
        if (root instanceof ViewGroup) {
            return (ViewGroup) root;
        }
        return null;
    }

    public void dismiss() {
        handler.removeCallbacks(dismissRunnable);
        if (getParent() instanceof ViewGroup) {
            final ViewGroup parent = (ViewGroup) getParent();
            if (YumiPowerSave.animations()) {
                animate().alpha(0f).translationY(YumiUi.dp(12))
                        .setDuration(YumiMd3.DURATION_PRESS_MS)
                        .withEndAction(() -> parent.removeView(this))
                        .start();
            } else {
                parent.removeView(this);
            }
        }
    }

    public void updateColors() {
        setBackground(YumiUi.rounded(YumiMd3.snackbarBackground(), YumiMd3.RADIUS_M_DP));
        messageView.setTextColor(YumiMd3.snackbarText());
        actionView.setTextColor(YumiMd3.primary());
    }
}
