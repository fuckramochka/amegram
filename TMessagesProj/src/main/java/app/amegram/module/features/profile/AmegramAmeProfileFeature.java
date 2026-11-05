package app.amegram.module.features.profile;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Live-XML profile styling (AmeProfileEngine, already modular under app.amegram).
 * OFF = stock Telegram profile instantly (engine falls back to vanilla defaults).
 * Applied live via reflection reset — no restart, no upstream edits.
 */
public class AmegramAmeProfileFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "ameprofile";
    }

    @Override
    public String title() {
        return "Аме-профиль";
    }

    @Override
    public String ramEstimate() {
        return "~200 КБ";
    }

    @Override
    public boolean isAvailable() {
        return app.amegram.hot.HotModulesManager.isModuleInstalledOnDisk("ame");
    }

    @Override
    public boolean isEnabled() {
        return isAvailable() && AmegramConfig.getBool("ameprofile_enabled", false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("ameprofile_enabled", enabled);
        reinitEngine();
    }

    @Override
    public void load() {
        loaded = true;
        reinitEngine();
    }

    @Override
    public void unload() {
        loaded = false;
        reinitEngine();
    }

    private static void reinitEngine() {
        try {
            app.amegram.bridge.ameprofile.AmeProfileEngine.reload();
        } catch (Throwable ignore) {
        }
    }
}
