package app.miogram.bridge.badge;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.MiogramLocale;

/**
 * Telegram-Native Miogram Badge Sheet.
 * Чистий перегляд відзнаки спільноти у стилі Material Design 3 / Telegram.
 * Відзнаки видаються виключно засновником спільноти Miyumi (@fuckramochka).
 */
public class MiogramBadgeBottomSheet extends BottomSheet {

    private final long targetUserId;
    private final boolean isSelf;

    public static MiogramBadgeBottomSheet show(Context context, long userId) {
        return show(context, userId, null);
    }

    public static MiogramBadgeBottomSheet show(Context context, long userId, MiogramBadgeType initialBadge) {
        MiogramBadgeBottomSheet sheet = new MiogramBadgeBottomSheet(context, userId, initialBadge);
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
        init(fragment.getParentActivity());
    }

    public MiogramBadgeBottomSheet(Context context, long userId) {
        this(context, userId, null);
    }

    public MiogramBadgeBottomSheet(Context context, long userId, MiogramBadgeType initialBadge) {
        super(context, false);
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId <= 0) {
            userId = currentUserId;
        }
        this.targetUserId = userId;
        this.isSelf = (userId == currentUserId || userId == 0);
        init(context);
    }

    private void init(Context inContext) {
        final Context context = inContext != null ? inContext : ApplicationLoader.applicationContext;

        setApplyBottomPadding(false);
        setApplyTopPadding(false);
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        final Context finalContext = context;
        final long clientUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        final boolean isFounder = (targetUserId == MiogramBadgeManager.FOUNDER_USER_ID);
        final boolean canManage = (clientUserId == MiogramBadgeManager.FOUNDER_USER_ID);

        MiogramSupabaseBridge.BadgeRecord record = MiogramBadgeManager.getBadgeRecord(targetUserId);
        if (record == null && isFounder) {
            record = new MiogramSupabaseBridge.BadgeRecord(
                    targetUserId,
                    MiogramBadgeType.DEVIL,
                    MiogramLocale.get("Засновник & Архітектор Miyumi ໒꒱", "Создатель & Архитектор Miyumi ໒꒱", "Miyumi Founder & Architect ໒꒱"),
                    MiogramLocale.get("Особиста відзнака засновника та головного архітектора спільноти Miyumi (@fuckramochka).",
                            "Личное отличие создателя и главного архитектора сообщества Miyumi (@fuckramochka).",
                            "Personal distinction of the Founder & Chief Architect of Miyumi (@fuckramochka)."),
                    "01.09.2026",
                    true,
                    true,
                    MiogramBadgeManager.FOUNDER_USER_ID
            );
        }

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
        root.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16));

        // 2. Стан, коли у користувача немає бейджа
        if (record == null) {
            FrameLayout emptyIconBox = new FrameLayout(context);
            GradientDrawable emptyBoxBg = new GradientDrawable();
            emptyBoxBg.setShape(GradientDrawable.RECTANGLE);
            emptyBoxBg.setCornerRadius(AndroidUtilities.dp(20));
            emptyBoxBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            emptyIconBox.setBackground(emptyBoxBg);
            emptyIconBox.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(18));

            ImageView emptyIcon = new ImageView(context);
            emptyIcon.setImageResource(R.drawable.msg_settings_premium);
            emptyIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
            emptyIconBox.addView(emptyIcon, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
            root.addView(emptyIconBox, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

            TextView emptyTitle = new TextView(context);
            emptyTitle.setText(MiogramLocale.get("Відзнака відсутня", "Отличие отсутствует", "No Badge"));
            emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
            emptyTitle.setTypeface(AndroidUtilities.bold());
            emptyTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
            emptyTitle.setGravity(Gravity.CENTER);
            root.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            TextView emptyDesc = new TextView(context);
            emptyDesc.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            emptyDesc.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
            emptyDesc.setGravity(Gravity.CENTER);
            emptyDesc.setLineSpacing(AndroidUtilities.dp(2), 1f);
            emptyDesc.setText(MiogramLocale.get(
                    "У вас наразі немає активної відзнаки Miyumi. Відзнаки видаються виключно засновником спільноти (@fuckramochka) за особливий внесок у розвиток клієнта.",
                    "У вас пока нет активного отличия Miyumi. Отличия выдаются исключительно создателем сообщества (@fuckramochka) за особый вклад в развитие клиента.",
                    "You currently do not have an active Miyumi badge. Badges are granted exclusively by the community founder (@fuckramochka) for contributions to client development."));
            root.addView(emptyDesc, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

            if (canManage) {
                TextView grantBtn = createPrimaryButton(context, MiogramLocale.get("Видати бейдж користувачу", "Выдать бейдж пользователю", "Grant Badge to User"));
                grantBtn.setOnClickListener(v -> {
                    if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                    dismiss();
                    MiogramBadgeGrantSheet.show(finalContext, targetUserId);
                });
                root.addView(grantBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
            }

            TextView okButton = createSecondaryButton(context, MiogramLocale.get("Зрозуміло", "Понятно", "Got It"));
            okButton.setOnClickListener(v -> {
                if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dismiss();
            });
            root.addView(okButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            scrollView.addView(root);
            setCustomView(scrollView);
            return;
        }

        // 3. Стан, коли бейдж є — красивий чистий перегляд
        FrameLayout previewContainer = new FrameLayout(context);
        GradientDrawable previewBg = new GradientDrawable();
        previewBg.setShape(GradientDrawable.RECTANGLE);
        previewBg.setCornerRadius(AndroidUtilities.dp(20));
        previewBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        previewContainer.setBackground(previewBg);
        previewContainer.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(16), AndroidUtilities.dp(22), AndroidUtilities.dp(16));

        ImageView badgePreviewView = new ImageView(context);
        badgePreviewView.setImageDrawable(new MiogramArrowDrawable(76, record.badgeType));
        previewContainer.addView(badgePreviewView, LayoutHelper.createFrame(96, 76, Gravity.CENTER));
        root.addView(previewContainer, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        TextView badgeNameView = new TextView(context);
        badgeNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        badgeNameView.setTypeface(AndroidUtilities.bold());
        badgeNameView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        badgeNameView.setGravity(Gravity.CENTER);
        badgeNameView.setText(!TextUtils.isEmpty(record.title) ? record.title : record.badgeType.getTitle());
        root.addView(badgeNameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView badgeSubView = new TextView(context);
        badgeSubView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        badgeSubView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2, resourcesProvider));
        badgeSubView.setGravity(Gravity.CENTER);
        badgeSubView.setText(record.badgeType.getTitle() + " • " + MiogramLocale.get("Офіційна відзнака", "Официальное отличие", "Official Distinction"));
        root.addView(badgeSubView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 4. Обґрунтування надання: ЯКЩО НЕМАЄ — ТОДІ ПУСТО (жодного філер-тексту)
        String reason = record.obtainedReason != null ? record.obtainedReason.trim() : "";
        if (!reason.isEmpty()) {
            LinearLayout reasonCard = new LinearLayout(context);
            reasonCard.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable reasonBg = new GradientDrawable();
            reasonBg.setShape(GradientDrawable.RECTANGLE);
            reasonBg.setCornerRadius(AndroidUtilities.dp(14));
            reasonBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
            reasonCard.setBackground(reasonBg);
            reasonCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

            TextView reasonTitle = new TextView(context);
            reasonTitle.setText(MiogramLocale.get("Обґрунтування надання відзнаки", "Обоснование выдачи отличия", "Award Citation & Reason"));
            reasonTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
            reasonTitle.setTypeface(AndroidUtilities.bold());
            reasonTitle.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
            reasonCard.addView(reasonTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

            TextView reasonBody = new TextView(context);
            reasonBody.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            reasonBody.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            reasonBody.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
            reasonBody.setText(reason);
            reasonCard.addView(reasonBody, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            root.addView(reasonCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        }

        // 5. Метадані відзнаки (Дата та верифікація)
        LinearLayout metaCard = new LinearLayout(context);
        metaCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable metaBg = new GradientDrawable();
        metaBg.setShape(GradientDrawable.RECTANGLE);
        metaBg.setCornerRadius(AndroidUtilities.dp(14));
        metaBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        metaCard.setBackground(metaBg);
        metaCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));

        String obtainDate = !TextUtils.isEmpty(record.obtainedAt) ? record.obtainedAt : null;
        if (obtainDate != null) {
            if (obtainDate.length() > 10) obtainDate = obtainDate.substring(0, 10);
            metaCard.addView(createTgMetaRow(context,
                    MiogramLocale.get("Дата надання:", "Дата выдачи:", "Date Granted:"),
                    obtainDate));
        }
        metaCard.addView(createTgMetaRow(context,
                MiogramLocale.get("Статус:", "Статус:", "Status:"),
                MiogramLocale.get("✓ Верифіковано спільнотою Miyumi ໒꒱", "✓ Верифицировано сообществом Miyumi ໒꒱", "✓ Verified by Miyumi ໒꒱")));
        metaCard.addView(createTgMetaRow(context,
                MiogramLocale.get("Стиль відзнаки:", "Стиль отличия:", "Badge Style:"),
                record.badgeType.getTitle()));

        root.addView(metaCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 6. Кнопки дій: тільки засновник може керувати бейджем
        if (canManage) {
            TextView manageBtn = createPrimaryButton(context, MiogramLocale.get("Керувати цим бейджем", "Управлять этим бейджем", "Manage This Badge"));
            manageBtn.setOnClickListener(v -> {
                if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                dismiss();
                MiogramBadgeGrantSheet.show(finalContext, targetUserId);
            });
            root.addView(manageBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        }

        TextView okButton = createSecondaryButton(context, MiogramLocale.get("Зрозуміло", "Понятно", "Got It"));
        okButton.setOnClickListener(v -> {
            if (app.miogram.bridge.customui.MiogramHaptic.isEnabled()) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            dismiss();
        });
        root.addView(okButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        scrollView.addView(root);
        setCustomView(scrollView);
    }

    private TextView createPrimaryButton(Context context, String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider));
        btn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        btnBg.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        btn.setBackground(btnBg);
        btn.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
        return btn;
    }

    private TextView createSecondaryButton(Context context, String text) {
        TextView btn = new TextView(context);
        btn.setText(text);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        btn.setTypeface(AndroidUtilities.bold());
        btn.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        btn.setGravity(Gravity.CENTER);
        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setShape(GradientDrawable.RECTANGLE);
        btnBg.setCornerRadius(AndroidUtilities.dp(10));
        btnBg.setColor(Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider));
        btn.setBackground(btnBg);
        btn.setPadding(0, AndroidUtilities.dp(13), 0, AndroidUtilities.dp(13));
        return btn;
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
}
