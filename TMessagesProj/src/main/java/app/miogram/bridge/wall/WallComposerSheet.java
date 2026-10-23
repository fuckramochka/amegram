package app.miogram.bridge.wall;

import android.text.TextWatcher;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallAction;
import app.miogram.bridge.wall.WallModels.WallElement;
import app.miogram.bridge.wall.WallModels.WallPoll;

/**
 * Шит створення/редагування елемента стіни.
 * Типи: текст, фото, гіфка, відео, стикер, файл, переслане, опитування,
 * малюнок, кнопка, HTML/CSS/JS. Ліміт «1 елемент на людину» підтримує викликач.
 */
public class WallComposerSheet extends BottomSheet {

    public interface PublishCallback {
        void onPublished(WallElement element);
    }

    private static final String[] TYPE_KEYS = {
            WallModels.TYPE_TEXT, WallModels.TYPE_PHOTO, WallModels.TYPE_GIF, WallModels.TYPE_VIDEO,
            WallModels.TYPE_STICKER, WallModels.TYPE_FILE, WallModels.TYPE_FORWARD,
            WallModels.TYPE_POLL, WallModels.TYPE_DRAWING, WallModels.TYPE_BUTTON, WallModels.TYPE_HTML
    };
    private static final String[] TYPE_EMOJI = {
            "✍", "🖼", "🎞", "🎬", "💖", "📎", "➦", "📊", "✏", "🔗", "⚡"
    };

    private static final String[][] HTML_TEMPLATES = new String[][]{
            {"Годинник", "<div class=\"clock\">--:--</div>",
                    ".clock{font-size:34px;font-weight:800;text-shadow:0 0 18px #ff4d8d;color:#fff;text-align:center;margin-top:22px}",
                    "function tick(){var d=new Date();var p=function(n){return (n<10?'0':'')+n};var el=document.querySelector('.clock');if(el)el.textContent=p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds())}setInterval(tick,250);tick();"},
            {"Неон", "<h1 class=\"neon\">стіна</h1>",
                    ".neon{font-size:38px;color:#fff;text-align:center;margin-top:18px;text-shadow:0 0 6px #ff4d8d,0 0 18px #8a5cff,0 0 34px #35e0ff;animation:pulse 2s infinite}@keyframes pulse{50%{opacity:.7}}",
                    ""},
            {"Пульс", "<div class=\"h\">💖</div>",
                    ".h{font-size:54px;text-align:center;margin-top:16px;animation:beat .9s infinite}@keyframes beat{0%,100%{transform:scale(1)}35%{transform:scale(1.35)}}",
                    ""},
            {"Кнопка", "<button class=\"b\" id=\"b\">Тапни</button><p id=\"o\" style=\"color:#fff;text-align:center\"></p>",
                    ".b{background:linear-gradient(90deg,#ff4d8d,#8a5cff);border:0;color:#fff;padding:12px 22px;border-radius:18px;font-weight:800;font-size:15px}",
                    "var n=0;var b=document.getElementById('b');var o=document.getElementById('o');b.onclick=()=>{n++;o.textContent='тапів: '+n;b.textContent='ще? ;)'}"}
    };

    private final Context context;
    private final BaseFragment parentFragment;
    private final Wall wall;
    private final WallElement original; // null -> створення
    private final PublishCallback callback;

    private final WallElement draft;
    private boolean hasMedia;
    private LinearLayout typeRow;
    private LinearLayout fieldsContainer;
    private LinearLayout styleRow;
    private LinearLayout sizeRow;
    private TextView errorView;
    private TextView publishBtn;
    private Runnable previewRefresher;
    private Runnable drawingRefresher;
    private HorizontalScrollView typeScroll;

