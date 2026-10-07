package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Деталка модуля (Market + Minimal):
 * іконка • назва • автор/розмір/довіра • опис • дозволи • чипи версій
 * (опис і версія змінюються при перемиканні) • велика кнопка Встановити знизу
 * • відкат • всі версії • поділитись • видалити.
 */
public class HotModuleDetailSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    private final String moduleId;
    private final HotCatalog.Entry entry;
    private final OnChanged onChanged;

    private HotCatalog.Build selected;
    private final List<HotCatalog.Build> allBuilds = new ArrayList<>();

    private TextView verLabel;
    private TextView trustLabel;
    private TextView descView;
    private TextView changelogBox;
    private LinearLayout chipsRow;
    private LinearLayout permsBox;
    private TextView warnBox;
    private TextView installBtn;
    private ProgressBar progress;
    private org.telegram.ui.Components.CheckBox2 enableCheck;

    public HotModuleDetailSheet(Context context, String moduleId,
                                HotCatalog.Entry entry, HotCatalog.Build preselect,
                                OnChanged onChanged) {
        super(context, false);
        this.moduleId = moduleId;
        this.entry = entry;
        this.onChanged = onChanged;

        if (entry != null) {
            allBuilds.addAll(entry.branches.values());
            allBuilds.addAll(entry.history);
        }
        if (preselect != null) {
            selected = preselect;
        } else if (entry != null && entry.defaultBuild() != null) {
            selected = entry.defaultBuild();
        } else if (!allBuilds.isEmpty()) {
            selected = allBuilds.get(0);
        }

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) bg = 0xFF14151F;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadii(new float[]{
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24), 0, 0, 0, 0});
        rootBg.setColor(bg);
        root.setBackground(rootBg);

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                AndroidUtilities.dp(20), AndroidUtilities.dp(8));
        scroll.addView(body, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        buildHeader(context, body);
        buildBody(context, body);
        buildFooter(context, root);
        refreshSelection();

        setCustomView(root);
    }

    private int themed(int key, int fallback) {
        int c = getThemedColor(key);
        return c == 0 ? fallback : c;
    }

    private void buildHeader(Context context, LinearLayout body) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);

        View grab = new View(context);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(AndroidUtilities.dp(99));
        g.setColor(0x33FFFFFF);
        grab.setBackground(g);
        body.addView(grab, LayoutHelper.createLinear(40, 4, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 14));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int modColor = HotModuleMeta.color(moduleId);
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(modColor));
        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(HotModuleMeta.icon(moduleId));
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(icon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
        row.addView(iconFrame, LayoutHelper.createLinear(56, 56, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(context);
        title.setText(entry != null && !TextUtils.isEmpty(entry.name) ? entry.name : moduleId);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        meta.addView(title);

        verLabel = new TextView(context);
        verLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        verLabel.setTextColor(sub);
        meta.addView(verLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        trustLabel = new TextView(context);
        trustLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        trustLabel.setTypeface(AndroidUtilities.bold());
        meta.addView(trustLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        row.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        body.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Статистика: статус • збірки • автор
        LinearLayout stats = new LinearLayout(context);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        boolean installed = HotModulesManager.isModuleInstalled(moduleId);
        stats.addView(statCell(context, installed ? "✓" : "○",
                MiogramLocale.get("Статус", "Статус", "Status"),
                MiogramLocale.get(installed ? "Встановлено" : "Не встановлено",
                        installed ? "Установлен" : "Не установлен",
                        installed ? "Installed" : "Not installed")), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        stats.addView(statCell(context, String.valueOf(Math.max(allBuilds.size(), 1)),
                MiogramLocale.get("Збірок", "Сборок", "Builds"),
                MiogramLocale.get("доступно", "доступно", "available")), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        String author = HotModuleMeta.author(moduleId, entry != null ? entry.author : "");
        stats.addView(statCell(context, author.length() > 12 ? author.substring(0, 12) + "…" : author,
                MiogramLocale.get("Автор", "Автор", "Author"),
                sizeText()), LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        body.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        if (HotModulesManager.isQuarantined(moduleId)) {
            TextView q = new TextView(context);
            q.setText("⛔ " + MiogramLocale.get(
                    "Модуль у карантині: падав 3+ рази. Перевстановіть, щоб спробувати знову.",
                    "Модуль в карантине: падал 3+ раза. Переустановите, чтобы попробовать снова.",
                    "Module quarantined: crashed 3+ times. Reinstall to retry."));
            q.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            q.setTextColor(themed(Theme.key_text_RedRegular, 0xFFFF5A5A));
            GradientDrawable qb = new GradientDrawable();
            qb.setCornerRadius(AndroidUtilities.dp(12));
            qb.setStroke(AndroidUtilities.dp(1), 0x66FF5A5A);
            q.setBackground(qb);
            q.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                    AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            body.addView(q, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        }
    }

    private String sizeText() {
        if (selected != null && selected.sizeBytes > 0) {
            return HotCatalog.formatSize(selected.sizeBytes);
        }
        return HotModuleMeta.categoryTitle(entry != null ? entry.category : HotModuleMeta.category(moduleId));
    }

    private View statCell(Context context, String big, String label, String hint) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        LinearLayout c = new LinearLayout(context);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        bg.setColor(themed(Theme.key_windowBackgroundGray, 0xFF1E1F22));
        c.setBackground(bg);
        c.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10));
        TextView b = new TextView(context);
        b.setText(big);
        b.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        b.setTypeface(AndroidUtilities.bold());
        b.setTextColor(text);
        b.setGravity(Gravity.CENTER);
        c.addView(b);
        TextView l = new TextView(context);
        l.setText(label + " • " + hint);
        l.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        l.setTextColor(sub);
        l.setGravity(Gravity.CENTER);
        c.addView(l);
        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(c, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 4, 0));
        return wrap;
    }

    private void buildBody(Context context, LinearLayout body) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);

        body.addView(sectionTitle(context, MiogramLocale.get("Про модуль", "О модуле", "About")));

        descView = new TextView(context);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        descView.setTextColor(text);
        descView.setLineSpacing(AndroidUtilities.dp(3), 1f);
        body.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        changelogBox = new TextView(context);
        changelogBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        changelogBox.setTextColor(sub);
        GradientDrawable chBg = new GradientDrawable();
        chBg.setCornerRadius(AndroidUtilities.dp(12));
        chBg.setColor(themed(Theme.key_windowBackgroundGray, 0xFF1E1F22));
        changelogBox.setBackground(chBg);
        changelogBox.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10),
                AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        body.addView(changelogBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        warnBox = new TextView(context);
        warnBox.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        warnBox.setTextColor(themed(Theme.key_text_RedRegular, 0xFFFF5A5A));
        warnBox.setVisibility(View.GONE);
        body.addView(warnBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        body.addView(sectionTitle(context, MiogramLocale.get("Доступи", "Доступы", "Permissions")));
        permsBox = new LinearLayout(context);
        permsBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(permsBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        body.addView(sectionTitle(context, MiogramLocale.get("Версія", "Версия", "Version")));

        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        body.addView(chipsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout checkRow = new LinearLayout(context);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER_VERTICAL);
        checkRow.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
        enableCheck = new org.telegram.ui.Components.CheckBox2(context, 21);
        enableCheck.setChecked(true, false);
        checkRow.addView(enableCheck, LayoutHelper.createLinear(21, 21, 0, 0, 8, 0));
        TextView checkText = new TextView(context);
        checkText.setText(MiogramLocale.get("Увімкнути після встановлення", "Включить после установки", "Enable after install"));
        checkText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        checkText.setTextColor(text);
        checkRow.addView(checkText);
        checkRow.setOnClickListener(v -> enableCheck.setChecked(!enableCheck.isChecked(), true));
        body.addView(checkRow);
    }

    private TextView sectionTitle(Context context, String s) {
        TextView v = new TextView(context);
        v.setText(s.toUpperCase());
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        v.setTypeface(AndroidUtilities.bold());
        v.setTextColor(themed(Theme.key_windowBackgroundWhiteBlueHeader, 0xFF6C63FF));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 4, 0, 6);
        v.setLayoutParams(lp);
        return v;
    }

    private void buildFooter(Context context, LinearLayout root) {
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);

        LinearLayout foot = new LinearLayout(context);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        foot.addView(progress, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        installBtn = new TextView(context);
        installBtn.setGravity(Gravity.CENTER);
        installBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        installBtn.setTypeface(AndroidUtilities.bold());
        installBtn.setTextColor(0xFFFFFFFF);
        installBtn.setBackground(YumiTheme.buttonRipple(accent, 14));
        installBtn.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
        installBtn.setOnClickListener(v -> confirmAndInstall());
        foot.addView(installBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        TextView versions = smallBtn(context, MiogramLocale.get("▤ Всі версії", "▤ Все версии", "▤ All versions"));
        versions.setOnClickListener(v -> {
            HotModuleVersionsSheet sheet = new HotModuleVersionsSheet(context, moduleId, entry, () -> {
                if (onChanged != null) onChanged.onChanged();
                refreshSelection();
            });
            sheet.show();
        });
        row.addView(versions, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 4, 0));

        TextView share = smallBtn(context, MiogramLocale.get("⤴ Поділитись", "⤴ Поделиться", "⤴ Share"));
        share.setOnClickListener(v -> HotModuleDeepLink.share(context, moduleId));
        row.addView(share, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 0, 0));
        foot.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        LinearLayout row2 = new LinearLayout(context);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        String prevVer = previousInstalledVersion();
        if (prevVer != null) {
            TextView rollback = smallBtn(context, MiogramLocale.get("↩ Відкотити до v", "↩ Откатить до v", "↩ Roll back to v") + prevVer);
            rollback.setOnClickListener(v -> HotModulesManager.activateVersion(moduleId, prevVer, (ok, msg, data) -> {
                if (onChanged != null) onChanged.onChanged();
                refreshSelection();
            }));
            row2.addView(rollback, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 4, 0));
        }
        if (HotModulesManager.isModuleInstalled(moduleId)) {
            TextView del = smallBtn(context, MiogramLocale.get("Видалити", "Удалить", "Delete"));
            del.setTextColor(themed(Theme.key_text_RedRegular, 0xFFFF5A5A));
            del.setOnClickListener(v -> {
                HotModulesManager.deleteModule(moduleId);
                if (onChanged != null) onChanged.onChanged();
                dismiss();
            });
            row2.addView(del, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 0, 0));
        }
        if (row2.getChildCount() > 0) {
            foot.addView(row2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        root.addView(foot);
    }

    private String previousInstalledVersion() {
        String active = "";
        String prev = null;
        for (HotModulesManager.InstalledInfo i : HotModulesManager.listInstalled()) {
            if (!i.manifest.id.equals(moduleId)) continue;
            if (i.active) active = i.manifest.version;
        }
        for (HotModulesManager.InstalledInfo i : HotModulesManager.listInstalled()) {
            if (!i.manifest.id.equals(moduleId) || i.active) continue;
            if (prev == null || i.manifest.version.compareTo(prev) > 0) prev = i.manifest.version;
        }
        return (prev != null && !prev.equals(active)) ? prev : null;
    }

    private TextView smallBtn(Context context, String s) {
        TextView v = new TextView(context);
        v.setText(s);
        v.setGravity(Gravity.CENTER);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        v.setTextColor(themed(Theme.key_dialogTextGray3, 0xFF8E8E93));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        bg.setColor(0x00000000);
        v.setBackground(bg);
        v.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
        return v;
    }

    private List<String> currentPermissions() {
        if (selected != null && selected.permissions != null && !selected.permissions.isEmpty()) {
            return selected.permissions;
        }
        return HotModuleMeta.fallbackPermissions(moduleId);
    }

    private void refreshSelection() {
        if (selected != null) {
            verLabel.setText(selected.branch + " • v" + selected.version);
        } else {
            verLabel.setText("—");
        }
        String sig = HotModulesManager.signatureStatus(selected);
        if ("SIGNED".equals(sig)) {
            trustLabel.setText("🔏 " + MiogramLocale.get("Підписано • SHA-256 ✓", "Подписано • SHA-256 ✓", "Signed • SHA-256 ✓"));
            trustLabel.setTextColor(0xFF4CAF50);
        } else if ("SHA256".equals(sig)) {
            trustLabel.setText("🔒 SHA-256 " + MiogramLocale.get("перевіряється при завантаженні", "проверяется при загрузке", "verified on download"));
            trustLabel.setTextColor(themed(Theme.key_dialogTextGray3, 0xFF8E8E93));
        } else {
            trustLabel.setText("⚠ " + MiogramLocale.get("Без контрольної суми", "Без контрольной суммы", "No checksum"));
            trustLabel.setTextColor(themed(Theme.key_text_RedRegular, 0xFFFF5A5A));
        }
        String base = entry != null && !TextUtils.isEmpty(entry.description)
                ? entry.description : HotModuleMeta.fallbackDescription(moduleId);
        descView.setText(base);
        if (selected != null && !TextUtils.isEmpty(selected.changelog)) {
            changelogBox.setVisibility(View.VISIBLE);
            changelogBox.setText("✨ v" + selected.version + " — " + selected.changelog);
        } else if (selected != null) {
            changelogBox.setVisibility(View.VISIBLE);
            changelogBox.setText(MiogramLocale.get("Версія v", "Версия v", "Version v") + selected.version);
        } else {
            changelogBox.setVisibility(View.GONE);
        }
        if (selected != null && !HotModulesManager.isCompatible(selected)) {
            warnBox.setVisibility(View.VISIBLE);
            warnBox.setText("⚠ " + MiogramLocale.get(
                    "Потрібен новіший AmeGram (min " + selected.minApp + "). Оновіть клієнт.",
                    "Нужен новее AmeGram (min " + selected.minApp + "). Обновите клиент.",
                    "Requires newer AmeGram (min " + selected.minApp + "). Update the client."));
        } else {
            warnBox.setVisibility(View.GONE);
        }
        rebuildPerms();
        rebuildChips();
        refreshInstallLabel();
    }

    private void rebuildPerms() {
        Context context = getContext();
        if (context == null || permsBox == null) return;
        permsBox.removeAllViews();
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        List<String> perms = currentPermissions();
        if (perms.isEmpty()) {
            TextView t = new TextView(context);
            t.setText(MiogramLocale.get("Без особливих доступів", "Без особых доступов", "No special access"));
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            t.setTextColor(sub);
            permsBox.addView(t);
            return;
        }
        for (String p : perms) {
            TextView t = new TextView(context);
            String hint = HotModuleMeta.permissionHint(p);
            t.setText("• " + HotModuleMeta.permissionTitle(p) + (hint.isEmpty() ? "" : " — " + hint));
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            t.setTextColor(sub);
            permsBox.addView(t);
        }
    }

    private void rebuildChips() {
        Context context = getContext();
        if (context == null || chipsRow == null) return;
        chipsRow.removeAllViews();
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        if (allBuilds.isEmpty()) {
            TextView t = new TextView(context);
            t.setText("—");
            t.setTextColor(sub);
            chipsRow.addView(t);
            return;
        }
        int shown = 0;
        for (HotCatalog.Build b : allBuilds) {
            if (shown >= 6) break;
            shown++;
            TextView chip = new TextView(context);
            boolean compat = HotModulesManager.isCompatible(b);
            chip.setText(b.branch + " · " + b.version + (compat ? "" : " ⚠"));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            chip.setTypeface(AndroidUtilities.bold());
            boolean on = selected != null && b.version.equals(selected.version) && b.branch.equals(selected.branch);
            chip.setTextColor(on ? 0xFFFFFFFF : sub);
            chip.setAlpha(compat ? 1f : 0.55f);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(99));
            if (on) {
                bg.setColor(accent);
            } else {
                bg.setStroke(AndroidUtilities.dp(1), 0x44FFFFFF);
                bg.setColor(0x00000000);
            }
            chip.setBackground(bg);
            chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            final HotCatalog.Build bb = b;
            chip.setOnClickListener(v -> {
                selected = bb;
                refreshSelection();
            });
            chipsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
    }

    private void refreshInstallLabel() {
        if (installBtn == null) return;
        boolean installed = HotModulesManager.isModuleInstalled(moduleId);
        String activeVer = "";
        try {
            for (HotModulesManager.InstalledInfo i : HotModulesManager.listInstalled()) {
                if (i.manifest.id.equals(moduleId) && i.active) {
                    activeVer = i.manifest.version;
                    break;
                }
            }
        } catch (Throwable ignore) {
        }
        boolean compat = selected == null || HotModulesManager.isCompatible(selected);
        installBtn.setAlpha(compat ? 1f : 0.5f);
        if (!installed) {
            installBtn.setText(MiogramLocale.get("Встановити", "Установить", "Install")
                    + (selected != null ? " • v" + selected.version : ""));
        } else if (selected != null && !selected.version.equals(activeVer)) {
            installBtn.setText(MiogramLocale.get("Оновити до v", "Обновить до v", "Update to v") + selected.version);
        } else {
            installBtn.setText("✓ " + MiogramLocale.get("Встановлено", "Установлен", "Installed")
                    + (activeVer.isEmpty() ? "" : " • v" + activeVer));
        }
    }

    /** Екран згоди: які доступи просить модуль — підтвердити перед скачуванням. */
    private void confirmAndInstall() {
        if (selected == null) return;
        if (!HotModulesManager.isCompatible(selected)) {
            try {
                android.widget.Toast.makeText(getContext(), "minApp " + selected.minApp,
                        android.widget.Toast.LENGTH_SHORT).show();
            } catch (Throwable ignore) {
            }
            return;
        }
        List<String> perms = currentPermissions();
        if (perms.isEmpty() || HotModulesManager.isModuleInstalled(moduleId)) {
            doInstall();
            return;
        }
        Context context = getContext();
        StringBuilder sb = new StringBuilder();
        for (String p : perms) {
            sb.append("• ").append(HotModuleMeta.permissionTitle(p)).append("\n");
        }
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(MiogramLocale.get("Доступи модуля", "Доступы модуля", "Module access"));
        b.setMessage(sb.toString().trim());
        b.setPositiveButton(MiogramLocale.get("Встановити", "Установить", "Install"),
                (d, w) -> doInstall());
        b.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        b.show();
    }

    private void doInstall() {
        if (selected == null) return;
        Context context = getContext();
        installBtn.setEnabled(false);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        boolean autoEnable = enableCheck != null && enableCheck.isChecked();
        HotModulesManager.downloadBuild(moduleId, selected, autoEnable,
                new HotModulesManager.ProgressCallback<Void>() {
                    @Override
                    public void onProgress(long downloaded, long total) {
                        if (total > 0) {
                            progress.setMax(100);
                            progress.setProgress((int) (downloaded * 100 / total));
                        }
                    }

                    @Override
                    public void onDone(boolean ok, String message, Void data) {
                        installBtn.setEnabled(true);
                        progress.setVisibility(View.GONE);
                        if (ok) {
                            refreshInstallLabel();
                            if (onChanged != null) onChanged.onChanged();
                        } else {
                            installBtn.setEnabled(true);
                            try {
                                android.widget.Toast.makeText(context,
                                        message != null ? message : "error",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Throwable ignore) {
                            }
                        }
                    }
                });
    }
}
