package app.amegram.bridge.ameprofile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramCustomUiPrefs;

/**
 * ✦ AME PROFILE ENGINE (АМЕ ПРОФІЛЬ) ✦
 * The pinnacle customization engine for Amegram profile aesthetics:
 * - 100% strict profile tab isolation: Zero global chat/bubble leaks.
 * - Comprehensive XML configuration with rich aesthetic presets.
 * - Full Ukrainian guide, snippet generation, and auto-formatting.
 * - 1-tap community topic sharing to https://t.me/dkamegram/1499.
 */
public class AmeProfileEngine {

    public static final String COMMUNITY_TOPIC_URL = "https://t.me/dkamegram/1499";
    private static final String PREFS_NAME = "amegram_profile_prefs";
    private static final String KEY_RAW_XML = "raw_profile_xml";

    public static class AmeCard {
        public String id = "";
        public String title = "";
        public String subtitle = "";
        public String icon = "link";
        public String url = "";
        public int bgColor = 0;
        public int gradientColor1 = 0;
        public int gradientColor2 = 0;
        public int textColor = 0;
        public int subtitleColor = 0;
        public String badge = "";
        public int badgeBgColor = 0;
        public int badgeTextColor = 0;
        public int borderColor = 0;
        public int borderWidth = 1;
        public int iconColor = 0;
        public int iconBgColor = 0;
        public int radius = 14;
    }

    // In-memory runtime state for fast profile view binding
    private static boolean phoneVisible = true;
    private static int phoneColor = 0;
    private static boolean usernameVisible = true;
    private static int usernameColor = 0;
    private static boolean bioVisible = true;
    private static int bioColor = 0;
    private static boolean birthdayVisible = true;
    private static int birthdayColor = 0;
    private static boolean presenceVisible = true;
    private static boolean mediaTabsVisible = true;

    private static final List<AmeCard> customCards = new ArrayList<>();
    private static boolean isInitialized = false;

    private static SharedPreferences getPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void ensureInitialized() {
        if (isInitialized) return;
        isInitialized = true;
        SharedPreferences sp = getPrefs();
        if (sp != null && sp.contains(KEY_RAW_XML)) {
            String xml = sp.getString(KEY_RAW_XML, null);
            if (!TextUtils.isEmpty(xml)) {
                applyProfileXmlInternal(xml, false);
                return;
            }
        }
        // Load defaults from MiogramCustomUiPrefs
        phoneVisible = !MiogramCustomUiPrefs.isHideRowPhone();
        usernameVisible = !MiogramCustomUiPrefs.isHideRowUsername();
        bioVisible = !MiogramCustomUiPrefs.isHideRowBio();
        mediaTabsVisible = !MiogramCustomUiPrefs.isHideMediaTabs();
    }

    public static boolean isPhoneVisible() { ensureInitialized(); return phoneVisible; }
    public static int getPhoneColor() { ensureInitialized(); return phoneColor; }
    public static boolean isUsernameVisible() { ensureInitialized(); return usernameVisible; }
    public static int getUsernameColor() { ensureInitialized(); return usernameColor; }
    public static boolean isBioVisible() { ensureInitialized(); return bioVisible; }
    public static int getBioColor() { ensureInitialized(); return bioColor; }
    public static boolean isBirthdayVisible() { ensureInitialized(); return birthdayVisible; }
    public static int getBirthdayColor() { ensureInitialized(); return birthdayColor; }
    public static boolean isPresenceVisible() { ensureInitialized(); return presenceVisible; }
    public static boolean isMediaTabsVisible() { ensureInitialized(); return mediaTabsVisible; }
    public static List<AmeCard> getCustomCards() {
        ensureInitialized();
        return Collections.unmodifiableList(new ArrayList<>(customCards));
    }

    /**
     * Preset definition for rapid aesthetic switching
     */
    public static class AmePreset {
        public final String name;
        public final String icon;
        public final String description;
        public final String xml;

        public AmePreset(String name, String icon, String description, String xml) {
            this.name = name;
            this.icon = icon;
            this.description = description;
            this.xml = xml;
        }
    }

