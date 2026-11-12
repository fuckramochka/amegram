package app.miogram.bridge.playlist;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Central manager for creating, storing, loading, and playing .yumi playlists.
 * Also handles Smart Queue operations (Play Next / Add to Queue).
 */
public final class YumiPlaylistManager {

    private static final String DIR_NAME = "yumi_playlists";

    private YumiPlaylistManager() {
    }

    public static File getPlaylistsDirectory() {
        File dir = new File(ApplicationLoader.getFilesDirFixed(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static List<YumiPlaylist> getAllPlaylists() {
        List<YumiPlaylist> list = new ArrayList<>();
        File dir = getPlaylistsDirectory();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yumi") || name.endsWith(".yumiplay"));
        if (files != null) {
            for (File f : files) {
                YumiPlaylist p = YumiPlaylist.fromFile(f);
                if (p != null) {
                    list.add(p);
                }
            }
        }
        Collections.sort(list, (a, b) -> Long.compare(b.updatedAt, a.updatedAt));
        return list;
    }

    public static YumiPlaylist getPlaylist(String id) {
        if (TextUtils.isEmpty(id)) return null;
        File file = new File(getPlaylistsDirectory(), id + ".yumi");
        return YumiPlaylist.fromFile(file);
    }

    public static boolean savePlaylist(YumiPlaylist playlist) {
        if (playlist == null || TextUtils.isEmpty(playlist.id)) return false;
        playlist.updatedAt = System.currentTimeMillis();
        File file = new File(getPlaylistsDirectory(), playlist.id + ".yumi");
        return playlist.saveToFile(file);
    }

    public static boolean deletePlaylist(String id) {
        if (TextUtils.isEmpty(id)) return false;
        File file = new File(getPlaylistsDirectory(), id + ".yumi");
        return file.exists() && file.delete();
    }

    public static YumiPlaylist createFromQueue(String title, List<MessageObject> queue) {
        if (TextUtils.isEmpty(title)) {
            title = "Playlist " + AndroidUtilities.formatDateAudio(System.currentTimeMillis() / 1000, true);
        }
        YumiPlaylist pl = new YumiPlaylist(title, "Yumigram");
        if (queue != null) {
            for (MessageObject mo : queue) {
                if (mo != null && mo.isMusic()) {
                    YumiPlaylist.Track track = YumiPlaylist.Track.fromMessageObject(mo);
                    if (track != null) {
                        pl.tracks.add(track);
                    }
                }
            }
        }
        savePlaylist(pl);
        return pl;
    }

    public static void playPlaylist(YumiPlaylist playlist, int startIndex, boolean shuffle) {
        if (playlist == null || playlist.tracks.isEmpty()) return;
        int account = UserConfig.selectedAccount;
        ArrayList<MessageObject> list = new ArrayList<>();
        for (YumiPlaylist.Track t : playlist.tracks) {
            list.add(t.toMessageObject(account));
        }
        if (list.isEmpty()) return;

        int index = Math.max(0, Math.min(list.size() - 1, startIndex));
        if (shuffle) {
            SharedConfig.shuffleMusic = true;
            Collections.shuffle(list);
            index = 0;
        }

        MessageObject current = list.get(index);
        MediaController.getInstance().setPlaylist(list, current, 0);
    }

    public static void reorderTracks(YumiPlaylist playlist, int from, int to) {
        if (playlist == null || from < 0 || to < 0 || from >= playlist.tracks.size() || to >= playlist.tracks.size()) {
            return;
        }
        YumiPlaylist.Track t = playlist.tracks.remove(from);
        playlist.tracks.add(to, t);
        savePlaylist(playlist);
    }

    public static void addTrack(YumiPlaylist playlist, MessageObject mo) {
        if (playlist == null || mo == null || !mo.isMusic()) return;
        YumiPlaylist.Track t = YumiPlaylist.Track.fromMessageObject(mo);
        if (t != null) {
            playlist.tracks.add(t);
            savePlaylist(playlist);
        }
    }

    public static void removeTrack(YumiPlaylist playlist, int index) {
        if (playlist == null || index < 0 || index >= playlist.tracks.size()) return;
        playlist.tracks.remove(index);
        savePlaylist(playlist);
    }

    public static File exportPlaylistFile(YumiPlaylist playlist) {
        if (playlist == null) return null;
        File cacheDir = new File(ApplicationLoader.applicationContext.getCacheDir(), "exported_playlists");
        if (!cacheDir.exists()) cacheDir.mkdirs();
        String safeName = (playlist.title != null ? playlist.title.replaceAll("[^a-zA-Z0-9А-Яа-яіІїЇєЄ\\-_ ]", "_") : "playlist");
        File dest = new File(cacheDir, safeName + ".yumi");
        if (playlist.saveToFile(dest)) {
            return dest;
        }
        return null;
    }

    public static void sharePlaylistFile(Activity activity, YumiPlaylist playlist) {
        if (activity == null || playlist == null) return;
        File file = exportPlaylistFile(playlist);
        if (file == null || !file.exists()) return;
        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, playlist.title);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, playlist.title));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /* =========================================================================
     * SMART QUEUE (Play Next / Add to Queue)
     * ========================================================================= */

    public static boolean playNext(MessageObject newTrack) {
        if (newTrack == null || !newTrack.isMusic()) return false;
        MediaController mc = MediaController.getInstance();
        ArrayList<MessageObject> playlist = mc.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            ArrayList<MessageObject> list = new ArrayList<>();
            list.add(newTrack);
            return mc.setPlaylist(list, newTrack, 0);
        }

        MessageObject playing = mc.getPlayingMessageObject();
        int idx = -1;
        if (playing != null) {
            idx = playlist.indexOf(playing);
            if (idx == -1) {
                for (int i = 0; i < playlist.size(); i++) {
                    MessageObject m = playlist.get(i);
                    if (m != null && m.getId() == playing.getId() && m.getDialogId() == playing.getDialogId()) {
                        idx = i;
                        break;
                    }
                }
            }
        }
        if (idx >= 0 && idx + 1 < playlist.size()) {
            playlist.add(idx + 1, newTrack);
        } else {
            playlist.add(newTrack);
        }
        NotificationCenter.getInstance(newTrack.currentAccount).postNotificationName(NotificationCenter.musicDidLoad);
        return true;
    }

    public static boolean addToQueue(MessageObject newTrack) {
        if (newTrack == null || !newTrack.isMusic()) return false;
        MediaController mc = MediaController.getInstance();
        ArrayList<MessageObject> playlist = mc.getPlaylist();
        if (playlist == null || playlist.isEmpty()) {
            ArrayList<MessageObject> list = new ArrayList<>();
            list.add(newTrack);
            return mc.setPlaylist(list, newTrack, 0);
        }
        playlist.add(newTrack);
        NotificationCenter.getInstance(newTrack.currentAccount).postNotificationName(NotificationCenter.musicDidLoad);
        return true;
    }
}
