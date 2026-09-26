package app.miogram.bridge.onboarding;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.OvershootInterpolator;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.exteraless.plugins.ui.MiogramPluginCatalogAlert;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.companion.MiogramCompanionPrefs;
import app.miogram.bridge.ameprofile.MiogramAmeProfileEngine;
import app.miogram.bridge.ameprofile.MiogramAmeProfileSheet;
import app.miogram.bridge.cloudvault.MiogramCloudVaultEngine;
import app.miogram.bridge.customui.MiogramHaptic;
import app.miogram.bridge.flags.MiogramFlags;
import app.miogram.bridge.plugins.MiogramPluginsMarket;
import app.miogram.bridge.ui.MiogramVisualsPrefs;
import app.miogram.bridge.ui.discord.MiogramDiscordLayout;

/**
 * Modern First Launch & Update Guide for Amegram.
 * Triggered strictly ONCE per install/update.
 * Highlights Amegram's core features:
 * - Step 0: Persona Selection (Ame vs KAngel)
 * - Step 1: Аме Профіль (Live XML profile styling & community topic sharing)
 * - Step 2: Хмарне Сховище у Топіках (AES-256 Forum Supergroup with folder topics)
 * - Step 3: Плагіни та Маркет (Opt-in plugins selection: Boykisser, PetPet, Custom Profile WASM, Localizer)
 * - Step 4: Discord UI & Рідке Скло (Discord server layout & AGSL liquid glass)
 */
public class AmegramGuideSheet extends BottomSheet {

    private static final int TOTAL_STEPS = 5;
    private int currentStep = 0;

    private boolean selectedAme = true;
    private final int accentColor;
    private final int textColor;
    private final int subTextColor;
    private final int cardBgColor;

    private ImageView characterSpriteView;
    private TextView speechBubbleView;
    private LinearLayout dotsContainer;
    private FrameLayout stepContentContainer;
    private TextView skipStepBtn;
    private TextView nextStepBtn;

    // Plugins selection state
    private final List<CheckBox> pluginCheckBoxes = new ArrayList<>();
    private final List<MiogramPluginsMarket.MarketPluginEntry> pluginEntries = new ArrayList<>();

    public AmegramGuideSheet(Context context, boolean needFocus) {
        super(context, needFocus);

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF14151F;
        fixNavigationBar(bgColor);

        int accent = getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader);
        if (accent == 0) accent = 0xFF6C63FF;
        accentColor = accent;

