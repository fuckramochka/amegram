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

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.miogram.bridge.MiogramLocale;

/**
 * "+" списка: каталог из отдельного репозитория (+asset fallback).
 * Новый модуль в modules.json появляется здесь сам, без обновления приложения.
 */
public class HotCatalogSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    private final LinearLayout itemsContainer;
    private final TextView statusView;
    private final OnChanged onChanged;

    public HotCatalogSheet(Context context, OnChanged onChanged) {
        super(context, false);
        this.onChanged = onChanged;

        int bg = getThemedColor(Theme.key_dialogBackground);
        if (bg == 0) bg = 0xFF14151F;
        int text = getThemedColor(Theme.key_dialogTextBlack);
        if (text == 0) text = 0xFFFFFFFF;
        int sub = getThemedColor(Theme.key_dialogTextGray3);
        if (sub == 0) sub = 0xFF8E8E93;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12),
                AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Каталог модулів", "Каталог модулей", "Module catalog"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        statusView.setTextColor(sub);
        root.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        ScrollView scroll = new ScrollView(context);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(itemsContainer);
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        load(true);
    }

    private int themed(int key, int fallback) {
        int c = getThemedColor(key);
        return c == 0 ? fallback : c;
    }

    private void load(boolean force) {
        Context context = getContext();
        statusView.setText(MiogramLocale.get("Завантаження…", "Загрузка…", "Loading…"));
        itemsContainer.removeAllViews();
        HotModulesManager.fetchCatalog(force, (ok, msg, catalog) -> {
            if (!ok || catalog == null) {
                statusView.setText(MiogramLocale.get("Не вдалося завантажити каталог",
                        "Не удалось загрузить каталог", "Catalog load failed")
                        + (msg != null && !msg.isEmpty() ? ": " + msg : ""));
                return;
            }
            String src = "cached".equals(msg)
                    ? MiogramLocale.get(" (кеш)", " (кэш)", " (cached)")
                    : "asset".equals(msg)
                    ? MiogramLocale.get(" (вбудований)", " (встроенный)", " (built-in)") : "";
            if (catalog.modules.isEmpty()) {
                statusView.setText(MiogramLocale.get("Каталог порожній",
                        "Каталог пуст", "Catalog is empty") + src);
                return;
            }
            statusView.setText(MiogramLocale.get("Модулів: ", "Модулей: ", "Modules: ")
                    + catalog.modules.size() + src);
            for (HotCatalog.Entry entry : catalog.modules) {
                itemsContainer.addView(buildCard(context, entry));
            }
        });
    }

    private View buildCard(Context context, HotCatalog.Entry entry) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);
        int cardBg = themed(Theme.key_windowBackgroundGray, 0xFF1E1F22);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(14));
        bg.setColor(cardBg);
        card.setBackground(bg);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        TextView name = new TextView(context);
        name.setText(entry.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(text);
        card.addView(name);

        if (!entry.description.isEmpty()) {
            TextView desc = new TextView(context);
            desc.setText(entry.description);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            desc.setTextColor(sub);
            card.addView(desc);
        }

        HotCatalog.Build def = entry.defaultBuild();
        TextView ver = new TextView(context);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        ver.setTextColor(sub);
        ver.setText(def != null ? (def.branch + " • " + def.version) : "—");
        card.addView(ver, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 4));

        // Галочка "Встановити та ввімкнути"
        LinearLayout checkRow = new LinearLayout(context);
        checkRow.setOrientation(LinearLayout.HORIZONTAL);
        checkRow.setGravity(Gravity.CENTER_VERTICAL);
        checkRow.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(6));

        org.telegram.ui.Components.CheckBox2 checkBox = new org.telegram.ui.Components.CheckBox2(context, 21);
        checkBox.setChecked(true, false);
        checkRow.addView(checkBox, LayoutHelper.createLinear(21, 21, 0, 0, 8, 0));

        TextView checkText = new TextView(context);
        checkText.setText(MiogramLocale.get("Встановити та ввімкнути", "Установить и включить", "Install and enable"));
        checkText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        checkText.setTextColor(text);
        checkRow.addView(checkText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        checkRow.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked(), true));
        card.addView(checkRow);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        row.addView(progress, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 8, 0, 0));

        boolean isInstalled = HotModulesManager.isModuleInstalled(entry.id);
        boolean builtin = HotModulesManager.isBuiltin(entry.id);
        TextView action = new TextView(context);
        action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        action.setTypeface(AndroidUtilities.bold());
        action.setTextColor(accent);
        action.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        if (builtin) {
            action.setText(MiogramLocale.get("Вбудовано в клієнт", "Встроен в клиент", "Built into client"));
            action.setAlpha(0.5f);
            action.setEnabled(false);
        } else {
            action.setText(isInstalled ? MiogramLocale.get("Оновити", "Обновить", "Update")
                    : MiogramLocale.get("Встановити", "Установить", "Install"));
        }
        action.setOnClickListener(v -> {
            if (def == null) return;
            action.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            boolean autoEnable = checkBox.isChecked();
            HotModulesManager.downloadBuild(entry.id, def, autoEnable,
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
                            action.setEnabled(true);
                            progress.setVisibility(View.GONE);
                            if (ok) {
                                action.setText("✓ " + message);
                                if (onChanged != null) onChanged.onChanged();
                            } else {
                                action.setText(MiogramLocale.get("Помилка", "Ошибка", "Error"));
                                action.setEnabled(true);
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
        row.addView(action);

        TextView versions = new TextView(context);
        versions.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        versions.setTextColor(sub);
        versions.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6),
                AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        versions.setText(MiogramLocale.get("Версії", "Версии", "Versions"));
        versions.setOnClickListener(v -> {
            HotModuleVersionsSheet sheet = new HotModuleVersionsSheet(context,
                    entry.id, entry, () -> {
                        if (onChanged != null) onChanged.onChanged();
                    });
            sheet.show();
        });
        row.addView(versions);

        card.addView(row);

        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        return wrap;
    }
}
