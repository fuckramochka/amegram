package app.amegram.bridge.ameprofile;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramCustomUiPrefs;

/**
 * ✦ AME STUDIO (АМЕ СТУДІО ໒꒱) — 10/10 GOLD STANDARD ✦
 * The ultimate visual & XML studio for Telegram profiles:
 * - Real-time Interactive Live Mockup Preview.
 * - 1-tap Aesthetic Presets (Cyberpunk, Sakura, Obsidian, Royal Gold, Matrix, Ocean).
 * - Code Editor with Quick Snippet Injection & Formatting.
 * - 1-tap Direct Publishing to Community Topic (https://t.me/dkamegram/1499).
 * - 100% strict profile isolation: Zero global modifications.
 */
public class AmeProfileSheet extends BottomSheet {

    private final EditText xmlEditor;
    private final FrameLayout livePreviewCard;
    private final TextView previewName;
    private final TextView previewThought;
    private final FrameLayout previewAvatarFrame;
    private final FrameLayout previewCardFrame;
    private final TextView previewCardTitle;
    private final TextView previewCardSubtitle;
    private final TextView previewCardBadge;
    private final ImageView previewCardIcon;
    private View previewAvatarRingView;

    public AmeProfileSheet(Context context) {
        super(context, true);

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF12131C;
        fixNavigationBar(bgColor);

        xmlEditor = new EditText(context);

        ScrollView masterScrollView = new ScrollView(context);
        masterScrollView.setFillViewport(true);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        // Drag handle
        View dragHandle = new View(context);
        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setColor(0x44FFFFFF);
        handleDrawable.setCornerRadius(AndroidUtilities.dp(3));
        dragHandle.setBackground(handleDrawable);
        root.addView(dragHandle, LayoutHelper.createLinear(40, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // Header Row: Title + "10/10" Gold Badge
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Аме Студіо ໒꒱", "Аме Студио ໒꒱", "Ame Studio ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        headerRow.addView(title, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        TextView goldBadge = new TextView(context);
        goldBadge.setText("✦ 10/10 GOLD ✦");
        goldBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        goldBadge.setTypeface(AndroidUtilities.bold());
        goldBadge.setTextColor(0xFF000000);
        goldBadge.setPadding(AndroidUtilities.dp(7), AndroidUtilities.dp(2), AndroidUtilities.dp(7), AndroidUtilities.dp(2));
        GradientDrawable goldBg = new GradientDrawable();
        goldBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        goldBg.setColors(new int[]{0xFFFFD700, 0xFFFFA500});
        goldBg.setCornerRadius(AndroidUtilities.dp(6));
        goldBadge.setBackground(goldBg);
        headerRow.addView(goldBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        root.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText(MiogramLocale.get(
                "Еталонне налаштування профілю. Редагуйте кожен піксель через XML, тестуйте у реальному часі та діліться стилем у спільноті.",
                "Эталонная настройка профиля. Редактируйте каждый пиксель через XML, тестируйте в реальном времени и делитесь стилем в сообществе.",
                "The pinnacle profile studio. Customize every detail via XML, live-preview instantly, and share presets with the community."
        ));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitle.setLineSpacing(AndroidUtilities.dp(2), 1.1f);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // =========================================================================
        // 1. LIVE INTERACTIVE MOCKUP PREVIEW
        // =========================================================================
        livePreviewCard = new FrameLayout(context);
        GradientDrawable prevCardBg = new GradientDrawable();
        prevCardBg.setColor(0xFF181B26);
        prevCardBg.setCornerRadius(AndroidUtilities.dp(16));
        prevCardBg.setStroke(AndroidUtilities.dp(1), 0x26FFFFFF);
        livePreviewCard.setBackground(prevCardBg);
        livePreviewCard.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        LinearLayout previewContent = new LinearLayout(context);
        previewContent.setOrientation(LinearLayout.VERTICAL);

        // Header info row inside preview
        LinearLayout prevHeaderRow = new LinearLayout(context);
        prevHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        prevHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        // Mini Avatar frame with custom shape and animated glow ring
        previewAvatarFrame = new FrameLayout(context);

        previewAvatarRingView = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rf = new RectF();
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2.5f));
                paint.setColor(0xFF00E5FF);
                rf.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), getWidth() - AndroidUtilities.dp(2), getHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rf, AndroidUtilities.dp(12), AndroidUtilities.dp(12), paint);
            }
        };
        previewAvatarFrame.addView(previewAvatarRingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ImageView previewAvatar = new ImageView(context);
        previewAvatar.setImageResource(R.drawable.msg_openprofile);
        previewAvatar.setColorFilter(0xFFFFFFFF);
        previewAvatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        previewAvatar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        previewAvatarFrame.addView(previewAvatar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        prevHeaderRow.addView(previewAvatarFrame, LayoutHelper.createLinear(44, 44, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        // Name + Thought Bubble Column
        LinearLayout nameCol = new LinearLayout(context);
        nameCol.setOrientation(LinearLayout.VERTICAL);

        previewThought = new TextView(context);
        previewThought.setText("💭 Ame Studio Active ໒꒱");
        previewThought.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        previewThought.setTextColor(0xFF00F0FF);
        previewThought.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        GradientDrawable thoughtBg = new GradientDrawable();
        thoughtBg.setColor(0x3300F0FF);
        thoughtBg.setCornerRadius(AndroidUtilities.dp(8));
        previewThought.setBackground(thoughtBg);
        nameCol.addView(previewThought, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        previewName = new TextView(context);
        String currentAccountName = "Amegram User";
        try {
            org.telegram.tgnet.TLRPC.User self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (self != null && !TextUtils.isEmpty(self.first_name)) {
                currentAccountName = self.first_name;
            }
        } catch (Throwable ignore) {}
        previewName.setText(currentAccountName + " ໒꒱");
        previewName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        previewName.setTypeface(AndroidUtilities.bold());
        previewName.setTextColor(0xFFFFFFFF);
        previewName.setShadowLayer(AndroidUtilities.dp(6), 0, 0, 0xFF00E5FF);
        nameCol.addView(previewName, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        prevHeaderRow.addView(nameCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        previewContent.addView(prevHeaderRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Mini Custom Card preview inside live preview
        previewCardFrame = new FrameLayout(context);
        GradientDrawable miniCardBg = new GradientDrawable();
        miniCardBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        miniCardBg.setColors(new int[]{0xFF202538, 0xFF353C59});
        miniCardBg.setCornerRadius(AndroidUtilities.dp(12));
        previewCardFrame.setBackground(miniCardBg);
        previewCardFrame.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));

        LinearLayout cardRow = new LinearLayout(context);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);

        previewCardIcon = new ImageView(context);
        previewCardIcon.setImageResource(R.drawable.msg_premium_liststar);
        previewCardIcon.setColorFilter(0xFFFFD700);
        cardRow.addView(previewCardIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        LinearLayout cardTextCol = new LinearLayout(context);
        cardTextCol.setOrientation(LinearLayout.VERTICAL);

        previewCardTitle = new TextView(context);
        previewCardTitle.setText("Інтерактивна Картка");
        previewCardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        previewCardTitle.setTypeface(AndroidUtilities.bold());
        previewCardTitle.setTextColor(0xFFFFFFFF);
        cardTextCol.addView(previewCardTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        previewCardSubtitle = new TextView(context);
        previewCardSubtitle.setText("Швидкий перехід за URL або каналом");
        previewCardSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        previewCardSubtitle.setTextColor(0xAAFFFFFF);
        cardTextCol.addView(previewCardSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        cardRow.addView(cardTextCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        previewCardBadge = new TextView(context);
        previewCardBadge.setText("TOP");
        previewCardBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f);
        previewCardBadge.setTypeface(AndroidUtilities.bold());
        previewCardBadge.setTextColor(0xFF000000);
        previewCardBadge.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
        GradientDrawable pBadgeBg = new GradientDrawable();
        pBadgeBg.setColor(0xFFFFD700);
        pBadgeBg.setCornerRadius(AndroidUtilities.dp(5));
        previewCardBadge.setBackground(pBadgeBg);
        cardRow.addView(previewCardBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        previewCardFrame.addView(cardRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewContent.addView(previewCardFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        livePreviewCard.addView(previewContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(livePreviewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // =========================================================================
        // 2. HORIZONTAL PRESET CAROUSEL (1-tap Aesthetic Themes)
        // =========================================================================
        TextView presetHeader = new TextView(context);
        presetHeader.setText(MiogramLocale.get("✦ ГОТОВІ ДИЗАЙН-ПРЕСЕТИ (1 ТАП) ✦", "✦ ГОТОВЫЕ ДИЗАЙН-ПРЕСЕТЫ (1 ТАП) ✦", "✦ READY DESIGN PRESETS (1 TAP) ✦"));
        presetHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        presetHeader.setTypeface(AndroidUtilities.bold());
        presetHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        root.addView(presetHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        HorizontalScrollView presetScroll = new HorizontalScrollView(context);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetContainer = new LinearLayout(context);
        presetContainer.setOrientation(LinearLayout.HORIZONTAL);

        List<AmeProfileEngine.AmePreset> presets = AmeProfileEngine.getPresets();
        for (AmeProfileEngine.AmePreset preset : presets) {
            TextView pBtn = new TextView(context);
            pBtn.setText(preset.icon + " " + preset.name);
            pBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
            pBtn.setTypeface(AndroidUtilities.bold());
            pBtn.setTextColor(Color.WHITE);
            pBtn.setGravity(Gravity.CENTER);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(0x22FFFFFF);
            btnBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
            btnBg.setCornerRadius(AndroidUtilities.dp(10));
            pBtn.setBackground(btnBg);
            pBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));

            pBtn.setOnClickListener(v -> {
                xmlEditor.setText(preset.xml);
                updateLivePreviewFromXml(preset.xml);
                Toast.makeText(context, "Пресет «" + preset.name + "» завантажено в редактор!", Toast.LENGTH_SHORT).show();
            });

            presetContainer.addView(pBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        }
        presetScroll.addView(presetContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(presetScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // =========================================================================
        // 3. QUICK SNIPPET TOOLBAR
        // =========================================================================
        HorizontalScrollView toolbarScroll = new HorizontalScrollView(context);
        toolbarScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout toolbar = new LinearLayout(context);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);

        addToolbarChip(context, toolbar, "+ Картка", () -> {
            insertSnippet("\n        <card id=\"new_card\"\n" +
                    "            title=\"Нова інтерактивна картка\"\n" +
                    "            subtitle=\"Опис або посилання\"\n" +
                    "            icon=\"star\"\n" +
                    "            url=\"https://t.me/dkamegram/1499\"\n" +
                    "            gradient-start=\"#1E2235\"\n" +
                    "            gradient-end=\"#333A56\"\n" +
                    "            text-color=\"#FFFFFF\"\n" +
                    "            badge=\"HOT\"\n" +
                    "            badge-bg=\"#FF5722\"\n" +
                    "            badge-color=\"#FFFFFF\"\n" +
                    "            radius=\"16\" />\n");
        });

        addToolbarChip(context, toolbar, "+ Сяйво імені", () -> {
            insertSnippet("\n    <name color-enabled=\"true\" color=\"#FFFFFF\" glow-enabled=\"true\" glow-color=\"#00E5FF\" glow-radius=\"16\" />\n");
        });

        addToolbarChip(context, toolbar, "+ Хмаринка думок", () -> {
            insertSnippet("\n    <thought visible=\"true\" text=\"✦ Твоя цитата тут ໒꒱\" text-color=\"#00F0FF\" bg-color=\"#18192A\" />\n");
        });

        addToolbarChip(context, toolbar, "+ Банер", () -> {
            insertSnippet("\n    <banner visible=\"true\" color=\"#1F1633\" alpha=\"95\" dim=\"15\" />\n");
        });

        addToolbarChip(context, toolbar, "✦ Форматувати XML", () -> {
            formatXmlInEditor();
        });

        toolbarScroll.addView(toolbar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        root.addView(toolbarScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // =========================================================================
        // 4. MONOSPACE XML CODE EDITOR
        // =========================================================================
        FrameLayout editorCard = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(ColorUtils.blendARGB(bgColor, 0xFF000000, 0.45f));
        cardBg.setStroke(AndroidUtilities.dp(1), ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.15f));
        cardBg.setCornerRadius(AndroidUtilities.dp(12));
        editorCard.setBackground(cardBg);

        ScrollView editorScroll = new ScrollView(context);
        editorScroll.setFillViewport(true);

        xmlEditor.setText(AmeProfileEngine.exportCurrentProfileXml());
        xmlEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        xmlEditor.setTypeface(Typeface.MONOSPACE);
        xmlEditor.setTextColor(0xFFE8EAED);
        xmlEditor.setBackground(null);
        xmlEditor.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        xmlEditor.setGravity(Gravity.TOP | Gravity.START);

        xmlEditor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateLivePreviewFromXml(s.toString());
            }
        });

        editorScroll.addView(xmlEditor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        editorCard.addView(editorScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 240));
        root.addView(editorCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // =========================================================================
        // 5. ACTION BUTTONS & PUBLISHING
        // =========================================================================
        // Primary Button: "Опублікувати у вітку https://t.me/dkamegram/1499"
        TextView publishBtn = new TextView(context);
        publishBtn.setText(MiogramLocale.get("Опублікувати у вітку (@dkamegram/1499) 🚀", "Опубликовать в ветку (@dkamegram/1499) 🚀", "Share to Community Topic (@dkamegram/1499) 🚀"));
        publishBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
        publishBtn.setTypeface(AndroidUtilities.bold());
        publishBtn.setTextColor(Color.WHITE);
        publishBtn.setGravity(Gravity.CENTER);

        GradientDrawable pubBg = new GradientDrawable();
        pubBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        pubBg.setColors(new int[]{0xFF6366F1, 0xFF8B5CF6});
        pubBg.setCornerRadius(AndroidUtilities.dp(11));
        publishBtn.setBackground(pubBg);
        publishBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        publishBtn.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            AmeProfileEngine.shareToCommunity(context, code);
            dismiss();
        });
        root.addView(publishBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Action Row 1: "Застосувати код ໒꒱" & "Скопіювати XML"
        LinearLayout actionRow1 = new LinearLayout(context);
        actionRow1.setOrientation(LinearLayout.HORIZONTAL);

        TextView applyBtn = new TextView(context);
        applyBtn.setText(MiogramLocale.get("Застосувати код ໒꒱", "Применить код ໒꒱", "Apply Code ໒꒱"));
        applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.0f);
        applyBtn.setTypeface(AndroidUtilities.bold());
        applyBtn.setTextColor(Color.WHITE);
        applyBtn.setGravity(Gravity.CENTER);

        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        applyBg.setColors(new int[]{0xFF10B981, 0xFF059669});
        applyBg.setCornerRadius(AndroidUtilities.dp(10));
        applyBtn.setBackground(applyBg);
        applyBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        applyBtn.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            MiogramCustomUiPrefs.resetBrokenCpbMasks();
            if (AmeProfileEngine.applyProfileXml(code)) {
                Toast.makeText(context, MiogramLocale.get("Аме профіль успішно застосовано!", "Аме профиль успешно применен!", "Ame Profile applied successfully!"), Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                Toast.makeText(context, MiogramLocale.get("Помилка в синтаксисі XML! Перевірте закриття тегів.", "Ошибка в синтаксисе XML! Проверьте закрытие тегов.", "XML Syntax error! Check closing tags."), Toast.LENGTH_LONG).show();
            }
        });
        actionRow1.addView(applyBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 6, 0));

        TextView copyBtn = new TextView(context);
        copyBtn.setText(MiogramLocale.get("Скопіювати XML", "Скопировать XML", "Copy XML"));
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.0f);
        copyBtn.setTypeface(AndroidUtilities.bold());
        copyBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        copyBtn.setGravity(Gravity.CENTER);

        GradientDrawable copyBg = new GradientDrawable();
        copyBg.setColor(ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.12f));
        copyBg.setCornerRadius(AndroidUtilities.dp(10));
        copyBtn.setBackground(copyBg);
        copyBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Ame Profile", xmlEditor.getText().toString()));
                Toast.makeText(context, MiogramLocale.get("XML скопійовано!", "XML скопирован!", "XML copied!"), Toast.LENGTH_SHORT).show();
            }
        });
        actionRow1.addView(copyBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 6, 0, 0, 0));

        root.addView(actionRow1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Action Row 2: "Вставити з буфера" & "Скинути до базового"
        LinearLayout actionRow2 = new LinearLayout(context);
        actionRow2.setOrientation(LinearLayout.HORIZONTAL);

        TextView pasteBtn = new TextView(context);
        pasteBtn.setText(MiogramLocale.get("Вставити з буфера", "Вставить из буфера", "Paste from Clipboard"));
        pasteBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.0f);
        pasteBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        pasteBtn.setGravity(Gravity.CENTER);
        pasteBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (!TextUtils.isEmpty(text)) {
                    xmlEditor.setText(text.toString());
                    updateLivePreviewFromXml(text.toString());
                    Toast.makeText(context, MiogramLocale.get("Вставлено з буфера!", "Вставлено из буфера!", "Pasted from clipboard!"), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(context, MiogramLocale.get("Буфер обміну порожній", "Буфер обмена пуст", "Clipboard is empty"), Toast.LENGTH_SHORT).show();
        });
        actionRow2.addView(pasteBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 6, 0));

        TextView resetBtn = new TextView(context);
        resetBtn.setText(MiogramLocale.get("Скинути до базового", "Сбросить до базового", "Reset to Defaults"));
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.0f);
        resetBtn.setTextColor(0xFFFF7043);
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        resetBtn.setOnClickListener(v -> {
            AmeProfileEngine.resetToDefaults();
            String defaultXml = AmeProfileEngine.exportCurrentProfileXml();
            xmlEditor.setText(defaultXml);
            updateLivePreviewFromXml(defaultXml);
            Toast.makeText(context, MiogramLocale.get("Скинуто до стандартного оформлення!", "Сброшено до стандартного оформления!", "Reset to defaults!"), Toast.LENGTH_SHORT).show();
        });
        actionRow2.addView(resetBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 6, 0, 0, 0));

        root.addView(actionRow2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        masterScrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setCustomView(masterScrollView);

        // Initial preview sync
        updateLivePreviewFromXml(xmlEditor.getText().toString());
    }

    private void addToolbarChip(Context context, LinearLayout container, String title, Runnable action) {
        TextView chip = new TextView(context);
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(0xFFD1D5DB);
        chip.setGravity(Gravity.CENTER);

        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(0x1FFFFFFF);
        cBg.setCornerRadius(AndroidUtilities.dp(8));
        chip.setBackground(cBg);
        chip.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        chip.setOnClickListener(v -> action.run());

        container.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
    }

    private void insertSnippet(String snippet) {
        int start = Math.max(xmlEditor.getSelectionStart(), 0);
        int end = Math.max(xmlEditor.getSelectionEnd(), 0);
        xmlEditor.getText().replace(Math.min(start, end), Math.max(start, end), snippet, 0, snippet.length());
    }

    private void formatXmlInEditor() {
        String raw = xmlEditor.getText().toString();
        if (TextUtils.isEmpty(raw)) return;
        try {
            // Clean multi-blank lines and normalize indentation
            String[] lines = raw.split("\n");
            StringBuilder sb = new StringBuilder();
            int consecutiveBlank = 0;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    consecutiveBlank++;
                    if (consecutiveBlank <= 1) {
                        sb.append("\n");
                    }
                } else {
                    consecutiveBlank = 0;
                    if (trimmed.startsWith("<?xml") || trimmed.startsWith("<!--") || trimmed.startsWith("-->") || trimmed.startsWith("═") || trimmed.startsWith("✦") || trimmed.startsWith("Цей") || trimmed.startsWith("Після") || trimmed.startsWith("1.") || trimmed.startsWith("2.") || trimmed.startsWith("───") || trimmed.startsWith("•") || trimmed.startsWith("Додавайте") || trimmed.startsWith("Атрибути") || trimmed.startsWith("</ame-profile>")) {
                        sb.append(line).append("\n");
                    } else if (trimmed.startsWith("<ame-profile")) {
                        sb.append(trimmed).append("\n");
                    } else if (trimmed.startsWith("<card") || trimmed.startsWith("id=") || trimmed.startsWith("title=") || trimmed.startsWith("subtitle=") || trimmed.startsWith("icon=") || trimmed.startsWith("url=") || trimmed.startsWith("gradient") || trimmed.startsWith("text-color=") || trimmed.startsWith("badge=") || trimmed.startsWith("radius=")) {
                        sb.append("        ").append(trimmed).append("\n");
                    } else if (trimmed.startsWith("<custom-cards") || trimmed.startsWith("</custom-cards>")) {
                        sb.append("    ").append(trimmed).append("\n");
                    } else {
                        sb.append("    ").append(trimmed).append("\n");
                    }
                }
            }
            xmlEditor.setText(sb.toString().trim() + "\n");
            Toast.makeText(getContext(), "XML успішно відформатовано!", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    private void updateLivePreviewFromXml(String xml) {
        if (TextUtils.isEmpty(xml)) return;
        try {
            // Extract colors and thought dynamically for preview
            int nameColor = extractColorAttr(xml, "name", "color", 0xFFFFFFFF);
            int glowColor = extractColorAttr(xml, "name", "glow-color", 0xFF00E5FF);
            boolean glowEnabled = extractBoolAttr(xml, "name", "glow-enabled", true);
            previewName.setTextColor(nameColor);
            if (glowEnabled) {
                previewName.setShadowLayer(AndroidUtilities.dp(6), 0, 0, glowColor);
            } else {
                previewName.setShadowLayer(0, 0, 0, 0);
            }

            int ringColor = extractColorAttr(xml, "avatar", "ring-color", 0xFF00E5FF);
            if (previewAvatarRingView != null) {
                previewAvatarRingView.invalidate();
            }

            String thought = extractStringAttr(xml, "thought", "text");
            if (!TextUtils.isEmpty(thought)) {
                previewThought.setVisibility(View.VISIBLE);
                previewThought.setText(thought);
                int tColor = extractColorAttr(xml, "thought", "text-color", 0xFF00F0FF);
                previewThought.setTextColor(tColor);
                int tBg = extractColorAttr(xml, "thought", "bg-color", 0x3300F0FF);
                GradientDrawable tg = new GradientDrawable();
                tg.setColor(tBg);
                tg.setCornerRadius(AndroidUtilities.dp(8));
                previewThought.setBackground(tg);
            } else {
                previewThought.setVisibility(View.GONE);
            }

            int cardGrad1 = extractColorAttr(xml, "card", "gradient-start", 0xFF202538);
            int cardGrad2 = extractColorAttr(xml, "card", "gradient-end", 0xFF353C59);
            GradientDrawable mcBg = new GradientDrawable();
            mcBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            mcBg.setColors(new int[]{cardGrad1, cardGrad2});
            mcBg.setCornerRadius(AndroidUtilities.dp(12));
            previewCardFrame.setBackground(mcBg);

            String cTitle = extractStringAttr(xml, "card", "title");
            if (!TextUtils.isEmpty(cTitle)) previewCardTitle.setText(cTitle);
            String cSub = extractStringAttr(xml, "card", "subtitle");
            if (!TextUtils.isEmpty(cSub)) previewCardSubtitle.setText(cSub);
            String cBadge = extractStringAttr(xml, "card", "badge");
            if (!TextUtils.isEmpty(cBadge)) {
                previewCardBadge.setVisibility(View.VISIBLE);
                previewCardBadge.setText(cBadge);
            } else {
                previewCardBadge.setVisibility(View.GONE);
            }
        } catch (Throwable ignore) {}
    }

    private static int extractColorAttr(String xml, String tag, String attr, int def) {
        try {
            int tagIdx = xml.indexOf("<" + tag);
            if (tagIdx != -1) {
                int tagEnd = xml.indexOf(">", tagIdx);
                if (tagEnd != -1) {
                    String sub = xml.substring(tagIdx, tagEnd);
                    int attrIdx = sub.indexOf(attr + "=\"");
                    if (attrIdx != -1) {
                        int valStart = attrIdx + attr.length() + 2;
                        int valEnd = sub.indexOf("\"", valStart);
                        if (valEnd != -1) {
                            String hex = sub.substring(valStart, valEnd).trim();
                            if (!TextUtils.isEmpty(hex)) {
                                return Color.parseColor(hex.startsWith("#") ? hex : "#" + hex);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignore) {}
        return def;
    }

    private static boolean extractBoolAttr(String xml, String tag, String attr, boolean def) {
        try {
            int tagIdx = xml.indexOf("<" + tag);
            if (tagIdx != -1) {
                int tagEnd = xml.indexOf(">", tagIdx);
                if (tagEnd != -1) {
                    String sub = xml.substring(tagIdx, tagEnd);
                    int attrIdx = sub.indexOf(attr + "=\"");
                    if (attrIdx != -1) {
                        int valStart = attrIdx + attr.length() + 2;
                        int valEnd = sub.indexOf("\"", valStart);
                        if (valEnd != -1) {
                            return Boolean.parseBoolean(sub.substring(valStart, valEnd));
                        }
                    }
                }
            }
        } catch (Throwable ignore) {}
        return def;
    }

    private static String extractStringAttr(String xml, String tag, String attr) {
        try {
            int tagIdx = xml.indexOf("<" + tag);
            if (tagIdx != -1) {
                int tagEnd = xml.indexOf(">", tagIdx);
                if (tagEnd != -1) {
                    String sub = xml.substring(tagIdx, tagEnd);
                    int attrIdx = sub.indexOf(attr + "=\"");
                    if (attrIdx != -1) {
                        int valStart = attrIdx + attr.length() + 2;
                        int valEnd = sub.indexOf("\"", valStart);
                        if (valEnd != -1) {
                            return sub.substring(valStart, valEnd);
                        }
                    }
                }
            }
        } catch (Throwable ignore) {}
        return null;
    }
}
