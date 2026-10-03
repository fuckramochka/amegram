package app.amegram.core.security;

/**
 * Дозволи модулів/плагінів. Закритий список: невідомий дозвіл у маніфесті
 * відхиляє модуль. Жодних «всіх дозволів одним прапорцем».
 */
public final class Permissions {

    public static final String NETWORK = "network";
    public static final String STORAGE = "storage";
    public static final String HOOK_NET = "hook_net";
    public static final String HOOK_UI = "hook_ui";
    public static final String NOTIFICATIONS = "notifications";
    public static final String BACKGROUND = "background";

    private static final String[] ALL = {
            NETWORK, STORAGE, HOOK_NET, HOOK_UI, NOTIFICATIONS, BACKGROUND
    };

    private Permissions() {
    }

    public static boolean isKnown(String permission) {
        if (permission == null) {
            return false;
        }
        for (String known : ALL) {
            if (known.equals(permission)) {
                return true;
            }
        }
        return false;
    }
}
