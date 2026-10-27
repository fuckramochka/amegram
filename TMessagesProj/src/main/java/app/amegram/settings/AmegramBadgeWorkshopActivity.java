package app.amegram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ScaleStateListAnimator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import app.amegram.theme.YumiMd3;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.badge.MiogramBadgeManager;
import app.miogram.bridge.badge.MiogramSupabaseBridge;

/**
 * Майстерня бейджиків (Badge Workshop / Студія бейджиків):
 * - Підтримка векторних SVG та растрових 128x128 PNG/WebP бейджів
 * - Інтерактивний живий перегляд бейджиків у профілі
 * - Центровані готові шаблони без зсувів у кути
 * - Прямий експорт у файл, копіювання коду та зручна відправка без конфліктних вікон
 */
public class AmegramBadgeWorkshopActivity extends BaseFragment {

    private static final int REQUEST_PICK_SVG = 1001;

    private static final String DEFAULT_STAR_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">"
                    + "<path d=\"M12 2 L14.5 9.5 L22 12 L14.5 14.5 L12 22 L9.5 14.5 L2 12 L9.5 9.5 Z\" fill=\"#C084FC\"/>"
                    + "<circle cx=\"12\" cy=\"12\" r=\"2.5\" fill=\"#FFFFFF\"/>"
                    + "</svg>";

    private static final String DEFAULT_HEART_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">"
                    + "<path d=\"M12 21 C12 21 3 14 3 8.5 C3 5.5 5.5 3 8.5 3 C10.5 3 11.5 4 12 5 C12.5 4 13.5 3 15.5 3 C18.5 3 21 5.5 21 8.5 C21 14 12 21 12 21 Z\" fill=\"#FB7185\"/>"
                    + "<circle cx=\"8.5\" cy=\"7.5\" r=\"1.5\" fill=\"#FFFFFF\" opacity=\"0.8\"/>"
                    + "</svg>";

    private static final String DEFAULT_WING_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">"
                    + "<path d=\"M22 4 C18 6 12 8 6 14 C4 16 3 18 5 19 C7 20 10 19 13 17 C11 19 10 21 12 22 C14 23 18 20 20 16 C22 12 23 7 22 4 Z\" fill=\"#38BDF8\"/>"
                    + "<path d=\"M20 7 C16 9 12 11 8 16\" stroke=\"#FFFFFF\" stroke-width=\"1.2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "</svg>";

    private static final String DEFAULT_CROSS_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">"
                    + "<path d=\"M10 2 H14 V7 H19 V11 H14 V22 H10 V11 H5 V7 H10 Z\" fill=\"#E2E8F0\" stroke=\"#8B5CF6\" stroke-width=\"1.2\"/>"
                    + "<circle cx=\"12\" cy=\"9\" r=\"1.5\" fill=\"#8B5CF6\"/>"
                    + "</svg>";

    private static final String DEFAULT_DIAMOND_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\">"
                    + "<path d=\"M6 3 L18 3 L22 9 L12 21 L2 9 Z\" fill=\"#A855F7\"/>"
                    + "<path d=\"M6 3 L12 9 L18 3 M2 9 L22 9 M12 9 L12 21 M6 9 L12 21 M18 9 L12 21\" stroke=\"#FFFFFF\" stroke-width=\"1\" fill=\"none\" opacity=\"0.7\"/>"
                    + "</svg>";

    private String currentSvgContent = DEFAULT_STAR_SVG;
    private Bitmap currentBadgeBitmap;
    private byte[] currentRawBytes;
    private boolean isRasterFormat = false;

    private ImageView badgePreviewSmall;
    private ImageView badgePreviewLarge;
    private TextView badgeStatusLabel;
    private EditText badgeNameInput;
    private EditText badgeDescInput;

