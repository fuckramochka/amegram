package app.miogram.ui.yumi;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;

import app.amegram.theme.YumiMd3;

/** Anchored Material 3 expressive popup menu with 48dp rows. */
public class YumiPopupMenu extends PopupWindow {

    private final Context context;
    private final LinearLayout list;
    private final int width;

    public YumiPopupMenu(@NonNull Context context) {
        super(context);
        this.context = context;
        width = Math.min(YumiUi.dp(260),
                Math.max(YumiUi.dp(180), AndroidUtilities.displaySize.x - YumiUi.dp(24)));
        setBackgroundDrawable(new ColorDrawable(0));
        setOutsideTouchable(true);
        setFocusable(true);
        setClippingEnabled(true);
        setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);

        final FrameLayout card = new FrameLayout(context);
        card.setBackground(YumiUi.rounded(YumiMd3.popupSurface(),
                YumiMd3.RADIUS_M_DP, YumiMd3.outline(), 1f));
        card.setElevation(YumiUi.dp(8));
        card.setPadding(YumiUi.dp(6), YumiUi.dp(6), YumiUi.dp(6), YumiUi.dp(6));

        list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        card.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(card);
        setWidth(width);
        setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    public YumiPopupMenu addItem(@DrawableRes int iconRes, CharSequence title, Runnable action) {
        return addItem(iconRes, title, 0, action);
    }

    public YumiPopupMenu addItem(@DrawableRes int iconRes, CharSequence title, int tint, Runnable action) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(YumiUi.dp(48));
        row.setPadding(YumiUi.dp(12), 0, YumiUi.dp(12), 0);
        row.setBackground(YumiUi.ripple(YumiMd3.withAlpha(YumiMd3.onSurface(), 0.09f),
                YumiMd3.rounded(0, YumiMd3.RADIUS_S_DP)));
        row.setClickable(true);
        row.setFocusable(true);

        final ImageView icon = new ImageView(context);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setImageResource(iconRes);
        final int iconColor = tint == 0 ? YumiMd3.onSurfaceVariant() : tint;
        icon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
        row.addView(icon, new LinearLayout.LayoutParams(YumiUi.dp(24), YumiUi.dp(24)));

        final TextView text = YumiUi.text(context, title, YumiMd3.BODY,
                iconColor, YumiMd3.REGULAR);
        text.setSingleLine(true);
        text.setEllipsize(android.text.TextUtils.TruncateAt.END);
        final LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginStart(YumiUi.dp(12));
        row.addView(text, textLp);

        row.setOnClickListener(v -> {
            YumiUi.haptic(v);
            dismiss();
            if (action != null) {
                action.run();
            }
        });
        list.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return this;
    }

    public YumiPopupMenu addDivider() {
        final View divider = new View(context);
        divider.setBackgroundColor(YumiMd3.hairline());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, YumiUi.dp(0.7f)));
        lp.setMargins(YumiUi.dp(12), YumiUi.dp(4), YumiUi.dp(12), YumiUi.dp(4));
        list.addView(divider, lp);
        return this;
    }

    public void showAt(@NonNull View anchor) {
        int xoff = anchor.getWidth() - width;
        if (xoff > 0 && YumiUi.isRtl()) {
            xoff = 0;
        }
        showAsDropDown(anchor, Math.max(0, xoff), YumiUi.dp(6));
    }
}
