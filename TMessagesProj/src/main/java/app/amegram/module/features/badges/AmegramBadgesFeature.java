package app.amegram.module.features.badges;

import app.amegram.module.AmegramConfig;
import app.amegram.module.AmegramFeature;

/**
 * Pixel badges (10 canonical styles) with lazy cloud sync.
 * Legacy: MiogramBadgeManager/Type/ArrowDrawable/BottomSheet/SupabaseBridge.
 * Rules for the clean port: no network on cold start, Room/local cache first,
 * Supabase fetch only when a profile opens, offline-first.
 */
public class AmegramBadgesFeature implements AmegramFeature {

    public static final String[] CANON_BADGES = {
            "visor", "neon_pink", "cyan_cyber", "dark_velvet", "halo",
            "horns", "prismatic", "wireframe", "glitch", "crown"
    };

    private volatile boolean loaded;

    @Override
    public String id() {
        return "badges";
    }

    @Override
    public String title() {
        return "Бейджики";
    }

    @Override
    public String ramEstimate() {
        return "~300 КБ кэш";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return AmegramConfig.getBool("badges_enabled", true);
    }

    @Override
    public void setEnabled(boolean enabled) {
        AmegramConfig.setBool("badges_enabled", enabled);
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
