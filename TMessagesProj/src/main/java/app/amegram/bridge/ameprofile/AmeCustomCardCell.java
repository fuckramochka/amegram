package app.amegram.bridge.ameprofile;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Gold-standard Interactive Custom Card View for "Аме Профіль" (Amegram Profile Studio).
 * Supports solid colors, linear gradients, neon glowing borders, dynamic status badges,
 * rich multi-platform icons, and native ripple touch animations.
 */
public class AmeCustomCardCell extends FrameLayout {

    private final FrameLayout cardContainer;
    private final ImageView iconView;
    private final FrameLayout iconWrapper;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView badgeView;
    private final ImageView chevronView;

    private AmeProfileEngine.AmeCard currentCard;

    public AmeCustomCardCell(Context context) {
        super(context);

        cardContainer = new FrameLayout(context);
        cardContainer.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        // Icon Container
        iconWrapper = new FrameLayout(context);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconWrapper.addView(iconView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        row.addView(iconWrapper, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        // Title + Subtitle Column
        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        subtitleView.setSingleLine(true);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        textCol.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        // Pill Badge
        badgeView = new TextView(context);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        badgeView.setTypeface(AndroidUtilities.bold());
        badgeView.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        badgeView.setVisibility(View.GONE);
        row.addView(badgeView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 6, 0));

        // Chevron
        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_arrowright);
        chevronView.setScaleType(ImageView.ScaleType.CENTER);
        row.addView(chevronView, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 2, 0, 0, 0));

        cardContainer.addView(row, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        addView(cardContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 12, 4, 12, 4));

        setOnClickListener(v -> {
            if (currentCard != null && !TextUtils.isEmpty(currentCard.url)) {
                Browser.openUrl(getContext(), currentCard.url);
            }
        });
    }

    public void bind(AmeProfileEngine.AmeCard card) {
        this.currentCard = card;
        if (card == null) return;

        int defCardBg = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (defCardBg == 0) defCardBg = 0xFF1C242F;
        int bgColor = card.bgColor != 0 ? card.bgColor : defCardBg;

        int defTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
        if (defTextColor == 0) defTextColor = 0xFFFFFFFF;
        int textColor = card.textColor != 0 ? card.textColor : defTextColor;

        int radius = card.radius > 0 ? card.radius : 14;

        // Background styling: Gradient or Solid
        GradientDrawable bg = new GradientDrawable();
        if (card.gradientColor1 != 0 && card.gradientColor2 != 0) {
            bg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            bg.setColors(new int[]{card.gradientColor1, card.gradientColor2});
        } else {
            bg.setColor(bgColor);
        }
        bg.setCornerRadius(AndroidUtilities.dp(radius));

        if (card.borderColor != 0) {
            int strokeWidth = card.borderWidth > 0 ? card.borderWidth : 1;
            bg.setStroke(AndroidUtilities.dp(strokeWidth), card.borderColor);
        }
        cardContainer.setBackground(bg);

        // Native touch ripple
        cardContainer.setClickable(false);
        setClickable(true);
        setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(radius), 0, ColorUtils.setAlphaComponent(textColor, 25)));

        // Title
        titleView.setText(card.title != null ? card.title : "");
        titleView.setTextColor(textColor);

        // Subtitle
        if (!TextUtils.isEmpty(card.subtitle)) {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(card.subtitle);
            int subColor = card.subtitleColor != 0 ? card.subtitleColor : ColorUtils.setAlphaComponent(textColor, 175);
            subtitleView.setTextColor(subColor);
        } else {
            subtitleView.setVisibility(View.GONE);
        }

        // Status Pill Badge
        if (!TextUtils.isEmpty(card.badge)) {
            badgeView.setVisibility(View.VISIBLE);
            badgeView.setText(card.badge);
            int bTextColor = card.badgeTextColor != 0 ? card.badgeTextColor : textColor;
            badgeView.setTextColor(bTextColor);

            int bBgColor = card.badgeBgColor != 0 ? card.badgeBgColor : ColorUtils.setAlphaComponent(textColor, 40);
            GradientDrawable bbg = new GradientDrawable();
            bbg.setColor(bBgColor);
            bbg.setCornerRadius(AndroidUtilities.dp(8));
            badgeView.setBackground(bbg);
        } else {
            badgeView.setVisibility(View.GONE);
        }

        // Chevron Arrow
        chevronView.setColorFilter(ColorUtils.setAlphaComponent(textColor, 130));
        chevronView.setVisibility(!TextUtils.isEmpty(card.url) ? View.VISIBLE : View.GONE);

        // Icon Container & Mapping
        int iconRes = R.drawable.msg_link2;
        String iconKey = card.icon != null ? card.icon.toLowerCase().trim() : "";
        if (iconKey.contains("steam")) {
            iconRes = R.drawable.msg_game;
        } else if (iconKey.contains("discord") || iconKey.contains("github") || iconKey.contains("web") || iconKey.contains("code")) {
            iconRes = R.drawable.msg_openin;
        } else if (iconKey.contains("star") || iconKey.contains("vip") || iconKey.contains("crown")) {
            iconRes = R.drawable.msg_premium_liststar;
        } else if (iconKey.contains("media") || iconKey.contains("photo") || iconKey.contains("gallery")) {
            iconRes = R.drawable.msg_media;
        } else if (iconKey.contains("theme") || iconKey.contains("palette")) {
            iconRes = R.drawable.msg_theme;
        } else if (iconKey.contains("stats") || iconKey.contains("chart")) {
            iconRes = R.drawable.msg_stats;
        } else if (iconKey.contains("music") || iconKey.contains("spotify") || iconKey.contains("audio")) {
            iconRes = R.drawable.msg_round_music;
        } else if (iconKey.contains("tg") || iconKey.contains("telegram") || iconKey.contains("channel") || iconKey.contains("bot")) {
            iconRes = R.drawable.msg_channel;
        }

        iconView.setImageResource(iconRes);
        int iconColor = card.iconColor != 0 ? card.iconColor : textColor;
        iconView.setColorFilter(iconColor);

        int iconBg = card.iconBgColor != 0 ? card.iconBgColor : ColorUtils.setAlphaComponent(iconColor, 35);
        GradientDrawable iconDraw = new GradientDrawable();
        iconDraw.setColor(iconBg);
        iconDraw.setCornerRadius(AndroidUtilities.dp(11));
        iconWrapper.setBackground(iconDraw);
        iconWrapper.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7), AndroidUtilities.dp(7));
    }
}
