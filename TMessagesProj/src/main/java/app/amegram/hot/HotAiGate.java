package app.amegram.hot;

import android.app.ActivityManager;
import android.content.Context;

import app.amegram.hot.api.HotAiText;
import app.amegram.hot.api.HotServices;
import app.miogram.bridge.MiogramLocale;

public final class HotAiGate {

    private HotAiGate() {
    }

    private static volatile boolean nanoDownloading = false;
    private static volatile int nanoDownloadProgress = 0;

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

    public static boolean isNanoDownloading() {
        return nanoDownloading;
    }

    public static void setNanoDownloading(boolean v) {
        nanoDownloading = v;
        if (!v) nanoDownloadProgress = 0;
    }

    public static int getNanoDownloadProgress() {
        return nanoDownloadProgress;
    }

    public static void setNanoDownloadProgress(int pct) {
        nanoDownloadProgress = Math.max(0, Math.min(100, pct));
    }

    private static float totalRamGb(Context context) {
        if (context == null) return 0f;
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return 0f;
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            return (float) (mi.totalMem / (1024.0 * 1024.0 * 1024.0));
        } catch (Throwable ignore) {
            return 0f;
        }
    }

    /** Nano підтримується лише на пристроях з >= 4.5 ГБ RAM. */
    public static boolean isNanoSupported(Context context) {
        return totalRamGb(context) >= 4.5f;
    }

    /** Попередження для слабких/середніх пристроїв, "" якщо все ок. */
    public static String nanoWarning(Context context) {
        float total = totalRamGb(context);
        if (total <= 0f) return "";
        if (total < 4.5f) {
            return MiogramLocale.get(
                    "Слабкий пристрій: Nano може не підтримуватись, буде хмара Flash-Lite",
                    "Слабое устройство: Nano может не поддерживаться, будет облако Flash-Lite",
                    "Low-end device: Nano may be unsupported, cloud Flash-Lite will be used");
        }
        if (total < 7.5f) {
            return MiogramLocale.get(
                    "Nano можливо, але важко - рекомендовано хмару",
                    "Nano возможно, но тяжело - рекомендовано облако",
                    "Nano is possible but heavy - cloud recommended");
        }
        return "";
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
