package app.amegram.module;

import android.content.Context;

/**
 * The ONLY allowed touch points into vanilla org.telegram code.
 * Goal: instead of 116 hacked files, vanilla needs just these 5 one-liners:
 *
 * <pre>
 * ApplicationLoader.onCreate          -> AmegramModule.init(this);
 * LaunchActivity.onCreate             -> AmegramHooks.onLaunchCreated(this);
 * DialogsActivity.createView          -> AmegramHooks.onDialogsCreate(fragment);
 * ChatMessageCell.onDraw              -> AmegramHooks.onChatCellDraw(...) // gated by flags
 * AudioPlayerAlert.&lt;init&gt;             -> AmegramHooks.onPlayerOpen(alert)
 * SendMessagesHelper.sendReadPacket   -> if (AmegramHooks.shouldBlockReadReceipts()) return;
 * </pre>
 *
 * Every method must be safe to call when the module is disabled: zero cost, zero crash.
 */
public final class AmegramHooks {

    private AmegramHooks() {
    }

    public static void onAppCreate(Context appContext) {
        // Reserved: WorkManager for update checks (24h), Supabase prefetch off by default.
    }

    public static void onLaunchCreated(final android.content.Context activity) {
        try {
            if (!AmegramFeatureManager.isEnabled("guide")) {
                return;
            }
            if (app.amegram.module.ui.AmegramWelcomeSheet.wasShown()) {
                return;
            }
            // Let the lock screen / dialogs settle first; never block startup.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (app.amegram.module.ui.AmegramWelcomeSheet.wasShown()) {
                        return;
                    }
                    new app.amegram.module.ui.AmegramWelcomeSheet(activity).show();
                } catch (Throwable ignore) {
                }
            }, 1500);
        } catch (Throwable ignore) {
        }
    }

    public static void onDialogsCreate(Object dialogsFragment) {
        // Reserved for LayoutDelegate. Intentionally does NOT addView anything today:
        // Discord-rail hack (addView + actionBar.GONE) is banned in the clean module.
    }

    /** Called from ChatMessageCell — must return in <1ms when badges disabled. */
    public static void onChatCellDraw(Object cell, Object canvas) {
        // Reserved: badge arrow overlay. No static Paint mutation allowed here.
    }

    public static void onPlayerOpen(Object audioPlayerAlert) {
        // Reserved: lazy AmegramPlayerFeature.load() on first open.
        try {
            if (AmegramFeatureManager.isEnabled("player")) {
                AmegramFeature f = AmegramFeatureManager.get("player");
                if (f != null) {
                    f.load();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    /** Single privacy gate for read receipts. Called from SendMessagesHelper. */
    public static boolean shouldBlockReadReceipts() {
        try {
            return AmegramFeatureManager.isEnabled("ghost")
                    && AmegramConfig.getBool("ghost_hide_read", true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shouldBlockOnline() {
        try {
            return AmegramFeatureManager.isEnabled("ghost")
                    && AmegramConfig.getBool("ghost_hide_online", true);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean shouldBlockTyping() {
        try {
            return AmegramFeatureManager.isEnabled("ghost")
                    && AmegramConfig.getBool("ghost_hide_typing", true);
        } catch (Throwable t) {
            return false;
        }
    }
}
