package app.amegram.hot.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
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

import app.amegram.theme.YumiComponents;
import app.amegram.theme.YumiTheme;

import java.util.ArrayList;
import java.util.HashMap;
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
    private EditText searchView;
    private String query = "";
    private final List<HotModulesManager.InstalledInfo> shown = new ArrayList<>();
    private final java.util.Map<String, String> pendingUpdates = new java.util.LinkedHashMap<>();
    private final java.util.Set<String> updating = new java.util.HashSet<>();
    /** Прогрес оновлення id->% (неблокуючий, пер-рядок). */
    private final Map<String, Integer> progress = new HashMap<>();

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
                    presentFragment(new HotStoreActivity());
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

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        searchView = YumiComponents.searchField(context,
                MiogramLocale.get("Пошук встановлених…", "Поиск установленных…", "Search installed…"));
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s != null ? s.toString().trim().toLowerCase() : "";
                refresh();
            }
        });
        LinearLayout searchWrap = new LinearLayout(context);
        searchWrap.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10),
                AndroidUtilities.dp(14), AndroidUtilities.dp(2));
        searchWrap.addView(searchView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));
        root.addView(searchWrap);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, null);
        root.addView(listView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));
        listView.setSections();
        fragmentView = root;
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

    private boolean matchesQuery(HotModulesManager.InstalledInfo info) {
        if (query == null || query.isEmpty()) return true;
        String q = query.toLowerCase();
        String name = info.manifest.name != null ? info.manifest.name.toLowerCase() : "";
        String id = info.manifest.id != null ? info.manifest.id.toLowerCase() : "";
        if (name.contains(q) || id.contains(q)) return true;
        String desc = HotModuleMeta.fallbackDescription(info.manifest.id).toLowerCase();
        return desc.contains(q);
    }

    /** Оновити один модуль до latestCompatible з каталогу. */
    private void triggerUpdate(String moduleId) {
        if (updating.contains(moduleId)) return;
        updating.add(moduleId);
        refresh();
        HotModulesManager.fetchCatalog(false, (ok, msg, catalog) -> {
            HotCatalog.Entry e = (ok && catalog != null) ? catalog.find(moduleId) : null;
            HotCatalog.Build latest = e != null ? e.latestCompatible(HotModulesManager.appVersion()) : null;
            if (latest == null || !HotModulesManager.isCompatible(latest)) {
                updating.remove(moduleId);
                refresh();
                return;
            }
            HotModulesManager.downloadBuild(moduleId, latest, true,
                    new HotModulesManager.ProgressCallback<Void>() {
                        @Override public void onProgress(long d, long t) {
                            int pct = t > 0 ? (int) (d * 100 / t) : 0;
                            progress.put(moduleId, pct);
                        }
                        @Override public void onDone(boolean doneOk, String message, Void data) {
                            updating.remove(moduleId);
                            progress.remove(moduleId);
                            reloadPendingUpdates();
                            refresh();
                        }
                    });
        });
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
        updating.add(id);
        HotModulesManager.downloadBuild(id, latest, true,
                new HotModulesManager.ProgressCallback<Void>() {
                    @Override public void onProgress(long d, long t) {
                        int pct = t > 0 ? (int) (d * 100 / t) : 0;
                        progress.put(id, pct);
                    }
                    @Override public void onDone(boolean ok, String message, Void data) {
                        updating.remove(id);
                        progress.remove(id);
                        reloadPendingUpdates();
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
                    android.widget.Toast.makeText(context,
                            MiogramLocale.get("✓ Набір скопійовано в буфер", "✓ Набор скопирован в буфер", "✓ Set copied to clipboard"),
                            android.widget.Toast.LENGTH_SHORT).show();
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
                        try {
                            presentFragment(new HotStoreActivity());
                        } catch (Throwable ignore) {
                        }
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

        // Пошук + сортування: оновлення першими, далі увімкнені, далі вимкнені, далі за назвою.
        List<HotModulesManager.InstalledInfo> filtered = new ArrayList<>();
        for (HotModulesManager.InstalledInfo info : shown) {
            if (matchesQuery(info)) filtered.add(info);
        }
        filtered.sort((a, b) -> {
            boolean au = pendingUpdates.containsKey(a.manifest.id);
            boolean bu = pendingUpdates.containsKey(b.manifest.id);
            if (au != bu) return au ? -1 : 1;
            if (a.enabled != b.enabled) return a.enabled ? -1 : 1;
            String an = a.manifest.name != null ? a.manifest.name : a.manifest.id;
            String bn = b.manifest.name != null ? b.manifest.name : b.manifest.id;
            return an.compareToIgnoreCase(bn);
        });

        int enabledCount = 0;
        for (HotModulesManager.InstalledInfo i : filtered) {
            if (i.enabled) enabledCount++;
        }
        items.add(UItem.asHeader(MiogramLocale.get("Встановлені модулі", "Установленные модули", "Installed modules")
                + " • " + filtered.size()
                + MiogramLocale.get(" • увімкнено: ", " • включено: ", " • on: ") + enabledCount
                + " • 0 " + MiogramLocale.get("вбудовано", "встроено", "built-in")));

        if (!pendingUpdates.isEmpty() && query.isEmpty()) {
            items.add(UItem.asCustom(buildUpdatesBanner(context)));
        }

        if (filtered.isEmpty()) {
            if (query.isEmpty()) {
                items.add(UItem.asCustom(buildEmptyStateView(context)));
            } else {
                items.add(UItem.asCustom(buildNoResultsView(context)));
            }
        } else {
            String lastGroup = "";
            for (int i = 0; i < filtered.size(); i++) {
                HotModulesManager.InstalledInfo info = filtered.get(i);
                String group;
                if (pendingUpdates.containsKey(info.manifest.id)) {
                    group = MiogramLocale.get("⬆ До оновлення", "⬆ К обновлению", "⬆ To update");
                } else if (info.enabled) {
                    group = MiogramLocale.get("🟢 Увімкнені", "🟢 Включены", "🟢 Enabled");
                } else {
                    group = MiogramLocale.get("⚪ Вимкнені", "⚪ Выключены", "⚪ Disabled");
                }
                if (!group.equals(lastGroup)) {
                    items.add(UItem.asHeader(group));
                    lastGroup = group;
                }
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
            all.setAlpha(0.5f);
            all.setText(MiogramLocale.get("Оновлення…", "Обновление…", "Updating…"));
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

        TextView btnCatalog = YumiComponents.primaryButton(context,
                MiogramLocale.get("Відкрити каталог модулів", "Открыть каталог модулей", "Open module catalog"));
        btnCatalog.setOnClickListener(v -> presentFragment(new HotStoreActivity()));
        layout.addView(btnCatalog, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return layout;
    }

    private View buildNoResultsView(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(28), AndroidUtilities.dp(24), AndroidUtilities.dp(28));
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Нічого не знайдено", "Ничего не найдено", "Nothing found"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setGravity(Gravity.CENTER);
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        TextView reset = YumiComponents.ghostButton(context,
                MiogramLocale.get("✕ Скинути пошук", "✕ Сбросить поиск", "✕ Clear search"),
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        reset.setOnClickListener(v -> {
            query = "";
            try {
                if (searchView != null) searchView.setText("");
            } catch (Throwable ignore) {
            }
            refresh();
        });
        layout.addView(reset, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
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
        titleView.setText((info.enabled ? "● " : "○ ") + info.manifest.name);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(info.enabled
                ? Theme.getColor(Theme.key_windowBackgroundWhiteBlackText)
                : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        titleView.setMaxLines(1);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        // Бейдж версії
        TextView verBadge = YumiComponents.badge(context, "v" + info.manifest.version,
                Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
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

        // Тонкий неблокуючий прогрес оновлення (пер-рядок).
        int pct0 = progress.containsKey(info.manifest.id) ? progress.get(info.manifest.id) : 0;
        boolean isUpdating = updating.contains(info.manifest.id);
        TextView progressView = new TextView(context);
        progressView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        progressView.setTextColor(YumiTheme.getPrimary());
        if (isUpdating) {
            progressView.setVisibility(View.VISIBLE);
            progressView.setText(pct0 > 0 ? pct0 + "%" : "…");
        } else {
            progressView.setVisibility(View.GONE);
        }
        infoCol.addView(progressView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

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
                    + HotModulesManager.getLoadFailures(info.manifest.id) + "× — торкніть щоб скинути",
                    "карантин: падал " + HotModulesManager.getLoadFailures(info.manifest.id) + "× — нажмите чтобы сбросить",
                    "quarantined: crashed " + HotModulesManager.getLoadFailures(info.manifest.id) + "× — tap to reset"));
            qBadge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            qBadge.setTextColor(YumiTheme.getError());
            qBadge.setOnClickListener(v -> {
                HotModulesManager.unquarantine(info.manifest.id);
                refresh();
            });
            infoCol.addView(qBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        }

        topRow.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 8, 0));

        // 3. Світч увімкнення/вимкнення — стабільний клік без рекурсії
        Switch sw = new Switch(context);
        sw.setChecked(info.enabled, false);
        View.OnClickListener toggleAction = v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next, true);
            HotModulesManager.setEnabled(info.manifest.id, next, (ok, msg, data) -> {
                if (!ok && msg != null && !msg.isEmpty()) {
                    try {
                        android.widget.Toast.makeText(context, msg,
                                android.widget.Toast.LENGTH_LONG).show();
                    } catch (Throwable ignore) {
                    }
                }
                refresh();
            });
        };
        sw.setOnClickListener(toggleAction);
        // Тап по рядку більше НЕ тоглить: тап по картці відкриває деталку,
        // тоглить тільки сам світч. Раніше topRow перехоплював тап і плутав юзера.
        topRow.addView(sw, LayoutHelper.createLinear(40, 26, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));

        card.addView(topRow);

        // Нижній рядок дій: [Оновити] [Налаштування] [Версії] [Видалити]
        LinearLayout bottomRow = new LinearLayout(context);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (upd != null) {
            TextView btnUpdate = new TextView(context);
            isUpdating = updating.contains(info.manifest.id);
            btnUpdate.setText(isUpdating ? "…" : "↑ " + MiogramLocale.get("Оновити до v", "Обновить до v", "Update to v") + upd);
            btnUpdate.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            btnUpdate.setTypeface(AndroidUtilities.bold());
            btnUpdate.setTextColor(YumiTheme.getPrimary());
            btnUpdate.setAlpha(isUpdating ? 0.5f : 1f);
            btnUpdate.setEnabled(!isUpdating);
            btnUpdate.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
            btnUpdate.setOnClickListener(v -> triggerUpdate(info.manifest.id));
            bottomRow.addView(btnUpdate);
        }

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
