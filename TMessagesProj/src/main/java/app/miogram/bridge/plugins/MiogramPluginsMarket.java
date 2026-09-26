package app.miogram.bridge.plugins;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import app.exteraless.plugins.PluginsController;
import app.miogram.bridge.MiogramLocale;

/**
 * Catalog & Market Engine for Amegram Plugins.
 * Completely opt-in: plugins are never forced or downloaded by default.
 * Provides easy 1-tap installation, removal, and metadata.
 */
public class MiogramPluginsMarket {

    private static final String PREFS_NAME = "miogram_plugins_market_prefs";
    private static final String KEY_PROMPT_VERSION = "plugins_prompt_version";

    public static class MarketPluginEntry {
        public final String id;
        public final String assetName;
        public final String title;
        public final String descUk;
        public final String descRu;
        public final String descEn;
        public final String category;
        public final int iconRes;

        public MarketPluginEntry(String id, String assetName, String title, String descUk, String descRu, String descEn, String category, int iconRes) {
            this.id = id;
            this.assetName = assetName;
            this.title = title;
            this.descUk = descUk;
            this.descRu = descRu;
            this.descEn = descEn;
            this.category = category;
            this.iconRes = iconRes;
        }

        public String getDescription() {
            return MiogramLocale.get(descUk, descRu, descEn);
        }
    }

    private static final List<MarketPluginEntry> CATALOG = new ArrayList<>();

    static {
        CATALOG.add(new MarketPluginEntry(
                "petpet",
                "petpet.py",
                "PetPet Generator",
                "Генерація анімованих GIF-погладжувань аватарок та картинок прямо з меню повідомлення.",
                "Генерация анимированных GIF-поглаживаний аватарок и картинок прямо из меню сообщения.",
                "Generates cute animated PetPet GIF pats of avatars and images directly from message menu.",
                "Медіа & GIF",
                R.drawable.msg_theme
        ));

        CATALOG.add(new MarketPluginEntry(
                "boykisser_meow",
                "boykisser_meow.py",
                "Boykisser Meow",
                "Нявкаючий звук та візуальні ефекти бойкісера при надсиланні повідомлень у чати.",
                "Мяукающий звук и визуальные эффекты бойкиссера при отправке сообщений в чаты.",
                "Cute meow sound effect and boykisser visual animations on sending messages.",
                "Аудіо & Розваги",
                R.drawable.baseline_stars_24
        ));

        CATALOG.add(new MarketPluginEntry(
                "custom_profile",
                "Custom Profile.plugin",
                "Custom Profile (WASM)",
                "WASM-рушій кастомізації профілю: карточки Steam, статус гри/музики та неонові плашки.",
                "WASM-движок кастомизации профиля: карточки Steam, статус игры/музыки и неоновые плашки.",
                "Full WASM package for custom profile cards (Steam, Spotify/Presence status, glowing badges).",
                "Профіль & Інтерфейс",
                R.drawable.msg_edit
        ));

        CATALOG.add(new MarketPluginEntry(
                "localizer",
                "Localizer.py",
                "Localizer",
                "Миттєвий переклад виділеного тексту або повідомлень за допомогою вбудованої моделі перекладу.",
                "Мгновенный перевод выделенного текста или сообщений с помощью встроенного переводчика.",
                "Instant local and neural translation of selected text and chat messages.",
                "Інструменти & Текст",
                R.drawable.msg_translate
        ));
    }

    public static List<MarketPluginEntry> getCatalog() {
        return CATALOG;
    }

    public static boolean isInstalled(MarketPluginEntry entry) {
        if (entry == null) return false;
        try {
            File pluginsDir = PluginsController.getInstance().getPluginsDir();
            File target = new File(pluginsDir, entry.assetName);
            return target.exists() && target.length() > 0;
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static boolean installPlugin(Context context, MarketPluginEntry entry) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null || entry == null) return false;

        try {
            File pluginsDir = PluginsController.getInstance().getPluginsDir();
            if (!pluginsDir.exists()) pluginsDir.mkdirs();

            File target = new File(pluginsDir, entry.assetName);
            try (InputStream is = context.getAssets().open("plugins/" + entry.assetName);
                 FileOutputStream fos = new FileOutputStream(target)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
            }

            PluginsController.getInstance().rescanAndLoadEnabled();
            return true;
        } catch (Throwable t) {
            FileLog.e("MiogramPluginsMarket: failed to install plugin " + entry.id, t);
            return false;
        }
    }

    public static boolean uninstallPlugin(MarketPluginEntry entry) {
        if (entry == null) return false;
        try {
            File pluginsDir = PluginsController.getInstance().getPluginsDir();
            File target = new File(pluginsDir, entry.assetName);
            if (target.exists()) {
                target.delete();
            }
            PluginsController.getInstance().uninstallPlugin(entry.id);
            PluginsController.getInstance().rescanAndLoadEnabled();
            return true;
        } catch (Throwable t) {
            FileLog.e("MiogramPluginsMarket: failed to uninstall plugin " + entry.id, t);
            return false;
        }
    }

    public static boolean shouldPromptOnboarding(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return false;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String lastVersion = sp.getString(KEY_PROMPT_VERSION, "");
        return !BuildVars.BUILD_VERSION_STRING.equals(lastVersion);
    }

    public static void markOnboardingDone(Context context) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context == null) return;
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_PROMPT_VERSION, BuildVars.BUILD_VERSION_STRING).apply();
    }
}
