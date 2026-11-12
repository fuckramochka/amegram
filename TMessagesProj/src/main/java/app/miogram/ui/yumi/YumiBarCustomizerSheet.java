package app.miogram.ui.yumi;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.MainTabsActivity;

import java.util.List;

import app.amegram.theme.YumiMd3;
import app.exteraless.appearance.AppearanceConfig;
import xyz.nextalone.nagram.NaConfig;

/**
 * Material 3 Expressive live customizer sheet for the Yumi navigation dock.
 * Opens upon two-finger spread gesture on the floating bar.
 */
public class YumiBarCustomizerSheet extends FrameLayout {

    public interface Callback {
        void onStretchModeChanged(boolean stretch);
        void onCompactModeChanged(boolean compact);
        void onTabToggled(int index, boolean visible);
        void onCallsModeChanged(boolean showCalls);
        void onSwapVisibleTabs(int indexA, int indexB);
        void onClose();
    }

    private final View scrimView;
    private final TextView hintPill;
    private final LinearLayout sheetCard;
    private final LinearLayout orderContainer;
    private final MainTabsActivity activity;
    private final Callback callback;
    private boolean isDismissing;

    public YumiBarCustomizerSheet(@NonNull Context context, @NonNull MainTabsActivity activity, @NonNull Callback callback) {
        super(context);
        this.activity = activity;
        this.callback = callback;
        setClickable(true);
        setFocusable(true);

        // 1. Scrim background that dims the screen
        scrimView = new View(context);
        scrimView.setBackgroundColor(Color.argb(160, 0, 0, 0));
        scrimView.setAlpha(0f);
        scrimView.setOnClickListener(v -> {
            if (!isDismissing) {
                callback.onClose();
            }
        });
        addView(scrimView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Floating live preview badge above the lifted bar
        hintPill = new TextView(context);
        hintPill.setText("✦ РЕЖИМ НАЛАШТУВАННЯ БАРУ ✦");
        hintPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        hintPill.setTextColor(YumiMd3.onPrimary());
        hintPill.setTypeface(AndroidUtilities.bold());
        hintPill.setLetterSpacing(0.08f);
        hintPill.setGravity(Gravity.CENTER);
        hintPill.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(7), AndroidUtilities.dp(16), AndroidUtilities.dp(7));

        GradientDrawable hintBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{YumiMd3.actionGradientStart(), YumiMd3.actionGradientEnd()});
        hintBg.setCornerRadius(AndroidUtilities.dp(20));
        hintPill.setBackground(hintBg);
        hintPill.setElevation(AndroidUtilities.dp(12));
        hintPill.setAlpha(0f);
        hintPill.setScaleX(0.85f);
        hintPill.setScaleY(0.85f);

