package app.amegram.module.features.system;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Rapid hotfixes without APK reinstall. Enforcement lives in
 * AmegramPatchManager: config-type patches always allowed, bytecode (.dex)
 * patches require the explicit "hotfix_code_patches" opt-in (default OFF).
 */
public class AmegramHotfixFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "hotfix";
    }

    @Override
    public String title() {
        return "Хотфиксы";
    }

    @Override
    public String ramEstimate() {
        return "~50 КБ";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("hotfix_enabled", true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("hotfix_enabled", enabled);
    }

    @Override
    public void load() {
        loaded = true;
    }

    @Override
    public void unload() {
        loaded = false;
    }

    public static boolean codePatchesAllowed() {
        return AmegramConfig.getBool("hotfix_code_patches", false);
    }

    public static void setCodePatchesAllowed(boolean v) {
        AmegramConfig.setBool("hotfix_code_patches", v);
    }
}
