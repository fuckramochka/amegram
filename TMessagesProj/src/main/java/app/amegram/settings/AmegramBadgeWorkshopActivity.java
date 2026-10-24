package app.amegram.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
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
import org.telegram.messenger.browser.Browser;
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

import app.amegram.theme.YumiMd3;
import app.amegram.theme.YumiTheme;
import app.miogram.bridge.MiogramLocale;

/**
 * Майстерня бейджиків (Badge Workshop / Студія бейджиків):
 * - Завантаження та інтерактивний попередній перегляд SVG/PNG бейджиків у профілі
 * - Шаблони та налаштування метаданих бейджика
 * - Відправка заявки та SVG-файлу напряму в t.me/yumigram?direct
 */
public class AmegramBadgeWorkshopActivity extends BaseFragment {

    private static final int REQUEST_PICK_SVG = 1001;

    private static final String DEFAULT_STAR_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"48\" height=\"48\">"
                    + "<path d=\"M12 2 L14.5 9.5 L22 12 L14.5 14.5 L12 22 L9.5 14.5 L2 12 L9.5 9.5 Z\" fill=\"#C084FC\"/>"
                    + "<circle cx=\"12\" cy=\"12\" r=\"2.5\" fill=\"#FFFFFF\"/>"
                    + "</svg>";

    private static final String DEFAULT_HEART_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"48\" height=\"48\">"
                    + "<path d=\"M12 21 C12 21 3 14 3 8.5 C3 5.5 5.5 3 8.5 3 C10.5 3 11.5 4 12 5 C12.5 4 13.5 3 15.5 3 C18.5 3 21 5.5 21 8.5 C21 14 12 21 12 21 Z\" fill=\"#FB7185\"/>"
                    + "<circle cx=\"8.5\" cy=\"7.5\" r=\"1.5\" fill=\"#FFFFFF\" opacity=\"0.8\"/>"
                    + "</svg>";

    private static final String DEFAULT_WING_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"48\" height=\"48\">"
                    + "<path d=\"M22 4 C18 6 12 8 6 14 C4 16 3 18 5 19 C7 20 10 19 13 17 C11 19 10 21 12 22 C14 23 18 20 20 16 C22 12 23 7 22 4 Z\" fill=\"#38BDF8\"/>"
                    + "<path d=\"M20 7 C16 9 12 11 8 16\" stroke=\"#FFFFFF\" stroke-width=\"1.2\" fill=\"none\" stroke-linecap=\"round\"/>"
                    + "</svg>";

    private static final String DEFAULT_CROSS_SVG =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"48\" height=\"48\">"
                    + "<path d=\"M10 2 H14 V7 H19 V11 H14 V22 H10 V11 H5 V7 H10 Z\" fill=\"#E2E8F0\" stroke=\"#8B5CF6\" stroke-width=\"1.2\"/>"
                    + "<circle cx=\"12\" cy=\"9\" r=\"1.5\" fill=\"#8B5CF6\"/>"
                    + "</svg>";

    private String currentSvgContent = DEFAULT_STAR_SVG;
    private Bitmap currentBadgeBitmap;

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

