package app.amegram.hot.api;

import android.content.Context;

/**
 * Інтерфейс сервісу плеєра та музики (модуль player).
 */
public interface HotPlayer {
    /** Чи активний сучасний кастомний плеєр замість стокового. */
    boolean isModernLayoutEnabled();

    /** Чи увімкнено спектральний візуалізатор аудіо. */
    boolean isVisualizerEnabled();

    /** Чи увімкнено показ синхронізованих текстів пісень (LRC). */
    boolean isLyricsEnabled();

    /** Відкрити екран глобального пошуку музики з 6 джерел. */
    void openMusicSearch(Context context);
}
