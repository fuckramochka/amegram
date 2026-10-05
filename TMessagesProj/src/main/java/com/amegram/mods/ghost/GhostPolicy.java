package com.amegram.mods.ghost;

/**
 * Pure-JVM ghost decision table. Zero Android / Neko / Telegram imports
 * so it runs in plain unit tests and survives the vanilla rebase.
 *
 * Semantics:
 * - legacy `send* = true`  means "send packets" (ghost OFF for that channel)
 * - amegram `hide* = true`  means "block packets" (ghost ON for that channel)
 * - master `ghost_enabled` gates everything: disabled module == vanilla behavior.
 */
public final class GhostPolicy {

    private GhostPolicy() {
    }

    /** Effective "send read receipts" flag. */
    public static boolean resolveSendRead(boolean moduleActive, boolean hideRead, boolean legacySendRead) {
        if (!moduleActive) {
            return legacySendRead;
        }
        return !hideRead;
    }

    public static boolean resolveSendStories(boolean moduleActive, boolean hideRead, boolean legacySendStories) {
        if (!moduleActive) {
            return legacySendStories;
        }
        return !hideRead;
    }

    public static boolean resolveSendOnline(boolean moduleActive, boolean hideOnline, boolean legacySendOnline) {
        if (!moduleActive) {
            return legacySendOnline;
        }
        return !hideOnline;
    }

    public static boolean resolveSendTyping(boolean moduleActive, boolean hideTyping, boolean legacySendTyping) {
        if (!moduleActive) {
            return legacySendTyping;
        }
        return !hideTyping;
    }

    public static boolean shouldBlockTyping(boolean sendTypingEnabled, boolean typingExcluded) {
        return !sendTypingEnabled && !typingExcluded;
    }

    public static boolean shouldBlockRead(boolean sendReadEnabled, boolean allowReadPacket, boolean readExcluded) {
        return !sendReadEnabled && !allowReadPacket && !readExcluded;
    }

    public static boolean shouldBlockStories(boolean sendStoriesEnabled, boolean readExcluded) {
        return !sendStoriesEnabled && !readExcluded;
    }

    public static boolean shouldForceOffline(boolean sendOnlineEnabled) {
        return !sendOnlineEnabled;
    }
}
