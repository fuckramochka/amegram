package app.amegram.theme;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Yumi — единая дизайн-система Yumigram (см. docs/YUMI_STYLE.md).
 * Готовые кирпичи: карточка, кнопки, чип, бейдж, баннер, шапка секции,
 * иконка-сквиркл, ручка шита, фон шита, поле поиска.
 * Все размеры/радиусы — только из токенов YumiTheme. Цвета текста
 * берет вызывающий (диалог vs экран), фоны — тональные поверхности.
 */
public final class YumiComponents {

    private YumiComponents() {
    }

    // ---------- текст ----------

    private static TextView text(Context context, CharSequence s, float sp, boolean bold, int color) {
        TextView v = new TextView(context);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        if (bold) v.setTypeface(AndroidUtilities.bold());
        v.setTextColor(color);
        return v;
    }

    public static TextView title(Context context, CharSequence s, int color) {
        return text(context, s, YumiTheme.TEXT_TITLE, true, color);
    }

    public static TextView head(Context context, CharSequence s, int color) {
        return text(context, s, YumiTheme.TEXT_HEAD, true, color);
    }

    public static TextView body(Context context, CharSequence s, int color) {
        return text(context, s, YumiTheme.TEXT_BODY, false, color);
    }

    public static TextView caption(Context context, CharSequence s, int color) {
        return text(context, s, YumiTheme.TEXT_CAPTION, false, color);
    }

