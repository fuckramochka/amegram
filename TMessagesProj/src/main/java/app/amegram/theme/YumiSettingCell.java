package app.amegram.theme;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

/**
 * Чистая ячейка настроек Yumigram в стиле Material Design 3.
 * Исключает наложение текста: заголовок и подпись расположены строго в колонку.
 * Слева — сквиркл 12dp с тональной заливкой и иконкой.
 * Справа — шеврон перехода или текстовое значение.
 */
public class YumiSettingCell extends FrameLayout {

    private final FrameLayout iconFrame;
    private final ImageView iconView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final TextView valueView;
    private final ImageView chevronView;
    private final LinearLayout textLayout;

    private boolean needDivider;
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public YumiSettingCell(Context context) {
        super(context);

        setBackground(Theme.getSelectorDrawable(false));
        setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        setMinimumHeight(AndroidUtilities.dp(64));

        iconFrame = new FrameLayout(context);
        iconView = new ImageView(context);
        iconView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        iconFrame.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        addView(iconFrame, LayoutHelper.createFrame(40, 40, Gravity.CENTER_VERTICAL | Gravity.START));

        textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 1));

        subtitleView = new TextView(context);
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setMaxLines(2);
        subtitleView.setEllipsize(TextUtils.TruncateAt.END);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1f);
        textLayout.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL);
        textLp.setMargins(AndroidUtilities.dp(54), 0, AndroidUtilities.dp(36), 0);
        addView(textLayout, textLp);

        valueView = new TextView(context);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        valueView.setSingleLine(true);
        valueView.setEllipsize(TextUtils.TruncateAt.END);
        valueView.setVisibility(GONE);
        addView(valueView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL | Gravity.END, 0, 0, 24, 0));

        chevronView = new ImageView(context);
        chevronView.setImageResource(R.drawable.msg_arrowright);
        chevronView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText), PorterDuff.Mode.MULTIPLY));
        addView(chevronView, LayoutHelper.createFrame(16, 16, Gravity.CENTER_VERTICAL | Gravity.END));

        dividerPaint.setColor(Theme.getColor(Theme.key_divider));
    }

    public void setDrawable(android.graphics.drawable.Drawable icon, CharSequence title, CharSequence subtitle, boolean showChevron, boolean divider) {
        iconFrame.setVisibility(VISIBLE);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(
                (Theme.isCurrentThemeDark() ? 0x2A000000 : 0x1C000000) | 0x00808080));
        iconView.setImageDrawable(icon);
        iconView.setColorFilter(null);
        FrameLayout.LayoutParams textLp = (FrameLayout.LayoutParams) textLayout.getLayoutParams();
        textLp.leftMargin = AndroidUtilities.dp(54);
        textLp.rightMargin = showChevron ? AndroidUtilities.dp(36) : 0;
        textLayout.setLayoutParams(textLp);
        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        if (!TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(VISIBLE);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            subtitleView.setVisibility(GONE);
        }
        valueView.setVisibility(GONE);
        chevronView.setVisibility(showChevron ? VISIBLE : GONE);
        this.needDivider = divider;
        invalidate();
    }

    public void set(int iconRes, int iconBgColor, CharSequence title, CharSequence subtitle, CharSequence value, boolean showChevron, boolean divider) {
        if (iconRes != 0) {
            iconFrame.setVisibility(VISIBLE);
            int baseColor = iconBgColor != 0 ? iconBgColor : YumiTheme.getPrimary();
            boolean isDark = Theme.isCurrentThemeDark();
            int bg = (baseColor & 0x00FFFFFF) | (isDark ? 0x2A000000 : 0x1C000000);
            iconFrame.setBackground(YumiTheme.squircleIconBackground(bg));
            iconView.setImageResource(iconRes);
            iconView.setColorFilter(new PorterDuffColorFilter(baseColor, PorterDuff.Mode.SRC_IN));
        } else {
            iconFrame.setVisibility(GONE);
        }

        FrameLayout.LayoutParams textLp = (FrameLayout.LayoutParams) textLayout.getLayoutParams();
        textLp.leftMargin = iconRes != 0 ? AndroidUtilities.dp(54) : 0;
        textLp.rightMargin = showChevron ? AndroidUtilities.dp(36) : (TextUtils.isEmpty(value) ? 0 : AndroidUtilities.dp(50));
        textLayout.setLayoutParams(textLp);

        titleView.setText(title);
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));

        if (!TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(VISIBLE);
            subtitleView.setText(subtitle);
            subtitleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        } else {
            subtitleView.setVisibility(GONE);
        }

        if (!TextUtils.isEmpty(value)) {
            valueView.setVisibility(VISIBLE);
            valueView.setText(value);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        } else {
            valueView.setVisibility(GONE);
        }

        chevronView.setVisibility(showChevron ? VISIBLE : GONE);
        this.needDivider = divider;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (needDivider) {
            int left = iconFrame.getVisibility() == VISIBLE ? AndroidUtilities.dp(70) : AndroidUtilities.dp(16);
            canvas.drawLine(left, getMeasuredHeight() - 1, getMeasuredWidth(), getMeasuredHeight() - 1, dividerPaint);
        }
    }

    public static class Factory extends UItem.UItemFactory<YumiSettingCell> {
        static {
            setup(new Factory());
        }

        @Override
        public YumiSettingCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            return new YumiSettingCell(context);
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            YumiSettingCell cell = (YumiSettingCell) view;
            int iconBg = (int) item.longValue;
            boolean showChevron = item.accent;
            if (item.object instanceof android.graphics.drawable.Drawable) {
                cell.setDrawable((android.graphics.drawable.Drawable) item.object,
                        item.text, item.subtext, showChevron, divider);
                return;
            }
            cell.set(item.iconResId, iconBg, item.text, item.subtext, item.textValue, showChevron, divider);
        }

        public static UItem of(int id, int iconRes, int iconBgColor, CharSequence title, CharSequence subtitle) {
            return of(id, iconRes, iconBgColor, title, subtitle, null, true);
        }

        public static UItem of(int id, int iconRes, int iconBgColor, CharSequence title, CharSequence subtitle, CharSequence value) {
            return of(id, iconRes, iconBgColor, title, subtitle, value, true);
        }

        public static UItem ofDrawable(int id, android.graphics.drawable.Drawable icon, CharSequence title, CharSequence subtitle) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.iconResId = 0;
            item.object = icon;
            item.text = title;
            item.subtext = subtitle;
            item.accent = true;
            return item;
        }

        public static UItem of(int id, int iconRes, int iconBgColor, CharSequence title, CharSequence subtitle, CharSequence value, boolean showChevron) {
            UItem item = UItem.ofFactory(Factory.class);
            item.id = id;
            item.iconResId = iconRes;
            item.longValue = iconBgColor;
            item.text = title;
            item.subtext = subtitle;
            item.textValue = value;
            item.accent = showChevron;
            return item;
        }
    }
}
