package app.miogram.bridge.ai;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

import app.miogram.bridge.MiogramLocale;
import xyz.nextalone.nagram.NaConfig;

public final class AmegramTranscriptionPrefs {

    public static final int PROVIDER_AUTO = 0;
    public static final int PROVIDER_PREMIUM = 1;
    public static final int PROVIDER_WORKERSAI = 2;
    public static final int PROVIDER_GEMINI = 3;
    public static final int PROVIDER_OPENAI = 4;
    public static final int PROVIDER_LOCAL = 5;

    private static final String PREFS_NAME = "amegram_transcription_prefs";
    private static final String KEY_PREFER_FOR_PREMIUM = "prefer_amegram_for_premium";

    private AmegramTranscriptionPrefs() {}

    private static SharedPreferences getPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static int getProvider() {
        return NaConfig.INSTANCE.getTranscribeProvider().Int();
    }

    public static void setProvider(int provider) {
        NaConfig.INSTANCE.getTranscribeProvider().setConfigInt(provider);
    }

    public static boolean isPreferAmegramForPremium() {
        return getPrefs().getBoolean(KEY_PREFER_FOR_PREMIUM, true);
    }

    public static void setPreferAmegramForPremium(boolean prefer) {
        getPrefs().edit().putBoolean(KEY_PREFER_FOR_PREMIUM, prefer).apply();
    }

    public static String getProviderTitle(int provider) {
        switch (provider) {
            case PROVIDER_PREMIUM:
                return "Telegram Premium (TGP) ⭐️";
            case PROVIDER_GEMINI:
                return "Amegram AI (Gemini 2.5 Flash ໒꒱)";
            case PROVIDER_LOCAL:
                return "Amegram Local (Офлайн нейромережа ⚡)";
            case PROVIDER_OPENAI:
                return "OpenAI / Whisper API";
            case PROVIDER_WORKERSAI:
                return "Cloudflare Workers AI";
            case PROVIDER_AUTO:
            default:
                return MiogramLocale.get("Автоматично", "Автоматически", "Automatic");
        }
    }

    public static String[] getProviderLabels() {
        return new String[] {
                MiogramLocale.get("Автоматично (TG Premium або Amegram)", "Автоматически (TG Premium или Amegram)", "Auto (TG Premium or Amegram)"),
                "Telegram Premium (TGP) ⭐️",
                "Amegram AI (Gemini 2.5 Flash ໒꒱)",
                "Amegram Local (Швидка офлайн-нейромережа ⚡)",
                "OpenAI / Whisper API",
                "Cloudflare Workers AI"
        };
    }

    public static int[] getProviderValues() {
        return new int[] {
                PROVIDER_AUTO,
                PROVIDER_PREMIUM,
                PROVIDER_GEMINI,
                PROVIDER_LOCAL,
                PROVIDER_OPENAI,
                PROVIDER_WORKERSAI
        };
    }
}
