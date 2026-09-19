package app.miogram.bridge.badge;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;

/**
 * 100% Telegram-Native Miogram Badge Bottom Sheet:
 * Follows official Telegram Android UI guidelines (Theme keys, squircle cards, native typography,
 * Telegram verification card layout for obtain reasons, and Telegram-style horizontal theme/status picker).
 */
public class MiogramBadgeBottomSheet extends BottomSheet {

    private final long targetUserId;
    private final boolean isSelf;
    private MiogramBadgeType selectedBadge;
    private ImageView badgePreviewView;
    private TextView badgeNameView;
    private TextView badgeSubView;
    private TextView dynamicLoreView;
    private final List<View> selectorCards = new ArrayList<>();

    
    public static MiogramBadgeBottomSheet show(Context context, long userId) {
        MiogramBadgeBottomSheet sheet = new MiogramBadgeBottomSheet(context, userId);
        sheet.show();
        return sheet;
    }

    public static MiogramBadgeBottomSheet show(BaseFragment fragment, long userId) {
        MiogramBadgeBottomSheet sheet = new MiogramBadgeBottomSheet(fragment, userId);
        sheet.show();
        return sheet;
    }

    public MiogramBadgeBottomSheet(BaseFragment fragment, long userId) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId <= 0) {
            userId = currentUserId;
        }
        this.targetUserId = userId;
        this.isSelf = (userId == currentUserId || userId == 0);
        this.selectedBadge = MiogramBadgeManager.getBadgeType(userId);
        init(fragment.getParentActivity());
    }

    public MiogramBadgeBottomSheet(Context context, long userId) {
        super(context, false);
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId <= 0) {
            userId = currentUserId;
        }
        this.targetUserId = userId;
        this.isSelf = (userId == currentUserId || userId == 0);
        this.selectedBadge = MiogramBadgeManager.getBadgeType(userId);
        init(context);
    }

    private void init(Context inContext) {
        final Context context = inContext != null ? inContext : ApplicationLoader.applicationContext;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        final Context finalContext = context;
        final long clientUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        final boolean isFounder = (targetUserId == MiogramBadgeManager.FOUNDER_USER_ID || clientUserId == MiogramBadgeManager.FOUNDER_USER_ID);
        MiogramSupabaseBridge.BadgeRecord record = MiogramBadgeManager.getBadgeRecord(targetUserId);
        if (record == null && isFounder) {
            record = new MiogramSupabaseBridge.BadgeRecord(
                    targetUserId,
                    selectedBadge != null ? selectedBadge : MiogramBadgeType.ORIGINAL,
                    MiogramLocale.get("Засновник Miogram ໒꒱", "Создатель Miogram ໒꒱", "Miogram Founder ໒꒱"),
                    MiogramLocale.get("Особиста відзнака засновника та головного архітектора екосистеми Miogram (@fuckramochka).",
                            "Личное отличие создателя и главного архитектора экосистемы Miogram (@fuckramochka).",
                            "Personal distinction of the Founder & Chief Architect of Miogram (@fuckramochka)."),
                    "01.09.2026",
                    true,
                    true,
                    MiogramBadgeManager.FOUNDER_USER_ID
            );
        }
        final MiogramSupabaseBridge.BadgeRecord finalRecord = record;

        ScrollView scrollView = new ScrollView(context);
        scrollView.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
        root.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18), AndroidUtilities.dp(20));

        // 1. Native Telegram Drag Handle
        ImageView dragHandle = new ImageView(context);
        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setShape(GradientDrawable.RECTANGLE);
        handleDrawable.setColor(Theme.getColor(Theme.key_sheet_scrollUp, resourcesProvider));
        handleDrawable.setCornerRadius(AndroidUtilities.dp(3));
        dragHandle.setImageDrawable(handleDrawable);
        root.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // 1b. Needy Streamer angel divider (magenta -> cyan).
        View nsoDivider = new View(context);
        GradientDrawable nsoDivBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFFFF2A85, 0xFF00E5FF});
        nsoDivBg.setShape(GradientDrawable.RECTANGLE);
        nsoDivBg.setCornerRadius(AndroidUtilities.dp(1));
        nsoDivider.setBackground(nsoDivBg);
        root.addView(nsoDivider, LayoutHelper.createLinear(120, 2, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // 2. NSO Stage: dark idol-goods chamber with a magenta glow edge.
        FrameLayout previewContainer = new FrameLayout(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(AndroidUtilities.dp(24));
        previewBg.setColor(0xFF1E1F22);
        previewBg.setStroke(AndroidUtilities.dp(2), 0xFFFF2A85);
        previewContainer.setBackground(previewBg);
        previewContainer.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(16));

        badgePreviewView = new ImageView(context);
        badgePreviewView.setImageDrawable(new MiogramArrowDrawable(76, selectedBadge));
        previewContainer.addView(badgePreviewView, LayoutHelper.createFrame(96, 76, Gravity.CENTER));

        root.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12));

        // 3. Telegram Native Title & Verification Subtitle
        badgeNameView = new TextView(context);
        badgeNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        badgeNameView.setTypeface(AndroidUtilities.bold());
        badgeNameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        badgeNameView.setGravity(Gravity.CENTER);
        if (isSelf) {
            badgeNameView.setText(MiogramLocale.get("Стрілочка Miogram ໒꒱", "Стрелочка Miogram ໒꒱", "Miogram Badge ໒꒱"));
        } else if (isFounder) {
            badgeNameView.setText(MiogramLocale.get("Засновник Miogram ໒꒱", "Создатель Miogram ໒꒱", "Miogram Founder ໒꒱"));
        } else {
            String customTitle = (record != null && !TextUtils.isEmpty(record.title)) ? record.title : MiogramLocale.get("Відзнака Спільноти ໒꒱", "Отличие Сообщества ໒꒱", "Community Badge ໒꒱");
            badgeNameView.setText(customTitle);
        }
        root.addView(badgeNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        badgeSubView = new TextView(context);
        badgeSubView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badgeSubView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        badgeSubView.setGravity(Gravity.CENTER);
                    badgeSubView.setText(selectedBadge.getTitle() + verifiedSuffix(isFounder, finalRecord));
        if (record == null && !isSelf && !isFounder) {
            badgeSubView.setText(MiogramLocale.get("Не активовано в Supabase", "Не активировано в Supabase", "Not Active in Supabase"));
            LinearLayout infoCard = new LinearLayout(context);
            infoCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable infoBg = new GradientDrawable();
            infoBg.setShape(GradientDrawable.RECTANGLE);
            infoBg.setCornerRadius(AndroidUtilities.dp(14));
            infoBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            infoCard.setBackground(infoBg);
            infoCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

            TextView infoTitle = new TextView(context);
            infoTitle.setText(MiogramLocale.get("Статус відзнаки", "Статус отличия", "Badge Status"));
            infoTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            infoTitle.setTypeface(AndroidUtilities.bold());
            infoTitle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            infoCard.addView(infoTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            TextView infoBody = new TextView(context);
            infoBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            infoBody.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            infoBody.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            infoBody.setText(MiogramLocale.get(
                    "У вас наразі немає активної відзнаки Miogram. Канонічні відзнаки (крила/стрілочки) видаються виключно вручну адміністрацією через хмарну базу даних Supabase за особливий внесок у розвиток спільноти.",
                    "У вас пока нет активного отличия Miogram. Канонические отличия (крылья/стрелочки) выдаются исключительно вручную администрацией через базу данных Supabase за особый вклад в развитие сообщества.",
                    "You currently do not have an active Miogram badge. Canonical badges are granted exclusively and manually by Miogram administration via Supabase database for community contributions."
            ));
            infoCard.addView(infoBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            root.addView(infoCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

            TextView okButton = new TextView(context);
            okButton.setText(MiogramLocale.get("Зрозуміло", "Понятно", "Got It"));
            okButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            okButton.setTypeface(AndroidUtilities.bold());
            okButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            okButton.setGravity(Gravity.CENTER);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(AndroidUtilities.dp(10));
            btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            okButton.setBackground(btnBg);
            okButton.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
            okButton.setOnClickListener(v -> {
                if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dismiss();
            });
            root.addView(okButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            scrollView.addView(root);
            setCustomView(scrollView);
            return;
        }

        // =============================================================
        // BRANCH A: INSPECTION MODE ("За що отримана стрілочка")
        // =============================================================
        if (!isSelf) {
            // Native Telegram Info Card: Офіційне Обґрунтування
            LinearLayout reasonCard = new LinearLayout(context);
            reasonCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable reasonBg = new GradientDrawable();
            reasonBg.setShape(GradientDrawable.RECTANGLE);
            reasonBg.setCornerRadius(AndroidUtilities.dp(14));
            reasonBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            reasonCard.setBackground(reasonBg);
            reasonCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

            TextView reasonTitle = new TextView(context);
            reasonTitle.setText(MiogramLocale.get("Обґрунтування надання відзнаки", "Обоснование выдачи отличия", "Award Citation & Reason"));
            reasonTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            reasonTitle.setTypeface(AndroidUtilities.bold());
            reasonTitle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            reasonCard.addView(reasonTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            TextView reasonBody = new TextView(context);
            reasonBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            reasonBody.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            reasonBody.setLineSpacing(AndroidUtilities.dp(2), 1.0f);

            String reason = (record != null && !TextUtils.isEmpty(record.obtainedReason)) ? record.obtainedReason :
                    (isFounder
                            ? MiogramLocale.get(
                                    "Особиста відзнака засновника та головного архітектора екосистеми Miogram (@fuckramochka). Надана при заснуванні проекту як символ найвищого статусу розробника та підтвердження офіційної автентичності білду.",
                                    "Личное отличие создателя и главного архитектора экосистемы Miogram (@fuckramochka). Предоставлено при основании проекта как символ высшего статуса разработчика и подтверждения официальной подлинности клиента.",
                                    "Personal distinction of the Founder & Chief Architect of Miogram (@fuckramochka). Granted upon project genesis as a symbol of supreme developer status and official client authenticity.")
                            : MiogramLocale.get(
                                    "Офіційно верифікований учасник хмарної екосистеми Miogram. Відзнаку надано за вагомий внесок у тестування, підтримку спільноти та активний розвиток клієнта.",
                                    "Официально верифицированный участник облачной экосистемы Miogram. Отличие предоставлено за весомый вклад в тестирование, поддержку сообщества и активное развитие клиента.",
                                    "Officially verified member of the Miogram Cloud ecosystem. Awarded for meaningful contributions to testing, community support, and client development."));

            reasonBody.setText(reason);
            reasonCard.addView(reasonBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(reasonCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

            // Native Telegram Metadata Rows Card
            LinearLayout metaCard = new LinearLayout(context);
            metaCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable metaBg = new GradientDrawable();
            metaBg.setShape(GradientDrawable.RECTANGLE);
            metaBg.setCornerRadius(AndroidUtilities.dp(14));
            metaBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            metaCard.setBackground(metaBg);
            metaCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

            String obtainDate = (record != null && !TextUtils.isEmpty(record.obtainedAt)) ? record.obtainedAt : "01.09.2026";
            if (obtainDate.length() > 10) {
                obtainDate = obtainDate.substring(0, 10);
            }

            metaCard.addView(createTgMetaRow(context,
                    MiogramLocale.get("Дата надання:", "Дата выдачи:", "Date Granted:"),
                    obtainDate));
            boolean rowVerified = record != null && record.verified;
            metaCard.addView(createTgMetaRow(context,
                    MiogramLocale.get("Хмарний статус:", "Облачный статус:", "Cloud Status:"),
                    rowVerified
                            ? MiogramLocale.get("Supabase, перевірено ✓", "Supabase, проверено ✓", "Supabase Verified ✓")
                            : MiogramLocale.get("Учасник спільноти", "Участник сообщества", "Community member")));
            if (record != null && record.grantorId == MiogramBadgeManager.FOUNDER_USER_ID) {
                metaCard.addView(createTgMetaRow(context,
                        MiogramLocale.get("Видав:", "Выдал:", "Granted by:"),
                        MiogramLocale.get("★ Засновник Miogram", "★ Основатель Miogram", "★ Miogram Founder")));
            }
            metaCard.addView(createTgMetaRow(context,
                    MiogramLocale.get("Ідентифікатор користувача:", "Идентификатор пользователя:", "User ID:"),
                    String.valueOf(targetUserId)));
            metaCard.addView(createTgMetaRow(context,
                    MiogramLocale.get("Стиль бейджа:", "Стиль бейджа:", "Badge Style:"),
                    selectedBadge.getTitle()));

            root.addView(metaCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

            // Native Lore Section
            LinearLayout loreCard = new LinearLayout(context);
            loreCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable loreBg = new GradientDrawable();
            loreBg.setShape(GradientDrawable.RECTANGLE);
            loreBg.setCornerRadius(AndroidUtilities.dp(14));
            loreBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            loreCard.setBackground(loreBg);
            loreCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            TextView loreTitle = new TextView(context);
            loreTitle.setText(MiogramLocale.get("Символізм стилю", "Символизм стиля", "Style Symbolism"));
            loreTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            loreTitle.setTypeface(AndroidUtilities.bold());
            loreTitle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            loreCard.addView(loreTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

            TextView loreBody = new TextView(context);
            loreBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            loreBody.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            loreBody.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            loreBody.setText(getBadgeLore(selectedBadge));
            loreCard.addView(loreBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(loreCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

            // Native Telegram Primary Button
            TextView okButton = new TextView(context);
            okButton.setText(MiogramLocale.get("Зрозуміло", "Понятно", "Got It"));
            okButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            okButton.setTypeface(AndroidUtilities.bold());
            okButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            okButton.setGravity(Gravity.CENTER);
            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(AndroidUtilities.dp(10));
            btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            okButton.setBackground(btnBg);
            okButton.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
            okButton.setOnClickListener(v -> {
                if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dismiss();
            });
            root.addView(okButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        } else {
            // =============================================================
            // BRANCH B: CUSTOMIZATION MODE ("Інтерфейс вибору стрілочки")
            // =============================================================
            TextView sectionHeader = new TextView(context);
            sectionHeader.setText(MiogramLocale.get("Оберіть стиль стрілочки (10 варіантів)", "Выберите стиль стрелочки (10 вариантов)", "Choose Arrow Style (10 choices)"));
            sectionHeader.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sectionHeader.setTypeface(AndroidUtilities.bold());
            sectionHeader.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider));
            root.addView(sectionHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

            // Native Telegram Horizontal Squircle Carousel
            HorizontalScrollView scroll = new HorizontalScrollView(context);
            scroll.setHorizontalScrollBarEnabled(false);
            LinearLayout carousel = new LinearLayout(context);
            carousel.setOrientation(LinearLayout.HORIZONTAL);
            carousel.setGravity(Gravity.CENTER_VERTICAL);
            carousel.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(8));

            selectorCards.clear();
            for (MiogramBadgeType type : MiogramBadgeType.values()) {
                LinearLayout card = new LinearLayout(context);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setGravity(Gravity.CENTER);
                card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

                boolean active = (type == selectedBadge);
                card.setBackground(buildTgCardBg(active));

                ImageView icon = new ImageView(context);
                icon.setImageDrawable(new MiogramArrowDrawable(36, type));
                card.addView(icon, LayoutHelper.createLinear(44, 36, Gravity.CENTER));

                TextView name = new TextView(context);
                name.setText(getBadgeShortName(type));
                name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
                name.setTypeface(AndroidUtilities.bold());
                name.setTextColor(active ? Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) : Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                card.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 4, 0, 0));

                card.setOnClickListener(v -> {
                    if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    selectedBadge = type;

                    // Update Top Preview
                    badgePreviewView.setImageDrawable(new MiogramArrowDrawable(76, selectedBadge));
        badgeSubView.setText(selectedBadge.getTitle() + verifiedSuffix(isFounder, finalRecord));

                    // Update Dynamic Lore
                    if (dynamicLoreView != null) {
                        dynamicLoreView.setText(getBadgeLore(selectedBadge));
                    }

                    // Auto-sync in background
                    long curUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                    MiogramSupabaseBridge.setSelectedBadgeForAccount(finalContext, curUserId, selectedBadge);

                    // Re-render selection borders
                    for (int i = 0; i < carousel.getChildCount(); i++) {
                        View ch = carousel.getChildAt(i);
                        boolean isCur = (i == selectedBadge.ordinal());
                        ch.setBackground(buildTgCardBg(isCur));
                        if (ch instanceof LinearLayout) {
                            LinearLayout l = (LinearLayout) ch;
                            if (l.getChildCount() >= 2 && l.getChildAt(1) instanceof TextView) {
                                ((TextView) l.getChildAt(1)).setTextColor(isCur ? Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider) : Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
                            }
                        }
                    }
                });

                selectorCards.add(card);
                carousel.addView(card, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
            }
            scroll.addView(carousel);
            root.addView(scroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

            // Dynamic Lore Card for Selected Style (Native Telegram Card)
            LinearLayout dynamicLoreCard = new LinearLayout(context);
            dynamicLoreCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable dLoreBg = new GradientDrawable();
            dLoreBg.setShape(GradientDrawable.RECTANGLE);
            dLoreBg.setCornerRadius(AndroidUtilities.dp(14));
            dLoreBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            dynamicLoreCard.setBackground(dLoreBg);
            dynamicLoreCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            TextView loreTitle = new TextView(context);
            loreTitle.setText(MiogramLocale.get("Опис обраного стилю", "Описание выбранного стиля", "Selected Style Details"));
            loreTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            loreTitle.setTypeface(AndroidUtilities.bold());
            loreTitle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            dynamicLoreCard.addView(loreTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

            dynamicLoreView = new TextView(context);
            dynamicLoreView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            dynamicLoreView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            dynamicLoreView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            dynamicLoreView.setText(getBadgeLore(selectedBadge));
            dynamicLoreCard.addView(dynamicLoreView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(dynamicLoreCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

            // Native Telegram Status Hint (TextInfoPrivacyCell style)
            TextView syncHint = new TextView(context);
            syncHint.setText(MiogramLocale.get(
                    "Хмарна синхронізація Supabase активна. Обраний стиль миттєво відображається у всіх співрозмовників у чатах.",
                    "Облачная синхронизация Supabase активна. Выбранный стиль мгновенно отображается у всех собеседников в чатах.",
                    "Supabase cloud sync active. Selected badge is instantly visible to everyone across chats."));
            syncHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            syncHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4, resourcesProvider));
            syncHint.setGravity(Gravity.CENTER);
            root.addView(syncHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

            // Telegram Native Primary Action Button
            TextView saveButton = new TextView(context);
            saveButton.setText(MiogramLocale.get("Застосувати стиль", "Применить стиль", "Apply Badge Style"));
            saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            saveButton.setTypeface(AndroidUtilities.bold());
            saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
            saveButton.setGravity(Gravity.CENTER);

            GradientDrawable btnBg = new GradientDrawable();
            btnBg.setShape(GradientDrawable.RECTANGLE);
            btnBg.setCornerRadius(AndroidUtilities.dp(10));
            btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            saveButton.setBackground(btnBg);
            saveButton.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));

            saveButton.setOnClickListener(v -> {
                if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                long curUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                MiogramSupabaseBridge.setSyncEnabledForAccount(finalContext, curUserId, true);
                MiogramSupabaseBridge.setSelectedBadgeForAccount(finalContext, curUserId, selectedBadge);
                dismiss();
            });

            root.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            // Founder-only: grant badges to other users.
            try {
                long selfId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                if (isFounder || selfId == MiogramBadgeManager.FOUNDER_USER_ID) {
                    TextView grantEntry = new TextView(context);
                    grantEntry.setText(MiogramLocale.get("★ Видати стрілочку людині", "★ Выдать стрелочку человеку", "★ Grant a badge"));
                    grantEntry.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                    grantEntry.setTypeface(AndroidUtilities.bold());
                    grantEntry.setGravity(Gravity.CENTER);
                    grantEntry.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
                    grantEntry.setBackground(Theme.getSelectorDrawable(false));
                    grantEntry.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
                    final Context grantCtx = context;
                    grantEntry.setOnClickListener(v -> {
                        if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        MiogramBadgeGrantSheet.show(grantCtx);
                    });
                    root.addView(grantEntry, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
                }
            } catch (Throwable ignored) {}
        }

        scrollView.addView(root);
        setCustomView(scrollView);
    }

    private View createTgMetaRow(Context context, String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));

        TextView labelView = new TextView(context);
        labelView.setText(label);
        labelView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        labelView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        row.addView(labelView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        TextView valView = new TextView(context);
        valView.setText(value);
        valView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valView.setTypeface(AndroidUtilities.bold());
        valView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
        row.addView(valView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        return row;
    }

    private GradientDrawable buildTgCardBg(boolean active) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(14));
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider);
        if (active) {
            d.setColor(Color.argb(30, Color.red(accent), Color.green(accent), Color.blue(accent)));
            d.setStroke(AndroidUtilities.dp(2f), accent);
        } else {
            d.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            d.setStroke(AndroidUtilities.dp(1f), Color.argb(20, 128, 128, 128));
        }
        return d;
    }

    private String getBadgeShortName(MiogramBadgeType type) {
        switch (type) {
            case PINK:    return "Pink";
            case CYAN:    return "Cyan";
            case DARK:    return "Dark";
            case ANGEL:   return "Angel";
            case DEVIL:   return "Devil";
            case RAINBOW: return "Rainbow";
            case OUTLINE: return "Outline";
            case GLITCH:  return "Glitch";
            case PREMIUM: return "Premium";
            case ORIGINAL:
            default:      return "Original";
        }
    }

    private static String verifiedSuffix(boolean isFounder, MiogramSupabaseBridge.BadgeRecord record) {
        if (!isFounder && (record == null || !record.verified)) {
            return "";
        }
        return " • " + MiogramLocale.get("Перевірено ✓", "Проверено ✓", "Verified ✓");
    }

    private String getBadgeLore(MiogramBadgeType type) {
        switch (type) {
            case PINK:
                return MiogramLocale.get(
                        "Неоново-рожеве піксельне серце з подвійними шевронами та блискітками. Символ невичерпної енергії, цифрових емоцій та відданості спільноті.",
                        "Неоново-розовое пиксельное сердце с двойными шевронами и блёстками. Символ неиссякаемой энергии, цифровых эмоций и преданности сообществу.",
                        "Neon pink pixel heart with double chevrons and sparkles. A symbol of boundless energy, digital emotion, and community devotion.");
            case CYAN:
                return MiogramLocale.get(
                        "Електричний блакитний стиль з антеною-візором, крижаним сяйвом та білими акцентами. Символізує швидкість, технологічність та цифровий простір.",
                        "Электрический лазурный стиль с антенной-визором, ледяным сиянием и белыми акцентами. Символизирует скорость, технологичность и цифровое пространство.",
                        "Electric sky-blue wings with data antenna visor, icy starlight, and crisp highlights. Symbolizes next-gen speed and digital clarity.");
            case DARK:
                return MiogramLocale.get(
                        "Нічний обсидіан з оксамитовим фіолетовим краєм, кібер-шпильками та лавандовим серцем. Для поціновувачів таємничої меланхолії та нічного вайбу.",
                        "Ночной обсидиан с бархатным фиолетовым краем, кибер-заколками и лавандовым сердцем. Для ценителей таинственной меланхолии и ночного вайба.",
                        "Midnight obsidian wings with velvet purple aura, cyber clips, and lavender heart. Crafted for lovers of nocturnal mystique and quiet melancholia.");
            case ANGEL:
                return MiogramLocale.get(
                        "Ангельські крила з ширяючим золотистим німбом, пастельними пір'їнами та чистим серцем. Відзнака гармонії, світлих намірів та піднесення.",
                        "Ангельские крылья с парящим золотистым нимбом, пастельными перьями и чистым сердцем. Знак гармонии, светлых намерений и возвышения.",
                        "Angelic wings with hovering golden halo, pastel feathered tips, and glowing heart. An emblem of harmony, pure intention, and ascension.");
            case DEVIL:
                return MiogramLocale.get(
                        "Гострі ріжки та крила кажана з гарячим неоновим контуром. Відзнака бунтарського характеру, зухвалого шарму та свободи самовираження.",
                        "Острые рожки и крылья летучей мыши с горячим неоновым контуром. Знак бунтарского характера, дерзкого шарма и свободы самовыражения.",
                        "Sharp devil horns and scalloped bat wings with blazing neon. Distinctive emblem of playful rebellion, bold charm, and pure expression.");
            case RAINBOW:
                return MiogramLocale.get(
                        "П'ятирівневий призматичний спектр із золотим контуром серця. Символ яскравих емоцій, творчого розмаїття та святкового настрою.",
                        "Пятиуровневый призматический спектр с золотым контуром сердца. Символ ярких эмоций, творческого разнообразия и праздничного настроения.",
                        "Five-tier prismatic rainbow spectrum with golden heart contour. Represents vivid emotion, creative diversity, and celebratory energy.");
            case OUTLINE:
                return MiogramLocale.get(
                        "Мінімалістичний 1-піксельний контур у стилі ретро-дисплеїв. Чиста цифрова естетика — точні лінії, прозора форма та жодних зайвих деталей.",
                        "Минималистичный 1-пиксельный контур в стиле ретро-дисплеев. Чистая цифровая эстетика — точные линии, прозрачная форма и никаких лишних деталей.",
                        "Minimalist 1px wireframe contour in retro-display style. Pure digital aesthetics — sharp geometry, transparent core, zero excess.");
            case GLITCH:
                return MiogramLocale.get(
                        "Хроматичне розщеплення кольору з ефектом RGB-зсуву та мерехтливими сканлайнами. Для поціновувачів естетики CRT-екранів, ретро-касет та цифрових збоїв.",
                        "Хроматическое расщепление цвета с эффектом RGB-сдвига и мерцающими сканлайнами. Для ценителей эстетики CRT-экранов, ретро-кассет и цифровых сбоев.",
                        "Chromatic RGB displacement with dynamic flickering scanlines. Made for connoisseurs of CRT monitors, retro VHS tapes, and cyber glitches.");
            case PREMIUM:
                return MiogramLocale.get(
                        "Королівська золота корона, сяючий німб та янтарні крила з нагрудними шевронами. Відзнака визнання найвищих досягнень та статусу в Miogram.",
                        "Королевская золотая корона, сияющий нимб и янтарные крылья с нагрудными шевронами. Знак признания высших достижений и статуса в Miogram.",
                        "Royal golden crown, radiant halo shimmer, and amber wings with armor chevrons. Elite distinction honoring paramount achievements and status.");
            case ORIGINAL:
            default:
                return MiogramLocale.get(
                        "Класичне піксельне серце з крилами, антеною та м'яким неоновим сяйвом. Канонічна відзнака, з якої розпочалася історія спільноти.",
                        "Классическое пиксельное сердце с крыльями, антенной и мягким неоновым сиянием. Канонический знак, с которого началась история сообщества.",
                        "Canonical pixel heart with wings, antenna visor, and soft neon glow. The foundational emblem of the community.");
        }
    }
}
