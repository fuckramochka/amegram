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
import android.view.MotionEvent;
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
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import app.amegram.theme.YumiMd3;
import app.exteraless.appearance.AppearanceConfig;
import xyz.nextalone.nagram.NaConfig;

/**
 * Material 3 Expressive live customizer sheet for the Yumi navigation dock.
 * Opens upon two-finger spread gesture on the floating bar.
 */
public class YumiBarCustomizerSheet extends FrameLayout {

    public interface Callback {
        void onCompactModeChanged(boolean compact);
        void onSearchButtonToggled(boolean show);
        void onTabToggled(int index, boolean visible);
        void onClose();
    }

    private final View scrimView;
    private final TextView hintPill;
    private final LinearLayout sheetCard;
    private final Callback callback;
    private boolean isDismissing;

    public YumiBarCustomizerSheet(@NonNull Context context, @NonNull Callback callback) {
        super(context);
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
                Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 80, 0, 0);
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
        sheetCard.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20),
                AndroidUtilities.dp(20) + AndroidUtilities.navigationBarHeight);

        // Top drag handle
        View dragHandle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setColor(YumiMd3.withAlpha(YumiMd3.onSurfaceVariant(), 0.35f));
        handleBg.setCornerRadius(AndroidUtilities.dp(2));
        dragHandle.setBackground(handleBg);
        sheetCard.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 2, 0, 12));

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
        sheetCard.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 1: Display style (Labels / Icons only)
        TextView styleHeader = createSectionHeader(context, "ВІДОБРАЖЕННЯ ТА ПІДПИСИ");
        sheetCard.addView(styleHeader);

        LinearLayout styleRow = new LinearLayout(context);
        styleRow.setOrientation(LinearLayout.HORIZONTAL);

        boolean initialCompact = NaConfig.INSTANCE.getMainTabsHideTitles().Bool();
        YumiChip chipCapsules = new YumiChip(context);
        chipCapsules.setText("Капсули з текстом");
        chipCapsules.setChecked(!initialCompact, false);

        YumiChip chipIconsOnly = new YumiChip(context);
        chipIconsOnly.setText("Тільки іконки");
        chipIconsOnly.setChecked(initialCompact, false);

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
        sheetCard.addView(styleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 2: Floating search button toggle
        TextView searchHeader = createSectionHeader(context, "ПЛАВАЮЧА КНОПКА ПОШУКУ");
        sheetCard.addView(searchHeader);

        boolean searchEnabled = !NaConfig.INSTANCE.getHideDialogsSearchField().Bool();
        YumiChip chipSearch = new YumiChip(context);
        chipSearch.setText("Окрема плаваюча кнопка пошуку 🔍");
        chipSearch.setChecked(searchEnabled, false);
        chipSearch.setOnCheckedChangeListener((chip, checked) -> {
            callback.onSearchButtonToggled(checked);
        });
        sheetCard.addView(chipSearch, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Section 3: Active tabs
        TextView tabsHeader = createSectionHeader(context, "АКТИВНІ ВКЛАДКИ");
        sheetCard.addView(tabsHeader);

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
        YumiChip chipContacts = new YumiChip(context);
        chipContacts.setText("Контакти");
        chipContacts.setChipIcon(R.drawable.yumi_ic_contacts);
        chipContacts.setChecked(!NaConfig.INSTANCE.getMainTabsHideContacts().Bool(), false);
        chipContacts.setOnCheckedChangeListener((chip, checked) -> callback.onTabToggled(1, checked));
        tabsRow.addView(chipContacts, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Calls
        YumiChip chipCalls = new YumiChip(context);
        chipCalls.setText("Дзвінки");
        chipCalls.setChipIcon(R.drawable.yumi_ic_call);
        chipCalls.setChecked(!NaConfig.INSTANCE.getMainTabsHideCallsSettings().Bool(), false);
        chipCalls.setOnCheckedChangeListener((chip, checked) -> callback.onTabToggled(2, checked));
        tabsRow.addView(chipCalls, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Profile
        YumiChip chipProfile = new YumiChip(context);
        chipProfile.setText("Профіль");
        chipProfile.setChipIcon(R.drawable.yumi_ic_person);
        chipProfile.setChecked(!NaConfig.INSTANCE.getMainTabsHideProfile().Bool(), false);
        chipProfile.setOnCheckedChangeListener((chip, checked) -> callback.onTabToggled(3, checked));
        tabsRow.addView(chipProfile, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        // Feed
        YumiChip chipFeed = new YumiChip(context);
        chipFeed.setText("Стрічка");
        chipFeed.setChipIcon(R.drawable.yumi_ic_feed);
        chipFeed.setChecked(AppearanceConfig.showFeedTab(), false);
        chipFeed.setOnCheckedChangeListener((chip, checked) -> callback.onTabToggled(4, checked));
        tabsRow.addView(chipFeed, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));

        tabScroll.addView(tabsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        sheetCard.addView(tabScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 18));

        // Done button
        YumiButton doneBtn = new YumiButton(context, YumiButton.STYLE_FILLED);
        doneBtn.setButtonText("Зберегти та закрити");
        doneBtn.setIcon(R.drawable.yumi_ic_check);
        doneBtn.setOnClickListener(v -> {
            if (!isDismissing) {
                callback.onClose();
            }
        });
        sheetCard.addView(doneBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LayoutParams cardLp = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM);
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
