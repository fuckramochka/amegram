package app.miogram.bridge.htmlprofile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.MiogramLocale;

/**
 * Dedicated Amegram HTML/CSS Profile Studio:
 * - Live code editor with Monospace font and theme presets.
 * - One-tap templates (Cyberpunk, Glassmorphism, Terminal).
 * - Instant toggle between Code Editor and Live Sandboxed Preview.
 */
public class MiogramHtmlProfileActivity extends BaseFragment {

    private static final int MENU_SAVE = 1;

    private EditText codeEditText;
    private WebView previewWebView;
    private FrameLayout editorContainer;
    private FrameLayout previewContainer;
    private TextView tabEditorBtn;
    private TextView tabPreviewBtn;
    private boolean isPreviewMode = false;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(MiogramLocale.get("HTML Профіль", "HTML Профиль", "HTML Profile"));
        actionBar.setSubtitle(MiogramLocale.get("Кастомізація картки", "Кастомизация карточки", "Profile Card Studio"));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_SAVE) {
                    saveProfileHtml();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_SAVE, R.drawable.ic_ab_done);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        // Top Template Pills Bar
        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout pillsLayout = new LinearLayout(context);
        pillsLayout.setOrientation(LinearLayout.HORIZONTAL);
        pillsLayout.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        pillsLayout.addView(createTemplatePill(context, "⚡ Cyberpunk", MiogramHtmlProfileEngine.TEMPLATE_CYBERPUNK));
        pillsLayout.addView(createTemplatePill(context, "✨ Glassmorphism", MiogramHtmlProfileEngine.TEMPLATE_GLASSMORPHISM));
        pillsLayout.addView(createTemplatePill(context, "💻 Terminal", MiogramHtmlProfileEngine.TEMPLATE_TERMINAL));
        pillsLayout.addView(createTemplatePill(context, "🗑 " + MiogramLocale.get("Очистити", "Очистить", "Clear"), ""));

        hsv.addView(pillsLayout);
        root.addView(hsv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Tab Switcher (Code vs Preview)
        LinearLayout tabSwitcher = new LinearLayout(context);
        tabSwitcher.setOrientation(LinearLayout.HORIZONTAL);
        tabSwitcher.setGravity(Gravity.CENTER);
        tabSwitcher.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        tabEditorBtn = new TextView(context);
        tabEditorBtn.setText(MiogramLocale.get("Код (HTML/CSS)", "Код (HTML/CSS)", "Code (HTML/CSS)"));
        tabEditorBtn.setTextSize(13);
        tabEditorBtn.setGravity(Gravity.CENTER);
        tabEditorBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        tabEditorBtn.setOnClickListener(v -> setPreviewMode(false));

        tabPreviewBtn = new TextView(context);
        tabPreviewBtn.setText(MiogramLocale.get("Попередній перегляд", "Предпросмотр", "Live Preview"));
        tabPreviewBtn.setTextSize(13);
        tabPreviewBtn.setGravity(Gravity.CENTER);
        tabPreviewBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        tabPreviewBtn.setOnClickListener(v -> setPreviewMode(true));

        tabSwitcher.addView(tabEditorBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        tabSwitcher.addView(tabPreviewBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));
        root.addView(tabSwitcher, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Content Area (Editor vs Preview)
        FrameLayout contentArea = new FrameLayout(context);

        // 1. Editor container
        editorContainer = new FrameLayout(context);
        editorContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(0), AndroidUtilities.dp(12), AndroidUtilities.dp(12));

        ScrollView editorScrollView = new ScrollView(context);
        editorScrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));

        codeEditText = new EditText(context);
        codeEditText.setTypeface(Typeface.MONOSPACE);
        codeEditText.setTextSize(12.5f);
        codeEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        codeEditText.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        codeEditText.setHint(MiogramLocale.get("Введіть HTML та CSS стилі...", "Введите HTML и CSS стили...", "Enter HTML & CSS styles..."));
        codeEditText.setGravity(Gravity.TOP | Gravity.START);
        codeEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        codeEditText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        codeEditText.setBackground(null);
        codeEditText.setText(MiogramHtmlProfileEngine.getMyCustomHtml());

        editorScrollView.addView(codeEditText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        editorContainer.addView(editorScrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentArea.addView(editorContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // 2. Preview container
        previewContainer = new FrameLayout(context);
        previewContainer.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(0), AndroidUtilities.dp(12), AndroidUtilities.dp(12));
        previewContainer.setVisibility(View.GONE);

        previewWebView = new WebView(context);
        previewWebView.setBackgroundColor(Color.TRANSPARENT);
        initPreviewWebView(previewWebView);
        previewContainer.addView(previewWebView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        contentArea.addView(previewContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        root.addView(contentArea, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.0f));

        updateTabStyles();

        fragmentView = root;
        return fragmentView;
    }

    private View createTemplatePill(Context context, String title, String htmlTemplate) {
        TextView pill = new TextView(context);
        pill.setText(title);
        pill.setTextSize(12);
        pill.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourceProvider));
        pill.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        pill.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = AndroidUtilities.dp(8);
        pill.setLayoutParams(lp);

        pill.setOnClickListener(v -> {
            codeEditText.setText(htmlTemplate);
            if (isPreviewMode) {
                reloadPreview();
            }
        });
        return pill;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initPreviewWebView(WebView wv) {
        WebSettings ws = wv.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
    }

    private void setPreviewMode(boolean preview) {
        if (this.isPreviewMode == preview) return;
        this.isPreviewMode = preview;
        editorContainer.setVisibility(preview ? View.GONE : View.VISIBLE);
        previewContainer.setVisibility(preview ? View.VISIBLE : View.GONE);
        updateTabStyles();
        if (preview) {
            reloadPreview();
        }
    }

    private void updateTabStyles() {
        int activeColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourceProvider);
        int inactiveColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider);

        tabEditorBtn.setTextColor(!isPreviewMode ? activeColor : inactiveColor);
        tabEditorBtn.setTypeface(!isPreviewMode ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);

        tabPreviewBtn.setTextColor(isPreviewMode ? activeColor : inactiveColor);
        tabPreviewBtn.setTypeface(isPreviewMode ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
    }

    private void reloadPreview() {
        if (previewWebView == null) return;
        String raw = codeEditText.getText() != null ? codeEditText.getText().toString() : "";
        String fullHtml = MiogramHtmlProfileEngine.buildFullPageHtml(raw, resourceProvider);
        previewWebView.loadDataWithBaseURL("https://amegram.local/", fullHtml, "text/html", "UTF-8", null);
    }

    private void saveProfileHtml() {
        String code = codeEditText.getText() != null ? codeEditText.getText().toString().trim() : "";
        MiogramHtmlProfileEngine.saveMyCustomHtml(code);
        Toast.makeText(getParentActivity(), MiogramLocale.get("HTML Профіль успішно збережено!", "HTML Профиль успешно сохранен!", "HTML Profile successfully saved!"), Toast.LENGTH_SHORT).show();
        finishFragment();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        if (previewWebView != null) {
            try {
                previewWebView.stopLoading();
                previewWebView.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}
