package app.miogram.bridge.lyrics;

import org.telegram.messenger.NotificationCenter;
import org.telegram.ui.LaunchActivity;

/**
 * Floating active lyrics ticker pill for Miogram (disabled).
 */
public class MiogramFloatingLyricsTicker implements NotificationCenter.NotificationCenterDelegate {

    private static volatile MiogramFloatingLyricsTicker instance;

    public static MiogramFloatingLyricsTicker getInstance() {
        if (instance == null) {
            synchronized (MiogramFloatingLyricsTicker.class) {
                if (instance == null) {
                    instance = new MiogramFloatingLyricsTicker();
                }
            }
        }
        return instance;
    }

    private MiogramFloatingLyricsTicker() {}

    public void attach(LaunchActivity activity) {
        // Disabled permanently
    }

    public void detach() {
        // Disabled permanently
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
    }

    public void updateLyricsState() {
    }

    public void hideTicker(boolean animated) {
    }
}
