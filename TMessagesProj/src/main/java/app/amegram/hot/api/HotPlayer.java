package app.amegram.hot.api;

import android.content.Context;

/** Interface for the optional music-provider search module used by MD3. */
public interface HotPlayer {
    /** Відкрити екран глобального пошуку музики з 6 джерел. */
    void openMusicSearch(Context context);
}
