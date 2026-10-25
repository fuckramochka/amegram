package app.amegram.settings.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LayoutHelper;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import app.amegram.theme.YumiMd3;
import app.amegram.theme.YumiSwitch;
import app.miogram.bridge.MiogramLocale;

/**
 * The Yumi settings surface.
 *
 * <p>A deliberately bespoke Material 3 Expressive composition: animated tonal
 * aurora backdrop, a branded hero, quick-control tiles, grouped tonal cards,
 * custom spring switches, a dedicated search state and a real button group.</p>
 */
public class YumiSettingsView extends FrameLayout {

    public interface ModelProvider {
        YumiSettingsModel buildModel();
    }

    public interface Listener {
        void onBack();

        void onRow(YumiSettingsModel.Row row);

        void onToggle(YumiSettingsModel.Row row, boolean checked);

        void onQuick(YumiSettingsModel.Quick quick);

        void onQuickToggle(YumiSettingsModel.Quick quick, boolean checked);

        void onFooterAction(YumiSettingsModel.Action action);
    }

    private static final int TYPE_HERO = 1;
    private static final int TYPE_SEARCH = 2;
    private static final int TYPE_HEADER = 3;
    private static final int TYPE_QUICK = 4;
    private static final int TYPE_GROUP = 5;
    private static final int TYPE_EMPTY = 6;
    private static final int TYPE_ACTIONS = 7;
    private static final int TYPE_FOOTER = 8;

    private static final int TOP_BAR_CONTENT_HEIGHT = 60;

    private final Context context;
    private final ModelProvider provider;
    private final Listener listener;
    private final RecyclerView recyclerView;
    private final LinearLayoutManager layoutManager;
    private final Adapter adapter;
    private final TopBar topBar;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private WeakReference<EditText> searchReference = new WeakReference<>(null);

    private YumiSettingsModel model;
    private Object boundHero;
    private String query = "";
    private boolean firstLayout = true;

    private final int statusBar;
    private final int topBarHeight;

