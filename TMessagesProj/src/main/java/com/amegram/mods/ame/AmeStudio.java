package com.amegram.mods.ame;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.ui.ActionBar.AlertDialog;


import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.List;



/**
 * Ame Profile Studio.
 * Streamlined, elegant, and modular customization sheet for Telegram profiles.
 * Features:
 * - Two uncluttered views: "Дизайн" (Visual preview + quick controls + presets) and "XML Код" (Monospace editor).
 * - Live interactive header mockup (avatar alignment, rotation, glow, banner, custom cards).
 * - Direct local media resolution & gallery picker.
 * - Minimal, high-aesthetic UI with zero button clutter.
 */
public class AmeStudio extends BottomSheet {

    private final EditText xmlEditor;
    private final FrameLayout livePreviewCard;
    private final LinearLayout prevHeaderRow;
    private final LinearLayout nameCol;
    private final TextView previewName;
    private final TextView previewThought;
    private final FrameLayout previewAvatarFrame;
    private final FrameLayout previewCardFrame;
    private final TextView previewCardTitle;
    private final TextView previewCardSubtitle;
    private final TextView previewCardBadge;
    private final ImageView previewCardIcon;
    private View previewAvatarRingView;

    private LinearLayout visualContainer;
    private LinearLayout codeContainer;
    private TextView tabVisualBtn;
    private TextView tabCodeBtn;

