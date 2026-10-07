package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import app.amegram.theme.YumiTheme;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.miogram.bridge.MiogramLocale;

/**
 * Керування хот-модулями:
 * перегляд встановлених розширень, зміна версії (новіша/старіша),
 * видалення, увімкнення/вимкнення, навантаження (RAM/DEX) та опис.
 */
public class HotModulesActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int MENU_ADD = 1;
    private static final int MENU_UPDATES = 2;
    private static final int MENU_BACKUP = 3;

    private UniversalRecyclerView listView;
    private final List<HotModulesManager.InstalledInfo> shown = new ArrayList<>();
    private final java.util.Map<String, String> pendingUpdates = new java.util.LinkedHashMap<>();

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Хот-модулі", "Хот-модули", "Hot modules"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADD) {
                    HotCatalogSheet sheet = new HotCatalogSheet(getContext(), () -> refresh());
                    sheet.show();
                } else if (id == MENU_UPDATES) {
                    checkUpdates(true);
                } else if (id == MENU_BACKUP) {
                    showBackupDialog();
                }
            }
        });
        actionBar.createMenu().addItem(MENU_UPDATES, R.drawable.baseline_system_update_24);
        actionBar.createMenu().addItem(MENU_BACKUP, R.drawable.baseline_share_24);
        actionBar.createMenu().addItem(MENU_ADD, R.drawable.filled_new_contact_24);

        fragmentView = listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        listView.setSections();
        HotModulesManager.init(context.getApplicationContext());
        HotModulesManager.addListener(this);
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        checkUpdates(false);
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        HotModulesManager.removeListener(this);
    }

    @Override
    public void onModulesChanged() {
        refresh();
    }

    private void refresh() {
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    /** Перевірка оновлень з тротлінгом 6 год (force = з меню, одразу). */
    private void checkUpdates(boolean force) {
        Context context = getContext();
        long last = 0;
        try {
            last = context.getSharedPreferences("hotmodules_prefs", Context.MODE_PRIVATE)
                    .getLong("updates_time", 0);
        } catch (Throwable ignore) {
        }
        if (!force && System.currentTimeMillis() - last < 6L * 60 * 60 * 1000L) {
            reloadPendingUpdates();
            return;
        }
        HotModulesManager.checkUpdatesAsync((ok, msg, found) -> {
            try {
                context.getSharedPreferences("hotmodules_prefs", Context.MODE_PRIVATE)
                        .edit().putLong("updates_time", System.currentTimeMillis()).apply();
            } catch (Throwable ignore) {
            }
            reloadPendingUpdates();
            refresh();
            if (force && context != null) {
                try {
                    android.widget.Toast.makeText(context,
                            ok ? (found.isEmpty()
                                    ? MiogramLocale.get("Оновлень нема ✓", "Обновлений нет ✓", "No updates ✓")
                                    : MiogramLocale.get("Знайдено оновлень: ", "Найдено обновлений: ", "Updates found: ") + found.size())
                                    : String.valueOf(msg),
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
            }
        });
    }

    private void reloadPendingUpdates() {
        pendingUpdates.clear();
        for (HotModulesManager.InstalledInfo info : HotModulesManager.listInstalled()) {
            if (!info.active) continue;
            String up = HotModulesManager.getUpdateAvailable(info.manifest.id);
            if (!up.isEmpty() && up.compareTo(info.manifest.version) > 0) {
                pendingUpdates.put(info.manifest.id, up);
            }
        }
    }

    /** Оновити все: качаємо кожен апдейт по черзі. */
    private void updateAll() {
        Context context = getContext();
        if (context == null || pendingUpdates.isEmpty()) return;
        HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
            if (!ok || catalog == null) return;
            List<String> ids = new ArrayList<>(pendingUpdates.keySet());
            updateNext(ids, 0, catalog);
        });
    }

    private void updateNext(List<String> ids, int idx, HotCatalog catalog) {
        if (idx >= ids.size()) {
            refresh();
            return;
        }
        String id = ids.get(idx);
        HotCatalog.Entry e = catalog.find(id);
        HotCatalog.Build latest = e != null ? e.latestCompatible(HotModulesManager.appVersion()) : null;
        if (latest == null || !HotModulesManager.isCompatible(latest)) {
            updateNext(ids, idx + 1, catalog);
            return;
        }
        HotModulesManager.downloadBuild(id, latest, true,
                new HotModulesManager.ProgressCallback<Void>() {
                    @Override public void onProgress(long d, long t) { }
                    @Override public void onDone(boolean ok, String message, Void data) {
                        updateNext(ids, idx + 1, catalog);
                    }
                });
    }

    /** Бекап набору: експорт у буфер / імпорт з тексту. */
    private void showBackupDialog() {
        Context context = getContext();
        if (context == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(MiogramLocale.get("Набір модулів", "Набор модулей", "Module set"));
        String[] items = new String[]{
                MiogramLocale.get("📤 Експорт (копіювати)", "📤 Экспорт (копировать)", "📤 Export (copy)"),
                MiogramLocale.get("📥 Імпорт (вставити)", "📥 Импорт (вставить)", "📥 Import (paste)")};
        b.setItems(items, (d, which) -> {
            if (which == 0) {
                try {
                    String json = HotModulesManager.exportModuleSet();
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("modules", json));
                    }
                    android.widget.Toast.makeText(context, json, android.widget.Toast.LENGTH_LONG).show();
                } catch (Throwable ignore) {
                }
            } else {
                android.widget.EditText input = new android.widget.EditText(context);
                input.setHint("[{\"id\":\"ghost\",…}]");
                AlertDialog.Builder b2 = new AlertDialog.Builder(context);
                b2.setTitle(MiogramLocale.get("Вставте набір", "Вставьте набор", "Paste set"));
                b2.setView(input);
                b2.setPositiveButton(MiogramLocale.get("Імпортувати", "Импортировать", "Import"), (d2, w) -> {
                    String txt = input.getText() != null ? input.getText().toString() : "";
                    List<java.util.Map<String, String>> refs = HotModulesManager.parseModuleSet(txt);
                    int missing = 0;
                    for (java.util.Map<String, String> r : refs) {
                        String id = r.get("id");
                        boolean wantOn = "1".equals(r.get("enabled"));
                        if (HotModulesManager.isModuleInstalled(id)) {
                            HotModulesManager.setEnabled(id, wantOn, (ok, m, v) -> refresh());
                        } else {
                            missing++;
                        }
                    }
                    refresh();
                    if (missing > 0) {
                        HotCatalogSheet sheet = new HotCatalogSheet(context, () -> refresh());
                        sheet.show();
                    }
                    try {
                        android.widget.Toast.makeText(context,
                                MiogramLocale.get("Імпортовано: ", "Импортировано: ", "Imported: ") + refs.size()
                                        + (missing > 0 ? MiogramLocale.get(" • добрати в магазині: ", " • добрать в магазине: ", " • get in store: ") + missing : ""),
                                android.widget.Toast.LENGTH_LONG).show();
                    } catch (Throwable ignore) {
                    }
                });
                b2.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
                b2.show();
            }
        });
        b.show();
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        shown.clear();
        Context context = getContext();
        if (context == null) return;

        Map<String, HotModulesManager.InstalledInfo> byId = new LinkedHashMap<>();
        for (HotModulesManager.InstalledInfo info : HotModulesManager.listInstalled()) {
            HotModulesManager.InstalledInfo cur = byId.get(info.manifest.id);
            if (cur == null || info.active) {
                byId.put(info.manifest.id, info.active ? info : cur == null ? info : cur);
            }
        }
        shown.addAll(byId.values());
        reloadPendingUpdates();

        items.add(UItem.asHeader(MiogramLocale.get("Встановлені модулі", "Установленные модули", "Installed modules")
                + " • 0 " + MiogramLocale.get("вбудовано", "встроено", "built-in")));

        if (!pendingUpdates.isEmpty()) {
            items.add(UItem.asCustom(buildUpdatesBanner(context)));
        }

        if (shown.isEmpty()) {
            items.add(UItem.asCustom(buildEmptyStateView(context)));
        } else {
            for (int i = 0; i < shown.size(); i++) {
                HotModulesManager.InstalledInfo info = shown.get(i);
                View cardView = buildModuleCard(context, info, i);
                items.add(UItem.asCustom(cardView));
            }
        }

        items.add(UItem.asShadow(MiogramLocale.get(
                "Чистий клієнт: 0 модулів вбудовано — все ставиться з магазину. Вимкнений модуль вивантажується з пам'яті (0 KB RAM). Торкніть картку, щоб побачити опис і версії.",
                "Чистый клиент: 0 модулей встроено — всё ставится из магазина. Выключенный модуль выгружается из памяти (0 KB RAM). Нажмите на карточку, чтобы увидеть описание и версии.",
                "Clean client: 0 built-in — everything comes from the store. Disabled modules unload (0 KB RAM). Tap a card for details & versions.")));
    }

    private View buildUpdatesBanner(Context context) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.HORIZONTAL);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6C63FF, 0xFF9D7BFF});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        banner.setBackground(bg);
        banner.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13),
                AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        banner.setLayoutParams(lp);

        TextView t = new TextView(context);
        t.setText("↑ " + MiogramLocale.get("Оновлення: ", "Обновления: ", "Updates: ") + pendingUpdates.size());
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(0xFFFFFFFF);
        banner.addView(t, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView all = new TextView(context);
        all.setText(MiogramLocale.get("Оновити все", "Обновить всё", "Update all"));
        all.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        all.setTypeface(AndroidUtilities.bold());
        all.setTextColor(0xFF5B50E6);
        GradientDrawable ab = new GradientDrawable();
        ab.setCornerRadius(AndroidUtilities.dp(11));
        ab.setColor(0xFFFFFFFF);
        all.setBackground(ab);
        all.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(9),
                AndroidUtilities.dp(16), AndroidUtilities.dp(9));
        all.setOnClickListener(v -> {
            all.setEnabled(false);
            updateAll();
        });
        banner.addView(all);
        return banner;
    }

    private View buildEmptyStateView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(32), AndroidUtilities.dp(24), AndroidUtilities.dp(32));

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.drawable.msg_plugins);
        icon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        layout.addView(icon, LayoutHelper.createLinear(48, 48, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Немає встановлених модулів", "Нет установленных модулей", "No installed modules"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get(
                "Ви самі обираєте, що завантажувати. Перейдіть у каталог, щоб переглянути описи та встановити потрібні розширення.",
                "Вы сами выбираете, что скачивать. Перейдите в каталог, чтобы посмотреть описания и установить нужные расширения.",
                "You choose what to install. Open catalog to view descriptions and install extensions."));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setGravity(Gravity.CENTER);
        layout.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView btnCatalog = new TextView(context);
        btnCatalog.setText(MiogramLocale.get("Відкрити каталог модулів", "Открыть каталог модулей", "Open module catalog"));
        btnCatalog.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        btnCatalog.setTypeface(AndroidUtilities.bold());
        btnCatalog.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        btnCatalog.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        btnCatalog.setBackground(btnBg);
        btnCatalog.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        btnCatalog.setOnClickListener(v -> {
            HotCatalogSheet sheet = new HotCatalogSheet(context, () -> refresh());
            sheet.show();
        });
        layout.addView(btnCatalog, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return layout;
    }

    private View buildModuleCard(Context context, HotModulesManager.InstalledInfo info, int index) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);

        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        card.setLayoutParams(lp);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        // Головний рядок: Іконка + Опис + Світч
        LinearLayout topRow = new LinearLayout(context);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // 1. Іконка модуля
        int modColor = getModuleColor(info.manifest.id);
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(modColor));

        ImageView iconView = new ImageView(context);
        iconView.setImageResource(getModuleIcon(info.manifest.id));
        iconView.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(iconView, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        topRow.addView(iconFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        // 2. Інформація (Заголовок, бейдж версії, навантаження, опис)
        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(info.manifest.name);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        titleRow.addView(titleView);

        // Бейдж версії
        TextView verBadge = new TextView(context);
        verBadge.setText("v" + info.manifest.version);
        verBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        verBadge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        verBadge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
        verBadge.setBackground(YumiTheme.squircleIconBackground(Theme.isCurrentThemeDark() ? 0x22FFFFFF : 0x14000000));
        titleRow.addView(verBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

        infoCol.addView(titleRow);

        // Навантаження / статус пам'яті в реальному часі
        TextView loadView = new TextView(context);
        loadView.setText(HotModulesManager.getModuleLoadEstimate(info.manifest.id));
        loadView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        loadView.setTextColor(info.enabled ? 0xFF4CAF50 : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoCol.addView(loadView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 2));

        // Опис модуля
        TextView descView = new TextView(context);
        descView.setText(getModuleDescription(info.manifest.id));
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setMaxLines(2);
        infoCol.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        // Бейджі: доступне оновлення • карантин
        String upd = pendingUpdates.get(info.manifest.id);
        if (upd != null) {
            TextView upBadge = new TextView(context);
            upBadge.setText("↑ v" + upd + MiogramLocale.get(" доступно", " доступно", " available"));
            upBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            upBadge.setTypeface(AndroidUtilities.bold());
            upBadge.setTextColor(YumiTheme.getPrimary());
            infoCol.addView(upBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }
        if (HotModulesManager.isQuarantined(info.manifest.id)) {
            TextView qBadge = new TextView(context);
            qBadge.setText("⛔ " + MiogramLocale.get("карантин: падав "
                    + HotModulesManager.getLoadFailures(info.manifest.id) + "×",
                    "карантин: падал " + HotModulesManager.getLoadFailures(info.manifest.id) + "×",
                    "quarantined: crashed " + HotModulesManager.getLoadFailures(info.manifest.id) + "×"));
            qBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            qBadge.setTextColor(YumiTheme.getError());
            infoCol.addView(qBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        topRow.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        // 3. Світч увімкнення/вимкнення — стабільний клік без рекурсії
        Switch sw = new Switch(context);
        sw.setChecked(info.enabled, false);
        View.OnClickListener toggleAction = v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            HotModulesManager.setEnabled(info.manifest.id, next, (ok, msg, data) -> refresh());
        };
        sw.setOnClickListener(toggleAction);
        topRow.setOnClickListener(toggleAction);
        topRow.addView(sw, LayoutHelper.createLinear(40, 26, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        card.addView(topRow);

        // Нижній рядок дій: [Налаштування (лише якщо увімкнено)] [Версії] [Видалити]
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (info.enabled) {
            TextView btnSettings = new TextView(context);
            btnSettings.setText(MiogramLocale.get("Налаштування", "Настройки", "Settings"));
            btnSettings.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            btnSettings.setTypeface(AndroidUtilities.bold());
            btnSettings.setTextColor(YumiTheme.getPrimary());
            btnSettings.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            btnSettings.setOnClickListener(v -> presentFragment(new HotModuleSettingsActivity(info.manifest.id)));
            bottomRow.addView(btnSettings);
        }

        TextView btnVersions = new TextView(context);
        btnVersions.setText(info.manifest.branch + " • " + MiogramLocale.get("Версії", "Версии", "Versions"));
        btnVersions.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btnVersions.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        btnVersions.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        btnVersions.setOnClickListener(v -> {
            HotModuleVersionsSheet sheet = new HotModuleVersionsSheet(context, info.manifest.id, null, () -> refresh());
            sheet.show();
        });
        bottomRow.addView(btnVersions);

        TextView btnDelete = new TextView(context);
        btnDelete.setText(MiogramLocale.get("Видалити", "Удалить", "Delete"));
        btnDelete.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        btnDelete.setTextColor(YumiTheme.getError());
        btnDelete.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        btnDelete.setOnClickListener(v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(MiogramLocale.get("Видалення модуля", "Удаление модуля", "Delete module"));
            builder.setMessage(MiogramLocale.get(
                    "Ви дійсно хочете видалити модуль " + info.manifest.name + " та звільнити пам'ять?",
                    "Вы действительно хотите удалить модуль " + info.manifest.name + " и освободить память?",
                    "Are you sure you want to delete module " + info.manifest.name + "?"));
            builder.setPositiveButton(MiogramLocale.get("Видалити", "Удалить", "Delete"), (d, w) -> {
                HotModulesManager.deleteModule(info.manifest.id);
                refresh();
            });
            builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
            builder.show();
        });
        bottomRow.addView(btnDelete);

        card.addView(bottomRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        card.setOnClickListener(v -> {
            // Тап по картці → деталка: опис, велика кнопка знизу, вибір версій.
            HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
                HotCatalog.Entry found = (ok && catalog != null) ? catalog.find(info.manifest.id) : null;
                HotCatalog.Build sel = null;
                if (found != null) {
                    for (HotCatalog.Build b : found.branches.values()) {
                        if (b.version.equals(info.manifest.version)) {
                            sel = b;
                            break;
                        }
                    }
                    if (sel == null) sel = found.defaultBuild();
                }
                HotModuleDetailSheet sheet = new HotModuleDetailSheet(context,
                        info.manifest.id, found, sel, () -> refresh());
                sheet.show();
            });
        });

        return card;
    }

    private static int getModuleColor(String id) {
        return HotModuleMeta.color(id);
    }

    private static int getModuleIcon(String id) {
        return HotModuleMeta.icon(id);
    }

    private static String getModuleDescription(String id) {
        return HotModuleMeta.fallbackDescription(id);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        // Кліки обробляються прямо всередині карток
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
