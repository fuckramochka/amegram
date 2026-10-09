package app.miogram.bridge.settings;

/**
 * Backward-compatible route for old Miogram navigation entry points.
 * All settings now open the single Amegram settings hub.
 */
public class MiogramSettingsActivity extends app.amegram.settings.AmegramSettingsActivity {
    public static final String CHANNEL_USERNAME = app.miogram.bridge.updater.MiogramUpdater.CHANNEL_USERNAME;
    public static final String FALLBACK_CHANNEL_USERNAME = app.miogram.bridge.updater.MiogramUpdater.FALLBACK_CHANNEL_USERNAME;
}
