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
        scrimView.setBackgroundColor(Color.argb(140, 0, 0, 0));
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

        // 3. Pixel OS / MD3 Floating card container with dock customization controls
        sheetCard = new LinearLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int maxHeight = (int) (AndroidUtilities.displaySize.y * 0.54f);
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                if (mode == MeasureSpec.UNSPECIFIED || size > maxHeight) {
                    heightMeasureSpec = MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST);
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        };
        sheetCard.setOrientation(LinearLayout.VERTICAL);
        sheetCard.setClickable(true);
        sheetCard.setClipToPadding(false);

        // M3 floating card container background with 28dp rounded corners & outline
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(YumiMd3.surfaceContainer());
        cardBg.setCornerRadius(AndroidUtilities.dp(28));
        cardBg.setStroke(Math.max(1, AndroidUtilities.dp(0.8f)), YumiMd3.outlineVariant());
        sheetCard.setBackground(cardBg);
        sheetCard.setElevation(AndroidUtilities.dp(16));
        sheetCard.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(16));

        // Top drag handle
        View dragHandle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(YumiMd3.withAlpha(YumiMd3.onSurfaceVariant(), 0.35f));
        handleBg.setCornerRadius(AndroidUtilities.dp(2));
        dragHandle.setBackground(handleBg);
        sheetCard.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

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
        subtitleView.setText("Pixel OS • Material You 3");
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

        TextView widthSub = createSubHeader(context, "Розтягування кнопок на всю ширину екрана");
        contentLayout.addView(widthSub);

        LinearLayout widthRow = new LinearLayout(context);
        widthRow.setOrientation(LinearLayout.HORIZONTAL);

        boolean initialStretch = AppearanceConfig.isYumiBarStretch();
        YumiChip chipStretch = new YumiChip(context);
        chipStretch.setText("↔ Розтягувати бар");
        chipStretch.setChecked(initialStretch, false);

        YumiChip chipCompact = new YumiChip(context);
        chipCompact.setText("⇤⇥ Компактний");
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

        // Section 3: Pixel OS Drag-to-reorder tip card
        TextView tipHeader = createSectionHeader(context, "ПЕРЕТЯГУВАННЯ КНОПОК");
        contentLayout.addView(tipHeader);

        LinearLayout tipCard = new LinearLayout(context);
        tipCard.setOrientation(LinearLayout.HORIZONTAL);
        tipCard.setGravity(Gravity.CENTER_VERTICAL);
        tipCard.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        GradientDrawable tipBg = new GradientDrawable();
        tipBg.setColor(YumiMd3.surfaceContainerHighest());
        tipBg.setCornerRadius(AndroidUtilities.dp(16));
        tipBg.setStroke(Math.max(1, AndroidUtilities.dp(0.8f)), YumiMd3.withAlpha(YumiMd3.primary(), 0.35f));
        tipCard.setBackground(tipBg);

        ImageView tipIcon = new ImageView(context);
        tipIcon.setImageResource(R.drawable.baseline_swap_horiz_24);
        tipIcon.setColorFilter(new PorterDuffColorFilter(YumiMd3.primary(), PorterDuff.Mode.SRC_IN));
        tipCard.addView(tipIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 10, 0));

        TextView tipText = new TextView(context);
        tipText.setText("Перетягуйте кнопки пальцем прямо на барі внизу, щоб змінити їх порядок у реальному часі");
        tipText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        tipText.setTextColor(YumiMd3.onSurface());
        tipCard.addView(tipText, new LinearLayout.LayoutParams(0, LayoutHelper.WRAP_CONTENT, 1f));

        contentLayout.addView(tipCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

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
        });

        chipFeed.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_FEED, checked);
            if (checked && chipContacts.isChecked()) {
                chipContacts.setChecked(false, false);
            }
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
        });
        tabsRow.addView(chipCallsSettings, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Profile
        YumiChip chipProfile = new YumiChip(context);
        chipProfile.setText("Профіль");
        chipProfile.setChipIcon(R.drawable.yumi_ic_person);
        chipProfile.setChecked(!NaConfig.INSTANCE.getMainTabsHideProfile().Bool(), false);
        chipProfile.setOnCheckedChangeListener((chip, checked) -> {
            callback.onTabToggled(MainTabsActivity.INDEX_PROFILE, checked);
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
        });

        chipSlotCalls.setOnClickListener(v -> {
            chipSlotCalls.setChecked(true);
            chipSlotSettings.setChecked(false);
            chipCallsSettings.setText("Дзвінки");
            chipCallsSettings.setChipIcon(R.drawable.yumi_ic_call);
            callback.onCallsModeChanged(true);
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
        sheetCard.addView(scrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Floating card placed cleanly above the bottom dock
        int bottomMargin = AndroidUtilities.dp(80) + AndroidUtilities.navigationBarHeight;
        LayoutParams cardLp = LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.WRAP_CONTENT,
                Gravity.BOTTOM,
                16, 0, 16, 0);
        cardLp.bottomMargin = bottomMargin;
        addView(sheetCard, cardLp);

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
        sheetCard.setTranslationY(height + AndroidUtilities.dp(60));
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
                .translationY(height + AndroidUtilities.dp(60))
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
