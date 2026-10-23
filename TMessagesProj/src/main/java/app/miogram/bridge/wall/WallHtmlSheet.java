package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.Components.LayoutHelper;

import app.miogram.bridge.wall.WallModels.WallElement;

/**
 * Повноекранний перегляд HTML/CSS/JS-віджета (карточка стіни відкриває це по тапу).
 */
public class WallHtmlSheet extends BottomSheet {

    private final Context context;
    private final BaseFragment parentFragment;
    private final WallElement element;

    public WallHtmlSheet(Context context, BaseFragment parentFragment, WallElement element) {
        super(context, false, null);
        this.context = context;
        this.parentFragment = parentFragment;
        this.element = element;
        setApplyTopPadding(true);
        setApplyBottomPadding(true);
        setTitle(MiogramLocale.get("HTML-віджет", "HTML-виджет", "HTML widget"));

        FrameLayout root = new FrameLayout(context);
        root.setBackground(WallUi.pill(WallUi.dark() ? 0xF2150F2E : 0xF8FFFFFF, 0x2EFFFFFF, 20));

        WebView webView = new WebView(context);
        WebSettings st = webView.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(false);
        try {
            st.setBlockNetworkLoads(true);
        } catch (Throwable ignore) {
        }
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.loadDataWithBaseURL(null, buildHtml(element), "text/html", "UTF-8", null);
        root.addView(webView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, AndroidUtilities.dp(340), Gravity.TOP, 10, 0, 10, 14));

        setCustomView(root);
    }

    /** Збирає фінальний HTML із частин. Якщо користувач дав цілий документ — віддаємо як є. */
    public static String buildHtml(WallElement element) {
        String html = safe(element.html);
        String css = safe(element.css);
        String js = safe(element.js);
        if (html.isEmpty() && css.isEmpty() && js.isEmpty()) {
            html = "<p style='color:#999;font-family:sans-serif'>порожній віджет</p>";
        }
        String lower = html.trim().toLowerCase();
        if (lower.startsWith("<!doctype") || lower.startsWith("<html")) {
            return html;
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">"
                + "<style>"
                + "html,body{margin:0;padding:14px;background:transparent;font-family:sans-serif;color:#fff;overflow:hidden}"
                + "*{box-sizing:border-box}"
                + css
                + "</style></head><body>"
                + html
                + "<script>try{"
                + js
                + "}catch(e){document.body.insertAdjacentHTML('beforeend','<pre style=\"color:#f88;font-size:10px;white-space:pre-wrap\">'+e+'</pre>')}</script>"
                + "</body></html>";
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
