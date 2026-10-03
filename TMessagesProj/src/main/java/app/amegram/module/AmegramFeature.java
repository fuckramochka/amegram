package app.amegram.module;

/**
 * Contract for every Amegram feature.
 * Features are lazy: nothing is loaded into RAM until user enables/downloads it.
 */
public interface AmegramFeature {

    /** Stable id, e.g. "ghost", "player", "badges", "guide". */
    String id();

    /** Human title, shown in Amegram -> Modules hub. */
    String title();

    /** Rough RAM footprint when loaded, for the Modules UI. */
    String ramEstimate();

    /** True when the feature code is present (built-in or downloaded .apm). */
    boolean isAvailable();

    /** True when user switched it on. */
    boolean isEnabled();

    void setEnabled(boolean enabled);

    /** Load into memory. Must be idempotent and cheap when disabled. */
    void load();

    /** Unload from memory (views, listeners, executors). */
    void unload();
}
