package app.miogram.bridge.presence;

import android.content.SharedPreferences;

import org.telegram.messenger.UserConfig;

/**
 * Account ownership for linked platforms.
 *
 * Managers are device-global singletons, but presence belongs to a Telegram
 * account. Every link stamps the tg userId that created it; snapshots and
 * self cards only expose the link on that account. Legacy links (owner 0)
 * stay visible everywhere so nothing disappears after update.
 */
public final class MiogramLinkOwner {

    private MiogramLinkOwner() {
    }

    public static final String KEY_OWNER_TG_ID = "owner_tg_id";

    public static long currentTgId() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static void stamp(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            long id = currentTgId();
            if (id != 0) prefs.edit().putLong(KEY_OWNER_TG_ID, id).apply();
        } catch (Throwable ignore) {}
    }

    public static void clear(SharedPreferences prefs) {
        if (prefs == null) return;
        try {
            prefs.edit().remove(KEY_OWNER_TG_ID).apply();
        } catch (Throwable ignore) {}
    }

    public static boolean visibleFor(SharedPreferences prefs, long tgUserId) {
        if (prefs == null) return true;
        try {
            long owner = prefs.getLong(KEY_OWNER_TG_ID, 0);
            return owner == 0 || tgUserId == 0 || owner == tgUserId;
        } catch (Throwable ignore) {
            return true;
        }
    }
}
