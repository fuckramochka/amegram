package app.miogram.ui.yumi;

import android.content.Context;
import android.os.PowerManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LiteMode;

/**
 * One place that answers "should Yumi spend battery on this?".
 *
 * <p>It follows both the Android battery saver and Telegram's own LiteMode
 * power-saving preset. The process override exists for previews, tests and
 * screenshot tooling; production code only reads it.</p>
 */
public final class YumiPowerSave {

    private static volatile Boolean override;

    private YumiPowerSave() {
    }

    public static boolean isEnabled(Context context) {
        final Boolean forced = override;
        if (forced != null) {
            return forced;
        }
        if (context == null) {
            context = ApplicationLoader.applicationContext;
        }
        if (context != null) {
            try {
                final PowerManager manager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (manager != null && manager.isPowerSaveMode()) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            // Telegram writes its "Power saving" preset as zero enabled features.
            return LiteMode.getValue(true) == LiteMode.PRESET_POWER_SAVER;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isEnabled() {
        return isEnabled(ApplicationLoader.applicationContext);
    }

    /** Test/preview only. Pass {@code null} to return to the real device state. */
    public static void setOverride(Boolean value) {
        override = value;
    }

    public static boolean animations() {
        return !isEnabled();
    }

    public static float motionScale() {
        return isEnabled() ? 0f : 1f;
    }
}