    public WallComposerSheet(Context context, BaseFragment parentFragment, Wall wall, WallElement editing, PublishCallback callback) {
        super(context, false, null);
        this.context = context;
        this.parentFragment = parentFragment;
        this.wall = wall;
        this.original = editing;
        this.callback = callback;
        long myId = WallStorage.currentUserId();
        this.draft = editing == null ? WallModels.newElement(WallModels.TYPE_TEXT, myId,
                WallStorage.displayName(myId), WallStorage.username(myId)) : cloneElement(editing);
        if (WallModels.TYPE_POLL.equals(draft.type) && draft.poll == null) {
            draft.poll = new WallPoll();
        }
        if (WallModels.TYPE_BUTTON.equals(draft.type) && draft.action == null) {
            draft.action = new WallAction();
            draft.action.kind = "url";
        }

        setApplyTopPadding(true);
        setApplyBottomPadding(true);
        setTitle(editing == null
                ? MiogramLocale.get("Новий елемент", "Новый элемент", "New element") + " ✦"
                : MiogramLocale.get("Редагувати елемент", "Редактировать элемент", "Edit element"));

        FrameLayout root = new FrameLayout(context);
        root.setBackground(WallUi.pill(WallUi.dark() ? 0xF2150F2E : 0xF8FFFFFF, 0x2EFFFFFF, 20));
        root.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(4), AndroidUtilities.dp(14), AndroidUtilities.dp(14));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        root.addView(scroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(column, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(context);
        WallUi.styleBody(hint, 11.5f, 0x99FFFFFF);
        hint.setText(MiogramLocale.get("Один елемент на людину · після публікації редагується",
                "Один элемент на человека · после публикации редактируется",
                "One element per person · editable after publishing"));
        column.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        HorizontalScrollView typeScroll = new HorizontalScrollView(context);
        typeScroll.setHorizontalScrollBarEnabled(false);
        typeRow = new LinearLayout(context);
        typeRow.setOrientation(LinearLayout.HORIZONTAL);
        typeScroll.addView(typeRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(typeScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        this.typeScroll = typeScroll;

        fieldsContainer = new LinearLayout(context);
        fieldsContainer.setOrientation(LinearLayout.VERTICAL);
        column.addView(fieldsContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout styleBlock = new LinearLayout(context);
        styleBlock.setOrientation(LinearLayout.HORIZONTAL);
        TextView styleLabel = new TextView(context);
        styleLabel.setText(MiogramLocale.get("Стиль", "Стиль", "Style"));
        WallUi.styleBody(styleLabel, 12, 0x88FFFFFF);
        LinearLayout styleLabelWrap = new LinearLayout(context);
        styleLabelWrap.setGravity(Gravity.CENTER_VERTICAL);
        styleLabelWrap.addView(styleLabel);
        styleBlock.addView(styleLabelWrap, LayoutHelper.createLinear(AndroidUtilities.dp(46), LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        HorizontalScrollView styleScroll = new HorizontalScrollView(context);
        styleScroll.setHorizontalScrollBarEnabled(false);
        styleRow = new LinearLayout(context);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);
        styleScroll.addView(styleRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        styleBlock.addView(styleScroll, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        column.addView(styleBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout sizeBlock = new LinearLayout(context);
        sizeBlock.setOrientation(LinearLayout.HORIZONTAL);
        TextView sizeLabel = new TextView(context);
        sizeLabel.setText(MiogramLocale.get("Ширина", "Ширина", "Width"));
        WallUi.styleBody(sizeLabel, 12, 0x88FFFFFF);
        LinearLayout sizeLabelWrap = new LinearLayout(context);
        sizeLabelWrap.setGravity(Gravity.CENTER_VERTICAL);
        sizeLabelWrap.addView(sizeLabel);
        sizeBlock.addView(sizeLabelWrap, LayoutHelper.createLinear(AndroidUtilities.dp(46), LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        HorizontalScrollView sizeScroll = new HorizontalScrollView(context);
        sizeScroll.setHorizontalScrollBarEnabled(false);
        sizeRow = new LinearLayout(context);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        sizeScroll.addView(sizeRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sizeBlock.addView(sizeScroll, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        column.addView(sizeBlock, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        errorView = new TextView(context);
        errorView.setTextColor(0xFFFF6B81);
        errorView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        errorView.setVisibility(View.GONE);
        column.addView(errorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        publishBtn = new TextView(context);
        publishBtn.setGravity(Gravity.CENTER);
        publishBtn.setMinHeight(AndroidUtilities.dp(46));
        publishBtn.setTextColor(0xFFFFFFFF);
        publishBtn.setTypeface(AndroidUtilities.bold());
        publishBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        publishBtn.setBackground(WallUi.brandPill(23));
        publishBtn.setText(original == null
                ? MiogramLocale.get("Опублікувати", "Опубликовать", "Publish")
                : MiogramLocale.get("Зберегти", "Сохранить", "Save"));
        column.addView(publishBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(root);
        publishBtn.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            String err = validateAndFill();
            if (err != null) {
                showError(err);
                return;
            }
            hideError();
            if (callback != null) {
                callback.onPublished(draft);
            }
            dismiss();
        });

        buildTypes();
        buildStyleChips();
        buildSizeChips();
        buildFields();
    }

    private static WallElement cloneElement(WallElement src) {
        try {
            com.google.gson.Gson g = new com.google.gson.Gson();
            return g.fromJson(g.toJson(src), WallElement.class);
        } catch (Throwable t) {
            return src; // рідкісний випадок, гаджет без gson — редагуємо як є
        }
    }

    // ---------- чипи ----------

    private TextView buildChip(Context context, String text, boolean selected) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(selected ? 0xFFFFFFFF : 0xB3FFFFFF);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        chip.setMinHeight(AndroidUtilities.dp(32));
        chip.setBackground(selected ? WallUi.brandPill(16) : WallUi.pill(0x18FFFFFF, 0x22FFFFFF, 16));
        return chip;
    }

    private void buildTypes() {
        Context context = getContext();
        if (context == null || typeRow == null) return;
        typeRow.removeAllViews();
        String[] labels = {
                MiogramLocale.get("Текст", "Текст", "Text"),
                MiogramLocale.get("Фото", "Фото", "Photo"),
                MiogramLocale.get("Гіфка", "Гифка", "GIF"),
                MiogramLocale.get("Відео", "Видео", "Video"),
                MiogramLocale.get("Стикер", "Стикер", "Sticker"),
                MiogramLocale.get("Файл", "Файл", "File"),
                MiogramLocale.get("Переслане", "Пересланное", "Forward"),
                MiogramLocale.get("Опитування", "Опрос", "Poll"),
                MiogramLocale.get("Малюнок", "Рисунок", "Drawing"),
                MiogramLocale.get("Кнопка", "Кнопка", "Button"),
                MiogramLocale.get("HTML", "HTML", "HTML")
        };
        int selectedIdx = 0;
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            if (TYPE_KEYS[i].equals(draft.type)) selectedIdx = i;
        }
        for (int i = 0; i < TYPE_KEYS.length; i++) {
            final String key = TYPE_KEYS[i];
            boolean selected = key.equals(draft.type);
            TextView chip = buildChip(context, TYPE_EMOJI[i] + " " + labels[i], selected);
            chip.setOnClickListener(v -> {
                WallUi.hapticSelect(v);
                selectType(key);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(8);
            typeRow.addView(chip, lp);
        }
        final int idx = selectedIdx;
        typeRow.post(() -> {
            try {
                View child = typeRow.getChildAt(idx);
                if (child != null && typeScroll != null) {
                    typeScroll.smoothScrollTo(Math.max(0, child.getLeft() - AndroidUtilities.dp(60)), 0);
                }
            } catch (Throwable ignore) {
            }
        });
    }

    private void selectType(String type) {
        if (type.equals(draft.type)) return;
        draft.type = type;
        resetTypeSpecifics(type);
        buildTypes();
        buildFields();
        buildSizeChips();
        hideError();
    }

    private void resetTypeSpecifics(String type) {
        hasMedia = false;
        draft.mediaPath = "";
        draft.thumbPath = "";
        draft.mediaWidth = 0;
        draft.mediaHeight = 0;
        draft.fileName = "";
        draft.mime = "";
        draft.fileSize = 0;
        if (!WallModels.TYPE_STICKER.equals(type)) {
            draft.emoji = "";
        }
        if (WallModels.TYPE_BUTTON.equals(type)) {
            if (draft.action == null) {
                draft.action = new WallAction();
                draft.action.kind = "url";
                draft.action.value = "";
            }
        } else {
            draft.action = null;
        }
        if (WallModels.TYPE_POLL.equals(type)) {
            if (draft.poll == null) {
                draft.poll = new WallPoll();
            }
        } else {
            draft.poll = null;
        }
        if (WallModels.TYPE_FORWARD.equals(type)) {
            draft.subtitle = "";
            draft.sourceUrl = "";
        }
        if (WallModels.TYPE_POLL.equals(type) || WallModels.TYPE_DRAWING.equals(type)
                || WallModels.TYPE_HTML.equals(type) || WallModels.TYPE_BUTTON.equals(type)
                || WallModels.TYPE_FORWARD.equals(type)) {
            draft.size = "full";
        }
    }

    private void buildStyleChips() {
        Context context = getContext();
        if (context == null || styleRow == null) return;
        styleRow.removeAllViews();
        String[] keys = {WallModels.STYLE_GLASS, WallModels.STYLE_NEON, WallModels.STYLE_PAPER, WallModels.STYLE_NIGHT};
        String[] labels = {
                MiogramLocale.get("Скло", "Стекло", "Glass"),
                MiogramLocale.get("Неон", "Неон", "Neon"),
                MiogramLocale.get("Папір", "Бумага", "Paper"),
                MiogramLocale.get("Ніч", "Ночь", "Night")
        };
        for (int i = 0; i < keys.length; i++) {
            final String key = keys[i];
            boolean selected = key.equals(draft.style);
            TextView chip = buildChip(context, labels[i], selected);
            chip.setMinHeight(AndroidUtilities.dp(28));
            chip.setOnClickListener(v -> {
                WallUi.hapticSelect(v);
                draft.style = key;
                buildStyleChips();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(6);
            styleRow.addView(chip, lp);
        }
    }

    private void buildSizeChips() {
        Context context = getContext();
        if (context == null || sizeRow == null) return;
        sizeRow.removeAllViews();
        boolean locked = WallModels.TYPE_POLL.equals(draft.type) || WallModels.TYPE_DRAWING.equals(draft.type);
        String[] labels = {
                MiogramLocale.get("Пів стіни", "Половина стены", "Half"),
                MiogramLocale.get("Повна", "Полная", "Full")
        };
        for (int i = 0; i < 2; i++) {
            final String key = i == 0 ? "half" : "full";
            boolean selected = i == 0 ? key.equals(draft.size) : key.equals(draft.size) || locked;
            TextView chip = buildChip(context, labels[i], selected);
            chip.setAlpha(!locked || i == 1 ? 1f : 0.55f);
            chip.setMinHeight(AndroidUtilities.dp(28));
            chip.setOnClickListener(v -> {
                if (locked && "half".equals(key)) {
                    WallUi.hapticTap(v);
                    return;
                }
                WallUi.hapticSelect(v);
                draft.size = key;
                buildSizeChips();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(6);
            sizeRow.addView(chip, lp);
        }
    }

    // ---------- поля ----------

    private EditText addInput(Context context, LinearLayout parent, String hint, String value, boolean multiline) {
        EditText input = new EditText(context);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14);
        input.setTextColor(WallUi.textPrimary());
        input.setHintTextColor(0x66FFFFFF);
        input.setHint(hint);
        input.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x12000000, 0x22FFFFFF, 12));
        input.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        input.setText(value == null ? "" : value);
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setMinLines(2);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        parent.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
        return input;
    }

    private void addSectionTitle(LinearLayout parent, String text) {
        TextView tv = new TextView(getContext());
        WallUi.styleBody(tv, 12, 0x99FFFFFF);
        tv.setText(text);
        parent.addView(tv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 2));
    }

    private interface WatcherSink {
        void accept(String value);
    }

    private TextWatcher simpleWatcher(WatcherSink sink) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                sink.accept(s == null ? "" : s.toString());
            }
        };
    }

    private void buildFields() {
        Context context = getContext();
        if (context == null || fieldsContainer == null) return;
        fieldsContainer.removeAllViews();
        previewRefresher = null;
        drawingRefresher = null;
        switch (draft.type) {
            case WallModels.TYPE_PHOTO:
            case WallModels.TYPE_GIF:
                buildMediaFields(context, draft.type);
                break;
            case WallModels.TYPE_VIDEO:
                buildMediaFields(context, WallModels.TYPE_VIDEO);
                break;
            case WallModels.TYPE_STICKER:
                buildStickerFields(context);
                break;
            case WallModels.TYPE_FILE:
                buildMediaFields(context, WallModels.TYPE_FILE);
                break;
            case WallModels.TYPE_FORWARD:
                buildForwardFields(context);
                break;
            case WallModels.TYPE_POLL:
                buildPollFields(context);
                break;
            case WallModels.TYPE_DRAWING:
                buildDrawingFields(context);
                break;
            case WallModels.TYPE_BUTTON:
                buildButtonFields(context);
                break;
            case WallModels.TYPE_HTML:
                buildHtmlFields(context);
                break;
            case WallModels.TYPE_TEXT:
            default:
                buildTextFields(context);
                break;
        }
        // підбір стилю/розміру для спецтипів
        buildSizeChips();
    }

    private void buildTextFields(Context context) {
        EditText input = addInput(context, fieldsContainer,
                MiogramLocale.get("Що хочеш написати?", "Что хочешь написать?", "What do you want to say?"),
                draft.text, true);
        input.setMinLines(3);
        TextView counter = new TextView(context);
        counter.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        counter.setTextColor(0x77FFFFFF);
        counter.setGravity(Gravity.END);
        counter.setText(draft.text.length() + "/500");
        fieldsContainer.addView(counter, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, -2, 0, 6));
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 500) {
                    s.delete(500, s.length());
                    return;
                }
                draft.text = s.toString();
                counter.setText(s.length() + "/500");
            }
        });
    }

    private String describeType() {
        switch (draft.type) {
            case WallModels.TYPE_GIF:
                return MiogramLocale.get("Гіфка", "Гифка", "GIF");
            case WallModels.TYPE_VIDEO:
                return MiogramLocale.get("Відео", "Видео", "Video");
            case WallModels.TYPE_FILE:
                return MiogramLocale.get("Файл", "Файл", "File");
            default:
                return MiogramLocale.get("Фото", "Фото", "Photo");
        }
    }

    private void buildMediaFields(Context context, String type) {
        addSectionTitle(fieldsContainer, describeType());
        FrameLayout previewWrap = new FrameLayout(context);
        previewWrap.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x12000000, 0x33FFFFFF, 14));

        BackupImageView preview = new BackupImageView(context);
        preview.setAspectFit(true);
        TextView emptyLabel = new TextView(context);
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setTextColor(0x88FFFFFF);
        previewWrap.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(120)));
        previewWrap.addView(emptyLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(120), Gravity.CENTER));
        fieldsContainer.addView(previewWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        TextView fileName = new TextView(context);
        fileName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        fileName.setTextColor(0x99FFFFFF);
        fileName.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        fileName.setSingleLine(true);
        fieldsContainer.addView(fileName, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView pickBtn = new TextView(context);
        pickBtn.setGravity(Gravity.CENTER);
        pickBtn.setMinHeight(AndroidUtilities.dp(42));
        pickBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        pickBtn.setTextColor(0xFFFFFFFF);
        pickBtn.setTypeface(AndroidUtilities.bold());
        pickBtn.setBackground(WallUi.pill(0x22FFFFFF, 0x66FFFFFF, 21));
        pickBtn.setText(MiogramLocale.get("Обрати файл з пристрою", "Выбрать файл с устройства", "Pick a file from device"));
        pickBtn.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            launchMediaPicker();
        });
        fieldsContainer.addView(pickBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        FrameLayout pw = previewWrap;
        Runnable refresh = () -> {
            boolean video = WallModels.TYPE_VIDEO.equals(draft.type);
            boolean file = WallModels.TYPE_FILE.equals(draft.type);
            if (hasMedia && !file && !(video && TextUtils.isEmpty(draft.thumbPath))) {
                String src = video && !TextUtils.isEmpty(draft.thumbPath) ? draft.thumbPath : draft.mediaPath;
                preview.setImage(ImageLocation.getForPath(src == null ? "" : src), "600_900", (Drawable) null, null);
                preview.setVisibility(View.VISIBLE);
                emptyLabel.setVisibility(View.GONE);
            } else {
                preview.setVisibility(View.GONE);
                emptyLabel.setVisibility(View.VISIBLE);
                emptyLabel.setText(file ? "📎 " : video ? "🎬 " : "🖼 ");
            }
            fileName.setText(hasMedia
                    ? "📄 " + draft.fileName + " · " + WallUi.formatSize(draft.fileSize)
                    : MiogramLocale.get("тацни «Обрати»", "тацни «Выбрать»", "tap «Pick»"));
        };
        refresh.run();
        previewRefresher = refresh;
    }

    private void buildStickerFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Емодзі-стікер", "Эмодзи-стикер", "Emoji sticker"));
        EditText input = addInput(context, fieldsContainer, "🫧", draft.emoji, false);
        input.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 28);
        input.setGravity(Gravity.CENTER);
        input.addTextChangedListener(simpleWatcher(s -> draft.emoji = s.toString()));
        HorizontalScrollView quick = new HorizontalScrollView(context);
        quick.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] presets = {"✨", "💖", "🔥", "🌈", "🫧", "🪩", "🌙", "🦋", "🍒", "🎧", "🧿", "🤍"};
        for (String e : presets) {
            TextView b = new TextView(context);
            b.setText(e);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 22);
            b.setGravity(Gravity.CENTER);
            b.setBackground(WallUi.pill(0x18FFFFFF, 0x22FFFFFF, 14));
            b.setOnClickListener(v -> {
                WallUi.hapticTap(v);
                draft.emoji = e;
                input.setText(e);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(46), AndroidUtilities.dp(46));
            lp.rightMargin = AndroidUtilities.dp(8);
            row.addView(b, lp);
        }
        quick.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fieldsContainer.addView(quick, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
    }

    private void buildForwardFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Переслане повідомлення", "Пересланное сообщение", "Forwarded message"));
        EditText text = addInput(context, fieldsContainer, MiogramLocale.get("Текст повідомлення", "Текст сообщения", "Message text"), draft.text, true);
        text.addTextChangedListener(simpleWatcher(s -> draft.text = s.toString()));
        EditText from = addInput(context, fieldsContainer, MiogramLocale.get("Від кого (необов'язково)", "От кого (необязательно)", "From (optional)"), draft.subtitle, false);
        from.addTextChangedListener(simpleWatcher(s -> draft.subtitle = s.toString()));
        EditText link = addInput(context, fieldsContainer, "t.me/…", draft.sourceUrl, false);
        link.addTextChangedListener(simpleWatcher(s -> draft.sourceUrl = s.toString()));
        TextView tip = new TextView(context);
        tip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
        tip.setTextColor(0x77FFFFFF);
        tip.setText(MiogramLocale.get("Швидше: у чаті довго-тапни повідомлення → «На стіну»",
                "Быстрее: в чате долго-тапни сообщение → «На стену»",
                "Faster: long-tap a message in chat → \"To wall\""));
        fieldsContainer.addView(tip, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
    }

    private void buildPollFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Опитування-слайдер", "Опрос-слайдер", "Slider poll"));
        if (draft.poll == null) {
            draft.poll = new WallPoll();
        }
        EditText q = addInput(context, fieldsContainer, MiogramLocale.get("Питання", "Вопрос", "Question"), draft.text, true);
        q.addTextChangedListener(simpleWatcher(s -> draft.text = s.toString()));
        EditText minLabel = addInput(context, fieldsContainer, "0 — «…»", draft.poll.minLabel == null ? "0" : draft.poll.minLabel, false);
        minLabel.addTextChangedListener(simpleWatcher(s -> draft.poll.minLabel = s.toString()));
        EditText maxLabel = addInput(context, fieldsContainer, "100 — «…»", draft.poll.maxLabel == null ? "100" : draft.poll.maxLabel, false);
        maxLabel.addTextChangedListener(simpleWatcher(s -> draft.poll.maxLabel = s.toString()));

        TextView hueCaption = new TextView(context);
        hueCaption.setText(MiogramLocale.get("Колір градієнта", "Цвет градиента", "Gradient color"));
        WallUi.styleBody(hueCaption, 12, 0x99FFFFFF);
        fieldsContainer.addView(hueCaption, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 6));
        HorizontalScrollView quick = new HorizontalScrollView(context);
        quick.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int[][] presets = {
                {0xFFFF4D8D, 0xFF8A5CFF},
                {0xFF35E0FF, 0xFF9DFF5C},
                {0xFFFFC24D, 0xFFFF5CD6},
                {0xFF5CE1FF, 0xFFFF4D8D},
                {0xFFB4FF5C, 0xFF35B9FF}
        };
        for (int i = 0; i < presets.length; i++) {
            final int[] pair = presets[i];
            String colorKey = String.format("#%06X", pair[0] & 0xFFFFFF);
            boolean selected = draft.poll.color1 != null && draft.poll.color1.equalsIgnoreCase(colorKey);
            TextView b = new TextView(context);
            b.setBackground(gradient(pair[0], pair[1]));
            b.setText(selected ? " ✓" : " ");
            if (selected) {
                b.setTypeface(AndroidUtilities.bold());
                b.setTextColor(0xFFFFFFFF);
            } else {
                b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
            }
            b.setGravity(Gravity.CENTER);
            b.setOnClickListener(v -> {
                WallUi.hapticTap(v);
                draft.poll.color1 = String.format("#%06X", pair[0] & 0xFFFFFF);
                draft.poll.color2 = String.format("#%06X", pair[1] & 0xFFFFFF);
                buildFields();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(46), AndroidUtilities.dp(28));
            lp.rightMargin = AndroidUtilities.dp(8);
            row.addView(b, lp);
        }
        quick.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fieldsContainer.addView(quick, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
    }

    private android.graphics.drawable.GradientDrawable gradient(int a, int b) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, new int[]{a, b});
        d.setCornerRadius(AndroidUtilities.dp(14));
        return d;
    }

    private void buildDrawingFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Намалюй на чистому місці стіни", "Нарисуй на чистом месте стены", "Draw on a clean spot of the wall"));
        FrameLayout previewWrap = new FrameLayout(context);
        previewWrap.setBackground(WallUi.pill(WallUi.dark() ? 0x18FFFFFF : 0x12000000, 0x33FFFFFF, 14));
        BackupImageView preview = new BackupImageView(context);
        preview.setAspectFit(true);
        TextView emptyLabel = new TextView(context);
        emptyLabel.setText("✏ " + MiogramLocale.get("полотно", "полотно", "canvas"));
        emptyLabel.setGravity(Gravity.CENTER);
        emptyLabel.setTextColor(0x88FFFFFF);
        previewWrap.addView(preview, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(120)));
        previewWrap.addView(emptyLabel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(120)));
        fieldsContainer.addView(previewWrap, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        TextView open = new TextView(context);
        open.setGravity(Gravity.CENTER);
        open.setMinHeight(AndroidUtilities.dp(42));
        open.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        open.setTextColor(0xFFFFFFFF);
        open.setTypeface(AndroidUtilities.bold());
        open.setBackground(WallUi.pill(0x22FFFFFF, 0x66FFFFFF, 21));
        open.setText(!TextUtils.isEmpty(draft.mediaPath)
                ? MiogramLocale.get("Малювати ще раз", "Рисовать еще раз", "Draw again")
                : MiogramLocale.get("Відкрити полотно", "Открыть полотно", "Open canvas"));
        open.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            launchCanvas();
        });
        fieldsContainer.addView(open, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        Runnable ref = () -> {
            if (!TextUtils.isEmpty(draft.mediaPath)) {
                preview.setImage(ImageLocation.getForPath(draft.mediaPath), "600_900", (Drawable) null, null);
                emptyLabel.setVisibility(View.GONE);
                preview.setVisibility(View.VISIBLE);
            } else {
                preview.setVisibility(View.GONE);
                emptyLabel.setVisibility(View.VISIBLE);
            }
        };
        ref.run();
        drawingRefresher = ref;
    }

    private void buildButtonFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Кнопка-дія", "Кнопка-действие", "Action button"));
        if (draft.action == null) {
            draft.action = new WallAction();
            draft.action.kind = "url";
        }
        EditText label = addInput(context, fieldsContainer, MiogramLocale.get("Підпис кнопки", "Подпись кнопки", "Button label"), draft.text, false);
        label.addTextChangedListener(simpleWatcher(s -> draft.text = s.toString()));
        TextView kindsCaption = new TextView(context);
        kindsCaption.setText(MiogramLocale.get("Що робить кнопка?", "Что делает кнопка?", "What does the button do?"));
        WallUi.styleBody(kindsCaption, 12, 0x99FFFFFF);
        fieldsContainer.addView(kindsCaption, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 6));
        String[] kinds = {"url", "copy", "chat"};
        String[] labels = {
                MiogramLocale.get("Відкрити посилання", "Открыть ссылку", "Open link"),
                MiogramLocale.get("Скопіювати текст", "Скопировать текст", "Copy text"),
                MiogramLocale.get("Відкрити чат @user", "Открыть чат @user", "Open chat @user")
        };
        LinearLayout kindsRow = new LinearLayout(context);
        kindsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < kinds.length; i++) {
            final String key = kinds[i];
            boolean selected = key.equals(draft.action.kind);
            TextView chip = buildChip(context, labels[i], selected);
            chip.setOnClickListener(v -> {
                WallUi.hapticSelect(v);
                draft.action.kind = key;
                buildFields();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(6);
            kindsRow.addView(chip, lp);
        }
        fieldsContainer.addView(kindsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        EditText value = addInput(context, fieldsContainer,
                "copy".equals(draft.action.kind) ? "текст для копіювання" : "chat".equals(draft.action.kind) ? "@username" : "https://t.me/…",
                draft.action.value, false);
        value.addTextChangedListener(simpleWatcher(s -> draft.action.value = s.toString()));
    }

    private void buildHtmlFields(Context context) {
        addSectionTitle(fieldsContainer, MiogramLocale.get("Шаблони — потім можна змінити", "Шаблоны — потом можно изменить", "Templates — editable later"));
        HorizontalScrollView tplScroll = new HorizontalScrollView(context);
        tplScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tplRow = new LinearLayout(context);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] tpl : HTML_TEMPLATES) {
            TextView chip = buildChip(context, "⚡ " + tpl[0], false);
            chip.setOnClickListener(v -> {
                WallUi.hapticTap(v);
                draft.html = tpl[1];
                draft.css = tpl[2];
                draft.js = tpl[3];
                buildFields();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = AndroidUtilities.dp(6);
            tplRow.addView(chip, lp);
        }
        tplScroll.addView(tplRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        fieldsContainer.addView(tplScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        addSectionTitle(fieldsContainer, "HTML");
        EditText html = addInput(context, fieldsContainer, "<div>…</div>", draft.html, true);
        html.setMinLines(2);
        html.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        html.setTypeface(android.graphics.Typeface.MONOSPACE);
        html.addTextChangedListener(simpleWatcher(s -> draft.html = s.toString()));

        addSectionTitle(fieldsContainer, "CSS");
        EditText css = addInput(context, fieldsContainer, ".class { color: #fff }", draft.css, true);
        css.setMinLines(2);
        css.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        css.setTypeface(android.graphics.Typeface.MONOSPACE);
        css.addTextChangedListener(simpleWatcher(s -> draft.css = s.toString()));

        addSectionTitle(fieldsContainer, "JS");
        EditText js = addInput(context, fieldsContainer, "// стилі анімуються прямо на стіні", draft.js, true);
        js.setMinLines(2);
        js.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 12);
        js.setTypeface(android.graphics.Typeface.MONOSPACE);
        js.addTextChangedListener(simpleWatcher(s -> draft.js = s.toString()));
    }

    // ---------- пікер медіа та полотно ----------

    private void launchMediaPicker() {
        try {
            if (parentFragment == null || parentFragment.getParentActivity() == null) return;
            String mime;
            switch (draft.type) {
                case WallModels.TYPE_GIF:
                    mime = "image/gif";
                    break;
                case WallModels.TYPE_VIDEO:
                    mime = "video/*";
                    break;
                case WallModels.TYPE_FILE:
                    mime = "*/*";
                    break;
                default:
                    mime = "image/*";
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType(mime);
            parentFragment.startActivityForResult(intent, WallActivity.REQ_MEDIA);
        } catch (Throwable e) {
            showError("Не вдалося відкрити вибір файлу");
        }
    }

    private void launchCanvas() {
        try {
            if (parentFragment == null || parentFragment.getParentActivity() == null) return;
            Intent intent = new Intent(parentFragment.getParentActivity(), WallDrawActivity.class);
            parentFragment.startActivityForResult(intent, WallActivity.REQ_DRAW);
        } catch (Throwable ignore) {
        }
    }

    public boolean handleActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == WallActivity.REQ_DRAW) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null) {
                String path = data.getStringExtra("path");
                if (!TextUtils.isEmpty(path)) {
                    draft.mediaPath = path;
                    draft.fileSize = new java.io.File(path).length();
                    draft.mime = "image/png";
                    draft.fileName = "drawing.png";
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inJustDecodeBounds = true;
                    try {
                        android.graphics.BitmapFactory.decodeFile(path, o);
                        draft.mediaWidth = o.outWidth;
                        draft.mediaHeight = o.outHeight;
                    } catch (Throwable ignore) {
                    }
                    hasMedia = true;
                    draft.size = "full";
                    if (drawingRefresher != null) drawingRefresher.run();
                    buildSizeChips();
                }
            }
            return true;
        }
        if (requestCode == WallActivity.REQ_MEDIA) {
            if (resultCode == android.app.Activity.RESULT_OK && data != null && data.getData() != null) {
                Uri uri = data.getData();
                if (parentFragment == null || parentFragment.getParentActivity() == null) return true;
                WallStorage.importFile(parentFragment.getParentActivity(), uri, wall.ownerId, null,
                        (path, thumb, name, mime, size, width, height) -> {
                            boolean gifExpected = WallModels.TYPE_GIF.equals(draft.type);
                            if (gifExpected && mime != null && !mime.toLowerCase().contains("gif")) {
                                showError("Це не гіфка — оберіть саме GIF-файл 🎞");
                                return;
                            }
                            draft.mediaPath = path;
                            draft.thumbPath = thumb == null ? "" : thumb;
                            draft.fileName = name == null ? "" : name;
                            draft.mime = mime == null ? "" : mime;
                            draft.fileSize = size;
                            draft.mediaWidth = width;
                            draft.mediaHeight = height;
                            hasMedia = true;
                            hideError();
                            if (previewRefresher != null) previewRefresher.run();
                        }, message -> showError(message == null ? "" : message));
            }
            return true;
        }
        return false;
    }

    // ---------- публікація ----------

    private String validateAndFill() {
        switch (draft.type) {
            case WallModels.TYPE_TEXT:
            case WallModels.TYPE_FORWARD:
                if (draft.text == null || draft.text.trim().isEmpty()) {
                    return "Текст порожній";
                }
                break;
            case WallModels.TYPE_PHOTO:
            case WallModels.TYPE_GIF:
            case WallModels.TYPE_VIDEO:
            case WallModels.TYPE_FILE:
                if (!hasMedia || TextUtils.isEmpty(draft.mediaPath)) {
                    return "Обери файл";
                }
                break;
            case WallModels.TYPE_STICKER:
                if (draft.emoji == null || draft.emoji.trim().isEmpty()) {
                    return "Обери емодзі";
                }
                break;
            case WallModels.TYPE_POLL:
                if (draft.text == null || draft.text.trim().isEmpty()) {
                    return "Сформулюй питання";
                }
                if (draft.poll == null) {
                    draft.poll = new WallPoll();
                }
                break;
            case WallModels.TYPE_DRAWING:
                if (TextUtils.isEmpty(draft.mediaPath)) {
                    return "Спочатку намалюй";
                }
                break;
            case WallModels.TYPE_BUTTON:
                if (draft.action == null || draft.action.value == null || draft.action.value.trim().isEmpty()) {
                    return "Кнопка потребує дії (посилання/текст/@юзер)";
                }
                if (draft.text == null || draft.text.trim().isEmpty()) {
                    draft.text = "Кнопка";
                }
                break;
            case WallModels.TYPE_HTML:
                if ((draft.html == null || draft.html.trim().isEmpty())
                        && (draft.css == null || draft.css.trim().isEmpty())
                        && (draft.js == null || draft.js.trim().isEmpty())) {
                    return "Додай HTML, CSS або JS";
                }
                break;
            default:
                break;
        }
        // ліміт «1 елемент на людину» для створення
        long myId = WallStorage.currentUserId();
        if (original == null) {
            WallElement mine = WallStorage.findElementOfAuthor(wall, myId);
            if (mine != null) {
                return "У тебе вже є елемент на цій стіні — редагуй його";
            }
        }
        return null;
    }

    private void showError(String text) {
        if (TextUtils.isEmpty(text)) return;
        errorView.setText(text);
        errorView.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }
}
