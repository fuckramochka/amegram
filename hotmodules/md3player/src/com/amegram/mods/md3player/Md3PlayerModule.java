package com.amegram.mods.md3player;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotMd3;
import app.amegram.hot.api.HotModule;

/**
 * MD3-плеєр як хот-модуль (id md3player).
 * Коду плеєра в dex НЕМАЄ: усі класи беруться з батьківського лоадера
 * (app.exteraless.player.* з APK) — тут лише entry + реєстрація сервісу.
 * Тому збірка важить ~3 КБ і не дублює статики (PlayerSheet.instance один).
 */
public class Md3PlayerModule implements HotModule {

    private HotHost host;

    @Override
    public String moduleId() {
        return "md3player";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService("md3player", new Svc());
    }

    @Override
    public void onDetach() {
        try {
            if (app.exteraless.player.PlayerSheet.instance != null) {
                app.exteraless.player.PlayerSheet.instance.dismissImmediately();
            }
        } catch (Throwable ignore) {
        }
        try {
            if (host != null) {
                host.unregisterService("md3player");
            }
        } catch (Throwable ignore) {
        }
        host = null;
    }

    static final class Svc implements HotMd3 {

        private MessageObject now() {
            try {
                return MediaController.getInstance().getPlayingMessageObject();
            } catch (Throwable ignore) {
                return null;
            }
        }

        @Override
        public boolean sheetForMusic() {
            MessageObject m = now();
            return m != null && m.isMusic();
        }

        @Override
        public Object createSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
            try {
                if (app.exteraless.player.PlayerSheet.instance != null) {
                    app.exteraless.player.PlayerSheet.instance.dismissImmediately();
                }
                BottomSheet sheet = new app.exteraless.player.PlayerSheet(context, resourcesProvider);
                if (sheetForMusic()) {
                    return sheet;
                }
                return new org.telegram.ui.Components.AudioPlayerAlert(context, resourcesProvider);
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
                return new org.telegram.ui.Components.AudioPlayerAlert(context, resourcesProvider);
            }
        }

        @Override
        public boolean miniForMusic() {
            MessageObject m = now();
            return m != null && m.isMusic();
        }

        @Override
        public void clearPlaylistState() {
            try {
                MediaController.getInstance().clearMusicPlaylistState();
            } catch (Throwable ignore) {
            }
        }

        @Override
        public int miniBarHeightDp() {
            return app.exteraless.player.PlayerBarView.HEIGHT_DP;
        }

        @Override
        public View createMiniBar(Context context, Theme.ResourcesProvider resourcesProvider,
                                  Runnable onClick, Runnable onClose) {
            try {
                return new app.exteraless.player.PlayerBarView(context, resourcesProvider, onClick, onClose);
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
                return null;
            }
        }
    }
}
