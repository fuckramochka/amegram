package app.amegram.hot.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotModulesManager;
import app.amegram.hot.HotSettingsUiGate;
import app.amegram.theme.YumiMd3;
import app.amegram.theme.YumiSwitch;
import app.miogram.bridge.MiogramLocale;

/**
 * Шторка «Внутрішній світ Yumi» у стилі Material Design 3 Expressive (Pixel / Android 14/15).
 * Показує стан системного ядра, налаштування автооновлення та список компонентів із можливістю відкату.
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
        setBackgroundColor(YumiMd3.surface());
        initView(fragment.getParentActivity());
    }

    private void initView(Context context) {
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(YumiMd3.dp(20), YumiMd3.dp(10), YumiMd3.dp(20), YumiMd3.dp(28));

        // 1. MD3 Drag Handle
        View handle = new View(context);
        handle.setBackground(YumiMd3.rounded(YumiMd3.withAlpha(YumiMd3.outline(), 0.45f), 3));
        LinearLayout.LayoutParams handleLp = new LinearLayout.LayoutParams(YumiMd3.dp(36), YumiMd3.dp(4));
        handleLp.gravity = Gravity.CENTER_HORIZONTAL;
        handleLp.bottomMargin = YumiMd3.dp(16);
        root.addView(handle, handleLp);

        // 2. MD3 Header
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.14f), 16));
        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.yumi_ic_auto_awesome);
        icon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
        iconFrame.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        header.addView(iconFrame, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);

        TextView eyebrow = YumiMd3.text(context, "СИСТЕМНІ МОДУЛІ", 11f, YumiMd3.primary(), YumiMd3.BOLD);
        eyebrow.setLetterSpacing(0.08f);
        titles.addView(eyebrow);

        TextView title = YumiMd3.text(context,
                MiogramLocale.get("Системні модулі", "Системные модули", "System modules"),
                22f, YumiMd3.onSurface(), YumiMd3.BOLD);
        titles.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        header.addView(titles, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(header);

        TextView desc = YumiMd3.text(context,
                MiogramLocale.get(
                        "Базові системні компоненти, необхідні для стабільної роботи інтерфейсу та основних функцій застосунку.",
                        "Базовые системные компоненты, необходимые для стабильной работы интерфейса и основных функций приложения.",
                        "Essential system components required for the stability and core functions of the application."),
                13.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        desc.setLineSpacing(YumiMd3.dp(2), 1f);
        LinearLayout.LayoutParams descLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        descLp.topMargin = YumiMd3.dp(10);
        descLp.bottomMargin = YumiMd3.dp(18);
        root.addView(desc, descLp);

        // 3. Status & Settings Tonal Card (MD3 Surface Container Low)
        LinearLayout statusCard = new LinearLayout(context);
        statusCard.setOrientation(LinearLayout.VERTICAL);
        statusCard.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerLow(), 22));
        statusCard.setPadding(YumiMd3.dp(18), YumiMd3.dp(16), YumiMd3.dp(18), YumiMd3.dp(16));

        // Рядок живого статусу
        LinearLayout statusRow = new LinearLayout(context);
        statusRow.setOrientation(LinearLayout.HORIZONTAL);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(context);
        dot.setBackground(YumiMd3.rounded(YumiMd3.success(), 99));
        statusRow.addView(dot, LayoutHelper.createLinear(10, 10, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        TextView statusText = YumiMd3.text(context,
                MiogramLocale.get("Усі модулі активні", "Все модули активны", "All modules are active"),
                14.5f, YumiMd3.success(), YumiMd3.BOLD);
        statusRow.addView(statusText, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView engineChip = YumiMd3.text(context, HotSettingsUiGate.getEngineName(), 11.5f, YumiMd3.onSurfaceVariant(), YumiMd3.MEDIUM);
        engineChip.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 99));
        engineChip.setPadding(YumiMd3.dp(9), YumiMd3.dp(4), YumiMd3.dp(9), YumiMd3.dp(4));
        statusRow.addView(engineChip);

        statusCard.addView(statusRow);

        // Роздільник MD3 Hairline
        View div = new View(context);
        div.setBackgroundColor(YumiMd3.hairline());
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, YumiMd3.dp(1)));
        divLp.topMargin = YumiMd3.dp(14);
        divLp.bottomMargin = YumiMd3.dp(14);
        statusCard.addView(div, divLp);

        // Перемикач автооновлення системи (YumiSwitch)
        LinearLayout autoUpdateRow = new LinearLayout(context);
        autoUpdateRow.setOrientation(LinearLayout.HORIZONTAL);
        autoUpdateRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout autoTextCol = new LinearLayout(context);
        autoTextCol.setOrientation(LinearLayout.VERTICAL);

        TextView autoTitle = YumiMd3.text(context,
                MiogramLocale.get("Автооновлення системи", "Автообновление системы", "System auto-update"),
                15f, YumiMd3.onSurface(), YumiMd3.MEDIUM);
        autoTextCol.addView(autoTitle);

        TextView autoSub = YumiMd3.text(context,
                MiogramLocale.get("Автоматично завантажувати стабільні оновлення",
                        "Автоматически загружать стабильные обновления",
                        "Automatically download stable updates"),
                12.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        autoTextCol.addView(autoSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        autoUpdateRow.addView(autoTextCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 14, 0));

        YumiSwitch autoSwitch = new YumiSwitch(context);
        autoSwitch.setChecked(HotModulesManager.isSystemAutoUpdateEnabled(), false);
        autoSwitch.setOnCheckedChangeListener((view, isChecked) -> {
            HotModulesManager.setSystemAutoUpdateEnabled(isChecked);
        });
        autoUpdateRow.addView(autoSwitch, LayoutHelper.createLinear(52, 32, Gravity.CENTER_VERTICAL));

        statusCard.addView(autoUpdateRow);
        root.addView(statusCard);

        // 4. Секція встановлених системних модулів
        TextView secTitle = YumiMd3.text(context,
                MiogramLocale.get("ВСТАНОВЛЕНІ МОДУЛІ", "УСТАНОВЛЕННЫЕ МОДУЛИ", "INSTALLED MODULES"),
                12f, YumiMd3.primary(), YumiMd3.BOLD);
        secTitle.setLetterSpacing(0.08f);
        LinearLayout.LayoutParams secLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        secLp.topMargin = YumiMd3.dp(22);
        secLp.bottomMargin = YumiMd3.dp(8);
        secLp.setMarginStart(YumiMd3.dp(4));
        root.addView(secTitle, secLp);

        LinearLayout modulesCard = new LinearLayout(context);
        modulesCard.setOrientation(LinearLayout.VERTICAL);
        modulesCard.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerLow(), 22));
        modulesCard.setPadding(YumiMd3.dp(16), YumiMd3.dp(10), YumiMd3.dp(16), YumiMd3.dp(10));

        if (systemModules.isEmpty()) {
            TextView empty = YumiMd3.text(context,
                    MiogramLocale.get("Вбудовані базові компоненти APK", "Встроенные базовые компоненты APK", "Built-in base APK components"),
                    13.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            empty.setPadding(0, YumiMd3.dp(12), 0, YumiMd3.dp(12));
            modulesCard.addView(empty);
        } else {
            for (int i = 0; i < systemModules.size(); i++) {
                if (i > 0) {
                    View mDiv = new View(context);
                    mDiv.setBackgroundColor(YumiMd3.hairline());
                    LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, YumiMd3.dp(1)));
                    mLp.topMargin = YumiMd3.dp(10);
                    mLp.bottomMargin = YumiMd3.dp(10);
                    modulesCard.addView(mDiv, mLp);
                }
                modulesCard.addView(buildSystemModuleRow(context, systemModules.get(i)));
            }
        }
        root.addView(modulesCard);

        // 5. Головна кнопка дії (MD3 Gradient Action Button)
        TextView checkBtn = YumiMd3.text(context,
                MiogramLocale.get("Перевірити оновлення", "Проверить обновления", "Check for updates"),
                15f, 0xFFFFFFFF, YumiMd3.BOLD);
        checkBtn.setGravity(Gravity.CENTER);
        checkBtn.setBackground(YumiMd3.gradient(YumiMd3.actionGradientStart(), YumiMd3.actionGradientEnd(), 18));
        checkBtn.setPadding(YumiMd3.dp(18), YumiMd3.dp(15), YumiMd3.dp(18), YumiMd3.dp(15));
        LinearLayout.LayoutParams btnLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        btnLp.topMargin = YumiMd3.dp(24);
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
        row.setPadding(0, YumiMd3.dp(4), 0, YumiMd3.dp(4));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        // Іконка модуля
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.12f), 12));
        ImageView modIcon = new ImageView(context);
        modIcon.setImageResource("settings_ui".equals(info.manifest.id) ? R.drawable.yumi_ic_settings : R.drawable.yumi_ic_auto_awesome);
        modIcon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
        iconBox.addView(modIcon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
        top.addView(iconBox, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = YumiMd3.text(context, info.manifest.name != null ? info.manifest.name : info.manifest.id,
                15f, YumiMd3.onSurface(), YumiMd3.BOLD);
        nameRow.addView(name);

        TextView verBadge = YumiMd3.text(context, "v" + info.manifest.version, 11f, YumiMd3.onSurfaceVariant(), YumiMd3.MEDIUM);
        verBadge.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 99));
        verBadge.setPadding(YumiMd3.dp(7), YumiMd3.dp(2), YumiMd3.dp(7), YumiMd3.dp(2));
        nameRow.addView(verBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        col.addView(nameRow);

        String descStr = "settings_ui".equals(info.manifest.id)
                ? MiogramLocale.get("Керує інтерфейсом налаштувань, темами та картками",
                "Управляет интерфейсом настроек, темами и карточками",
                "Controls settings UI, themes and cards")
                : (info.manifest.name != null ? info.manifest.name : "");
        TextView desc = YumiMd3.text(context, descStr, 12.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
        col.addView(desc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        top.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView badge = YumiMd3.text(context, MiogramLocale.get("Системний", "Системный", "System"), 11.5f, YumiMd3.primary(), YumiMd3.BOLD);
        badge.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.12f), 99));
        badge.setPadding(YumiMd3.dp(8), YumiMd3.dp(4), YumiMd3.dp(8), YumiMd3.dp(4));
        top.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        ImageView chevron = new ImageView(context);
        chevron.setImageResource(R.drawable.yumi_ic_chevron_right);
        chevron.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        top.addView(chevron, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));

        top.setClickable(true);
        top.setFocusable(true);
        GradientDrawable topMask = YumiMd3.rounded(0xFFFFFFFF, 14);
        top.setBackground(new RippleDrawable(
                ColorStateList.valueOf(YumiMd3.defaultRippleColor()), null, topMask));
        top.setPadding(YumiMd3.dp(8), YumiMd3.dp(8), YumiMd3.dp(8), YumiMd3.dp(8));
        top.setOnClickListener(v -> {
            dismiss();
            if (fragment != null) {
                fragment.presentFragment(new HotModuleSettingsActivity(info.manifest.id));
            }
        });

        row.addView(top);

        // Перевірка можливості відкату версії
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
            TextView rollbackBtn = YumiMd3.text(context,
                    MiogramLocale.get("Відкатити на v" + rollbackVer, "Откатить на v" + rollbackVer, "Rollback to v" + rollbackVer),
                    12.5f, YumiMd3.primary(), YumiMd3.BOLD);
            rollbackBtn.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.08f), 99));
            rollbackBtn.setPadding(YumiMd3.dp(12), YumiMd3.dp(6), YumiMd3.dp(12), YumiMd3.dp(6));
            LinearLayout.LayoutParams rbLp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
            rbLp.topMargin = YumiMd3.dp(8);
            rbLp.setMarginStart(YumiMd3.dp(50));
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
            row.addView(rollbackBtn, rbLp);
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