    private String selectedColorHex = "#C084FC";

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setTitle(MiogramLocale.get("Майстерня бейджиків", "Мастерская бейджиков", "Badge Workshop"));
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(24));
        scrollView.addView(root, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        // 1. Hero Header
        root.addView(createHeroCard(context));

        // 2. Live Profile Mockup Preview
        root.addView(createProfilePreviewCard(context));

        // 3. Zoomed Badge Inspector & File Controls
        root.addView(createUploadControlCard(context));

        // 4. Badge Details & Color Input
        root.addView(createMetadataCard(context));

        // 5. Actions Card (Copy Code, Save File, Share, Apply)
        root.addView(createSubmitCard(context));

        renderSvgPreview(currentSvgContent);

        fragmentView = scrollView;
        return fragmentView;
    }

    private View createHeroCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Студія бейджиків Yumigram", "Студия бейджиков Yumigram", "Yumigram Badge Studio"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(title);

        TextView sub = new TextView(context);
        sub.setText(MiogramLocale.get(
                "Створюйте власні векторні SVG або растрові (128x128 PNG) бейджики. Переглядайте результат поруч із нікнеймом, копіюйте код та експортуйте файл.",
                "Создавайте свои векторные SVG или растровые (128x128 PNG) бейджики. Просматривайте результат рядом с никнеймом, копируйте код и экспортируйте файл.",
                "Design custom vector SVG or raster (128x128 PNG) badges. Preview them next to your name, copy code and export files."));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        sub.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        return card;
    }

    private View createProfilePreviewCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView header = new TextView(context);
        header.setText(MiogramLocale.get("Живий перегляд у профілі", "Живой просмотр в профиле", "Live profile preview"));
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTypeface(AndroidUtilities.bold());
        header.setTextColor(YumiMd3.primary());
        card.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Simulated Profile Row
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        rowBg.setCornerRadius(AndroidUtilities.dp(14));
        row.setBackground(rowBg);

        TLRPC.User self = getUserConfig().getCurrentUser();
        BackupImageView avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(24));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (self != null) {
            avatarDrawable.setInfo(self);
            avatarView.setForUserOrChat(self, avatarDrawable);
        }
        row.addView(avatarView, LayoutHelper.createLinear(48, 48, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout col = new LinearLayout(context);
        col.setOrientation(LinearLayout.VERTICAL);

        LinearLayout nameRow = new LinearLayout(context);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        nameView.setText(self != null ? UserObject.getUserName(self) : "Yumigram User");
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        nameRow.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 6, 0));

        badgePreviewSmall = new ImageView(context);
        badgePreviewSmall.setScaleType(ImageView.ScaleType.FIT_CENTER);
        nameRow.addView(badgePreviewSmall, LayoutHelper.createLinear(22, 22, Gravity.CENTER_VERTICAL));
        col.addView(nameRow);

        TextView subView = new TextView(context);
        String username = self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : "ID: " + (self != null ? self.id : "0");
        subView.setText(username + " • " + MiogramLocale.get("онлайн", "в сети", "online"));
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        col.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(col, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(row);

        return card;
    }

    private View createUploadControlCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        // Zoomed Inspector Box
        FrameLayout box = new FrameLayout(context);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        boxBg.setCornerRadius(AndroidUtilities.dp(16));
        boxBg.setStroke(AndroidUtilities.dp(1), YumiMd3.outlineVariant());
        box.setBackground(boxBg);

        badgePreviewLarge = new ImageView(context);
        badgePreviewLarge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        box.addView(badgePreviewLarge, LayoutHelper.createFrame(48, 48, Gravity.CENTER));
        top.addView(box, LayoutHelper.createLinear(68, 68, Gravity.CENTER_VERTICAL, 0, 0, 14, 0));

        LinearLayout infoCol = new LinearLayout(context);
        infoCol.setOrientation(LinearLayout.VERTICAL);

        TextView infoTitle = new TextView(context);
        infoTitle.setText(MiogramLocale.get("Формат бейджика", "Формат бейджика", "Badge Format"));
        infoTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTitle.setTypeface(AndroidUtilities.bold());
        infoTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        infoCol.addView(infoTitle);

        badgeStatusLabel = new TextView(context);
        badgeStatusLabel.setText(MiogramLocale.get("Підтримуються: SVG або PNG (128x128)", "Поддерживаются: SVG или PNG (128x128)", "Supported: SVG or PNG (128x128)"));
        badgeStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        badgeStatusLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoCol.addView(badgeStatusLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        top.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(top);

        // Buttons
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        TextView uploadBtn = createButton(context, MiogramLocale.get("Завантажити файл", "Загрузить файл", "Upload File"), true, this::openFilePicker);
        btnRow.addView(uploadBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 8, 0));

        TextView pasteBtn = createButton(context, MiogramLocale.get("Вставити код", "Вставить код", "Paste Code"), false, this::showPasteCodeDialog);
        btnRow.addView(pasteBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 0, 0));

        card.addView(btnRow);

        // Template chips
        TextView tplTitle = new TextView(context);
        tplTitle.setText(MiogramLocale.get("Готові шаблони:", "Готовые шаблоны:", "Preset templates:"));
        tplTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tplTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(tplTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 6));

        HorizontalScrollView hScroll = new HorizontalScrollView(context);
        hScroll.setHorizontalScrollBarEnabled(false);

        LinearLayout tplRow = new LinearLayout(context);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);

        tplRow.addView(createTemplateChip(context, "✦ Зірка", DEFAULT_STAR_SVG));
        tplRow.addView(createTemplateChip(context, "♥ Серце", DEFAULT_HEART_SVG));
        tplRow.addView(createTemplateChip(context, "🪽 Крило", DEFAULT_WING_SVG));
        tplRow.addView(createTemplateChip(context, "✝ Хрест", DEFAULT_CROSS_SVG));
        tplRow.addView(createTemplateChip(context, "💎 Кристал", DEFAULT_DIAMOND_SVG));

        hScroll.addView(tplRow);
        card.addView(hScroll);

        return card;
    }

    private View createTemplateChip(Context context, String label, String svg) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setStroke(AndroidUtilities.dp(1), YumiMd3.outlineVariant());
        chip.setBackground(bg);

        chip.setOnClickListener(v -> {
            chip.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            setSvgContent(svg);
        });
        ScaleStateListAnimator.apply(chip, 0.05f, 1.4f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, AndroidUtilities.dp(8), 0);
        chip.setLayoutParams(lp);

        return chip;
    }

    private View createMetadataCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Інформація про бейдж", "Информация о бейдже", "Badge Details"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        badgeNameInput = createInputField(context, MiogramLocale.get("Назва бейджика (напр. Cyber Wing)", "Название бейджика (напр. Cyber Wing)", "Badge name (e.g. Cyber Wing)"));
        card.addView(badgeNameInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 10));

        badgeDescInput = createInputField(context, MiogramLocale.get("Опис або коментар (необов'язково)", "Описание или комментарий (необязательно)", "Description or comment (optional)"));
        card.addView(badgeDescInput, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 12));

        // Color selector
        TextView colorLabel = new TextView(context);
        colorLabel.setText(MiogramLocale.get("Бажаний колір акценту:", "Желаемый цвет акцента:", "Desired accent color:"));
        colorLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        colorLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(colorLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        LinearLayout colorRow = new LinearLayout(context);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);

        String[] hexes = {"#C084FC", "#FB7185", "#38BDF8", "#FBBF24", "#34D399", "#A78BFA"};
        for (String hex : hexes) {
            colorRow.addView(createColorCircle(context, hex));
        }
        card.addView(colorRow);

        return card;
    }

    private View createColorCircle(Context context, String hex) {
        View circle = new View(context);
        int color = android.graphics.Color.parseColor(hex);
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.OVAL);
        gd.setColor(color);
        circle.setBackground(gd);

        circle.setOnClickListener(v -> {
            circle.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            selectedColorHex = hex;
            if (badgeStatusLabel != null) {
                badgeStatusLabel.setText(MiogramLocale.get("Акцент: " + hex, "Акцент: " + hex, "Accent: " + hex));
            }
        });
        ScaleStateListAnimator.apply(circle, 0.1f, 1.4f);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
        lp.setMargins(0, 0, AndroidUtilities.dp(10), 0);
        circle.setLayoutParams(lp);

        return circle;
    }

    private EditText createInputField(Context context, String hint) {
        EditText et = new EditText(context);
        et.setHint(hint);
        et.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        et.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        et.setSingleLine(true);
        et.setPadding(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), 0);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setStroke(AndroidUtilities.dp(1), YumiMd3.outlineVariant());
        et.setBackground(bg);

        return et;
    }

    private View createSubmitCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));
        card.setBackground(YumiTheme.cardBackground(YumiTheme.RADIUS_LG));
        card.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView sectionTitle = new TextView(context);
        sectionTitle.setText(MiogramLocale.get("Дії з бейджиком", "Действия с бейджиком", "Badge Actions"));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        sectionTitle.setTypeface(AndroidUtilities.bold());
        sectionTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        card.addView(sectionTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        // 1. Primary Action: Copy Code
        TextView copyBtn = createActionButton(context,
                MiogramLocale.get("📋 Копіювати код бейджа", "📋 Копировать код бейджа", "📋 Copy Badge Code"),
                true, this::copyBadgeCode);
        card.addView(copyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));

        // 2. Action: Export as File
        TextView exportBtn = createActionButton(context,
                MiogramLocale.get("💾 Експортувати як файл", "💾 Экспортировать как файл", "💾 Export as File"),
                false, this::exportBadgeFile);
        card.addView(exportBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));

        // 3. Action: Share / Submit to Telegram
        TextView shareBtn = createActionButton(context,
                MiogramLocale.get("✈️ Надіслати у діалог / модераторам", "✈️ Отправить в диалог / модераторам", "✈️ Send to Chat / Moderators"),
                false, this::shareBadgeDirectly);
        card.addView(shareBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));

        // 4. Founder: Apply to Self
        long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                ? getUserConfig().getCurrentUser().id : 0;
        if (selfId == MiogramBadgeManager.FOUNDER_USER_ID) {
            TextView applyBtn = createActionButton(context,
                    MiogramLocale.get("✨ Застосувати собі у профіль", "✨ Применить себе в профиль", "✨ Apply to My Profile"),
                    false, this::applyBadgeToSelf);
            card.addView(applyBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 46, 0, 0, 0, 8));
        }

        TextView note = new TextView(context);
        note.setText(MiogramLocale.get(
                "Бейдж можна експортувати як чистий SVG/PNG файл, скопіювати в буфер обміну або надіслати в діалог.",
                "Бейдж можно экспортировать как чистый SVG/PNG файл, скопировать в буфер обмена или отправить в диалог.",
                "Export your badge as an SVG/PNG file, copy its code to clipboard, or send it to chat."));
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        note.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        return card;
    }

    private TextView createActionButton(Context context, String label, boolean primary, Runnable action) {
        TextView tv = new TextView(context);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(AndroidUtilities.dp(12));
        if (primary) {
            bg.setColor(YumiMd3.primary());
            tv.setTextColor(0xFFFFFFFF);
        } else {
            bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            bg.setStroke(AndroidUtilities.dp(1), YumiMd3.outlineVariant());
            tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        }
        tv.setBackground(bg);

        tv.setOnClickListener(v -> {
            tv.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (action != null) action.run();
        });
        ScaleStateListAnimator.apply(tv, 0.03f, 1.4f);
        return tv;
    }

    private TextView createButton(Context context, String label, boolean primary, Runnable action) {
        return createActionButton(context, label, primary, action);
    }

    private void openFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/svg+xml", "image/png", "image/webp", "image/jpeg", "image/*", "text/*"});
            startActivityForResult(intent, REQUEST_PICK_SVG);
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, MiogramLocale.get("Помилка запуску файлового менеджера", "Ошибка запуска файлового менеджера", "Failed to launch file picker")).show();
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_SVG && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            handlePickedUri(data.getData());
        }
    }

    private void handlePickedUri(Uri uri) {
        try {
            Context ctx = getParentActivity() != null ? getParentActivity() : ApplicationLoader.applicationContext;
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return;
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            is.close();
            byte[] rawBytes = bos.toByteArray();
            if (rawBytes.length == 0) return;

            // Check if raster format (PNG, WebP, JPG, etc.)
            Bitmap bm = null;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                bm = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length, opts);
            } catch (Throwable ignore) {}

            if (bm != null) {
                isRasterFormat = true;
                currentRawBytes = rawBytes;
                currentBadgeBitmap = bm;
                currentSvgContent = null;
                applyBadgeBitmapPreview(bm, "PNG / Растр");
            } else {
                isRasterFormat = false;
                currentRawBytes = rawBytes;
                String content = new String(rawBytes, StandardCharsets.UTF_8);
                setSvgContent(content);
            }
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, MiogramLocale.get("Не вдалося прочитати файл", "Не удалось прочитать файл", "Failed to read file")).show();
        }
    }

    private void showPasteCodeDialog() {
        Context context = getParentActivity() != null ? getParentActivity() : getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Вставити код бейджа (SVG)", "Вставить код бейджа (SVG)", "Paste Badge Code (SVG)"));

        EditText et = new EditText(context);
        et.setText(currentSvgContent != null ? currentSvgContent : "");
        et.setHint("<svg ...> ... </svg>");
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        et.setLines(6);
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setBackground(Theme.createEditTextDrawable(context, false));

        FrameLayout fl = new FrameLayout(context);
        fl.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(10), AndroidUtilities.dp(20), AndroidUtilities.dp(10));
        fl.addView(et, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        builder.setView(fl);

        builder.setPositiveButton("OK", (d, w) -> {
            String text = et.getText().toString();
            if (!TextUtils.isEmpty(text)) {
                setSvgContent(text);
            }
        });
        builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        builder.show();
    }

    private void setSvgContent(String content) {
        if (TextUtils.isEmpty(content)) return;
        this.currentSvgContent = content.trim();
        this.isRasterFormat = false;
        renderSvgPreview(currentSvgContent);
    }

    private String normalizeSvgXml(String rawSvg) {
        if (rawSvg == null) return "";
        String s = rawSvg.trim();
        // Якщо у тегу <svg> є viewBox, але розміри завищені або відсутні, нормалізуємо розмір
        try {
            int vbIdx = s.indexOf("viewBox=\"");
            if (vbIdx < 0) vbIdx = s.indexOf("viewBox='");
            if (vbIdx >= 0) {
                char quote = s.charAt(vbIdx + 7);
                int endQuote = s.indexOf(quote, vbIdx + 9);
                if (endQuote > vbIdx) {
                    String vb = s.substring(vbIdx + 9, endQuote).trim();
                    String[] parts = vb.split("[,\\s]+");
                    if (parts.length >= 4) {
                        float vbW = Float.parseFloat(parts[2]);
                        float vbH = Float.parseFloat(parts[3]);
                        if (vbW > 0 && vbH > 0) {
                            // Синхронізуємо атрибути width/height з viewBox, щоб Telegram SvgHelper коректно центрував
                            s = s.replaceFirst("<svg([^>]*)width=[\"'][^\"']*[\"']", "<svg$1width=\"" + (int) vbW + "\"");
                            s = s.replaceFirst("<svg([^>]*)height=[\"'][^\"']*[\"']", "<svg$1height=\"" + (int) vbH + "\"");
                        }
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        return s;
    }

    private void renderSvgPreview(String svg) {
        if (TextUtils.isEmpty(svg)) return;
        try {
            String norm = normalizeSvgXml(svg);
            int dpSize = AndroidUtilities.dp(48);
            Bitmap bm = SvgHelper.getBitmap(norm, dpSize, dpSize, false);
            if (bm != null) {
                this.isRasterFormat = false;
                this.currentRawBytes = svg.getBytes(StandardCharsets.UTF_8);
                applyBadgeBitmapPreview(bm, "SVG Вектор");
            } else {
                if (badgeStatusLabel != null) {
                    badgeStatusLabel.setText(MiogramLocale.get("SVG завантажено як текст", "SVG загружен как текст", "SVG loaded as text"));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            if (badgeStatusLabel != null) {
                badgeStatusLabel.setText(MiogramLocale.get("Помилка розпізнавання SVG", "Ошибка распознавания SVG", "SVG parse error"));
            }
        }
    }

    private void applyBadgeBitmapPreview(Bitmap bm, String typeLabel) {
        if (bm == null) return;
        this.currentBadgeBitmap = bm;
        if (badgePreviewLarge != null) {
            badgePreviewLarge.setImageBitmap(bm);
            badgePreviewLarge.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (badgePreviewSmall != null) {
            badgePreviewSmall.setImageBitmap(bm);
            badgePreviewSmall.setScaleType(ImageView.ScaleType.FIT_CENTER);
        }
        if (badgeStatusLabel != null) {
            String extra = (bm.getWidth() == 128 && bm.getHeight() == 128) ? " (128x128 ✓)" : "";
            badgeStatusLabel.setText(MiogramLocale.get(
                    typeLabel + " • " + bm.getWidth() + "x" + bm.getHeight() + " px" + extra,
                    typeLabel + " • " + bm.getWidth() + "x" + bm.getHeight() + " px" + extra,
                    typeLabel + " • " + bm.getWidth() + "x" + bm.getHeight() + " px" + extra));
        }
    }

    private void copyBadgeCode() {
        String codeToCopy;
        if (isRasterFormat && currentRawBytes != null) {
            codeToCopy = "data:image/png;base64," + Base64.encodeToString(currentRawBytes, Base64.NO_WRAP);
        } else if (!TextUtils.isEmpty(currentSvgContent)) {
            codeToCopy = currentSvgContent;
        } else {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    MiogramLocale.get("Спочатку завантажте або оберіть бейдж", "Сначала загрузите или выберите бейдж", "Choose or upload a badge first")).show();
            return;
        }
        AndroidUtilities.addToClipboard(codeToCopy);
        BulletinFactory.of(this).createSimpleBulletin(R.raw.done,
                MiogramLocale.get("Код бейджа скопійовано! ✓", "Код бейджа скопирован! ✓", "Badge code copied! ✓")).show();
    }

    private File saveCurrentBadgeFile() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            File cacheDir = new File(ctx.getCacheDir(), "badges");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String rawName = badgeNameInput != null && !TextUtils.isEmpty(badgeNameInput.getText())
                    ? badgeNameInput.getText().toString().trim() : "custom_badge";
            String safeName = rawName.replaceAll("[^a-zA-Z0-9_-]", "_");

            String ext = isRasterFormat ? ".png" : ".svg";
            File targetFile = new File(cacheDir, safeName + ext);

            FileOutputStream fos = new FileOutputStream(targetFile);
            if (isRasterFormat && currentRawBytes != null) {
                fos.write(currentRawBytes);
            } else if (currentBadgeBitmap != null && isRasterFormat) {
                currentBadgeBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            } else if (!TextUtils.isEmpty(currentSvgContent)) {
                fos.write(currentSvgContent.getBytes(StandardCharsets.UTF_8));
            } else if (currentRawBytes != null) {
                fos.write(currentRawBytes);
            }
            fos.flush();
            fos.close();

            return targetFile;
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    MiogramLocale.get("Помилка збереження файлу", "Ошибка сохранения файла", "Failed to save file")).show();
            return null;
        }
    }

    private void exportBadgeFile() {
        File file = saveCurrentBadgeFile();
        if (file != null) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done,
                    MiogramLocale.get("Файл збережено: " + file.getName(), "Файл сохранён: " + file.getName(), "File saved: " + file.getName())).show();
        }
    }

    private void shareBadgeDirectly() {
        File file = saveCurrentBadgeFile();
        if (file == null) return;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            String rawName = badgeNameInput != null && !TextUtils.isEmpty(badgeNameInput.getText())
                    ? badgeNameInput.getText().toString().trim() : "Custom Badge";
            TLRPC.User self = getUserConfig().getCurrentUser();
            String username = self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : "—";
            long id = self != null ? self.id : 0;
            String name = self != null ? UserObject.getUserName(self) : "User";
            String desc = badgeDescInput != null && !TextUtils.isEmpty(badgeDescInput.getText())
                    ? badgeDescInput.getText().toString().trim() : "—";

            String applicationText = "🏷 Заявка на новий бейджик Yumigram\n"
                    + "• Назва: " + rawName + "\n"
                    + "• Автор: " + name + " (" + username + ", ID: " + id + ")\n"
                    + "• Формат: " + (isRasterFormat ? "PNG (128x128)" : "SVG Вектор") + "\n"
                    + "• Бажаний колір: " + selectedColorHex + "\n"
                    + "• Опис: " + desc;

            Uri fileUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(isRasterFormat ? "image/png" : "image/svg+xml");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_TEXT, applicationText);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            if (getParentActivity() != null) {
                getParentActivity().startActivity(Intent.createChooser(shareIntent,
                        MiogramLocale.get("Надіслати бейдж", "Отправить бейдж", "Send Badge")));
            }
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    MiogramLocale.get("Помилка відправки файлу", "Ошибка отправки файла", "Failed to share file")).show();
        }
    }

    private void applyBadgeToSelf() {
        long selfId = getUserConfig() != null && getUserConfig().getCurrentUser() != null
                ? getUserConfig().getCurrentUser().id : 0;
        if (selfId <= 0) return;
        String rawName = badgeNameInput != null && !TextUtils.isEmpty(badgeNameInput.getText())
                ? badgeNameInput.getText().toString().trim() : "Custom Workshop Badge";
        String desc = badgeDescInput != null && !TextUtils.isEmpty(badgeDescInput.getText())
                ? badgeDescInput.getText().toString().trim() : "Створено в майстерні бейджів";

        MiogramSupabaseBridge.grantBadgeToUser(selfId, "custom", rawName, desc, () -> {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done,
                    MiogramLocale.get("Бейдж успішно застосовано! ✓", "Бейдж успешно применён! ✓", "Badge applied! ✓")).show();
        });
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
