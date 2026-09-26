package app.miogram.bridge.ameprofile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramCustomUiPrefs;

/**
 * Engine for "Аме Профіль" (Ame Profile):
 * - Generates clean XML representing user's current native profile design.
 * - Parses and applies shared Ame Profile XML from other users.
 * - Seamlessly shares profiles to the official community topic: https://t.me/dkamegram/1499.
 */
public class MiogramAmeProfileEngine {

    public static final String COMMUNITY_TOPIC_URL = "https://t.me/dkamegram/1499";

    /**
     * Generates a clean, well-formatted XML representation of the current profile layout and styles.
     */
    public static String exportCurrentProfileXml() {
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
        sb.append("<ame-profile version=\"1.0\" author=\"").append(escapeXml(username)).append("\">\n");

        // 1. Visibility of profile rows
        sb.append("    <visibility\n");
        sb.append("        hide-phone=\"").append(MiogramCustomUiPrefs.isHideRowPhone()).append("\"\n");
        sb.append("        hide-username=\"").append(MiogramCustomUiPrefs.isHideRowUsername()).append("\"\n");
        sb.append("        hide-bio=\"").append(MiogramCustomUiPrefs.isHideRowBio()).append("\"\n");
        sb.append("        hide-media=\"").append(MiogramCustomUiPrefs.isHideMediaTabs()).append("\" />\n");

        // 2. Banner Header
        sb.append("    <banner\n");
        sb.append("        enabled=\"").append(MiogramCustomUiPrefs.isBannerEnabled()).append("\"\n");
        sb.append("        mode=\"").append(escapeXml(MiogramCustomUiPrefs.getBannerMode())).append("\"\n");
        sb.append("        color=\"").append(escapeXml(MiogramCustomUiPrefs.getBannerColorHex())).append("\"\n");
        sb.append("        alpha=\"").append(MiogramCustomUiPrefs.getBannerAlpha()).append("\"\n");
        sb.append("        dim=\"").append(MiogramCustomUiPrefs.getBannerDim()).append("\" />\n");

        // 3. Avatar Geometry & Glowing Ring
        sb.append("    <avatar\n");
        sb.append("        shape=\"").append(escapeXml(MiogramCustomUiPrefs.getAvatarShape())).append("\"\n");
        sb.append("        radius=\"").append(MiogramCustomUiPrefs.getAvatarRadius()).append("\"\n");
        sb.append("        ring-enabled=\"").append(MiogramCustomUiPrefs.isAvatarRingEnabled()).append("\"\n");
        sb.append("        ring-color=\"").append(escapeXml(MiogramCustomUiPrefs.getAvatarRingColorHex())).append("\"\n");
        sb.append("        ring-pulse=\"").append(MiogramCustomUiPrefs.isAvatarRingPulse()).append("\" />\n");

        // 4. Name & Text FX
        sb.append("    <name\n");
        sb.append("        color-enabled=\"").append(MiogramCustomUiPrefs.isNameColorEnabled()).append("\"\n");
        sb.append("        color=\"").append(escapeXml(MiogramCustomUiPrefs.getNameColorHex())).append("\"\n");
        sb.append("        glow-enabled=\"").append(MiogramCustomUiPrefs.isNameGlowEnabled()).append("\"\n");
        sb.append("        glow-color=\"").append(escapeXml(MiogramCustomUiPrefs.getNameGlowColorHex())).append("\"\n");
        sb.append("        glow-radius=\"").append(MiogramCustomUiPrefs.getNameGlowRadius()).append("\"\n");
        sb.append("        fx=\"").append(escapeXml(MiogramCustomUiPrefs.getNameFx())).append("\" />\n");

        // 5. Blocks Styling
        sb.append("    <blocks\n");
        sb.append("        color-enabled=\"").append(MiogramCustomUiPrefs.isBlocksColorEnabled()).append("\"\n");
        sb.append("        color=\"").append(escapeXml(MiogramCustomUiPrefs.getBlocksColorHex())).append("\"\n");
        sb.append("        alpha=\"").append(MiogramCustomUiPrefs.getBlocksAlpha()).append("\"\n");
        sb.append("        blur=\"").append(MiogramCustomUiPrefs.isBlocksBlur()).append("\"\n");
        sb.append("        depth=\"").append(MiogramCustomUiPrefs.getBlocksDepth()).append("\"\n");
        sb.append("        radius=\"").append(MiogramCustomUiPrefs.getBlocksRadius()).append("\" />\n");

        // 6. Thought Cloud
        String thought = MiogramCustomUiPrefs.getThoughtText();
        sb.append("    <thought\n");
        sb.append("        text=\"").append(escapeXml(thought != null ? thought : "")).append("\"\n");
        sb.append("        text-color=\"").append(escapeXml(MiogramCustomUiPrefs.getThoughtTextColorHex())).append("\"\n");
        sb.append("        bg-color=\"").append(escapeXml(MiogramCustomUiPrefs.getThoughtBgColorHex())).append("\" />\n");

        // 7. Text Colors
        sb.append("    <palette\n");
        sb.append("        text-color-enabled=\"").append(MiogramCustomUiPrefs.isProfileTextColorEnabled()).append("\"\n");
        sb.append("        text-color=\"").append(escapeXml(MiogramCustomUiPrefs.getProfileTextColorHex())).append("\" />\n");

        sb.append("</ame-profile>\n");
        return sb.toString();
    }

