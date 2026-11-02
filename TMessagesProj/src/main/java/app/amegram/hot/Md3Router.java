package app.amegram.hot;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

import app.amegram.hot.api.HotMd3;

/** Routes music playback to MD3; Telegram's alert remains for voice messages. */
public final class Md3Router {

    public static final String MODULE_ID = "md3player";
    public static final String SERVICE = "md3player";

    private Md3Router() {
    }

    public static HotMd3 service() {
        try {
            HotMd3 s = HotModulesManager.getService(SERVICE);
            if (s != null && HotModulesManager.isModuleEnabled(MODULE_ID)) {
                return s;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    public static boolean sheetEnabled() {
        return true;
    }

    public static boolean miniEnabled() {
        try {
            return app.exteraless.appearance.AppearanceConfig.md3MiniPlayer();
        } catch (Throwable ignore) {
            return false;
        }
    }

    private static boolean handles(MessageObject messageObject) {
        return messageObject != null && messageObject.isMusic();
    }

    /** Music always opens MD3; non-music audio keeps Telegram's voice-message controls. */
    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        MessageObject now = null;
        try {
            now = MediaController.getInstance().getPlayingMessageObject();
        } catch (Throwable ignore) {
        }
        if (handles(now)) {
            HotMd3 s = service();
            if (s != null) {
                try {
                    if (s.sheetForMusic()) {
                        Object sheet = s.createSheet(context, resourcesProvider);
                        if (sheet instanceof BottomSheet) {
                            return (BottomSheet) sheet;
                        }
                    }
                } catch (Throwable ignore) {
                }
            }
            try {
                if (app.exteraless.player.PlayerSheet.instance != null) {
                    app.exteraless.player.PlayerSheet.instance.dismissImmediately();
                }
                return new app.exteraless.player.PlayerSheet(context, resourcesProvider);
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    /** Відкрити шит з фрагмента (з morph-переходом міні→повний, якщо є source). */
    public static void open(BaseFragment fragment, View source) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        MessageObject now = null;
        try {
            now = MediaController.getInstance().getPlayingMessageObject();
        } catch (Throwable ignore) {
        }
        if (now == null) {
            return;
        }
        BottomSheet sheet = create(fragment.getParentActivity(), fragment.getResourceProvider());
        try {
            if (sheet instanceof app.exteraless.player.PlayerSheet && source instanceof app.exteraless.player.PlayerMiniView
                    && ((app.exteraless.player.PlayerMiniView) source).canTransition()) {
                ((app.exteraless.player.PlayerSheet) sheet).setTransitionSource(
                        (app.exteraless.player.PlayerMiniView) source);
            }
        } catch (Throwable ignore) {
        }
        fragment.showDialog(sheet);
    }

    /** Міні-бар для FragmentContextView (null = стокова поведінка). */
    public static View createMiniBar(Context context, Theme.ResourcesProvider resourcesProvider,
                                     Runnable onClick, Runnable onClose) {
        HotMd3 s = service();
        if (s != null) {
            try {
                if (s.miniForMusic()) {
                    View v = s.createMiniBar(context, resourcesProvider, onClick, onClose);
                    if (v != null) {
                        return v;
                    }
                }
            } catch (Throwable ignore) {
            }
        }
        if (!miniEnabled()) return null;
        try {
            return new app.exteraless.player.PlayerBarView(context, resourcesProvider, onClick, onClose);
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
            return null;
        }
    }

    public static int miniBarHeightDp() {
        HotMd3 s = service();
        if (s != null) {
            try {
                return s.miniBarHeightDp();
            } catch (Throwable ignore) {
            }
        }
        return app.exteraless.player.PlayerBarView.HEIGHT_DP;
    }

    public static void clearPlaylistStateForMini() {
        HotMd3 s = service();
        if (s != null) {
            try {
                s.clearPlaylistState();
                return;
            } catch (Throwable ignore) {
            }
        }
        try {
            MediaController.getInstance().clearMusicPlaylistState();
        } catch (Throwable ignore) {
        }
    }
}
