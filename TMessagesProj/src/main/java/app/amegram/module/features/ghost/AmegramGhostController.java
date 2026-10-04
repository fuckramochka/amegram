package app.amegram.module.features.ghost;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Ghost mode, clean-room port.
 * Legacy sources: NekoConfig.sendRead/Online/UploadPackets + GhostPill + MiogramPrivacySettings.
 * Difference: no dependency on NekoConfig/PillStack. State lives in AmegramConfig,
 * enforcement via AmegramHooks.shouldBlock* called from SendMessagesHelper.
 */
public class AmegramGhostController implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "ghost";
    }

    @Override
    public String title() {
        return "Режим призрака";
    }

    @Override
    public String ramEstimate() {
        return "~50 КБ";
    }

    @Override
    public boolean isAvailable() {
        return true; // built-in, no download needed
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("ghost_enabled", false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("ghost_enabled", enabled);
        mirrorLegacy(enabled);
    }

    private static void mirrorLegacy(boolean enabled) {
        try {
            Class<?> neko = Class.forName("tw.nekomimi.nekogram.NekoConfig");
            neko.getMethod("setGhostMode", boolean.class).invoke(null, enabled);
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void load() {
        loaded = true;
        tryMigrateFromLegacy();
    }

    @Override
    public void unload() {
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }

    /**
     * Hub master switch. OFF = ghost dead everywhere (effSend* return true).
     * Migration (Neko -> Amegram flags) runs once on load(); legacy Neko UI
     * becomes inert while the hub owns ghost.
     */
    public static boolean isOn() {
        try {
            return AmegramConfig.getBool("ghost_enabled", false);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * True when the module owns ghost decisions (user switched ghost on at least once
     * or migration already ran). Before first enable we defer to legacy NekoConfig
     * so existing users keep their behavior bit-for-bit.
     */
    public static boolean isModuleActive() {
        try {
            return AmegramConfig.getBool("ghost_enabled", false)
                    || AmegramConfig.getBool("ghost_migrated", false);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * One-way migration from legacy NekoConfig via reflection (no compile-time
     * dependency, safe on vanilla where NekoConfig does not exist).
     * Maps: sendRead=false -> hideRead=true, etc. Ghost master = any channel hidden.
     */
    public static void tryMigrateFromLegacy() {
        try {
            if (AmegramConfig.getBool("ghost_migrated", false)) {
                return;
            }
            Class<?> neko = Class.forName("tw.nekomimi.nekogram.NekoConfig");
            boolean sendRead = readLegacyBool(neko, "sendReadMessagePackets", true);
            boolean sendOnline = readLegacyBool(neko, "sendOnlinePackets", true);
            boolean sendTyping = readLegacyBool(neko, "sendUploadProgress", true);
            boolean ghostOn = !sendRead || !sendOnline || !sendTyping;
            // Only claim ownership when legacy had ghost actually engaged.
            // Otherwise stay dormant and keep deferring to legacy defaults.
            if (ghostOn) {
                AmegramConfig.setBool("ghost_enabled", true);
                AmegramConfig.setBool("ghost_hide_read", !sendRead);
                AmegramConfig.setBool("ghost_hide_online", !sendOnline);
                AmegramConfig.setBool("ghost_hide_typing", !sendTyping);
            }
            AmegramConfig.setBool("ghost_migrated", true);
        } catch (ClassNotFoundException vanilla) {
            // Vanilla official client: nothing to migrate from.
        } catch (Throwable ignore) {
        }
    }

    private static boolean readLegacyBool(Class<?> neko, String field, boolean def) {
        try {
            Object item = neko.getField(field).get(null);
            return (boolean) item.getClass().getMethod("Bool").invoke(item);
        } catch (Throwable t) {
            return def;
        }
    }

    // Effective flags for the network interceptor: module wins when active,
    // legacy NekoConfig is the fallback (passed in by the caller).

    // Per-action toggles, shown in Amegram -> Privacy.
    public static boolean hideRead() {
        return AmegramConfig.getBool("ghost_hide_read", true);
    }

    public static boolean hideOnline() {
        return AmegramConfig.getBool("ghost_hide_online", true);
    }

    public static boolean hideTyping() {
        return AmegramConfig.getBool("ghost_hide_typing", true);
    }

    public static void setHideRead(boolean v) {
        AmegramConfig.setBool("ghost_hide_read", v);
    }

    public static void setHideOnline(boolean v) {
        AmegramConfig.setBool("ghost_hide_online", v);
    }

    public static void setHideTyping(boolean v) {
        AmegramConfig.setBool("ghost_hide_typing", v);
    }

    /** Master toggle (GhostPill): flips the module, mirrors legacy Neko flags
     * via reflection so legacy indicators stay truthful. Per-channel choices preserved. */
    public static void toggleMaster() {
        boolean next = !isOn();
        new AmegramGhostController().setEnabled(next);
        try {
            Class<?> neko = Class.forName("tw.nekomimi.nekogram.NekoConfig");
            neko.getMethod("setGhostMode", boolean.class).invoke(null, next);
        } catch (Throwable ignore) {
        }
        try {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            h.post(() -> {
                try {
                    org.telegram.messenger.NotificationCenter.getInstance(
                            org.telegram.messenger.UserConfig.selectedAccount)
                            .postNotificationName(
                                    org.telegram.messenger.NotificationCenter.mainUserInfoChanged);
                } catch (Throwable ignore) {
                }
            });
        } catch (Throwable ignore) {
        }
    }
}
