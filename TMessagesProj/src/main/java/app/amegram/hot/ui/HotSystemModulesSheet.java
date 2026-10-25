package app.amegram.hot.ui;

import android.content.Context;
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
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.HotSettingsUiGate;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Шторка «Внутрішній світ Yumi» (Системні модулі ядра).
 * Дозволяє переглядати статус критичних системних компонентів,
 * налаштовувати автооновлення та виконувати безпечний відкат версій.
 */
public class HotSystemModulesSheet extends BottomSheet {

    private final BaseFragment fragment;
    private final List<HotModulesManager.InstalledInfo> systemModules;
    private final Runnable onDismissListener;

    public static void show(BaseFragment fragment, List<HotModulesManager.InstalledInfo> modules, Runnable onDismiss) {
        if (fragment == null || fragment.getParentActivity() == null) return;
        new HotSystemModulesSheet(fragment, modules, onDismiss).show();
    }

    public HotSystemModulesSheet(BaseFragment fragment, List<HotModulesManager.InstalledInfo> modules, Runnable onDismiss) {
        super(fragment.getParentActivity(), false);
        this.fragment = fragment;
        this.systemModules = modules != null ? modules : new ArrayList<>();
        this.onDismissListener = onDismiss;
        initView(fragment.getParentActivity());
    }

