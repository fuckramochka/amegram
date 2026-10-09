package app.amegram.hot.api;

/** Optional, hot-updatable rules for organizing files saved from chats. */
public interface HotFileOrganization {
    /** Returns a safe relative directory name for a chat, or null to use host fallback. */
    String chatSubfolder(String chatTitle, long peerId);
}
