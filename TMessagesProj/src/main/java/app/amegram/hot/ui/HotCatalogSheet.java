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
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Магазин модулів (Market + Minimal, нативно):
 * - 0 вбудованих: онбординг з паками (установка набором в 1 тап);
 * - featured-карусель, фільтр за категоріями, ранжований пошук;
 * - картка: іконка • назва • опис • кнопка Взяти;
 * - встановлених у магазині НЕМА; тап по картці → деталка.
 */
public class HotCatalogSheet extends BottomSheet {

    public interface OnChanged {
        void onChanged();
    }

    private final LinearLayout itemsContainer;
    private final LinearLayout catsRow;
    private final LinearLayout featuredBox;
    private final TextView statusView;
    private final EditText searchView;
    private final LinearLayout onboardBanner;
    private final LinearLayout packsBox;
    private final OnChanged onChanged;

    private HotCatalog lastCatalog;
    private String lastSrc = "";
    private String query = "";
    private String activeCat = "";
    private boolean loading = true;

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
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setCornerRadii(new float[]{
                AndroidUtilities.dp(24), AndroidUtilities.dp(24),
                AndroidUtilities.dp(24), AndroidUtilities.dp(24), 0, 0, 0, 0});
        rootBg.setColor(bg);
        root.setBackground(rootBg);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8),
                AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        View grab = new View(context);
        GradientDrawable grabBg = new GradientDrawable();
        grabBg.setCornerRadius(AndroidUtilities.dp(99));
        grabBg.setColor(0x33FFFFFF);
        grab.setBackground(grabBg);
        root.addView(grab, LayoutHelper.createLinear(40, 4, Gravity.CENTER_HORIZONTAL, 0, 4, 0, 12));

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Магазин модулів", "Магазин модулей", "Module store"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(text);
        titleRow.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView refresh = new TextView(context);
        refresh.setText("⟳");
        refresh.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        refresh.setTextColor(sub);
        refresh.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4),
                AndroidUtilities.dp(4), AndroidUtilities.dp(4));
        refresh.setOnClickListener(v -> load(true));
        titleRow.addView(refresh);
        root.addView(titleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        statusView = new TextView(context);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        statusView.setTextColor(sub);
        root.addView(statusView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        onboardBanner = buildOnboardBanner(context);
        onboardBanner.setVisibility(View.GONE);
        root.addView(onboardBanner, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        packsBox = new LinearLayout(context);
        packsBox.setOrientation(LinearLayout.VERTICAL);
        packsBox.setVisibility(View.GONE);
        root.addView(packsBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        searchView = new EditText(context);
        searchView.setHint(MiogramLocale.get("Пошук модулів…", "Поиск модулей…", "Search modules…"));
        searchView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        searchView.setTextColor(text);
        searchView.setHintTextColor(sub);
        searchView.setSingleLine(true);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setCornerRadius(AndroidUtilities.dp(12));
        searchBg.setColor(themed(Theme.key_windowBackgroundGray, 0xFF1E1F22));
        searchView.setBackground(searchBg);
        searchView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(11),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));
        searchView.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) {
                query = s != null ? s.toString().trim().toLowerCase() : "";
                render();
            }
        });
        root.addView(searchView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 6, 0, 8));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout scrollBody = new LinearLayout(context);
        scrollBody.setOrientation(LinearLayout.VERTICAL);
        catsRow = new LinearLayout(context);
        catsRow.setOrientation(LinearLayout.HORIZONTAL);
        HorizontalScrollView catsScroll = new HorizontalScrollView(context);
        catsScroll.setHorizontalScrollBarEnabled(false);
        catsScroll.addView(catsRow);
        scrollBody.addView(catsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        featuredBox = new LinearLayout(context);
        featuredBox.setOrientation(LinearLayout.VERTICAL);
        scrollBody.addView(featuredBox);
        itemsContainer = new LinearLayout(context);
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        scrollBody.addView(itemsContainer);
        scroll.addView(scrollBody, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP));
        root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        setCustomView(root);
        showSkeletons();
        load(true);
    }

    private int themed(int key, int fallback) {
        int c = getThemedColor(key);
        return c == 0 ? fallback : c;
    }

    private void showSkeletons() {
        Context context = getContext();
        if (context == null) return;
        loading = true;
        itemsContainer.removeAllViews();
        for (int i = 0; i < 3; i++) {
            LinearLayout sk = new LinearLayout(context);
            sk.setBackground(YumiTheme.cardBackground(18));
            sk.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(16),
                    AndroidUtilities.dp(14), AndroidUtilities.dp(16));
            sk.setAlpha(0.45f);
            TextView t = new TextView(context);
            t.setText("▓▓▓▓▓▓▓▓▓▓▓▓▓\n▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓");
            t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            t.setTextColor(themed(Theme.key_dialogTextGray3, 0xFF8E8E93));
            sk.addView(t);
            itemsContainer.addView(sk, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        }
    }

    private LinearLayout buildOnboardBanner(Context context) {
        LinearLayout banner = new LinearLayout(context);
        banner.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6C63FF, 0xFF9D7BFF});
        bg.setCornerRadius(AndroidUtilities.dp(18));
        banner.setBackground(bg);
        banner.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14),
                AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        TextView h = new TextView(context);
        h.setText(MiogramLocale.get("👋 Чистий клієнт — 0 модулів",
                "👋 Чистый клиент — 0 модулей", "👋 Clean client — 0 modules"));
        h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        h.setTypeface(AndroidUtilities.bold());
        h.setTextColor(0xFFFFFFFF);
        banner.addView(h);

        TextView p = new TextView(context);
        p.setText(MiogramLocale.get(
                "В APK нічого не вшито. Візьміть готовий пак або оберіть поштучно — все ставиться як .hmod в 1 тап.",
                "В APK ничего не вшито. Возьмите готовый пак или выберите поштучно — всё ставится как .hmod в 1 тап.",
                "Nothing is built in. Grab a pack or pick one by one — everything installs as .hmod in 1 tap."));
        p.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        p.setTextColor(0xE8FFFFFF);
        banner.addView(p, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        return banner;
    }

    private void renderPacks() {
        Context context = getContext();
        if (context == null) return;
        packsBox.removeAllViews();
        boolean show = onboardBanner.getVisibility() == View.VISIBLE;
        packsBox.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        for (HotModuleMeta.Pack pack : HotModuleMeta.packs()) {
            packsBox.addView(buildPackCard(context, pack));
        }
    }

    private View buildPackCard(Context context, HotModuleMeta.Pack pack) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(YumiTheme.cardBackground(16));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12),
                AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        TextView emoji = new TextView(context);
        emoji.setText(pack.emoji);
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 26);
        card.addView(emoji, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 12, 0));

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(context);
        name.setText(MiogramLocale.get("Пак «", "Пак «", "Pack “") + pack.title + "»");
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(text);
        meta.addView(name);
        TextView desc = new TextView(context);
        desc.setText(pack.desc);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        desc.setTextColor(sub);
        meta.addView(desc);
        card.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView take = new TextView(context);
        take.setText(MiogramLocale.get("Пак →", "Пак →", "Pack →"));
        take.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        take.setTypeface(AndroidUtilities.bold());
        take.setTextColor(0xFFFFFFFF);
        take.setBackground(YumiTheme.buttonRipple(accent, 10));
        take.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(9),
                AndroidUtilities.dp(14), AndroidUtilities.dp(9));
        take.setOnClickListener(v -> {
            take.setEnabled(false);
            installPackSequentially(pack, 0, () -> {
                take.setEnabled(true);
                if (onChanged != null) onChanged.onChanged();
                render();
            });
        });
        card.addView(take);

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return wrap;
    }

    /** Установка пака по черзі: пропускаємо вже встановлені та несумісні. */
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
        HotModulesManager.downloadBuild(id, def, true,
                new HotModulesManager.ProgressCallback<Void>() {
                    @Override public void onProgress(long d, long t) { }
                    @Override public void onDone(boolean ok, String message, Void data) {
                        next.run();
                    }
                });
    }

    private void load(boolean force) {
        statusView.setText(MiogramLocale.get("Завантаження…", "Загрузка…", "Loading…"));
        showSkeletons();
        HotModulesManager.fetchCatalog(force, (ok, msg, catalog) -> {
            loading = false;
            if (!ok || catalog == null) {
                itemsContainer.removeAllViews();
                statusView.setText(MiogramLocale.get("Не вдалося завантажити каталог",
                        "Не удалось загрузить каталог", "Catalog load failed")
                        + (msg != null && !msg.isEmpty() ? ": " + msg : ""));
                return;
            }
            lastCatalog = catalog;
            lastSrc = msg != null ? msg : "";
            render();
        });
    }

    /** Ранжування пошуку: збіг з початку назви/id вище за збіг всередині. */
    private int rankOf(HotCatalog.Entry e, String q) {
        String name = e.name != null ? e.name.toLowerCase() : "";
        String id = e.id.toLowerCase();
        if (name.startsWith(q) || id.startsWith(q)) return 0;
        if (name.contains(q) || id.contains(q)) return 1;
        String d = e.description != null ? e.description.toLowerCase() : "";
        if (d.contains(q)) return 2;
        return 3;
    }

    private void render() {
        Context context = getContext();
        if (context == null || lastCatalog == null || loading) return;
        itemsContainer.removeAllViews();
        featuredBox.removeAllViews();

        String src = "cached".equals(lastSrc)
                ? MiogramLocale.get(" • кеш", " • кэш", " • cached")
                : "asset".equals(lastSrc)
                ? MiogramLocale.get(" • офлайн-копія", " • офлайн-копия", " • offline") : "";

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

        onboardBanner.setVisibility(installedCount == 0 ? View.VISIBLE : View.GONE);
        renderPacks();
        renderCats(context);
        if (!featured.isEmpty() && featured.size() >= 2) {
            featuredBox.addView(buildFeaturedRow(context, featured));
        }

        statusView.setText(MiogramLocale.get("Доступно: ", "Доступно: ", "Available: ")
                + visible.size()
                + MiogramLocale.get(" • встановлено: ", " • установлено: ", " • installed: ")
                + installedCount + src);

        if (visible.isEmpty()) {
            itemsContainer.addView(emptyRow(context));
            return;
        }
        for (HotCatalog.Entry entry : visible) {
            itemsContainer.addView(buildCard(context, entry));
        }
        TextView hint = new TextView(context);
        hint.setText(MiogramLocale.get(
                "Встановлені модулі приховані — керуйте ними у списку «Хот-модулі».",
                "Установленные модули скрыты — управляйте ими в списке «Хот-модули».",
                "Installed modules are hidden — manage them in “Hot modules”."));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        hint.setTextColor(themed(Theme.key_dialogTextGray3, 0xFF8E8E93));
        hint.setGravity(Gravity.CENTER);
        itemsContainer.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 4));
    }

    private void renderCats(Context context) {
        catsRow.removeAllViews();
        if (lastCatalog == null) return;
        List<String> cats = new ArrayList<>();
        cats.add("");
        for (String c : lastCatalog.categories()) {
            if (!cats.contains(c)) cats.add(c);
        }
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        for (String c : cats) {
            TextView chip = new TextView(context);
            boolean on = activeCat.equals(c);
            chip.setText(c.isEmpty()
                    ? MiogramLocale.get("Всі", "Все", "All")
                    : HotModuleMeta.categoryTitle(c));
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            chip.setTypeface(AndroidUtilities.bold());
            chip.setTextColor(on ? 0xFFFFFFFF : sub);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(AndroidUtilities.dp(99));
            if (on) bg.setColor(accent);
            else {
                bg.setStroke(AndroidUtilities.dp(1), 0x44FFFFFF);
                bg.setColor(0x00000000);
            }
            chip.setBackground(bg);
            chip.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8),
                    AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            final String cc = c;
            chip.setOnClickListener(v -> {
                activeCat = cc;
                render();
            });
            catsRow.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
    }

    private View buildFeaturedRow(Context context, List<HotCatalog.Entry> list) {
        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView h = new TextView(context);
        h.setText(MiogramLocale.get("⭐ Вибір редакції", "⭐ Выбор редакции", "⭐ Featured"));
        h.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        h.setTypeface(AndroidUtilities.bold());
        h.setTextColor(themed(Theme.key_dialogTextBlack, 0xFFFFFFFF));
        col.addView(h, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        HorizontalScrollView hv = new HorizontalScrollView(context);
        hv.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (HotCatalog.Entry e : list) {
            row.addView(buildFeaturedCard(context, e));
        }
        hv.addView(row);
        col.addView(hv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        return col;
    }

    private View buildFeaturedCard(Context context, HotCatalog.Entry e) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{HotModuleMeta.color(e.id), 0xFF2A2A3A});
        bg.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(bg);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14),
                AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        card.setMinimumWidth(AndroidUtilities.dp(190));

        TextView emoji = new TextView(context);
        emoji.setText(featuredEmoji(e.id));
        emoji.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 30);
        card.addView(emoji);
        TextView name = new TextView(context);
        name.setText(e.name);
        name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(0xFFFFFFFF);
        card.addView(name, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        TextView desc = new TextView(context);
        String d = e.description != null && !e.description.isEmpty()
                ? e.description : HotModuleMeta.fallbackDescription(e.id);
        desc.setText(d.length() > 70 ? d.substring(0, 70) + "…" : d);
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        desc.setTextColor(0xCCFFFFFF);
        desc.setMaxLines(2);
        card.addView(desc);

        HotCatalog.Build def = e.defaultBuild();
        card.setOnClickListener(v -> new HotModuleDetailSheet(context, e.id, e, def,
                () -> {
                    if (onChanged != null) onChanged.onChanged();
                    render();
                }).show());

        LinearLayout wrap = new LinearLayout(context);
        wrap.addView(card, LayoutHelper.createLinear(AndroidUtilities.dp(190),
                LayoutHelper.WRAP_CONTENT, 0, 0, 10, 0));
        return wrap;
    }

    private static String featuredEmoji(String id) {
        if ("ghost".equals(id)) return "👻";
        if ("player".equals(id)) return "🎵";
        if ("vault".equals(id)) return "🔐";
        if ("ai".equals(id)) return "✨";
        if ("tiktok".equals(id)) return "🎬";
        if ("ame".equals(id)) return "💎";
        if ("stt".equals(id)) return "🎙";
        if ("experimental".equals(id)) return "🧪";
        if ("automation".equals(id)) return "⚙";
        return "🧩";
    }

    private View emptyRow(Context context) {
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        TextView v = new TextView(context);
        boolean allInstalled = lastCatalog != null && !lastCatalog.modules.isEmpty();
        v.setText(!query.isEmpty() || !activeCat.isEmpty()
                ? MiogramLocale.get("Нічого не знайдено", "Ничего не найдено", "Nothing found")
                : allInstalled
                ? "✅ " + MiogramLocale.get("Все встановлено", "Всё установлено", "All installed")
                : MiogramLocale.get("Каталог порожній", "Каталог пуст", "Catalog is empty"));
        v.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        v.setTextColor(sub);
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, AndroidUtilities.dp(26), 0, AndroidUtilities.dp(26));
        return v;
    }

    private View buildCard(Context context, HotCatalog.Entry entry) {
        int text = themed(Theme.key_dialogTextBlack, 0xFFFFFFFF);
        int sub = themed(Theme.key_dialogTextGray3, 0xFF8E8E93);
        int accent = themed(Theme.key_featuredStickers_addButton, 0xFF6C63FF);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(YumiTheme.cardBackground(18));
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(13),
                AndroidUtilities.dp(14), AndroidUtilities.dp(11));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        int modColor = HotModuleMeta.color(entry.id);
        FrameLayout iconFrame = new FrameLayout(context);
        iconFrame.setBackground(YumiTheme.squircleIconBackground(modColor));
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
        name.setTextColor(text);
        name.setMaxLines(1);
        titleRow.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        HotCatalog.Build def = entry.defaultBuild();
        if (def != null) {
            TextView badge = new TextView(context);
            badge.setText("v" + def.version);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setTextColor(sub);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(99));
            badgeBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
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
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        descView.setTextColor(sub);
        descView.setMaxLines(2);
        descView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        meta.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

        top.addView(meta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(top);

        LinearLayout bottom = new LinearLayout(context);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setVisibility(View.GONE);
        bottom.addView(progress, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 8, 0, 0));

        TextView ver = new TextView(context);
        ver.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        ver.setTextColor(sub);
        String catName = HotModuleMeta.categoryTitle(
                entry.category != null && !entry.category.isEmpty()
                        ? entry.category : HotModuleMeta.category(entry.id));
        ver.setText(def != null ? (def.branch + " • " + catName) : catName);
        bottom.addView(ver, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        TextView action = new TextView(context);
        action.setText(MiogramLocale.get("↓ Взяти", "↓ Взять", "↓ Get"));
        action.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        action.setTypeface(AndroidUtilities.bold());
        action.setTextColor(0xFFFFFFFF);
        action.setGravity(Gravity.CENTER);
        action.setBackground(YumiTheme.buttonRipple(accent, 12));
        action.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(9),
                AndroidUtilities.dp(18), AndroidUtilities.dp(9));
        action.setOnClickListener(v -> {
            if (def == null) return;
            if (!HotModulesManager.isCompatible(def)) {
                try {
                    android.widget.Toast.makeText(context,
                            "minApp " + def.minApp, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
                return;
            }
            action.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(0);
            HotModulesManager.downloadBuild(entry.id, def, true,
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
                                if (onChanged != null) onChanged.onChanged();
                                render();
                                try {
                                    android.widget.Toast.makeText(context,
                                            "✓ " + entry.id + " v" + message,
                                            android.widget.Toast.LENGTH_SHORT).show();
                                } catch (Throwable ignore) {
                                }
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
        bottom.addView(action);

        card.addView(bottom, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        card.setOnClickListener(v -> {
            HotModuleDetailSheet sheet = new HotModuleDetailSheet(context,
                    entry.id, entry, def, () -> {
                        if (onChanged != null) onChanged.onChanged();
                        render();
                    });
            sheet.show();
        });

        LinearLayout wrap = new LinearLayout(context);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        return wrap;
    }
}
