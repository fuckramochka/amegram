package org.telegram.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;

public class LauncherIconController {

    private static final String KEY_SAVED_ICON = "saved_launcher_icon_key";

    public static void tryFixLauncherIconIfNeeded() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return;

        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        String savedKey = prefs.getString(KEY_SAVED_ICON, null);
        LauncherIcon targetIcon = LauncherIcon.EXTERALESS;
        if (savedKey != null) {
            for (LauncherIcon icon : LauncherIcon.values()) {
                if (icon.key.equals(savedKey)) {
                    targetIcon = icon;
                    break;
                }
            }
        }

        for (LauncherIcon icon : LauncherIcon.values()) {
            if (isEnabled(icon)) {
                return;
            }
        }

        setIcon(targetIcon);
    }

    public static boolean isEnabled(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return false;

        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        String savedKey = prefs.getString(KEY_SAVED_ICON, null);
        if (savedKey != null) {
            return icon.key.equals(savedKey);
        }

        int i = ctx.getPackageManager().getComponentEnabledSetting(icon.getComponentName(ctx));
        // While user hasn't made a choice, default to EXTERALESS:
        return i == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                || (i == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon == LauncherIcon.EXTERALESS);
    }

    public static void setIcon(LauncherIcon icon) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || icon == null) return;

        PackageManager pm = ctx.getPackageManager();

        // 1. Persist chosen icon in preferences
        MessagesController.getGlobalMainSettings().edit().putString(KEY_SAVED_ICON, icon.key).apply();

        // 2. Enable target component FIRST to prevent Android 14-16 / Samsung One UI from seeing 0 launcher components
        try {
            pm.setComponentEnabledSetting(
                    icon.getComponentName(ctx),
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
            );
        } catch (Throwable ignore) {}

        // 3. Disable all other launcher components
        for (LauncherIcon i : LauncherIcon.values()) {
            if (i != icon) {
                try {
                    pm.setComponentEnabledSetting(
                            i.getComponentName(ctx),
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                    );
                } catch (Throwable ignore) {}
            }
        }
    }

    public enum LauncherIcon {
        EXTERALESS("ExteralessIcon", R.drawable.exteraless_icon_background,
                R.drawable.exteraless_icon_foreground, R.string.AppIconExteraless),
        BLUEPRINT("BlueprintIcon", R.drawable.blueprint_icon_background,
                R.drawable.blueprint_icon_foreground, R.string.AppIconBlueprint),
        RED("RedIcon", R.drawable.red_icon_background,
                R.drawable.red_icon_foreground, R.string.AppIconRed),
        NYA("NyaIcon", R.drawable.nya_icon_background,
                R.drawable.nya_icon_foreground, R.string.AppIconNya),
        AYU("AyuIcon", R.drawable.ayu_icon_background,
                R.drawable.ayu_icon_foreground, R.string.AppIconAyu),
        QUACK("QuackIcon", R.drawable.quack_icon_background,
                R.drawable.quack_icon_foreground, R.string.AppIconQuack),
        TELEGRAM("TelegramIcon", R.drawable.icon_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconTelegramOriginal),
        VINTAGE("VintageIcon", R.drawable.icon_6_background_sa, R.mipmap.icon_6_foreground_sa, R.string.AppIconVintage),
        AQUA("AquaIcon", R.drawable.icon_4_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconAqua),
        PREMIUM("PremiumIcon", R.drawable.icon_3_background_sa, R.mipmap.icon_3_foreground_sa, R.string.AppIconPremium),
        TURBO("TurboIcon", R.drawable.icon_5_background_sa, R.mipmap.icon_5_foreground_sa, R.string.AppIconTurbo),
        NOX("NoxIcon", R.mipmap.icon_2_background_sa, R.mipmap.icon_foreground_sa, R.string.AppIconNox);

        public final String key;
        public final int background;
        public final int foreground;
        public final int title;
        public final boolean premium;

        private ComponentName componentName;

        public ComponentName getComponentName(Context ctx) {
            if (componentName == null) {
                componentName = new ComponentName(ctx.getPackageName(), "org.telegram.messenger." + key);
            }
            return componentName;
        }

        LauncherIcon(String key, int background, int foreground, int title) {
            this(key, background, foreground, title, false);
        }

        LauncherIcon(String key, int background, int foreground, int title, boolean premium) {
            this.key = key;
            this.background = background;
            this.foreground = foreground;
            this.title = title;
            this.premium = premium;
        }

    }
}
