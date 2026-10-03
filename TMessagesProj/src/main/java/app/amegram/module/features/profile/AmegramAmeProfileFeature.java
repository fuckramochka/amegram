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
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("ameprofile_enabled", true);
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
            Class<?> engine = Class.forName("app.amegram.bridge.ameprofile.AmeProfileEngine");
            java.lang.reflect.Field f = engine.getDeclaredField("isInitialized");
            f.setAccessible(true);
            f.setBoolean(null, false);
            engine.getMethod("ensureInitialized").invoke(null);
        } catch (ClassNotFoundException vanilla) {
            // Vanilla: engine lands here at rebase (Etap 6).
        } catch (Throwable ignore) {
        }
    }
}
