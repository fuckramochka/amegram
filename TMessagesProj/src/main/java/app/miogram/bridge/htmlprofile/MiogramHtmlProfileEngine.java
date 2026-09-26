package app.miogram.bridge.htmlprofile;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;

/**
 * Engine for Amegram Native HTML/CSS Profiles:
 * - Stores & loads customizable HTML/CSS profile cards.
 * - Injects dynamic CSS variables mapped to Amegram/Telegram active theme.
 * - Provides preset templates (Cyberpunk, Glassmorphism, Terminal).
 * - Client-side safe sandbox and privacy toggle.
 */
public class MiogramHtmlProfileEngine {

    private static final String PREFS_NAME = "miogram_html_profile_prefs";
    private static final String KEY_ENABLED = "html_profile_enabled";
    private static final String KEY_MY_HTML = "my_custom_html";
    private static final String KEY_USER_PREFIX = "user_html_";

    public static final String TEMPLATE_CYBERPUNK =
            "<div class=\"card cyberpunk\">\n" +
            "  <div class=\"badge\">⚡ AMEGRAM VERIFIED</div>\n" +
            "  <div class=\"user-row\">\n" +
            "    <div class=\"avatar-ring\">✦</div>\n" +
            "    <div class=\"user-info\">\n" +
            "      <div class=\"name\">CYBER RUNNER</div>\n" +
            "      <div class=\"sub\">Neural Link • Amegram OS</div>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "  <div class=\"bio\">Building next-gen fluid messaging. Zero bloat, pure speed, infinite customizability.</div>\n" +
            "  <div class=\"tags\">\n" +
            "    <span class=\"tag\">#Android</span>\n" +
            "    <span class=\"tag\">#Amegram</span>\n" +
            "    <span class=\"tag\">#CyberDeck</span>\n" +
            "  </div>\n" +
            "</div>";

    public static final String TEMPLATE_GLASSMORPHISM =
            "<div class=\"card glass\">\n" +
            "  <div class=\"glass-header\">\n" +
            "    <span class=\"pill\">✨ DIGITAL CREATOR</span>\n" +
            "    <span class=\"status-dot\"></span>\n" +
            "  </div>\n" +
            "  <h3>Amegram Enthusiast</h3>\n" +
            "  <p>Exploring sound waves in PocketPlayer, organizing vault archives, and coding custom themes.</p>\n" +
            "  <div class=\"metrics\">\n" +
            "    <div class=\"metric\"><div class=\"val\">100%</div><div class=\"lbl\">Privacy</div></div>\n" +
            "    <div class=\"metric\"><div class=\"val\">0ms</div><div class=\"lbl\">Lag</div></div>\n" +
            "    <div class=\"metric\"><div class=\"val\">FLAC</div><div class=\"lbl\">Audio</div></div>\n" +
            "  </div>\n" +
            "</div>";

