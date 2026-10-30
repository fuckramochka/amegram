package app.exteraless.player;

import android.content.Context;
import android.os.Bundle;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

import app.exteraless.appearance.AppearanceConfig;

public final class Md3Player {

    private Md3Player() {
    }

    public static boolean enabled() {
        return true;
    }

    public static boolean miniEnabled() {
        return AppearanceConfig.md3MiniPlayer();
    }

    private static boolean handles(MessageObject messageObject) {
        return messageObject != null && messageObject.isMusic();
    }

    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        // Єдиний роутер плеєра: md3player-модуль → сучасний AudioPlayerAlert.
        return app.amegram.hot.Md3Router.create(context, resourcesProvider);
    }

    public static void open(BaseFragment fragment, PlayerMiniView source) {
        // Міні-бар веде туди ж, куди й решта точок входу в плеєр.
        app.amegram.hot.Md3Router.open(fragment, source);
    }

    public static boolean hidesContextPlayer(BaseFragment fragment, MessageObject messageObject) {
        if (!miniEnabled() || fragment == null || messageObject == null || !messageObject.isMusic()) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }
}
