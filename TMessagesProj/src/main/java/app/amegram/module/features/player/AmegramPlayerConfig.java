package app.amegram.module.features.player;

import android.content.Context;
import android.content.SharedPreferences;

import app.amegram.module.AmegramConfig;

/**
 * Typed player configuration. Replaces MiogramPlayerPrefs (516 lines of
 * copy-paste getters) with one immutable snapshot + explicit migration.
 *
 * Migration is vanilla-safe: legacy values are read by prefs FILE NAME
 * ("miogram_player_prefs") with zero imports of legacy classes.
 * Runs once, on first PlayerFeature.load().
 */
public final class AmegramPlayerConfig {

    public static final String LEGACY_PREFS = "miogram_player_prefs";

    public static final int BG_COVER_BLUR = 0;
    public static final int BG_GRADIENT = 1;
    public static final int BG_SOLID = 2;
    public static final int BG_TRANSPARENT = 3;

    public static final String PRESET_DEFAULT = "default";
    public static final String PRESET_LYRICS = "lyrics";
    public static final String PRESET_MINIMAL = "minimal";
    public static final String PRESET_VINYL = "vinyl";
    public static final String PRESET_COMPACT = "compact";

    public final int backgroundMode;
    public final boolean visualizerEnabled;
    public final String layoutPreset;
    public final String controlsOrder;
    public final int lyricsFontSize;
    public final float lyricsInactiveOpacity;

    private AmegramPlayerConfig(int backgroundMode, boolean visualizerEnabled,
                                String layoutPreset, String controlsOrder,
                                int lyricsFontSize, float lyricsInactiveOpacity) {
        this.backgroundMode = backgroundMode;
        this.visualizerEnabled = visualizerEnabled;
        this.layoutPreset = layoutPreset;
        this.controlsOrder = controlsOrder;
        this.lyricsFontSize = lyricsFontSize;
        this.lyricsInactiveOpacity = lyricsInactiveOpacity;
    }

    public static AmegramPlayerConfig load() {
        migrateOnce();
        return new AmegramPlayerConfig(
                AmegramConfig.getInt("player_bg_mode", BG_COVER_BLUR),
                AmegramConfig.getBool("player_visualizer", false),
                AmegramConfig.getString("player_preset", PRESET_DEFAULT),
                AmegramConfig.getString("player_controls",
                        "shuffle,repeat,prev,play,next,speed,queue"),
                AmegramConfig.getInt("player_lyrics_size", 18),
                AmegramConfig.getFloat("player_lyrics_dim", 0.55f));
    }

    public void save() {
        AmegramConfig.setString("player_preset", layoutPreset);
        AmegramConfig.setString("player_controls", controlsOrder);
        AmegramConfig.setBool("player_visualizer", visualizerEnabled);
        AmegramConfig.setInt("player_bg_mode", backgroundMode);
        AmegramConfig.setInt("player_lyrics_size", lyricsFontSize);
        AmegramConfig.setFloat("player_lyrics_dim", lyricsInactiveOpacity);
    }

    private static void migrateOnce() {
        if (AmegramConfig.getBool("player_migrated", false)) {
            return;
        }
        try {
            Context ctx = appContext();
            if (ctx != null) {
                SharedPreferences legacy = ctx.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
                if (legacy != null) {
                    copyInt(legacy, "bg_mode", "player_bg_mode");
                    copyBool(legacy, "visualizer_enabled", "player_visualizer");
                    copyString(legacy, "player_layout_preset", "player_preset", PRESET_DEFAULT);
                    copyString(legacy, "controls_order", "player_controls",
                            "shuffle,repeat,prev,play,next,speed,queue");
                    copyInt(legacy, "lyrics_font_size", "player_lyrics_size");
                    copyFloat(legacy, "lyrics_inactive_opacity", "player_lyrics_dim");
                }
            }
        } catch (Throwable ignore) {
        }
        AmegramConfig.setBool("player_migrated", true);
    }

    private static Context appContext() {
        try {
            return org.telegram.messenger.ApplicationLoader.applicationContext;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void copyInt(SharedPreferences from, String k, String to) {
        if (from.contains(k)) {
            AmegramConfig.setInt(to, from.getInt(k, 0));
        }
    }

    private static void copyBool(SharedPreferences from, String k, String to) {
        if (from.contains(k)) {
            AmegramConfig.setBool(to, from.getBoolean(k, false));
        }
    }

    private static void copyFloat(SharedPreferences from, String k, String to) {
        if (from.contains(k)) {
            AmegramConfig.setFloat(to, from.getFloat(k, 0.55f));
        }
    }

    private static void copyString(SharedPreferences from, String k, String to, String def) {
        if (from.contains(k)) {
            String v = from.getString(k, def);
            AmegramConfig.setString(to, v != null ? v : def);
        }
    }
}
