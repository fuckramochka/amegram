package app.miogram.bridge.wall;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

/**
 * Екран «Бета-модулі»: керує вбудованою бета-версією стіни
 * та показує статус .hmod-модуля (HotServices.WALL).
 */
public class WallBetaSettingsActivity extends BaseFragment {

    private Switch mainSwitch;
    private TextView statusView;
    private TextView sourceView;
    private LinearLayout installCard;

    @Override
    public View createView(Context context) {
        hasOwnBackground = true;

        actionBar.setTitle(MiogramLocale.get("Бета-модулі", "Бета-модули", "Beta modules"));
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setCastShadows(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        column.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // --- hero-карта ---
        LinearLayout hero = new LinearLayout(context);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackground(WallUi.brandPillSoft(22));
        hero.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        LinearLayout heroRow = new LinearLayout(context);
        heroRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView mark = new TextView(context);
        mark.setText("✦ стіна");
        mark.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 19);
        mark.setTypeface(AndroidUtilities.bold());
        mark.setTextColor(0xFFFFFFFF);
        heroRow.addView(mark, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView beta = new TextView(context);
        beta.setText("beta");
        beta.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10);
        beta.setTypeface(AndroidUtilities.bold());
        beta.setTextColor(0xFFFFFFFF);
        beta.setBackground(WallUi.pill(0x33FFFFFF, 0, 10));
        beta.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3), AndroidUtilities.dp(8), AndroidUtilities.dp(3));
        heroRow.addView(beta, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        hero.addView(heroRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        statusView = new TextView(context);
        WallUi.styleBody(statusView, 12.5f, 0xCCFFFFFF);
        hero.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        sourceView = new TextView(context);
        WallUi.styleBody(sourceView, 11.5f, 0x99FFFFFF);
        hero.addView(sourceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        content.addView(hero, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // --- перемикач ---
        LinearLayout toggle = new LinearLayout(context);
        toggle.setOrientation(LinearLayout.HORIZONTAL);
        toggle.setGravity(Gravity.CENTER_VERTICAL);
        toggle.setBackground(WallUi.pill(WallUi.dark() ? 0xFF18122E : 0xFFFFFFFF, 0, 18));
        toggle.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        TextView toggleLabel = new TextView(context);
        toggleLabel.setText(MiogramLocale.get("Увімкнути стіну", "Включить стену", "Enable wall"));
        toggleLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        toggleLabel.setTypeface(AndroidUtilities.bold());
        toggleLabel.setTextColor(WallUi.textPrimary());
        toggle.addView(toggleLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        mainSwitch = new Switch(context);
        mainSwitch.setChecked(WallPrefs.isBetaEnabled(), false);
        toggle.addView(mainSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 2, 0));
        toggle.setOnClickListener(v -> {
            boolean value = !mainSwitch.isChecked();
            mainSwitch.setChecked(value, true);
            WallPrefs.setBetaEnabled(value);
            WallUi.hapticToggle(v, value);
            refreshStatus();
            if (value) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                        MiogramLocale.get("Стіна ввімкнена ✦ тапни банер свого профілю",
                                "Стена включена ✦ тапни баннер своего профиля",
                                "Wall enabled ✦ tap your profile banner")).show();
            }
        });
        content.addView(toggle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // --- відкрити ---
        LinearLayout openRow = new LinearLayout(context);
        openRow.setOrientation(LinearLayout.HORIZONTAL);
        openRow.setGravity(Gravity.CENTER);
        openRow.setMinimumHeight(AndroidUtilities.dp(46));
        openRow.setBackground(WallUi.pill(WallUi.dark() ? 0xFF18122E : 0xFFFFFFFF, 0x14FFFFFF, 18));
        TextView openLabel = new TextView(context);
        openLabel.setText("🚪 " + MiogramLocale.get("Відкрити свою стіну", "Открыть свою стену", "Open your wall"));
        openLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        openLabel.setTextColor(WallUi.textPrimary());
        openLabel.setTypeface(AndroidUtilities.bold());
        openRow.addView(openLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        openRow.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            if (!WallModule.isAvailable()) {
                showBullettin();
                return;
            }
            WallModule.openOwnWall(this);
        });
        content.addView(openRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // --- встановити .hmod ---
        installCard = new LinearLayout(context);
        installCard.setOrientation(LinearLayout.HORIZONTAL);
        installCard.setGravity(Gravity.CENTER);
        installCard.setMinimumHeight(AndroidUtilities.dp(46));
        installCard.setBackground(WallUi.pill(WallUi.dark() ? 0xFF18122E : 0xFFFFFFFF, 0x14FFFFFF, 18));
        TextView installLabel = new TextView(context);
        installLabel.setText("🧩 " + MiogramLocale.get("Встановити .hmod-модуль", "Установить .hmod-модуль", "Install .hmod module"));
        installLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        installLabel.setTextColor(WallUi.CYAN);
        installLabel.setTypeface(AndroidUtilities.bold());
        installCard.addView(installLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        installCard.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            installHotModule();
        });
        content.addView(installCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // --- опис ---
        LinearLayout about = new LinearLayout(context);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setBackground(WallUi.pill(WallUi.dark() ? 0xFF14102A : 0xFFF2F2F8, 0, 18));
        about.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        TextView aboutTitle = new TextView(context);
        WallUi.styleTitle(aboutTitle, 13);
        aboutTitle.setText(MiogramLocale.get("Як це працює", "Как это работает", "How it works"));
        about.addView(aboutTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        TextView aboutText = new TextView(context);
        WallUi.styleBody(aboutText, 12.5f, WallUi.textSecondary());
        aboutText.setText(MiogramLocale.get(
                "• Тапни банер свого профілю — відкриється твоя стіна\n• Кожен кладе 1 елемент, ти модеруєш\n• Поки все локально, у наступних версіях — автосинк з VPS\n• .hmod — та сама стіна в упаковці модуля",
                "• Тапни по баннеру своего профиля — откроется твоя стена\n• Каждый кладет 1 элемент, ты модерируешь\n• Пока всё локально, в следующих версиях — автосинк с VPS\n• .hmod — та же стена в упаковке модуля",
                "• Tap your profile banner to open your wall\n• Everyone places 1 element, you moderate\n• Local now, VPS auto-sync later\n• .hmod — the same wall packaged as a module"));
        about.addView(aboutText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        content.addView(about, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        refreshStatus();
        return column;
    }

    private void showBullettin() {
        try {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    MiogramLocale.get("Спочатку увімкни стіну", "Сначала включи стену", "Enable the wall first")).show();
        } catch (Throwable ignore) {
        }
    }

    private void refreshStatus() {
        boolean enabled = WallPrefs.isBetaEnabled() || WallModule.isHotEnabled();
        if (statusView != null) {
        statusView.setText(enabled
                ? "◉ " + MiogramLocale.get("стіна активна", "стена активна", "wall active") + " · " + WallModule.sourceLabel()
                : "○ " + MiogramLocale.get("стіна вимкнена", "стена выключена", "wall disabled"));
        }
        if (sourceView != null) {
            sourceView.setText("v" + WallModule.VERSION);
        }
        if (installCard != null) {
            installCard.setVisibility(WallModule.isHotInstalled() ? View.GONE : View.VISIBLE);
        }
    }

    private void installHotModule() {
        if (getParentActivity() == null) return;
        app.amegram.hot.HotModulesManager.installBundledModule(WallModule.MODULE_ID, true, (ok, message, data) -> {
            AndroidUtilities.runOnUIThread(() -> {
                refreshStatus();
                if (ok) {
                    try {
                        BulletinFactory.of(WallBetaSettingsActivity.this).createSimpleBulletin(R.raw.info,
                                MiogramLocale.get("Модуль встановлено ✦", "Модуль установлен ✦", "Module installed ✦")).show();
                    } catch (Throwable ignore) {
                    }
                }
            });
        });
    }
}
