package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.amegram.theme.YumiComponents;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Вибір версії модуля: встановлені (активувати/видалити)
 * + доступні з каталогу (гілки + історія з чейнджлогом).
 * Перемикання версії одразу міняє активну збірку й оновлює опис у деталці.
 */
public class HotModuleVersionsSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    private final String moduleId;
    private HotCatalog.Entry catalogEntry;
    private final OnChanged onChanged;
    private final LinearLayout itemsContainer;

    public HotModuleVersionsSheet(Context context, String moduleId,
                                  HotCatalog.Entry catalogEntry, OnChanged onChanged) {
        super(context, false);
        this.moduleId = moduleId;
        this.catalogEntry = catalogEntry;
        this.onChanged = onChanged;

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) bg = 0xFF14151F;
        int text = getThemedColor(Theme.key_dialogTextBlack);
        if (text == 0) text = 0xFFFFFFFF;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(YumiComponents.sheetBackground(context));
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        root.addView(YumiComponents.grabHandle(context));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Версії: ", "Версии: ", "Versions: ") + moduleId);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView hint = new TextView(context);
        hint.setText(MiogramLocale.get(
                "Торкніть «Встановити» на потрібній гілці — опис і версія в деталці оновляться.",
                "Нажмите «Установить» на нужной ветке — описание и версия в деталке обновятся.",
                "Tap Install on a branch — description & version update in details."));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        hint.setTextColor(themed(Theme.key_dialogTextGray3, 0xFF8E8E93));
        root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        rebuild();
        if (catalogEntry == null) {
            // Довантажуємо каталог, щоб показати гілки навіть з екрана "Встановлені".
            HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
                if (ok && catalog != null && catalog.find(moduleId) != null) {
                    this.catalogEntry = catalog.find(moduleId);
                    rebuild();
                }
            });
        }
    }

    private int themed(int key, int fallback) {
        int c = getThemedColor(key);
        return c == 0 ? fallback : c;
    }

    private void rebuild() {
        Context context = getContext();
        if (context == null) return;
        itemsContainer.removeAllViews();
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);
        int danger = themed(Theme.key_text_RedRegular, 0xFFFF5A5A);

        List<HotModulesManager.InstalledInfo> installed = new ArrayList<>();
        for (HotModulesManager.InstalledInfo i : HotModulesManager.listInstalled()) {
            if (i.manifest.id.equals(moduleId)) installed.add(i);
        }

        itemsContainer.addView(sectionLabel(context,
                MiogramLocale.get("Встановлені", "Установленные", "Installed")));
        if (installed.isEmpty()) {
            itemsContainer.addView(simpleRow(context,
                    MiogramLocale.get("Нічого не скачано — виберіть гілку нижче",
                            "Ничего не скачано — выберите ветку ниже",
                            "Nothing downloaded — pick a branch below"), sub));
        }
        for (HotModulesManager.InstalledInfo info : installed) {
            itemsContainer.addView(installedRow(context, info, text, sub, accent, danger));
        }

        if (catalogEntry != null) {
            itemsContainer.addView(sectionLabel(context,
                    MiogramLocale.get("Гілки", "Ветки", "Branches")));
            if (catalogEntry.branches.isEmpty()) {
                itemsContainer.addView(simpleRow(context, "—", sub));
            }
            for (String branch : catalogEntry.branches.keySet()) {
                HotCatalog.Build b = catalogEntry.branches.get(branch);
                if (b != null) itemsContainer.addView(remoteRow(context, b, text, sub, accent, installed));
            }
            if (!catalogEntry.history.isEmpty()) {
                itemsContainer.addView(sectionLabel(context,
                        MiogramLocale.get("Історія", "История", "History")));
                for (HotCatalog.Build b : catalogEntry.history) {
                    itemsContainer.addView(remoteRow(context, b, text, sub, accent, installed));
                }
            }
        } else {
            itemsContainer.addView(simpleRow(context,
                    MiogramLocale.get("Каталог ще вантажиться…", "Каталог ещё грузится…", "Loading catalog…"), sub));
        }
    }

    private View sectionLabel(Context context, String s) {
        return YumiComponents.sectionHeader(context, s);
    }

    private View simpleRow(Context context, String s, int color) {
        TextView v = new TextView(context);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        v.setTextColor(color);
        v.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(6),
                AndroidUtilities.dp(2), AndroidUtilities.dp(6));
        return v;
    }

    private View installedRow(Context context, HotModulesManager.InstalledInfo info,
                              int text, int sub, int accent, int danger) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(YumiTheme.cardBackground(14));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(8), AndroidUtilities.dp(11));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView ver = new TextView(context);
        ver.setText("v" + info.manifest.version + " • " + info.manifest.branch
                + (info.active ? " ✓" : ""));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        ver.setTypeface(info.active ? AndroidUtilities.bold() : null);
        ver.setTextColor(info.active ? accent : text);
        meta.addView(ver);
        TextView st = new TextView(context);
        st.setText(info.active
                ? MiogramLocale.get("активна", "активна", "active")
                : MiogramLocale.get("завантажена", "загружена", "downloaded"));
        st.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        st.setTextColor(sub);
        meta.addView(st);

        card.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (!info.active) {
            TextView use = new TextView(context);
            use.setText(MiogramLocale.get("Активувати", "Активировать", "Activate"));
            use.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            use.setTypeface(AndroidUtilities.bold());
            use.setTextColor(accent);
            use.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(10), AndroidUtilities.dp(8));
            use.setOnClickListener(v -> HotModulesManager.activateVersion(
                    moduleId, info.manifest.version, (ok, msg, data) -> {
                        if (onChanged != null) onChanged.onChanged();
                        rebuild();
                    }));
            card.addView(use);
        }

        TextView del = new TextView(context);
        del.setText("✕");
        del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        del.setTextColor(danger);
        del.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(6), AndroidUtilities.dp(8));
        del.setOnClickListener(v -> {
            HotModulesManager.deleteVersion(moduleId, info.manifest.version);
            if (onChanged != null) onChanged.onChanged();
            rebuild();
        });
        card.addView(del);

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return wrap;
    }

    private View remoteRow(Context context, HotCatalog.Build b, int text, int sub,
                           int accent, List<HotModulesManager.InstalledInfo> installed) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(14));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView ver = new TextView(context);
        ver.setText("v" + b.version);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        ver.setTypeface(AndroidUtilities.bold());
        ver.setTextColor(text);
        titleRow.addView(ver);
        TextView branch = new TextView(context);
        branch.setText(b.branch + (HotModulesManager.isCompatible(b) ? "" : " ⚠"));
        branch.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        branch.setTextColor(sub);
        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(AndroidUtilities.dp(99));
        badge.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        branch.setBackground(badge);
        branch.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2),
                AndroidUtilities.dp(8), AndroidUtilities.dp(2));
        titleRow.addView(branch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        boolean already = false;
        for (HotModulesManager.InstalledInfo i : installed) {
            if (i.manifest.version.equals(b.version)) {
                already = true;
                break;
            }
        }
        if (already) {
            TextView got = new TextView(context);
            got.setText("✓");
            got.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            got.setTextColor(accent);
            got.setPadding(AndroidUtilities.dp(6), 0, 0, 0);
            titleRow.addView(got);
        }
        meta.addView(titleRow);
        if (b.changelog != null && !b.changelog.isEmpty()) {
            TextView log = new TextView(context);
            log.setText(b.changelog);
            log.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            log.setTextColor(sub);
            log.setMaxLines(3);
            log.setEllipsize(android.text.TextUtils.TruncateAt.END);
            meta.addView(log, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }
        row.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setVisibility(View.GONE);
        row.addView(progress);

        org.telegram.ui.Components.CheckBox2 checkBox = new org.telegram.ui.Components.CheckBox2(context, 21);
        checkBox.setChecked(true, false);
        row.addView(checkBox, LayoutHelper.createLinear(21, 21, 0, 4, 4, 0));

        final boolean compat = HotModulesManager.isCompatible(b);
        TextView dl = YumiComponents.pillButton(context, !compat
                ? "minApp " + b.minApp
                : already
                ? MiogramLocale.get("Перевстановити", "Переустановить", "Reinstall")
                : MiogramLocale.get("Встановити", "Установить", "Install"));
        dl.setAlpha(compat ? 1f : 0.5f);
        dl.setOnClickListener(v -> {
            if (!compat) {
                try {
                    android.widget.Toast.makeText(context,
                            MiogramLocale.get("Потрібен новіший AmeGram (min " + b.minApp + ")",
                                    "Нужен новее AmeGram (min " + b.minApp + ")",
                                    "Requires newer AmeGram (min " + b.minApp + ")"),
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
                return;
            }
            dl.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            boolean autoEnable = checkBox.isChecked();
            HotModulesManager.downloadBuild(moduleId, b, autoEnable,
                    new HotModulesManager.ProgressCallback<Void>() {
                        @Override
                        public void onProgress(long downloaded, long total) {
                        }

                        @Override
                        public void onDone(boolean ok, String message, Void data) {
                            dl.setEnabled(true);
                            progress.setVisibility(View.GONE);
                            if (ok) {
                                if (onChanged != null) onChanged.onChanged();
                                rebuild();
                            } else {
                                try {
                                    android.widget.Toast.makeText(context,
                                            message != null ? message : "error",
                                            android.widget.Toast.LENGTH_SHORT).show();
                                } catch (Throwable ignore) {
                                }
                            }
                        }
                    });
        });
        row.addView(dl);
        card.addView(row);

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return wrap;
    }
}