    /**
     * Parses an Ame Profile XML and applies all settings into MiogramCustomUiPrefs.
     * Returns true if successfully parsed and applied.
     */
    public static boolean applyProfileXml(String xml) {
        if (TextUtils.isEmpty(xml)) return false;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(false);
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName().toLowerCase();
                    switch (tag) {
                        case "visibility":
                            parseVisibility(parser);
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
                        case "blocks":
                            parseBlocks(parser);
                            break;
                        case "thought":
                            parseThought(parser);
                            break;
                        case "palette":
                            parsePalette(parser);
                            break;
                    }
                }
                eventType = parser.next();
            }

            // Notify UI to re-render
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            return true;
        } catch (Throwable t) {
            FileLog.e("MiogramAmeProfileEngine: failed to parse XML", t);
            return false;
        }
    }

    private static void parseVisibility(XmlPullParser p) {
        String phone = p.getAttributeValue(null, "hide-phone");
        if (phone != null) MiogramCustomUiPrefs.setHideRowPhone(Boolean.parseBoolean(phone));

        String username = p.getAttributeValue(null, "hide-username");
        if (username != null) MiogramCustomUiPrefs.setHideRowUsername(Boolean.parseBoolean(username));

        String bio = p.getAttributeValue(null, "hide-bio");
        if (bio != null) MiogramCustomUiPrefs.setHideRowBio(Boolean.parseBoolean(bio));

        String media = p.getAttributeValue(null, "hide-media");
        if (media != null) MiogramCustomUiPrefs.setHideMediaTabs(Boolean.parseBoolean(media));
    }

    private static void parseBanner(XmlPullParser p) {
        String enabled = p.getAttributeValue(null, "enabled");
        if (enabled != null) MiogramCustomUiPrefs.setBannerEnabled(Boolean.parseBoolean(enabled));

        String mode = p.getAttributeValue(null, "mode");
        if (mode != null) MiogramCustomUiPrefs.setBannerMode(mode);

        String color = p.getAttributeValue(null, "color");
        if (color != null) MiogramCustomUiPrefs.setBannerColor(color);

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
        if (shape != null) MiogramCustomUiPrefs.setAvatarShape(shape);

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { MiogramCustomUiPrefs.setAvatarRadius(Integer.parseInt(radius)); } catch (Throwable ignore) {}
        }

        String ring = p.getAttributeValue(null, "ring-enabled");
        if (ring != null) MiogramCustomUiPrefs.setAvatarRingEnabled(Boolean.parseBoolean(ring));

        String ringColor = p.getAttributeValue(null, "ring-color");
        if (ringColor != null) MiogramCustomUiPrefs.setAvatarRingColor(ringColor);

        String pulse = p.getAttributeValue(null, "ring-pulse");
        if (pulse != null) MiogramCustomUiPrefs.setAvatarRingPulse(Boolean.parseBoolean(pulse));
    }

    private static void parseName(XmlPullParser p) {
        String colorEnabled = p.getAttributeValue(null, "color-enabled");
        if (colorEnabled != null) MiogramCustomUiPrefs.setNameColorEnabled(Boolean.parseBoolean(colorEnabled));

        String color = p.getAttributeValue(null, "color");
        if (color != null) MiogramCustomUiPrefs.setNameColor(color);

        String glowEnabled = p.getAttributeValue(null, "glow-enabled");
        if (glowEnabled != null) MiogramCustomUiPrefs.setNameGlowEnabled(Boolean.parseBoolean(glowEnabled));

        String glowColor = p.getAttributeValue(null, "glow-color");
        if (glowColor != null) MiogramCustomUiPrefs.setNameGlowColor(glowColor);

        String glowRadius = p.getAttributeValue(null, "glow-radius");
        if (glowRadius != null) {
            try { MiogramCustomUiPrefs.setNameGlowRadius(Integer.parseInt(glowRadius)); } catch (Throwable ignore) {}
        }

        String fx = p.getAttributeValue(null, "fx");
        if (fx != null) MiogramCustomUiPrefs.setNameFx(fx);
    }

    private static void parseBlocks(XmlPullParser p) {
        String colorEnabled = p.getAttributeValue(null, "color-enabled");
        if (colorEnabled != null) MiogramCustomUiPrefs.setBlocksColorEnabled(Boolean.parseBoolean(colorEnabled));

        String color = p.getAttributeValue(null, "color");
        if (color != null) MiogramCustomUiPrefs.setBlocksColor(color);

        String alpha = p.getAttributeValue(null, "alpha");
        if (alpha != null) {
            try { MiogramCustomUiPrefs.setBlocksAlpha(Integer.parseInt(alpha)); } catch (Throwable ignore) {}
        }

        String blur = p.getAttributeValue(null, "blur");
        if (blur != null) MiogramCustomUiPrefs.setBlocksBlur(Boolean.parseBoolean(blur));

        String depth = p.getAttributeValue(null, "depth");
        if (depth != null) {
            try { MiogramCustomUiPrefs.setBlocksDepth(Integer.parseInt(depth)); } catch (Throwable ignore) {}
        }

        String radius = p.getAttributeValue(null, "radius");
        if (radius != null) {
            try { MiogramCustomUiPrefs.setBlocksRadius(Integer.parseInt(radius)); } catch (Throwable ignore) {}
        }
    }

    private static void parseThought(XmlPullParser p) {
        String text = p.getAttributeValue(null, "text");
        if (text != null) MiogramCustomUiPrefs.setThoughtText(text);

        String textColor = p.getAttributeValue(null, "text-color");
        if (textColor != null) MiogramCustomUiPrefs.setThoughtTextColor(textColor);

        String bgColor = p.getAttributeValue(null, "bg-color");
        if (bgColor != null) MiogramCustomUiPrefs.setThoughtBgColor(bgColor);
    }

    private static void parsePalette(XmlPullParser p) {
        String textEnabled = p.getAttributeValue(null, "text-color-enabled");
        if (textEnabled != null) MiogramCustomUiPrefs.setProfileTextColorEnabled(Boolean.parseBoolean(textEnabled));

        String textColor = p.getAttributeValue(null, "text-color");
        if (textColor != null) MiogramCustomUiPrefs.setProfileTextColor(textColor);
    }

    /**
     * Copies the Ame Profile XML to clipboard and opens the community topic: https://t.me/dkamegram/1499
     */
    public static void shareToCommunity(Context context, String xml) {
        if (context == null) context = ApplicationLoader.applicationContext;
        if (context != null) {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("Ame Profile", xml));
            }
            Toast.makeText(context, MiogramLocale.get("Код Аме профілю скопійовано! Відкриваємо вітку...", "Код Аме профиля скопирован! Открываем ветку...", "Ame Profile code copied! Opening community topic..."), Toast.LENGTH_LONG).show();
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
