package app.miogram.bridge.htmlprofile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import app.miogram.bridge.MiogramLocale;

/**
 * Native Amegram HTML/CSS Profile Card Component:
 * - Embedded in ProfileActivity with hardware accelerated WebView.
 * - Auto-measures height based on rendered content to eliminate inner scrollbars.
 * - Secure sandbox: disables local file access, handles external links safely.
 * - Edit button for self profile.
 */
public class MiogramHtmlProfileCard extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private final FrameLayout cardContainer;
    private final WebView webView;
    private final LinearLayout editHeader;
    private long currentUserId = 0;
    private boolean isSelf = false;
    private int contentHeightDp = 180;

    @SuppressLint("SetJavaScriptEnabled")
    public MiogramHtmlProfileCard(@NonNull Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(4), AndroidUtilities.dp(12), AndroidUtilities.dp(6));

        cardContainer = new FrameLayout(context);
        cardContainer.setBackgroundColor(Color.TRANSPARENT);

        webView = new WebView(context);
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(OVER_SCROLL_NEVER);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void resize(final float height) {
                AndroidUtilities.runOnUIThread(() -> {
                    int dp = (int) Math.ceil(height);
                    if (dp > 20 && Math.abs(dp - contentHeightDp) > 4) {
                        contentHeightDp = Math.min(dp, 600); // capped at 600dp for sanity
                        LayoutParams lp = (LayoutParams) webView.getLayoutParams();
                        if (lp != null) {
                            lp.height = AndroidUtilities.dp(contentHeightDp);
                            webView.setLayoutParams(lp);
                        }
                    }
                });
            }
        }, "AmegramHost");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!TextUtils.isEmpty(url)) {
                    Browser.openUrl(getContext(), url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Auto-detect height
                view.loadUrl("javascript:(function() { " +
                        "var h = Math.max(document.body.scrollHeight, document.documentElement.scrollHeight); " +
                        "AmegramHost.resize(h); " +
                        "})()");
            }
        });

        cardContainer.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(contentHeightDp)));

        // Edit button overlay for self
        editHeader = new LinearLayout(context);
        editHeader.setOrientation(LinearLayout.HORIZONTAL);
        editHeader.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        editHeader.setPadding(0, 0, AndroidUtilities.dp(8), 0);

        ImageView editIcon = new ImageView(context);
        editIcon.setImageResource(R.drawable.msg_edit);
        editIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        editHeader.addView(editIcon, LayoutHelper.createLinear(16, 16, Gravity.CENTER_VERTICAL, 0, 0, 4, 0));

        TextView editLabel = new TextView(context);
        editLabel.setText(MiogramLocale.get("HTML Профіль", "HTML Профиль", "HTML Profile"));
        editLabel.setTextSize(11);
        editLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        editHeader.addView(editLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        editHeader.setOnClickListener(v -> openEditor());

        cardContainer.addView(editHeader, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 28, Gravity.TOP | Gravity.END, 0, 2, 4, 0));

        addView(cardContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
    }

    public void bind(long userId, boolean isSelf) {
        this.currentUserId = userId;
        this.isSelf = isSelf;

        if (!MiogramHtmlProfileEngine.isEnabled()) {
            setVisibility(View.GONE);
            return;
        }

        String rawHtml = MiogramHtmlProfileEngine.getCustomHtmlForUser(userId);
        if (TextUtils.isEmpty(rawHtml) && !isSelf) {
            setVisibility(View.GONE);
            return;
        }

        setVisibility(View.VISIBLE);
        editHeader.setVisibility(isSelf ? View.VISIBLE : View.GONE);

        String fullHtml = MiogramHtmlProfileEngine.buildFullPageHtml(rawHtml, resourcesProvider);
        webView.loadDataWithBaseURL("https://amegram.local/", fullHtml, "text/html", "UTF-8", null);
    }

    private void openEditor() {
        BaseFragment fragment = LaunchActivity.getLastFragment();
        if (fragment != null) {
            fragment.presentFragment(new MiogramHtmlProfileActivity());
        }
    }

    public void onDestroy() {
        try {
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            webView.onPause();
            webView.removeAllViews();
            webView.destroy();
        } catch (Exception ignored) {
        }
    }
}