        // 5. Submit to t.me/yumigram?direct
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
                "Створіть власний векторний бейдж, протестуйте вигляд поруч із нікнеймом та надішліть заявку в t.me/yumigram?direct.",
                "Создайте свой векторный бейдж, протестируйте вид рядом с никнеймом и отправьте заявку в t.me/yumigram?direct.",
                "Design your custom vector badge, preview it next to your name, and submit to t.me/yumigram?direct."));
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
        infoTitle.setText(MiogramLocale.get("Векторний бейдж", "Векторный бейдж", "Vector Badge"));
        infoTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        infoTitle.setTypeface(AndroidUtilities.bold());
        infoTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        infoCol.addView(infoTitle);

        badgeStatusLabel = new TextView(context);
        badgeStatusLabel.setText(MiogramLocale.get("Формат: SVG • 24x24 px", "Формат: SVG • 24x24 px", "Format: SVG • 24x24 px"));
        badgeStatusLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        badgeStatusLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoCol.addView(badgeStatusLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        top.addView(infoCol, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        card.addView(top);

        // Buttons
        LinearLayout btnRow = new LinearLayout(context);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 14, 0, 0));

        TextView uploadBtn = createButton(context, MiogramLocale.get("Завантажити SVG", "Загрузить SVG", "Upload SVG"), true, () -> openFilePicker());
        btnRow.addView(uploadBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 8, 0));

        TextView pasteBtn = createButton(context, MiogramLocale.get("Вставити код", "Вставить код", "Paste Code"), false, () -> showPasteCodeDialog());
        btnRow.addView(pasteBtn, LayoutHelper.createLinear(0, 44, 1f, 0, 0, 0, 0));

        card.addView(btnRow);

        // Template chips
        TextView tplTitle = new TextView(context);
        tplTitle.setText(MiogramLocale.get("Готові шаблони:", "Готовые шаблоны:", "Preset templates:"));
        tplTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tplTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        card.addView(tplTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 6));

        LinearLayout tplRow = new LinearLayout(context);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);

        tplRow.addView(createTemplateChip(context, "✦ Зірка", DEFAULT_STAR_SVG));
        tplRow.addView(createTemplateChip(context, "♥ Серце", DEFAULT_HEART_SVG));
        tplRow.addView(createTemplateChip(context, "🪽 Крило", DEFAULT_WING_SVG));
        tplRow.addView(createTemplateChip(context, "✝ Хрест", DEFAULT_CROSS_SVG));

        card.addView(tplRow);

        return card;
    }

    private View createTemplateChip(Context context, String label, String svg) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        chip.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6), AndroidUtilities.dp(10), AndroidUtilities.dp(6));

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

        TextView submitBtn = new TextView(context);
        submitBtn.setText(MiogramLocale.get("Відправити у t.me/yumigram?direct", "Отправить в t.me/yumigram?direct", "Submit to t.me/yumigram?direct"));
        submitBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        submitBtn.setTypeface(AndroidUtilities.bold());
        submitBtn.setTextColor(0xFFFFFFFF);
        submitBtn.setGravity(Gravity.CENTER);

        GradientDrawable btnBg = new GradientDrawable();
        btnBg.setColor(YumiMd3.primary());
        btnBg.setCornerRadius(AndroidUtilities.dp(16));
        submitBtn.setBackground(btnBg);

        submitBtn.setOnClickListener(v -> {
            submitBtn.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            submitBadgeToTelegram();
        });
        ScaleStateListAnimator.apply(submitBtn, 0.03f, 1.4f);

        card.addView(submitBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        TextView note = new TextView(context);
        note.setText(MiogramLocale.get(
                "Заявка з бейджем автоматично підготує файл та відкриє прямий діалог для затвердження модераторами спільноти.",
                "Заявка с бейджем автоматически подготовит файл и откроет прямой диалог для утверждения модераторами сообщества.",
                "Your badge request will format the file and open the direct link for review by community staff."));
        note.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        note.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        note.setLineSpacing(AndroidUtilities.dp(2), 1f);
        card.addView(note, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        return card;
    }

    private TextView createButton(Context context, String label, boolean primary, Runnable action) {
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

    private void openFilePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/svg+xml", "image/*", "text/*"});
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
            byte[] buf = new byte[4096];
            int r;
            while ((r = is.read(buf)) != -1) {
                bos.write(buf, 0, r);
            }
            is.close();
            String content = bos.toString("UTF-8");
            setSvgContent(content);
        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error, MiogramLocale.get("Не вдалося прочитати файл", "Не удалось прочитать файл", "Failed to read file")).show();
        }
    }

    private void showPasteCodeDialog() {
        Context context = getParentActivity() != null ? getParentActivity() : getContext();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Вставити SVG код", "Вставить SVG код", "Paste SVG code"));

        EditText et = new EditText(context);
        et.setText(currentSvgContent);
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
        renderSvgPreview(currentSvgContent);
    }

    private void renderSvgPreview(String svg) {
        try {
            int dpSize = AndroidUtilities.dp(48);
            Bitmap bm = SvgHelper.getBitmap(svg, dpSize, dpSize, false);
            if (bm != null) {
                this.currentBadgeBitmap = bm;
                if (badgePreviewLarge != null) badgePreviewLarge.setImageBitmap(bm);
                if (badgePreviewSmall != null) badgePreviewSmall.setImageBitmap(bm);
                if (badgeStatusLabel != null) {
                    badgeStatusLabel.setText(MiogramLocale.get(
                            "SVG розпізнано • " + bm.getWidth() + "x" + bm.getHeight() + " px",
                            "SVG распознан • " + bm.getWidth() + "x" + bm.getHeight() + " px",
                            "SVG parsed • " + bm.getWidth() + "x" + bm.getHeight() + " px"));
                }
            } else {
                if (badgeStatusLabel != null) {
                    badgeStatusLabel.setText(MiogramLocale.get("SVG завантажено як текст", "SVG загружен как текст", "SVG loaded as text"));
                }
            }
        } catch (Throwable e) {
            FileLog.e(e);
            if (badgeStatusLabel != null) {
                badgeStatusLabel.setText(MiogramLocale.get("SVG містить нестандартні теги", "SVG содержит нестандартные теги", "SVG has custom tags"));
            }
        }
    }

    private void submitBadgeToTelegram() {
        if (TextUtils.isEmpty(currentSvgContent)) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info,
                    MiogramLocale.get("Спочатку завантажте SVG файл", "Сначала загрузите SVG файл", "Upload an SVG file first")).show();
            return;
        }

        try {
            Context ctx = ApplicationLoader.applicationContext;
            File cacheDir = new File(ctx.getCacheDir(), "badges");
            if (!cacheDir.exists()) cacheDir.mkdirs();

            String rawName = badgeNameInput != null && !TextUtils.isEmpty(badgeNameInput.getText())
                    ? badgeNameInput.getText().toString().trim() : "badge";
            String safeName = rawName.replaceAll("[^a-zA-Z0-9_-]", "_");
            File svgFile = new File(cacheDir, safeName + ".svg");

            FileOutputStream fos = new FileOutputStream(svgFile);
            fos.write(currentSvgContent.getBytes("UTF-8"));
            fos.close();

            TLRPC.User self = getUserConfig().getCurrentUser();
            String username = self != null && !TextUtils.isEmpty(self.username) ? "@" + self.username : "—";
            long id = self != null ? self.id : 0;
            String name = self != null ? UserObject.getUserName(self) : "Користувач";
            String desc = badgeDescInput != null && !TextUtils.isEmpty(badgeDescInput.getText())
                    ? badgeDescInput.getText().toString().trim() : "—";

            String applicationText = "🏷 Заявка на новий бейджик Yumigram\n"
                    + "• Назва: " + rawName + "\n"
                    + "• Автор: " + name + " (" + username + ", ID: " + id + ")\n"
                    + "• Бажаний колір: " + selectedColorHex + "\n"
                    + "• Опис: " + desc;

            AndroidUtilities.addToClipboard(applicationText);

            // 1. Launch Browser / Deep link to t.me/yumigram?direct
            Browser.openUrl(getParentActivity(), "https://t.me/yumigram?direct");

            // 2. Share Intent for the SVG file
            try {
                Uri fileUri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", svgFile);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/svg+xml");
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                shareIntent.putExtra(Intent.EXTRA_TEXT, applicationText);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (getParentActivity() != null) {
                    getParentActivity().startActivity(Intent.createChooser(shareIntent,
                            MiogramLocale.get("Надіслати SVG у t.me/yumigram", "Отправить SVG в t.me/yumigram", "Send SVG to t.me/yumigram")));
                }
            } catch (Throwable ignore) {
            }

            BulletinFactory.of(this).createSimpleBulletin(
                    R.raw.done,
                    MiogramLocale.get("Заявку підготовлено! Текст скопійовано", "Заявка подготовлена! Текст скопирован", "Request prepared! Copied to clipboard")
            ).show();

        } catch (Throwable e) {
            FileLog.e(e);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.error,
                    MiogramLocale.get("Помилка підготовки заявки", "Ошибка подготовки заявки", "Failed to prepare request")).show();
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return !Theme.isCurrentThemeDark();
    }
}