    private void initView(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16),
                AndroidUtilities.dp(20), AndroidUtilities.dp(28));

        // Заголовок
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.yumi_ic_auto_awesome);
        icon.setColorFilter(YumiTheme.primaryColor());
        header.addView(icon, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(AndroidUtilities.dp(12), 0, 0, 0);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Внутрішній світ Yumi", "Внутренний мир Yumi", "Yumi Internal Core"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        titles.addView(title);

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get("Системні компоненти ядра • Завжди активні",
                "Системные компоненты ядра • Всегда активны",
                "Core system components • Always active"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        titles.addView(sub);

        header.addView(titles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(header);

        // Карточка статусу ядра
        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable scBg = new GradientDrawable();
        scBg.setCornerRadius(AndroidUtilities.dp(16));
        scBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        statusCard.setBackground(scBg);
        statusCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        LinearLayout.LayoutParams scLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        scLp.topMargin = AndroidUtilities.dp(16);
        root.addView(statusCard, scLp);

        TextView engineStatus = new TextView(context);
        engineStatus.setText(MiogramLocale.get("Рушій інтерфейсу: ", "Движок интерфейса: ", "UI Engine: ")
                + HotSettingsUiGate.getEngineName());
        engineStatus.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        engineStatus.setTypeface(AndroidUtilities.bold());
        engineStatus.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        statusCard.addView(engineStatus);

        TextView engineDesc = new TextView(context);
        engineDesc.setText(MiogramLocale.get(
                "Системні модулі неможливо вимкнути випадково. У разі помилки система автоматично виконає безпечний відкат на попередню версію.",
                "Системные модули невозможно отключить случайно. В случае ошибки система автоматически откатится на предыдущую версию.",
                "System modules cannot be disabled. On failure, safe rollback to previous version triggers automatically."));
        engineDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        engineDesc.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        engineDesc.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(12));
        statusCard.addView(engineDesc);

        // Перемикач автооновлення
        LinearLayout autoUpdateRow = new LinearLayout(context);
        autoUpdateRow.setOrientation(LinearLayout.HORIZONTAL);
        autoUpdateRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView autoUpdateText = new TextView(context);
        autoUpdateText.setText(MiogramLocale.get("Автооновлення системи", "Автообновление системы", "System auto-update"));
        autoUpdateText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        autoUpdateText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        autoUpdateRow.addView(autoUpdateText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        Switch autoSwitch = new Switch(context);
        autoSwitch.setChecked(HotModulesManager.isSystemAutoUpdateEnabled(), false);
        autoSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            HotModulesManager.setSystemAutoUpdateEnabled(isChecked);
        });
        autoUpdateRow.addView(autoSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        statusCard.addView(autoUpdateRow);

        // Секція встановлених системних модулів
        TextView secTitle = new TextView(context);
        secTitle.setText(MiogramLocale.get("Компоненти ядра", "Компоненты ядра", "Core components"));
        secTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        secTitle.setTypeface(AndroidUtilities.bold());
        secTitle.setTextColor(YumiTheme.primaryColor());
        secTitle.setPadding(0, AndroidUtilities.dp(20), 0, AndroidUtilities.dp(8));
        root.addView(secTitle);

        if (systemModules.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText(MiogramLocale.get("Вбудовані базові компоненти APK", "Встроенные базовые компоненты APK", "Built-in APK base components"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
            root.addView(empty);
        } else {
            for (HotModulesManager.InstalledInfo info : systemModules) {
                root.addView(buildSystemModuleRow(context, info));
            }
        }

        // Кнопка перевірки оновлень ядра
        TextView checkBtn = new TextView(context);
        checkBtn.setText(MiogramLocale.get("Перевірити оновлення ядра", "Проверить обновления ядра", "Check core updates"));
        checkBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        checkBtn.setTypeface(AndroidUtilities.bold());
        checkBtn.setTextColor(0xFFFFFFFF);
        checkBtn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(AndroidUtilities.dp(14));
        btnBg.setColor(YumiTheme.primaryColor());
        checkBtn.setBackground(btnBg);
        checkBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13),
                AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        LinearLayout.LayoutParams btnLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        btnLp.topMargin = AndroidUtilities.dp(20);
        checkBtn.setOnClickListener(v -> {
            checkBtn.setEnabled(false);
            checkBtn.setAlpha(0.6f);
            HotModulesManager.checkUpdatesAsync((ok, msg, data) -> {
                AndroidUtilities.runOnUIThread(() -> {
                    checkBtn.setEnabled(true);
                    checkBtn.setAlpha(1.0f);
                    if (fragment != null) {
                        BulletinFactory.of(fragment).createSimpleBulletin(
                                R.drawable.baseline_system_update_24,
                                MiogramLocale.get("Система актуальна", "Система актуальна", "System is up to date")
                        ).show();
                    }
                });
            });
        });
        root.addView(checkBtn, btnLp);

        scroll.addView(root);
        setCustomView(scroll);
    }

    private View buildSystemModuleRow(Context context, HotModulesManager.InstalledInfo info) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rBg = new GradientDrawable();
        rBg.setCornerRadius(AndroidUtilities.dp(14));
        rBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        rBg.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        row.setBackground(rBg);
        row.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(8);
        row.setLayoutParams(lp);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(context);
        name.setText(info.manifest.name != null ? info.manifest.name : info.manifest.id);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        top.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView badge = new TextView(context);
        badge.setText(MiogramLocale.get("🔒 Системний", "🔒 Системный", "🔒 System"));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        badge.setTypeface(AndroidUtilities.bold());
        badge.setTextColor(YumiTheme.primaryColor());
        GradientDrawable bBg = new GradientDrawable();
        bBg.setCornerRadius(AndroidUtilities.dp(6));
        bBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray));
        badge.setBackground(bBg);
        badge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2),
                AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        top.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        row.addView(top);

        TextView ver = new TextView(context);
        ver.setText("v" + info.manifest.version + " • " + info.manifest.branch);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        ver.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        ver.setPadding(0, AndroidUtilities.dp(2), 0, 0);
        row.addView(ver);

        // Кнопка відкату (якщо доступні інші версії)
        List<HotModulesManager.InstalledInfo> all = HotModulesManager.listInstalled();
        HotModulesManager.InstalledInfo prev = null;
        for (HotModulesManager.InstalledInfo other : all) {
            if (other.manifest.id.equals(info.manifest.id) && !other.manifest.version.equals(info.manifest.version)) {
                prev = other;
                break;
            }
        }
        if (prev != null) {
            final String rollbackVer = prev.manifest.version;
            TextView rollbackBtn = new TextView(context);
            rollbackBtn.setText(MiogramLocale.get("Відкатити на v" + rollbackVer, "Откатить на v" + rollbackVer, "Rollback to v" + rollbackVer));
            rollbackBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            rollbackBtn.setTypeface(AndroidUtilities.bold());
            rollbackBtn.setTextColor(YumiTheme.primaryColor());
            rollbackBtn.setPadding(0, AndroidUtilities.dp(6), 0, 0);
            rollbackBtn.setOnClickListener(v -> {
                HotModulesManager.activateVersion(info.manifest.id, rollbackVer, (ok, m, d) -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        dismiss();
                        if (fragment != null) {
                            BulletinFactory.of(fragment).createSimpleBulletin(
                                    R.drawable.baseline_system_update_24,
                                    MiogramLocale.get("Відкочено на v" + rollbackVer, "Откачено на v" + rollbackVer, "Rolled back to v" + rollbackVer)
                            ).show();
                        }
                    });
                });
            });
            row.addView(rollbackBtn);
        }

        return row;
    }

    @Override
    public void dismiss() {
        super.dismiss();
        if (onDismissListener != null) {
            onDismissListener.run();
        }
    }
}
