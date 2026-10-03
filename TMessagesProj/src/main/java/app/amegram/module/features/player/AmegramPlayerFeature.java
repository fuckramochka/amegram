package app.amegram.module.features.player;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Facade for the modern audio player.
 * Legacy sources: MiogramModernPlayerLayout (~2000 строк), MiogramBassVisualizer,
 * MiogramLyricsView/Engine/LrcModel/SourceSelect, MiogramPlayerPrefs (516 строк).
 * Migration rule: heavy view is created ONLY inside load(), never at app start.
 * Prefs migrate from "miogram_player_prefs" into AmegramConfig on first run.
 */
public class AmegramPlayerFeature implements AmegramFeature {

    private volatile boolean loaded;

    @Override
    public String id() {
        return "player";
    }

    @Override
    public String title() {
        return "Аудиоплеер + тексты";
    }

    @Override
    public String ramEstimate() {
        return "~2 МБ при открытии";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("player_enabled", true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("player_enabled", enabled);
    }

    @Override
    public synchronized void load() {
        if (loaded) {
            return;
        }
        // Heavy player view (MiogramModernPlayerLayout) attaches here and ONLY here —
        // never at app start. Config migrates once from legacy prefs by file name.
        AmegramPlayerConfig.load();
        loaded = true;
        // NOTE: actual MiogramModernPlayerLayout port attaches in AmegramHooks.onPlayerOpen().
        // It must NOT be referenced from ApplicationLoader — cold start stays unaffected.
    }

    @Override
    public synchronized void unload() {
        loaded = false;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