    public AmeStudio(Context context) {
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
        root.addView(dragHandle, LayoutHelper.createLinear(38, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // Top Bar: Title + Segmented Tab Switcher (Visual / Code)
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(context);
        title.setText("Ame Studio ໒꒱");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        topBar.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        // Segmented Tab Container
        LinearLayout tabSwitch = new LinearLayout(context);
        tabSwitch.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable tabSwitchBg = new GradientDrawable();
        tabSwitchBg.setColor(Blend.blendARGB(bgColor, 0xFFFFFFFF, 0.08f));
        tabSwitchBg.setCornerRadius(AndroidUtilities.dp(10));
        tabSwitch.setBackground(tabSwitchBg);
        tabSwitch.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));

        tabVisualBtn = new TextView(context);
        tabVisualBtn.setText(T.get("Дизайн", "Дизайн", "Design"));
        tabVisualBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tabVisualBtn.setTypeface(AndroidUtilities.bold());
        tabVisualBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));

        tabCodeBtn = new TextView(context);
        tabCodeBtn.setText("XML");
        tabCodeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tabCodeBtn.setTypeface(AndroidUtilities.bold());
        tabCodeBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));

        tabSwitch.addView(tabVisualBtn);
        tabSwitch.addView(tabCodeBtn);
        topBar.addView(tabSwitch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        root.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Containers for the two views
        visualContainer = new LinearLayout(context);
        visualContainer.setOrientation(LinearLayout.VERTICAL);

        codeContainer = new LinearLayout(context);
        codeContainer.setOrientation(LinearLayout.VERTICAL);
        codeContainer.setVisibility(View.GONE);

        tabVisualBtn.setOnClickListener(v -> switchTab(true));
        tabCodeBtn.setOnClickListener(v -> switchTab(false));
        updateTabStyles(true);

        // =========================================================================
        // VIEW 1: VISUAL DESIGN MODE
        // =========================================================================

        // 1. Live Interactive Mockup
        livePreviewCard = new FrameLayout(context);
        GradientDrawable prevCardBg = new GradientDrawable();
        prevCardBg.setColor(0xFF181B26);
        prevCardBg.setCornerRadius(AndroidUtilities.dp(14));
        prevCardBg.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
        livePreviewCard.setBackground(prevCardBg);
        livePreviewCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        LinearLayout previewContent = new LinearLayout(context);
        previewContent.setOrientation(LinearLayout.VERTICAL);

        prevHeaderRow = new LinearLayout(context);
        prevHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        prevHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        previewAvatarFrame = new FrameLayout(context);
        previewAvatarRingView = new View(context) {
            private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            private final RectF rf = new RectF();
            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(AndroidUtilities.dp(2f));
                paint.setColor(0xFF00E5FF);
                rf.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), getWidth() - AndroidUtilities.dp(2), getHeight() - AndroidUtilities.dp(2));
                canvas.drawRoundRect(rf, AndroidUtilities.dp(10), AndroidUtilities.dp(10), paint);
            }
        };
        previewAvatarFrame.addView(previewAvatarRingView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        ImageView previewAvatar = new ImageView(context);
        previewAvatar.setImageResource(R.drawable.msg_openprofile);
        previewAvatar.setColorFilter(0xFFFFFFFF);
        previewAvatar.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        previewAvatar.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        previewAvatarFrame.addView(previewAvatar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        nameCol = new LinearLayout(context);
        nameCol.setOrientation(LinearLayout.VERTICAL);

        previewThought = new TextView(context);
        previewThought.setText("💭 Ame Studio Active");
        previewThought.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
        previewThought.setTextColor(0xFF00F0FF);
        previewThought.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
        GradientDrawable thoughtBg = new GradientDrawable();
        thoughtBg.setColor(0x3300F0FF);
        thoughtBg.setCornerRadius(AndroidUtilities.dp(6));
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
        previewName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        previewName.setTypeface(AndroidUtilities.bold());
        previewName.setTextColor(0xFFFFFFFF);
        previewName.setShadowLayer(AndroidUtilities.dp(4), 0, 0, 0xFF00E5FF);
        nameCol.addView(previewName, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        prevHeaderRow.addView(previewAvatarFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
        prevHeaderRow.addView(nameCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));
        previewContent.addView(prevHeaderRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Mini Custom Card preview
        previewCardFrame = new FrameLayout(context);
        GradientDrawable miniCardBg = new GradientDrawable();
        miniCardBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        miniCardBg.setColors(new int[]{0xFF202538, 0xFF353C59});
        miniCardBg.setCornerRadius(AndroidUtilities.dp(10));
        previewCardFrame.setBackground(miniCardBg);
        previewCardFrame.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));

        LinearLayout cardRow = new LinearLayout(context);
        cardRow.setOrientation(LinearLayout.HORIZONTAL);
        cardRow.setGravity(Gravity.CENTER_VERTICAL);

        previewCardIcon = new ImageView(context);
        previewCardIcon.setImageResource(R.drawable.msg_premium_liststar);
        previewCardIcon.setColorFilter(0xFFFFD700);
        cardRow.addView(previewCardIcon, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        LinearLayout cardTextCol = new LinearLayout(context);
        cardTextCol.setOrientation(LinearLayout.VERTICAL);

        previewCardTitle = new TextView(context);
        previewCardTitle.setText("Інтерактивна картка");
        previewCardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        previewCardTitle.setTypeface(AndroidUtilities.bold());
        previewCardTitle.setTextColor(0xFFFFFFFF);
        cardTextCol.addView(previewCardTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        previewCardSubtitle = new TextView(context);
        previewCardSubtitle.setText("Швидкий перехід / інформація");
        previewCardSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        previewCardSubtitle.setTextColor(0xAAFFFFFF);
        cardTextCol.addView(previewCardSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        cardRow.addView(cardTextCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        previewCardBadge = new TextView(context);
        previewCardBadge.setText("TOP");
        previewCardBadge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8.5f);
        previewCardBadge.setTypeface(AndroidUtilities.bold());
        previewCardBadge.setTextColor(0xFF000000);
        previewCardBadge.setPadding(AndroidUtilities.dp(5), AndroidUtilities.dp(1), AndroidUtilities.dp(5), AndroidUtilities.dp(1));
        GradientDrawable pBadgeBg = new GradientDrawable();
        pBadgeBg.setColor(0xFFFFD700);
        pBadgeBg.setCornerRadius(AndroidUtilities.dp(4));
        previewCardBadge.setBackground(pBadgeBg);
        cardRow.addView(previewCardBadge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 4, 0, 0, 0));

        previewCardFrame.addView(cardRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewContent.addView(previewCardFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));

        livePreviewCard.addView(previewContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        visualContainer.addView(livePreviewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // 2. Presets Carousel (Clean modern pills)
        HorizontalScrollView presetScroll = new HorizontalScrollView(context);
        presetScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout presetContainer = new LinearLayout(context);
        presetContainer.setOrientation(LinearLayout.HORIZONTAL);

        List<AmeEngine.AmePreset> presets = AmeEngine.getPresets();
        for (AmeEngine.AmePreset preset : presets) {
            TextView pBtn = new TextView(context);
            pBtn.setText(preset.icon + " " + preset.name);
            pBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            pBtn.setTypeface(AndroidUtilities.bold());
            pBtn.setTextColor(Color.WHITE);
            pBtn.setGravity(Gravity.CENTER);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setColor(0x1FFFFFFF);
            btnBg.setCornerRadius(AndroidUtilities.dp(8));
            pBtn.setBackground(btnBg);
            pBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));

            pBtn.setOnClickListener(v -> {
                xmlEditor.setText(preset.xml);
                updateLivePreviewFromXml(preset.xml);
            });

            presetContainer.addView(pBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));
        }
        presetScroll.addView(presetContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        visualContainer.addView(presetScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // 3. Visual Quick Controls Row (Avatar alignment & tilt)
        LinearLayout controlsCard = new LinearLayout(context);
        controlsCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable ctrlBg = new GradientDrawable();
        ctrlBg.setColor(Blend.blendARGB(bgColor, 0xFFFFFFFF, 0.05f));
        ctrlBg.setCornerRadius(AndroidUtilities.dp(12));
        controlsCard.setBackground(ctrlBg);
        controlsCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        // Alignment Row
        LinearLayout alignRow = new LinearLayout(context);
        alignRow.setOrientation(LinearLayout.HORIZONTAL);
        alignRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView alignLabel = new TextView(context);
        alignLabel.setText(T.get("Розміщення аватарки:", "Размещение аватарки:", "Avatar Position:"));
        alignLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        alignLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        alignRow.addView(alignLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        alignRow.addView(createSmallChip(context, "Зліва", () -> updateXmlAttr("layout", "avatar-align", "left")));
        alignRow.addView(createSmallChip(context, "Центр", () -> updateXmlAttr("layout", "avatar-align", "center")));
        alignRow.addView(createSmallChip(context, "Справа", () -> updateXmlAttr("layout", "avatar-align", "right")));

        controlsCard.addView(alignRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Tilt & Glow Row
        LinearLayout tiltRow = new LinearLayout(context);
        tiltRow.setOrientation(LinearLayout.HORIZONTAL);
        tiltRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tiltLabel = new TextView(context);
        tiltLabel.setText(T.get("Нахил / Сяйво:", "Наклон / Сияние:", "Tilt / Glow:"));
        tiltLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        tiltLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        tiltRow.addView(tiltLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        tiltRow.addView(createSmallChip(context, "0°", () -> updateXmlAttr("avatar", "rotation", "0")));
        tiltRow.addView(createSmallChip(context, "10°", () -> updateXmlAttr("avatar", "rotation", "10")));
        tiltRow.addView(createSmallChip(context, "-10°", () -> updateXmlAttr("avatar", "rotation", "-10")));
        tiltRow.addView(createSmallChip(context, "✨ Сяйво", () -> {
            boolean cur = extractBoolAttr(xmlEditor.getText().toString(), "name", "glow-enabled", true);
            updateXmlAttr("name", "glow-enabled", cur ? "false" : "true");
        }));

        controlsCard.addView(tiltRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Photo / Media row
        LinearLayout mediaRow = new LinearLayout(context);
        mediaRow.setOrientation(LinearLayout.HORIZONTAL);
        mediaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView mediaLabel = new TextView(context);
        mediaLabel.setText(T.get("Медіа банера:", "Медиа баннера:", "Banner Media:"));
        mediaLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        mediaLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        mediaRow.addView(mediaLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        mediaRow.addView(createSmallChip(context, "🖼 Додати фото з галереї", () -> promptAddLocalMedia(context)));
        controlsCard.addView(mediaRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        visualContainer.addView(controlsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));
        root.addView(visualContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // =========================================================================
        // VIEW 2: XML CODE EDITOR MODE
        // =========================================================================
        HorizontalScrollView codeToolScroll = new HorizontalScrollView(context);
        codeToolScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout codeToolbar = new LinearLayout(context);
        codeToolbar.setOrientation(LinearLayout.HORIZONTAL);

        codeToolbar.addView(createSmallChip(context, "+ Картка", () -> insertSnippet("\n        <card id=\"new_card\"\n            title=\"Нова інтерактивна картка\"\n            subtitle=\"Опис або посилання\"\n            icon=\"star\"\n            url=\"https://t.me/dkamegram\"\n            gradient-start=\"#1E2235\"\n            gradient-end=\"#333A56\"\n            gradient-angle=\"45\"\n            text-color=\"#FFFFFF\"\n            badge=\"HOT\"\n            badge-bg=\"#FF5722\"\n            radius=\"14\" />\n")));
        codeToolbar.addView(createSmallChip(context, "+ Банер", () -> insertSnippet("\n    <banner visible=\"true\" type=\"color\" color=\"#1F1633\" gradient-start=\"#1F1633\" gradient-end=\"#3F2B96\" gradient-angle=\"45\" />\n")));
        codeToolbar.addView(createSmallChip(context, "+ Думка", () -> insertSnippet("\n    <thought visible=\"true\" text=\"✦ Твоя цитата тут ໒꒱\" text-color=\"#00F0FF\" bg-color=\"#18192A\" />\n")));
        codeToolbar.addView(createSmallChip(context, "✦ Форматувати", this::formatXmlInEditor));

        codeToolScroll.addView(codeToolbar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        codeContainer.addView(codeToolScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        FrameLayout editorCard = new FrameLayout(context);
        GradientDrawable editorBg = new GradientDrawable();
        editorBg.setColor(Blend.blendARGB(bgColor, 0xFF000000, 0.45f));
        editorBg.setStroke(AndroidUtilities.dp(1), Blend.blendARGB(bgColor, 0xFFFFFFFF, 0.12f));
        editorBg.setCornerRadius(AndroidUtilities.dp(12));
        editorCard.setBackground(editorBg);

        ScrollView editorScroll = new ScrollView(context);
        editorScroll.setFillViewport(true);

        xmlEditor.setText(AmeEngine.exportCurrentProfileXml());
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
        editorCard.addView(editorScroll, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 260));
        codeContainer.addView(editorCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        root.addView(codeContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // =========================================================================
        // UNIFIED BOTTOM ACTION BAR (Clean & Uncluttered)
        // =========================================================================
        // Primary apply button
        TextView applyBtn = new TextView(context);
        applyBtn.setText(T.get("Застосувати оформлення ໒꒱", "Применить оформление ໒꒱", "Apply Profile Design ໒꒱"));
        applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.0f);
        applyBtn.setTypeface(AndroidUtilities.bold());
        applyBtn.setTextColor(Color.WHITE);
        applyBtn.setGravity(Gravity.CENTER);

        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        applyBg.setColors(new int[]{0xFF6366F1, 0xFF8B5CF6});
        applyBg.setCornerRadius(AndroidUtilities.dp(10));
        applyBtn.setBackground(applyBg);
        applyBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));
        applyBtn.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            UiPrefs.resetBrokenCpbMasks();

            List<String> missing = AmeMedia.findMissingLocalKeys(code);
            if (!missing.isEmpty()) {
                promptMissingMedia(context, missing.get(0), code);
            } else {
                applyAndDismiss(context, code);
            }
        });
        root.addView(applyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 8));

        // Secondary actions: Copy XML, Paste XML, Reset to default
        LinearLayout secondaryBar = new LinearLayout(context);
        secondaryBar.setOrientation(LinearLayout.HORIZONTAL);
        secondaryBar.setGravity(Gravity.CENTER_VERTICAL);

        TextView copyBtn = new TextView(context);
        copyBtn.setText(T.get("Копіювати", "Копировать", "Copy"));
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        copyBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        copyBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        copyBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Ame Profile", xmlEditor.getText().toString()));
                Toast.makeText(context, T.get("XML скопійовано!", "XML скопирован!", "XML copied!"), Toast.LENGTH_SHORT).show();
            }
        });
        secondaryBar.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        TextView pasteBtn = new TextView(context);
        pasteBtn.setText(T.get("Вставити", "Вставить", "Paste"));
        pasteBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        pasteBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        pasteBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (!TextUtils.isEmpty(text)) {
                    xmlEditor.setText(text.toString());
                    updateLivePreviewFromXml(text.toString());
                    Toast.makeText(context, T.get("Вставлено!", "Вставлено!", "Pasted!"), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(context, T.get("Буфер обміну порожній", "Буфер обмена пуст", "Clipboard empty"), Toast.LENGTH_SHORT).show();
        });
        secondaryBar.addView(pasteBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        TextView resetBtn = new TextView(context);
        resetBtn.setText(T.get("Скинути", "Сбросить", "Reset"));
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        resetBtn.setTextColor(0xFFFF7043);
        resetBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        resetBtn.setOnClickListener(v -> {
            AmeEngine.resetToDefaults();
            String defaultXml = AmeEngine.exportCurrentProfileXml();
            xmlEditor.setText(defaultXml);
            updateLivePreviewFromXml(defaultXml);
            Toast.makeText(context, T.get("Скинуто!", "Сброшено!", "Reset!"), Toast.LENGTH_SHORT).show();
        });
        secondaryBar.addView(resetBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        View spacer = new View(context);
        secondaryBar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1.0f));

        TextView shareLink = new TextView(context);
        shareLink.setText("✦ @dkamegram");
        shareLink.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        shareLink.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        shareLink.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4));
        shareLink.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            AmeEngine.shareToCommunity(context, code);
            dismiss();
        });
        secondaryBar.addView(shareLink, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        root.addView(secondaryBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        masterScrollView.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        setCustomView(masterScrollView);

        updateLivePreviewFromXml(xmlEditor.getText().toString());
    }

    private void switchTab(boolean showVisual) {
        visualContainer.setVisibility(showVisual ? View.VISIBLE : View.GONE);
        codeContainer.setVisibility(showVisual ? View.GONE : View.VISIBLE);
        updateTabStyles(showVisual);
        if (showVisual) {
            updateLivePreviewFromXml(xmlEditor.getText().toString());
        }
    }

    private void updateTabStyles(boolean visualActive) {
        GradientDrawable activeBg = new GradientDrawable();
        activeBg.setColor(0x33FFFFFF);
        activeBg.setCornerRadius(AndroidUtilities.dp(7));

        if (visualActive) {
            tabVisualBtn.setBackground(activeBg);
            tabVisualBtn.setTextColor(0xFFFFFFFF);
            tabCodeBtn.setBackground(null);
            tabCodeBtn.setTextColor(0x88FFFFFF);
        } else {
            tabCodeBtn.setBackground(activeBg);
            tabCodeBtn.setTextColor(0xFFFFFFFF);
            tabVisualBtn.setBackground(null);
            tabVisualBtn.setTextColor(0x88FFFFFF);
        }
    }

    private TextView createSmallChip(Context context, String text, Runnable action) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(0xFFD1D5DB);
        chip.setGravity(Gravity.CENTER);

        GradientDrawable cBg = new GradientDrawable();
        cBg.setColor(0x1FFFFFFF);
        cBg.setCornerRadius(AndroidUtilities.dp(6));
        chip.setBackground(cBg);
        chip.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    private void updateXmlAttr(String tag, String attr, String value) {
        String xml = xmlEditor.getText().toString();
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
                        String newSub = sub.substring(0, valStart) + value + sub.substring(valEnd);
                        String newXml = xml.substring(0, tagIdx) + newSub + xml.substring(tagEnd);
                        xmlEditor.setText(newXml);
                        updateLivePreviewFromXml(newXml);
                        return;
                    }
                } else {
                    String newSub = sub.trim();
                    if (newSub.endsWith("/")) {
                        newSub = newSub.substring(0, newSub.length() - 1).trim() + " " + attr + "=\"" + value + "\" /";
                    } else {
                        newSub = newSub + " " + attr + "=\"" + value + "\"";
                    }
                    String newXml = xml.substring(0, tagIdx) + newSub + xml.substring(tagEnd);
                    xmlEditor.setText(newXml);
                    updateLivePreviewFromXml(newXml);
                    return;
                }
            }
        }
        insertSnippet(" " + attr + "=\"" + value + "\"");
    }

    private void applyAndDismiss(Context context, String code) {
        if (AmeEngine.applyProfileXml(code)) {
            Toast.makeText(context, T.get("Оформлення застосовано!", "Оформление применено!", "Profile applied!"), Toast.LENGTH_SHORT).show();
            dismiss();
        } else {
            Toast.makeText(context, T.get("Помилка в синтаксисі XML!", "Ошибка в синтаксисе XML!", "XML Syntax error!"), Toast.LENGTH_LONG).show();
        }
    }

    private void promptMissingMedia(Context context, String missingKey, String code) {
        Activity act = null;
        if (context instanceof Activity) {
            act = (Activity) context;
        }

        if (act == null) {
            applyAndDismiss(context, code);
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle("📸 Локальне медіа: local:" + missingKey);
        builder.setMessage("У вашому коді вказано «local:" + missingKey + "», але файл ще не обрано з галереї. Бажаєте обрати фото зараз?");
        final Activity finalAct = act;
        builder.setPositiveButton("Вибрати з галереї", (dialog, which) -> {
            AmeMedia.pickMedia(finalAct, missingKey, () -> {
                updateLivePreviewFromXml(xmlEditor.getText().toString());
                applyAndDismiss(context, code);
            });
        });
        builder.setNegativeButton("Продовжити так", (dialog, which) -> {
            applyAndDismiss(context, code);
        });
        builder.show();
    }

    private void promptAddLocalMedia(Context context) {
        Activity act = null;
        if (context instanceof Activity) {
            act = (Activity) context;
        }
        if (act == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(act);
        builder.setTitle("🖼 Обрати фото для профілю");
        builder.setMessage("Введіть ключ медіа (наприклад: photo1):");

        final EditText input = new EditText(act);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText("photo1");
        input.setSelectAllOnFocus(true);
        builder.setView(input);

        final Activity finalAct = act;
        builder.setPositiveButton("Обрати з галереї", (dialog, which) -> {
            String key = input.getText().toString().trim();
            if (TextUtils.isEmpty(key)) key = "photo1";
            final String finalKey = AmeMedia.cleanKey(key);
            AmeMedia.pickMedia(finalAct, finalKey, () -> {
                updateXmlAttr("banner", "src", "local:" + finalKey);
                updateLivePreviewFromXml(xmlEditor.getText().toString());
                Toast.makeText(context, "Фото збережено для local:" + finalKey + "!", Toast.LENGTH_SHORT).show();
            });
        });
        builder.setNegativeButton("Скасувати", null);
        builder.show();
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
                    if (trimmed.startsWith("<?xml") || trimmed.startsWith("<!--") || trimmed.startsWith("-->") || trimmed.startsWith("</ame-profile>")) {
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
            Toast.makeText(getContext(), T.get("XML відформатовано!", "XML отформатирован!", "XML formatted!"), Toast.LENGTH_SHORT).show();
        } catch (Throwable ignore) {}
    }

    private void updateLivePreviewFromXml(String xml) {
        if (TextUtils.isEmpty(xml)) return;
        try {
            // 1. Layout Positioning & Transforms
            String avatarAlign = extractStringAttr(xml, "layout", "avatar-align");
            if (avatarAlign == null) avatarAlign = extractStringAttr(xml, "avatar", "align");
            if (avatarAlign == null) avatarAlign = "left";

            float avatarRot = extractFloatAttr(xml, "avatar", "rotation", 0f);
            if (avatarRot == 0f) avatarRot = extractFloatAttr(xml, "layout", "avatar-rotation", 0f);
            previewAvatarFrame.setRotation(avatarRot);

            float nameRot = extractFloatAttr(xml, "name", "rotation", 0f);
            if (nameRot == 0f) nameRot = extractFloatAttr(xml, "layout", "name-rotation", 0f);
            previewName.setRotation(nameRot);

            // Dynamically reorder header row according to avatar alignment
            prevHeaderRow.removeAllViews();
            if ("right".equalsIgnoreCase(avatarAlign)) {
                prevHeaderRow.addView(nameCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));
                prevHeaderRow.addView(previewAvatarFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));
            } else if ("center".equalsIgnoreCase(avatarAlign)) {
                prevHeaderRow.addView(previewAvatarFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));
                prevHeaderRow.addView(nameCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));
            } else {
                prevHeaderRow.addView(previewAvatarFrame, LayoutHelper.createLinear(40, 40, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));
                prevHeaderRow.addView(nameCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));
            }

            // 2. Banner backdrop preview
            String bannerSrc = extractStringAttr(xml, "banner", "src");
            Bitmap bannerBmp = null;
            if (!TextUtils.isEmpty(bannerSrc)) {
                bannerBmp = AmeMedia.getInstance().getBitmap(bannerSrc);
            }

            int bGrad1 = extractColorAttr(xml, "banner", "gradient-start", 0);
            int bGrad2 = extractColorAttr(xml, "banner", "gradient-end", 0);
            float bAngle = extractFloatAttr(xml, "banner", "gradient-angle", 45f);

            if (bannerBmp != null) {
                BitmapDrawable bd = new BitmapDrawable(getContext().getResources(), bannerBmp);
                livePreviewCard.setBackground(bd);
            } else if (bGrad1 != 0 && bGrad2 != 0) {
                GradientDrawable bbg = AmeGradient.createGradient(bGrad1, bGrad2, 0, bAngle, 14);
                livePreviewCard.setBackground(bbg);
            } else {
                int bColor = extractColorAttr(xml, "banner", "color", 0xFF181B26);
                GradientDrawable pCardBg = new GradientDrawable();
                pCardBg.setColor(bColor != 0 ? bColor : 0xFF181B26);
                pCardBg.setCornerRadius(AndroidUtilities.dp(14));
                pCardBg.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
                livePreviewCard.setBackground(pCardBg);
            }

            // 3. Name styling & glow
            int nameColor = extractColorAttr(xml, "name", "color", 0xFFFFFFFF);
            int glowColor = extractColorAttr(xml, "name", "glow-color", 0xFF00E5FF);
            boolean glowEnabled = extractBoolAttr(xml, "name", "glow-enabled", true);
            previewName.setTextColor(nameColor);
            if (glowEnabled) {
                previewName.setShadowLayer(AndroidUtilities.dp(4), 0, 0, glowColor);
            } else {
                previewName.setShadowLayer(0, 0, 0, 0);
            }

            // 4. Ring color
            if (previewAvatarRingView != null) {
                previewAvatarRingView.invalidate();
            }

            // 5. Thought text & bubble
            String thought = extractStringAttr(xml, "thought", "text");
            if (!TextUtils.isEmpty(thought)) {
                previewThought.setVisibility(View.VISIBLE);
                previewThought.setText(thought);
                int tColor = extractColorAttr(xml, "thought", "text-color", 0xFF00F0FF);
                previewThought.setTextColor(tColor);
                int tBg = extractColorAttr(xml, "thought", "bg-color", 0x3300F0FF);
                GradientDrawable tg = new GradientDrawable();
                tg.setColor(tBg);
                tg.setCornerRadius(AndroidUtilities.dp(6));
                previewThought.setBackground(tg);
            } else {
                previewThought.setVisibility(View.GONE);
            }

            // 6. Custom Card preview styling
            int cardGrad1 = extractColorAttr(xml, "card", "gradient-start", 0xFF202538);
            int cardGrad2 = extractColorAttr(xml, "card", "gradient-end", 0xFF353C59);
            float cardAngle = extractFloatAttr(xml, "card", "gradient-angle", 45f);
            float cardRot = extractFloatAttr(xml, "card", "rotation", 0f);
            previewCardFrame.setRotation(cardRot);

            GradientDrawable mcBg = AmeGradient.createGradient(cardGrad1, cardGrad2, 0, cardAngle, 10);
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

    private static float extractFloatAttr(String xml, String tag, String attr, float def) {
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
                            return Float.parseFloat(sub.substring(valStart, valEnd));
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
