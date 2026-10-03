package app.amegram.module.features.net;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Anti-censorship engine module. Enforcement lives in
 * MiogramAntiBlockEngine.start() (gated on the same key, default ON).
 * Zero compile-time dependency: the engine is kicked via reflection so this
 * file survives the vanilla rebase untouched.
 */
public class AmegramAntiBlockFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "antiblock";
    }

    @Override
    public String title() {
        return "Антиблок";
    }

    @Override
    public String ramEstimate() {
        return "~400 КБ";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("antiblock_enabled", true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("antiblock_enabled", enabled);
    }

    @Override
    public void load() {
        loaded = true;
        // Engine autostarts at boot; hub enable after boot needs a kick.
        try {
            Class<?> engine = Class.forName("app.miogram.bridge.bypass.MiogramAntiBlockEngine");
            Object inst = engine.getMethod("getInstance").invoke(null);
            engine.getMethod("start").invoke(inst);
        } catch (ClassNotFoundException vanilla) {
            // Vanilla official client: transport lands here at rebase (Etap 6).
        } catch (Throwable ignore) {
        }
    }

    @Override
    public void unload() {
        // Live sockets can't be yanked mid-session safely; OFF applies at next cold start.
        loaded = false;
    }
}