        int tc = getThemedColor(Theme.key_dialogTextBlack);
        textColor = tc == 0 ? 0xFFFFFFFF : tc;
        int stc = getThemedColor(Theme.key_dialogTextGray2);
        subTextColor = stc == 0 ? 0xAAFFFFFF : stc;
        cardBgColor = ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.08f);

        selectedAme = MiogramCompanionPrefs.isAmeActive();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        // Drag Handle
        View dragHandle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(0x44888888);
        handleBg.setCornerRadius(AndroidUtilities.dp(2.5f));
        dragHandle.setBackground(handleBg);
        root.addView(dragHandle, LayoutHelper.createLinear(38, 5, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        // Top Navigation Bar
        LinearLayout topBar = new LinearLayout(context);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);

        dotsContainer = new LinearLayout(context);
        dotsContainer.setOrientation(LinearLayout.HORIZONTAL);
        dotsContainer.setGravity(Gravity.CENTER_VERTICAL);
        updateDots();
        topBar.addView(dotsContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        View spacer = new View(context);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 0, 1.0f));

        TextView skipAllBtn = new TextView(context);
        skipAllBtn.setText(MiogramLocale.get("Пропустити все", "Пропустить всё", "Skip all"));
        skipAllBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        skipAllBtn.setTextColor(subTextColor);
        skipAllBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        skipAllBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            finishOnboarding();
        });
        topBar.addView(skipAllBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        root.addView(topBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Character Sprite & Speech Bubble Header
        LinearLayout characterBox = new LinearLayout(context);
        characterBox.setOrientation(LinearLayout.HORIZONTAL);
        characterBox.setGravity(Gravity.CENTER_VERTICAL);
        characterBox.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        GradientDrawable charBoxBg = new GradientDrawable();
        charBoxBg.setColor(ColorUtils.blendARGB(bgColor, accentColor, 0.12f));
        charBoxBg.setCornerRadius(AndroidUtilities.dp(18));
        charBoxBg.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(accentColor, 60));
        characterBox.setBackground(charBoxBg);

        characterSpriteView = new ImageView(context);
        characterSpriteView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        characterBox.addView(characterSpriteView, LayoutHelper.createLinear(80, 80, Gravity.CENTER_VERTICAL));

        speechBubbleView = new TextView(context);
        speechBubbleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        speechBubbleView.setTextColor(textColor);
        speechBubbleView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);
        speechBubbleView.setLineSpacing(AndroidUtilities.dp(2), 1.05f);
        characterBox.addView(speechBubbleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        root.addView(characterBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Dynamic Step Content Area
        ScrollView contentScrollView = new ScrollView(context);
        contentScrollView.setVerticalScrollBarEnabled(false);
        stepContentContainer = new FrameLayout(context);
        contentScrollView.addView(stepContentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(contentScrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // Bottom Action Bar: [Пропустити крок] | [Далі / Завершити]
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, AndroidUtilities.dp(12), 0, 0);

        skipStepBtn = new TextView(context);
        skipStepBtn.setText(MiogramLocale.get("Пропустити крок", "Пропустить шаг", "Skip step"));
        skipStepBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        skipStepBtn.setTextColor(subTextColor);
        skipStepBtn.setGravity(Gravity.CENTER);
        skipStepBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        skipStepBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            nextStep();
        });
        bottomBar.addView(skipStepBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 46, 0, 0, 10, 0));

        nextStepBtn = new TextView(context);
        nextStepBtn.setText(MiogramLocale.get("Далі ໒꒱", "Далее ໒꒱", "Next ໒꒱"));
        nextStepBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        nextStepBtn.setTypeface(AndroidUtilities.bold());
        nextStepBtn.setTextColor(0xFFFFFFFF);
        nextStepBtn.setGravity(Gravity.CENTER);
        GradientDrawable nextBg = new GradientDrawable();
        nextBg.setColor(accentColor);
        nextBg.setCornerRadius(AndroidUtilities.dp(16));
        nextStepBtn.setBackground(nextBg);
        nextStepBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            handleNextAction();
        });
        bottomBar.addView(nextStepBtn, new LinearLayout.LayoutParams(0, AndroidUtilities.dp(46), 1.0f));

        root.addView(bottomBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        setCustomView(root);
        renderStep(0);
    }

    private void updateDots() {
        if (dotsContainer == null) return;
        dotsContainer.removeAllViews();
        Context ctx = getContext();
        for (int i = 0; i < TOTAL_STEPS; i++) {
            View dot = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(AndroidUtilities.dp(3));
            if (i == currentStep) {
                d.setColor(accentColor);
                dot.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(18), AndroidUtilities.dp(6)));
            } else {
                d.setColor(0x44FFFFFF);
                dot.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(6), AndroidUtilities.dp(6)));
            }
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
            lp.rightMargin = AndroidUtilities.dp(4);
            dotsContainer.addView(dot);
        }
    }

    private void bounceSprite() {
        if (characterSpriteView == null) return;
        ObjectAnimator bounce = ObjectAnimator.ofPropertyValuesHolder(
                characterSpriteView,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.85f, 1.08f, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.85f, 1.08f, 1.0f)
        );
        bounce.setDuration(350);
        bounce.setInterpolator(new OvershootInterpolator(1.4f));
        bounce.start();
    }

    private void updateCharacterState(int spriteRes, String speechText) {
        if (characterSpriteView != null) {
            characterSpriteView.setImageResource(spriteRes);
            bounceSprite();
        }
        if (speechBubbleView != null) {
            speechBubbleView.setText(speechText);
        }
    }

    private void renderStep(int step) {
        this.currentStep = step;
        updateDots();
        stepContentContainer.removeAllViews();
        Context context = getContext();

        switch (step) {
            case 0:
                renderStepPersona(context);
                break;
            case 1:
                renderStepAmeProfile(context);
                break;
            case 2:
                renderStepCloudVault(context);
                break;
            case 3:
                renderStepPlugins(context);
                break;
            case 4:
                renderStepDiscordAndGlass(context);
                break;
        }

        if (step == TOTAL_STEPS - 1) {
            nextStepBtn.setText(MiogramLocale.get("Розпочати подорож ✧", "Начать путешествие ✧", "Start Journey ✧"));
            skipStepBtn.setVisibility(View.GONE);
        } else {
            nextStepBtn.setText(MiogramLocale.get("Далі ໒꒱", "Далее ໒꒱", "Next ໒꒱"));
            skipStepBtn.setVisibility(View.VISIBLE);
        }
    }

    /**
     * STEP 0: Persona Selection (Ame vs KAngel)
     */
    private void renderStepPersona(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Привіт! Я Аме ໒꒱. Допоможу тобі налаштувати затишний Amegram, розібратися з фішками та персоналізувати клієнт.",
                "Привет! Я Аме ໒꒱. Помогу настроить уютный Amegram, разобраться с фишками и персонализировать клиент.",
                "Hi! I'm Ame ໒꒱. I'll help you set up cozy Amegram, explore key features, and customize your app.")
                : MiogramLocale.get(
                "†Внеси пожертву у світлі!† Я Кангель ✧. Зробимо твій клієнт найяскравішим та найпотужнішим у Всесвіті!",
                "†Вознеси молитву во славу!† Я Кангель ✧. Сделаем твой клиент самым ярким и мощным во Вселенной!",
                "†Pray to the light!† I am KAngel ✧. Let's make your client the brightest and most powerful in the universe!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_happy : R.drawable.miogram_ai_kangel_happy,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Обери свою супутницю", "Выбери свою спутницу", "Choose your companion"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(12));
        layout.addView(title);

        LinearLayout cardsRow = new LinearLayout(context);
        cardsRow.setOrientation(LinearLayout.HORIZONTAL);

        LinearLayout ameCard = createPersonaCard(
                context,
                "Аме (Ame-chan ໒꒱)",
                MiogramLocale.get("Лампова стрімерка", "Ламповая стримерша", "Cozy indie streamer"),
                MiogramLocale.get("Щира, спокійна, розмовляє душевно та любить нічний затишок.",
                        "Искренняя, спокойная, душевно общается и любит ночной уют.",
                        "Honest, calm, soulfully chatting and loves cozy nights."),
                R.drawable.miogram_ai_ame_avatar,
                selectedAme
        );

        LinearLayout kangelCard = createPersonaCard(
                context,
                "Кангель (KAngel ✧)",
                MiogramLocale.get("Кібер-ідол Всесвіту", "Кибер-идол Вселенной", "Cyber-idol of the universe"),
                MiogramLocale.get("Сяюча, гіперактивна, обожнює увагу, естетику та драйв.",
                        "Сияющая, гиперактивная, обожает внимание, эстетику и драйв.",
                        "Radiant, hyperactive, adores attention, aesthetics and hype."),
                R.drawable.miogram_ai_kangel_avatar,
                !selectedAme
        );

        ameCard.setOnClickListener(v -> {
            MiogramHaptic.select(v);
            selectedAme = true;
            MiogramCompanionPrefs.setActiveCompanion(MiogramCompanionPrefs.COMPANION_AME);
            renderStepPersona(context);
        });

        kangelCard.setOnClickListener(v -> {
            MiogramHaptic.select(v);
            selectedAme = false;
            MiogramCompanionPrefs.setActiveCompanion(MiogramCompanionPrefs.COMPANION_KANGEL);
            renderStepPersona(context);
        });

        cardsRow.addView(ameCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        cardsRow.addView(new View(context), LayoutHelper.createLinear(12, 1));
        cardsRow.addView(kangelCard, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        layout.addView(cardsRow);
        stepContentContainer.addView(layout);
    }

    private LinearLayout createPersonaCard(Context context, String name, String subtitle, String desc, int avatarRes, boolean isSelected) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(16));
        if (isSelected) {
            bg.setColor(ColorUtils.blendARGB(cardBgColor, accentColor, 0.22f));
            bg.setStroke(AndroidUtilities.dp(2), accentColor);
        } else {
            bg.setColor(cardBgColor);
            bg.setStroke(AndroidUtilities.dp(1), 0x22FFFFFF);
        }
        card.setBackground(bg);

        ImageView avatar = new ImageView(context);
        avatar.setImageResource(avatarRes);
        avatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(avatar, LayoutHelper.createLinear(50, 50, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 6));

        TextView nameView = new TextView(context);
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(isSelected ? accentColor : textColor);
        nameView.setGravity(Gravity.CENTER);
        card.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        subView.setTextColor(subTextColor);
        subView.setGravity(Gravity.CENTER);
        card.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10.5f);
        descView.setTextColor(ColorUtils.setAlphaComponent(textColor, 180));
        descView.setGravity(Gravity.CENTER);
        card.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return card;
    }

    /**
     * STEP 1: Аме Профіль & Вітка Спільноти
     */
    private void renderStepAmeProfile(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Твій профіль тепер живий! Редагуй кожну деталь через чистий XML-код або публікуй його у вітку @dkamegram, щоб інші могли встановити твій вигляд.",
                "Твой профиль теперь живой! Редактируй каждую деталь через чистый XML-код или публикуй его в ветку @dkamegram, чтобы другие могли установить твой вид.",
                "Your profile is alive! Customize everything via clean live XML, export it, or share in our community topic so others can use your style.")
                : MiogramLocale.get(
                "†Божественний стиль!† Ховай зайві рядки, налаштовуй неонові рамки, кольори та імпортуй готові XML-дизайни з нашої вітки!",
                "†Божественный стиль!† Скрывай лишние строки, настраивай неоновые рамки, цвета и импортируй готовые XML-дизайны из нашей ветки!",
                "†Aesthetic perfection!† Hide elements, customize glowing rings, colors, or import ready-to-use XML profiles from our community topic!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_talk : R.drawable.miogram_ai_kangel_pray,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Аме профіль & XML-дизайн ໒꒱", "Аме профиль & XML-дизайн ໒꒱", "Ame Profile & Live XML ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        layout.addView(title);

        // Preview Card
        LinearLayout codeCard = new LinearLayout(context);
        codeCard.setOrientation(LinearLayout.VERTICAL);
        codeCard.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardBgColor);
        cardBg.setCornerRadius(AndroidUtilities.dp(14));
        codeCard.setBackground(cardBg);

        TextView xmlPreview = new TextView(context);
        xmlPreview.setText(
                "<ame-profile version=\"1.0\">\n" +
                "  <banner mode=\"blur\" alpha=\"85\" />\n" +
                "  <avatar shape=\"circle\" ring-pulse=\"true\" />\n" +
                "  <visibility hide-phone=\"true\" />\n" +
                "</ame-profile>"
        );
        xmlPreview.setTypeface(Typeface.MONOSPACE);
        xmlPreview.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        xmlPreview.setTextColor(0xFF80D8FF);
        codeCard.addView(xmlPreview);

        TextView expText = new TextView(context);
        expText.setText(MiogramLocale.get(
                "• Прямий експорт та імпорт конфігурації у форматі XML\n• Можливість ховати номер, юзернейм або біо\n• Обмін профілями у вітці спільноти",
                "• Прямой экспорт и импорт конфигурации в формате XML\n• Возможность скрывать номер, юзернейм или био\n• Обмен профилями в ветке сообщества",
                "• Direct XML configuration export & import\n• Hide phone, username or bio rows seamlessly\n• Profile sharing in community topic"
        ));
        expText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        expText.setTextColor(textColor);
        expText.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(4));
        codeCard.addView(expText);

        layout.addView(codeCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Action Buttons Row
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView openXmlBtn = new TextView(context);
        openXmlBtn.setText(MiogramLocale.get("Відкрити Аме профіль", "Открыть Аме профиль", "Open Ame Profile"));
        openXmlBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        openXmlBtn.setTypeface(AndroidUtilities.bold());
        openXmlBtn.setTextColor(Color.WHITE);
        openXmlBtn.setGravity(Gravity.CENTER);
        GradientDrawable openXmlBg = new GradientDrawable();
        openXmlBg.setColor(accentColor);
        openXmlBg.setCornerRadius(AndroidUtilities.dp(12));
        openXmlBtn.setBackground(openXmlBg);
        openXmlBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        openXmlBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            new MiogramAmeProfileSheet(context).show();
        });
        btnRow.addView(openXmlBtn, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView topicBtn = new TextView(context);
        topicBtn.setText(MiogramLocale.get("Вітка @dkamegram", "Ветка @dkamegram", "Topic @dkamegram"));
        topicBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        topicBtn.setTypeface(AndroidUtilities.bold());
        topicBtn.setTextColor(textColor);
        topicBtn.setGravity(Gravity.CENTER);
        GradientDrawable topicBg = new GradientDrawable();
        topicBg.setColor(ColorUtils.blendARGB(cardBgColor, 0xFFFFFFFF, 0.1f));
        topicBg.setCornerRadius(AndroidUtilities.dp(12));
        topicBtn.setBackground(topicBg);
        topicBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        topicBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            Browser.openUrl(context, MiogramAmeProfileEngine.COMMUNITY_TOPIC_URL);
        });
        btnRow.addView(topicBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));

        layout.addView(btnRow);
        stepContentContainer.addView(layout);
    }

    /**
     * STEP 2: Хмарне Сховище у Топіках
     */
    private void renderStepCloudVault(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Більше ніякого безладу! Хмарне сховище створює приватний форум-чат, де всі файли шифруються AES-256 і акуратно сортуються по топіках.",
                "Больше никакого беспорядка! Облачное хранилище создает приватный форум-чат, где файлы шифруются AES-256 и сортируются по топикам.",
                "No more mess in Saved Messages! Cloud Vault creates a private forum chat where files are encrypted via AES-256 and sorted into topics.")
                : MiogramLocale.get(
                "†Нескінченна приватна хмара!† Файли будь-якого розміру нарізаються на чанки і зберігаються в окремих темах без витрати пам'яті телефону!",
                "†Бесконечное приватное облако!† Файлы любого размера нарезаются на чанки и хранятся в отдельных темах без траты памяти телефона!",
                "†Unlimited private vault!† Files of any size are chunked and secured in forum topics with zero local disk footprint!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_happy : R.drawable.miogram_ai_kangel_happy,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Хмарне сховище у топіках", "Облачное хранилище в топиках", "Cloud Vault in Forum Topics"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        layout.addView(title);

        String[] topicNames = {"Загальне", "Медіа", "Документи", "Архіви"};
        String[] topicDescs = {
                MiogramLocale.get("Швидкі замітки та довільні файли", "Быстрые заметки и любые файлы", "Quick notes & mixed files"),
                MiogramLocale.get("Фотографії, музика та повні відео", "Фотографии, музыка и видео", "Photos, music & full videos"),
                MiogramLocale.get("Робочі документи, проекти та код", "Рабочие документы, проекты и код", "Office documents & project code"),
                MiogramLocale.get("ZIP/TAR архіви з безпечною нарізкою", "ZIP/TAR архивы с нарезкой на чанки", "ZIP/TAR multi-part archives")
        };
        int[] colors = {0xFF4CAF50, 0xFFE53935, 0xFF3390EC, 0xFFFB8C00};

        for (int i = 0; i < topicNames.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(cardBgColor);
            bg.setCornerRadius(AndroidUtilities.dp(12));
            row.setBackground(bg);

            View dot = new View(context);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(colors[i]);
            dot.setBackground(dotBg);
            row.addView(dot, LayoutHelper.createLinear(10, 10, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            LinearLayout col = new LinearLayout(context);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView nView = new TextView(context);
            nView.setText(topicNames[i]);
            nView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nView.setTypeface(AndroidUtilities.bold());
            nView.setTextColor(textColor);
            col.addView(nView);

            TextView dView = new TextView(context);
            dView.setText(topicDescs[i]);
            dView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            dView.setTextColor(subTextColor);
            col.addView(dView);

            row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            layout.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }

        stepContentContainer.addView(layout);
    }

    /**
     * STEP 3: Плагіни та Маркет (Опціональний вибір)
     */
    private void renderStepPlugins(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Плагіни тепер тільки за твоїм бажанням! Ознайомся зі списком і вибери лише те, що тобі потрібно, або завантаж розширення пізніше з Маркету.",
                "Плагины теперь только по твоему желанию! Ознакомься со списком и выбери лишь то, что нужно, или установи расширения позже из Маркета.",
                "Plugins are now strictly opt-in! Select which ones you want installed right now, or download them later from the in-app Market.")
                : MiogramLocale.get(
                "†Повний контроль розширень!† Boykisser, PetPet, Custom Profile чи Localizer — увімкни улюблені фішки в один тап!",
                "†Полный контроль расширений!† Boykisser, PetPet, Custom Profile или Localizer — включи любимые фишки в один тап!",
                "†Complete plugin freedom!† Boykisser, PetPet, Custom Profile or Localizer — enable your favorite extensions with 1 tap!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_talk : R.drawable.miogram_ai_kangel_happy,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Плагіни та Маркет ໒꒱", "Плагины и Маркет ໒꒱", "Plugins & Market ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        layout.addView(title);

        pluginCheckBoxes.clear();
        pluginEntries.clear();
        pluginEntries.addAll(MiogramPluginsMarket.getCatalog());

        for (int i = 0; i < pluginEntries.size(); i++) {
            MiogramPluginsMarket.MarketPluginEntry entry = pluginEntries.get(i);
            boolean already = MiogramPluginsMarket.isInstalled(entry);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));

            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(cardBgColor);
            cardBg.setCornerRadius(AndroidUtilities.dp(12));
            card.setBackground(cardBg);

            LinearLayout textCol = new LinearLayout(context);
            textCol.setOrientation(LinearLayout.VERTICAL);

            TextView nameText = new TextView(context);
            nameText.setText(entry.title + " (" + entry.category + ")");
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nameText.setTypeface(AndroidUtilities.bold());
            nameText.setTextColor(textColor);
            textCol.addView(nameText);

            TextView descText = new TextView(context);
            descText.setText(entry.getDescription());
            descText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            descText.setTextColor(subTextColor);
            textCol.addView(descText);

            card.addView(textCol, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            CheckBox cb = new CheckBox(context);
            cb.setChecked(already);
            card.addView(cb, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 6, 0, 0, 0));
            pluginCheckBoxes.add(cb);

            card.setOnClickListener(v -> {
                MiogramHaptic.select(v);
                cb.setChecked(!cb.isChecked());
            });

            layout.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }

        // Button to open full market
        TextView marketBtn = new TextView(context);
        marketBtn.setText(MiogramLocale.get("Відкрити Маркет плагінів ໒꒱", "Открыть Маркет плагинов ໒꒱", "Open Plugins Market ໒꒱"));
        marketBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        marketBtn.setTypeface(AndroidUtilities.bold());
        marketBtn.setTextColor(accentColor);
        marketBtn.setGravity(Gravity.CENTER);
        marketBtn.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        marketBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            new MiogramPluginCatalogAlert(getParentActivity() != null ? getParentActivity() : null).show();
        });
        layout.addView(marketBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        stepContentContainer.addView(layout);
    }

    /**
     * STEP 4: Discord UI & Рідке Скло (AGSL)
     */
    private void renderStepDiscordAndGlass(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Насолоджуйся фірмовим стилем: вигляд серверів Discord із зручними бічними каналами та надзвичайно плавне рідке скло AGSL.",
                "Наслаждайся фирменным стилем: вид серверов Discord с удобными боковыми каналами и потрясающе плавное жидкое стекло AGSL.",
                "Experience our signature aesthetics: Discord server navigation and real-time AGSL liquid glassmorphism.")
                : MiogramLocale.get(
                "†Кібер-естетика майбутнього!† Вмикай магію рідкого скла, Discord-тему та насолоджуйся топовою швидкістю!",
                "†Кибер-эстетика будущего!† Включай магию жидкого стекла, Discord-тему и наслаждайся топовой скоростью!",
                "†Futuristic cyber aesthetic!† Turn on liquid frosted glass, Discord theme and enjoy supreme smoothness!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_talk : R.drawable.miogram_ai_kangel_pray,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Інтерфейс Discord & Рідке скло", "Интерфейс Discord & Жидкое стекло", "Discord UI & Liquid Frosted Glass"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(8));
        layout.addView(title);

        // Feature 1: Discord UI
        layout.addView(createSettingToggleCard(
                context,
                MiogramLocale.get("Інтерфейс у стилі Discord", "Интерфейс в стиле Discord", "Discord-style Interface"),
                MiogramLocale.get("Зручна бічна панель серверів та вертикальний список каналів", "Удобная боковая панель серверов и каналов", "Server sidebar and vertical channels layout"),
                MiogramDiscordLayout.isDiscordUiEnabled(),
                enabled -> MiogramDiscordLayout.setDiscordUiEnabled(enabled)
        ));

        // Feature 2: AGSL Liquid Glass
        layout.addView(createSettingToggleCard(
                context,
                MiogramLocale.get("Ефект рідкого скла AGSL", "Эффект жидкого стекла AGSL", "AGSL Liquid Frosted Glass"),
                MiogramLocale.get("Глибоке шейдерне розмиття та світлові відблиски на панелях", "Глубокое шейдерное размытие и блики на панелях", "Real-time AGSL blur and specular glass reflections"),
                MiogramVisualsPrefs.loadBool(context, "agsl_enabled", true),
                enabled -> {
                    MiogramFlags.setSpatialDecoration(enabled);
                    MiogramVisualsPrefs.saveBool(context, "agsl_enabled", enabled);
                }
        ));

        stepContentContainer.addView(layout);
    }

    private LinearLayout createSettingToggleCard(Context context, String title, String subtitle, boolean initialChecked, OnToggleListener listener) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBgColor);
        bg.setCornerRadius(AndroidUtilities.dp(14));
        card.setBackground(bg);

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(textColor);
        textCol.addView(titleView);

        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        subView.setTextColor(subTextColor);
        textCol.addView(subView);

        card.addView(textCol, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        final boolean[] checked = new boolean[]{initialChecked};
        ImageView checkView = new ImageView(context);
        checkView.setImageResource(checked[0] ? R.drawable.msg_check : R.drawable.round_check2);
        checkView.setColorFilter(new PorterDuffColorFilter(checked[0] ? 0xFF34C759 : 0x44FFFFFF, PorterDuff.Mode.SRC_IN));
        card.addView(checkView, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 10, 0, 0, 0));

        card.setOnClickListener(v -> {
            MiogramHaptic.select(v);
            checked[0] = !checked[0];
            checkView.setColorFilter(new PorterDuffColorFilter(checked[0] ? 0xFF34C759 : 0x44FFFFFF, PorterDuff.Mode.SRC_IN));
            if (listener != null) listener.onToggle(checked[0]);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.bottomMargin = AndroidUtilities.dp(8);
        card.setLayoutParams(lp);
        return card;
    }

    private interface OnToggleListener {
        void onToggle(boolean enabled);
    }

    private void handleNextAction() {
        if (currentStep == 3) {
            // Apply selected plugins from step 3
            Context context = getContext();
            for (int i = 0; i < pluginEntries.size(); i++) {
                if (i < pluginCheckBoxes.size()) {
                    MiogramPluginsMarket.MarketPluginEntry entry = pluginEntries.get(i);
                    CheckBox cb = pluginCheckBoxes.get(i);
                    if (cb.isChecked()) {
                        MiogramPluginsMarket.installPlugin(context, entry);
                    } else if (MiogramPluginsMarket.isInstalled(entry)) {
                        MiogramPluginsMarket.uninstallPlugin(entry);
                    }
                }
            }
        }

        if (currentStep < TOTAL_STEPS - 1) {
            renderStep(currentStep + 1);
        } else {
            finishOnboarding();
        }
    }

    private void nextStep() {
        if (currentStep < TOTAL_STEPS - 1) {
            renderStep(currentStep + 1);
        } else {
            finishOnboarding();
        }
    }

    private void finishOnboarding() {
        MiogramCompanionPrefs.setGuideShownVersion(BuildVars.BUILD_VERSION_STRING);
        MiogramCompanionPrefs.setOnboardingCompleted(true);
        MiogramPluginsMarket.markOnboardingDone(getContext());
        dismiss();
    }
}
