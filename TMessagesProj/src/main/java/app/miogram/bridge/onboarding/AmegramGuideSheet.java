package app.miogram.bridge.onboarding;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.MiogramAiService;
import app.miogram.bridge.ai.companion.MiogramCompanionPrefs;
import app.miogram.bridge.customui.MiogramHaptic;
import app.miogram.bridge.player.MiogramPlayerPrefs;

/**
 * First Launch & Onboarding Guide for Amegram.
 * Interactive wizard featuring:
 * - Step 1: Persona selection (Ame-chan ໒꒱ vs OMGkawaiiAngel ✧†) with reactive character sprite.
 * - Step 2: Recommended initial settings (Theme, Title Marquee, Player cover radius, music cache).
 * - Step 3: Google Gemini AI Companion API key prompt with direct link to Google AI Studio.
 * - Step 4: Ecosystem & connected platforms overview (TikTok MI, Steam, Discord, Spotify, GitHub).
 * - Intuitive controls: "Пропустити все", "Пропустити крок", "Далі", "Завершити".
 */
public class AmegramGuideSheet extends BottomSheet {

    private static final int TOTAL_STEPS = 4;
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
    private EditText apiKeyInput;

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

        // Top Navigation Bar: Title/Progress on left, "Пропустити все" on right
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
        characterBox.addView(characterSpriteView, LayoutHelper.createLinear(88, 88, Gravity.CENTER_VERTICAL));

