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
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.List;

import app.amegram.hot.HotCatalog;
import app.amegram.hot.HotModulesManager;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Повноекранний магазин модулів (мова дизайну = список встановлених):
 * онбординг + паки • категорії • featured • пошук • картки
 * (іконка • назва • опис • Взяти). Встановлених тут нема.
 * Тап по картці → деталка з версіями і великою кнопкою знизу.
 */
public class HotStoreActivity extends BaseFragment implements HotModulesManager.ModulesChangeListener {

    private static final int MENU_REFRESH = 1;

    private UniversalRecyclerView listView;
    private EditText searchView;
    private HotCatalog lastCatalog;
    private String lastSrc = "";
    private String query = "";
    private String activeCat = "";
    private boolean loading = true;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Магазин модулів", "Магазин модулей", "Module store"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_REFRESH) {
                    load(true);
                }
            }
        });
        actionBar.createMenu().addItem(MENU_REFRESH, R.drawable.baseline_system_update_24);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        searchView = new EditText(context);
        searchView.setHint(MiogramLocale.get("Пошук модулів…", "Поиск модулей…", "Search modules…"));
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        searchView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchView.setSingleLine(true);
        searchView.setBackground(YumiTheme.cardBackground(12));
        searchView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));
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

        HotModulesManager.init(context.getApplicationContext());
        HotModulesManager.addListener(this);
        load(false);
        fragmentView = root;
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
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

    private void load(boolean force) {
        loading = true;
        refresh();
        HotModulesManager.fetchCatalog(force, (ok, msg, catalog) -> {
            loading = false;
            if (ok && catalog != null) {
                lastCatalog = catalog;
                lastSrc = msg != null ? msg : "";
            }
            refresh();
        });
    }

    private int rankOf(HotCatalog.Entry e, String q) {
        String name = e.name != null ? e.name.toLowerCase() : "";
        String id = e.id.toLowerCase();
        if (name.startsWith(q) || id.startsWith(q)) return 0;
        if (name.contains(q) || id.contains(q)) return 1;
        String d = e.description != null ? e.description.toLowerCase() : "";
        if (d.contains(q)) return 2;
        return 3;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        Context context = getContext();
        if (context == null) return;

        if (loading || lastCatalog == null) {
            items.add(UItem.asHeader(MiogramLocale.get("Магазин модулів", "Магазин модулей", "Module store")));
            for (int i = 0; i < 3; i++) items.add(UItem.asCustom(skeletonCard(context)));
            return;
        }

        List<HotCatalog.Entry> visible = new ArrayList<>();
        List<HotCatalog.Entry> featured = new ArrayList<>();
        int installedCount = 0;
        for (HotCatalog.Entry e : lastCatalog.modules) {
            if (HotModulesManager.isModuleInstalled(e.id)) {
                installedCount++;
                continue;
            }
            String cat = e.category != null && !e.category.isEmpty()
                    ? e.category : HotModuleMeta.category(e.id);
            if (!activeCat.isEmpty() && !activeCat.equals(cat)) continue;
            if (!query.isEmpty() && rankOf(e, query) > 2) continue;
            visible.add(e);
            if (e.featured && query.isEmpty() && activeCat.isEmpty()) featured.add(e);
        }
        if (!query.isEmpty()) {
            final String q = query;
            visible.sort((a, b) -> {
                int r = Integer.compare(rankOf(a, q), rankOf(b, q));
                if (r != 0) return r;
                return a.name.compareTo(b.name);
            });
        }

        String src = "cached".equals(lastSrc)
                ? MiogramLocale.get(" • кеш", " • кэш", " • cached")
                : "asset".equals(lastSrc)
                ? MiogramLocale.get(" • офлайн", " • офлайн", " • offline") : "";
        items.add(UItem.asHeader(MiogramLocale.get("Доступно: ", "Доступно: ", "Available: ")
                + visible.size()
                + MiogramLocale.get(" • встановлено: ", " • установлено: ", " • installed: ")
                + installedCount + src));

        if (installedCount == 0) {
            items.add(UItem.asCustom(buildOnboardBanner(context)));
            for (HotModuleMeta.Pack pack : HotModuleMeta.packs()) {
                items.add(UItem.asCustom(buildPackCard(context, pack)));
            }
        }

        items.add(UItem.asCustom(buildCatsRow(context)));

        if (featured.size() >= 2) {
            items.add(UItem.asCustom(buildFeaturedRow(context, featured)));
        }

        if (visible.isEmpty()) {
            items.add(UItem.asCustom(emptyRow(context)));
        } else {
            for (HotCatalog.Entry e : visible) {
                items.add(UItem.asCustom(buildCard(context, e)));
            }
        }

        items.add(UItem.asShadow(MiogramLocale.get(
                "Встановлені модулі приховані — керуйте ними у списку «Хот-модулі».",
                "Установленные модули скрыты — управляйте ими в списке «Хот-модули».",
                "Installed modules are hidden — manage them in “Hot modules”.")));
    }

    private View skeletonCard(Context context) {
        LinearLayout sk = new LinearLayout(context);
        sk.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        sk.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16),
                AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        sk.setAlpha(0.45f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        sk.setLayoutParams(lp);
        TextView t = new TextView(context);
        t.setText("▓▓▓▓▓▓▓▓▓▓▓▓\n▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sk.addView(t);
        return sk;
    }

    private View cardWrap(Context context, View card) {
        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(AndroidUtilities.dp(14), AndroidUtilities.dp(5), AndroidUtilities.dp(14), AndroidUtilities.dp(5));
        card.setLayoutParams(lp);
        wrap.addView(card);
        return wrap;
    }

    private View buildOnboardBanner(Context context) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6C63FF, 0xFF9D7BFF});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        banner.setBackground(bg);
        banner.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        TextView h = new TextView(context);
        h.setText(MiogramLocale.get("Чистий клієнт — 0 модулів",
                "Чистый клиент — 0 модулей", "Clean client — 0 modules"));
        h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        h.setTypeface(AndroidUtilities.bold());
        h.setTextColor(0xFFFFFFFF);
        banner.addView(h);
        TextView p = new TextView(context);
        p.setText(MiogramLocale.get(
                "В APK нічого не вшито. Візьміть готовий пак або оберіть поштучно.",
                "В APK ничего не вшито. Возьмите готовый пак или выберите поштучно.",
                "Nothing is built in. Grab a pack or pick one by one."));
        p.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        p.setTextColor(0xE8FFFFFF);
        banner.addView(p, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        return cardWrap(context, banner);
    }

    private View buildPackCard(Context context, HotModuleMeta.Pack pack) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(YumiTheme.cardBackground(16));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(0xFF6C63FF));
        TextView emoji = new TextView(context);
        emoji.setText(pack.emoji);
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
        emoji.setGravity(Gravity.CENTER);
        iconFrame.addView(emoji, LayoutHelper.createFrame(40, 40, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(MiogramLocale.get("Пак «", "Пак «", "Pack “") + pack.title + "»");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        meta.addView(name);
        TextView desc = new TextView(context);
        desc.setText(pack.desc);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        meta.addView(desc);
        card.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView take = new TextView(context);
        take.setText(MiogramLocale.get("Пак →", "Пак →", "Pack →"));
        take.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        take.setTypeface(AndroidUtilities.bold());
        take.setTextColor(0xFFFFFFFF);
        take.setBackground(YumiTheme.buttonRipple(YumiTheme.getPrimary(), 10));
        take.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(9),
                AndroidUtilities.dp(14), AndroidUtilities.dp(9));
        take.setOnClickListener(v -> {
            take.setEnabled(false);
            installPackSequentially(pack, 0, this::refresh);
        });
        card.addView(take);
        return cardWrap(context, card);
    }

    private void installPackSequentially(HotModuleMeta.Pack pack, int idx, Runnable done) {
        if (lastCatalog == null || idx >= pack.modules.length) {
            if (done != null) done.run();
            return;
        }
        String id = pack.modules[idx];
        Runnable next = () -> installPackSequentially(pack, idx + 1, done);
        if (HotModulesManager.isModuleInstalled(id)) {
            next.run();
            return;
        }
        HotCatalog.Entry e = lastCatalog.find(id);
        HotCatalog.Build def = e != null ? e.latestCompatible(HotModulesManager.appVersion()) : null;
        if (def == null && e != null) def = e.defaultBuild();
        if (e == null || def == null || !HotModulesManager.isCompatible(def)) {
            next.run();
            return;
        }
        Context context = getContext();
        HotModulesManager.downloadBuild(id, def, true,
                new HotModulesManager.ProgressCallback<Void>() {
                    @Override public void onProgress(long d, long t) { }
                    @Override public void onDone(boolean ok, String message, Void data) {
                        if (!ok && context != null) {
                            try {
                                android.widget.Toast.makeText(context, id + ": " + message,
                                        android.widget.Toast.LENGTH_LONG).show();
                            } catch (Throwable ignore) {
                            }
                        }
                        next.run();
                    }
                });
    }

    private View buildCatsRow(Context context) {
        LinearLayout outer = new LinearLayout(context);
        outer.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4),
                AndroidUtilities.dp(0), AndroidUtilities.dp(4));
        HorizontalScrollView hv = new HorizontalScrollView(context);
        hv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<String> cats = new ArrayList<>();
        cats.add("");
        if (lastCatalog != null) {
            for (String c : lastCatalog.categories()) {
                if (!cats.contains(c)) cats.add(c);
            }
        }
        for (String c : cats) {
            TextView chip = new TextView(context);
            boolean on = activeCat.equals(c);
            chip.setText(c.isEmpty()
                    ? MiogramLocale.get("Всі", "Все", "All")
                    : HotModuleMeta.categoryTitle(c));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setTextColor(on ? 0xFFFFFFFF
                    : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(99));
            if (on) bg.setColor(YumiTheme.getPrimary());
            else bg.setColor(Theme.isCurrentThemeDark() ? 0x22FFFFFF : 0x14000000);
            chip.setBackground(bg);
            chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            final String cc = c;
            chip.setOnClickListener(v -> {
                activeCat = cc;
                refresh();
            });
            row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        hv.addView(row);
        outer.addView(hv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT));
        return outer;
    }

    private View buildFeaturedRow(Context context, List<HotCatalog.Entry> list) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4),
                AndroidUtilities.dp(0), AndroidUtilities.dp(4));
        TextView h = new TextView(context);
        h.setText(MiogramLocale.get("⭐ Вибір редакції", "⭐ Выбор редакции", "⭐ Featured"));
        h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        h.setTypeface(AndroidUtilities.bold());
        h.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        col.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        HorizontalScrollView hv = new HorizontalScrollView(context);
        hv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (HotCatalog.Entry e : list) {
            row.addView(buildFeaturedCard(context, e));
        }
        hv.addView(row);
        col.addView(hv);
        return col;
    }

    private View buildFeaturedCard(Context context, HotCatalog.Entry e) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(YumiTheme.cardBackground(16));
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12),
                AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(HotModuleMeta.color(e.id)));
        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(HotModuleMeta.icon(e.id));
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        card.addView(iconFrame, LayoutHelper.createLinear(52, 52, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(e.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        meta.addView(name);
        TextView desc = new TextView(context);
        String d = e.description != null && !e.description.isEmpty()
                ? e.description : HotModuleMeta.fallbackDescription(e.id);
        desc.setText(d.length() > 60 ? d.substring(0, 60) + "…" : d);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        desc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        desc.setMaxLines(2);
        meta.addView(desc);
        card.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        HotCatalog.Build def = e.defaultBuild();
        card.setOnClickListener(v -> {
            Context c = getContext();
            if (c == null) return;
            new HotModuleDetailSheet(c, e.id, e, def, this::refresh).show();
        });

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(card, LayoutHelper.createLinear(AndroidUtilities.dp(280),
                LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));
        return wrap;
    }

    private View emptyRow(Context context) {
        TextView v = new TextView(context);
        v.setText(!query.isEmpty() || !activeCat.isEmpty()
                ? MiogramLocale.get("Нічого не знайдено", "Ничего не найдено", "Nothing found")
                : "✅ " + MiogramLocale.get("Все встановлено", "Всё установлено", "All installed"));
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, AndroidUtilities.dp(26), 0, AndroidUtilities.dp(26));
        return v;
    }

    private View buildCard(Context context, HotCatalog.Entry entry) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(HotModuleMeta.color(entry.id)));
        ImageView icon = new ImageView(context);
        try {
            icon.setImageResource(HotModuleMeta.icon(entry.id));
        } catch (Throwable ignore) {
        }
        icon.setColorFilter(0xFFFFFFFF);
        iconFrame.addView(icon, LayoutHelper.createFrame(24, 24, Gravity.CENTER));
        top.addView(iconFrame, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(context);
        name.setText(entry.name != null && !entry.name.isEmpty() ? entry.name : entry.id);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        name.setMaxLines(1);
        titleRow.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        HotCatalog.Build def = HotModulesManager.appVersion() > 0
                ? entry.latestCompatible(HotModulesManager.appVersion()) : entry.defaultBuild();
        if (def == null) def = entry.defaultBuild();
        if (def != null) {
            TextView badge = new TextView(context);
            badge.setText("v" + def.version);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(99));
            badgeBg.setColor(Theme.isCurrentThemeDark() ? 0x22FFFFFF : 0x14000000);
            badge.setBackground(badgeBg);
            badge.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(3),
                    AndroidUtilities.dp(8), AndroidUtilities.dp(3));
            titleRow.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
        }
        meta.addView(titleRow);

        String desc = entry.description != null && !entry.description.isEmpty()
                ? entry.description : HotModuleMeta.fallbackDescription(entry.id);
        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        descView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        descView.setMaxLines(2);
        descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        top.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(top);

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        TextView ver = new TextView(context);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        ver.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        String catName = HotModuleMeta.categoryTitle(
                entry.category != null && !entry.category.isEmpty()
                        ? entry.category : HotModuleMeta.category(entry.id));
        ver.setText(def != null ? (def.branch + " • " + catName) : catName);
        bottom.addView(ver, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView action = new TextView(context);
        final HotCatalog.Build d = def;
        final boolean compat = d == null || HotModulesManager.isCompatible(d);
        action.setText(!compat ? "minApp " + d.minApp
                : MiogramLocale.get("↓ Взяти", "↓ Взять", "↓ Get"));
        action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        action.setTypeface(AndroidUtilities.bold());
        action.setTextColor(0xFFFFFFFF);
        action.setGravity(Gravity.CENTER);
        action.setBackground(YumiTheme.buttonRipple(YumiTheme.getPrimary(), 12));
        action.setAlpha(compat ? 1f : 0.5f);
        action.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9),
                AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        action.setOnClickListener(v -> {
            if (d == null || !compat) {
                try {
                    android.widget.Toast.makeText(context,
                            MiogramLocale.get("Потрібен новіший AmeGram", "Нужен новее AmeGram", "Requires newer AmeGram"),
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
                return;
            }
            action.setEnabled(false);
            action.setText("…");
            HotModulesManager.downloadBuild(entry.id, d, true,
                    new HotModulesManager.ProgressCallback<Void>() {
                        @Override
                        public void onProgress(long downloaded, long total) {
                        }

                        @Override
                        public void onDone(boolean ok, String message, Void data) {
                            try {
                                android.widget.Toast.makeText(context,
                                        ok ? "✓ " + entry.id + " v" + message : message,
                                        android.widget.Toast.LENGTH_LONG).show();
                            } catch (Throwable ignore) {
                            }
                            refresh();
                        }
                    });
        });
        bottom.addView(action);
        card.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        final HotCatalog.Build dd = def;
        card.setOnClickListener(v -> {
            Context c = getContext();
            if (c == null) return;
            new HotModuleDetailSheet(c, entry.id, entry, dd, this::refresh).show();
        });
        return cardWrap(context, card);
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        // Кліки живуть всередині карток.
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
