package app.miogram.bridge.player;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;

import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.playlist.YumiPlaylist;

/**
 * Persists playback state (current track, precise millisecond position, and queue)
 * across app restarts. Allows seamless resumption right where the user left off.
 */
public final class YumiPlayerState {

    private static final String PREF_NAME = "yumi_player_state";
    private static final String KEY_HAS_STATE = "has_state";
    private static final String KEY_DIALOG_ID = "dialog_id";
    private static final String KEY_MSG_ID = "msg_id";
    private static final String KEY_POS_MS = "pos_ms";
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_DURATION = "duration";
    private static final String KEY_QUEUE = "queue_json";

    private YumiPlayerState() {
    }

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean hasSavedState() {
        return MiogramPlayerPrefs.isResumePlaybackEnabled() && getPrefs().getBoolean(KEY_HAS_STATE, false);
    }

    public static void saveState(MessageObject current, long positionMs, List<MessageObject> queue) {
        if (!MiogramPlayerPrefs.isResumePlaybackEnabled() || current == null || !current.isMusic()) {
            return;
        }
        try {
            SharedPreferences.Editor ed = getPrefs().edit();
            ed.putBoolean(KEY_HAS_STATE, true);
            ed.putLong(KEY_DIALOG_ID, current.getDialogId());
            ed.putInt(KEY_MSG_ID, current.getId());
            ed.putLong(KEY_POS_MS, Math.max(0, positionMs));
            ed.putString(KEY_TITLE, current.getMusicTitle());
            ed.putString(KEY_ARTIST, current.getMusicAuthor());
            ed.putInt(KEY_DURATION, (int) Math.round(current.getDuration()));

            if (queue != null && !queue.isEmpty()) {
                JSONArray arr = new JSONArray();
                int limit = Math.min(100, queue.size());
                for (int i = 0; i < limit; i++) {
                    MessageObject mo = queue.get(i);
                    if (mo != null && mo.isMusic()) {
                        YumiPlaylist.Track track = YumiPlaylist.Track.fromMessageObject(mo);
                        if (track != null) {
                            arr.put(track.toJson());
                        }
                    }
                }
                ed.putString(KEY_QUEUE, arr.toString());
            }
            ed.apply();
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static long getLastPositionMs() {
        return getPrefs().getLong(KEY_POS_MS, 0L);
    }

    public static String getLastTitle() {
        return getPrefs().getString(KEY_TITLE, "");
    }

    public static String getLastArtist() {
        return getPrefs().getString(KEY_ARTIST, "");
    }

    public static int getLastDuration() {
        return getPrefs().getInt(KEY_DURATION, 0);
    }

    public static ArrayList<MessageObject> restoreQueue(int account) {
        ArrayList<MessageObject> list = new ArrayList<>();
        String raw = getPrefs().getString(KEY_QUEUE, null);
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray arr = new JSONArray(raw);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        YumiPlaylist.Track t = YumiPlaylist.Track.fromJson(obj);
                        if (t != null) {
                            list.add(t.toMessageObject(account));
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return list;
    }

    public static boolean resumePlayback() {
        if (!hasSavedState()) return false;
        try {
            int account = UserConfig.selectedAccount;
            ArrayList<MessageObject> queue = restoreQueue(account);
            if (queue.isEmpty()) return false;

            int targetMsgId = getPrefs().getInt(KEY_MSG_ID, 0);
            long targetDialogId = getPrefs().getLong(KEY_DIALOG_ID, 0);
            long posMs = getLastPositionMs();

            MessageObject targetTrack = null;
            for (MessageObject mo : queue) {
                if (mo.getId() == targetMsgId && mo.getDialogId() == targetDialogId) {
                    targetTrack = mo;
                    break;
                }
            }
            if (targetTrack == null) {
                targetTrack = queue.get(0);
            }

            MediaController mc = MediaController.getInstance();
            mc.setPlaylist(queue, targetTrack, 0);
            if (posMs > 0) {
                mc.seekToProgressMs(targetTrack, posMs);
            }
            return true;
        } catch (Throwable e) {
            FileLog.e(e);
            return false;
        }
    }

    public static void clear() {
        getPrefs().edit().clear().apply();
    }
}
