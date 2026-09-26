package app.miogram.bridge.ameprofile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
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

import app.miogram.bridge.MiogramLocale;

/**
 * Modern interactive BottomSheet for "Аме профіль" (Ame Profile):
 * - Live XML editor for native profile elements, colors, visibility, and styles.
 * - 1-tap "Опублікувати у вітку" button connecting directly to https://t.me/dkamegram/1499.
 * - 1-tap "Вставити & Застосувати" for importing Ame profiles shared by other community members.
 */
public class MiogramAmeProfileSheet extends BottomSheet {

    private final EditText xmlEditor;

    public MiogramAmeProfileSheet(Context context) {
        super(context, true);

        setApplyBottomPadding(false);
        setApplyTopPadding(false);

        int bgColor = getThemedColor(Theme.key_dialogBackground);
        if (bgColor == 0) bgColor = 0xFF14151F;
        fixNavigationBar(bgColor);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        // Drag bar
        View dragHandle = new View(context);
        GradientDrawable handleDrawable = new GradientDrawable();
        handleDrawable.setColor(0x33FFFFFF);
        handleDrawable.setCornerRadius(AndroidUtilities.dp(3));
        dragHandle.setBackground(handleDrawable);
        root.addView(dragHandle, LayoutHelper.createLinear(36, 4, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 14));

        // Header Title
        TextView title = new TextView(context);
        title.setText(MiogramLocale.get("Аме профіль ໒꒱", "Аме профиль ໒꒱", "Ame Profile ໒꒱"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        // Subtitle
        TextView subtitle = new TextView(context);
        subtitle.setText(MiogramLocale.get(
                "XML-код вашого поточного профілю. Редагуйте рядки, ховайте зайві елементи або публікуйте у вітку спільноти, щоб інші користувачі могли його встановити.",
                "XML-код вашего текущего профиля. Редактируйте строки, скрывайте лишние элементы или публикуйте в ветку сообщества, чтобы другие пользователи могли его установить.",
                "Live XML of your current profile. Edit attributes, hide elements, or share to the community topic so other users can import your look."
        ));
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitle.setLineSpacing(AndroidUtilities.dp(2), 1.15f);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // XML Code Editor Frame
        FrameLayout editorCard = new FrameLayout(context);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(ColorUtils.blendARGB(bgColor, 0xFF000000, 0.35f));
        cardBg.setStroke(AndroidUtilities.dp(1), ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.15f));
        cardBg.setCornerRadius(AndroidUtilities.dp(12));
        editorCard.setBackground(cardBg);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);

        xmlEditor = new EditText(context);
        xmlEditor.setText(MiogramAmeProfileEngine.exportCurrentProfileXml());
        xmlEditor.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        xmlEditor.setTypeface(Typeface.MONOSPACE);
        xmlEditor.setTextColor(0xFFE0E0FF);
        xmlEditor.setBackground(null);
        xmlEditor.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        xmlEditor.setGravity(Gravity.TOP | Gravity.START);
        scrollView.addView(xmlEditor, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        editorCard.addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 220));
        root.addView(editorCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Primary Button: "Опублікувати у вітку https://t.me/dkamegram/1499"
        TextView publishBtn = new TextView(context);
        publishBtn.setText(MiogramLocale.get("Опублікувати у вітку (@dkamegram/1499)", "Опубликовать в ветку (@dkamegram/1499)", "Share to Community Topic (@dkamegram/1499)"));
        publishBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f);
        publishBtn.setTypeface(AndroidUtilities.bold());
        publishBtn.setTextColor(Color.WHITE);
        publishBtn.setGravity(Gravity.CENTER);

        GradientDrawable pubBg = new GradientDrawable();
        int accent = Theme.getColor(Theme.key_featuredStickers_addButton);
        pubBg.setColor(accent != 0 ? accent : 0xFF6C63FF);
        pubBg.setCornerRadius(AndroidUtilities.dp(12));
        publishBtn.setBackground(pubBg);
        publishBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(13), AndroidUtilities.dp(16), AndroidUtilities.dp(13));
        publishBtn.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            MiogramAmeProfileEngine.shareToCommunity(context, code);
            dismiss();
        });
        root.addView(publishBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Secondary Action Row: "Застосувати зміни" & "Вставити з буфера"
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        TextView applyBtn = new TextView(context);
        applyBtn.setText(MiogramLocale.get("Застосувати код", "Применить код", "Apply Code"));
        applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        applyBtn.setTypeface(AndroidUtilities.bold());
        applyBtn.setTextColor(Color.WHITE);
        applyBtn.setGravity(Gravity.CENTER);

        GradientDrawable applyBg = new GradientDrawable();
        applyBg.setColor(0xFF2E7D32);
        applyBg.setCornerRadius(AndroidUtilities.dp(10));
        applyBtn.setBackground(applyBg);
        applyBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));
        applyBtn.setOnClickListener(v -> {
            String code = xmlEditor.getText().toString().trim();
            if (MiogramAmeProfileEngine.applyProfileXml(code)) {
                Toast.makeText(context, MiogramLocale.get("Аме профіль успішно застосовано!", "Аме профиль успешно применен!", "Ame Profile applied successfully!"), Toast.LENGTH_SHORT).show();
                dismiss();
            } else {
                Toast.makeText(context, MiogramLocale.get("Помилка в синтаксисі XML!", "Ошибка в синтаксисе XML!", "Syntax error in XML!"), Toast.LENGTH_SHORT).show();
            }
        });
        actionRow.addView(applyBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 6, 0));

        TextView pasteBtn = new TextView(context);
        pasteBtn.setText(MiogramLocale.get("Вставити з буфера", "Вставить из буфера", "Paste from Clipboard"));
        pasteBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        pasteBtn.setTypeface(AndroidUtilities.bold());
        pasteBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        pasteBtn.setGravity(Gravity.CENTER);

        GradientDrawable pasteBg = new GradientDrawable();
        pasteBg.setColor(ColorUtils.blendARGB(bgColor, 0xFFFFFFFF, 0.12f));
        pasteBg.setCornerRadius(AndroidUtilities.dp(10));
        pasteBtn.setBackground(pasteBg);
        pasteBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(11), AndroidUtilities.dp(12), AndroidUtilities.dp(11));
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = cm.getPrimaryClip().getItemAt(0).getText();
                if (!TextUtils.isEmpty(text)) {
                    xmlEditor.setText(text.toString());
                    Toast.makeText(context, MiogramLocale.get("Вставлено з буфера!", "Вставлено из буфера!", "Pasted from clipboard!"), Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(context, MiogramLocale.get("Буфер обміну порожній", "Буфер обмена пуст", "Clipboard is empty"), Toast.LENGTH_SHORT).show();
        });
        actionRow.addView(pasteBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 6, 0, 0, 0));

        root.addView(actionRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        setCustomView(root);
    }
}