        LayoutParams hintLp = LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 48, 0, 0);
        addView(hintPill, hintLp);

        // 3. Bottom sheet with dock customization controls
        sheetCard = new LinearLayout(context);
        sheetCard.setOrientation(LinearLayout.VERTICAL);
        sheetCard.setClickable(true);
        sheetCard.setClipToPadding(false);

        // M3 container background with rounded top corners & outline
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(YumiMd3.surfaceContainer());
        cardBg.setCornerRadii(new float[]{
                AndroidUtilities.dp(28), AndroidUtilities.dp(28),
                AndroidUtilities.dp(28), AndroidUtilities.dp(28),
                0, 0, 0, 0
        });
        cardBg.setStroke(Math.max(1, AndroidUtilities.dp(0.8f)), YumiMd3.outlineVariant());
        sheetCard.setBackground(cardBg);
        sheetCard.setElevation(AndroidUtilities.dp(16));
        sheetCard.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(10), AndroidUtilities.dp(18),
                AndroidUtilities.dp(16) + AndroidUtilities.navigationBarHeight);

        // Top drag handle
        View dragHandle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(YumiMd3.withAlpha(YumiMd3.onSurfaceVariant(), 0.35f));
        handleBg.setCornerRadius(AndroidUtilities.dp(2));
        dragHandle.setBackground(handleBg);
        sheetCard.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 10));

        // Header row
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView tuneIcon = new ImageView(context);
        tuneIcon.setImageResource(R.drawable.yumi_ic_tune);
        tuneIcon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
        headerRow.addView(tuneIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));

        LinearLayout titleCol = new LinearLayout(context);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);

        TextView titleView = new TextView(context);
        titleView.setText("Налаштування панелі");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f);
        titleView.setTextColor(YumiMd3.onSurface());
        titleView.setTypeface(AndroidUtilities.bold());
        titleCol.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Зміни застосовуються на льоту");
        subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
        subtitleView.setTextColor(YumiMd3.onSurfaceVariant());
        titleCol.addView(subtitleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

        ImageView closeBtn = new ImageView(context);
        closeBtn.setImageResource(R.drawable.yumi_ic_close);
        closeBtn.setScaleType(ImageView.ScaleType.CENTER);
        closeBtn.setColorFilter(new PorterDuffColorFilter(YumiMd3.onSurfaceVariant(), PorterDuff.Mode.SRC_IN));
        closeBtn.setBackground(YumiUi.selectable(0, YumiMd3.RADIUS_FULL_DP));
        closeBtn.setOnClickListener(v -> {
            if (!isDismissing) {
                callback.onClose();
            }
        });
        headerRow.addView(closeBtn, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
        sheetCard.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // Scrollable content layout so nothing is cut off
        ScrollView scrollView = new ScrollView(context);
        scrollView.setOverScrollMode(OVER_SCROLL_NEVER);
        scrollView.setVerticalScrollBarEnabled(false);
        scrollView.setClipToPadding(false);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);

        // Section 1: Bar Width (Stretch vs Compact)
        TextView widthHeader = createSectionHeader(context, "ШИРИНА ПАНЕЛІ");
        contentLayout.addView(widthHeader);

        TextView widthSub = createSubHeader(context, "1: розтягувати бар навіть якщо мало кнопок; 2: не розтягувати");
        contentLayout.addView(widthSub);

        LinearLayout widthRow = new LinearLayout(context);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);

        boolean initialStretch = AppearanceConfig.isYumiBarStretch();
        YumiChip chipStretch = new YumiChip(context);
        chipStretch.setText("↔ Розтягувати бар");
        chipStretch.setChecked(initialStretch, false);

        YumiChip chipCompact = new YumiChip(context);
        chipCompact.setText("⇤⇥ Не розтягувати");
        chipCompact.setChecked(!initialStretch, false);

        chipStretch.setOnClickListener(v -> {
            chipStretch.setChecked(true);
            chipCompact.setChecked(false);
            callback.onStretchModeChanged(true);
        });

        chipCompact.setOnClickListener(v -> {
            chipCompact.setChecked(true);
            chipStretch.setChecked(false);
            callback.onStretchModeChanged(false);
        });

        widthRow.addView(chipStretch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        widthRow.addView(chipCompact, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(widthRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 2: Display style (Labels / Icons only)
        TextView styleHeader = createSectionHeader(context, "ВІДОБРАЖЕННЯ ТА ПІДПИСИ");
        contentLayout.addView(styleHeader);

        LinearLayout styleRow = new LinearLayout(context);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);

        boolean initialCompactTitles = NaConfig.INSTANCE.getMainTabsHideTitles().Bool();
        YumiChip chipCapsules = new YumiChip(context);
        chipCapsules.setText("Капсули з текстом");
        chipCapsules.setChecked(!initialCompactTitles, false);

        YumiChip chipIconsOnly = new YumiChip(context);
        chipIconsOnly.setText("Тільки іконки");
        chipIconsOnly.setChecked(initialCompactTitles, false);

        chipCapsules.setOnClickListener(v -> {
            chipCapsules.setChecked(true);
            chipIconsOnly.setChecked(false);
            callback.onCompactModeChanged(false);
        });

        chipIconsOnly.setOnClickListener(v -> {
            chipIconsOnly.setChecked(true);
            chipCapsules.setChecked(false);
            callback.onCompactModeChanged(true);
        });

        styleRow.addView(chipCapsules, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        styleRow.addView(chipIconsOnly, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(styleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 3: Reorder buttons
        TextView orderHeader = createSectionHeader(context, "ПОРЯДОК КНОПОК У БАРІ");
        contentLayout.addView(orderHeader);

        TextView orderSub = createSubHeader(context, "Тисніть ◀ або ▶, щоб поміняти місцями кнопки на льоту");
        contentLayout.addView(orderSub);

        orderContainer = new LinearLayout(context);
        orderContainer.setOrientation(LinearLayout.VERTICAL);
        contentLayout.addView(orderContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 4: Active tabs
        TextView tabsHeader = createSectionHeader(context, "АКТИВНІ ВКЛАДКИ");
        contentLayout.addView(tabsHeader);

        HorizontalScrollView tabScroll = new HorizontalScrollView(context);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setClipToPadding(false);

        LinearLayout tabsRow = new LinearLayout(context);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);

        // Chats (locked permanent)
        YumiChip chipChats = new YumiChip(context);
        chipChats.setText("Чати");
        chipChats.setChipIcon(R.drawable.yumi_ic_chat);
        chipChats.setChecked(true, false);
        chipChats.setCheckable(false);
        tabsRow.addView(chipChats, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Contacts
        boolean contactsActive = !NaConfig.INSTANCE.getMainTabsHideContacts().Bool() && !AppearanceConfig.showFeedTab();
        YumiChip chipContacts = new YumiChip(context);
        chipContacts.setText("Контакти");
        chipContacts.setChipIcon(R.drawable.yumi_ic_contacts);
        chipContacts.setChecked(contactsActive, false);

        // Feed
        boolean feedActive = AppearanceConfig.showFeedTab();
        YumiChip chipFeed = new YumiChip(context);
        chipFeed.setText("Стрічка");
        chipFeed.setChipIcon(R.drawable.yumi_ic_feed);
        chipFeed.setChecked(feedActive, false);

        chipContacts.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_CONTACTS, checked);
            if (checked && chipFeed.isChecked()) {
                chipFeed.setChecked(false, false);
            }
            refreshOrderList(context);
        });

        chipFeed.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_FEED, checked);
            if (checked && chipContacts.isChecked()) {
                chipContacts.setChecked(false, false);
            }
            refreshOrderList(context);
        });

        tabsRow.addView(chipContacts, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        tabsRow.addView(chipFeed, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Settings / Calls Slot
        boolean callsOrSettingsActive = !NaConfig.INSTANCE.getMainTabsHideCallsSettings().Bool();
        boolean isCallsMode = UserConfig.getInstance(UserConfig.selectedAccount).showCallsTab;
        YumiChip chipCallsSettings = new YumiChip(context);
        chipCallsSettings.setText(isCallsMode ? "Дзвінки" : "Налаштування");
        chipCallsSettings.setChipIcon(isCallsMode ? R.drawable.yumi_ic_call : R.drawable.yumi_ic_settings);
        chipCallsSettings.setChecked(callsOrSettingsActive, false);
        chipCallsSettings.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_SETTINGS, checked);
            refreshOrderList(context);
        });
        tabsRow.addView(chipCallsSettings, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Profile
        YumiChip chipProfile = new YumiChip(context);
        chipProfile.setText("Профіль");
        chipProfile.setChipIcon(R.drawable.yumi_ic_person);
        chipProfile.setChecked(!NaConfig.INSTANCE.getMainTabsHideProfile().Bool(), false);
        chipProfile.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_PROFILE, checked);
            refreshOrderList(context);
        });
        tabsRow.addView(chipProfile, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        tabScroll.addView(tabsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(tabScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Sub-selector: Choose Settings vs Calls
        TextView slotSub = createSubHeader(context, "Вміст вкладки налаштувань / дзвінків:");
        contentLayout.addView(slotSub);

        LinearLayout slotRow = new LinearLayout(context);
        slotRow.setOrientation(LinearLayout.HORIZONTAL);

        YumiChip chipSlotSettings = new YumiChip(context);
        chipSlotSettings.setText("⚙️ Налаштування");
        chipSlotSettings.setChecked(!isCallsMode, false);

        YumiChip chipSlotCalls = new YumiChip(context);
        chipSlotCalls.setText("📞 Дзвінки");
        chipSlotCalls.setChecked(isCallsMode, false);

        chipSlotSettings.setOnClickListener(v -> {
            chipSlotSettings.setChecked(true);
            chipSlotCalls.setChecked(false);
            chipCallsSettings.setText("Налаштування");
            chipCallsSettings.setChipIcon(R.drawable.yumi_ic_settings);
            callback.onCallsModeChanged(false);
            refreshOrderList(context);
        });

        chipSlotCalls.setOnClickListener(v -> {
            chipSlotCalls.setChecked(true);
            chipSlotSettings.setChecked(false);
            chipCallsSettings.setText("Дзвінки");
            chipCallsSettings.setChipIcon(R.drawable.yumi_ic_call);
            callback.onCallsModeChanged(true);
            refreshOrderList(context);
        });

        slotRow.addView(chipSlotSettings, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        slotRow.addView(chipSlotCalls, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        contentLayout.addView(slotRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // Done button
        YumiButton doneBtn = new YumiButton(context, YumiButton.STYLE_FILLED);
        doneBtn.setButtonText("Зберегти та закрити");
        doneBtn.setIcon(R.drawable.yumi_ic_check);
        doneBtn.setOnClickListener(v -> {
            if (!isDismissing) {
                callback.onClose();
            }
        });
        contentLayout.addView(doneBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        scrollView.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        sheetCard.addView(scrollView, new LinearLayout.LayoutParams(LayoutHelper.MATCH_PARENT, 0, 1f));

        // Restrict maximum sheet card height so lifted dock stays fully visible
        int maxSheetHeight = (int) (AndroidUtilities.displaySize.y * 0.52f);
        LayoutParams cardLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, maxSheetHeight, Gravity.BOTTOM);
        addView(sheetCard, cardLp);

        // Populate reorder list initially
        refreshOrderList(context);

        // Start entrance animation
        sheetCard.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                sheetCard.getViewTreeObserver().removeOnPreDrawListener(this);
                animateEnter();
                return true;
            }
        });
    }

    public void refreshOrderList(Context context) {
        if (orderContainer == null || activity == null) {
            return;
        }
        orderContainer.removeAllViews();
        List<Integer> visibleTabs = activity.getVisibleTabIndices();
        if (visibleTabs == null || visibleTabs.isEmpty()) {
            TextView emptyTv = new TextView(context);
            emptyTv.setText("Немає активних вкладок для впорядкування");
            emptyTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            emptyTv.setTextColor(YumiMd3.onSurfaceVariant());
            emptyTv.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4));
            orderContainer.addView(emptyTv);
            return;
        }

        for (int i = 0; i < visibleTabs.size(); i++) {
            final int tabId = visibleTabs.get(i);
            final int pos = i;
            final boolean canMoveLeft = i > 0;
            final boolean canMoveRight = i < visibleTabs.size() - 1;

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));

            GradientDrawable itemBg = new GradientDrawable();
            itemBg.setColor(YumiMd3.surfaceContainerHighest());
            itemBg.setCornerRadius(AndroidUtilities.dp(16));
            itemBg.setStroke(Math.max(1, AndroidUtilities.dp(0.6f)), YumiMd3.withAlpha(YumiMd3.outlineVariant(), 0.6f));
            row.setBackground(itemBg);

            // Badge number: "1", "2", ...
            TextView badge = new TextView(context);
            badge.setText(String.valueOf(i + 1));
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
            badge.setTextColor(YumiMd3.onPrimary());
            badge.setTypeface(AndroidUtilities.bold());
            badge.setGravity(Gravity.CENTER);
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setShape(GradientDrawable.OVAL);
            badgeBg.setColor(YumiMd3.primary());
            badge.setBackground(badgeBg);
            row.addView(badge, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Tab icon
            ImageView icon = new ImageView(context);
            icon.setImageResource(getTabIcon(tabId));
            icon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
            row.addView(icon, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

            // Tab title
            TextView title = new TextView(context);
            title.setText(getTabTitle(tabId));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            title.setTextColor(YumiMd3.onSurface());
            title.setTypeface(AndroidUtilities.bold());
            row.addView(title, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

            // Move left button ◀
            ImageView btnLeft = new ImageView(context);
            btnLeft.setImageResource(R.drawable.yumi_ic_arrow_back);
            btnLeft.setScaleType(ImageView.ScaleType.CENTER);
            btnLeft.setColorFilter(new PorterDuffColorFilter(
                    canMoveLeft ? YumiMd3.primary() : YumiMd3.withAlpha(YumiMd3.onSurfaceVariant(), 0.3f), PorterDuff.Mode.SRC_IN));
            if (canMoveLeft) {
                btnLeft.setBackground(YumiUi.selectable(YumiMd3.surfaceContainerLow(), YumiMd3.RADIUS_FULL_DP));
                btnLeft.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    int prevTabId = visibleTabs.get(pos - 1);
                    callback.onSwapVisibleTabs(tabId, prevTabId);
                    refreshOrderList(context);
                });
            } else {
                btnLeft.setAlpha(0.35f);
            }
            row.addView(btnLeft, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

            // Move right button ▶
            ImageView btnRight = new ImageView(context);
            btnRight.setImageResource(R.drawable.yumi_ic_forward);
            btnRight.setScaleType(ImageView.ScaleType.CENTER);
            btnRight.setColorFilter(new PorterDuffColorFilter(
                    canMoveRight ? YumiMd3.primary() : YumiMd3.withAlpha(YumiMd3.onSurfaceVariant(), 0.3f), PorterDuff.Mode.SRC_IN));
            if (canMoveRight) {
                btnRight.setBackground(YumiUi.selectable(YumiMd3.surfaceContainerLow(), YumiMd3.RADIUS_FULL_DP));
                btnRight.setOnClickListener(v -> {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    int nextTabId = visibleTabs.get(pos + 1);
                    callback.onSwapVisibleTabs(tabId, nextTabId);
                    refreshOrderList(context);
                });
            } else {
                btnRight.setAlpha(0.35f);
            }
            row.addView(btnRight, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL));

            orderContainer.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        }
    }

    private int getTabIcon(int tabId) {
        if (tabId == MainTabsActivity.INDEX_CHATS) return R.drawable.yumi_ic_chat;
        if (tabId == MainTabsActivity.INDEX_FEED) return R.drawable.yumi_ic_feed;
        if (tabId == MainTabsActivity.INDEX_CONTACTS) return R.drawable.yumi_ic_contacts;
        if (tabId == MainTabsActivity.INDEX_CALLS) return R.drawable.yumi_ic_call;
        if (tabId == MainTabsActivity.INDEX_SETTINGS) return R.drawable.yumi_ic_settings;
        if (tabId == MainTabsActivity.INDEX_PROFILE) return R.drawable.yumi_ic_person;
        return R.drawable.yumi_ic_apps;
    }

    private String getTabTitle(int tabId) {
        if (tabId == MainTabsActivity.INDEX_CHATS) return "Чати";
        if (tabId == MainTabsActivity.INDEX_FEED) return "Стрічка";
        if (tabId == MainTabsActivity.INDEX_CONTACTS) return "Контакти";
        if (tabId == MainTabsActivity.INDEX_CALLS) return "Дзвінки";
        if (tabId == MainTabsActivity.INDEX_SETTINGS) return "Налаштування";
        if (tabId == MainTabsActivity.INDEX_PROFILE) return "Профіль";
        return "Вкладка";
    }

    private TextView createSectionHeader(Context context, String title) {
        TextView tv = new TextView(context);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTextColor(YumiMd3.primary());
        tv.setTypeface(AndroidUtilities.bold());
        tv.setLetterSpacing(0.06f);
        tv.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(2));
        return tv;
    }

    private TextView createSubHeader(Context context, String sub) {
        TextView tv = new TextView(context);
        tv.setText(sub);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        tv.setTextColor(YumiMd3.onSurfaceVariant());
        tv.setPadding(0, 0, 0, AndroidUtilities.dp(6));
        return tv;
    }

    public void animateEnter() {
        scrimView.animate()
                .alpha(1f)
                .setDuration(280L)
                .start();

        hintPill.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(320L)
                .start();

        final int height = sheetCard.getMeasuredHeight() > 0 ? sheetCard.getMeasuredHeight() : AndroidUtilities.dp(360);
        sheetCard.setTranslationY(height);
        sheetCard.animate()
                .translationY(0f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(340L)
                .start();
    }

    public void animateExit(Runnable onFinished) {
        if (isDismissing) {
            return;
        }
        isDismissing = true;

        scrimView.animate()
                .alpha(0f)
                .setDuration(240L)
                .start();

        hintPill.animate()
                .alpha(0f)
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(200L)
                .start();

        final int height = sheetCard.getMeasuredHeight() > 0 ? sheetCard.getMeasuredHeight() : AndroidUtilities.dp(360);
        sheetCard.animate()
                .translationY(height)
                .setInterpolator(CubicBezierInterpolator.DEFAULT)
                .setDuration(240L)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (onFinished != null) {
                            onFinished.run();
                        }
                    }
                })
                .start();
    }

    public boolean isDismissing() {
        return isDismissing;
    }
}
