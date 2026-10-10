package app.amegram.module.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.amegram.theme.YumiTheme;
import app.amegram.module.AmegramConfig;
import app.miogram.bridge.MiogramLocale;

/**
 * Сучасний MD3-гід по ключових можливостях Yumigram.
 * Красивий онбординг без грубих чекбоксів і з повною підтримкою теми.
 */
public class AmegramWelcomeSheet extends BottomSheet {

    private static final String KEY_SHOWN = "guide_shown";

    public AmegramWelcomeSheet(Context context) {
        super(context, false);

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) bg = 0xFF1E1E2E;
        int textPrimary = getThemedColor(Theme.key_dialogTextBlack);
        if (textPrimary == 0) textPrimary = 0xFFFFFFFF;
        int textSecondary = getThemedColor(Theme.key_dialogTextGray2);
        if (textSecondary == 0) textSecondary = 0xAAFFFFFF;
        int accent = YumiTheme.getPrimary();
        if (accent == 0) accent = 0xFF7C4DFF;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        // Drag handle indicator
        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x33FFFFFF);
        handleBg.setCornerRadius(AndroidUtilities.dp(3));
        handle.setBackground(handleBg);
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(36), AndroidUtilities.dp(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = AndroidUtilities.dp(16);
        root.addView(handle, handleParams);

        // Header Title
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get(
                "Ласкаво просимо до Yumigram",
                "Добро пожаловать в Yumigram",
                "Welcome to Yumigram"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textPrimary);
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        // Header Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText(MiogramLocale.get(
                "Швидкий гід по можливостях та налаштуваннях вашого клієнта",
                "Быстрый гид по возможностям и настройкам вашего клиента",
                "Quick overview of your client's features and settings"));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subtitle.setTextColor(textSecondary);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        ScrollView scroll = new ScrollView(context);
        LinearLayout cards = new LinearLayout(context);
        cards.setOrientation(LinearLayout.VERTICAL);

        cards.addView(makeFeatureCard(context,
                R.drawable.msg_plugins, 0xFF8B5CF6,
                MiogramLocale.get("Модульна система (.hmod)", "Модульная система (.hmod)", "Modular system (.hmod)"),
                MiogramLocale.get(
                        "Завантажуйте додатковий функціонал наживо з магазину розширень без оновлення самого APK.",
                        "Загружайте дополнительный функционал на лету из магазина расширений без обновления самого APK.",
                        "Download extra features live from the extension catalog without updating the APK."),
                textPrimary, textSecondary));

        cards.addView(makeFeatureCard(context,
                R.drawable.msg_secret, 0xFF9C27B0,
                MiogramLocale.get("Приватність та безпека", "Приватность и безопасность", "Privacy & security"),
                MiogramLocale.get(
                        "Режим невидимки, приховування статусу онлайну, тайпінгу та подвійне дно з окремим кодом.",
                        "Режим невидимки, скрытие онлайна, тайпинга и двойное дно с отдельным кодом.",
                        "Ghost mode, hidden online, typing state and duress double bottom PIN."),
                textPrimary, textSecondary));

        cards.addView(makeFeatureCard(context,
                R.drawable.msg_language, 0xFF2A87FF,
                MiogramLocale.get("Обхід блокувань (Антиблок)", "Обход блокировок (Антиблок)", "Anti-block bypass"),
                MiogramLocale.get(
                        "Вбудований рушій проксі з автоматичним підбором швидких серверів для захисту зв'язку.",
                        "Встроенный движок прокси с автоматическим подбором быстрых серверов для защиты связи.",
                        "Built-in proxy engine with smart server selection for uninterrupted connection."),
                textPrimary, textSecondary));

        cards.addView(makeFeatureCard(context,
                R.drawable.msg_premium_normal, 0xFFE5486B,
                MiogramLocale.get("Бейджики та персоналізація", "Бейджики и персонализация", "Badges & personalization"),
                MiogramLocale.get(
                        "Унікальні піксельні бейджі спільноти Yumigram, кастомізація плеєра та сучасний MD3-інтерфейс.",
                        "Уникальные пиксельные бейджи сообщества Yumigram, кастомизация плеера и современный MD3-интерфейс.",
                        "Unique pixel community badges, player customization and modern MD3 interface."),
                textPrimary, textSecondary));

        scroll.addView(cards);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // Start button
        TextView start = new TextView(context);
        start.setText(MiogramLocale.get("Зрозуміло, розпочати", "Понятно, начать", "Got it, let's start"));
        start.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        start.setTypeface(AndroidUtilities.bold());
        start.setTextColor(Color.WHITE);
        start.setGravity(Gravity.CENTER);

        GradientDrawable bgBtn = new GradientDrawable();
        bgBtn.setColor(accent);
        bgBtn.setCornerRadius(AndroidUtilities.dp(14));
        start.setBackground(bgBtn);
        start.setPadding(0, AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14));
        start.setOnClickListener(v -> {
            markShown();
            dismiss();
        });
        root.addView(start, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        setCustomView(root);
    }

    private static View makeFeatureCard(Context ctx, int iconRes, int iconColor,
                                       String title, String desc,
                                       int textPrimary, int textSecondary) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x0CFFFFFF);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setStroke(AndroidUtilities.dp(1), 0x15FFFFFF);
        card.setBackground(bg);

        // Icon container
        FrameLayout iconFrame = new FrameLayout(ctx);
        GradientDrawable iconBg = new GradientDrawable();
        iconBg.setColor((iconColor & 0x00FFFFFF) | 0x26000000);
        iconBg.setCornerRadius(AndroidUtilities.dp(10));
        iconFrame.setBackground(iconBg);

        ImageView iv = new ImageView(ctx);
        iv.setImageResource(iconRes);
        iv.setColorFilter(iconColor);
        iconFrame.addView(iv, LayoutHelper.createFrame(24, 24, Gravity.CENTER));

        card.addView(iconFrame, LayoutHelper.createLinear(40, 40, 0, 0, 12, 0));

        // Texts
        LinearLayout textCol = new LinearLayout(ctx);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tvTitle.setTypeface(AndroidUtilities.bold());
        tvTitle.setTextColor(textPrimary);
        textCol.addView(tvTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        TextView tvDesc = new TextView(ctx);
        tvDesc.setText(desc);
        tvDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tvDesc.setTextColor(textSecondary);
        tvDesc.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        textCol.addView(tvDesc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        card.addView(textCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = AndroidUtilities.dp(8);
        card.setLayoutParams(lp);

        return card;
    }

    @Override
    public void dismiss() {
        markShown();
        super.dismiss();
    }

    public static boolean wasShown() {
        try {
            return AmegramConfig.getBool(KEY_SHOWN, false);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void markShown() {
        try {
            AmegramConfig.setBool(KEY_SHOWN, true);
        } catch (Throwable ignore) {
        }
    }
}