    public YumiSettingsView(Context context, ModelProvider provider, Listener listener) {
        super(context);
        this.context = context;
        this.provider = provider;
        this.listener = listener;

        setClipChildren(false);
        setClipToPadding(false);
        setBackgroundColor(YumiMd3.surface());

        statusBar = Math.max(0, AndroidUtilities.getStatusBarHeight(context));
        topBarHeight = statusBar + YumiMd3.dp(TOP_BAR_CONTENT_HEIGHT);

        YumiBackgroundView background = new YumiBackgroundView(context);
        addView(background, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        recyclerView = new RecyclerView(context);
        recyclerView.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        recyclerView.setClipToPadding(false);
        recyclerView.setOverScrollMode(OVER_SCROLL_NEVER);
        recyclerView.setVerticalScrollBarEnabled(false);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        layoutManager = new LinearLayoutManager(context, RecyclerView.VERTICAL, false);
        recyclerView.setLayoutManager(layoutManager);
        adapter = new Adapter();
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateTopBar();
            }
        });
        addView(recyclerView);

        topBar = new TopBar(context);
        FrameLayout.LayoutParams topParams = new FrameLayout.LayoutParams(
                LayoutHelper.MATCH_PARENT, topBarHeight, Gravity.TOP);
        topBar.setLayoutParams(topParams);
        topBar.setClickable(true);
        topBar.setOnClickListener(v -> {
        });
        addView(topBar);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRecyclerPadding(w);
    }

    @SuppressWarnings("deprecation")
    private void updateRecyclerPadding(int width) {
        int side = Math.max(0, (width - YumiMd3.dp(620)) / 2);
        int top = topBarHeight + YumiMd3.dp(4);
        int bottom = YumiMd3.dp(28) + Math.max(0, AndroidUtilities.navigationBarHeight);
        if (recyclerView.getPaddingLeft() != side || recyclerView.getPaddingTop() != top
                || recyclerView.getPaddingRight() != side || recyclerView.getPaddingBottom() != bottom) {
            recyclerView.setPadding(side, top, side, bottom);
        }
        topBar.setContentInset(side);
    }

    public void setPageTitle(CharSequence title) {
        topBar.setTitle(title);
    }

    public void refresh() {
        if (provider == null) {
            return;
        }
        YumiSettingsModel next = provider.buildModel();
        if (next == null) {
            return;
        }
        boolean wasEmpty = model == null;
        model = next;
        topBar.setTitle(next.pageTitle);

        List<Item> items = buildItems();
        if (wasEmpty) {
            adapter.items = items;
            adapter.notifyDataSetChanged();
            if (firstLayout) {
                firstLayout = false;
                post(this::playEntrance);
            }
        } else {
            int position = layoutManager.findFirstVisibleItemPosition();
            View first = position == RecyclerView.NO_POSITION ? null : layoutManager.findViewByPosition(position);
            int offset = first == null ? 0 : first.getTop() - recyclerView.getPaddingTop();
            applyItems(items, false);
            if (position != RecyclerView.NO_POSITION) {
                layoutManager.scrollToPositionWithOffset(Math.min(position, Math.max(0, items.size() - 1)), offset);
            }
        }
        updateTopBar();
    }

    public void focusSearch() {
        View search = searchReference.get();
        if (search != null && search.isAttachedToWindow()) {
            recyclerView.smoothScrollToPosition(1);
            search.postDelayed(() -> openSearch(search), 180);
            return;
        }
        recyclerView.scrollToPosition(1);
        recyclerView.postDelayed(() -> {
            EditText field = searchReference.get();
            if (field != null) {
                openSearch(field);
            }
        }, 80);
    }

    private void openSearch(View search) {
        EditText field = searchReference.get();
        if (field == null) {
            return;
        }
        field.requestFocus();
        field.setSelection(field.length());
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void clearSearch() {
        EditText field = searchReference.get();
        if (field != null) {
            field.setText("");
            field.requestFocus();
        } else {
            query = "";
            applyItems(buildItems(), true);
        }
    }

    private void onQueryChanged(String value) {
        String next = value == null ? "" : value.trim();
        if (next.equals(query)) {
            return;
        }
        query = next;
        searchHandler.removeCallbacksAndMessages(null);
        searchHandler.postDelayed(() -> {
            if (!isAttachedToWindow()) {
                return;
            }
            applyItems(buildItems(), true);
        }, 90);
    }

    private boolean hasQuery() {
        return query != null && !query.isEmpty();
    }

    private List<Item> buildItems() {
        List<Item> items = new ArrayList<>();
        if (model == null) {
            return items;
        }
        items.add(new Item(TYPE_HERO, model.hero));
        items.add(new Item(TYPE_SEARCH, null));

        boolean searching = hasQuery();
        if (searching) {
            List<YumiSettingsModel.Row> found = searchRows();
            if (found.isEmpty()) {
                items.add(new Item(TYPE_EMPTY, null));
            } else {
                YumiSettingsModel.Section result = new YumiSettingsModel.Section();
                result.title = MiogramLocale.get("Результати", "Результаты", "Results");
                result.subtitle = found.size() + " " + MiogramLocale.plural(found.size(),
                        "збіг", "збіги", "збігів",
                        "совпадение", "совпадения", "совпадений",
                        "match", "matches");
                result.rows.addAll(found);
                items.add(new Item(TYPE_HEADER, result));
                items.add(new Item(TYPE_GROUP, result));
            }
        } else {
            if (!model.quick.isEmpty()) {
                YumiSettingsModel.Section quickSection = new YumiSettingsModel.Section();
                quickSection.title = MiogramLocale.get("Швидке керування", "Быстрое управление", "Quick controls");
                quickSection.subtitle = MiogramLocale.get(
                        "Один дотик — і все як треба", "Одно касание — и всё как надо", "One tap, exactly your way");
                items.add(new Item(TYPE_HEADER, quickSection));
                items.add(new Item(TYPE_QUICK, model.quick));
            }
            for (YumiSettingsModel.Section section : model.sections) {
                if (section.rows.isEmpty()) {
                    continue;
                }
                items.add(new Item(TYPE_HEADER, section));
                items.add(new Item(TYPE_GROUP, section));
            }
            if (model.footerPrimary != null || model.footerSecondary != null) {
                items.add(new Item(TYPE_ACTIONS, null));
            }
        }
        items.add(new Item(TYPE_FOOTER, null));
        return items;
    }

    private List<YumiSettingsModel.Row> searchRows() {
        List<YumiSettingsModel.Row> result = new ArrayList<>();
        if (model == null || query.isEmpty()) {
            return result;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        Set<String> seen = new HashSet<>();
        for (YumiSettingsModel.Quick quick : model.quick) {
            if (containsAny(needle, quick.title, quick.subtitle, quick.moduleId)) {
                YumiSettingsModel.Row row = new YumiSettingsModel.Row();
                row.id = quick.id;
                row.moduleId = quick.moduleId;
                row.icon = quick.icon;
                row.accent = quick.accent;
                row.title = quick.title;
                row.subtitle = quick.subtitle;
                row.kind = quick.toggle ? YumiSettingsModel.KIND_SWITCH : YumiSettingsModel.KIND_NAV;
                row.checked = quick.checked;
                if (seen.add(rowKey(row))) {
                    result.add(row);
                }
            }
        }
        for (YumiSettingsModel.Section section : model.sections) {
            boolean sectionMatches = containsAny(needle, section.title, section.subtitle);
            for (YumiSettingsModel.Row row : section.rows) {
                if (sectionMatches || containsAny(needle, row.title, row.subtitle, row.value, row.moduleId)) {
                    if (seen.add(rowKey(row))) {
                        result.add(row);
                    }
                }
            }
        }
        for (YumiSettingsModel.Row row : model.searchIndex) {
            if (containsAny(needle, row.title, row.subtitle, row.value, row.moduleId)
                    && seen.add(rowKey(row))) {
                result.add(row);
            }
        }
        return result;
    }

    private static String rowKey(YumiSettingsModel.Row row) {
        return row.id + ":" + (row.moduleId == null ? "" : row.moduleId);
    }

    private static boolean containsAny(String needle, CharSequence... values) {
        for (CharSequence value : values) {
            if (value != null && value.toString().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private void applyItems(List<Item> items, boolean keepSearch) {
        if (!keepSearch || adapter.items.size() < 2) {
            adapter.items = items;
            boundHero = items.isEmpty() ? null : items.get(0).payload;
            adapter.notifyDataSetChanged();
            updateTopBar();
            return;
        }

        // Only the tail changes while filtering. Mutating the adapter's own list
        // keeps notifyItemRange* consistent and leaves the search holder (and its
        // focused EditText) attached, so the keyboard never flickers.
        List<Item> current = adapter.items;
        int oldRest = Math.max(0, current.size() - 2);
        if (oldRest > 0) {
            current.subList(2, current.size()).clear();
            adapter.notifyItemRangeRemoved(2, oldRest);
        }
        current.set(0, items.get(0));
        current.set(1, items.get(1));
        int newRest = Math.max(0, items.size() - 2);
        if (newRest > 0) {
            current.addAll(items.subList(2, items.size()));
            adapter.notifyItemRangeInserted(2, newRest);
        }
        if (boundHero != items.get(0).payload) {
            boundHero = items.get(0).payload;
            adapter.notifyItemChanged(0);
        }
        recyclerView.post(() -> layoutManager.scrollToPositionWithOffset(1, 0));
        updateTopBar();
    }

    private void updateTopBar() {
        int scroll = recyclerView.computeVerticalScrollOffset();
        float reveal = Math.min(1f, Math.max(0f, scroll / (float) YumiMd3.dp(28)));
        topBar.setReveal(reveal);
    }

    private void playEntrance() {
        if (recyclerView.getChildCount() == 0) {
            return;
        }
        int count = Math.min(recyclerView.getChildCount(), 7);
        for (int i = 0; i < count; i++) {
            View child = recyclerView.getChildAt(i);
            child.setAlpha(0f);
            child.setTranslationY(YumiMd3.dp(14));
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 35L)
                    .setDuration(360L)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
        }
    }

    private void attachPressScale(View view) {
        final float[] down = new float[2];
        view.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                down[0] = event.getX();
                down[1] = event.getY();
                v.animate().scaleX(0.975f).scaleY(0.975f).setDuration(110).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                if (Math.abs(event.getX() - down[0]) > YumiMd3.dp(8)
                        || Math.abs(event.getY() - down[1]) > YumiMd3.dp(8)) {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
            }
            return false;
        });
    }

    private void haptic(View view) {
        try {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Throwable ignore) {
        }
    }

    private void hideSearchFocus() {
        View focused = findFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }

    private static class Item {
        final int type;
        final Object payload;

        Item(int type, Object payload) {
            this.type = type;
            this.payload = payload;
        }
    }

    // ------------------------------------------------------------------
    // Top bar
    // ------------------------------------------------------------------

    private class TopBar extends FrameLayout {

        private final Paint backgroundPaint = new Paint();
        private final Paint hairlinePaint = new Paint();
        private final RoundIconButton back;
        private final RoundIconButton search;
        private final TextView title;
        private float reveal;

        TopBar(Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);

            back = new RoundIconButton(context, R.drawable.yumi_ic_arrow_back,
                    MiogramLocale.get("Назад", "Назад", "Back"));
            back.setOnClickListener(v -> {
                haptic(v);
                hideSearchFocus();
                listener.onBack();
            });
            FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                    YumiMd3.dp(48), YumiMd3.dp(48), Gravity.TOP | Gravity.START);
            backLp.topMargin = statusBar + YumiMd3.dp(6);
            backLp.setMarginStart(YumiMd3.dp(8));
            addView(back, backLp);

            search = new RoundIconButton(context, R.drawable.yumi_ic_search,
                    MiogramLocale.get("Пошук налаштувань", "Поиск настроек", "Search settings"));
            search.setOnClickListener(v -> {
                haptic(v);
                YumiSettingsView.this.focusSearch();
            });
            FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(
                    YumiMd3.dp(48), YumiMd3.dp(48), Gravity.TOP | Gravity.END);
            searchLp.topMargin = statusBar + YumiMd3.dp(6);
            searchLp.setMarginEnd(YumiMd3.dp(8));
            addView(search, searchLp);

            title = YumiMd3.text(context, "", 20, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT, YumiMd3.dp(48), Gravity.TOP);
            titleLp.topMargin = statusBar + YumiMd3.dp(6);
            titleLp.setMarginStart(YumiMd3.dp(64));
            titleLp.setMarginEnd(YumiMd3.dp(64));
            addView(title, titleLp);

            hairlinePaint.setColor(YumiMd3.hairline());
        }

        void setContentInset(int inset) {
            FrameLayout.LayoutParams backLp = (FrameLayout.LayoutParams) back.getLayoutParams();
            if (backLp.getMarginStart() != YumiMd3.dp(8) + inset) {
                backLp.setMarginStart(YumiMd3.dp(8) + inset);
                back.setLayoutParams(backLp);

                FrameLayout.LayoutParams searchLp = (FrameLayout.LayoutParams) search.getLayoutParams();
                searchLp.setMarginEnd(YumiMd3.dp(8) + inset);
                search.setLayoutParams(searchLp);

                FrameLayout.LayoutParams titleLp = (FrameLayout.LayoutParams) title.getLayoutParams();
                titleLp.setMarginStart(YumiMd3.dp(64) + inset);
                titleLp.setMarginEnd(YumiMd3.dp(64) + inset);
                title.setLayoutParams(titleLp);
            }
        }

        void setTitle(CharSequence value) {
            title.setText(value);
        }

        void setReveal(float value) {
            if (Math.abs(reveal - value) < 0.01f) {
                return;
            }
            reveal = value;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (reveal > 0.001f) {
                backgroundPaint.setColor(YumiMd3.withAlpha(YumiMd3.surfaceContainer(), reveal));
                canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
                hairlinePaint.setColor(YumiMd3.withAlpha(YumiMd3.outline(), 0.18f * reveal));
                canvas.drawRect(0, getHeight() - YumiMd3.dp(1), getWidth(), getHeight(), hairlinePaint);
            }
        }
    }

    private static class RoundIconButton extends FrameLayout {
        RoundIconButton(Context context, int iconRes, CharSequence description) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setContentDescription(description);
            GradientDrawable content = YumiMd3.rounded(
                    YumiMd3.surfaceContainerHigh(),
                    YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.22f : 0.14f), 24, 0.75f);
            GradientDrawable mask = YumiMd3.rounded(0xFFFFFFFF, 24);
            setBackground(new RippleDrawable(
                    ColorStateList.valueOf(YumiMd3.defaultRippleColor()), content, mask));
            ImageView icon = new ImageView(context);
            icon.setImageResource(iconRes);
            icon.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurface(), PorterDuff.Mode.SRC_IN));
            addView(icon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        }
    }

    // ------------------------------------------------------------------
    // Adapter
    // ------------------------------------------------------------------

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private List<Item> items = new ArrayList<>();

        @Override
        public int getItemViewType(int position) {
            return items.get(position).type;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            switch (viewType) {
                case TYPE_HERO:
                    return new HeroHolder(new HeroCard(context));
                case TYPE_SEARCH:
                    return new SearchHolder(context);
                case TYPE_HEADER:
                    return new HeaderHolder(context);
                case TYPE_QUICK:
                    return new QuickHolder(context);
                case TYPE_GROUP:
                    return new GroupHolder(context);
                case TYPE_EMPTY:
                    return new EmptyHolder(context);
                case TYPE_ACTIONS:
                    return new ActionsHolder(context);
                case TYPE_FOOTER:
                default:
                    return new FooterHolder(context);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (holder instanceof HeroHolder) {
                ((HeroHolder) holder).bind((YumiSettingsModel.Hero) item.payload);
            } else if (holder instanceof SearchHolder) {
                ((SearchHolder) holder).bind();
            } else if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).bind((YumiSettingsModel.Section) item.payload);
            } else if (holder instanceof QuickHolder) {
                ((QuickHolder) holder).bind((List<YumiSettingsModel.Quick>) item.payload);
            } else if (holder instanceof GroupHolder) {
                ((GroupHolder) holder).bind((YumiSettingsModel.Section) item.payload);
            } else if (holder instanceof EmptyHolder) {
                ((EmptyHolder) holder).bind();
            } else if (holder instanceof ActionsHolder) {
                ((ActionsHolder) holder).bind();
            } else if (holder instanceof FooterHolder) {
                ((FooterHolder) holder).bind();
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    // ------------------------------------------------------------------
    // Hero
    // ------------------------------------------------------------------

    private class HeroHolder extends RecyclerView.ViewHolder {
        private final HeroCard card;

        HeroHolder(HeroCard card) {
            super(card);
            this.card = card;
        }

        void bind(YumiSettingsModel.Hero hero) {
            card.bind(hero);
        }
    }
    private class HeroCard extends FrameLayout {
        private final LinearLayout content;
        private final ImageView logo;
        private final TextView eyebrow;
        private final TextView title;
        private final TextView subtitle;
        private final TextView statModules;
        private final TextView statVersion;
        private final TextView statSecurity;
        private final TextView badge;

        HeroCard(Context context) {
            super(context);
            setPadding(YumiMd3.dp(16), YumiMd3.dp(8), YumiMd3.dp(16), YumiMd3.dp(6));

            content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setBackground(YumiMd3.rounded(
                    YumiMd3.surfaceContainerLow(),
                    YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.22f : 0.14f), 26, 0.75f));
            content.setPadding(YumiMd3.dp(20), YumiMd3.dp(18), YumiMd3.dp(20), YumiMd3.dp(18));
            addView(content, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            FrameLayout logoBox = new FrameLayout(context);
            logoBox.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.14f), 16));
            logo = new ImageView(context);
            logo.setImageResource(R.drawable.yumi_logo);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logoBox.addView(logo, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
            top.addView(logoBox, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL));

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setPadding(YumiMd3.dp(14), 0, 0, 0);

            LinearLayout eyebrowRow = new LinearLayout(context);
            eyebrowRow.setOrientation(LinearLayout.HORIZONTAL);
            eyebrowRow.setGravity(Gravity.CENTER_VERTICAL);

            eyebrow = YumiMd3.text(context, "", 10.5f, YumiMd3.primary(), YumiMd3.BOLD);
            eyebrow.setLetterSpacing(0.08f);
            eyebrow.setSingleLine(true);
            eyebrow.setEllipsize(TextUtils.TruncateAt.END);
            eyebrowRow.addView(eyebrow, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            badge = YumiMd3.text(context, "MD3", 10.5f, YumiMd3.primary(), YumiMd3.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.12f), 99));
            badge.setPadding(YumiMd3.dp(8), YumiMd3.dp(2), YumiMd3.dp(8), YumiMd3.dp(2));
            eyebrowRow.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
            textCol.addView(eyebrowRow);

            title = YumiMd3.text(context, "", 20, YumiMd3.onSurface(), YumiMd3.BOLD);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            textCol.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            subtitle = YumiMd3.text(context, "", 13f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setLineSpacing(YumiMd3.dp(1), 1f);
            textCol.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

            top.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            content.addView(top);

            LinearLayout stats = new LinearLayout(context);
            stats.setOrientation(LinearLayout.HORIZONTAL);
            stats.setGravity(Gravity.CENTER_VERTICAL);
            statModules = statChip(context, "");
            statVersion = statChip(context, "");
            statSecurity = statChip(context, "");
            LinearLayout.LayoutParams one = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
            one.setMarginEnd(YumiMd3.dp(6));
            LinearLayout.LayoutParams two = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
            two.setMarginStart(YumiMd3.dp(3));
            two.setMarginEnd(YumiMd3.dp(3));
            LinearLayout.LayoutParams three = new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f);
            three.setMarginStart(YumiMd3.dp(6));
            stats.addView(statModules, one);
            stats.addView(statVersion, two);
            stats.addView(statSecurity, three);
            content.addView(stats, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 16, 0, 0));
        }

        private TextView statChip(Context context, CharSequence text) {
            TextView view = YumiMd3.text(context, text, 11f, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            view.setGravity(Gravity.CENTER);
            view.setSingleLine(true);
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setBackground(YumiMd3.rounded(
                    YumiMd3.surfaceContainerHigh(),
                    YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.20f : 0.12f), 99, 0.75f));
            view.setPadding(YumiMd3.dp(8), YumiMd3.dp(6), YumiMd3.dp(8), YumiMd3.dp(6));
            return view;
        }

        void bind(YumiSettingsModel.Hero hero) {
            eyebrow.setText(hero.eyebrow);
            title.setText(hero.title);
            subtitle.setText(hero.subtitle);
            statModules.setText(hero.statModules);
            statVersion.setText(hero.statVersion);
            statSecurity.setText(hero.statSecurity);
            if (hero.logoRes != 0) {
                logo.setImageResource(hero.logoRes);
            } else {
                logo.setImageResource(R.drawable.yumi_logo);
            }
            updateBadge();
        }

        private void updateBadge() {
            badge.setVisibility(getWidth() > 0 && getWidth() < YumiMd3.dp(320) ? GONE : VISIBLE);
        }
    }

    // ------------------------------------------------------------------
    // Search
    // ------------------------------------------------------------------

    private class SearchHolder extends RecyclerView.ViewHolder {

        private final EditText input;
        private final ImageView clear;
        private final FrameLayout box;
        private boolean suppress;

        SearchHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout outer = (LinearLayout) itemView;
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setPadding(YumiMd3.dp(16), YumiMd3.dp(8), YumiMd3.dp(16), YumiMd3.dp(4));

            box = new FrameLayout(context);
            box.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(),
                    YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.22f : 0.14f), 28, 0.75f));
            box.setClipToOutline(true);
            box.setClickable(true);
            outer.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

            ImageView lung = new ImageView(context);
            lung.setImageResource(R.drawable.yumi_ic_search);
            lung.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            FrameLayout.LayoutParams lungLp = new FrameLayout.LayoutParams(YumiMd3.dp(20), YumiMd3.dp(20),
                    Gravity.CENTER_VERTICAL | Gravity.START);
            lungLp.setMarginStart(YumiMd3.dp(17));
            box.addView(lung, lungLp);

            input = new EditText(context);
            input.setBackground(null);
            input.setSingleLine(true);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            input.setTypeface(YumiMd3.typeface(YumiMd3.REGULAR));
            input.setTextColor(YumiMd3.onSurface());
            input.setHintTextColor(YumiMd3.onSurfaceVariant());
            input.setHint(MiogramLocale.get("Пошук налаштувань", "Поиск настроек", "Search settings"));
            input.setPaddingRelative(YumiMd3.dp(48), 0, YumiMd3.dp(52), 0);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            input.setHighlightColor(Color.TRANSPARENT);
            box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            clear = new ImageView(context);
            clear.setImageResource(R.drawable.yumi_ic_close);
            clear.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            clear.setBackground(YumiMd3.transparentRipple(24, YumiMd3.defaultRippleColor()));
            clear.setPadding(YumiMd3.dp(13), YumiMd3.dp(13), YumiMd3.dp(13), YumiMd3.dp(13));
            clear.setClickable(true);
            clear.setContentDescription(MiogramLocale.get("Очистити", "Очистить", "Clear"));
            FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(YumiMd3.dp(48), YumiMd3.dp(48),
                    Gravity.CENTER_VERTICAL | Gravity.END);
            clearLp.setMarginEnd(YumiMd3.dp(4));
            box.addView(clear, clearLp);
            clear.setOnClickListener(v -> {
                haptic(v);
                clearSearch();
            });

            input.setOnFocusChangeListener((v, hasFocus) -> updateSearchOutline(hasFocus));
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) {
                        imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                    }
                    return true;
                }
                return false;
            });
            box.setOnClickListener(v -> {
                input.requestFocus();
                InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                }
            });

            input.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    clear.setVisibility(s != null && s.length() > 0 ? VISIBLE : GONE);
                    if (!suppress) {
                        onQueryChanged(s == null ? "" : s.toString());
                    }
                }
            });
        }

        void bind() {
            suppress = true;
            input.setText(query);
            input.setSelection(input.length());
            clear.setVisibility(input.length() > 0 ? VISIBLE : GONE);
            suppress = false;
            searchReference = new WeakReference<>(input);
        }

        private void updateSearchOutline(boolean focused) {
            box.setBackground(YumiMd3.rounded(
                    YumiMd3.surfaceContainerHigh(),
                    focused ? YumiMd3.primary()
                            : YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.22f : 0.14f),
                    28, focused ? 1.5f : 0.75f));
        }
    }

    // ------------------------------------------------------------------
    // Section headers
    // ------------------------------------------------------------------

    private class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;

        HeaderHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(20), YumiMd3.dp(20), YumiMd3.dp(20), YumiMd3.dp(9));

            title = YumiMd3.text(context, "", 13, YumiMd3.onSurfaceVariant(), YumiMd3.MEDIUM);
            title.setLetterSpacing(0.02f);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title);

            subtitle = YumiMd3.text(context, "", 11.5f, YumiMd3.muted(YumiMd3.onSurfaceVariant()), YumiMd3.REGULAR);
            subtitle.setMaxLines(1);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
        }

        void bind(YumiSettingsModel.Section section) {
            title.setText(section.title);
            if (TextUtils.isEmpty(section.subtitle)) {
                subtitle.setVisibility(GONE);
            } else {
                subtitle.setVisibility(VISIBLE);
                subtitle.setText(section.subtitle);
            }
        }
    }

    // ------------------------------------------------------------------
    // Quick controls
    // ------------------------------------------------------------------

    private class QuickHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;

        QuickHolder(Context context) {
            super(new LinearLayout(context));
            root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(16), 0, YumiMd3.dp(16), 0);
        }

        void bind(List<YumiSettingsModel.Quick> quick) {
            root.removeAllViews();
            for (int i = 0; i < quick.size(); i += 2) {
                LinearLayout row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);
                root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

                View left = buildQuickTile(quick.get(i));
                LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0,
                        LayoutHelper.WRAP_CONTENT, 1f);
                leftLp.setMarginEnd(YumiMd3.dp(5));
                row.addView(left, leftLp);

                if (i + 1 < quick.size()) {
                    View right = buildQuickTile(quick.get(i + 1));
                    LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0,
                            LayoutHelper.WRAP_CONTENT, 1f);
                    rightLp.setMarginStart(YumiMd3.dp(5));
                    row.addView(right, rightLp);
                } else {
                    View spacer = new View(context);
                    LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 1, 1f);
                    spacerLp.setMarginStart(YumiMd3.dp(5));
                    row.addView(spacer, spacerLp);
                }
            }
        }

        private View buildQuickTile(YumiSettingsModel.Quick quick) {
            QuickTile tile = new QuickTile(context);
            tile.bind(quick);
            return tile;
        }
    }

    private class QuickTile extends LinearLayout {
        private final FrameLayout iconBox;
        private final ImageView icon;
        private final TextView title;
        private final TextView subtitle;
        private final YumiSwitch toggle;
        private final ImageView chevron;
        private YumiSettingsModel.Quick bound;

        QuickTile(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setPadding(YumiMd3.dp(15), YumiMd3.dp(14), YumiMd3.dp(14), YumiMd3.dp(14));
            setMinimumHeight(YumiMd3.dp(136));
            setClickable(true);
            setFocusable(true);
            attachPressScale(this);

            LinearLayout top = new LinearLayout(context);
            top.setOrientation(HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);

            iconBox = new FrameLayout(context);
            icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            iconBox.addView(icon, LayoutHelper.createFrame(20, 20, Gravity.CENTER));
            top.addView(iconBox, LayoutHelper.createLinear(38, 38));

            View spacer = new View(context);
            top.addView(spacer, LayoutHelper.createLinear(0, 1, 1f));

            toggle = new YumiSwitch(context);
            toggle.setVisibility(GONE);
            top.addView(toggle, LayoutHelper.createLinear(54, 48));

            chevron = new ImageView(context);
            chevron.setImageResource(R.drawable.yumi_ic_chevron_right);
            chevron.setVisibility(GONE);
            top.addView(chevron, LayoutHelper.createLinear(20, 20));
            addView(top);

            title = YumiMd3.text(context, "", 14.5f, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

            subtitle = YumiMd3.text(context, "", 11.5f, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            subtitle.setMinLines(2);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            subtitle.setLineSpacing(YumiMd3.dp(1), 1f);
            addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

            setOnClickListener(v -> {
                haptic(v);
                if (bound != null) {
                    if (!bound.toggle) {
                        hideSearchFocus();
                    }
                    listener.onQuick(bound);
                }
            });
            toggle.setOnCheckedChangeListener((view, checked) -> {
                if (bound != null) {
                    listener.onQuickToggle(bound, checked);
                }
            });
        }

        void bind(YumiSettingsModel.Quick quick) {
            bound = quick;
            int accent = quick.accent;
            iconBox.setBackground(YumiMd3.rounded(YumiMd3.iconTone(accent, 0.18f), 13));
            icon.setImageResource(quick.icon);
            icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            title.setText(quick.title);
            subtitle.setText(quick.subtitle);

            GradientDrawable content = new GradientDrawable();
            content.setShape(GradientDrawable.RECTANGLE);
            content.setColor(YumiMd3.tone(accent, YumiMd3.isDark() ? 0.12f : 0.08f));
            content.setCornerRadius(YumiMd3.dp(22));
            content.setStroke(Math.max(1, YumiMd3.dp(0.75f)),
                    YumiMd3.withAlpha(accent, YumiMd3.isDark() ? 0.22f : 0.15f));
            GradientDrawable mask = YumiMd3.rounded(0xFFFFFFFF, 22);
            RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(
                    YumiMd3.accentRippleColor(accent)), content, mask);
            setBackground(ripple);

            if (quick.toggle) {
                toggle.setVisibility(VISIBLE);
                chevron.setVisibility(GONE);
                toggle.setAccent(accent);
                toggle.setChecked(quick.checked, false);
            } else {
                toggle.setVisibility(GONE);
                chevron.setVisibility(VISIBLE);
                chevron.setColorFilter(new PorterDuffColorFilter(YumiMd3.muted(accent), PorterDuff.Mode.SRC_IN));
            }
            setContentDescription(quick.title + ". " + quick.subtitle);
        }
    }

    // ------------------------------------------------------------------
    // Grouped rows
    // ------------------------------------------------------------------

    private class GroupHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;

        GroupHolder(Context context) {
            super(new LinearLayout(context));
            root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(16), 0, YumiMd3.dp(16), 0);
        }

        void bind(YumiSettingsModel.Section section) {
            root.removeAllViews();
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setShape(GradientDrawable.RECTANGLE);
            cardBackground.setColor(YumiMd3.surfaceContainerLow());
            cardBackground.setCornerRadius(YumiMd3.dp(24));
            cardBackground.setStroke(Math.max(1, YumiMd3.dp(0.75f)),
                    YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.22f : 0.14f));
            card.setBackground(cardBackground);
            card.setClipToOutline(true);
            root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT));

            for (int i = 0; i < section.rows.size(); i++) {
                YumiSettingsModel.Row row = section.rows.get(i);
                if (i > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(YumiMd3.withAlpha(YumiMd3.outline(), YumiMd3.isDark() ? 0.18f : 0.12f));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutHelper.MATCH_PARENT, Math.max(1, YumiMd3.dp(0.75f)));
                    lp.setMarginStart(YumiMd3.dp(68));
                    card.addView(divider, lp);
                }
                card.addView(buildRow(row, i == 0, i == section.rows.size() - 1));
            }
        }

        private View buildRow(YumiSettingsModel.Row row, boolean first, boolean last) {
            LinearLayout rowView = new LinearLayout(context);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setMinimumHeight(YumiMd3.dp(68));
            rowView.setPadding(YumiMd3.dp(16), YumiMd3.dp(11), YumiMd3.dp(13), YumiMd3.dp(11));
            rowView.setClickable(true);
            rowView.setFocusable(true);
            rowView.setEnabled(row.enabled);

            float radius = YumiMd3.dp(24);
            float[] corners;
            if (first && last) {
                corners = new float[]{radius, radius, radius, radius, radius, radius, radius, radius};
            } else if (first) {
                corners = new float[]{radius, radius, radius, radius, 0, 0, 0, 0};
            } else if (last) {
                corners = new float[]{0, 0, 0, 0, radius, radius, radius, radius};
            } else {
                corners = new float[]{0, 0, 0, 0, 0, 0, 0, 0};
            }
            GradientDrawable mask = new GradientDrawable();
            mask.setColor(0xFFFFFFFF);
            mask.setCornerRadii(corners);
            RippleDrawable ripple = new RippleDrawable(ColorStateList.valueOf(
                    YumiMd3.accentRippleColor(row.accent)), null, mask);
            rowView.setBackground(ripple);

            int accent = row.kind == YumiSettingsModel.KIND_DANGER ? YumiMd3.error() : row.accent;
            FrameLayout iconBox = new FrameLayout(context);
            iconBox.setBackground(YumiMd3.rounded(YumiMd3.iconTone(accent, 0.16f), 13));
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(row.icon);
            icon.setColorFilter(new PorterDuffColorFilter(accent, PorterDuff.Mode.SRC_IN));
            iconBox.addView(icon, LayoutHelper.createFrame(21, 21, Gravity.CENTER));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(YumiMd3.dp(40), YumiMd3.dp(40));
            iconLp.setMarginEnd(YumiMd3.dp(13));
            rowView.addView(iconBox, iconLp);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = YumiMd3.text(context, row.title, 15.5f, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            if (row.kind == YumiSettingsModel.KIND_DANGER) {
                title.setTextColor(YumiMd3.error());
            }
            textCol.addView(title);

            if (!TextUtils.isEmpty(row.subtitle)) {
                TextView subtitle = YumiMd3.text(context, row.subtitle, 12.5f,
                        YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
                subtitle.setMaxLines(2);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                subtitle.setLineSpacing(YumiMd3.dp(1), 1f);
                textCol.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            }
            rowView.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            if (row.kind == YumiSettingsModel.KIND_SWITCH) {
                YumiSwitch toggle = new YumiSwitch(context);
                toggle.setAccent(accent);
                toggle.setChecked(row.checked, false);
                toggle.setContentDescription(row.title);
                toggle.setOnCheckedChangeListener((view, checked) -> listener.onToggle(row, checked));
                LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(YumiMd3.dp(54), YumiMd3.dp(48));
                switchLp.setMarginStart(YumiMd3.dp(10));
                toggle.setEnabled(row.enabled);
                rowView.addView(toggle, switchLp);
            } else {
                if (!TextUtils.isEmpty(row.value)) {
                    TextView value = YumiMd3.text(context, row.value, 11.5f, accent, YumiMd3.MEDIUM);
                    value.setSingleLine(true);
                    value.setGravity(Gravity.CENTER);
                    value.setBackground(YumiMd3.rounded(YumiMd3.tone(accent, 0.12f), 99));
                    value.setPadding(YumiMd3.dp(9), YumiMd3.dp(4), YumiMd3.dp(9), YumiMd3.dp(4));
                    LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                    valueLp.setMarginStart(YumiMd3.dp(8));
                    rowView.addView(value, valueLp);
                }
                ImageView chevron = new ImageView(context);
                chevron.setImageResource(R.drawable.yumi_ic_chevron_right);
                chevron.setColorFilter(new PorterDuffColorFilter(
                        YumiMd3.muted(YumiMd3.onSurfaceVariant()), PorterDuff.Mode.SRC_IN));
                LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(YumiMd3.dp(18), YumiMd3.dp(18));
                chevronLp.setMarginStart(YumiMd3.dp(8));
                rowView.addView(chevron, chevronLp);
            }

            if (!row.enabled) {
                rowView.setAlpha(0.45f);
            }
            rowView.setOnClickListener(v -> {
                if (!row.enabled) {
                    return;
                }
                haptic(v);
                hideSearchFocus();
                listener.onRow(row);
            });
            rowView.setContentDescription(row.title + ". " + (row.subtitle == null ? "" : row.subtitle));
            return rowView;
        }
    }

    // ------------------------------------------------------------------
    // Empty state
    // ------------------------------------------------------------------

    private class EmptyHolder extends RecyclerView.ViewHolder {
        private final TextView body;

        EmptyHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(YumiMd3.dp(32), YumiMd3.dp(30), YumiMd3.dp(32), YumiMd3.dp(40));

            FrameLayout iconBox = new FrameLayout(context);
            iconBox.setBackground(YumiMd3.rounded(YumiMd3.tone(YumiMd3.primary(), 0.16f), 24));
            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.yumi_ic_search);
            icon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
            iconBox.addView(icon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            root.addView(iconBox, LayoutHelper.createLinear(72, 72));

            TextView title = YumiMd3.text(context, MiogramLocale.get(
                    "Нічого не знайшлося", "Ничего не нашлось", "Nothing found"), 17,
                    YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setGravity(Gravity.CENTER);
            root.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            body = YumiMd3.text(context, "", 13,
                    YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            body.setGravity(Gravity.CENTER);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));

            TextView clear = YumiMd3.text(context, MiogramLocale.get(
                    "Скинути пошук", "Сбросить поиск", "Clear search"), 13,
                    YumiMd3.primary(), YumiMd3.MEDIUM);
            clear.setGravity(Gravity.CENTER);
            clear.setBackground(YumiMd3.ripple(YumiMd3.tone(YumiMd3.primary(), 0.13f),
                    22, YumiMd3.accentRippleColor(YumiMd3.primary())));
            clear.setPadding(YumiMd3.dp(10), YumiMd3.dp(12), YumiMd3.dp(10), YumiMd3.dp(12));
            clear.setOnClickListener(v -> {
                haptic(v);
                clearSearch();
            });
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                    LayoutHelper.WRAP_CONTENT, YumiMd3.dp(48));
            clearLp.topMargin = YumiMd3.dp(18);
            root.addView(clear, clearLp);
        }

        void bind() {
            if (query.isEmpty()) {
                body.setText(MiogramLocale.get(
                        "Спробуй інший запит — налаштування точно є.",
                        "Попробуй другой запрос — настройки точно есть.",
                        "Try another query — the setting is probably here."));
            } else {
                body.setText(MiogramLocale.format(
                        "За запитом «%s» нічого немає. Спробуй інше слово.",
                        "По запросу «%s» ничего нет. Попробуй другое слово.",
                        "Nothing matched “%s”. Try a different word.", query));
            }
        }
    }

    // ------------------------------------------------------------------
    // Footer actions + note
    // ------------------------------------------------------------------

    private class ActionsHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;

        ActionsHolder(Context context) {
            super(new LinearLayout(context));
            root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.HORIZONTAL);
            root.setPadding(YumiMd3.dp(16), YumiMd3.dp(20), YumiMd3.dp(16), YumiMd3.dp(2));
        }

        void bind() {
            root.removeAllViews();
            if (model == null) {
                return;
            }
            YumiSettingsModel.Action primary = model.footerPrimary;
            YumiSettingsModel.Action secondary = model.footerSecondary;
            if (primary != null) {
                ActionButton button = new ActionButton(context, primary, true);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, YumiMd3.dp(52), 1f);
                if (secondary != null) {
                    lp.setMarginEnd(YumiMd3.dp(9));
                }
                root.addView(button, lp);
            }
            if (secondary != null) {
                ActionButton button = new ActionButton(context, secondary, false);
                root.addView(button, new LinearLayout.LayoutParams(
                        LayoutHelper.WRAP_CONTENT, YumiMd3.dp(52)));
            }
        }
    }

    private class ActionButton extends FrameLayout {
        private final TextView label;
        private final ImageView icon;

        ActionButton(Context context, YumiSettingsModel.Action action, boolean primary) {
            super(context);
            setClickable(true);
            setFocusable(true);
            setContentDescription(action.label);
            setMinimumWidth(YumiMd3.dp(primary ? 0 : 104));
            setPadding(YumiMd3.dp(16), 0, YumiMd3.dp(16), 0);
            attachPressScale(this);

            GradientDrawable content;
            int textColor;
            if (primary) {
                content = YumiMd3.gradient(YumiMd3.actionGradientStart(), YumiMd3.actionGradientEnd(), 18);
                textColor = 0xFFFFFFFF;
            } else {
                int accent = YumiMd3.primary();
                content = YumiMd3.rounded(YumiMd3.tone(accent, 0.14f),
                        YumiMd3.withAlpha(accent, 0.22f), 18, 1);
                textColor = accent;
            }
            GradientDrawable mask = YumiMd3.rounded(0xFFFFFFFF, 18);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(primary ? 0x33FFFFFF : YumiMd3.accentRippleColor(YumiMd3.primary())),
                    content, mask);
            setBackground(ripple);

            LinearLayout inner = new LinearLayout(context);
            inner.setOrientation(LinearLayout.HORIZONTAL);
            inner.setGravity(Gravity.CENTER);
            addView(inner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            icon = new ImageView(context);
            icon.setImageResource(action.icon);
            icon.setColorFilter(new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_IN));
            inner.addView(icon, LayoutHelper.createLinear(18, 18));

            label = YumiMd3.text(context, action.label, 13.5f, textColor, YumiMd3.MEDIUM);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            inner.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 7, 0));

            setOnClickListener(v -> {
                haptic(v);
                hideSearchFocus();
                listener.onFooterAction(action);
            });
        }

        @Override
        public CharSequence getAccessibilityClassName() {
            return "android.widget.Button";
        }
    }

    private class FooterHolder extends RecyclerView.ViewHolder {
        private final TextView note;
        private final ImageView logo;

        FooterHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(YumiMd3.dp(24), YumiMd3.dp(24), YumiMd3.dp(24), YumiMd3.dp(30));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            logo = new ImageView(context);
            logo.setImageResource(YumiMd3.isDark() ? R.drawable.yumi_logo : R.drawable.yumi_logo_light);
            row.addView(logo, LayoutHelper.createLinear(16, 16));

            note = YumiMd3.text(context, "", 11, YumiMd3.muted(YumiMd3.onSurfaceVariant()), YumiMd3.REGULAR);
            note.setGravity(Gravity.CENTER);
            note.setMaxLines(2);
            row.addView(note, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, 0, 0, 7, 0));
            root.addView(row);
        }

        void bind() {
            if (model != null) {
                note.setText(model.footerNote);
            }
        }
    }
}
