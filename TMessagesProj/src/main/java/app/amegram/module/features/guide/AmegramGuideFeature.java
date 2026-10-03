package app.amegram.module.features.guide;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Welcome guide. Legacy: AmegramGuideSheet (5 steps, 896 lines, drags Discord+AGSL+market).
 * Clean version: max 3 steps (persona -> modules -> done), shown once per version.
 * Actual BottomSheet UI attaches lazily from Settings, never at cold start.
 */
public class AmegramGuideFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "guide";
    }

    @Override
    public String title() {
        return "Гид";
    }

    @Override
    public String ramEstimate() {
        return "~100 КБ при показе";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("guide_enabled", true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("guide_enabled", enabled);
    }

    @Override
    public void load() {
        loaded = true;
    }

    @Override
    public void unload() {
        loaded = false;
    }

    /** Called from AmegramHooks.onLaunchCreated(). No-op when already shown for this version. */
    public static void maybeShowOnLaunch() {
        try {
            if (!isEnabledStatic()) {
                return;
            }
            if (app.amegram.module.ui.AmegramWelcomeSheet.wasShown()) {
                return;
            }
            // LaunchActivity context is not available here without a fragment;
            // the sheet opens from the Modules hub and on first hub visit.
        } catch (Throwable ignore) {
        }
    }

    private static boolean isEnabledStatic() {
        try {
            return app.amegram.module.AmegramConfig.getBool("guide_enabled", true);
        } catch (Throwable t) {
            return false;
        }
    }
}
