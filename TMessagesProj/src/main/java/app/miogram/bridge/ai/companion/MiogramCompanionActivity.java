package app.miogram.bridge.ai.companion;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoAlbumPickerActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ai.MiogramAiService;
import app.miogram.bridge.badge.MiogramSupabaseBridge;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Yumigram AI Companion Screen (Yumi & Mio).
 * Built strictly according to the Telegram design ecosystem:
 * - Native Telegram ActionBar with back navigation and options menu (Clear history, Report bug, Switch companion)
 * - Dynamic Theme integration: seamlessly supports Light, Dark, Tinted, and Day themes
 * - Interactive Horizontal Slides Carousel for companions with custom artwork, dynamic speech bubbles, stats, and dot indicators
 * - Authentic Telegram message bubbles with companion mood sprites and timestamping
 * - Integrated action permission cards for client tool executions
 * - Telegram-style composer with quick prompt chips and circular send button
 */
public class MiogramCompanionActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private long scopedDialogId = 0;
    private Utilities.Callback<String> onDraftInsertCallback;

    private LinearLayout heroCard;
    private HorizontalScrollView heroCarouselScroll;
    private LinearLayout heroCarouselLayout;
    private LinearLayout ameSlideCard;
    private LinearLayout kangelSlideCard;
    private ImageView ameSlideAvatar;
    private ImageView kangelSlideAvatar;
    private TextView ameSpeechBubble;
    private TextView kangelSpeechBubble;
    private TextView ameSelectBtn;
    private TextView kangelSelectBtn;
    private View dotAme;
    private View dotKAngel;
    private TextView tabAme;
    private TextView tabKAngel;
    private LinearLayout dotsRow;
    private TextView heroToggleBtn;

    // Compact Hero Bar (displayed when collapsed to reclaim chat space)
    private LinearLayout compactHeroBar;
    private ImageView compactAvatar;
    private TextView compactTitle;
    private TextView compactStats;
    private boolean heroCollapsed = false;

    private int lastCarouselScrollX = -1;
    private final Runnable carouselSettleRunnable = new Runnable() {
        @Override
        public void run() {
            if (heroCarouselScroll == null) return;
            int currentX = heroCarouselScroll.getScrollX();
            if (lastCarouselScrollX == currentX) {
                snapCarouselToNearestPage();
            } else {
                lastCarouselScrollX = currentX;
                heroCarouselScroll.postDelayed(this, 40);
            }
        }
    };

    private TextView statDay;
    private TextView statFollowers;
    private TextView statStress;
    private TextView statAffection;
    private TextView statDarkness;

    private ScrollView chatScrollView;
    private LinearLayout chatMessagesLayout;
    private EditTextBoldCursor inputField;
    private FrameLayout sendButton;
    private ImageView sendIcon;
    private ProgressBar sendProgress;

    // Multimodal image attachment
    private String attachedImagePath = null;
    private FrameLayout attachedImageBar;
    private ImageView attachedImageThumb;
    private ImageView attachButton;

    // Monster console: live MioTool call log (toggle chip in composer).
    private LinearLayout consolePanel;
    private LinearLayout consoleLog;
    private ScrollView consoleScroll;
    private boolean consoleOpen = false;
    private app.miogram.bridge.ai.tools.MioTool.ConsoleListener consoleListener;

    private static class MessageViewHolder {
        View rowView;
        LinearLayout bubbleCard;
        TextView bodyView;
        TextView timeView;
        ImageView avatarView;
        ImageView photoView;
        LinearLayout actionCard;
    }

    private final Map<MiogramCompanionPrefs.ChatMessage, MessageViewHolder> messageViewHolderMap = new HashMap<>();
    private final List<MiogramCompanionPrefs.ChatMessage> history = new ArrayList<>();
    private boolean isSending = false;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public MiogramCompanionActivity() {
        this(null);
    }

    public MiogramCompanionActivity(Bundle args) {
        super(args);
        if (args != null) {
            scopedDialogId = args.getLong("dialog_id", 0);
        }
    }

    public void setDraftInsertCallback(Utilities.Callback<String> callback) {
        this.onDraftInsertCallback = callback;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        consoleListener = line -> AndroidUtilities.runOnUIThread(() -> appendConsoleLine(line));
        app.miogram.bridge.ai.tools.MioTool.addConsoleListener(consoleListener);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        try {
            app.miogram.bridge.ai.tools.MioTool.removeConsoleListener(consoleListener);
        } catch (Throwable ignore) {}
        consoleListener = null;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            if (fragmentView != null) {
                updateCompanionTheme();
                renderFullHistory();
            }
        }
    }

    @Override
    public View createView(Context context) {
        buildActionBar();

        FrameLayout root = new FrameLayout(context);
        fragmentView = root;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout mainColumn = new LinearLayout(context);
        mainColumn.setOrientation(LinearLayout.VERTICAL);
        root.addView(mainColumn, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Scrollable Chat History Layout
        chatScrollView = new ScrollView(context);
        chatScrollView.setFillViewport(true);
        mainColumn.addView(chatScrollView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        chatMessagesLayout = new LinearLayout(context);
        chatMessagesLayout.setOrientation(LinearLayout.VERTICAL);
        chatMessagesLayout.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        chatScrollView.addView(chatMessagesLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // 3. Compact Hero NSO Stage (Pinned at top of messages, scrolls naturally with chat)
        buildHeroCard(context, chatMessagesLayout);

        // 4. Quick Prompts & Composer Bar
        buildComposer(context, mainColumn);

        // Load persisted history
        history.addAll(MiogramCompanionPrefs.loadHistory());
        if (history.isEmpty()) {
            addInitialGreeting();
        } else {
            renderFullHistory();
        }

        updateCompanionTheme();
        setHeroCardCollapsed(MiogramCompanionPrefs.isHeroCollapsed());
        if (heroCarouselScroll != null) {
            boolean isAme = MiogramCompanionPrefs.isAmeActive();
            int pageWidth = getCardSlideWidth() + AndroidUtilities.dp(8);
            heroCarouselScroll.post(() -> heroCarouselScroll.scrollTo(isAme ? 0 : pageWidth, 0));
        }

        return fragmentView;
    }

    private void buildActionBar() {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    showClearHistoryDialog();
                } else if (id == 2) {
                    MiogramSupabaseBridge.showBugReportDialog(
                            getParentActivity(),
                            MiogramLocale.get("Звіт про баг", "Отчет о баге", "Bug Report"),
                            MiogramLocale.get(
                                    "Бажаєте надіслати звіт про помилку та логи творцю @dkramochka?",
                                    "Желаете отправить отчет об ошибке и логи создателю @dkramochka?",
                                    "Would you like to send a bug report and logs to creator @dkramochka?"
                            ),
                            "AI Companion User Report",
                            "User reported issue from companion menu. Active: " + (MiogramCompanionPrefs.isYumiActive() ? "Yumi" : "Mio")
                    );
                } else if (id == 3) {
                    switchCompanion(!MiogramCompanionPrefs.isYumiActive());
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem headerItem = menu.addItem(0, R.drawable.ic_ab_other);
        headerItem.addSubItem(1, R.drawable.msg_clear, MiogramLocale.get("Очистити діалог", "Очистить диалог", "Clear dialogue"));
        headerItem.addSubItem(2, R.drawable.msg_log, MiogramLocale.get("Звіт про баг (@dkramochka)", "Отчет о баге (@dkramochka)", "Report bug (@dkramochka)"));
        headerItem.addSubItem(3, R.drawable.msg_theme, MiogramLocale.get("Змінити супутницю (Юмі ↔ Міо)", "Сменить спутницу (Юми ↔ Мио)", "Switch companion (Yumi ↔ Mio)"));
    }

    private int getCardSlideWidth() {
        int w = 0;
        if (heroCarouselScroll != null && heroCarouselScroll.getWidth() > 0) {
            w = heroCarouselScroll.getWidth();
        } else if (chatMessagesLayout != null && chatMessagesLayout.getWidth() > 0) {
            w = chatMessagesLayout.getWidth();
        } else if (fragmentView != null && fragmentView.getWidth() > 0) {
            w = fragmentView.getWidth() - AndroidUtilities.dp(20);
        } else if (AndroidUtilities.displaySize != null && AndroidUtilities.displaySize.x > 0) {
            w = AndroidUtilities.displaySize.x - AndroidUtilities.dp(20);
        }
        if (w <= 0) {
            w = 1080;
        }
        // heroCarouselLayout has 16dp horizontal padding (8dp left + 8dp right)
        // Keep card bounds strictly inside container to avoid overflow on all screens/tablets
        int maxSafe = Math.min(w - AndroidUtilities.dp(24), AndroidUtilities.dp(340));
        return Math.max(AndroidUtilities.dp(220), maxSafe);
    }

    private void updateCardWidths() {
        int w = getCardSlideWidth();
        if (ameSlideCard != null) {
            ViewGroup.LayoutParams lp = ameSlideCard.getLayoutParams();
            if (lp != null && lp.width != w) {
                lp.width = w;
                ameSlideCard.setLayoutParams(lp);
            }
        }
        if (kangelSlideCard != null) {
            ViewGroup.LayoutParams lp = kangelSlideCard.getLayoutParams();
            if (lp != null && lp.width != w) {
                lp.width = w;
                kangelSlideCard.setLayoutParams(lp);
            }
        }
    }

    private void buildHeroCard(Context context, LinearLayout parent) {
        heroCard = new LinearLayout(context);
        heroCard.setOrientation(LinearLayout.VERTICAL);
        heroCard.setGravity(Gravity.CENTER_HORIZONTAL);
        heroCard.setPadding(0, AndroidUtilities.dp(2), 0, AndroidUtilities.dp(4));
        LinearLayout.LayoutParams cardLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4);
        parent.addView(heroCard, cardLp);

        // Top Header Row: Segmented Switcher + Collapse button
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER);
        heroCard.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout switcherBar = new LinearLayout(context);
        switcherBar.setOrientation(LinearLayout.HORIZONTAL);
        switcherBar.setGravity(Gravity.CENTER);
        switcherBar.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        switcherBar.setPadding(AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3), AndroidUtilities.dp(3));
        headerRow.addView(switcherBar, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32));

        tabAme = new TextView(context);
        tabAme.setText("໒꒱ Юмі (Yumi)");
        tabAme.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tabAme.setTypeface(AndroidUtilities.bold());
        tabAme.setGravity(Gravity.CENTER);
        tabAme.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        switcherBar.addView(tabAme, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT));
        tabAme.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(true);
        });

        tabKAngel = new TextView(context);
        tabKAngel.setText("☕ Міо (Mio)");
        tabKAngel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tabKAngel.setTypeface(AndroidUtilities.bold());
        tabKAngel.setGravity(Gravity.CENTER);
        tabKAngel.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        switcherBar.addView(tabKAngel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 4, 0, 0, 0));
        tabKAngel.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(false);
        });

        heroToggleBtn = new TextView(context);
        heroToggleBtn.setText(heroCollapsed ? "▼" : "▲");
        heroToggleBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        heroToggleBtn.setTypeface(AndroidUtilities.bold());
        heroToggleBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        heroToggleBtn.setGravity(Gravity.CENTER);
        heroToggleBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite)));
        heroToggleBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(4));
        headerRow.addView(heroToggleBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 6, 0, 0, 0));
        heroToggleBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            setHeroCardCollapsed(!heroCollapsed);
        });

        // Compact Hero Bar (displayed when collapsed)
        compactHeroBar = new LinearLayout(context);
        compactHeroBar.setOrientation(LinearLayout.HORIZONTAL);
        compactHeroBar.setGravity(Gravity.CENTER_VERTICAL);
        compactHeroBar.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite)));
        compactHeroBar.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        heroCard.addView(compactHeroBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 6, 0, 6, 4));

        compactAvatar = new ImageView(context);
        compactAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        compactHeroBar.addView(compactAvatar, LayoutHelper.createLinear(28, 28, 0, 0, 8, 0));

        compactTitle = new TextView(context);
        compactTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactTitle.setTypeface(AndroidUtilities.bold());
        compactTitle.setSingleLine(true);
        compactHeroBar.addView(compactTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        compactStats = new TextView(context);
        compactStats.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        compactStats.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        compactStats.setSingleLine(true);
        compactHeroBar.addView(compactStats, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 8, 0, 4, 0));

        TextView expandHint = new TextView(context);
        expandHint.setText("▼");
        expandHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        expandHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        compactHeroBar.addView(expandHint, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        compactHeroBar.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            setHeroCardCollapsed(false);
        });

        // 1. Horizontal Scroll Slides Carousel
        heroCarouselScroll = new HorizontalScrollView(context);
        heroCarouselScroll.setHorizontalScrollBarEnabled(false);
        heroCarouselScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        heroCard.addView(heroCarouselScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        heroCarouselLayout = new LinearLayout(context);
        heroCarouselLayout.setOrientation(LinearLayout.HORIZONTAL);
        heroCarouselLayout.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(2), AndroidUtilities.dp(8), AndroidUtilities.dp(2));
        heroCarouselScroll.addView(heroCarouselLayout, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        // Slide 1: Ame-chan Card
        buildAmeSlide(context, heroCarouselLayout);

        // Slide 2: KAngel Card
        buildKangelSlide(context, heroCarouselLayout);

        // 2. Dots Indicator Row
        dotsRow = new LinearLayout(context);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, AndroidUtilities.dp(4), 0, AndroidUtilities.dp(2));
        heroCard.addView(dotsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        boolean isAme = MiogramCompanionPrefs.isAmeActive();

        dotAme = new View(context);
        dotsRow.addView(dotAme, LayoutHelper.createLinear(isAme ? 20 : 8, 6, 0, 0, 6, 0));
        dotAme.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(true);
        });

        dotKAngel = new View(context);
        dotsRow.addView(dotKAngel, LayoutHelper.createLinear(!isAme ? 20 : 8, 6));
        dotKAngel.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(false);
        });

        // 3. Carousel Snapping with settle check
        heroCarouselScroll.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                lastCarouselScrollX = -1;
                heroCarouselScroll.postDelayed(carouselSettleRunnable, 60);
            }
            return false;
        });

        heroCard.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left != oldRight - oldLeft) {
                updateCardWidths();
                // Re-seat the active persona page after resize/rotation so the
                // card never renders half-cut (looks like overflow off-screen).
                if (heroCarouselScroll != null) {
                    int pageWidth = getCardSlideWidth() + AndroidUtilities.dp(8);
                    heroCarouselScroll.scrollTo(MiogramCompanionPrefs.isAmeActive() ? 0 : pageWidth, 0);
                }
            }
        });

        updateCarouselVisuals(isAme);
    }

    private void buildAmeSlide(Context context, LinearLayout parent) {
        int cardWidth = getCardSlideWidth();
        ameSlideCard = new LinearLayout(context);
        ameSlideCard.setOrientation(LinearLayout.VERTICAL);
        ameSlideCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(cardWidth, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0);
        parent.addView(ameSlideCard, lp);

        // Header Row: Name + Subtitle + Model
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        ameSlideCard.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        headerRow.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView name = new TextView(context);
        name.setText("໒꒱ Юмі (Yumi)");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(0xFF9D4EDD);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name);

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get("Цундере-хакерка • ROOT ACCESS", "Цундере-хакерша • ROOT ACCESS", "Tsundere hacker • ROOT ACCESS"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(sub);

        TextView model = new TextView(context);
        model.setText("Yumi-Core");
        model.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        model.setTypeface(AndroidUtilities.bold());
        model.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        model.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
        model.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        model.setSingleLine(true);
        headerRow.addView(model, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

        // Middle Row: Sprite + Speech Bubble
        LinearLayout middleRow = new LinearLayout(context);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        middleRow.setGravity(Gravity.CENTER_VERTICAL);
        ameSlideCard.addView(middleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        ameSlideAvatar = new ImageView(context);
        ameSlideAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        ameSlideAvatar.setImageResource(R.drawable.miogram_ai_ame_neutral);
        middleRow.addView(ameSlideAvatar, LayoutHelper.createLinear(64, 56));

        ameSpeechBubble = new TextView(context);
        ameSpeechBubble.setText(MiogramLocale.get("«Хмпф! Тільки не кажи, що знову сам не розібрався ww І що мені з тобою робити? (¬_¬)»", "«Хмпф! Только не говори, что опять сам не разобрался ww И что мне с тобой делать? (¬_¬)»", "\"Hmph! Don't tell me you couldn't figure it out yourself again ww What am I gonna do with you? (¬_¬)\""));
        ameSpeechBubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        ameSpeechBubble.setTextColor(0xFFFFFFFF);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0x339D4EDD);
        bubble.setCornerRadius(AndroidUtilities.dp(10));
        bubble.setStroke(AndroidUtilities.dp(1), 0x669D4EDD);
        ameSpeechBubble.setBackground(bubble);
        ameSpeechBubble.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        ameSpeechBubble.setMaxLines(3);
        ameSpeechBubble.setEllipsize(TextUtils.TruncateAt.END);
        middleRow.addView(ameSpeechBubble, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 8, 0, 0, 0));

        // Stats Row
        LinearLayout statsBar = new LinearLayout(context);
        statsBar.setOrientation(LinearLayout.HORIZONTAL);
        statsBar.setGravity(Gravity.CENTER);
        ameSlideCard.addView(statsBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        statDay = createStatChip(context, "SYNC 95%", 0xFF9D4EDD);
        statsBar.addView(statDay, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 3, 0));

        statStress = createStatChip(context, "TSUN " + MiogramCompanionPrefs.getStress() + "%", 0xFFE84393);
        statsBar.addView(statStress, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 3, 0));

        statAffection = createStatChip(context, "BOND " + MiogramCompanionPrefs.getAffection() + "%", 0xFFFF70A6);
        statsBar.addView(statAffection, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.1f, 0, 0, 3, 0));

        statDarkness = createStatChip(context, "CORE " + MiogramCompanionPrefs.getDarkness() + "%", 0xFF6C5CE7);
        statsBar.addView(statDarkness, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        // Bottom Select / Active Button
        ameSelectBtn = new TextView(context);
        ameSelectBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        ameSelectBtn.setTypeface(AndroidUtilities.bold());
        ameSelectBtn.setGravity(Gravity.CENTER);
        ameSlideCard.addView(ameSelectBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        ameSlideCard.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(true);
        });
        ameSelectBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(true);
        });
    }

    private void buildKangelSlide(Context context, LinearLayout parent) {
        int cardWidth = getCardSlideWidth();
        kangelSlideCard = new LinearLayout(context);
        kangelSlideCard.setOrientation(LinearLayout.VERTICAL);
        kangelSlideCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(cardWidth, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 0);
        parent.addView(kangelSlideCard, lp);

        // Header Row: Name + Subtitle + Model
        LinearLayout headerRow = new LinearLayout(context);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        kangelSlideCard.addView(headerRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        headerRow.addView(titles, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView name = new TextView(context);
        name.setText(MiogramLocale.get("☕ Міо (Mio)", "☕ Мио (Mio)", "☕ Mio"));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTypeface(AndroidUtilities.bold());
        name.setTextColor(0xFF00B4D8);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(name);

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get("Домашній затишок & спокій • HAVEN", "Домашний уют & покой • HAVEN", "Cozy peace & comfort • HAVEN"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(sub);

        TextView model = new TextView(context);
        model.setText("Mio-Haven");
        model.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        model.setTypeface(AndroidUtilities.bold());
        model.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        model.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
        model.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(2), AndroidUtilities.dp(6), AndroidUtilities.dp(2));
        model.setSingleLine(true);
        headerRow.addView(model, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 0, 0));

        // Middle Row: Sprite + Speech Bubble
        LinearLayout middleRow = new LinearLayout(context);
        middleRow.setOrientation(LinearLayout.HORIZONTAL);
        middleRow.setGravity(Gravity.CENTER_VERTICAL);
        kangelSlideCard.addView(middleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        kangelSlideAvatar = new ImageView(context);
        kangelSlideAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
        kangelSlideAvatar.setImageResource(R.drawable.miogram_ai_kangel_neutral);
        middleRow.addView(kangelSlideAvatar, LayoutHelper.createLinear(64, 56));

        kangelSpeechBubble = new TextView(context);
        kangelSpeechBubble.setText(MiogramLocale.get("«Привіт... я заварила тобі гарячого чаю ☕ Відпочинь трохи, ти сьогодні втомився... (´｡• ω •｡`)»", "«Привет... я заварила тебе горячего чая ☕ Отдохни немного, ты сегодня устал... (´｡• ω •｡`)»", "\"Hello... I brewed you some warm tea ☕ Rest a bit, you've had a long day... (´｡• ω •｡`)\""));
        kangelSpeechBubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        kangelSpeechBubble.setTextColor(0xFFFFFFFF);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0x3300B4D8);
        bubble.setCornerRadius(AndroidUtilities.dp(10));
        bubble.setStroke(AndroidUtilities.dp(1), 0x6600B4D8);
        kangelSpeechBubble.setBackground(bubble);
        kangelSpeechBubble.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(6), AndroidUtilities.dp(8), AndroidUtilities.dp(6));
        kangelSpeechBubble.setMaxLines(3);
        kangelSpeechBubble.setEllipsize(TextUtils.TruncateAt.END);
        middleRow.addView(kangelSpeechBubble, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 8, 0, 0, 0));

        // Stats Row
        LinearLayout statsBar = new LinearLayout(context);
        statsBar.setOrientation(LinearLayout.HORIZONTAL);
        statsBar.setGravity(Gravity.CENTER);
        kangelSlideCard.addView(statsBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

        TextView statDay2 = createStatChip(context, "CALM 98%", 0xFF2EC4B6);
        statsBar.addView(statDay2, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 3, 0));

        statFollowers = createStatChip(context, "WARM 95%", 0xFF00B4D8);
        statsBar.addView(statFollowers, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.1f, 0, 0, 3, 0));

        TextView statHype = createStatChip(context, "BOND 92%", 0xFFE84393);
        statsBar.addView(statHype, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 3, 0));

        TextView statBlessing = createStatChip(context, "CARE ☕", 0xFFFFB703);
        statsBar.addView(statBlessing, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.1f));

        // Bottom Select / Active Button
        kangelSelectBtn = new TextView(context);
        kangelSelectBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        kangelSelectBtn.setTypeface(AndroidUtilities.bold());
        kangelSelectBtn.setGravity(Gravity.CENTER);
        kangelSlideCard.addView(kangelSelectBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 28));

        kangelSlideCard.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(false);
        });
        kangelSelectBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            switchCompanion(false);
        });
    }

    private void setHeroCardCollapsed(boolean collapsed) {
        heroCollapsed = collapsed;
        MiogramCompanionPrefs.setHeroCollapsed(collapsed);

        if (compactHeroBar != null) {
            compactHeroBar.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        }
        if (heroCarouselScroll != null) {
            heroCarouselScroll.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (dotsRow != null) {
            dotsRow.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        }
        if (heroToggleBtn != null) {
            heroToggleBtn.setText(collapsed ? "▼" : "▲");
        }
    }

    private void updateStatsViews() {
        boolean isYumi = MiogramCompanionPrefs.isYumiActive();
        int str = MiogramCompanionPrefs.getStress();
        int love = MiogramCompanionPrefs.getAffection();
        int dark = MiogramCompanionPrefs.getDarkness();
        long fol = MiogramCompanionPrefs.getFollowers();

        if (statStress != null) statStress.setText(isYumi ? ("TSUN " + str + "%") : ("TIRED " + str + "%"));
        if (statAffection != null) statAffection.setText("BOND " + love + "%");
        if (statDarkness != null) statDarkness.setText("CORE " + dark + "%");
        if (statFollowers != null) statFollowers.setText(isYumi ? ("SYNC " + formatFollowers(fol)) : ("WARM " + formatFollowers(fol)));

        if (compactStats != null) {
            compactStats.setText((isYumi ? "TSUN " : "TIRED ") + str + "% • BOND " + love + "% • " + (isYumi ? "SYNC " : "WARM ") + formatFollowers(fol));
        }
    }

    private static String formatFollowers(long fol) {
        if (fol >= 1_000_000) {
            return String.format(Locale.US, "%.1fM", fol / 1_000_000.0f);
        } else if (fol >= 1_000) {
            return String.format(Locale.US, "%.1fK", fol / 1_000.0f);
        }
        return String.valueOf(fol);
    }

    private void snapCarouselToNearestPage() {
        if (heroCarouselScroll == null) return;
        int scrollX = heroCarouselScroll.getScrollX();
        int pageWidth = getCardSlideWidth() + AndroidUtilities.dp(8);
        int targetPage = (scrollX + pageWidth / 3) / pageWidth;
        if (targetPage < 0) targetPage = 0;
        if (targetPage > 1) targetPage = 1;
        heroCarouselScroll.smoothScrollTo(targetPage * pageWidth, 0);
        boolean toAme = (targetPage == 0);
        if (toAme != MiogramCompanionPrefs.isAmeActive()) {
            switchCompanion(toAme);
        } else {
            updateCarouselVisuals(toAme);
        }
    }

    private void updateCarouselVisuals(boolean isAme) {
        if (ameSlideCard != null) {
            ameSlideCard.setBackground(createCompanionSlideDrawable(isAme, 0xFF9D4EDD));
        }
        if (kangelSlideCard != null) {
            kangelSlideCard.setBackground(createCompanionSlideDrawable(!isAme, 0xFF00B4D8));
        }
        if (ameSelectBtn != null) {
            ameSelectBtn.setText(isAme
                    ? MiogramLocale.get("✓ АКТИВНА СУПУТНИЦЯ (ЮМІ) ໒꒱", "✓ АКТИВНАЯ СПУТНИЦА (ЮМИ) ໒꒱", "✓ ACTIVE COMPANION (YUMI) ໒꒱")
                    : MiogramLocale.get("ОБРАТИ ЮМІ ໒꒱", "ВЫБРАТЬ ЮМИ ໒꒱", "CHOOSE YUMI ໒꒱"));
            ameSelectBtn.setTextColor(isAme ? 0xFFFFFFFF : 0xFF9D4EDD);
            ameSelectBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), isAme ? 0xFF9D4EDD : 0x229D4EDD));
        }
        if (kangelSelectBtn != null) {
            kangelSelectBtn.setText(!isAme
                    ? MiogramLocale.get("✓ АКТИВНА СУПУТНИЦЯ (МІО) ☕", "✓ АКТИВНАЯ СПУТНИЦА (МИО) ☕", "✓ ACTIVE COMPANION (MIO) ☕")
                    : MiogramLocale.get("ОБРАТИ МІО ☕", "ВЫБРАТЬ МИО ☕", "CHOOSE MIO ☕"));
            kangelSelectBtn.setTextColor(!isAme ? 0xFFFFFFFF : 0xFF00B4D8);
            kangelSelectBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), !isAme ? 0xFF00B4D8 : 0x2200B4D8));
        }
        if (dotAme != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dotAme.getLayoutParams();
            if (lp != null) {
                lp.width = AndroidUtilities.dp(isAme ? 20 : 8);
                dotAme.setLayoutParams(lp);
            }
            dotAme.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), isAme ? 0xFF9D4EDD : 0x44888888));
        }
        if (dotKAngel != null) {
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dotKAngel.getLayoutParams();
            if (lp != null) {
                lp.width = AndroidUtilities.dp(!isAme ? 20 : 8);
                dotKAngel.setLayoutParams(lp);
            }
            dotKAngel.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(3), !isAme ? 0xFF00B4D8 : 0x44888888));
        }
        if (tabAme != null) {
            tabAme.setTextColor(isAme ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            tabAme.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), isAme ? 0xFF9D4EDD : 0x00000000));
        }
        if (tabKAngel != null) {
            tabKAngel.setTextColor(!isAme ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            tabKAngel.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), !isAme ? 0xFF00B4D8 : 0x00000000));
        }
    }

    private Drawable createCompanionSlideDrawable(boolean active, int accentColor) {
        GradientDrawable gd = new GradientDrawable();
        int baseBg = Theme.getColor(Theme.key_windowBackgroundWhite);
        if (active) {
            gd.setColor(baseBg != 0 ? baseBg : 0xFF181822);
            gd.setStroke(AndroidUtilities.dp(1.8f), accentColor);
        } else {
            gd.setColor(baseBg != 0 ? baseBg : 0xFF181822);
            gd.setStroke(AndroidUtilities.dp(1), Color.argb(0x44, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor)));
        }
        gd.setCornerRadius(AndroidUtilities.dp(16));
        return gd;
    }

    private TextView createStatChip(Context context, String text, int color) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(color);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setMaxLines(1);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        int bg = Color.argb(0x22, Color.red(color), Color.green(color), Color.blue(color));
        chip.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(6), bg));
        chip.setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(3), AndroidUtilities.dp(2), AndroidUtilities.dp(3));
        return chip;
    }

    private void switchCompanion(boolean toAme) {
        MiogramCompanionPrefs.setActiveCompanion(toAme ? MiogramCompanionPrefs.COMPANION_AME : MiogramCompanionPrefs.COMPANION_KANGEL);
        MiogramCompanionPrefs.setOnboardingCompleted(true);
        updateCompanionTheme();
        updateCarouselVisuals(toAme);
        if (heroCarouselScroll != null) {
            int pageWidth = getCardSlideWidth() + AndroidUtilities.dp(8);
            heroCarouselScroll.smoothScrollTo(toAme ? 0 : pageWidth, 0);
        }
        if (history.isEmpty() || (history.size() == 1 && !history.get(0).isUser)) {
            history.clear();
            addInitialGreeting();
        } else {
            renderFullHistory();
        }
    }

    private void buildComposer(Context context, LinearLayout parent) {
        // Quick Action Chips Row in Horizontal Scroll
        HorizontalScrollView chipsScroll = new HorizontalScrollView(context);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        chipsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        parent.addView(chipsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        chipsScroll.addView(chipsRow, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        addChip(context, chipsRow, MiogramLocale.get("Як справи?", "Как дела?", "How are you?"), () -> {
            boolean isY = MiogramCompanionPrefs.isYumiActive();
            inputField.setText(isY
                    ? MiogramLocale.get("Юмі, як справи? Що нового кодиш? (¬_¬)", "Юми, как дела? Что нового кодишь? (¬_¬)", "Yumi, how are you? What are you coding? (¬_¬)")
                    : MiogramLocale.get("Міо, як справи? Ти відпочила? (´｡• ω •｡`)", "Мио, как дела? Ты отдохнула? (´｡• ω •｡`)", "Mio, how are you? Did you rest well? (´｡• ω •｡`)"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Що в чатах?", "Что в чатах?", "What's in chats?"), () -> {
            inputField.setText(MiogramLocale.get("Що нового в моїх чатах? Зроби короткий огляд!", "Что нового в моих чатах? Сделай краткий обзор!", "What's new in my chats? Give me a quick summary!"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Непрочитані", "Непрочитанные", "Unread"), () -> {
            inputField.setText(MiogramLocale.get("Почитай мої непрочитані повідомлення і розкажи що там пишуть", "Почитай мои непрочитанные сообщения и расскажи что там пишут", "Read my unread messages and summarize what's happening"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Зараз грає", "Сейчас играет", "Now playing"), () -> {
            inputField.setText(MiogramLocale.get("Що зараз грає в плеєрі?", "Что сейчас играет в плеере?", "What's currently playing in the music player?"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Заварити чай ☕", "Заварить чай ☕", "Brew tea ☕"), () -> {
            boolean isY = MiogramCompanionPrefs.isYumiActive();
            inputField.setText(isY
                    ? MiogramLocale.get("Юмі, відволічись від коду і консолі, давай вип'ємо чаю ☕", "Юми, отвлекись от кода и консоли, давай попьем чаю ☕", "Yumi, take a break from code and console, let's drink tea ☕")
                    : MiogramLocale.get("Міо, давай зробимо затишну паузу і заваримо ароматного чаю ☕", "Мио, давай сделаем уютную паузу и заварим ароматного чая ☕", "Mio, let's take a cozy break and brew fragrant tea ☕"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Дурнику ww", "Дурачок ww", "Silly ww"), () -> {
            inputField.setText(MiogramLocale.get("Юмі, ти знову бурчиш? Дурнику ww (¬_¬)", "Юми, ты снова ворчишь? Дурачок ww (¬_¬)", "Yumi, are you pouting again? Silly ww (¬_¬)"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Знайти чат", "Найти чат", "Find chat"), () -> {
            inputField.setText(MiogramLocale.get("Знайди в лс з ", "Найди в лс с ", "Find chat with "));
            inputField.setSelection(inputField.getText().length());
            inputField.requestFocus();
            AndroidUtilities.showKeyboard(inputField);
        });

        addChip(context, chipsRow, MiogramLocale.get("Пошук по групах", "Поиск по группам", "Search groups"), () -> {
            inputField.setText(MiogramLocale.get("Пошукай по групах що пишуть про ", "Поищи по группам что пишут про ", "Search groups for "));
            inputField.setSelection(inputField.getText().length());
            inputField.requestFocus();
            AndroidUtilities.showKeyboard(inputField);
        });

        addChip(context, chipsRow, MiogramLocale.get("Юзербот .ping", "Юзербот .ping", "Userbot .ping"), () -> {
            inputField.setText(MiogramLocale.get("Перевір затримку через юзербот команду ping", "Проверь пинг через команду юзербота ping", "Check latency using userbot command ping"));
            onSendMessage();
        });

        addChip(context, chipsRow, MiogramLocale.get("Мої плагіни", "Мои плагины", "My Plugins"), () -> {
            inputField.setText(MiogramLocale.get("Покажи список встановлених плагінів MioHook", "Покажи список установленных плагинов MioHook", "Show list of installed MioHook plugins"));
            onSendMessage();
        });

        if (scopedDialogId != 0) {
            addChip(context, chipsRow, MiogramLocale.get("Що тут пишуть?", "Что тут пишут?", "What are they writing?"), () -> {
                inputField.setText(MiogramLocale.get("Прочитай останні повідомлення цього чату", "Прочитай последние сообщения этого чата", "Read the latest messages in this chat"));
                onSendMessage();
            });
            addChip(context, chipsRow, MiogramLocale.get("Почистити цей чат", "Очистить этот чат", "Clear this chat"), () -> {
                inputField.setText(MiogramLocale.get("Будь ласка, очисти історію цього чату", "Пожалуйста, очисти историю этого чата", "Please clear history of this chat"));
                onSendMessage();
            });
        } else {
            addChip(context, chipsRow, MiogramLocale.get("Почистити чат", "Очистить чат", "Clear chat"), () -> {
                inputField.setText(MiogramLocale.get("Допоможи мені почистити непотрібні чати", "Помоги мне очистить ненужные чаты", "Help me clear unneeded chats"));
                onSendMessage();
            });
        }
        addChip(context, chipsRow, MiogramLocale.get("Ghost Mode", "Ghost Mode", "Ghost Mode"), () -> {
            inputField.setText(MiogramLocale.get("Перемкни Ghost Mode", "Переключи Ghost Mode", "Toggle Ghost Mode"));
            onSendMessage();
        });
        addChip(context, chipsRow, MiogramLocale.get("Консоль", "Консоль", "Console"), this::toggleConsole);

        addChip(context, chipsRow, MiogramLocale.get("Звіт про баг", "Отчет о баге", "Report Bug"), () -> {
            MiogramSupabaseBridge.showBugReportDialog(
                    getParentActivity(),
                    MiogramLocale.get("Звіт про баг", "Отчет о баге", "Bug Report"),
                    MiogramLocale.get(
                            "Бажаєте надіслати звіт про помилку та логи творцю @dkramochka?",
                            "Желаете отправить отчет об ошибке и логи создателю @dkramochka?",
                            "Would you like to send a bug report and logs to creator @dkramochka?"
                    ),
                    "AI Companion User Report",
                    "User reported issue from companion chips. Active: " + (MiogramCompanionPrefs.isYumiActive() ? "Yumi" : "Mio")
            );
        });

        // Monster console panel (collapsed by default, above composer).
        consolePanel = new LinearLayout(context);
        consolePanel.setOrientation(LinearLayout.VERTICAL);
        consolePanel.setBackgroundColor(0xFF0D1117);
        consolePanel.setVisibility(View.GONE);
        consolePanel.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));
        consoleScroll = new ScrollView(context);
        consoleScroll.setVerticalScrollBarEnabled(true);
        consoleLog = new LinearLayout(context);
        consoleLog.setOrientation(LinearLayout.VERTICAL);
        consoleScroll.addView(consoleLog, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        consolePanel.addView(consoleScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(140)));
        parent.addView(consolePanel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Multimodal Attached Image Bar (Preview above composer)
        attachedImageBar = new FrameLayout(context);
        attachedImageBar.setVisibility(View.GONE);
        attachedImageBar.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(2));
        parent.addView(attachedImageBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        LinearLayout imgPreviewBox = new LinearLayout(context);
        imgPreviewBox.setOrientation(LinearLayout.HORIZONTAL);
        imgPreviewBox.setGravity(Gravity.CENTER_VERTICAL);
        imgPreviewBox.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite)));
        imgPreviewBox.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
        attachedImageBar.addView(imgPreviewBox, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        attachedImageThumb = new ImageView(context);
        attachedImageThumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
        attachedImageThumb.setClipToOutline(true);
        imgPreviewBox.addView(attachedImageThumb, LayoutHelper.createLinear(36, 36));

        TextView imgLabel = new TextView(context);
        imgLabel.setText(MiogramLocale.get("Фото прикріплено (мультимодальний зір)", "Фото прикреплено (мультимодальное зрение)", "Photo attached (multimodal vision)"));
        imgLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        imgLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        imgPreviewBox.addView(imgLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 8, 0));

        ImageView removeImgBtn = new ImageView(context);
        removeImgBtn.setImageResource(R.drawable.msg_clear);
        removeImgBtn.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2), PorterDuff.Mode.SRC_IN));
        imgPreviewBox.addView(removeImgBtn, LayoutHelper.createLinear(20, 20));
        removeImgBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            attachedImagePath = null;
            updateAttachedImageBar();
        });

        // Telegram Native Composer Bar
        LinearLayout composerBar = new LinearLayout(context);
        composerBar.setOrientation(LinearLayout.HORIZONTAL);
        composerBar.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelBackground));
        composerBar.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(10), AndroidUtilities.dp(8));
        composerBar.setGravity(Gravity.CENTER_VERTICAL);
        parent.addView(composerBar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Photo/Image Attach Button (Multimodal Gemini Vision)
        attachButton = new ImageView(context);
        attachButton.setImageResource(R.drawable.msg_camera);
        attachButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        attachButton.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_chat_messagePanelIcons), PorterDuff.Mode.SRC_IN));
        attachButton.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
        composerBar.addView(attachButton, LayoutHelper.createLinear(38, 38, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));
        attachButton.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            openPhotoPicker();
        });

        FrameLayout inputWrapper = new FrameLayout(context);
        GradientDrawable inputGd = new GradientDrawable();
        inputGd.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        inputGd.setCornerRadius(AndroidUtilities.dp(20));
        inputGd.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        inputWrapper.setBackground(inputGd);
        composerBar.addView(inputWrapper, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 8, 0));

        inputField = new EditTextBoldCursor(context);
        inputField.setHint(MiogramLocale.get("Повідомлення для супутника…", "Сообщение для спутника…", "Message companion…"));
        inputField.setHintTextColor(Theme.getColor(Theme.key_chat_messagePanelHint));
        inputField.setTextColor(Theme.getColor(Theme.key_chat_messagePanelText));
        inputField.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        inputField.setBackground(null);
        inputField.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        inputField.setMaxLines(4);
        inputWrapper.addView(inputField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Telegram Circular Send Button
        sendButton = new FrameLayout(context);
        sendButton.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(42), 0xFFFF70A6));
        composerBar.addView(sendButton, LayoutHelper.createLinear(42, 42));

        sendIcon = new ImageView(context);
        sendIcon.setImageResource(R.drawable.ic_send);
        sendIcon.setColorFilter(new PorterDuffColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN));
        sendIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        sendButton.addView(sendIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

        sendProgress = new ProgressBar(context);
        sendProgress.setVisibility(View.GONE);
        sendButton.addView(sendProgress, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

        sendButton.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            onSendMessage();
        });
    }

    private void openPhotoPicker() {
        PhotoAlbumPickerActivity fragment = new PhotoAlbumPickerActivity(PhotoAlbumPickerActivity.SELECT_TYPE_ALL, false, false, null);
        fragment.setMaxSelectedPhotos(1, false);
        fragment.setDelegate(new PhotoAlbumPickerActivity.PhotoAlbumPickerActivityDelegate() {
            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
                if (photos != null && !photos.isEmpty()) {
                    SendMessagesHelper.SendingMediaInfo info = photos.get(0);
                    if (info != null && !TextUtils.isEmpty(info.path)) {
                        attachedImagePath = info.path;
                        updateAttachedImageBar();
                    }
                }
            }

            @Override
            public void startPhotoSelectActivity() {}
        });
        presentFragment(fragment);
    }

    private void updateAttachedImageBar() {
        if (attachedImageBar == null) return;
        if (attachedImagePath != null && new File(attachedImagePath).exists()) {
            attachedImageBar.setVisibility(View.VISIBLE);
            if (attachedImageThumb != null) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(attachedImagePath);
                    if (bmp != null) {
                        attachedImageThumb.setImageBitmap(bmp);
                    }
                } catch (Throwable ignore) {}
            }
        } else {
            attachedImageBar.setVisibility(View.GONE);
            if (attachedImageThumb != null) {
                attachedImageThumb.setImageDrawable(null);
            }
        }
    }

    private void addChip(Context context, LinearLayout parent, String text, Runnable onClick) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        gd.setCornerRadius(AndroidUtilities.dp(14));
        gd.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        chip.setBackground(gd);
        chip.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(5), AndroidUtilities.dp(10), AndroidUtilities.dp(5));
        LinearLayout.LayoutParams lp = LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0);
        parent.addView(chip, lp);

        chip.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            if (onClick != null) onClick.run();
        });
    }

    private void updateCompanionTheme() {
        boolean isYumi = MiogramCompanionPrefs.isYumiActive();
        int accentColor = isYumi ? 0xFF9D4EDD : 0xFF00B4D8;

        if (fragmentView != null) {
            fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        }

        // Action bar update
        actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
        actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        actionBar.setSubtitleColor(Theme.getColor(Theme.key_actionBarDefaultSubtitle));
        actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
        actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        actionBar.setTitle(isYumi ? "Юмі ໒꒱" : "Міо ☕");
        actionBar.setSubtitle(isYumi
                ? ("Yumi-Core • " + MiogramLocale.get("Цундере-ШІ ໒꒱", "Цундере-ИИ ໒꒱", "Tsundere AI ໒꒱"))
                : ("Mio-Haven • " + MiogramLocale.get("Затишна ШІ ☕", "Уютный ИИ ☕", "Cozy AI ☕"))
        );

        updateCarouselVisuals(isYumi);

        if (compactAvatar != null) {
            compactAvatar.setImageResource(isYumi ? R.drawable.miogram_ai_ame_avatar : R.drawable.miogram_ai_kangel_avatar);
        }
        if (compactTitle != null) {
            compactTitle.setText(isYumi ? "໒꒱ Юмі (Yumi)" : "☕ Міо (Mio)");
            compactTitle.setTextColor(accentColor);
        }
        updateStatsViews();

        if (attachButton != null) {
            attachButton.setColorFilter(new PorterDuffColorFilter(accentColor, PorterDuff.Mode.SRC_IN));
        }

        if (sendButton != null) {
            sendButton.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(42), accentColor));
        }

        if (inputField != null) {
            inputField.setCursorColor(accentColor);
        }
    }

    private void updateStageMood(String mood) {
        boolean isAme = MiogramCompanionPrefs.isAmeActive();
        int ameSprite = resolveSpriteForMood(mood, true);
        int kangelSprite = resolveSpriteForMood(mood, false);

        if (ameSlideAvatar != null) {
            ameSlideAvatar.setImageResource(ameSprite);
            if (isAme) {
                ameSlideAvatar.setScaleX(0.85f);
                ameSlideAvatar.setScaleY(0.85f);
                ameSlideAvatar.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        }
        if (kangelSlideAvatar != null) {
            kangelSlideAvatar.setImageResource(kangelSprite);
            if (!isAme) {
                kangelSlideAvatar.setScaleX(0.85f);
                kangelSlideAvatar.setScaleY(0.85f);
                kangelSlideAvatar.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new OvershootInterpolator(1.4f)).start();
            }
        }

        String m = mood != null ? mood.toLowerCase(Locale.US) : "neutral";
        if (ameSpeechBubble != null) {
            if (m.contains("happy") || m.contains("tsun")) {
                ameSpeechBubble.setText(MiogramLocale.get("«(★ω★) Хех, дивись і вчися, як працює справжній профі! Не дякуй ww»", "«(★ω★) Хех, смотри и учись, как работает настоящий профи! Не благодари ww»", "\"«(★ω★) Heh, watch and learn how a real pro works! Don't thank me ww»\""));
            } else if (m.contains("sad")) {
                ameSpeechBubble.setText(MiogramLocale.get("«(´-ω-`) Мені не сумно, просто процесор перегрівся... Не дивись так ww»", "«(´-ω-`) Мне не грустно, просто процессор перегрелся... Не смотри так ww»", "\"«(´-ω-`) I'm not sad, just my CPU overheating... Don't look at me like that ww»\""));
            } else {
                ameSpeechBubble.setText(MiogramLocale.get("«(¬_¬) Ну що знову? Тільки не кажи, що без мене нічого не працює ww»", "«(¬_¬) Ну что опять? Только не говори, что без меня ничего не работает ww»", "\"«(¬_¬) What is it now? Don't tell me nothing works without me ww»\""));
            }
        }
        if (kangelSpeechBubble != null) {
            if (m.contains("happy") || m.contains("calm")) {
                kangelSpeechBubble.setText(MiogramLocale.get("«(´｡• ω •｡`) Я така рада бачити тебе усміхненим... Тримай чашечку чаю ☕»", "«(´｡• ω •｡`) Я так рада видеть тебя улыбающимся... Держи чашечку чая ☕»", "\"«(´｡• ω •｡`) I'm so happy to see you smiling... Here's some tea ☕»\""));
            } else if (m.contains("shy") || m.contains("talk")) {
                kangelSpeechBubble.setText(MiogramLocale.get("«(´,,•ω•,,)♡ Все буде добре. Я поруч, не поспішай нікуди...»", "«(´,,•ω•,,)♡ Всё будет хорошо. Я рядом, не спеши никуда...»", "\"«(´,,•ω•,,)♡ Everything will be fine. I'm right here with you...»\""));
            } else if (m.contains("sad")) {
                kangelSpeechBubble.setText(MiogramLocale.get("«(´・ω・｀) Не сумуй, будь ласка... Давай просто помовчимо разом і поп'ємо чаю...»", "«(´・ω・｀) Не грусти, пожалуйста... Давай просто помолчим вместе и попьем чая...»", "\"«(´・ω・｀) Don't be sad, please... Let's just sit quietly together and have tea...»\""));
            } else {
                kangelSpeechBubble.setText(MiogramLocale.get("«☕ Затишний вечір... Як твій день минув? (´｡• ω •｡`)»", "«☕ Уютный вечер... Как твой день прошел? (´｡• ω •｡`)»", "\"«☕ Cozy evening... How was your day? (´｡• ω •｡`)»\""));
            }
        }
    }

    private int resolveSpriteForMood(String mood) {
        return resolveSpriteForMood(mood, MiogramCompanionPrefs.isAmeActive());
    }

    private int resolveSpriteForMood(String mood, boolean isAme) {
        String m = mood != null ? mood.toLowerCase(Locale.US) : "neutral";

        if (isAme) {
            if (m.contains("happy") || m.contains("tsun")) return R.drawable.miogram_ai_ame_happy;
            if (m.contains("sad")) return R.drawable.miogram_ai_ame_sad;
            if (m.contains("talk")) return R.drawable.miogram_ai_ame_talk;
            if (m.contains("game")) return R.drawable.miogram_ai_ame_game;
            if (m.contains("home")) return R.drawable.miogram_ai_ame_home;
            return R.drawable.miogram_ai_ame_neutral;
        } else {
            if (m.contains("happy")) return R.drawable.miogram_ai_kangel_happy;
            if (m.contains("calm") || m.contains("pray")) return R.drawable.miogram_ai_kangel_pray;
            if (m.contains("shy") || m.contains("start")) return R.drawable.miogram_ai_kangel_start;
            if (m.contains("cosplay")) return R.drawable.miogram_ai_kangel_cosplay;
            if (m.contains("sad")) return R.drawable.miogram_ai_kangel_sad;
            return R.drawable.miogram_ai_kangel_neutral;
        }
    }

    private void showClearHistoryDialog() {
        Context ctx = getParentActivity();
        if (ctx == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(MiogramLocale.get("Очистити діалог?", "Очистить диалог?", "Clear dialogue?"));
        builder.setMessage(MiogramLocale.get(
                "Ви дійсно хочете очистити історію спілкування зі супутником?",
                "Вы действительно хотите очистить историю общения со спутником?",
                "Are you sure you want to clear chat history with companion?"
        ));
        builder.setPositiveButton(MiogramLocale.get("Очистити", "Очистить", "Clear"), (d, which) -> {
            history.clear();
            MiogramCompanionPrefs.clearHistory();
            chatMessagesLayout.removeAllViews();
            addInitialGreeting();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void addInitialGreeting() {
        boolean isYumi = MiogramCompanionPrefs.isYumiActive();
        String text;
        String mood;
        if (isYumi) {
            text = MiogramLocale.get(
                    "Хмпф! Чого тобі? Тільки не кажи, що знову заплутався в чатах (¬_¬) Ладно вже, кажи що треба, допоможу ww",
                    "Хмпф! Чего тебе? Только не говори, что опять запутался в чатах (¬_¬) Ладно уж, говори что надо, помогу ww",
                    "Hmph! What do you need? Don't tell me you got lost in chats again (¬_¬) Fine, tell me what you need, I'll help ww"
            );
            mood = "talk";
        } else {
            text = MiogramLocale.get(
                    "Привіт... ☕ Я заварила свіжого чаю. Сідай зручніше і відпочинь. Якщо знадобиться допомога з повідомленнями чи справами — я завжди поруч (´｡• ω •｡`)",
                    "Привет... ☕ Я заварила свежего чая. Присаживайся поудобнее и отдохни. Если понадобится помощь с сообщениями или делами — я всегда рядом (´｡• ω •｡`)",
                    "Hello... ☕ I brewed fresh tea. Make yourself comfortable and rest. If you need any help with messages or tasks — I'm always right here (´｡• ω •｡`)"
            );
            mood = "calm";
        }
        MiogramCompanionPrefs.ChatMessage msg = new MiogramCompanionPrefs.ChatMessage(false, text, mood, System.currentTimeMillis(), null, null);
        history.add(msg);
        MiogramCompanionPrefs.saveHistory(history);
        renderMessageBubble(msg);
    }

    private void addSwitchAnnouncement(boolean toYumi) {
        String text = toYumi
                ? MiogramLocale.get("Перемкнулися на Юмі ໒꒱ Хмпф, сподіваюся, ти покликав мене для чогось дійсно важливого (¬_¬)", "Переключились на Юми ໒꒱ Хмпф, надеюсь, ты позвал меня ради чего-то действительно важного (¬_¬)", "Switched to Yumi ໒꒱ Hmph, hope you called me for something actually important (¬_¬)")
                : MiogramLocale.get("Перемкнулися на Міо ☕ Ласкаво просимо до нашого затишного куточка... Спокійно видихни (´｡• ω •｡`)", "Переключились на Мио ☕ Добро пожаловать в наш уютный уголок... Спокойно выдохни (´｡• ω •｡`)", "Switched to Mio ☕ Welcome to our cozy haven... Take a gentle breath (´｡• ω •｡`)");
        String mood = toYumi ? "talk" : "calm";
        MiogramCompanionPrefs.ChatMessage msg = new MiogramCompanionPrefs.ChatMessage(false, text, mood, System.currentTimeMillis(), null, null);
        history.add(msg);
        MiogramCompanionPrefs.saveHistory(history);
        renderMessageBubble(msg);
    }

    private void renderFullHistory() {
        messageViewHolderMap.clear();
        chatMessagesLayout.removeAllViews();
        if (heroCard != null) {
            chatMessagesLayout.addView(heroCard);
        }
        for (MiogramCompanionPrefs.ChatMessage msg : history) {
            renderMessageBubble(msg);
        }
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void renderMessageBubble(MiogramCompanionPrefs.ChatMessage msg) {
        Context ctx = chatMessagesLayout.getContext();
        boolean isYumi = MiogramCompanionPrefs.isYumiActive();
        int accentColor = isYumi ? 0xFF9D4EDD : 0xFF00B4D8;

        MessageViewHolder holder = new MessageViewHolder();

        LinearLayout bubbleRow = new LinearLayout(ctx);
        bubbleRow.setOrientation(LinearLayout.HORIZONTAL);
        if (msg.isUser) {
            bubbleRow.setGravity(Gravity.RIGHT);
            bubbleRow.setPadding(AndroidUtilities.dp(56), AndroidUtilities.dp(2), AndroidUtilities.dp(4), AndroidUtilities.dp(2));
        } else {
            bubbleRow.setGravity(Gravity.LEFT);
            bubbleRow.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(2), AndroidUtilities.dp(56), AndroidUtilities.dp(2));
        }
        chatMessagesLayout.addView(bubbleRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        holder.rowView = bubbleRow;

        if (!msg.isUser) {
            FrameLayout avatarFrame = new FrameLayout(ctx);
            avatarFrame.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(36), isYumi ? 0x259D4EDD : 0x2500B4D8));
            ImageView spriteAvatar = new ImageView(ctx);
            spriteAvatar.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int resId = resolveSpriteForMood(msg.mood, isYumi);
            spriteAvatar.setImageResource(resId);
            avatarFrame.addView(spriteAvatar, LayoutHelper.createFrame(32, 32, Gravity.CENTER));
            bubbleRow.addView(avatarFrame, LayoutHelper.createLinear(36, 36, Gravity.BOTTOM, 0, 0, 8, 0));
            holder.avatarView = spriteAvatar;
        }

        LinearLayout bubbleCard = new LinearLayout(ctx);
        bubbleCard.setOrientation(LinearLayout.VERTICAL);
        bubbleCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
        holder.bubbleCard = bubbleCard;

        float r = AndroidUtilities.dp(16);
        float tail = AndroidUtilities.dp(4);

        if (msg.isUser) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Theme.getColor(Theme.key_chat_outBubble));
            gd.setCornerRadii(new float[]{r, r, r, r, tail, tail, r, r});
            bubbleCard.setBackground(gd);
            bubbleRow.addView(bubbleCard, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));

            // Photo attachment preview if user attached an image
            if (msg.imagePath != null && new File(msg.imagePath).exists()) {
                ImageView photoView = new ImageView(ctx);
                photoView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photoView.setClipToOutline(true);
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(msg.imagePath);
                    if (bmp != null) photoView.setImageBitmap(bmp);
                } catch (Throwable ignore) {}
                GradientDrawable roundImg = new GradientDrawable();
                roundImg.setCornerRadius(AndroidUtilities.dp(12));
                photoView.setBackground(roundImg);
                bubbleCard.addView(photoView, LayoutHelper.createLinear(AndroidUtilities.dp(200), AndroidUtilities.dp(140), 0, 0, 0, 6));
                holder.photoView = photoView;
            }

            TextView body = new TextView(ctx);
            body.setText(msg.text);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            body.setTextColor(Theme.getColor(Theme.key_chat_messageTextOut));
            body.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            bubbleCard.addView(body, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            holder.bodyView = body;

            TextView time = new TextView(ctx);
            time.setText(timeFormat.format(new Date(msg.timestamp)));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            time.setTextColor(Theme.getColor(Theme.key_chat_outTimeText));
            time.setGravity(Gravity.RIGHT);
            bubbleCard.addView(time, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            holder.timeView = time;
        } else {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(Theme.getColor(Theme.key_chat_inBubble));
            gd.setCornerRadii(new float[]{r, r, r, r, r, r, tail, tail});
            gd.setStroke(AndroidUtilities.dp(1), isYumi ? 0x229D4EDD : 0x2200B4D8);
            bubbleCard.setBackground(gd);
            bubbleRow.addView(bubbleCard, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT));

            TextView author = new TextView(ctx);
            author.setText(isYumi ? "Юмі ໒꒱" : "Міо ☕");
            author.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            author.setTypeface(AndroidUtilities.bold());
            author.setTextColor(accentColor);
            bubbleCard.addView(author);

            TextView body = new TextView(ctx);
            body.setText(msg.text);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            body.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
            body.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            bubbleCard.addView(body, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            holder.bodyView = body;

            if (scopedDialogId != 0 || onDraftInsertCallback != null) {
                TextView insertBtn = new TextView(ctx);
                insertBtn.setText("↳ " + MiogramLocale.get("Вставити в чат", "Вставить в чат", "Insert into chat"));
                insertBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                insertBtn.setTypeface(AndroidUtilities.bold());
                insertBtn.setTextColor(accentColor);
                insertBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), isYumi ? 0x1F9D4EDD : 0x1F00B4D8));
                insertBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
                insertBtn.setOnClickListener(v -> {
                    MiogramHaptic.tap(v);
                    if (onDraftInsertCallback != null) {
                        onDraftInsertCallback.run(msg.text);
                        finishFragment();
                    }
                });
                bubbleCard.addView(insertBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
            }

            if (msg.toolAction != null && !msg.actionExecuted) {
                holder.actionCard = renderActionCard(ctx, bubbleCard, msg);
            }

            TextView time = new TextView(ctx);
            time.setText(timeFormat.format(new Date(msg.timestamp)));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            time.setTextColor(Theme.getColor(Theme.key_chat_inTimeText));
            time.setGravity(Gravity.RIGHT);
            bubbleCard.addView(time, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            holder.timeView = time;
        }

        messageViewHolderMap.put(msg, holder);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void updateMessageBubble(MiogramCompanionPrefs.ChatMessage msg) {
        MessageViewHolder holder = messageViewHolderMap.get(msg);
        if (holder == null || holder.bodyView == null) {
            renderMessageBubble(msg);
            return;
        }
        holder.bodyView.setText(msg.text);
        if (holder.timeView != null) {
            holder.timeView.setText(timeFormat.format(new Date(msg.timestamp)));
        }
        if (holder.avatarView != null && !msg.isUser) {
            holder.avatarView.setImageResource(resolveSpriteForMood(msg.mood, MiogramCompanionPrefs.isAmeActive()));
        }
        if (holder.actionCard != null) {
            holder.actionCard.setVisibility(msg.toolAction != null && !msg.actionExecuted ? View.VISIBLE : View.GONE);
        }
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void showActionBulletin(String text) {
        try {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, text).show();
        } catch (Throwable ignore) {}
    }

    private LinearLayout renderActionCard(Context ctx, LinearLayout parent, MiogramCompanionPrefs.ChatMessage msg) {
        boolean isAme = MiogramCompanionPrefs.isAmeActive();
        int accentColor = isAme ? 0xFFFF70A6 : 0xFF00B4D8;

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        gd.setCornerRadius(AndroidUtilities.dp(12));
        gd.setStroke(AndroidUtilities.dp(1.5f), 0xFFFFB703);
        card.setBackground(gd);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        parent.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 4));

        TextView title = new TextView(ctx);
        title.setText(MiogramLocale.get("Запит на виконання дії", "Запрос на выполнение действия", "Action Permission Request"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(0xFFFFB703);
        card.addView(title);

        TextView details = new TextView(ctx);
        String humanDesc = msg.toolAction;
        try {
            JSONObject dbg = null;
            if (msg.toolParams != null) dbg = new JSONObject(msg.toolParams);
            humanDesc = MiogramCompanionToolbox.describeTool(msg.toolAction, dbg);
        } catch (Throwable ignore) {}
        details.setText(humanDesc);
        details.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        details.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(details, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 8));

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(btnRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView approveBtn = new TextView(ctx);
        approveBtn.setText(MiogramLocale.get("ДОЗВОЛИТИ", "РАЗРЕШИТЬ", "ALLOW"));
        approveBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        approveBtn.setTypeface(AndroidUtilities.bold());
        approveBtn.setTextColor(0xFFFFFFFF);
        approveBtn.setGravity(Gravity.CENTER);
        approveBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), accentColor));
        approveBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(7), AndroidUtilities.dp(10), AndroidUtilities.dp(7));
        btnRow.addView(approveBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 4, 0));

        TextView denyBtn = new TextView(ctx);
        denyBtn.setText(MiogramLocale.get("ВІДХИЛИТИ", "ОТКЛОНИТЬ", "DENY"));
        denyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        denyBtn.setTypeface(AndroidUtilities.bold());
        denyBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        denyBtn.setGravity(Gravity.CENTER);
        denyBtn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(8), Theme.getColor(Theme.key_windowBackgroundGray)));
        denyBtn.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(7), AndroidUtilities.dp(10), AndroidUtilities.dp(7));
        btnRow.addView(denyBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 4, 0, 0, 0));

        approveBtn.setOnClickListener(v -> {
            MiogramHaptic.success(v);
            msg.actionApproved = true;
            msg.actionExecuted = true;
            MiogramCompanionPrefs.saveHistory(history);
            card.setVisibility(View.GONE);

            JSONObject paramsObj = null;
            try {
                if (msg.toolParams != null) paramsObj = new JSONObject(msg.toolParams);
            } catch (Throwable ignore) {}
            MiogramCompanionToolbox.ActionRequest req = new MiogramCompanionToolbox.ActionRequest(msg.toolAction, paramsObj, false);
            app.miogram.bridge.ai.tools.MioTool.exec(currentAccount, req.name, req.params, resultText -> AndroidUtilities.runOnUIThread(() -> {
                MiogramCompanionPrefs.ChatMessage resultMsg = new MiogramCompanionPrefs.ChatMessage(false, "✓ " + resultText, "happy", System.currentTimeMillis(), null, null);
                history.add(resultMsg);
                MiogramCompanionPrefs.saveHistory(history);
                renderMessageBubble(resultMsg);
                MiogramCompanionPrefs.applyStatDeltas(1, 0, 0, 500);
                updateStatsViews();
            }));
        });

        denyBtn.setOnClickListener(v -> {
            MiogramHaptic.tap(v);
            msg.actionApproved = false;
            msg.actionExecuted = true;
            MiogramCompanionPrefs.saveHistory(history);
            card.setVisibility(View.GONE);

            String reply = isAme
                    ? MiogramLocale.get(
                            "Хмпф... раз ти забороняєш, то розбирайся сам (¬_¬) Я пальцем не поворухну!",
                            "Хмпф... раз ты запрещаешь, то разбирайся сам (¬_¬) Я пальцем не пошевелю!",
                            "Hmph... if you forbid it, deal with it yourself (¬_¬) I won't lift a finger!")
                    : MiogramLocale.get(
                            "Звісно... я розумію. Скасовую дію, нічого зайвого чіпати не буду (´｡• ω •｡`)",
                            "Конечно... я понимаю. Отменяю действие, ничего лишнего трогать не буду (´｡• ω •｡`)",
                            "Of course... I understand. Canceling action, I won't touch anything (´｡• ω •｡`)");
            MiogramCompanionPrefs.ChatMessage cancelMsg = new MiogramCompanionPrefs.ChatMessage(false, reply, "sad", System.currentTimeMillis(), null, null);
            history.add(cancelMsg);
            MiogramCompanionPrefs.saveHistory(history);
            renderMessageBubble(cancelMsg);
            updateStageMood("sad");
        });
        return card;
    }

    private static final int AGENT_MAX_STEPS = 6;

    private static class AgentStepRecord {
        final String actionRaw;
        final String observation;

        AgentStepRecord(String actionRaw, String observation) {
            this.actionRaw = actionRaw;
            this.observation = observation;
        }
    }

    private void onSendMessage() {
        if (isSending) return;
        String query = inputField.getText() != null ? inputField.getText().toString().trim() : "";
        final String imagePathToSend = attachedImagePath;

        if (TextUtils.isEmpty(query) && TextUtils.isEmpty(imagePathToSend)) return;

        inputField.setText("");
        attachedImagePath = null;
        updateAttachedImageBar();

        MiogramCompanionToolbox.PickResolution pick =
                MiogramCompanionToolbox.tryResolvePendingPick(currentAccount, query);
        // System routing hints go ONLY to the model — never into the visible
        // bubble or saved history (otherwise user sees "message + prompt").
        String modelQuery = query;
        if ("RESOLVED".equals(pick.kind) && pick.foundChat != null) {
            String ref = pick.foundChat.username.isEmpty() ? pick.foundChat.name : "@" + pick.foundChat.username;
            modelQuery = query + "\n[System routing: User picked «" + pick.foundChat.name + "» (" + ref + "). "
                    + "Your next call MUST contain {\"chat_id\": " + pick.foundChat.dialogId + "}. "
                    + "Do not list again, do not ask — act on this chat.]";
        } else if ("NEXT_PAGE".equals(pick.kind)) {
            modelQuery = query + "\n[Not the right pick; show next options or keep paging the chat list.]";
        }

        MiogramCompanionPrefs.ChatMessage userMsg = new MiogramCompanionPrefs.ChatMessage(true, query, "neutral", System.currentTimeMillis(), null, null, imagePathToSend);
        history.add(userMsg);
        MiogramCompanionPrefs.saveHistory(history);
        renderMessageBubble(userMsg);

        // Dynamic Companion Stats reactivity:
        String lower = query.toLowerCase(Locale.US);
        if (lower.contains("чай") || lower.contains("tea") || lower.contains("завар") || lower.contains("чаю")) {
            MiogramCompanionPrefs.brewTea();
            updateStatsViews();
            MiogramHaptic.success(sendButton);
            showActionBulletin("☕ " + MiogramLocale.get("Заварено запашний чай: Спокій +20%, Втома -15%", "Заварен ароматный чай: Покой +20%, Усталость -15%", "Brewed hot tea: Calm +20%, Fatigue -15%"));
        } else if (lower.contains("дюск") || lower.contains("dysk") || lower.contains("пігулк") || lower.contains("пилюл") || lower.contains("таблетк")) {
            MiogramCompanionPrefs.takeMagicPill();
            updateStatsViews();
            MiogramHaptic.success(sendButton);
            showActionBulletin("☕ " + MiogramLocale.get("Заварено гарячий чай: Спокій +20%, Втома -15%", "Заварен горячий чай: Покой +20%, Усталость -15%", "Brewed hot tea: Calm +20%, Fatigue -15%"));
        } else if (lower.contains("молодець") || lower.contains("топ") || lower.contains("красуня") || lower.contains("люблю") || lower.contains("найкраща") || lower.contains("дякую") || lower.contains("мило")) {
            MiogramCompanionPrefs.applyStatDeltas(4, -8, 0, 150);
            updateStatsViews();
        } else if (lower.contains("дістала") || lower.contains("набридла") || lower.contains("відчепись") || lower.contains("тупа") || lower.contains("кринж")) {
            MiogramCompanionPrefs.applyStatDeltas(-10, 15, 10, 0);
            updateStatsViews();
        }

        isSending = true;
        if (sendIcon != null) sendIcon.setVisibility(View.GONE);
        if (sendProgress != null) sendProgress.setVisibility(View.VISIBLE);
        if (sendButton != null) sendButton.setAlpha(0.6f);

        List<AgentStepRecord> steps = new ArrayList<>();
        executeAgentStep(1, query, modelQuery, steps, null, imagePathToSend);
    }

    private String buildPromptForTurn(String displayQuery, String modelQuery, List<AgentStepRecord> steps, boolean forceFinal) {
        TLRPC.User currentUser = UserConfig.getInstance(currentAccount).getCurrentUser();
        String userName = currentUser != null ? UserObject.getUserName(currentUser) : "User";
        String systemPrompt = MiogramCompanionPersona.getSystemPrompt(MiogramCompanionPrefs.getActiveCompanion(), userName, scopedDialogId, currentAccount);

        StringBuilder sb = new StringBuilder();
        sb.append(systemPrompt).append("\n\n### CONVERSATION HISTORY:\n");
        int start = Math.max(0, history.size() - 24);
        for (int i = start; i < history.size(); i++) {
            MiogramCompanionPrefs.ChatMessage m = history.get(i);
            if (i == history.size() - 1 && m.isUser && m.text.equals(displayQuery)) continue;
            sb.append(m.isUser ? "User: " : "Companion: ");
            if (m.imagePath != null) {
                sb.append("[Photo attached: ").append(new File(m.imagePath).getName()).append("] ");
            }
            sb.append(m.text).append("\n");
        }

        sb.append("\n### CURRENT TURN:\n");
        sb.append("User: ").append(modelQuery).append("\n");

        if (steps != null && !steps.isEmpty()) {
            for (AgentStepRecord s : steps) {
                sb.append("Companion: ").append(s.actionRaw).append("\n");
                sb.append("[OBSERVATION: ").append(s.observation).append("]\n");
            }
            if (forceFinal) {
                sb.append("System: tools done — write the final in-character reply now ([MOOD: ...] first). No more tool calls.\n");
            } else {
                sb.append("System: observation above. Reply with the next [ACTION: ...] or the final in-character reply ([MOOD: ...] first).\n");
            }
        }
        sb.append("Companion:");
        return sb.toString();
    }

    private void executeAgentStep(final int stepIndex, final String displayQuery, final String modelQuery,
                                  final List<AgentStepRecord> steps,
                                  final MiogramCompanionPrefs.ChatMessage existingBubble,
                                  final String imagePath) {
        if (stepIndex > AGENT_MAX_STEPS) {
            executeFinalSynthesis(displayQuery, modelQuery, steps, existingBubble);
            return;
        }

        String prompt = buildPromptForTurn(displayQuery, modelQuery, steps, false);
        Utilities.Callback2<String, String> aiCallback = (rawReply, err) -> AndroidUtilities.runOnUIThread(() -> {
            if (err != null && (rawReply == null || rawReply.isEmpty())) {
                finishAgentTurn();
                if (existingBubble == null) {
                    String errorNotice = MiogramLocale.get("Ой... сталася помилка з'єднання: ", "Ой... возникла ошибка соединения: ", "Oops... connection error: ") + err
                            + MiogramLocale.get("\n\nНатисни «Надіслати» ще раз щоб повторити.", "\n\nНажми «Отправить» ещё раз чтобы повторить.", "\n\nTap Send again to retry.");
                    MiogramCompanionPrefs.ChatMessage errBubble = new MiogramCompanionPrefs.ChatMessage(false, errorNotice, "sad", System.currentTimeMillis(), null, null);
                    history.add(errBubble);
                    MiogramCompanionPrefs.saveHistory(history);
                    renderMessageBubble(errBubble);
                    updateStageMood("sad");
                }
                return;
            }

            String mood = MiogramCompanionToolbox.extractMoodTag(rawReply);
            MiogramCompanionToolbox.ActionRequest action = MiogramCompanionToolbox.parseAction(rawReply);
            String cleanText = MiogramCompanionToolbox.stripActionBlock(MiogramCompanionToolbox.stripMoodTag(rawReply));

            if (action != null) {
                if (action.sensitive) {
                    String actionName = action.name;
                    String actionParams = action.params != null ? action.params.toString() : null;
                    MiogramCompanionPrefs.ChatMessage cardMsg = new MiogramCompanionPrefs.ChatMessage(false, cleanText, mood, System.currentTimeMillis(), actionName, actionParams);
                    history.add(cardMsg);
                    MiogramCompanionPrefs.saveHistory(history);
                    renderMessageBubble(cardMsg);
                    updateStageMood(mood);
                    finishAgentTurn();
                    return;
                }

                MiogramCompanionPrefs.ChatMessage bubble = existingBubble;
                String toolDesc = MiogramCompanionToolbox.describeTool(action.name, action.params);
                String workingText = "(っ˘ω˘ς ) " + toolDesc;
                String actionParamsStr = action.params != null ? action.params.toString() : null;
                if (bubble == null) {
                    bubble = new MiogramCompanionPrefs.ChatMessage(false, workingText, mood, System.currentTimeMillis(), action.name, actionParamsStr);
                    history.add(bubble);
                    renderMessageBubble(bubble);
                } else {
                    bubble.text = workingText;
                    updateMessageBubble(bubble);
                }
                updateStageMood(mood);
                final MiogramCompanionPrefs.ChatMessage activeBubble = bubble;
                final String actionRawStr = "[ACTION: " + action.name + " | " + (action.params != null ? action.params.toString() : "{}") + "]";

                app.miogram.bridge.ai.tools.MioTool.exec(currentAccount, action.name, action.params, resultText -> AndroidUtilities.runOnUIThread(() -> {
                    steps.add(new AgentStepRecord(actionRawStr, resultText != null ? resultText : ""));
                    if (agentResultIsError(resultText)) {
                        updateStageMood("sad");
                    } else {
                        MiogramCompanionPrefs.applyStatDeltas(1, 0, 0, 400 + (long)(Math.random() * 800));
                        updateStatsViews();
                    }
                    executeAgentStep(stepIndex + 1, displayQuery, modelQuery, steps, activeBubble, null);
                }));
            } else {
                // Final response reached
                MiogramCompanionPrefs.ChatMessage bubble = existingBubble;
                if (bubble == null) {
                    bubble = new MiogramCompanionPrefs.ChatMessage(false, cleanText, mood, System.currentTimeMillis(), null, null);
                    history.add(bubble);
                    renderMessageBubble(bubble);
                } else {
                    bubble.text = cleanText;
                    bubble.mood = mood;
                    bubble.actionExecuted = true;
                    updateMessageBubble(bubble);
                }
                MiogramCompanionPrefs.saveHistory(history);
                updateStageMood(mood);
                finishAgentTurn();
            }
        });

        File imgFile = (stepIndex == 1 && imagePath != null) ? new File(imagePath) : null;
        if (imgFile != null && imgFile.exists()) {
            MiogramAiService.generateTextWithImage(prompt, imgFile, "image/jpeg", aiCallback);
        } else {
            MiogramAiService.generateText(prompt, aiCallback);
        }
    }

    private void executeFinalSynthesis(final String displayQuery, final String modelQuery, final List<AgentStepRecord> steps,
                                       final MiogramCompanionPrefs.ChatMessage existingBubble) {
        String prompt = buildPromptForTurn(displayQuery, modelQuery, steps, true);
        MiogramAiService.generateText(prompt, (rawReply, err) -> AndroidUtilities.runOnUIThread(() -> {
            finishAgentTurn();
            if (err != null && (rawReply == null || rawReply.isEmpty())) {
                return;
            }
            String mood = MiogramCompanionToolbox.extractMoodTag(rawReply);
            String cleanText = MiogramCompanionToolbox.stripActionBlock(MiogramCompanionToolbox.stripMoodTag(rawReply));
            if (existingBubble != null) {
                existingBubble.text = cleanText;
                existingBubble.mood = mood;
                existingBubble.actionExecuted = true;
                updateMessageBubble(existingBubble);
            } else {
                MiogramCompanionPrefs.ChatMessage bubble = new MiogramCompanionPrefs.ChatMessage(false, cleanText, mood, System.currentTimeMillis(), null, null);
                history.add(bubble);
                renderMessageBubble(bubble);
            }
            MiogramCompanionPrefs.saveHistory(history);
            updateStageMood(mood);
        }));
    }

    private void finishAgentTurn() {
        isSending = false;
        if (sendIcon != null) sendIcon.setVisibility(View.VISIBLE);
        if (sendProgress != null) sendProgress.setVisibility(View.GONE);
        if (sendButton != null) sendButton.setAlpha(1.0f);
    }

    private static boolean agentResultIsError(String resultText) {
        if (resultText == null) return true;
        String rt = resultText.toLowerCase();
        return rt.contains("не вдалося") || rt.contains("не знайшла") || rt.contains("помилка")
                || rt.contains("не удалось") || rt.contains("не нашла") || rt.contains("ошибка")
                || rt.contains("failed") || rt.contains("error") || rt.contains("could not")
                || rt.contains("denied");
    }

    private void toggleConsole() {
        consoleOpen = !consoleOpen;
        if (consolePanel != null) {
            consolePanel.setVisibility(consoleOpen ? View.VISIBLE : View.GONE);
        }
        if (consoleOpen) {
            backfillConsole();
        }
    }

    private void backfillConsole() {
        if (consoleLog == null) return;
        try {
            consoleLog.removeAllViews();
            java.util.List<app.miogram.bridge.ai.tools.MioTool.ConsoleLine> lines =
                    app.miogram.bridge.ai.tools.MioTool.snapshot();
            for (app.miogram.bridge.ai.tools.MioTool.ConsoleLine line : lines) {
                addConsoleRow(line);
            }
            scrollConsoleToEnd();
        } catch (Throwable ignore) {}
    }

    private void appendConsoleLine(app.miogram.bridge.ai.tools.MioTool.ConsoleLine line) {
        if (line == null || consoleLog == null) return;
        try {
            addConsoleRow(line);
            while (consoleLog.getChildCount() > 200) {
                consoleLog.removeViewAt(0);
            }
            if (consoleOpen) scrollConsoleToEnd();
        } catch (Throwable ignore) {}
    }

    private void addConsoleRow(app.miogram.bridge.ai.tools.MioTool.ConsoleLine line) {
        try {
            Context context = getParentActivity() != null ? getParentActivity() : getContext();
            if (context == null || consoleLog == null) return;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, AndroidUtilities.dp(1), 0, AndroidUtilities.dp(1));

            TextView stamp = new TextView(context);
            stamp.setText(line.stamp() + " ");
            stamp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            stamp.setTypeface(android.graphics.Typeface.MONOSPACE);
            stamp.setTextColor(0xFF6E7681);
            row.addView(stamp, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

            TextView badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f);
            badge.setTypeface(android.graphics.Typeface.MONOSPACE);
            badge.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(1), AndroidUtilities.dp(4), AndroidUtilities.dp(1));

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setCornerRadius(AndroidUtilities.dp(4));

            String phaseTag = " " + line.phase.toUpperCase(Locale.US) + " ";
            int color = 0xFF8B949E;
            int bgCol = 0x208B949E;
            if ("ok".equals(line.phase)) {
                phaseTag = " OK ";
                color = 0xFF3FB950;
                bgCol = 0x2A3FB950;
            } else if ("error".equals(line.phase)) {
                phaseTag = " FAIL ";
                color = 0xFFF85149;
                bgCol = 0x2AF85149;
            } else if ("denied".equals(line.phase) || "paused".equals(line.phase)) {
                phaseTag = " WAIT ";
                color = 0xFFD29922;
                bgCol = 0x2AD29922;
            } else if ("started".equals(line.phase)) {
                phaseTag = " RUN ";
                color = 0xFF58A6FF;
                bgCol = 0x2A58A6FF;
            }
            badge.setText(phaseTag);
            badge.setTextColor(color);
            badgeBg.setColor(bgCol);
            badge.setBackground(badgeBg);
            row.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

            TextView body = new TextView(context);
            body.setText(line.tool + (line.preview != null && !line.preview.isEmpty() ? " ➔ " + line.preview : ""));
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            body.setTypeface(android.graphics.Typeface.MONOSPACE);
            body.setTextColor(0xFFC9D1D9);
            row.addView(body, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            consoleLog.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 2));
        } catch (Throwable ignore) {}
    }

    private void scrollConsoleToEnd() {
        try {
            if (consoleScroll != null) {
                consoleScroll.post(() -> {
                    try {
                        consoleScroll.fullScroll(View.FOCUS_DOWN);
                    } catch (Throwable ignore) {}
                });
            }
        } catch (Throwable ignore) {}
    }

    private Drawable createCardDrawable() {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        gd.setCornerRadius(AndroidUtilities.dp(16));
        gd.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        return gd;
    }

    private Drawable createTabDrawable(boolean active, int accentColor) {
        GradientDrawable gd = new GradientDrawable();
        if (active) {
            int tint = Color.argb(0x1F, Color.red(accentColor), Color.green(accentColor), Color.blue(accentColor));
            gd.setColor(tint);
            gd.setStroke(AndroidUtilities.dp(1.5f), accentColor);
        } else {
            gd.setColor(Color.TRANSPARENT);
            gd.setStroke(AndroidUtilities.dp(1), Theme.getColor(Theme.key_divider));
        }
        gd.setCornerRadius(AndroidUtilities.dp(12));
        return gd;
    }
}
