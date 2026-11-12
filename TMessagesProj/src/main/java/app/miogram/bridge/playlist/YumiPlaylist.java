package app.miogram.bridge.playlist;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Universal Yumi Playlist (.yumi / .yumiplay) model.
 * Serializable to structured JSON for fast sharing, importing, and playback.
 */
public class YumiPlaylist {

    public static final String SIGNATURE = "yumi_playlist";
    public static final int CURRENT_VERSION = 1;

    public String id;
    public String title;
    public String author;
    public String description;
    public long createdAt;
    public long updatedAt;
    public final List<Track> tracks = new ArrayList<>();

    public YumiPlaylist() {
        this.id = UUID.randomUUID().toString();
        this.title = "Yumi Playlist";
        this.author = "Yumigram";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public YumiPlaylist(String title, String author) {
        this();
        if (!TextUtils.isEmpty(title)) {
            this.title = title;
        }
        if (!TextUtils.isEmpty(author)) {
            this.author = author;
        }
    }

    public int getDurationSeconds() {
        int total = 0;
        for (Track t : tracks) {
            total += t.duration;
        }
        return total;
    }

    public String getFormattedDuration() {
        int total = getDurationSeconds();
        int minutes = total / 60;
        int hours = minutes / 60;
        if (hours > 0) {
            int remMin = minutes % 60;
            return hours + " hr " + (remMin > 0 ? remMin + " min" : "");
        } else {
            return minutes + " min";
        }
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("yumi_type", SIGNATURE);
            json.put("version", CURRENT_VERSION);
            json.put("id", id);
            json.put("title", title);
            json.put("author", author);
            json.put("description", description != null ? description : "");
            json.put("created_at", createdAt);
            json.put("updated_at", System.currentTimeMillis());

            JSONArray arr = new JSONArray();
            for (Track t : tracks) {
                arr.put(t.toJson());
            }
            json.put("tracks", arr);
        } catch (Throwable ignore) {
        }
        return json;
    }

    public static YumiPlaylist fromJson(JSONObject json) {
        if (json == null) return null;
        try {
            String type = json.optString("yumi_type", "");
            if (!SIGNATURE.equals(type) && !json.has("tracks")) {
                return null;
            }
            YumiPlaylist p = new YumiPlaylist();
            p.id = json.optString("id", UUID.randomUUID().toString());
            p.title = json.optString("title", "Yumi Playlist");
            p.author = json.optString("author", "Yumigram");
            p.description = json.optString("description", "");
            p.createdAt = json.optLong("created_at", System.currentTimeMillis());
            p.updatedAt = json.optLong("updated_at", p.createdAt);

            JSONArray arr = json.optJSONArray("tracks");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.optJSONObject(i);
                    if (obj != null) {
                        Track t = Track.fromJson(obj);
                        if (t != null) {
                            p.tracks.add(t);
                        }
                    }
                }
            }
            return p;
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static YumiPlaylist fromFile(File file) {
        if (file == null || !file.exists() || !file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return fromJson(new JSONObject(sb.toString()));
        } catch (Throwable ignore) {
            return null;
        }
    }

    public boolean saveToFile(File destFile) {
        if (destFile == null) return false;
        try {
            File parent = destFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(destFile), StandardCharsets.UTF_8)) {
                writer.write(toJson().toString(2));
                writer.flush();
                return true;
            }
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static class Track {
        public long dialogId;
        public int messageId;
        public long docId;
        public long accessHash;
        public int dcId;
        public String title;
        public String artist;
        public int duration;
        public long size;
        public String mimeType;
        public String localPath;
        public String fileName;

        public Track() {
        }

        public static Track fromMessageObject(MessageObject mo) {
            if (mo == null) return null;
            Track t = new Track();
            t.dialogId = mo.getDialogId();
            t.messageId = mo.getId();
            t.title = mo.getMusicTitle();
            t.artist = mo.getMusicAuthor();
            t.duration = mo.getDuration();

            TLRPC.Document doc = mo.getDocument();
            if (doc != null) {
                t.docId = doc.id;
                t.accessHash = doc.access_hash;
                t.dcId = doc.dc_id;
                t.size = doc.size;
                t.mimeType = doc.mime_type;
                t.fileName = FileLoader.getDocumentFileName(doc);
            }
            if (mo.messageOwner != null && !TextUtils.isEmpty(mo.messageOwner.attachPath)) {
                t.localPath = mo.messageOwner.attachPath;
            }
            return t;
        }

        public JSONObject toJson() {
            JSONObject obj = new JSONObject();
            try {
                obj.put("dialog_id", dialogId);
                obj.put("message_id", messageId);
                obj.put("doc_id", docId);
                obj.put("access_hash", accessHash);
                obj.put("dc_id", dcId);
                obj.put("title", title != null ? title : "");
                obj.put("artist", artist != null ? artist : "");
                obj.put("duration", duration);
                obj.put("size", size);
                obj.put("mime", mimeType != null ? mimeType : "audio/mpeg");
                obj.put("file_name", fileName != null ? fileName : "");
                if (!TextUtils.isEmpty(localPath)) {
                    obj.put("local_path", localPath);
                }
            } catch (Throwable ignore) {
            }
            return obj;
        }

        public static Track fromJson(JSONObject obj) {
            if (obj == null) return null;
            Track t = new Track();
            t.dialogId = obj.optLong("dialog_id", 0);
            t.messageId = obj.optInt("message_id", 0);
            t.docId = obj.optLong("doc_id", 0);
            t.accessHash = obj.optLong("access_hash", 0);
            t.dcId = obj.optInt("dc_id", 0);
            t.title = obj.optString("title", "Unknown Title");
            t.artist = obj.optString("artist", "Unknown Artist");
            t.duration = obj.optInt("duration", 0);
            t.size = obj.optLong("size", 0);
            t.mimeType = obj.optString("mime", "audio/mpeg");
            t.fileName = obj.optString("file_name", "");
            t.localPath = obj.optString("local_path", "");
            return t;
        }

        public MessageObject toMessageObject(int account) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.id = messageId != 0 ? messageId : (int) (System.currentTimeMillis() / 1000);
            message.dialog_id = dialogId;
            message.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;

            TLRPC.TL_messageMediaDocument media = new TLRPC.TL_messageMediaDocument();
            media.document = new TLRPC.TL_document();
            media.document.id = docId;
            media.document.access_hash = accessHash;
            media.document.dc_id = dcId;
            media.document.mime_type = mimeType != null ? mimeType : "audio/mpeg";
            media.document.size = size;

            TLRPC.TL_documentAttributeAudio audio = new TLRPC.TL_documentAttributeAudio();
            audio.title = title;
            audio.performer = artist;
            audio.duration = duration;
            audio.voice = false;
            media.document.attributes.add(audio);

            if (!TextUtils.isEmpty(fileName)) {
                TLRPC.TL_documentAttributeFilename fn = new TLRPC.TL_documentAttributeFilename();
                fn.file_name = fileName;
                media.document.attributes.add(fn);
            }

            message.media = media;
            if (!TextUtils.isEmpty(localPath)) {
                message.attachPath = localPath;
            }

            return new MessageObject(account, message, false, true);
        }
    }
}
