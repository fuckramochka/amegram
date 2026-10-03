package app.amegram.module;

import android.content.Context;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import app.amegram.module.features.badges.AmegramBadgesFeature;
import app.amegram.module.features.ghost.AmegramGhostController;
import app.amegram.module.features.guide.AmegramGuideFeature;
import app.amegram.module.features.player.AmegramPlayerFeature;

/**
 * Registry of downloadable/enableable features.
 * "Хочу только плеер и призрак" — включаешь два тумблера, остальное даже не грузится.
 */
public final class AmegramFeatureManager {

    private static final Map<String, AmegramFeature> FEATURES = new LinkedHashMap<>();
    private static volatile boolean initialized;

    private AmegramFeatureManager() {
    }

    public static synchronized void init(Context appContext) {
        if (initialized) {
            return;
        }
        initialized = true;
        register(new AmegramGhostController());
        register(new AmegramPlayerFeature());
        register(new AmegramBadgesFeature());
        register(new AmegramGuideFeature());
        register(new app.amegram.module.features.net.AmegramAntiBlockFeature());
        register(new app.amegram.module.features.profile.AmegramAmeProfileFeature());
        register(new app.amegram.module.features.system.AmegramHotfixFeature());
        register(new app.amegram.module.features.vault.AmegramDoubleBottomFeature());
        // Auto-load only what user previously enabled. Everything else stays on disk.
        for (AmegramFeature f : FEATURES.values()) {
            try {
                if (f.isEnabled() && f.isAvailable()) {
                    f.load();
                }
            } catch (Throwable ignore) {
            }
        }
    }

    public static void register(AmegramFeature feature) {
        if (feature != null) {
            FEATURES.put(feature.id(), feature);
        }
    }

    public static List<AmegramFeature> all() {
        return new ArrayList<>(FEATURES.values());
    }

    public static AmegramFeature get(String id) {
        return FEATURES.get(id);
    }

    public static boolean isEnabled(String id) {
        AmegramFeature f = FEATURES.get(id);
        return f != null && f.isEnabled();
    }

    /** UI toggle handler: enable -> load now, disable -> unload now. */
    public static void setEnabled(String id, boolean enabled) {
        AmegramFeature f = FEATURES.get(id);
        if (f == null) {
            return;
        }
        f.setEnabled(enabled);
        try {
            if (enabled && f.isAvailable()) {
                f.load();
            } else if (!enabled) {
                f.unload();
            }
        } catch (Throwable ignore) {
        }
    }
}