    public static final String TEMPLATE_TERMINAL =
            "<div class=\"card terminal\">\n" +
            "  <div class=\"term-bar\">\n" +
            "    <span class=\"dot red\"></span>\n" +
            "    <span class=\"dot yellow\"></span>\n" +
            "    <span class=\"dot green\"></span>\n" +
            "    <span class=\"term-title\">user@amegram:~</span>\n" +
            "  </div>\n" +
            "  <div class=\"term-body\">\n" +
            "    <div class=\"cmd\">$ whoami</div>\n" +
            "    <div class=\"res\">amegram_power_user</div>\n" +
            "    <div class=\"cmd\">$ amegram --status</div>\n" +
            "    <div class=\"res success\">[OK] Subfolders active</div>\n" +
            "    <div class=\"res success\">[OK] Cloud Vault connected</div>\n" +
            "    <div class=\"res success\">[OK] PocketPlayer synced</div>\n" +
            "    <div class=\"cmd\">$ echo \"Stay tuned\"</div>\n" +
            "    <div class=\"res\">Stay tuned<span class=\"blink\">_</span></div>\n" +
            "  </div>\n" +
            "</div>";

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return getPrefs().getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(boolean enabled) {
        getPrefs().edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public static String getMyCustomHtml() {
        String saved = getPrefs().getString(KEY_MY_HTML, null);
        if (TextUtils.isEmpty(saved)) {
            return TEMPLATE_CYBERPUNK;
        }
        return saved;
    }

    public static void saveMyCustomHtml(String html) {
        getPrefs().edit().putString(KEY_MY_HTML, html != null ? html.trim() : "").apply();
    }

    public static String getCustomHtmlForUser(long userId) {
        long myId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        if (userId == myId || userId == 0) {
            return getMyCustomHtml();
        }
        return getPrefs().getString(KEY_USER_PREFIX + userId, null);
    }

    public static void saveCustomHtmlForUser(long userId, String html) {
        if (userId <= 0) return;
        getPrefs().edit().putString(KEY_USER_PREFIX + userId, html != null ? html.trim() : "").apply();
    }

    public static void clearUserCustomHtml(long userId) {
        getPrefs().edit().remove(KEY_USER_PREFIX + userId).apply();
    }

    /**
     * Builds full standalone HTML page with theme colors and isolated CSS.
     */
    public static String buildFullPageHtml(String contentHtml, Theme.ResourcesProvider resourcesProvider) {
        int bgColor = Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider);
        int textColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        int subTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider);
        int accentColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader, resourcesProvider);
        int cardBgColor = Theme.getColor(Theme.key_windowBackgroundGray, resourcesProvider);
        int dividerColor = Theme.getColor(Theme.key_divider, resourcesProvider);

        String hexBg = String.format("#%06X", (0xFFFFFF & bgColor));
        String hexText = String.format("#%06X", (0xFFFFFF & textColor));
        String hexSub = String.format("#%06X", (0xFFFFFF & subTextColor));
        String hexAccent = String.format("#%06X", (0xFFFFFF & accentColor));
        String hexCardBg = String.format("#%06X", (0xFFFFFF & cardBgColor));
        String hexDivider = String.format("#%06X", (0xFFFFFF & dividerColor));

        return "<!DOCTYPE html>\n" +
                "<html>\n" +
                "<head>\n" +
                "  <meta charset=\"utf-8\">\n" +
                "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, user-scalable=no, maximum-scale=1.0\">\n" +
                "  <style>\n" +
                "    :root {\n" +
                "      --am-bg: " + hexBg + ";\n" +
                "      --am-text: " + hexText + ";\n" +
                "      --am-subtext: " + hexSub + ";\n" +
                "      --am-accent: " + hexAccent + ";\n" +
                "      --am-card-bg: " + hexCardBg + ";\n" +
                "      --am-divider: " + hexDivider + ";\n" +
                "    }\n" +
                "    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }\n" +
                "    body {\n" +
                "      background: transparent;\n" +
                "      color: var(--am-text);\n" +
                "      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\n" +
                "      padding: 10px 14px;\n" +
                "      overflow-x: hidden;\n" +
                "      word-wrap: break-word;\n" +
                "      user-select: none;\n" +
                "    }\n" +
                "    a { color: var(--am-accent); text-decoration: none; }\n" +
                "    .card {\n" +
                "      border-radius: 16px;\n" +
                "      padding: 16px;\n" +
                "      transition: transform 0.2s ease;\n" +
                "    }\n" +
                "    /* Cyberpunk Theme */\n" +
                "    .cyberpunk {\n" +
                "      background: linear-gradient(135deg, rgba(20,20,35,0.95), rgba(10,10,20,0.98));\n" +
                "      border: 1px solid rgba(0, 240, 255, 0.35);\n" +
                "      box-shadow: 0 4px 20px rgba(0, 240, 255, 0.12), inset 0 0 15px rgba(255, 0, 85, 0.08);\n" +
                "      color: #fff;\n" +
                "    }\n" +
                "    .cyberpunk .badge {\n" +
                "      display: inline-block;\n" +
                "      font-size: 10px;\n" +
                "      font-weight: 800;\n" +
                "      letter-spacing: 1.5px;\n" +
                "      padding: 3px 8px;\n" +
                "      border-radius: 4px;\n" +
                "      background: rgba(0, 240, 255, 0.15);\n" +
                "      color: #00f0ff;\n" +
                "      border: 1px solid #00f0ff;\n" +
                "      margin-bottom: 12px;\n" +
                "      text-shadow: 0 0 8px rgba(0, 240, 255, 0.6);\n" +
                "    }\n" +
                "    .cyberpunk .user-row { display: flex; align-items: center; gap: 12px; margin-bottom: 10px; }\n" +
                "    .cyberpunk .avatar-ring {\n" +
                "      width: 42px; height: 42px; border-radius: 50%;\n" +
                "      background: linear-gradient(45deg, #ff0055, #00f0ff);\n" +
                "      display: flex; align-items: center; justify-content: center;\n" +
                "      font-size: 20px; box-shadow: 0 0 12px rgba(255, 0, 85, 0.4);\n" +
                "    }\n" +
                "    .cyberpunk .name { font-size: 17px; font-weight: 800; letter-spacing: 0.5px; color: #fff; }\n" +
                "    .cyberpunk .sub { font-size: 12px; color: #00f0ff; opacity: 0.85; font-family: monospace; }\n" +
                "    .cyberpunk .bio { font-size: 13px; line-height: 1.5; color: #d0d0e0; margin-bottom: 12px; }\n" +
                "    .cyberpunk .tags { display: flex; gap: 6px; flex-wrap: wrap; }\n" +
                "    .cyberpunk .tag {\n" +
                "      font-size: 11px; padding: 3px 8px; border-radius: 6px;\n" +
                "      background: rgba(255, 0, 85, 0.15); color: #ff0077; border: 1px solid rgba(255,0,85,0.3);\n" +
                "    }\n" +
                "    /* Glassmorphism Theme */\n" +
                "    .glass {\n" +
                "      background: rgba(128, 128, 128, 0.12);\n" +
                "      backdrop-filter: blur(16px);\n" +
                "      -webkit-backdrop-filter: blur(16px);\n" +
                "      border: 1px solid rgba(255, 255, 255, 0.15);\n" +
                "      box-shadow: 0 8px 32px rgba(0, 0, 0, 0.1);\n" +
                "    }\n" +
                "    .glass-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }\n" +
                "    .glass .pill { font-size: 11px; font-weight: 700; color: var(--am-accent); }\n" +
                "    .glass .status-dot { width: 8px; height: 8px; border-radius: 50%; background: #4caf50; box-shadow: 0 0 6px #4caf50; }\n" +
                "    .glass h3 { font-size: 16px; margin-bottom: 6px; color: var(--am-text); }\n" +
                "    .glass p { font-size: 13px; color: var(--am-subtext); line-height: 1.4; margin-bottom: 12px; }\n" +
                "    .glass .metrics { display: flex; justify-content: space-around; padding-top: 10px; border-top: 1px solid var(--am-divider); }\n" +
                "    .glass .metric { text-align: center; }\n" +
                "    .glass .val { font-size: 15px; font-weight: 800; color: var(--am-accent); }\n" +
                "    .glass .lbl { font-size: 10px; color: var(--am-subtext); text-transform: uppercase; margin-top: 2px; }\n" +
                "    /* Terminal Theme */\n" +
                "    .terminal {\n" +
                "      background: #0d1117;\n" +
                "      border: 1px solid #30363d;\n" +
                "      box-shadow: 0 6px 18px rgba(0,0,0,0.3);\n" +
                "      font-family: 'Fira Code', Menlo, Monaco, Consolas, monospace;\n" +
                "      color: #c9d1d9;\n" +
                "    }\n" +
                "    .term-bar { display: flex; align-items: center; gap: 6px; margin-bottom: 12px; padding-bottom: 8px; border-bottom: 1px solid #21262d; }\n" +
                "    .term-bar .dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }\n" +
                "    .term-bar .red { background: #ff5f56; }\n" +
                "    .term-bar .yellow { background: #ffbd2e; }\n" +
                "    .term-bar .green { background: #27c93f; }\n" +
                "    .term-title { font-size: 11px; color: #8b949e; margin-left: 6px; }\n" +
                "    .term-body { font-size: 12px; line-height: 1.6; }\n" +
                "    .term-body .cmd { color: #58a6ff; font-weight: 600; }\n" +
                "    .term-body .res { color: #8b949e; margin-left: 12px; margin-bottom: 4px; }\n" +
                "    .term-body .success { color: #3fb950; }\n" +
                "    .term-body .blink { animation: blink 1s infinite; color: #58a6ff; }\n" +
                "    @keyframes blink { 0%, 50% { opacity: 1; } 51%, 100% { opacity: 0; } }\n" +
                "  </style>\n" +
                "</head>\n" +
                "<body>\n" +
                (contentHtml != null ? contentHtml : "") + "\n" +
                "</body>\n" +
                "</html>";
    }
}
