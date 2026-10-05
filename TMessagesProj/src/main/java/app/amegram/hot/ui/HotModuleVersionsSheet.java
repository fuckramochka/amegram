package app.amegram.hot.ui;

import android.content.Context;
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
import app.miogram.bridge.MiogramLocale;

/**
 * Версии и ветки модуля: установленные (активировать/удалить)
 * + доступные из каталога (скачать). Выбор ветки = строки stable/beta/...
 */
public class HotModuleVersionsSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    private final String moduleId;
    private final HotCatalog.Entry catalogEntry;
    private final OnChanged onChanged;
    private final LinearLayout itemsContainer;

    public HotModuleVersionsSheet(Context context, String moduleId,
                                  HotCatalog.Entry catalogEntry, OnChanged onChanged) {
        super(context, false);
        this.moduleId = moduleId;
        this.catalogEntry = catalogEntry;
        this.onChanged = onChanged;

        int bg = themed(Theme.key_dialogBackground, 0xFF14151F);
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Версії: ", "Версии: ", "Versions: ") + moduleId);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        ScrollView scroll = new ScrollView(context);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        rebuild();
    }

    private int themed(int key, int fallback) {
        int c = getThemedColor(key);
        return c == 0 ? fallback : c;
    }

    private void rebuild() {
        Context context = getContext();
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
                    MiogramLocale.get("Нічого не скачано", "Ничего не скачано", "Nothing downloaded"), sub));
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
                if (b != null) itemsContainer.addView(remoteRow(context, b, text, sub, accent));
            }
            if (!catalogEntry.history.isEmpty()) {
                itemsContainer.addView(sectionLabel(context,
                        MiogramLocale.get("Історія", "История", "History")));
                for (HotCatalog.Build b : catalogEntry.history) {
                    itemsContainer.addView(remoteRow(context, b, text, sub, accent));
                }
            }
        }
    }

    private View sectionLabel(Context context, String s) {
        TextView v = new TextView(context);
        v.setText(s.toUpperCase());
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        v.setTypeface(AndroidUtilities.bold());
        v.setTextColor(themed(Theme.key_windowBackgroundWhiteBlueHeader, 0xFF6C63FF));
        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(v, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 4));
        return wrap;
    }

    private View simpleRow(Context context, String s, int color) {
        TextView v = new TextView(context);
        v.setText(s);
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setTextColor(color);
        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(v, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        return wrap;
    }

    private View installedRow(Context context, HotModulesManager.InstalledInfo info,
                              int text, int sub, int accent, int danger) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView ver = new TextView(context);
        ver.setText(info.manifest.version + " • " + info.manifest.branch
                + (info.active ? " ✓" : ""));
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        ver.setTypeface(info.active ? AndroidUtilities.bold() : null);
        ver.setTextColor(info.active ? accent : text);
        meta.addView(ver);

        row.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        if (!info.active) {
            TextView use = new TextView(context);
            use.setText(MiogramLocale.get("Активувати", "Активировать", "Activate"));
            use.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            use.setTextColor(accent);
            use.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(10), AndroidUtilities.dp(8));
            use.setOnClickListener(v -> HotModulesManager.activateVersion(
                    moduleId, info.manifest.version, (ok, msg, data) -> {
                        if (onChanged != null) onChanged.onChanged();
                        rebuild();
                    }));
            row.addView(use);
        }

        TextView del = new TextView(context);
        del.setText("✕");
        del.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        del.setTextColor(danger);
        del.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8),
                AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        del.setOnClickListener(v -> {
            HotModulesManager.deleteVersion(moduleId, info.manifest.version);
            if (onChanged != null) onChanged.onChanged();
            rebuild();
        });
        row.addView(del);

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        return wrap;
    }

    private View remoteRow(Context context, HotCatalog.Build b, int text, int sub, int accent) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView ver = new TextView(context);
        ver.setText(b.branch + " • " + b.version);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        ver.setTextColor(text);
        meta.addView(ver);
        if (b.changelog != null && !b.changelog.isEmpty()) {
            TextView log = new TextView(context);
            log.setText(b.changelog);
            log.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            log.setTextColor(sub);
            meta.addView(log);
        }
        row.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        progress.setVisibility(View.GONE);
        row.addView(progress);

        org.telegram.ui.Components.CheckBox2 checkBox = new org.telegram.ui.Components.CheckBox2(context, 21);
        checkBox.setChecked(true, false);
        row.addView(checkBox, LayoutHelper.createLinear(21, 21, 0, 4, 4, 0));

        TextView dl = new TextView(context);
        dl.setText(MiogramLocale.get("Встановити", "Установить", "Install"));
        dl.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        dl.setTypeface(AndroidUtilities.bold());
        dl.setTextColor(accent);
        dl.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8),
                AndroidUtilities.dp(4), AndroidUtilities.dp(8));
        dl.setOnClickListener(v -> {
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
                                dl.setText("✓ " + message);
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

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));
        return wrap;
    }
}
