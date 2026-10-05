package app.amegram.hot;

import app.amegram.hot.api.HotExperimental;
import app.amegram.hot.api.HotServices;

/**
 * Гейт експериментальних налаштувань у ядрі.
 * Дозволяє модулю 'experimental' активувати прискорення, шумозаглушення,
 * безлімітні закріплені чати, збереження видалених, а також розширення Amegram:
 * wide posts, swipe actions, inline math, browser adblock, no-premium translation тощо.
 */
public final class HotExperimentalGate {

    private HotExperimentalGate() {
    }

    private static HotExperimental service() {
        try {
            return HotModulesManager.getService(HotServices.EXPERIMENTAL);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isUnlimitedPinned() {
        HotExperimental e = service();
        return e != null && e.isUnlimitedPinned();
    }

    public static boolean isUnlimitedFavStickers() {
        HotExperimental e = service();
        return e != null && e.isUnlimitedFavStickers();
    }

    public static boolean isUploadBoost() {
        HotExperimental e = service();
        return e != null && e.isUploadBoost();
    }

    public static boolean isNoiseSuppression() {
        HotExperimental e = service();
        return e != null && e.isNoiseSuppression();
    }

    public static boolean isEnhancedVideoBitrate() {
        HotExperimental e = service();
        return e != null && e.isEnhancedVideoBitrate();
    }

    public static boolean isSendMp4AsVideo() {
        HotExperimental e = service();
        return e != null && e.isSendMp4AsVideo();
    }

    public static boolean isPreferHardwareDecoder() {
        HotExperimental e = service();
        return e != null && e.isPreferHardwareDecoder();
    }

    public static boolean isSaveDeletedMessages() {
        HotExperimental e = service();
        return e != null && e.isSaveDeletedMessages();
    }

    public static boolean isSaveEditHistory() {
        HotExperimental e = service();
        return e != null && e.isSaveEditHistory();
    }

    public static boolean isWidePosts() {
        HotExperimental e = service();
        return e != null && e.isWidePosts();
    }

    public static boolean isInlineMathResult() {
        HotExperimental e = service();
        return e != null && e.isInlineMathResult();
    }

    public static boolean isSwipeActions() {
        HotExperimental e = service();
        return e != null && e.isSwipeActions();
    }

    public static boolean isTranslateChatNoPremium() {
        HotExperimental e = service();
        return e != null && e.isTranslateChatNoPremium();
    }

    public static boolean isBrowserAdblock() {
        HotExperimental e = service();
        return e != null && e.isBrowserAdblock();
    }

    public static boolean isShowForwardsCount() {
        HotExperimental e = service();
        return e != null && e.isShowForwardsCount();
    }

    public static boolean isHideStickerTime() {
        HotExperimental e = service();
        return e != null && e.isHideStickerTime();
    }

    public static boolean isTabCounterUnmutedOnly() {
        HotExperimental e = service();
        return e != null && e.isTabCounterUnmutedOnly();
    }
}
