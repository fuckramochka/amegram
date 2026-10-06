package com.amegram.mods.ame;

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



/**
 * ✦ AME PROFILE ENGINE (АМЕ ПРОФІЛЬ) — 10/10 MASTER ENGINE ✦
 * The pinnacle customization engine for Amegram profile aesthetics:
 * - 100% strict profile tab isolation: Zero global chat/bubble leaks.
 * - Free layout positioning: Avatar on the right/center/left, large sizing, offset.
 * - Transforms & Tilt: Rotation angles on avatar, name, and modular elements.
 * - Gradient Physics: 0°-360° gradient angles and animated high-speed neon sweeps.
 * - Dynamic Media: Local photo/video binding (src="local:photo1"), remote URLs, GIFs, looping videos.
 * - Arbitrary Modular DOM: Add, duplicate, reorder, or delete cards, text blocks, and media.
 */
public class AmeEngine {

    public static final String COMMUNITY_TOPIC_URL = "https://t.me/dkamegram/1499";
    private static final String PREFS_NAME = "amegram_profile_prefs";
    private static final String KEY_RAW_XML = "raw_profile_xml";

    public static class AmeCard {
        public String id = "";
        public String type = "card"; // "card", "text", "image", "badge"
        public String title = "";
        public String subtitle = "";
        public String icon = "link";
        public String url = "";
        public String src = ""; // local:xxx or http(s)://
        public int bgColor = 0;
        public int gradientColor1 = 0;
        public int gradientColor2 = 0;
        public int gradientColor3 = 0;
        public float gradientAngle = 0f;
        public float gradientSpeed = 0f;
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
        public float rotation = 0f; // tilt angle in degrees
        public int textSize = 15;
        public String align = "left"; // left, center, right
    }

    // --- Runtime Profile Layout State ---
    private static String avatarAlign = "left"; // "left", "center", "right"
    private static int avatarSize = 100; // dp
    private static float avatarRotation = 0f; // degrees
    private static int avatarOffsetX = 0; // dp
    private static int avatarOffsetY = 0; // dp

    private static String nameAlign = "left"; // "left", "center", "right"
    private static float nameRotation = 0f; // degrees
    private static int nameSize = 22; // sp

    private static String thoughtAlign = "left";
    private static float thoughtRotation = 0f;

    // Banner Media state
    private static boolean bannerVisible = true;
    private static String bannerType = "color"; // "color", "image", "gif", "video"
    private static String bannerSrc = ""; // local:xxx or http(s)://
    private static boolean bannerSound = false;
    private static boolean bannerLoop = true;
    private static int bannerHeight = 0; // dp (0 = default automatic)
    private static int bannerGradientColor1 = 0;
    private static int bannerGradientColor2 = 0;
    private static int bannerGradientColor3 = 0;
    private static float bannerGradientAngle = 0f;
    private static float bannerGradientSpeed = 0f;