        speechBubbleView = new TextView(context);
        speechBubbleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        speechBubbleView.setTextColor(textColor);
        speechBubbleView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(4), 0);
        speechBubbleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        characterBox.addView(speechBubbleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL));

        root.addView(characterBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Dynamic Step Content Area (inside ScrollView so it always fits)
        ScrollView contentScrollView = new ScrollView(context);
        contentScrollView.setVerticalScrollBarEnabled(false);
        stepContentContainer = new FrameLayout(context);
        contentScrollView.addView(stepContentContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(contentScrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        // Bottom Action Bar: [Пропустити крок] | [Далі / Завершити]
        LinearLayout bottomBar = new LinearLayout(context);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setGravity(Gravity.CENTER_VERTICAL);
        bottomBar.setPadding(0, AndroidUtilities.dp(14), 0, 0);

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
                dot.setLayoutParams(new LinearLayout.LayoutParams(AndroidUtilities.dp(20), AndroidUtilities.dp(6)));
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
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.82f, 1.08f, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.82f, 1.08f, 1.0f)
        );
        bounce.setDuration(400);
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
                renderStepSettings(context);
                break;
            case 2:
                renderStepApiKey(context);
                break;
            case 3:
                renderStepEcosystem(context);
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
                "Привіт! Я Аме ໒꒱. Допоможу тобі налаштувати затишний Amegram, знайти треки або просто побалакати.",
                "Привет! Я Аме ໒꒱. Помогу настроить уютный Amegram, найти треки или просто поболтать.",
                "Hi! I'm Ame ໒꒱. I'll help you set up cozy Amegram, find tracks or just hang out.")
                : MiogramLocale.get(
                "†Внеси пожертву у світлі!† Я Кангель ✧. Зробимо твій клієнт найяскравішим та найшвидшим у Всесвіті!",
                "†Вознеси молитву во славу!† Я Кангель ✧. Сделаем твой клиент самым ярким и быстрым во Вселенной!",
                "†Pray to the light!† I am KAngel ✧. Let's make your client the brightest and fastest in the universe!");

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
        title.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(12));
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
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14));

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
        card.addView(avatar, LayoutHelper.createLinear(54, 54, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        TextView nameView = new TextView(context);
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(isSelected ? accentColor : textColor);
        nameView.setGravity(Gravity.CENTER);
        card.addView(nameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));

        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        subView.setTextColor(subTextColor);
        subView.setGravity(Gravity.CENTER);
        card.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        descView.setTextColor(ColorUtils.setAlphaComponent(textColor, 180));
        descView.setGravity(Gravity.CENTER);
        card.addView(descView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        return card;
    }

    /**
     * STEP 1: Recommended Initial Settings (Visuals & Player)
     */
    private void renderStepSettings(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Тут я зібрала найкращі візуальні налаштування Amegram: плавний плеєр, обкладинки та текст пісень.",
                "Здесь я собрала лучшие визуальные настройки Amegram: плавный плеер, обложки и текст песен.",
                "Here are the best visual tweaks for Amegram: smooth player, rounded covers, synced lyrics.")
                : MiogramLocale.get(
                "†Сяйво естетики!† Обери радіус карток, біжучий рядок та насолоджуйся топовим дизайном!",
                "†Сияние эстетики!† Выбери радиус карточек, бегущую строку и наслаждайся топовым дизайном!",
                "†Aesthetic glow!† Pick cover curve, marquee text and enjoy stellar design!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_talk : R.drawable.miogram_ai_kangel_pray,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Рекомендовані перші налаштування", "Рекомендуемые первые настройки", "Recommended initial settings"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(10));
        layout.addView(title);

        // Setting 1: Player Cover Corner Radius Preset (Rounded Modern)
        layout.addView(createSettingToggleCard(
                context,
                MiogramLocale.get("Закруглені обкладинки плеєра (20dp)", "Закруглённые обложки плеера (20dp)", "Rounded player covers (20dp)"),
                MiogramLocale.get("Сучасний стиль із м'якими кутами замість гострих квадратів", "Современный стиль с мягкими углами вместо острых", "Modern aesthetic with soft corners"),
                MiogramPlayerPrefs.getCoverCornerRadius() >= 16,
                enabled -> MiogramPlayerPrefs.setCoverCornerRadius(enabled ? 20 : 6)
        ));

        // Setting 2: Marquee scrolling title
        layout.addView(createSettingToggleCard(
                context,
                MiogramLocale.get("Біжучий рядок для довгих назв треків", "Бегущая строка для длинных названий треков", "Marquee scrolling for track titles"),
                MiogramLocale.get("Назва пісні плавно прокручується без обрізання", "Название трека плавно скроллится без обрезки", "Long track names scroll smoothly"),
                MiogramPlayerPrefs.isTitleMarqueeEnabled(),
                MiogramPlayerPrefs::setTitleMarqueeEnabled
        ));

        // Setting 3: Synced Lyrics Glow
        layout.addView(createSettingToggleCard(
                context,
                MiogramLocale.get("Неонове сяйво активного рядка тексту пісні", "Неоновое свечение активной строки караоке", "Neon glow on active lyrics line"),
                MiogramLocale.get("М'яке підсвічування тексту в такт музиці", "Мягкая подсветка текста в такт музыке", "Soft glowing aura on singing lines"),
                MiogramPlayerPrefs.isLyricsActiveGlow(),
                MiogramPlayerPrefs::setLyricsActiveGlow
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
        checkView.setImageResource(checked[0] ? R.drawable.msg_check : R.drawable.msg_round_check_active);
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

    /**
     * STEP 2: Gemini AI API Key Prompt
     */
    private void renderStepApiKey(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Щоб я могла розпізнавати голосові, писати тобі та виконувати дії, введи безкоштовний ключ Gemini!",
                "Чтобы я могла распознавать голосовые, писать тебе и выполнять действия, введи бесплатный ключ Gemini!",
                "To let me transcribe voice notes, chat with you and run tools, add a free Gemini key!")
                : MiogramLocale.get(
                "†Божественний розум!† Ключ Gemini живить мій інтелект і стріми. Візьми його безкоштовно в Google AI Studio!",
                "†Божественный разум!† Ключ Gemini питает мой интеллект и стримы. Получи его бесплатно в Google AI Studio!",
                "†Divine intelligence!† Gemini key powers my intellect and streams. Grab one free in Google AI Studio!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_game : R.drawable.miogram_ai_kangel_happy,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Ключ Gemini для ШІ-супутниці", "Ключ Gemini для ИИ-спутницы", "Gemini Key for AI Companion"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        layout.addView(title);

        TextView desc = new TextView(context);
        desc.setText(MiogramLocale.get(
                "Google надає безкоштовні API-ключі без обмеження за часом для особистого користування. Ключ зберігається лише на твоєму пристрої.",
                "Google предоставляет бесплатные API-ключи без ограничения по времени. Ключ хранится только на твоём устройстве.",
                "Google provides free Gemini API keys for personal use. Keys are kept securely on your device."
        ));
        desc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        desc.setTextColor(subTextColor);
        desc.setPadding(0, 0, 0, AndroidUtilities.dp(12));
        layout.addView(desc);

        // Action button to open Google AI Studio
        TextView getKeyBtn = new TextView(context);
        getKeyBtn.setText(MiogramLocale.get("🌐 Отримати ключ (Google AI Studio)", "🌐 Получить ключ (Google AI Studio)", "🌐 Get Key (Google AI Studio)"));
        getKeyBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        getKeyBtn.setTypeface(AndroidUtilities.bold());
        getKeyBtn.setTextColor(accentColor);
        getKeyBtn.setGravity(Gravity.CENTER);
        GradientDrawable getKeyBg = new GradientDrawable();
        getKeyBg.setColor(ColorUtils.blendARGB(cardBgColor, accentColor, 0.15f));
        getKeyBg.setCornerRadius(AndroidUtilities.dp(12));
        getKeyBg.setStroke(AndroidUtilities.dp(1), ColorUtils.setAlphaComponent(accentColor, 100));
        getKeyBtn.setBackground(getKeyBg);
        getKeyBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        getKeyBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"));
                context.startActivity(browserIntent);
            } catch (Throwable ignore) {}
        });
        layout.addView(getKeyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Input field
        apiKeyInput = new EditText(context);
        apiKeyInput.setHint(MiogramLocale.get("Встав ключ (AIzaSy...)", "Вставь ключ (AIzaSy...)", "Paste key (AIzaSy...)"));
        apiKeyInput.setHintTextColor(0x66FFFFFF);
        apiKeyInput.setTextColor(0xFFFFFFFF);
        apiKeyInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        apiKeyInput.setSingleLine(true);
        apiKeyInput.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(cardBgColor);
        inputBg.setCornerRadius(AndroidUtilities.dp(12));
        inputBg.setStroke(AndroidUtilities.dp(1), 0x33FFFFFF);
        apiKeyInput.setBackground(inputBg);

        String currentKey = MiogramAiService.getApiKey();
        if (!TextUtils.isEmpty(currentKey)) {
            apiKeyInput.setText(currentKey);
        }
        layout.addView(apiKeyInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        if (MiogramAiService.hasApiKey()) {
            TextView statusOk = new TextView(context);
            statusOk.setText(MiogramLocale.get("✓ Ключ вже налаштовано і готовий до роботи!", "✓ Ключ уже настроен и готов к работе!", "✓ API Key is already configured and ready!"));
            statusOk.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            statusOk.setTextColor(0xFF34C759);
            statusOk.setPadding(AndroidUtilities.dp(4), 0, 0, 0);
            layout.addView(statusOk);
        }

        stepContentContainer.addView(layout);
    }

    /**
     * STEP 3: Connected Ecosystem & Finish
     */
    private void renderStepEcosystem(Context context) {
        String speech = selectedAme
                ? MiogramLocale.get(
                "Все готово! Amegram тепер підключено до нашого хмарного мосту та екосистеми. Ласкаво просимо ໒꒱",
                "Всё готово! Amegram теперь подключён к нашему облачному мосту и экосистеме. Добро пожаловать ໒꒱",
                "All done! Amegram is connected to our cloud bridge and ecosystem. Welcome aboard ໒꒱")
                : MiogramLocale.get(
                "†БЛАГОСЛОВЕННЯ!† Твій Amegram на повній потужності! Сяй разом зі мною у всесвіті!",
                "†БЛАГОСЛОВЕНИЕ!† Твой Amegram на полной мощности! Сияй вместе со мной во вселенной!",
                "†BLESSING!† Your Amegram is at full power! Shine with me in the cyberspace!");

        updateCharacterState(
                selectedAme ? R.drawable.miogram_ai_ame_happy : R.drawable.miogram_ai_kangel_happy,
                speech
        );

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Єдина екосистема Amegram", "Единая экосистема Amegram", "Amegram Connected Ecosystem"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(textColor);
        title.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(8));
        layout.addView(title);

        layout.addView(createServiceItem(
                context,
                "TikTok MI",
                MiogramLocale.get("Оригінальні відео 9:16 HD, лайки, підписки та коментарі прямо в клієнті.",
                        "Оригинальные видео 9:16 HD, лайки, подписки и комментарии прямо в клиенте.",
                        "Native 9:16 HD videos, likes, subscriptions and comments in-app.")
        ));

        layout.addView(createServiceItem(
                context,
                "Steam, Discord, Spotify & GitHub",
                MiogramLocale.get("Хмарне збереження статусів, присутність у реальному часі та авто-відновлення.",
                        "Облачное сохранение статусов, присутствие в реальном времени и авто-восстановление.",
                        "Cloud presence persistence, real-time rich presence and auto-recovery.")
        ));

        layout.addView(createServiceItem(
                context,
                "YouTube Music & Пошук треків",
                MiogramLocale.get("Миттєве завантаження у 'Збережені' та пряма відправка в топіки форумів.",
                        "Мгновенное скачивание в 'Избранное' и прямая отправка в топики форумов.",
                        "Instant download to Cloud and direct routing to forum topics.")
        ));

        stepContentContainer.addView(layout);
    }

    private LinearLayout createServiceItem(Context context, String name, String desc) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardBgColor);
        bg.setCornerRadius(AndroidUtilities.dp(12));
        row.setBackground(bg);

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setText(name);
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(textColor);
        col.addView(nameView);

        TextView descView = new TextView(context);
        descView.setText(desc);
        descView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        descView.setTextColor(subTextColor);
        col.addView(descView);

        row.addView(col, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
        lp.bottomMargin = AndroidUtilities.dp(8);
        row.setLayoutParams(lp);
        return row;
    }

    private void handleNextAction() {
        if (currentStep == 2 && apiKeyInput != null) {
            String entered = apiKeyInput.getText() != null ? apiKeyInput.getText().toString().trim() : "";
            if (!TextUtils.isEmpty(entered)) {
                MiogramAiService.setApiKey(entered);
                Toast.makeText(getContext(), MiogramLocale.get("Ключ Gemini збережено!", "Ключ Gemini сохранён!", "Gemini key saved!"), Toast.LENGTH_SHORT).show();
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
        MiogramCompanionPrefs.setOnboardingCompleted(true);
        dismiss();
    }
}