    /** Заголовок секции: капс, акцент, отступы 4/6. */
    public static TextView sectionHeader(Context context, CharSequence s) {
        TextView v = text(context, s.toString().toUpperCase(), YumiTheme.TEXT_MICRO + 0.5f, true,
                Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        v.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 4, 0, 6));
        return v;
    }

    // ---------- карточка ----------

    /** Тональная карточка Yumi: surfaceContainer, радиус 20, паддинги 16/14. */
    public static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setPadding(AndroidUtilities.dp(YumiTheme.CARD_PAD_H),
                AndroidUtilities.dp(YumiTheme.CARD_PAD_V),
                AndroidUtilities.dp(YumiTheme.CARD_PAD_H),
                AndroidUtilities.dp(YumiTheme.CARD_PAD_V));
        return card;
    }

    /** Обертка карточки для списков: поля 14/5. */
    public static LinearLayout cardWrap(Context context, View card) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(YumiTheme.CARD_MARGIN_H),
                AndroidUtilities.dp(YumiTheme.CARD_MARGIN_V),
                AndroidUtilities.dp(YumiTheme.CARD_MARGIN_H),
                AndroidUtilities.dp(YumiTheme.CARD_MARGIN_V));
        card.setLayoutParams(lp);
        wrap.addView(card);
        return wrap;
    }

    // ---------- кнопки ----------

    private static TextView baseButton(Context context, CharSequence s, float sp) {
        TextView v = new TextView(context);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        v.setTypeface(AndroidUtilities.bold());
        v.setGravity(Gravity.CENTER);
        return v;
    }

    /** Главная кнопка: primary-заливка, белый текст 15, радиус 14, паддинг 13. */
    public static TextView primaryButton(Context context, CharSequence s) {
        TextView v = baseButton(context, s, YumiTheme.TEXT_HEAD);
        v.setTextColor(0xFFFFFFFF);
        v.setBackground(YumiTheme.buttonRipple(YumiTheme.getPrimary(), YumiTheme.BTN_RADIUS));
        v.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
        return v;
    }

    /** Компактная пилюля: primary, белый текст 13.5, радиус 12, паддинги 18/9. */
    public static TextView pillButton(Context context, CharSequence s) {
        TextView v = baseButton(context, s, YumiTheme.TEXT_SMALL + 0.5f);
        v.setTextColor(0xFFFFFFFF);
        v.setBackground(YumiTheme.buttonRipple(YumiTheme.getPrimary(), YumiTheme.PILL_RADIUS));
        v.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9),
                AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        return v;
    }

    /** Призрачная кнопка: контур, серый текст 13, радиус 12, паддинг 10. */
    public static TextView ghostButton(Context context, CharSequence s, int textColor) {
        TextView v = baseButton(context, s, YumiTheme.TEXT_SMALL);
        v.setTextColor(textColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(YumiTheme.PILL_RADIUS));
        bg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        bg.setColor(0x00000000);
        v.setBackground(bg);
        v.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        return v;
    }

    // ---------- чип / бейдж ----------

    /** Чип фильтра: пилюля 99, паддинги 14/8, жирный 12.5. */
    public static TextView chip(Context context, CharSequence s, boolean selected) {
        TextView v = baseButton(context, s, YumiTheme.TEXT_CAPTION + 0.5f);
        v.setTextColor(selected ? 0xFFFFFFFF
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(99));
        if (selected) {
            bg.setColor(YumiTheme.getPrimary());
        } else {
            bg.setStroke(AndroidUtilities.dp(1), 0x44FFFFFF);
            bg.setColor(0x00000000);
        }
        v.setBackground(bg);
        v.setPadding(AndroidUtilities.dp(YumiTheme.CHIP_PAD_H),
                AndroidUtilities.dp(YumiTheme.CHIP_PAD_V),
                AndroidUtilities.dp(YumiTheme.CHIP_PAD_H),
                AndroidUtilities.dp(YumiTheme.CHIP_PAD_V));
        return v;
    }

    /** Бейдж версии: контурная пилюля, серый жирный 11, паддинги 8/3. */
    public static TextView badge(Context context, CharSequence s, int textColor) {
        TextView v = baseButton(context, s, YumiTheme.TEXT_MICRO);
        v.setTextColor(textColor);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(99));
        bg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        v.setBackground(bg);
        v.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3),
                AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        return v;
    }

    // ---------- баннер / шит / иконка / поиск ----------

    /** Градиентный баннер: радиус 18, паддинги 16/14, белый заголовок 16 + подзаголовок 13. */
    public static LinearLayout banner(Context context, CharSequence title, CharSequence subtitle) {
        LinearLayout b = new LinearLayout(context);
        b.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{YumiTheme.LOGO_GRADIENT_START, YumiTheme.LOGO_GRADIENT_END});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        b.setBackground(bg);
        b.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        TextView h = text(context, title, YumiTheme.TEXT_TITLE - 1, true, 0xFFFFFFFF);
        b.addView(h);
        TextView p = text(context, subtitle, YumiTheme.TEXT_SMALL, false, 0xE8FFFFFF);
        b.addView(p, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        return b;
    }

    /** Ручка bottom sheet: 40x4, белый 20%. */
    public static View grabHandle(Context context) {
        View v = new View(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(99));
        bg.setColor(0x33FFFFFF);
        v.setBackground(bg);
        v.setLayoutParams(LayoutHelper.createLinear(40, 4, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));
        return v;
    }

    /** Фон bottom sheet: верхние углы 24, цвет диалога. */
    public static GradientDrawable sheetBackground(Context context) {
        int bg = Theme.getColor(Theme.key_dialogBackground);
        if (bg == 0) bg = 0xFF14151F;
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadii(new float[]{
                AndroidUtilities.dp(28), AndroidUtilities.dp(28),
                AndroidUtilities.dp(28), AndroidUtilities.dp(28), 0, 0, 0, 0});
        d.setColor(bg);
        return d;
    }

    /** Сквиркл-иконка: цветной фон 12dp + белая иконка по центру. */
    public static FrameLayout squircleIcon(Context context, int resId, int color, int boxDp, int iconDp) {
        FrameLayout frame = new FrameLayout(context);
        frame.setBackground(YumiTheme.squircleIconBackground(color));
        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(resId);
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(0xFFFFFFFF);
        frame.addView(icon, LayoutHelper.createFrame(iconDp, iconDp, Gravity.CENTER));
        frame.setLayoutParams(LayoutHelper.createLinear(boxDp, boxDp, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));
        return frame;
    }

    /** Поле поиска: тональная карточка, радиус 12, паддинги 14/11, текст 14. */
    public static EditText searchField(Context context, CharSequence hint) {
        EditText v = new EditText(context);
        v.setHint(hint);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, YumiTheme.TEXT_BODY);
        v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        v.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        v.setSingleLine(true);
        v.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_MD));
        v.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));
        return v;
    }
}
