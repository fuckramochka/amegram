package app.amegram.hot.api;

import android.content.Context;

/** Interface for the music-provider and player features. */
public interface HotPlayer {

    /** Чи активний сучасний кастомний плеєр замість стокового. */
    default boolean isModernLayoutEnabled() {
        return true;
    }

    /** Чи увімкнено спектральний візуалізатор аудіо. */
    default boolean isVisualizerEnabled() {
        return true;
    }

    /** Чи увімкнено показ синхронізованих текстів пісень (LRC). */
    default boolean isLyricsEnabled() {
        return true;
    }

    /** Відкрити екран глобального пошуку музики з 6 джерел. */
    void openMusicSearch(Context context);
}
