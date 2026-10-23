package app.amegram.settings.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
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
 * Authentic Google Material Design 3 (Pixel / Android 14/15) settings hub.
 *
 * <p>Replaces all pseudo-MD3 visual clutter (aurora backdrops, noise textures,
 * glowing hero cards with "MD3" badges, and 2x2 giant quick-action tiles)
 * with authentic Google Material Design 3 components: Large Top App Bar,
 * pill Search Bar, flat grouped preference cards, unified tonal icons,
 * and standard M3 switches.</p>
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

    private static final int TYPE_PAGE_TITLE = 1;
    private static final int TYPE_SEARCH = 2;
    private static final int TYPE_HEADER = 3;
    private static final int TYPE_GROUP = 5;
    private static final int TYPE_EMPTY = 6;
    private static final int TYPE_FOOTER = 8;

    private static final int TOP_BAR_CONTENT_HEIGHT = 56;

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
    private String query = "";

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
        addView(topBar);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateRecyclerPadding(w);
    }

    @SuppressWarnings("deprecation")
    private void updateRecyclerPadding(int width) {
        int side = Math.max(0, (width - YumiMd3.dp(640)) / 2);
        int top = topBarHeight;
        int bottom = YumiMd3.dp(24) + Math.max(0, AndroidUtilities.navigationBarHeight);
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
        model = next;
        topBar.setTitle(next.pageTitle);

        List<Item> items = buildItems();
        adapter.items = items;
        adapter.notifyDataSetChanged();
        updateTopBar();
    }

    public void focusSearch() {
        View search = searchReference.get();
        if (search != null && search.isAttachedToWindow()) {
            recyclerView.smoothScrollToPosition(1);
            search.postDelayed(() -> openSearch(search), 150);
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
            applyItems(buildItems());
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
            applyItems(buildItems());
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

        boolean searching = hasQuery();
        if (searching) {
            items.add(new Item(TYPE_SEARCH, null));
            List<YumiSettingsModel.Row> found = searchRows();
            if (found.isEmpty()) {
                items.add(new Item(TYPE_EMPTY, null));
            } else {
                YumiSettingsModel.Section result = new YumiSettingsModel.Section();
                result.title = MiogramLocale.get("Результати", "Результаты", "Results");
                result.rows.addAll(found);
                items.add(new Item(TYPE_HEADER, result));
                items.add(new Item(TYPE_GROUP, result));
            }
        } else {
            // Position 0: Authentic Google M3 Large Top App Bar Title
            items.add(new Item(TYPE_PAGE_TITLE, model));
            // Position 1: Authentic Google M3 SearchBar
            items.add(new Item(TYPE_SEARCH, null));

            for (YumiSettingsModel.Section section : model.sections) {
                if (section.rows.isEmpty()) {
                    continue;
                }
                items.add(new Item(TYPE_HEADER, section));
                items.add(new Item(TYPE_GROUP, section));
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

    private void applyItems(List<Item> items) {
        adapter.items = items;
        adapter.notifyDataSetChanged();
        updateTopBar();
    }

    private void updateTopBar() {
        int scroll = recyclerView.computeVerticalScrollOffset();
        float reveal = Math.min(1f, Math.max(0f, scroll / (float) YumiMd3.dp(48)));
        topBar.setReveal(reveal);
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
    // Authentic M3 Top Bar (collapsing title, back, search)
    // ------------------------------------------------------------------

    private class TopBar extends FrameLayout {

        private final Paint backgroundPaint = new Paint();
        private final Paint hairlinePaint = new Paint();
        private final ImageView back;
        private final ImageView search;
        private final TextView title;
        private float reveal;

        TopBar(Context context) {
            super(context);
            setWillNotDraw(false);
            setClipChildren(false);

            backgroundPaint.setColor(YumiMd3.surface());
            hairlinePaint.setColor(YumiMd3.withAlpha(YumiMd3.outline(), 0.16f));

            back = new ImageView(context);
            back.setImageResource(R.drawable.yumi_ic_arrow_back);
            back.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurface(), PorterDuff.Mode.SRC_IN));
            back.setBackground(YumiMd3.transparentRipple(24, YumiMd3.defaultRippleColor()));
            back.setScaleType(ImageView.ScaleType.CENTER);
            back.setContentDescription(MiogramLocale.get("Назад", "Назад", "Back"));
            back.setOnClickListener(v -> {
                haptic(v);
                hideSearchFocus();
                listener.onBack();
            });
            FrameLayout.LayoutParams backLp = new FrameLayout.LayoutParams(
                    YumiMd3.dp(48), YumiMd3.dp(48), Gravity.CENTER_VERTICAL | Gravity.START);
            backLp.topMargin = statusBar / 2;
            backLp.setMarginStart(YumiMd3.dp(4));
            addView(back, backLp);

            search = new ImageView(context);
            search.setImageResource(R.drawable.yumi_ic_search);
            search.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurface(), PorterDuff.Mode.SRC_IN));
            search.setBackground(YumiMd3.transparentRipple(24, YumiMd3.defaultRippleColor()));
            search.setScaleType(ImageView.ScaleType.CENTER);
            search.setContentDescription(MiogramLocale.get("Пошук налаштувань", "Поиск настроек", "Search settings"));
            search.setOnClickListener(v -> {
                haptic(v);
                YumiSettingsView.this.focusSearch();
            });
            FrameLayout.LayoutParams searchLp = new FrameLayout.LayoutParams(
                    YumiMd3.dp(48), YumiMd3.dp(48), Gravity.CENTER_VERTICAL | Gravity.END);
            searchLp.topMargin = statusBar / 2;
            searchLp.setMarginEnd(YumiMd3.dp(4));
            addView(search, searchLp);

            title = YumiMd3.text(context, "", 20, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            title.setAlpha(0f);
            FrameLayout.LayoutParams titleLp = new FrameLayout.LayoutParams(
                    LayoutHelper.MATCH_PARENT, YumiMd3.dp(48), Gravity.CENTER_VERTICAL);
            titleLp.topMargin = statusBar / 2;
            titleLp.setMarginStart(YumiMd3.dp(56));
            titleLp.setMarginEnd(YumiMd3.dp(56));
            addView(title, titleLp);
        }

        void setContentInset(int inset) {
            FrameLayout.LayoutParams backLp = (FrameLayout.LayoutParams) back.getLayoutParams();
            if (backLp.getMarginStart() != YumiMd3.dp(4) + inset) {
                backLp.setMarginStart(YumiMd3.dp(4) + inset);
                back.setLayoutParams(backLp);

                FrameLayout.LayoutParams searchLp = (FrameLayout.LayoutParams) search.getLayoutParams();
                searchLp.setMarginEnd(YumiMd3.dp(4) + inset);
                search.setLayoutParams(searchLp);

                FrameLayout.LayoutParams titleLp = (FrameLayout.LayoutParams) title.getLayoutParams();
                titleLp.setMarginStart(YumiMd3.dp(56) + inset);
                titleLp.setMarginEnd(YumiMd3.dp(56) + inset);
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
            title.setAlpha(reveal);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (reveal > 0.001f) {
                backgroundPaint.setColor(YumiMd3.surface());
                canvas.drawRect(0, 0, getWidth(), getHeight(), backgroundPaint);
                hairlinePaint.setColor(YumiMd3.withAlpha(YumiMd3.outline(), 0.16f * reveal));
                canvas.drawRect(0, getHeight() - YumiMd3.dp(1), getWidth(), getHeight(), hairlinePaint);
            }
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
                case TYPE_PAGE_TITLE:
                    return new PageTitleHolder(context);
                case TYPE_SEARCH:
                    return new SearchHolder(context);
                case TYPE_HEADER:
                    return new HeaderHolder(context);
                case TYPE_GROUP:
                    return new GroupHolder(context);
                case TYPE_EMPTY:
                    return new EmptyHolder(context);
                case TYPE_FOOTER:
                default:
                    return new FooterHolder(context);
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Item item = items.get(position);
            if (holder instanceof PageTitleHolder) {
                ((PageTitleHolder) holder).bind((YumiSettingsModel) item.payload);
            } else if (holder instanceof SearchHolder) {
                ((SearchHolder) holder).bind();
            } else if (holder instanceof HeaderHolder) {
                ((HeaderHolder) holder).bind((YumiSettingsModel.Section) item.payload);
            } else if (holder instanceof GroupHolder) {
                ((GroupHolder) holder).bind((YumiSettingsModel.Section) item.payload);
            } else if (holder instanceof EmptyHolder) {
                ((EmptyHolder) holder).bind();
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
    // Large Page Title (Android 14/15 M3 Large Top App Bar)
    // ------------------------------------------------------------------

    private class PageTitleHolder extends RecyclerView.ViewHolder {
        private final TextView title;
        private final TextView subtitle;

        PageTitleHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(20), YumiMd3.dp(16), YumiMd3.dp(20), YumiMd3.dp(12));

            title = YumiMd3.text(context, "", 32, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title);

            subtitle = YumiMd3.text(context, "", 14, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            subtitle.setMaxLines(2);
            subtitle.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        }

        void bind(YumiSettingsModel m) {
            title.setText(m.pageTitle);
            if (m.hero != null && !TextUtils.isEmpty(m.hero.subtitle)) {
                subtitle.setText(m.hero.subtitle);
                subtitle.setVisibility(VISIBLE);
            } else {
                subtitle.setVisibility(GONE);
            }
        }
    }

    // ------------------------------------------------------------------
    // Search Bar (Authentic Google M3 Pill)
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
            outer.setPadding(YumiMd3.dp(16), YumiMd3.dp(4), YumiMd3.dp(16), YumiMd3.dp(12));

            box = new FrameLayout(context);
            box.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 28));
            box.setClipToOutline(true);
            box.setClickable(true);
            outer.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 56));

            ImageView searchIcon = new ImageView(context);
            searchIcon.setImageResource(R.drawable.yumi_ic_search);
            searchIcon.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(YumiMd3.dp(22), YumiMd3.dp(22),
                    Gravity.CENTER_VERTICAL | Gravity.START);
            iconLp.setMarginStart(YumiMd3.dp(16));
            box.addView(searchIcon, iconLp);

            input = new EditText(context);
            input.setBackground(null);
            input.setSingleLine(true);
            input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            input.setTypeface(YumiMd3.typeface(YumiMd3.REGULAR));
            input.setTextColor(YumiMd3.onSurface());
            input.setHintTextColor(YumiMd3.onSurfaceVariant());
            input.setHint(MiogramLocale.get("Пошук у налаштуваннях", "Поиск в настройках", "Search settings"));
            input.setPaddingRelative(YumiMd3.dp(52), 0, YumiMd3.dp(48), 0);
            input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
            box.addView(input, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            clear = new ImageView(context);
            clear.setImageResource(R.drawable.yumi_ic_close);
            clear.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
            clear.setBackground(YumiMd3.transparentRipple(20, YumiMd3.defaultRippleColor()));
            clear.setPadding(YumiMd3.dp(10), YumiMd3.dp(10), YumiMd3.dp(10), YumiMd3.dp(10));
            clear.setClickable(true);
            clear.setContentDescription(MiogramLocale.get("Очистити", "Очистить", "Clear"));
            FrameLayout.LayoutParams clearLp = new FrameLayout.LayoutParams(YumiMd3.dp(44), YumiMd3.dp(44),
                    Gravity.CENTER_VERTICAL | Gravity.END);
            clearLp.setMarginEnd(YumiMd3.dp(6));
            box.addView(clear, clearLp);
            clear.setOnClickListener(v -> {
                haptic(v);
                clearSearch();
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
    }

    // ------------------------------------------------------------------
    // Category Section Header
    // ------------------------------------------------------------------

    private class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        HeaderHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(20), YumiMd3.dp(18), YumiMd3.dp(20), YumiMd3.dp(6));

            title = YumiMd3.text(context, "", 14, YumiMd3.primary(), YumiMd3.MEDIUM);
            title.setLetterSpacing(0.04f);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(title);
        }

        void bind(YumiSettingsModel.Section section) {
            title.setText(section.title != null ? section.title.toString().toUpperCase(Locale.ROOT) : "");
        }
    }

    // ------------------------------------------------------------------
    // Grouped Preference Card (Authentic M3 Surface Container)
    // ------------------------------------------------------------------

    private class GroupHolder extends RecyclerView.ViewHolder {
        private final LinearLayout root;

        GroupHolder(Context context) {
            super(new LinearLayout(context));
            root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(YumiMd3.dp(16), 0, YumiMd3.dp(16), YumiMd3.dp(10));
        }

        void bind(YumiSettingsModel.Section section) {
            root.removeAllViews();
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);

            GradientDrawable cardBackground = YumiMd3.rounded(YumiMd3.surfaceContainer(), 24);
            card.setBackground(cardBackground);
            card.setClipToOutline(true);
            root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT));

            for (int i = 0; i < section.rows.size(); i++) {
                YumiSettingsModel.Row row = section.rows.get(i);
                if (i > 0) {
                    View divider = new View(context);
                    divider.setBackgroundColor(YumiMd3.withAlpha(YumiMd3.outline(), 0.15f));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LayoutHelper.MATCH_PARENT, Math.max(1, YumiMd3.dp(1)));
                    lp.setMarginStart(YumiMd3.dp(72));
                    lp.setMarginEnd(YumiMd3.dp(16));
                    card.addView(divider, lp);
                }
                card.addView(buildRow(row, i == 0, i == section.rows.size() - 1));
            }
        }

        private View buildRow(YumiSettingsModel.Row row, boolean first, boolean last) {
            LinearLayout rowView = new LinearLayout(context);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setMinimumHeight(YumiMd3.dp(64));
            rowView.setPadding(YumiMd3.dp(16), YumiMd3.dp(12), YumiMd3.dp(16), YumiMd3.dp(12));
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
            mask.setCornerRadii(corners);
            mask.setColor(0xFFFFFFFF);
            RippleDrawable ripple = new RippleDrawable(
                    ColorStateList.valueOf(YumiMd3.defaultRippleColor()), null, mask);
            rowView.setBackground(ripple);

            FrameLayout iconBox = new FrameLayout(context);
            iconBox.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 20));
            ImageView icon = new ImageView(context);
            icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            icon.setImageResource(row.icon);
            int iconColor = row.kind == YumiSettingsModel.KIND_DANGER ? YumiMd3.error() : YumiMd3.primary();
            icon.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
            iconBox.addView(icon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(YumiMd3.dp(40), YumiMd3.dp(40));
            iconLp.setMarginEnd(YumiMd3.dp(16));
            rowView.addView(iconBox, iconLp);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setGravity(Gravity.CENTER_VERTICAL);

            TextView title = YumiMd3.text(context, row.title, 16, YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setSingleLine(true);
            title.setEllipsize(TextUtils.TruncateAt.END);
            if (row.kind == YumiSettingsModel.KIND_DANGER) {
                title.setTextColor(YumiMd3.error());
            }
            textCol.addView(title);

            if (!TextUtils.isEmpty(row.subtitle)) {
                TextView subtitle = YumiMd3.text(context, row.subtitle, 13.5f,
                        YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
                subtitle.setMaxLines(2);
                subtitle.setEllipsize(TextUtils.TruncateAt.END);
                textCol.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                        LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            }
            rowView.addView(textCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

            if (row.kind == YumiSettingsModel.KIND_SWITCH) {
                YumiSwitch toggle = new YumiSwitch(context);
                toggle.setAccent(YumiMd3.primary());
                toggle.setChecked(row.checked, false);
                toggle.setContentDescription(row.title);
                toggle.setOnCheckedChangeListener((view, checked) -> listener.onToggle(row, checked));
                LinearLayout.LayoutParams switchLp = new LinearLayout.LayoutParams(YumiMd3.dp(52), YumiMd3.dp(32));
                switchLp.setMarginStart(YumiMd3.dp(12));
                toggle.setEnabled(row.enabled);
                rowView.addView(toggle, switchLp);
            } else {
                if (!TextUtils.isEmpty(row.value)) {
                    TextView value = YumiMd3.text(context, row.value, 13, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
                    value.setSingleLine(true);
                    value.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                            LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT);
                    valueLp.setMarginStart(YumiMd3.dp(8));
                    rowView.addView(value, valueLp);
                }
                ImageView chevron = new ImageView(context);
                chevron.setImageResource(R.drawable.yumi_ic_chevron_right);
                chevron.setColorFilter(new PorterDuffColorFilter(
                        YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
                LinearLayout.LayoutParams chevronLp = new LinearLayout.LayoutParams(YumiMd3.dp(20), YumiMd3.dp(20));
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
            root.setPadding(YumiMd3.dp(32), YumiMd3.dp(40), YumiMd3.dp(32), YumiMd3.dp(40));

            FrameLayout iconBox = new FrameLayout(context);
            iconBox.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 28));
            ImageView icon = new ImageView(context);
            icon.setImageResource(R.drawable.yumi_ic_search);
            icon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
            iconBox.addView(icon, LayoutHelper.createFrame(28, 28, Gravity.CENTER));
            root.addView(iconBox, LayoutHelper.createLinear(56, 56));

            TextView title = YumiMd3.text(context, MiogramLocale.get(
                    "Нічого не знайдено", "Ничего не найдено", "Nothing found"), 18,
                    YumiMd3.onSurface(), YumiMd3.MEDIUM);
            title.setGravity(Gravity.CENTER);
            root.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 0));

            body = YumiMd3.text(context, "", 14,
                    YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            body.setGravity(Gravity.CENTER);
            body.setMaxLines(2);
            body.setEllipsize(TextUtils.TruncateAt.END);
            root.addView(body, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT,
                    LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));

            TextView clear = YumiMd3.text(context, MiogramLocale.get(
                    "Скинути пошук", "Сбросить поиск", "Clear search"), 14,
                    YumiMd3.primary(), YumiMd3.MEDIUM);
            clear.setGravity(Gravity.CENTER);
            clear.setBackground(YumiMd3.rounded(YumiMd3.surfaceContainerHigh(), 20));
            clear.setPadding(YumiMd3.dp(16), YumiMd3.dp(10), YumiMd3.dp(16), YumiMd3.dp(10));
            clear.setOnClickListener(v -> {
                haptic(v);
                clearSearch();
            });
            LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(
                    LayoutHelper.WRAP_CONTENT, YumiMd3.dp(40));
            clearLp.topMargin = YumiMd3.dp(18);
            root.addView(clear, clearLp);
        }

        void bind() {
            if (query.isEmpty()) {
                body.setText(MiogramLocale.get(
                        "Спробуйте інший запит.",
                        "Попробуйте другой запрос.",
                        "Try another search query."));
            } else {
                body.setText(MiogramLocale.format(
                        "За запитом «%s» нічого не знайдено.",
                        "По запросу «%s» ничего не найдено.",
                        "Nothing matched “%s”.", query));
            }
        }
    }

    // ------------------------------------------------------------------
    // Footer note
    // ------------------------------------------------------------------

    private class FooterHolder extends RecyclerView.ViewHolder {
        private final TextView note;

        FooterHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView;
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(YumiMd3.dp(24), YumiMd3.dp(20), YumiMd3.dp(24), YumiMd3.dp(36));

            note = YumiMd3.text(context, "", 12, YumiMd3.onSurfaceVariant(), YumiMd3.REGULAR);
            note.setGravity(Gravity.CENTER);
            root.addView(note);
        }

        void bind() {
            if (model != null && !TextUtils.isEmpty(model.footerNote)) {
                note.setText(model.footerNote);
            } else {
                note.setText("Yumigram · Material Design 3");
            }
        }
    }
}
