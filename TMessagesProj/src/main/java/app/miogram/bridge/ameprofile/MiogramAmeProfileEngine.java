package app.miogram.bridge.ameprofile;

import android.content.Context;
import java.util.List;

import app.amegram.bridge.ameprofile.AmeProfileEngine;

/**
 * Backward compatibility bridge delegating to {@link AmeProfileEngine}.
 */
public class MiogramAmeProfileEngine {

    public static final String COMMUNITY_TOPIC_URL = AmeProfileEngine.COMMUNITY_TOPIC_URL;

    public static class AmeCard extends AmeProfileEngine.AmeCard {}

    public static void ensureInitialized() {
        AmeProfileEngine.ensureInitialized();
    }

    public static boolean isPhoneVisible() {
        return AmeProfileEngine.isPhoneVisible();
    }

    public static int getPhoneColor() {
        return AmeProfileEngine.getPhoneColor();
    }

    public static boolean isUsernameVisible() {
        return AmeProfileEngine.isUsernameVisible();
    }

    public static int getUsernameColor() {
        return AmeProfileEngine.getUsernameColor();
    }

    public static boolean isBioVisible() {
        return AmeProfileEngine.isBioVisible();
    }

    public static int getBioColor() {
        return AmeProfileEngine.getBioColor();
    }

    public static boolean isBirthdayVisible() {
        return AmeProfileEngine.isBirthdayVisible();
    }

    public static int getBirthdayColor() {
        return AmeProfileEngine.getBirthdayColor();
    }

    public static boolean isPresenceVisible() {
        return AmeProfileEngine.isPresenceVisible();
    }

    public static boolean isMediaTabsVisible() {
        return AmeProfileEngine.isMediaTabsVisible();
    }

    public static List<AmeProfileEngine.AmeCard> getCustomCards() {
        return AmeProfileEngine.getCustomCards();
    }

    public static String exportCurrentProfileXml() {
        return AmeProfileEngine.exportCurrentProfileXml();
    }

    public static boolean applyProfileXml(String xml) {
        return AmeProfileEngine.applyProfileXml(xml);
    }

    public static void resetToDefaults() {
        AmeProfileEngine.resetToDefaults();
    }

    public static void shareToCommunity(Context context, String xml) {
        AmeProfileEngine.shareToCommunity(context, xml);
    }
}
