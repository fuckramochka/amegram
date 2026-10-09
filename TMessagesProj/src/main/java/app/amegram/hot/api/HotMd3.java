package app.amegram.hot.api;

import android.content.Context;
import android.view.View;

import org.telegram.ui.ActionBar.Theme;

/**
 * Сервіс MD3-плеєра (модуль md3player).
 * Модуль реєструє його в HotHost.registerService("md3player", ...),
 * ядро забирає через Md3Router і показує шит/міні-бар замість стокових.
 * Нема модуля — роутер іде у вбудований фолбек або сток.
 */
public interface HotMd3 {

    /** Чи брати MD3-шит для поточної музики. */
    boolean sheetForMusic();

    /** Створити повний шит плеєра (BottomSheet). */
    Object createSheet(Context context, Theme.ResourcesProvider resourcesProvider);

    /** Чи брати MD3-мінібар у чатах. */
    boolean miniForMusic();

    /** Очистити стан плейлиста при виході (міні-режим). */
    void clearPlaylistState();

    /** Висота міні-бара в dp. */
    int miniBarHeightDp();

    /** Створити міні-бар (PlayerBarView-сумісний View). */
    View createMiniBar(Context context, Theme.ResourcesProvider resourcesProvider,
                       Runnable onClick, Runnable onClose);
}
