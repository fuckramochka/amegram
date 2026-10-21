package app.miogram.ui.yumi;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import org.telegram.messenger.AndroidUtilities;

import app.amegram.theme.YumiMd3;

/** A bottom sheet owned by Yumi: solid tonal surface, 28dp top corners, drag handle. */
public class YumiBottomSheet extends Dialog {

    private final LinearLayout sheet;
    private final LinearLayout header;
    private final LinearLayout contentHost;
    private final TextView titleView;
    private final TextView subtitleView;
    private final ImageView closeView;
    private final YumiRoundRectDrawable surface;
    private final ScrollView scrollView;
    private final int maxHeight;

    private float downY;
    private boolean dragging;

    public YumiBottomSheet(@NonNull Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        final Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.38f);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(0);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        maxHeight = Math.round(AndroidUtilities.displaySize.y * 0.86f);

        final FrameLayout root = new FrameLayout(context);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.setOnClickListener(v -> dismiss());

        sheet = new LinearLayout(context);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setFocusable(true);
        surface = new YumiRoundRectDrawable(YumiMd3.surfaceContainerLow(),
                YumiMd3.RADIUS_XXL_DP);
        sheet.setBackground(surface);
        sheet.setElevation(YumiUi.dp(12));
        sheet.setPadding(0, YumiUi.dp(8), 0, YumiUi.dp(8));
        final FrameLayout.LayoutParams sheetLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        root.addView(sheet, sheetLp);

        final FrameLayout dragArea = new FrameLayout(context);
        dragArea.setMinimumHeight(YumiUi.dp(24));
        final View handle = new View(context);
        final GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(YumiMd3.outlineStrong());
        handleBg.setCornerRadius(YumiUi.dp(2));
        handle.setBackground(handleBg);
        dragArea.addView(handle, new FrameLayout.LayoutParams(
                YumiUi.dp(32), YumiUi.dp(4), Gravity.CENTER));
        sheet.addView(dragArea, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, YumiUi.dp(24)));
        dragArea.setOnTouchListener(this::onDragTouch);

        header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(YumiUi.dp(24), YumiUi.dp(4), YumiUi.dp(12), YumiUi.dp(8));
        sheet.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout titleBlock = new LinearLayout(context);
        titleBlock.setOrientation(LinearLayout.VERTICAL);
        header.addView(titleBlock, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        titleView = YumiUi.text(context, "", YumiMd3.TITLE,
                YumiMd3.onSurface(), YumiMd3.MEDIUM);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleBlock.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ViewCompat.setAccessibilityHeading(titleView, true);

        subtitleView = YumiUi.text(context, "", YumiMd3.BODY_SMALL,
                YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        subtitleView.setVisibility(View.GONE);
        final LinearLayout.LayoutParams subtitleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleLp.topMargin = YumiUi.dp(2);
        titleBlock.addView(subtitleView, subtitleLp);

        closeView = new ImageView(context);
        closeView.setImageResource(org.telegram.messenger.R.drawable.yumi_ic_close);
        closeView.setScaleType(ImageView.ScaleType.CENTER);
        closeView.setContentDescription(YumiLocale.close());
        closeView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        closeView.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_FULL_DP));
        closeView.setOnClickListener(v -> {
            YumiUi.haptic(v);
            dismiss();
        });
        header.addView(closeView, new LinearLayout.LayoutParams(
                YumiUi.dp(48), YumiUi.dp(48)));

        scrollView = new ScrollView(context);
        scrollView.setClipToPadding(false);
        scrollView.setPadding(0, 0, 0, YumiUi.dp(8));
        scrollView.setFillViewport(false);
        sheet.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        contentHost = new LinearLayout(context);
        contentHost.setOrientation(LinearLayout.VERTICAL);
        contentHost.setPadding(YumiUi.dp(24), 0, YumiUi.dp(24), YumiUi.dp(8));
        scrollView.addView(contentHost, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(root);
        setCanceledOnTouchOutside(true);
    }

    public YumiBottomSheet setYumiTitle(CharSequence title) {
        titleView.setText(title);
        return this;
    }

    public YumiBottomSheet setSubtitle(CharSequence subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        return this;
    }

    public YumiBottomSheet setContent(@Nullable View view) {
        contentHost.removeAllViews();
        if (view != null) {
            contentHost.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return this;
    }

    public LinearLayout getContentHost() {
        return contentHost;
    }

    private boolean onDragTouch(View v, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downY = event.getRawY();
                dragging = true;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    final float delta = Math.max(0, event.getRawY() - downY);
                    sheet.setTranslationY(delta);
                    sheet.setAlpha(1f - Math.min(0.25f, delta / (maxHeight * 1.5f)));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    final float delta = sheet.getTranslationY();
                    if (delta > YumiUi.dp(96)) {
                        dismiss();
                    } else {
                        sheet.animate().translationY(0f).alpha(1f)
                                .setDuration(YumiMd3.DURATION_STANDARD_MS)
                                .setInterpolator(YumiMotion.EMPHASIZED).start();
                    }
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void show() {
        super.show();
        final Window window = getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        if (YumiPowerSave.animations()) {
            sheet.post(() -> {
                sheet.setTranslationY(sheet.getHeight());
                sheet.setAlpha(0.6f);
                sheet.animate().translationY(0f).alpha(1f)
                        .setDuration(YumiMd3.DURATION_ENTER_MS)
                        .setInterpolator(YumiMotion.EMPHASIZED).start();
            });
        }
    }

    @Override
    public void onBackPressed() {
        cancel();
    }

    public void updateColors() {
        surface.setFillColor(YumiMd3.surfaceContainerLow());
        titleView.setTextColor(YumiMd3.onSurface());
        subtitleView.setTextColor(YumiMd3.onSurfaceVariant());
        closeView.setColorFilter(new PorterDuffColorFilter(
                YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
    }
}
