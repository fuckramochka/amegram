package app.amegram.hot;

import app.amegram.hot.api.HotAiText;
import app.amegram.hot.api.HotServices;

public final class HotAiGate {

    private HotAiGate() {
    }

    public static HotAiText get() {
        try {
            return HotModulesManager.getService(HotServices.AI_TEXT);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isAvailable() {
        return get() != null;
    }

    public static boolean isReplaceEditor() {
        HotAiText t = get();
        return t != null && HotModulesManager.getBool("ai", "replace_editor", true);
    }

    public static boolean isReplaceSummaries() {
        HotAiText t = get();
        return t != null && HotModulesManager.getBool("ai", "replace_summaries", true);
    }

    public static boolean isGeminiNano() {
        return HotModulesManager.getBool("ai", "gemini_nano", false);
    }

    public static void generate(String systemPrompt, String userText, HotAiText.Callback callback) {
        HotAiText t = get();
        if (t != null) {
            t.generate(systemPrompt, userText, callback);
        } else if (callback != null) {
            callback.onError("AI module is not available");
        }
    }
}
