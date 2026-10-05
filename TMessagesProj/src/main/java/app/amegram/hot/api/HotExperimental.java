package app.amegram.hot.api;

/**
 * Інтерфейс сервісу експериментальних налаштувань (модуль experimental).
 * Включає розширення Amegram: wide posts, swipe actions,
 * inline math, browser adblock, translation without premium, forwards count тощо.
 */
public interface HotExperimental {
    boolean isUnlimitedPinned();
    boolean isUnlimitedFavStickers();
    boolean isUploadBoost();
    boolean isNoiseSuppression();
    boolean isEnhancedVideoBitrate();
    boolean isSendMp4AsVideo();
    boolean isPreferHardwareDecoder();
    boolean isSaveDeletedMessages();
    boolean isSaveEditHistory();

    // Розширення Amegram
    boolean isWidePosts();
    boolean isInlineMathResult();
    boolean isSwipeActions();
    boolean isTranslateChatNoPremium();
    boolean isBrowserAdblock();
    boolean isShowForwardsCount();
    boolean isHideStickerTime();
    boolean isTabCounterUnmutedOnly();
}
