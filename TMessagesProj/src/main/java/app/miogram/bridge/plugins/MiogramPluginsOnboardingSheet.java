package app.miogram.bridge.plugins;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Clean opt-in Onboarding BottomSheet shown once after install or app update.
 * Informs user about available plugins and asks explicitly which ones to install.
 */
public class MiogramPluginsOnboardingSheet extends BottomSheet {

    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final List<MiogramPluginsMarket.MarketPluginEntry> entries;

    public MiogramPluginsOnboardingSheet(Context context) {
        super(context, false);

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF14151F;
        fixNavigationBar(bgColor);

        entries = MiogramPluginsMarket.getCatalog();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        // Drag Bar
        View dragHandle = new View(context);
        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setColor(0x33FFFFFF);
        handleDrawable.setCornerRadius(AndroidUtilities.dp(3));
        dragHandle.setBackground(handleDrawable);
        root.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Header Title
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Плагіни Amegram ໒꒱", "Плагины Amegram ໒꒱", "Amegram Plugins ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText(MiogramLocale.get(
                "Ми не завантажуємо плагіни без вашого дозволу. Оберіть, які розширення ви хочете встановити прямо зараз:",
                "Мы не скачиваем плагины без вашего согласия. Выберите, какие расширения вы хотите установить прямо сейчас:",
                "We never install plugins without your consent. Choose which extensions you would like to enable now:"
        ));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitle.setLineSpacing(AndroidUtilities.dp(2), 1.15f);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Scrollable list of plugins
        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        LinearLayout pluginsList = new LinearLayout(context);
        pluginsList.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < entries.size(); i++) {
            MiogramPluginsMarket.MarketPluginEntry entry = entries.get(i);
            boolean already = MiogramPluginsMarket.isInstalled(entry);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.06f));
            cardBg.setCornerRadius(AndroidUtilities.dp(14));
            card.setBackground(cardBg);

            // Icon
            ImageView iconView = new ImageView(context);
            iconView.setImageResource(entry.iconRes);
            int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
            if (accent == 0) accent = 0xFF6C63FF;
            iconView.setColorFilter(accent);
            card.addView(iconView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

            // Texts
            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);

            LinearLayout nameRow = new LinearLayout(context);
            nameRow.setOrientation(LinearLayout.HORIZONTAL);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView nameText = new TextView(context);
            nameText.setText(entry.title);
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            nameText.setTypeface(AndroidUtilities.bold());
            nameText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            nameRow.addView(nameText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView badge = new TextView(context);
            badge.setText(" " + entry.category + " ");
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            badge.setTextColor(accent);
            nameRow.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

            textCol.addView(nameRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 3));

            TextView descText = new TextView(context);
            descText.setText(entry.getDescription());
            descText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            descText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            textCol.addView(descText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            card.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

            // Checkbox
            CheckBox cb = new CheckBox(context);
            cb.setChecked(already);
            card.addView(cb, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
            checkBoxes.add(cb);

            card.setOnClickListener(v -> {
                MiogramHaptic.select(v);
                cb.setChecked(!cb.isChecked());
            });

            pluginsList.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }

        scrollView.addView(pluginsList, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 240, 0, 0, 0, 14));

        // Primary Action: "Встановити вибрані"
        TextView installBtn = new TextView(context);
        installBtn.setText(MiogramLocale.get("Встановити вибрані", "Установить выбранные", "Install Selected"));
        installBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        installBtn.setTypeface(AndroidUtilities.bold());
        installBtn.setTextColor(Color.WHITE);
        installBtn.setGravity(Gravity.CENTER);

        GradientDrawable instBg = new GradientDrawable();
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        if (accent == 0) accent = 0xFF6C63FF;
        instBg.setColor(accent);
        instBg.setCornerRadius(AndroidUtilities.dp(12));
        installBtn.setBackground(instBg);
        installBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        installBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            int installedCount = 0;
            for (int i = 0; i < entries.size(); i++) {
                MiogramPluginsMarket.MarketPluginEntry entry = entries.get(i);
                CheckBox cb = checkBoxes.get(i);
                if (cb.isChecked()) {
                    MiogramPluginsMarket.installPlugin(context, entry);
                    installedCount++;
                } else {
                    if (MiogramPluginsMarket.isInstalled(entry)) {
                        MiogramPluginsMarket.uninstallPlugin(entry);
                    }
                }
            }
            MiogramPluginsMarket.markOnboardingDone(context);
            Toast.makeText(context, MiogramLocale.get(
                    "Налаштування плагінів збережено! (" + installedCount + " акт.)",
                    "Настройки плагинов сохранены! (" + installedCount + " акт.)",
                    "Plugin settings saved! (" + installedCount + " act.)"
            ), Toast.LENGTH_SHORT).show();
            dismiss();
        });
        root.addView(installBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Secondary Action: "Пропустити (Завантажу пізніше з маркету)"
        TextView skipBtn = new TextView(context);
        skipBtn.setText(MiogramLocale.get("Пропустити (Завантажу пізніше з маркету)", "Пропустить (Загружу позже из маркета)", "Skip (I'll install later from market)"));
        skipBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        skipBtn.setTypeface(AndroidUtilities.bold());
        skipBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        skipBtn.setGravity(Gravity.CENTER);
        skipBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        skipBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            MiogramPluginsMarket.markOnboardingDone(context);
            dismiss();
        });
        root.addView(skipBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        setCustomView(root);
    }
}