    public static List<AmePreset> getPresets() {
        List<AmePreset> presets = new ArrayList<>();

        // 1. Ame Cyberpunk
        presets.add(new AmePreset(
                "Ame Cyberpunk",
                "⚡",
                "Неонове бірюзово-рожеве сяйво, кібер-картки та сквіркл",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"cyberpunk\">\n\n" +
                "    <theme bg-color=\"#0D0E15\" card-bg=\"#161926\" card-radius=\"16\" />\n\n" +
                "    <banner visible=\"true\" color=\"#1F1633\" alpha=\"95\" dim=\"15\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"1\" radius=\"26\" ring-enabled=\"true\" ring-color=\"#00F0FF\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFFFFF\" glow-enabled=\"true\" glow-color=\"#00E5FF\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"⚡ Living in the neon cybergrid ໒꒱\" text-color=\"#00F0FF\" bg-color=\"#18192A\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#818CF8\" username-visible=\"true\" username-color=\"#00F0FF\" bio-visible=\"true\" bio-color=\"#E2E8F0\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"cyber_hub\" title=\"Cyberpunk Terminal\" subtitle=\"Офіційний портал оновлень та конфігів\" icon=\"code\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#1F1D36\" gradient-end=\"#3F2B96\" text-color=\"#FFFFFF\" badge=\"ONLINE\" badge-bg=\"#00E5FF\" badge-color=\"#000000\" radius=\"16\" />\n" +
                "        <card id=\"steam_deck\" title=\"Steam Game Station\" subtitle=\"Переглянути мої досягнення та ігри\" icon=\"steam\" url=\"https://steamcommunity.com\" bg-color=\"#171A21\" text-color=\"#C7D5E0\" badge=\"STEAM\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 2. Pastel Sakura Dream
        presets.add(new AmePreset(
                "Sakura Dream",
                "🌸",
                "Ніжний пастельний градієнт, лавандові відтінки та м'яке сяйво",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"sakura\">\n\n" +
                "    <theme bg-color=\"#1A1520\" card-bg=\"#241E2D\" card-radius=\"18\" />\n\n" +
                "    <banner visible=\"true\" color=\"#362238\" alpha=\"90\" dim=\"10\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"1\" radius=\"28\" ring-enabled=\"true\" ring-color=\"#FF80BF\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFE6F2\" glow-enabled=\"true\" glow-color=\"#FF66B2\" glow-radius=\"12\" />\n\n" +
                "    <thought visible=\"true\" text=\"🌸 Квітну у весняному саду ໒꒱\" text-color=\"#FFB3D9\" bg-color=\"#2B1E30\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#DDA0DD\" username-visible=\"true\" username-color=\"#FF99CC\" bio-visible=\"true\" bio-color=\"#F5EEF8\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"sakura_channel\" title=\"Мій затишний куточок ໒꒱\" subtitle=\"Естетичні фото, музика та натхнення\" icon=\"star\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#3E2745\" gradient-end=\"#6B3E69\" text-color=\"#FFFFFF\" badge=\"CUTE\" badge-bg=\"#FF80BF\" badge-color=\"#1A1520\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 3. OLED Obsidian Minimal
        presets.add(new AmePreset(
                "OLED Obsidian",
                "🖤",
                "Глибокий чорний мінімалізм, чіткі білі лінії та монохром",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"obsidian\">\n\n" +
                "    <theme bg-color=\"#000000\" card-bg=\"#111111\" card-radius=\"14\" />\n\n" +
                "    <banner visible=\"false\" color=\"#000000\" alpha=\"0\" dim=\"0\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"0\" radius=\"36\" ring-enabled=\"true\" ring-color=\"#FFFFFF\" ring-pulse=\"false\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFFFFF\" glow-enabled=\"false\" glow-color=\"#FFFFFF\" glow-radius=\"0\" />\n\n" +
                "    <thought visible=\"true\" text=\"Obsidian simplicity.\" text-color=\"#CCCCCC\" bg-color=\"#161616\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#AAAAAA\" username-visible=\"true\" username-color=\"#FFFFFF\" bio-visible=\"true\" bio-color=\"#DDDDDD\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"github_card\" title=\"GitHub Repository\" subtitle=\"Open-source проекти та розробка\" icon=\"github\" url=\"https://github.com\" bg-color=\"#161616\" border-color=\"#333333\" text-color=\"#FFFFFF\" badge=\"DEV\" radius=\"14\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 4. Amegram Royal Gold
        presets.add(new AmePreset(
                "Royal Gold",
                "👑",
                "Імперське золото, пульсуючий німб та преміум-бейджі",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"royal_gold\">\n\n" +
                "    <theme bg-color=\"#0C0B08\" card-bg=\"#191712\" card-radius=\"16\" />\n\n" +
                "    <banner visible=\"true\" color=\"#2E230B\" alpha=\"95\" dim=\"20\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"1\" radius=\"24\" ring-enabled=\"true\" ring-color=\"#FFD700\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFF2B2\" glow-enabled=\"true\" glow-color=\"#FFC800\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"👑 Golden Standard of Quality ໒꒱\" text-color=\"#FFD700\" bg-color=\"#261E0F\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#F0E68C\" username-visible=\"true\" username-color=\"#FFD700\" bio-visible=\"true\" bio-color=\"#FFF8DC\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"vip_club\" title=\"Amegram VIP Club\" subtitle=\"Ексклюзивний доступ та привілеї спільноти\" icon=\"star\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#36280B\" gradient-end=\"#6B5219\" text-color=\"#FFF6CC\" badge=\"VIP 10/10\" badge-bg=\"#FFD700\" badge-color=\"#000000\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 5. Emerald Matrix
        presets.add(new AmePreset(
                "Emerald Matrix",
                "🌿",
                "Глибокий смарагдовий матричний стиль з неоновим м'ятним сяйвом",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"matrix\">\n\n" +
                "    <theme bg-color=\"#08120B\" card-bg=\"#0E1F14\" card-radius=\"16\" />\n\n" +
                "    <banner visible=\"true\" color=\"#0E2A18\" alpha=\"95\" dim=\"15\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"1\" radius=\"22\" ring-enabled=\"true\" ring-color=\"#00FF88\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#E6FFF2\" glow-enabled=\"true\" glow-color=\"#00FF88\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"01000001 01001101 01000101 ໒꒱\" text-color=\"#00FF88\" bg-color=\"#0C2B18\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#66FFB2\" username-visible=\"true\" username-color=\"#00FF88\" bio-visible=\"true\" bio-color=\"#D1FAE5\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"matrix_source\" title=\"Matrix Terminal Source\" subtitle=\"Системні журнали та код розробки\" icon=\"code\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#0E331B\" gradient-end=\"#16542D\" text-color=\"#FFFFFF\" badge=\"ROOT\" badge-bg=\"#00FF88\" badge-color=\"#000000\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 6. Midnight Ocean Ice
        presets.add(new AmePreset(
                "Ocean Ice",
                "🌊",
                "Глибокий сапфіровий океан, крижаний градієнт та ультрамарин",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"1.0\" preset=\"ocean\">\n\n" +
                "    <theme bg-color=\"#080E1C\" card-bg=\"#0F192E\" card-radius=\"16\" />\n\n" +
                "    <banner visible=\"true\" color=\"#122240\" alpha=\"95\" dim=\"15\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"0\" radius=\"34\" ring-enabled=\"true\" ring-color=\"#38BDF8\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#F0F9FF\" glow-enabled=\"true\" glow-color=\"#0284C7\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"🌊 Whispers of the midnight abyss ໒꒱\" text-color=\"#38BDF8\" bg-color=\"#112547\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#7DD3FC\" username-visible=\"true\" username-color=\"#38BDF8\" bio-visible=\"true\" bio-color=\"#E0F2FE\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"ocean_station\" title=\"Deep Ocean Station\" subtitle=\"Стріми, треки та chill атмосфера\" icon=\"music\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#132B52\" gradient-end=\"#1E498A\" text-color=\"#FFFFFF\" badge=\"WAVE\" badge-bg=\"#38BDF8\" badge-color=\"#080E1C\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        return presets;
    }

    /**
     * Generates a fully documented XML representing current profile layout and styles,
     * equipped with an extensive Ukrainian guide in header comments.
     */
    public static String exportCurrentProfileXml() {
        ensureInitialized();
        SharedPreferences sp = getPrefs();
        if (sp != null && sp.contains(KEY_RAW_XML)) {
            String saved = sp.getString(KEY_RAW_XML, null);
            if (!TextUtils.isEmpty(saved) && saved.contains("<ame-profile")) {
                return saved;
            }
        }

        int slot = UserConfig.selectedAccount;
        String username = "";
        try {
            org.telegram.tgnet.TLRPC.User self = UserConfig.getInstance(slot).getCurrentUser();
            if (self != null && !TextUtils.isEmpty(self.username)) {
                username = "@" + self.username;
            }
        } catch (Throwable ignore) {}

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
        sb.append("<!--\n");
        sb.append("  ══════════════════════════════════════════════════════════════════════════\n");
        sb.append("  ✦ АМЕ ПРОФІЛЬ СТУДІО (AMEGRAM PROFILE STUDIO 10/10) - ПОВНИЙ ГАЙД ✦\n");
        sb.append("  ══════════════════════════════════════════════════════════════════════════\n");
        sb.append("  Цей XML-конфіг надає абсолютний контроль над кожним пікселем вашого профілю,\n");
        sb.append("  гарантуючи 100% ізоляцію: жодних змін у чатах чи глобальній темі!\n\n");
        sb.append("  Швидкі дії:\n");
        sb.append("  1. \"Застосувати код ໒꒱\" — оновлює профіль на льоту без перезапуску.\n");
        sb.append("  2. \"Опублікувати у вітку\" — відкриває офіційну тему спільноти:\n");
        sb.append("     https://t.me/dkamegram/1499, копіюючи код у ваш буфер обміну.\n\n");
        sb.append("  ─── СТРУКТУРА ТА СЕКЦІЇ ТЕГІВ ───\n");
        sb.append("  1. <theme>:\n");
        sb.append("     • bg-color=\"#HEX\"          : фоновий колір сторінки профілю\n");
        sb.append("     • card-bg=\"#HEX\"           : колір блоків і карток профілю\n");
        sb.append("     • card-radius=\"16\"         : радіус закруглення блоків (dp)\n\n");
        sb.append("  2. <banner>:\n");
        sb.append("     • visible=\"true/false\"     : відображення верхнього банера\n");
        sb.append("     • color=\"#HEX\"             : колір банера\n");
        sb.append("     • alpha=\"100\"              : непрозорість (0-100)\n");
        sb.append("     • dim=\"20\"                 : затемнення фото (0-100)\n\n");
        sb.append("  3. <avatar>:\n");
        sb.append("     • visible=\"true/false\"     : відображення аватарки\n");
        sb.append("     • shape=\"0|1|2|3|4|5|6|7\"  : форма (0:коло, 1:сквіркл, 2:квадрат,\n");
        sb.append("                                   3:шестикутник, 4:п'ятикутник, 5:зірка, 6:серце)\n");
        sb.append("     • radius=\"26\"              : радіус кутів для форми (dp)\n");
        sb.append("     • ring-enabled=\"true\"      : неонове сяюче кільце\n");
        sb.append("     • ring-color=\"#HEX\"        : колір сяйва кільця\n");
        sb.append("     • ring-pulse=\"true/false\"  : пульсація сяйва\n\n");
        sb.append("  4. <name>:\n");
        sb.append("     • color-enabled=\"true\"     : власний колір імені\n");
        sb.append("     • color=\"#HEX\"             : колір тексту імені\n");
        sb.append("     • glow-enabled=\"true\"      : неонове сяйво букв імені\n");
        sb.append("     • glow-color=\"#HEX\"        : колір сяйва\n");
        sb.append("     • glow-radius=\"14\"         : розмиття сяйва\n\n");
        sb.append("  5. <thought>:\n");
        sb.append("     • visible=\"true/false\"     : хмаринка думок/статусу\n");
        sb.append("     • text=\"Текст статусу\"     : текст цитати або емоджі\n");
        sb.append("     • text-color=\"#HEX\"        : колір тексту цитати\n");
        sb.append("     • bg-color=\"#HEX\"          : фон хмаринки\n\n");
        sb.append("  6. <info-rows>:\n");
        sb.append("     • phone-visible=\"true/false\"    : показувати телефон\n");
        sb.append("     • phone-color=\"#HEX\"            : колір телефону\n");
        sb.append("     • username-visible=\"true/false\" : показувати юзернейм\n");
        sb.append("     • username-color=\"#HEX\"         : колір юзернейму\n");
        sb.append("     • bio-visible=\"true/false\"      : показувати біографію\n");
        sb.append("     • bio-color=\"#HEX\"              : колір тексту біографії\n");
        sb.append("     • birthday-visible=\"true/false\" : показувати день народження\n\n");
        sb.append("  7. <presence>:\n");
        sb.append("     • visible=\"true/false\"     : блок Steam / Discord / активності\n\n");
        sb.append("  8. <media-tabs>:\n");
        sb.append("     • visible=\"true/false\"     : вкладки медіа та файлів під профілем\n\n");
        sb.append("  9. <custom-cards>:\n");
        sb.append("     Додавайте довільну кількість інтерактивних карток із посиланнями!\n");
        sb.append("     Атрибути <card>: title, subtitle, icon, url, bg-color,\n");
        sb.append("     gradient-start, gradient-end, text-color, badge, badge-bg, badge-color, radius.\n");
        sb.append("  ══════════════════════════════════════════════════════════════════════════\n");
        sb.append("-->\n");
        sb.append("<ame-profile version=\"1.0\" author=\"").append(escapeXml(username)).append("\">\n\n");

        // 1. Theme
        sb.append("    <theme\n");
        sb.append("        bg-color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getBgColor()))).append("\"\n");
        sb.append("        card-bg=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getBlocksColor()))).append("\"\n");
        sb.append("        card-radius=\"").append(MiogramCustomUiPrefs.getBlocksRadius()).append("\" />\n\n");

        // 2. Banner
        sb.append("    <banner\n");
        sb.append("        visible=\"").append(MiogramCustomUiPrefs.isBannerEnabled()).append("\"\n");
        sb.append("        color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getBannerColor()))).append("\"\n");
        sb.append("        alpha=\"").append(MiogramCustomUiPrefs.getBannerAlpha()).append("\"\n");
        sb.append("        dim=\"").append(MiogramCustomUiPrefs.getBannerDim()).append("\" />\n\n");

        // 3. Avatar
        sb.append("    <avatar\n");
        sb.append("        visible=\"true\"\n");
        sb.append("        shape=\"").append(MiogramCustomUiPrefs.getAvatarShape()).append("\"\n");
        sb.append("        radius=\"").append(MiogramCustomUiPrefs.getAvatarRadius()).append("\"\n");
        sb.append("        ring-enabled=\"").append(MiogramCustomUiPrefs.isAvatarRingEnabled()).append("\"\n");
        sb.append("        ring-color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getAvatarRingColor()))).append("\"\n");
        sb.append("        ring-pulse=\"").append(MiogramCustomUiPrefs.isAvatarRingPulse()).append("\" />\n\n");

        // 4. Name
        sb.append("    <name\n");
        sb.append("        color-enabled=\"").append(MiogramCustomUiPrefs.isNameColorEnabled()).append("\"\n");
        sb.append("        color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getNameColor()))).append("\"\n");
        sb.append("        glow-enabled=\"").append(MiogramCustomUiPrefs.isNameGlowEnabled()).append("\"\n");
        sb.append("        glow-color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getNameGlowColor()))).append("\"\n");
        sb.append("        glow-radius=\"").append(MiogramCustomUiPrefs.getNameGlowRadius()).append("\" />\n\n");

        // 5. Thought
        String thought = MiogramCustomUiPrefs.getThoughtText();
        sb.append("    <thought\n");
        sb.append("        visible=\"").append(!TextUtils.isEmpty(thought)).append("\"\n");
        sb.append("        text=\"").append(escapeXml(thought != null ? thought : "")).append("\"\n");
        sb.append("        text-color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getThoughtTextColor()))).append("\"\n");
        sb.append("        bg-color=\"").append(escapeXml(MiogramCustomUiPrefs.hex(MiogramCustomUiPrefs.getThoughtBgColor()))).append("\" />\n\n");

        // 6. Info rows
        sb.append("    <info-rows\n");
        sb.append("        phone-visible=\"").append(!MiogramCustomUiPrefs.isHideRowPhone()).append("\"\n");
        sb.append("        phone-color=\"").append(phoneColor != 0 ? escapeXml(MiogramCustomUiPrefs.hex(phoneColor)) : "").append("\"\n");
        sb.append("        username-visible=\"").append(!MiogramCustomUiPrefs.isHideRowUsername()).append("\"\n");
        sb.append("        username-color=\"").append(usernameColor != 0 ? escapeXml(MiogramCustomUiPrefs.hex(usernameColor)) : "").append("\"\n");
        sb.append("        bio-visible=\"").append(!MiogramCustomUiPrefs.isHideRowBio()).append("\"\n");
        sb.append("        bio-color=\"").append(bioColor != 0 ? escapeXml(MiogramCustomUiPrefs.hex(bioColor)) : "").append("\"\n");
        sb.append("        birthday-visible=\"true\" />\n\n");

        // 7. Presence
        sb.append("    <presence visible=\"true\" />\n\n");

        // 8. Media tabs
        sb.append("    <media-tabs visible=\"").append(!MiogramCustomUiPrefs.isHideMediaTabs()).append("\" />\n\n");

        // 9. Custom cards
        sb.append("    <custom-cards>\n");
        sb.append("        <card\n");
        sb.append("            id=\"community_topic\"\n");
        sb.append("            title=\"Вітка Аме Профілів ໒꒱\"\n");
        sb.append("            subtitle=\"Переглядайте та завантажуйте конфіги інших користувачів\"\n");
        sb.append("            icon=\"star\"\n");
        sb.append("            url=\"https://t.me/dkamegram/1499\"\n");
        sb.append("            gradient-start=\"#1E2235\"\n");
        sb.append("            gradient-end=\"#333A56\"\n");
        sb.append("            text-color=\"#FFFFFF\"\n");
        sb.append("            badge=\"10/10\"\n");
        sb.append("            badge-bg=\"#6C63FF\"\n");
        sb.append("            badge-color=\"#FFFFFF\"\n");
        sb.append("            radius=\"16\" />\n");
        sb.append("    </custom-cards>\n\n");

        sb.append("</ame-profile>\n");
        return sb.toString();
    }

    /**
     * Parses and applies the Ame Profile XML into runtime state and preferences.
     */
    public static boolean applyProfileXml(String xml) {
        return applyProfileXmlInternal(xml, true);
    }

    private static synchronized boolean applyProfileXmlInternal(String xml, boolean saveToStorage) {
        if (TextUtils.isEmpty(xml)) return false;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            List<AmeCard> parsedCards = new ArrayList<>();

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName().toLowerCase();
                    switch (tag) {
                        case "theme":
                            parseTheme(parser);
                            break;
                        case "banner":
                            parseBanner(parser);
                            break;
                        case "avatar":
                            parseAvatar(parser);
                            break;
                        case "name":
                            parseName(parser);
                            break;
                        case "thought":
                            parseThought(parser);
                            break;
                        case "info-rows":
                        case "visibility":
                            parseInfoRows(parser);
                            break;
                        case "presence":
                            parsePresence(parser);
                            break;
                        case "media-tabs":
                            parseMediaTabs(parser);
                            break;
                        case "card":
                            AmeCard card = parseCard(parser);
                            if (card != null) parsedCards.add(card);
                            break;
                    }
                }
                eventType = parser.next();
            }

            customCards.clear();
            customCards.addAll(parsedCards);

            if (saveToStorage) {
                SharedPreferences sp = getPrefs();
                if (sp != null) {
                    sp.edit().putString(KEY_RAW_XML, xml).apply();
                }
            }

            isInitialized = true;
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                NotificationCenter.getInstance(i).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            }
            return true;
        } catch (Throwable t) {
            FileLog.e("AmeProfileEngine: Failed to parse XML", t);
            return false;
        }
    }

    private static void parseTheme(XmlPullParser p) {
        String bg = p.getAttributeValue(null, "bg-color");
        if (bg != null) {
            MiogramCustomUiPrefs.setBgColor(MiogramCustomUiPrefs.parseColor(bg, 0xFF14151F));
            MiogramCustomUiPrefs.setBgEnabled(true);
        }

        String cardBg = p.getAttributeValue(null, "card-bg");
        if (cardBg != null) {
            MiogramCustomUiPrefs.setBlocksColor(MiogramCustomUiPrefs.parseColor(cardBg, 0xFF1C242F));
            MiogramCustomUiPrefs.setBlocksColorEnabled(true);
        }

        String radius = p.getAttributeValue(null, "card-radius");
        if (radius != null) {
            try {
                MiogramCustomUiPrefs.setBlocksRadius(Integer.parseInt(radius));
                MiogramCustomUiPrefs.setBlocksRadiusEnabled(true);
            } catch (Throwable ignore) {}
        }
    }

    private static void parseBanner(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        if (visible == null) visible = p.getAttributeValue(null, "enabled");
        if (visible != null) MiogramCustomUiPrefs.setBannerEnabled(Boolean.parseBoolean(visible));

        String color = p.getAttributeValue(null, "color");
        if (color != null) {
            MiogramCustomUiPrefs.setBannerColor(MiogramCustomUiPrefs.parseColor(color, 0xFF1C242F));
        }

        String alpha = p.getAttributeValue(null, "alpha");
        if (alpha != null) {
            try { MiogramCustomUiPrefs.setBannerAlpha(Integer.parseInt(alpha)); } catch (Throwable ignore) {}
        }

        String dim = p.getAttributeValue(null, "dim");
        if (dim != null) {
            try { MiogramCustomUiPrefs.setBannerDim(Integer.parseInt(dim)); } catch (Throwable ignore) {}
        }
    }

    private static void parseAvatar(XmlPullParser p) {
        String shape = p.getAttributeValue(null, "shape");
        if (shape != null) {
            try { MiogramCustomUiPrefs.setAvatarShape(Integer.parseInt(shape)); } catch (Throwable ignore) {}
        }

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { MiogramCustomUiPrefs.setAvatarRadius(Integer.parseInt(radius)); } catch (Throwable ignore) {}
        }

        String ring = p.getAttributeValue(null, "ring-enabled");
        if (ring != null) MiogramCustomUiPrefs.setAvatarRingEnabled(Boolean.parseBoolean(ring));

        String ringColor = p.getAttributeValue(null, "ring-color");
        if (ringColor != null) {
            MiogramCustomUiPrefs.setAvatarRingColor(MiogramCustomUiPrefs.parseColor(ringColor, 0xFF00E5FF));
        }

        String ringPulse = p.getAttributeValue(null, "ring-pulse");
        if (ringPulse != null) MiogramCustomUiPrefs.setAvatarRingPulse(Boolean.parseBoolean(ringPulse));
    }

    private static void parseName(XmlPullParser p) {
        String colorEnabled = p.getAttributeValue(null, "color-enabled");
        if (colorEnabled != null) MiogramCustomUiPrefs.setNameColorEnabled(Boolean.parseBoolean(colorEnabled));

        String color = p.getAttributeValue(null, "color");
        if (color != null) {
            MiogramCustomUiPrefs.setNameColor(MiogramCustomUiPrefs.parseColor(color, 0xFFFFFFFF));
        }

        String glowEnabled = p.getAttributeValue(null, "glow-enabled");
        if (glowEnabled != null) MiogramCustomUiPrefs.setNameGlowEnabled(Boolean.parseBoolean(glowEnabled));

        String glowColor = p.getAttributeValue(null, "glow-color");
        if (glowColor != null) {
            MiogramCustomUiPrefs.setNameGlowColor(MiogramCustomUiPrefs.parseColor(glowColor, 0xFF2A87FF));
        }

        String glowRadius = p.getAttributeValue(null, "glow-radius");
        if (glowRadius != null) {
            try { MiogramCustomUiPrefs.setNameGlowRadius(Integer.parseInt(glowRadius)); } catch (Throwable ignore) {}
        }
    }

    private static void parseThought(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        String text = p.getAttributeValue(null, "text");
        if ("false".equalsIgnoreCase(visible)) {
            MiogramCustomUiPrefs.setThoughtText("");
        } else if (text != null) {
            MiogramCustomUiPrefs.setThoughtText(text);
        }

        String textColor = p.getAttributeValue(null, "text-color");
        if (textColor != null) {
            MiogramCustomUiPrefs.setThoughtTextColor(MiogramCustomUiPrefs.parseColor(textColor, 0xFFFFFFFF));
        }

        String bgColor = p.getAttributeValue(null, "bg-color");
        if (bgColor != null) {
            MiogramCustomUiPrefs.setThoughtBgColor(MiogramCustomUiPrefs.parseColor(bgColor, 0xFF1C242F));
        }
    }

    private static void parseInfoRows(XmlPullParser p) {
        String phoneVis = p.getAttributeValue(null, "phone-visible");
        if (phoneVis != null) phoneVisible = Boolean.parseBoolean(phoneVis);
        String phoneCol = p.getAttributeValue(null, "phone-color");
        if (!TextUtils.isEmpty(phoneCol)) {
            phoneColor = MiogramCustomUiPrefs.parseColor(phoneCol, 0);
        } else {
            phoneColor = 0;
        }

        String usernameVis = p.getAttributeValue(null, "username-visible");
        if (usernameVis != null) usernameVisible = Boolean.parseBoolean(usernameVis);
        String usernameCol = p.getAttributeValue(null, "username-color");
        if (!TextUtils.isEmpty(usernameCol)) {
            usernameColor = MiogramCustomUiPrefs.parseColor(usernameCol, 0);
        } else {
            usernameColor = 0;
        }

        String bioVis = p.getAttributeValue(null, "bio-visible");
        if (bioVis != null) bioVisible = Boolean.parseBoolean(bioVis);
        String bioCol = p.getAttributeValue(null, "bio-color");
        if (!TextUtils.isEmpty(bioCol)) {
            bioColor = MiogramCustomUiPrefs.parseColor(bioCol, 0);
        } else {
            bioColor = 0;
        }

        String birthdayVis = p.getAttributeValue(null, "birthday-visible");
        if (birthdayVis != null) birthdayVisible = Boolean.parseBoolean(birthdayVis);
        String birthdayCol = p.getAttributeValue(null, "birthday-color");
        if (!TextUtils.isEmpty(birthdayCol)) {
            birthdayColor = MiogramCustomUiPrefs.parseColor(birthdayCol, 0);
        } else {
            birthdayColor = 0;
        }
    }

    private static void parsePresence(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        if (visible != null) presenceVisible = Boolean.parseBoolean(visible);
    }

    private static void parseMediaTabs(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        if (visible != null) mediaTabsVisible = Boolean.parseBoolean(visible);
    }

    private static AmeCard parseCard(XmlPullParser p) {
        AmeCard card = new AmeCard();
        card.id = p.getAttributeValue(null, "id");
        card.title = p.getAttributeValue(null, "title");
        card.subtitle = p.getAttributeValue(null, "subtitle");
        card.icon = p.getAttributeValue(null, "icon");
        card.url = p.getAttributeValue(null, "url");

        String bg = p.getAttributeValue(null, "bg-color");
        if (bg != null) card.bgColor = MiogramCustomUiPrefs.parseColor(bg, 0);

        String gradStart = p.getAttributeValue(null, "gradient-start");
        if (gradStart != null) card.gradientColor1 = MiogramCustomUiPrefs.parseColor(gradStart, 0);

        String gradEnd = p.getAttributeValue(null, "gradient-end");
        if (gradEnd != null) card.gradientColor2 = MiogramCustomUiPrefs.parseColor(gradEnd, 0);

        String text = p.getAttributeValue(null, "text-color");
        if (text != null) card.textColor = MiogramCustomUiPrefs.parseColor(text, 0);

        String subText = p.getAttributeValue(null, "subtitle-color");
        if (subText != null) card.subtitleColor = MiogramCustomUiPrefs.parseColor(subText, 0);

        card.badge = p.getAttributeValue(null, "badge");

        String badgeBg = p.getAttributeValue(null, "badge-bg");
        if (badgeBg != null) card.badgeBgColor = MiogramCustomUiPrefs.parseColor(badgeBg, 0);

        String badgeCol = p.getAttributeValue(null, "badge-color");
        if (badgeCol != null) card.badgeTextColor = MiogramCustomUiPrefs.parseColor(badgeCol, 0);

        String border = p.getAttributeValue(null, "border-color");
        if (border != null) card.borderColor = MiogramCustomUiPrefs.parseColor(border, 0);

        String borderW = p.getAttributeValue(null, "border-width");
        if (borderW != null) {
            try { card.borderWidth = Integer.parseInt(borderW); } catch (Throwable ignore) {}
        }

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { card.radius = Integer.parseInt(radius); } catch (Throwable ignore) {}
        }
        return !TextUtils.isEmpty(card.title) ? card : null;
    }

    public static void resetToDefaults() {
        SharedPreferences sp = getPrefs();
        if (sp != null) sp.edit().remove(KEY_RAW_XML).apply();
        phoneVisible = true;
        phoneColor = 0;
        usernameVisible = true;
        usernameColor = 0;
        bioVisible = true;
        bioColor = 0;
        birthdayVisible = true;
        birthdayColor = 0;
        presenceVisible = true;
        mediaTabsVisible = true;
        customCards.clear();
        isInitialized = false;
        ensureInitialized();
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
            NotificationCenter.getInstance(i).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
        }
    }

    public static void shareToCommunity(Context context, String xml) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context != null) {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Ame Profile", xml));
            }
            Toast.makeText(context, MiogramLocale.get(
                    "XML Аме профілю скопійовано! Відкриваємо вітку спільноти...",
                    "XML Аме профиля скопирован! Открываем ветку сообщества...",
                    "Ame Profile XML copied! Opening community topic..."
            ), Toast.LENGTH_LONG).show();
            Browser.openUrl(context, COMMUNITY_TOPIC_URL);
        }
    }

    private static String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