    // Info rows visibility & colors
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
        if (!isModuleEnabled()) {
            applyVanillaDefaults();
            return;
        }
        SharedPreferences sp = getPrefs();
        if (sp != null && sp.contains(KEY_RAW_XML)) {
            String xml = sp.getString(KEY_RAW_XML, null);
            if (!TextUtils.isEmpty(xml)) {
                applyProfileXmlInternal(xml, false);
                return;
            }
        }
        // Defaults
        applyVanillaDefaults();
    }

    /** Module OFF (Amegram hub toggle) = stock Telegram profile, instantly. */
    private static void applyVanillaDefaults() {
        avatarAlign = "left";
        avatarSize = 100;
        avatarRotation = 0f;
        avatarOffsetX = 0;
        avatarOffsetY = 0;
        nameAlign = "left";
        nameRotation = 0f;
        nameSize = 22;
        thoughtAlign = "left";
        thoughtRotation = 0f;
        bannerVisible = false;
        bannerType = "color";
        bannerSrc = "";
        bannerSound = false;
        bannerLoop = true;
        bannerHeight = 0;
        bannerGradientColor1 = 0;
        bannerGradientColor2 = 0;
        bannerGradientColor3 = 0;
        bannerGradientAngle = 0f;
        bannerGradientSpeed = 0f;
        phoneVisible = !UiPrefs.isHideRowPhone();
        phoneColor = 0;
        usernameVisible = !UiPrefs.isHideRowUsername();
        usernameColor = 0;
        bioVisible = !UiPrefs.isHideRowBio();
        bioColor = 0;
        birthdayVisible = true;
        birthdayColor = 0;
        presenceVisible = false;
        mediaTabsVisible = !UiPrefs.isHideMediaTabs();
        customCards.clear();
    }

    /**
     * Reads the unified module flag by prefs FILE NAME (no imports, vanilla-safe).
     * Default true = current behavior preserved for existing users.
     */
    public static boolean isModuleEnabled() {
        try {
            return app.amegram.hot.HotModulesManager.isModuleEnabled("ame");
        } catch (Throwable ignore) {
        }
        return true;
    }

    // Getters for Layout & Transforms
    public static String getAvatarAlign() { ensureInitialized(); return avatarAlign; }
    public static int getAvatarSize() { ensureInitialized(); return avatarSize; }
    public static float getAvatarScaleMultiplier() { ensureInitialized(); return avatarSize > 0 ? (avatarSize / 100f) : 1f; }
    public static float getAvatarRotation() { ensureInitialized(); return avatarRotation; }
    public static int getAvatarOffsetX() { ensureInitialized(); return avatarOffsetX; }
    public static int getAvatarOffsetY() { ensureInitialized(); return avatarOffsetY; }

    public static String getNameAlign() { ensureInitialized(); return nameAlign; }
    public static float getNameRotation() { ensureInitialized(); return nameRotation; }
    public static int getNameSize() { ensureInitialized(); return nameSize; }

    public static String getThoughtAlign() { ensureInitialized(); return thoughtAlign; }
    public static float getThoughtRotation() { ensureInitialized(); return thoughtRotation; }

    // Banner Getters
    public static boolean isBannerVisible() { ensureInitialized(); return bannerVisible && UiPrefs.isBannerEnabled(); }
    public static String getBannerType() { ensureInitialized(); return bannerType; }
    public static String getBannerSrc() { ensureInitialized(); return bannerSrc; }
    public static boolean isBannerSound() { ensureInitialized(); return bannerSound; }
    public static boolean isBannerLoop() { ensureInitialized(); return bannerLoop; }
    public static int getBannerHeight() { ensureInitialized(); return bannerHeight; }
    public static int getBannerGradientColor1() { ensureInitialized(); return bannerGradientColor1; }
    public static int getBannerGradientColor2() { ensureInitialized(); return bannerGradientColor2; }
    public static int getBannerGradientColor3() { ensureInitialized(); return bannerGradientColor3; }
    public static float getBannerGradientAngle() { ensureInitialized(); return bannerGradientAngle; }
    public static float getBannerGradientSpeed() { ensureInitialized(); return bannerGradientSpeed; }

    // Info rows Getters
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

        // 1. Ame Cyberpunk: Avatar on the Right, Animated Gradient, Neon Tilt
        presets.add(new AmePreset(
                "Cyber Right-Align",
                "⚡",
                "Велика аватарка справа, нік зліва, живий анімований градієнт та нахил",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"2.0\" preset=\"cyber_right\">\n\n" +
                "    <theme bg-color=\"#0A0B10\" card-bg=\"#141624\" card-radius=\"18\" />\n\n" +
                "    <layout avatar-align=\"right\" avatar-size=\"115\" avatar-rotation=\"10\" name-align=\"left\" />\n\n" +
                "    <banner visible=\"true\" type=\"color\" color=\"#1B1530\" gradient-start=\"#16082F\" gradient-end=\"#38126B\" gradient-angle=\"45\" gradient-speed=\"1.8\" alpha=\"95\" dim=\"15\" />\n\n" +
                "    <avatar visible=\"true\" align=\"right\" size=\"115\" rotation=\"10\" shape=\"1\" radius=\"28\" ring-enabled=\"true\" ring-color=\"#00F0FF\" ring-pulse=\"true\" ring-width=\"3\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFFFFF\" glow-enabled=\"true\" glow-color=\"#00E5FF\" glow-radius=\"16\" align=\"left\" />\n\n" +
                "    <thought visible=\"true\" text=\"⚡ Cyberpunk Layout: Right Avatar ໒꒱\" text-color=\"#00F0FF\" bg-color=\"#18192A\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#818CF8\" username-visible=\"true\" username-color=\"#00F0FF\" bio-visible=\"true\" bio-color=\"#E2E8F0\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"cyber_terminal\" title=\"Cyber Terminal Station\" subtitle=\"Гіпершвидкісний потік оновлень\" icon=\"code\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#1F1D36\" gradient-middle=\"#3F2B96\" gradient-end=\"#00F0FF\" gradient-angle=\"45\" gradient-speed=\"2.0\" text-color=\"#FFFFFF\" badge=\"ONLINE\" badge-bg=\"#00E5FF\" badge-color=\"#000000\" radius=\"16\" rotation=\"-1\" />\n" +
                "        <card id=\"steam_hub\" title=\"Steam Game Matrix\" subtitle=\"Досягнення та ігровий профіль\" icon=\"steam\" url=\"https://steamcommunity.com\" bg-color=\"#161926\" border-color=\"#00F0FF\" border-width=\"1\" text-color=\"#C7D5E0\" badge=\"STEAM\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 2. Sakura Centered Dream: Soft angled gradients and local photo banner support
        presets.add(new AmePreset(
                "Sakura Dream",
                "🌸",
                "Ніжний пастельний градієнт 135°, лавандове сяйво та витончена естетика",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"2.0\" preset=\"sakura\">\n\n" +
                "    <theme bg-color=\"#1A1520\" card-bg=\"#241E2D\" card-radius=\"18\" />\n\n" +
                "    <layout avatar-align=\"left\" avatar-size=\"105\" avatar-rotation=\"-5\" name-align=\"left\" />\n\n" +
                "    <banner visible=\"true\" type=\"color\" color=\"#362238\" gradient-start=\"#3E2745\" gradient-end=\"#6B3E69\" gradient-angle=\"135\" gradient-speed=\"1.0\" alpha=\"92\" dim=\"10\" />\n\n" +
                "    <avatar visible=\"true\" align=\"left\" size=\"105\" rotation=\"-5\" shape=\"1\" radius=\"28\" ring-enabled=\"true\" ring-color=\"#FF80BF\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFE6F2\" glow-enabled=\"true\" glow-color=\"#FF66B2\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"🌸 Квітну у весняному саду ໒꒱\" text-color=\"#FFB3D9\" bg-color=\"#2B1E30\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#DDA0DD\" username-visible=\"true\" username-color=\"#FF99CC\" bio-visible=\"true\" bio-color=\"#F5EEF8\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"sakura_corner\" title=\"Мій затишний куточок ໒꒱\" subtitle=\"Естетичні фото, музика та натхнення\" icon=\"star\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#4A2E50\" gradient-end=\"#784475\" gradient-angle=\"60\" gradient-speed=\"1.2\" text-color=\"#FFFFFF\" badge=\"CUTE\" badge-bg=\"#FF80BF\" badge-color=\"#1A1520\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 3. OLED Obsidian Minimal
        presets.add(new AmePreset(
                "OLED Obsidian",
                "🖤",
                "Глибокий чорний мінімалізм, чіткі контури та монохром",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"2.0\" preset=\"obsidian\">\n\n" +
                "    <theme bg-color=\"#000000\" card-bg=\"#111111\" card-radius=\"14\" />\n\n" +
                "    <layout avatar-align=\"left\" avatar-size=\"100\" avatar-rotation=\"0\" />\n\n" +
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
                "Імперське золото, велика аватарка, 45° градієнти та розкіш",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"2.0\" preset=\"royal_gold\">\n\n" +
                "    <theme bg-color=\"#0C0B08\" card-bg=\"#191712\" card-radius=\"16\" />\n\n" +
                "    <layout avatar-align=\"right\" avatar-size=\"120\" avatar-rotation=\"8\" name-align=\"left\" />\n\n" +
                "    <banner visible=\"true\" type=\"color\" color=\"#2E230B\" gradient-start=\"#36280B\" gradient-end=\"#6B5219\" gradient-angle=\"45\" gradient-speed=\"1.4\" alpha=\"95\" dim=\"20\" />\n\n" +
                "    <avatar visible=\"true\" align=\"right\" size=\"120\" rotation=\"8\" shape=\"1\" radius=\"26\" ring-enabled=\"true\" ring-color=\"#FFD700\" ring-pulse=\"true\" ring-width=\"3\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#FFF2B2\" glow-enabled=\"true\" glow-color=\"#FFC800\" glow-radius=\"16\" />\n\n" +
                "    <thought visible=\"true\" text=\"👑 Golden Standard of Quality ໒꒱\" text-color=\"#FFD700\" bg-color=\"#261E0F\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#F0E68C\" username-visible=\"true\" username-color=\"#FFD700\" bio-visible=\"true\" bio-color=\"#FFF8DC\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"vip_club\" title=\"Amegram VIP Club\" subtitle=\"Ексклюзивний доступ та привілеї спільноти\" icon=\"star\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#3D2D0C\" gradient-middle=\"#73591B\" gradient-end=\"#FFD700\" gradient-angle=\"45\" gradient-speed=\"2.2\" text-color=\"#FFF6CC\" badge=\"VIP 10/10\" badge-bg=\"#FFD700\" badge-color=\"#000000\" radius=\"16\" rotation=\"1\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        // 5. Emerald Matrix
        presets.add(new AmePreset(
                "Emerald Matrix",
                "🌿",
                "Смарагдовий неоновий термінал, 90° вертикальний градієнт",
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<ame-profile version=\"2.0\" preset=\"matrix\">\n\n" +
                "    <theme bg-color=\"#08120B\" card-bg=\"#0E1F14\" card-radius=\"16\" />\n\n" +
                "    <layout avatar-align=\"left\" avatar-size=\"100\" avatar-rotation=\"0\" />\n\n" +
                "    <banner visible=\"true\" type=\"color\" color=\"#0E2A18\" gradient-start=\"#092113\" gradient-end=\"#144726\" gradient-angle=\"90\" gradient-speed=\"1.5\" alpha=\"95\" dim=\"15\" />\n\n" +
                "    <avatar visible=\"true\" shape=\"1\" radius=\"22\" ring-enabled=\"true\" ring-color=\"#00FF88\" ring-pulse=\"true\" />\n\n" +
                "    <name color-enabled=\"true\" color=\"#E6FFF2\" glow-enabled=\"true\" glow-color=\"#00FF88\" glow-radius=\"14\" />\n\n" +
                "    <thought visible=\"true\" text=\"01000001 01001101 01000101 ໒꒱\" text-color=\"#00FF88\" bg-color=\"#0C2B18\" />\n\n" +
                "    <info-rows phone-visible=\"true\" phone-color=\"#66FFB2\" username-visible=\"true\" username-color=\"#00FF88\" bio-visible=\"true\" bio-color=\"#D1FAE5\" birthday-visible=\"true\" />\n\n" +
                "    <presence visible=\"true\" />\n\n" +
                "    <media-tabs visible=\"true\" />\n\n" +
                "    <custom-cards>\n" +
                "        <card id=\"matrix_source\" title=\"Matrix Terminal Source\" subtitle=\"Системні журнали та код розробки\" icon=\"code\" url=\"https://t.me/dkamegram/1499\" gradient-start=\"#0E331B\" gradient-end=\"#16542D\" gradient-angle=\"45\" gradient-speed=\"1.5\" text-color=\"#FFFFFF\" badge=\"ROOT\" badge-bg=\"#00FF88\" badge-color=\"#000000\" radius=\"16\" />\n" +
                "    </custom-cards>\n\n" +
                "</ame-profile>"
        ));

        return presets;
    }

    /**
     * Generates a fully documented XML representing current profile layout and styles,
     * equipped with an exhaustive Ukrainian guide in header comments.
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
        sb.append("  ════════════════════════════════════════════════════════════════════════════════════\n");
        sb.append("  ✦ АМЕ ПРОФІЛЬ СТУДІО (AME PROFILE STUDIO 10/10) — ЕТАЛОННИЙ ГАЙД ✦\n");
        sb.append("  ════════════════════════════════════════════════════════════════════════════════════\n");
        sb.append("  Цей файл надає абсолютний контроль над розміщенням, формами, медіа та анімаціями!\n");
        sb.append("  100% сувора ізоляція: жодного впливу на чати, бульбашки чи інші вікна.\n\n");
        sb.append("  ─── 1. РОЗМІЩЕННЯ ТА НАХИЛ (LAYOUT & TRANSFORMS) ───\n");
        sb.append("  • avatar-align=\"right|center|left\" : розміщення аватарки (наприклад, велика аватарка\n");
        sb.append("                                       справа, а нік/статус зліва чи по центру!)\n");
        sb.append("  • avatar-size=\"120\"                 : розмір аватарки в dp (80..160)\n");
        sb.append("  • avatar-rotation=\"15\"              : градус нахилу/повороту аватарки (-45°..+45°)\n");
        sb.append("  • name-align=\"left|center|right\"    : вирівнювання імені\n");
        sb.append("  • rotation=\"-5\"                      : нахил будь-якої картки, тексту чи блоку\n\n");
        sb.append("  ─── 2. ГРАДІЄНТИ ТА ШВИДКІСТЬ АНІМАЦІЇ ───\n");
        sb.append("  • gradient-start=\"#HEX\", gradient-middle=\"#HEX\", gradient-end=\"#HEX\"\n");
        sb.append("  • gradient-angle=\"45\"               : кут нахилу градієнта від 0 до 360 градусів\n");
        sb.append("  • gradient-speed=\"2.0\"              : швидкість живого анімованого переливу\n");
        sb.append("                                       (0 = статичний, 1.0..3.0 = плавний хід)\n\n");
        sb.append("  ─── 3. МЕДІА: ФОТО З ІНТЕРНЕТУ, ЛОКАЛЬНІ ФОТО, ГІФКИ, ВІДЕО ───\n");
        sb.append("  • src=\"https://example.com/art.jpg\" : пряме посилання на фото або гіфку\n");
        sb.append("  • src=\"local:photo1\"                 : локальне фото з галереї! При збереженні або\n");
        sb.append("                                       натисканні «Вибрати фото», програма попросить\n");
        sb.append("                                       вибрати файл і надійно збереже його локально!\n");
        sb.append("  • type=\"color|image|gif|video\"      : тип фону або банера\n");
        sb.append("  • sound=\"true|false\"                : увімкнути чи вимкнути звук для відео-банера\n\n");
        sb.append("  ─── 4. ДОВІЛЬНІ ЕЛЕМЕНТИ (ДОДАВАЙТЕ, ДУБЛЮЙТЕ, ВИДАЛЯЙТЕ) ───\n");
        sb.append("  Ви можете створювати будь-яку кількість <card>, <text>, <image> елементів,\n");
        sb.append("  міняти їх порядок, видаляти непотрібні або копіювати нові.\n");
        sb.append("  ════════════════════════════════════════════════════════════════════════════════════\n");
        sb.append("-->\n");
        sb.append("<ame-profile version=\"2.0\" author=\"").append(escapeXml(username)).append("\">\n\n");

        // 1. Theme
        sb.append("    <theme\n");
        sb.append("        bg-color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getBgColor()))).append("\"\n");
        sb.append("        card-bg=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getBlocksColor()))).append("\"\n");
        sb.append("        card-radius=\"").append(UiPrefs.getBlocksRadius()).append("\" />\n\n");

        // 2. Layout
        sb.append("    <layout\n");
        sb.append("        avatar-align=\"").append(escapeXml(avatarAlign)).append("\"\n");
        sb.append("        avatar-size=\"").append(avatarSize).append("\"\n");
        sb.append("        avatar-rotation=\"").append(avatarRotation).append("\"\n");
        sb.append("        name-align=\"").append(escapeXml(nameAlign)).append("\" />\n\n");

        // 3. Banner
        sb.append("    <banner\n");
        sb.append("        visible=\"").append(bannerVisible).append("\"\n");
        sb.append("        type=\"").append(escapeXml(bannerType)).append("\"\n");
        if (!TextUtils.isEmpty(bannerSrc)) {
            sb.append("        src=\"").append(escapeXml(bannerSrc)).append("\"\n");
        }
        sb.append("        color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getBannerColor()))).append("\"\n");
        if (bannerGradientColor1 != 0 && bannerGradientColor2 != 0) {
            sb.append("        gradient-start=\"").append(escapeXml(UiPrefs.hex(bannerGradientColor1))).append("\"\n");
            sb.append("        gradient-end=\"").append(escapeXml(UiPrefs.hex(bannerGradientColor2))).append("\"\n");
            sb.append("        gradient-angle=\"").append(bannerGradientAngle).append("\"\n");
            sb.append("        gradient-speed=\"").append(bannerGradientSpeed).append("\"\n");
        }
        sb.append("        alpha=\"").append(UiPrefs.getBannerAlpha()).append("\"\n");
        sb.append("        dim=\"").append(UiPrefs.getBannerDim()).append("\" />\n\n");

        // 4. Avatar
        sb.append("    <avatar\n");
        sb.append("        visible=\"true\"\n");
        sb.append("        align=\"").append(escapeXml(avatarAlign)).append("\"\n");
        sb.append("        size=\"").append(avatarSize).append("\"\n");
        sb.append("        rotation=\"").append(avatarRotation).append("\"\n");
        sb.append("        shape=\"").append(UiPrefs.getAvatarShape()).append("\"\n");
        sb.append("        radius=\"").append(UiPrefs.getAvatarRadius()).append("\"\n");
        sb.append("        ring-enabled=\"").append(UiPrefs.isAvatarRingEnabled()).append("\"\n");
        sb.append("        ring-color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getAvatarRingColor()))).append("\"\n");
        sb.append("        ring-pulse=\"").append(UiPrefs.isAvatarRingPulse()).append("\" />\n\n");

        // 5. Name
        sb.append("    <name\n");
        sb.append("        color-enabled=\"").append(UiPrefs.isNameColorEnabled()).append("\"\n");
        sb.append("        color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getNameColor()))).append("\"\n");
        sb.append("        glow-enabled=\"").append(UiPrefs.isNameGlowEnabled()).append("\"\n");
        sb.append("        glow-color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getNameGlowColor()))).append("\"\n");
        sb.append("        glow-radius=\"").append(UiPrefs.getNameGlowRadius()).append("\"\n");
        sb.append("        align=\"").append(escapeXml(nameAlign)).append("\"\n");
        sb.append("        rotation=\"").append(nameRotation).append("\" />\n\n");

        // 6. Thought
        String thought = UiPrefs.getThoughtText();
        sb.append("    <thought\n");
        sb.append("        visible=\"").append(!TextUtils.isEmpty(thought)).append("\"\n");
        sb.append("        text=\"").append(escapeXml(thought != null ? thought : "")).append("\"\n");
        sb.append("        text-color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getThoughtTextColor()))).append("\"\n");
        sb.append("        bg-color=\"").append(escapeXml(UiPrefs.hex(UiPrefs.getThoughtBgColor()))).append("\" />\n\n");

        // 7. Info rows
        sb.append("    <info-rows\n");
        sb.append("        phone-visible=\"").append(!UiPrefs.isHideRowPhone()).append("\"\n");
        sb.append("        phone-color=\"").append(phoneColor != 0 ? escapeXml(UiPrefs.hex(phoneColor)) : "").append("\"\n");
        sb.append("        username-visible=\"").append(!UiPrefs.isHideRowUsername()).append("\"\n");
        sb.append("        username-color=\"").append(usernameColor != 0 ? escapeXml(UiPrefs.hex(usernameColor)) : "").append("\"\n");
        sb.append("        bio-visible=\"").append(!UiPrefs.isHideRowBio()).append("\"\n");
        sb.append("        bio-color=\"").append(bioColor != 0 ? escapeXml(UiPrefs.hex(bioColor)) : "").append("\"\n");
        sb.append("        birthday-visible=\"true\" />\n\n");

        // 8. Presence
        sb.append("    <presence visible=\"true\" />\n\n");

        // 9. Media tabs
        sb.append("    <media-tabs visible=\"").append(!UiPrefs.isHideMediaTabs()).append("\" />\n\n");

        // 10. Custom Cards
        sb.append("    <custom-cards>\n");
        if (customCards.isEmpty()) {
            sb.append("        <card\n");
            sb.append("            id=\"community_topic\"\n");
            sb.append("            title=\"Вітка Аме Профілів ໒꒱\"\n");
            sb.append("            subtitle=\"Переглядайте та завантажуйте конфіги інших користувачів\"\n");
            sb.append("            icon=\"star\"\n");
            sb.append("            url=\"https://t.me/dkamegram/1499\"\n");
            sb.append("            gradient-start=\"#1E2235\"\n");
            sb.append("            gradient-end=\"#333A56\"\n");
            sb.append("            gradient-angle=\"45\"\n");
            sb.append("            gradient-speed=\"1.5\"\n");
            sb.append("            text-color=\"#FFFFFF\"\n");
            sb.append("            badge=\"10/10\"\n");
            sb.append("            badge-bg=\"#6C63FF\"\n");
            sb.append("            badge-color=\"#FFFFFF\"\n");
            sb.append("            radius=\"16\"\n");
            sb.append("            rotation=\"0\" />\n");
        } else {
            for (AmeCard card : customCards) {
                sb.append("        <card\n");
                sb.append("            id=\"").append(escapeXml(card.id)).append("\"\n");
                sb.append("            title=\"").append(escapeXml(card.title)).append("\"\n");
                if (!TextUtils.isEmpty(card.subtitle)) {
                    sb.append("            subtitle=\"").append(escapeXml(card.subtitle)).append("\"\n");
                }
                sb.append("            icon=\"").append(escapeXml(card.icon)).append("\"\n");
                if (!TextUtils.isEmpty(card.url)) {
                    sb.append("            url=\"").append(escapeXml(card.url)).append("\"\n");
                }
                if (!TextUtils.isEmpty(card.src)) {
                    sb.append("            src=\"").append(escapeXml(card.src)).append("\"\n");
                }
                if (card.gradientColor1 != 0 && card.gradientColor2 != 0) {
                    sb.append("            gradient-start=\"").append(escapeXml(UiPrefs.hex(card.gradientColor1))).append("\"\n");
                    if (card.gradientColor3 != 0) {
                        sb.append("            gradient-middle=\"").append(escapeXml(UiPrefs.hex(card.gradientColor3))).append("\"\n");
                    }
                    sb.append("            gradient-end=\"").append(escapeXml(UiPrefs.hex(card.gradientColor2))).append("\"\n");
                    sb.append("            gradient-angle=\"").append(card.gradientAngle).append("\"\n");
                    sb.append("            gradient-speed=\"").append(card.gradientSpeed).append("\"\n");
                } else if (card.bgColor != 0) {
                    sb.append("            bg-color=\"").append(escapeXml(UiPrefs.hex(card.bgColor))).append("\"\n");
                }
                if (card.textColor != 0) {
                    sb.append("            text-color=\"").append(escapeXml(UiPrefs.hex(card.textColor))).append("\"\n");
                }
                if (!TextUtils.isEmpty(card.badge)) {
                    sb.append("            badge=\"").append(escapeXml(card.badge)).append("\"\n");
                    if (card.badgeBgColor != 0) sb.append("            badge-bg=\"").append(escapeXml(UiPrefs.hex(card.badgeBgColor))).append("\"\n");
                    if (card.badgeTextColor != 0) sb.append("            badge-color=\"").append(escapeXml(UiPrefs.hex(card.badgeTextColor))).append("\"\n");
                }
                if (card.borderColor != 0) {
                    sb.append("            border-color=\"").append(escapeXml(UiPrefs.hex(card.borderColor))).append("\"\n");
                    sb.append("            border-width=\"").append(card.borderWidth).append("\"\n");
                }
                sb.append("            radius=\"").append(card.radius).append("\"\n");
                if (card.rotation != 0) {
                    sb.append("            rotation=\"").append(card.rotation).append("\"\n");
                }
                sb.append("        />\n");
            }
        }
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
                        case "layout":
                        case "header":
                            parseLayout(parser);
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
                        case "item":
                        case "element":
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
            FileLog.e("AmeEngine: Failed to parse XML", t);
            return false;
        }
    }

    private static void parseTheme(XmlPullParser p) {
        String bg = p.getAttributeValue(null, "bg-color");
        if (bg != null) {
            UiPrefs.setBgColor(UiPrefs.parseColor(bg, 0xFF14151F));
            UiPrefs.setBgEnabled(true);
        }

        String cardBg = p.getAttributeValue(null, "card-bg");
        if (cardBg != null) {
            UiPrefs.setBlocksColor(UiPrefs.parseColor(cardBg, 0xFF1C242F));
            UiPrefs.setBlocksColorEnabled(true);
        }

        String radius = p.getAttributeValue(null, "card-radius");
        if (radius != null) {
            try {
                UiPrefs.setBlocksRadius(Integer.parseInt(radius));
                UiPrefs.setBlocksRadiusEnabled(true);
            } catch (Throwable ignore) {}
        }
    }

    private static void parseLayout(XmlPullParser p) {
        String align = p.getAttributeValue(null, "avatar-align");
        if (align != null) avatarAlign = align.toLowerCase().trim();

        String size = p.getAttributeValue(null, "avatar-size");
        if (size != null) {
            try { avatarSize = Integer.parseInt(size); } catch (Throwable ignore) {}
        }

        String rot = p.getAttributeValue(null, "avatar-rotation");
        if (rot == null) rot = p.getAttributeValue(null, "avatar-tilt");
        if (rot != null) {
            try { avatarRotation = Float.parseFloat(rot); } catch (Throwable ignore) {}
        }

        String nAlign = p.getAttributeValue(null, "name-align");
        if (nAlign != null) nameAlign = nAlign.toLowerCase().trim();

        String nRot = p.getAttributeValue(null, "name-rotation");
        if (nRot != null) {
            try { nameRotation = Float.parseFloat(nRot); } catch (Throwable ignore) {}
        }
    }

    private static void parseBanner(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        if (visible == null) visible = p.getAttributeValue(null, "enabled");
        if (visible != null) {
            bannerVisible = Boolean.parseBoolean(visible);
            UiPrefs.setBannerEnabled(bannerVisible);
        }

        String type = p.getAttributeValue(null, "type");
        if (type != null) bannerType = type.toLowerCase().trim();

        String src = p.getAttributeValue(null, "src");
        if (src != null) bannerSrc = src;

        String sound = p.getAttributeValue(null, "sound");
        if (sound != null) bannerSound = Boolean.parseBoolean(sound);

        String loop = p.getAttributeValue(null, "loop");
        if (loop != null) bannerLoop = Boolean.parseBoolean(loop);

        String height = p.getAttributeValue(null, "height");
        if (height != null) {
            try { bannerHeight = Integer.parseInt(height); } catch (Throwable ignore) {}
        }

        String color = p.getAttributeValue(null, "color");
        if (color != null) {
            UiPrefs.setBannerColor(UiPrefs.parseColor(color, 0xFF1C242F));
        }

        String gradStart = p.getAttributeValue(null, "gradient-start");
        if (gradStart != null) bannerGradientColor1 = UiPrefs.parseColor(gradStart, 0);

        String gradEnd = p.getAttributeValue(null, "gradient-end");
        if (gradEnd != null) bannerGradientColor2 = UiPrefs.parseColor(gradEnd, 0);

        String gradMiddle = p.getAttributeValue(null, "gradient-middle");
        if (gradMiddle != null) bannerGradientColor3 = UiPrefs.parseColor(gradMiddle, 0);

        String gradAngle = p.getAttributeValue(null, "gradient-angle");
        if (gradAngle == null) gradAngle = p.getAttributeValue(null, "angle");
        if (gradAngle != null) {
            try { bannerGradientAngle = Float.parseFloat(gradAngle); } catch (Throwable ignore) {}
        }

        String gradSpeed = p.getAttributeValue(null, "gradient-speed");
        if (gradSpeed == null) gradSpeed = p.getAttributeValue(null, "speed");
        if (gradSpeed != null) {
            try { bannerGradientSpeed = Float.parseFloat(gradSpeed); } catch (Throwable ignore) {}
        }

        String alpha = p.getAttributeValue(null, "alpha");
        if (alpha != null) {
            try { UiPrefs.setBannerAlpha(Integer.parseInt(alpha)); } catch (Throwable ignore) {}
        }

        String dim = p.getAttributeValue(null, "dim");
        if (dim != null) {
            try { UiPrefs.setBannerDim(Integer.parseInt(dim)); } catch (Throwable ignore) {}
        }
    }

    private static void parseAvatar(XmlPullParser p) {
        String align = p.getAttributeValue(null, "align");
        if (align != null) avatarAlign = align.toLowerCase().trim();

        String size = p.getAttributeValue(null, "size");
        if (size != null) {
            try { avatarSize = Integer.parseInt(size); } catch (Throwable ignore) {}
        }

        String rot = p.getAttributeValue(null, "rotation");
        if (rot == null) rot = p.getAttributeValue(null, "tilt");
        if (rot != null) {
            try { avatarRotation = Float.parseFloat(rot); } catch (Throwable ignore) {}
        }

        String shape = p.getAttributeValue(null, "shape");
        if (shape != null) {
            try { UiPrefs.setAvatarShape(Integer.parseInt(shape)); } catch (Throwable ignore) {}
        }

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { UiPrefs.setAvatarRadius(Integer.parseInt(radius)); } catch (Throwable ignore) {}
        }

        String ring = p.getAttributeValue(null, "ring-enabled");
        if (ring != null) UiPrefs.setAvatarRingEnabled(Boolean.parseBoolean(ring));

        String ringColor = p.getAttributeValue(null, "ring-color");
        if (ringColor != null) {
            UiPrefs.setAvatarRingColor(UiPrefs.parseColor(ringColor, 0xFF00E5FF));
        }

        String ringPulse = p.getAttributeValue(null, "ring-pulse");
        if (ringPulse != null) UiPrefs.setAvatarRingPulse(Boolean.parseBoolean(ringPulse));
    }

    private static void parseName(XmlPullParser p) {
        String colorEnabled = p.getAttributeValue(null, "color-enabled");
        if (colorEnabled != null) UiPrefs.setNameColorEnabled(Boolean.parseBoolean(colorEnabled));

        String color = p.getAttributeValue(null, "color");
        if (color != null) {
            UiPrefs.setNameColor(UiPrefs.parseColor(color, 0xFFFFFFFF));
        }

        String glowEnabled = p.getAttributeValue(null, "glow-enabled");
        if (glowEnabled != null) UiPrefs.setNameGlowEnabled(Boolean.parseBoolean(glowEnabled));

        String glowColor = p.getAttributeValue(null, "glow-color");
        if (glowColor != null) {
            UiPrefs.setNameGlowColor(UiPrefs.parseColor(glowColor, 0xFF2A87FF));
        }

        String glowRadius = p.getAttributeValue(null, "glow-radius");
        if (glowRadius != null) {
            try { UiPrefs.setNameGlowRadius(Integer.parseInt(glowRadius)); } catch (Throwable ignore) {}
        }

        String align = p.getAttributeValue(null, "align");
        if (align != null) nameAlign = align.toLowerCase().trim();

        String rot = p.getAttributeValue(null, "rotation");
        if (rot != null) {
            try { nameRotation = Float.parseFloat(rot); } catch (Throwable ignore) {}
        }

        String sz = p.getAttributeValue(null, "size");
        if (sz != null) {
            try { nameSize = Integer.parseInt(sz); } catch (Throwable ignore) {}
        }
    }

    private static void parseThought(XmlPullParser p) {
        String visible = p.getAttributeValue(null, "visible");
        String text = p.getAttributeValue(null, "text");
        if ("false".equalsIgnoreCase(visible)) {
            UiPrefs.setThoughtText("");
        } else if (text != null) {
            UiPrefs.setThoughtText(text);
        }

        String textColor = p.getAttributeValue(null, "text-color");
        if (textColor != null) {
            UiPrefs.setThoughtTextColor(UiPrefs.parseColor(textColor, 0xFFFFFFFF));
        }

        String bgColor = p.getAttributeValue(null, "bg-color");
        if (bgColor != null) {
            UiPrefs.setThoughtBgColor(UiPrefs.parseColor(bgColor, 0xFF1C242F));
        }

        String rot = p.getAttributeValue(null, "rotation");
        if (rot != null) {
            try { thoughtRotation = Float.parseFloat(rot); } catch (Throwable ignore) {}
        }
    }

    private static void parseInfoRows(XmlPullParser p) {
        String phoneVis = p.getAttributeValue(null, "phone-visible");
        if (phoneVis != null) phoneVisible = Boolean.parseBoolean(phoneVis);
        String phoneCol = p.getAttributeValue(null, "phone-color");
        if (!TextUtils.isEmpty(phoneCol)) {
            phoneColor = UiPrefs.parseColor(phoneCol, 0);
        } else {
            phoneColor = 0;
        }

        String usernameVis = p.getAttributeValue(null, "username-visible");
        if (usernameVis != null) usernameVisible = Boolean.parseBoolean(usernameVis);
        String usernameCol = p.getAttributeValue(null, "username-color");
        if (!TextUtils.isEmpty(usernameCol)) {
            usernameColor = UiPrefs.parseColor(usernameCol, 0);
        } else {
            usernameColor = 0;
        }

        String bioVis = p.getAttributeValue(null, "bio-visible");
        if (bioVis != null) bioVisible = Boolean.parseBoolean(bioVis);
        String bioCol = p.getAttributeValue(null, "bio-color");
        if (!TextUtils.isEmpty(bioCol)) {
            bioColor = UiPrefs.parseColor(bioCol, 0);
        } else {
            bioColor = 0;
        }

        String birthdayVis = p.getAttributeValue(null, "birthday-visible");
        if (birthdayVis != null) birthdayVisible = Boolean.parseBoolean(birthdayVis);
        String birthdayCol = p.getAttributeValue(null, "birthday-color");
        if (!TextUtils.isEmpty(birthdayCol)) {
            birthdayColor = UiPrefs.parseColor(birthdayCol, 0);
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
        card.src = p.getAttributeValue(null, "src");

        String bg = p.getAttributeValue(null, "bg-color");
        if (bg != null) card.bgColor = UiPrefs.parseColor(bg, 0);

        String gradStart = p.getAttributeValue(null, "gradient-start");
        if (gradStart != null) card.gradientColor1 = UiPrefs.parseColor(gradStart, 0);

        String gradEnd = p.getAttributeValue(null, "gradient-end");
        if (gradEnd != null) card.gradientColor2 = UiPrefs.parseColor(gradEnd, 0);

        String gradMid = p.getAttributeValue(null, "gradient-middle");
        if (gradMid != null) card.gradientColor3 = UiPrefs.parseColor(gradMid, 0);

        String gradAngle = p.getAttributeValue(null, "gradient-angle");
        if (gradAngle == null) gradAngle = p.getAttributeValue(null, "angle");
        if (gradAngle != null) {
            try { card.gradientAngle = Float.parseFloat(gradAngle); } catch (Throwable ignore) {}
        }

        String gradSpeed = p.getAttributeValue(null, "gradient-speed");
        if (gradSpeed == null) gradSpeed = p.getAttributeValue(null, "speed");
        if (gradSpeed != null) {
            try { card.gradientSpeed = Float.parseFloat(gradSpeed); } catch (Throwable ignore) {}
        }

        String rot = p.getAttributeValue(null, "rotation");
        if (rot == null) rot = p.getAttributeValue(null, "tilt");
        if (rot != null) {
            try { card.rotation = Float.parseFloat(rot); } catch (Throwable ignore) {}
        }

        String text = p.getAttributeValue(null, "text-color");
        if (text != null) card.textColor = UiPrefs.parseColor(text, 0);

        String subText = p.getAttributeValue(null, "subtitle-color");
        if (subText != null) card.subtitleColor = UiPrefs.parseColor(subText, 0);

        card.badge = p.getAttributeValue(null, "badge");

        String badgeBg = p.getAttributeValue(null, "badge-bg");
        if (badgeBg != null) card.badgeBgColor = UiPrefs.parseColor(badgeBg, 0);

        String badgeCol = p.getAttributeValue(null, "badge-color");
        if (badgeCol != null) card.badgeTextColor = UiPrefs.parseColor(badgeCol, 0);

        String border = p.getAttributeValue(null, "border-color");
        if (border != null) card.borderColor = UiPrefs.parseColor(border, 0);

        String borderW = p.getAttributeValue(null, "border-width");
        if (borderW != null) {
            try { card.borderWidth = Integer.parseInt(borderW); } catch (Throwable ignore) {}
        }

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { card.radius = Integer.parseInt(radius); } catch (Throwable ignore) {}
        }
        return !TextUtils.isEmpty(card.title) || !TextUtils.isEmpty(card.src) ? card : null;
    }

    public static void resetToDefaults() {
        SharedPreferences sp = getPrefs();
        if (sp != null) sp.edit().remove(KEY_RAW_XML).apply();
        avatarAlign = "left";
        avatarSize = 100;
        avatarRotation = 0f;
        nameAlign = "left";
        nameRotation = 0f;
        nameSize = 22;
        thoughtAlign = "left";
        thoughtRotation = 0f;

        bannerVisible = true;
        bannerType = "color";
        bannerSrc = "";
        bannerSound = false;
        bannerLoop = true;
        bannerHeight = 0;
        bannerGradientColor1 = 0;
        bannerGradientColor2 = 0;
        bannerGradientColor3 = 0;
        bannerGradientAngle = 0f;
        bannerGradientSpeed = 0f;

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
            Toast.makeText(context, T.get(
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
