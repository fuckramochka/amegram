package app.amegram.module.features.vault;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Double Bottom: real PIN opens the full workspace, duress PIN opens a decoy
 * (allowed chats only, search + notifications + chat-open all filtered).
 * Enforcement lives in MiogramDoubleBottomManager (Argon2id v2 verifiers,
 * timing-equalized async verdicts) + the existing PasscodeView/Dialogs/
 * Search/Notifications hooks. OFF = duress can never activate.
 */
public class AmegramDoubleBottomFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "doublebottom";
    }

    @Override
    public String title() {
        return "Двойное дно";
    }

    @Override
    public String ramEstimate() {
        return "~100 КБ";
    }

    @Override
    public boolean isAvailable() {
        return app.amegram.hot.HotModulesManager.isModuleInstalledOnDisk("vault");
    }

    @Override
    public boolean isEnabled() {
        return isAvailable() && AmegramConfig.getBool("doublebottom_enabled", false);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("doublebottom_enabled", enabled);
        if (!enabled) {
            // Never leave a decoy workspace active behind the toggle.
            try {
                app.miogram.bridge.vault.MiogramDoubleBottomManager.setDuressActive(false);
            } catch (Throwable ignore) {
            }
        }
    }

    @Override
    public void load() {
        loaded = true;
    }

    @Override
    public void unload() {
        loaded = false;
    }
}
