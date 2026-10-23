package app.miogram.bridge.wall;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.miogram.bridge.wall.WallModels.Wall;
import app.miogram.bridge.wall.WallModels.WallAction;
import app.miogram.bridge.wall.WallModels.WallComment;
import app.miogram.bridge.wall.WallModels.WallElement;
import app.miogram.bridge.wall.WallModels.WallPoll;

/**
 * Локальне сховище стіни: JSON у SharedPreferences + медіатека у files/wall/&lt;ownerId&gt;/.
 * Свідомо без мережі: бета працює офлайн, а майбутній синк з VPS лише
 * вивантажуватиме цей самий JSON (див. {@link #exportWall} / {@link #importWall}).
 */
public final class WallStorage {

    public interface SavedFileCallback {
        void onSaved(String path, String thumbPath, String fileName, String mime, long size, int width, int height);
    }

    public interface ErrorCallback {
        void onError(String message);
    }

    private static final String PREFS = "wall_store";
    private static final String KEY_WALL_PREFIX = "wall_";
    private static final Gson gson = new Gson();
    private static final Type MAP_TYPE = new TypeToken<java.util.LinkedHashMap<String, Object>>() {
    }.getType();

    /** Дешевий кеш кількості елементів — для чипа на банері профілю (без парсингу JSON щокадру). */
    private static final java.util.HashMap<Long, Integer> countCache = new java.util.HashMap<>();

    private WallStorage() {
    }

    private static SharedPreferences prefs() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(long ownerId) {
        return KEY_WALL_PREFIX + ownerId;
    }

    // ---------- завантаження / збереження ----------

    public static Wall loadWall(long ownerId, String ownerName, String ownerUsername) {
        Wall wall = null;
        SharedPreferences p = prefs();
        if (p != null) {
            String json = p.getString(key(ownerId), "");
            if (!TextUtils.isEmpty(json)) {
                try {
                    wall = gson.fromJson(json, Wall.class);
                } catch (Throwable e) {
                    FileLog.e("wall: broken json for " + ownerId, e);
                }
            }
        }
        if (wall == null) {
            wall = WallModels.newWall(ownerId, ownerName, ownerUsername);
        }
        sanitize(wall);
        if (!TextUtils.isEmpty(ownerName) && TextUtils.isEmpty(wall.ownerName)) {
            wall.ownerName = ownerName;
        }
        return wall;
    }

    public static void saveWall(Wall wall) {
        if (wall == null) return;
        wall.updatedAt = System.currentTimeMillis();
        synchronized (countCache) {
            countCache.put(wall.ownerId, wall.elements == null ? 0 : wall.elements.size());
        }
        SharedPreferences p = prefs();
        if (p == null) return;
        try {
            p.edit().putString(key(wall.ownerId), gson.toJson(wall)).apply();
        } catch (Throwable e) {
            FileLog.e("wall: save failed", e);
        }
    }

    /** Кількість елементів стіни власника (кеш, рідкий паблік — банер-чип). */
    public static int elementCount(long ownerId) {
        Integer cached;
        synchronized (countCache) {
            cached = countCache.get(ownerId);
        }
        if (cached != null) return cached;
        SharedPreferences p = prefs();
        if (p == null) return 0;
        int count = 0;
        String json = p.getString(key(ownerId), "");
        if (!TextUtils.isEmpty(json)) {
            try {
                Wall w = gson.fromJson(json, Wall.class);
                count = w == null || w.elements == null ? 0 : w.elements.size();
            } catch (Throwable ignore) {
            }
        }
        synchronized (countCache) {
            countCache.put(ownerId, count);
        }
        return count;
    }

    private static void sanitize(Wall wall) {
        if (wall.elements == null) wall.elements = new ArrayList<>();
        if (wall.editorIds == null) wall.editorIds = new ArrayList<>();
        if (TextUtils.isEmpty(wall.editMode)) wall.editMode = WallModels.EDIT_EVERYONE;
        for (WallElement e : wall.elements) {
            if (e.likes == null) e.likes = new java.util.LinkedHashMap<>();
            if (e.comments == null) e.comments = new ArrayList<>();
            if (TextUtils.isEmpty(e.id)) e.id = java.util.UUID.randomUUID().toString();
            if (TextUtils.isEmpty(e.type)) e.type = WallModels.TYPE_TEXT;
            if (TextUtils.isEmpty(e.style)) e.style = WallModels.STYLE_GLASS;
            if (TextUtils.isEmpty(e.size)) e.size = "half";
            for (WallComment c : e.comments) {
                if (c.likes == null) c.likes = new java.util.LinkedHashMap<>();
                if (TextUtils.isEmpty(c.id)) c.id = java.util.UUID.randomUUID().toString();
            }
            if (e.poll != null && e.poll.votes == null) {
                e.poll.votes = new java.util.LinkedHashMap<>();
            }
        }
    }

    // ---------- елементи ----------

    public static WallElement findElementOfAuthor(Wall wall, long authorId) {
        if (wall == null || wall.elements == null) return null;
        for (WallElement e : wall.elements) {
            if (e.authorId == authorId) return e;
        }
        return null;
    }

    public static WallElement findElement(Wall wall, String elementId) {
        if (wall == null || wall.elements == null || elementId == null) return null;
        for (WallElement e : wall.elements) {
            if (elementId.equals(e.id)) return e;
        }
        return null;
    }

    public static void upsertElement(Wall wall, WallElement element) {
        if (wall == null || element == null) return;
        if (wall.elements == null) wall.elements = new ArrayList<>();
        element.updatedAt = System.currentTimeMillis();
        for (int i = 0; i < wall.elements.size(); i++) {
            if (element.id.equals(wall.elements.get(i).id)) {
                wall.elements.set(i, element);
                saveWall(wall);
                return;
            }
        }
        wall.elements.add(0, element);
        saveWall(wall);
    }

    public static void removeElement(Wall wall, String elementId) {
        if (wall == null || wall.elements == null || elementId == null) return;
        for (int i = 0; i < wall.elements.size(); i++) {
            if (elementId.equals(wall.elements.get(i).id)) {
                wall.elements.remove(i);
                saveWall(wall);
                return;
            }
        }
    }

    public static void toggleLike(Wall wall, String elementId, long userId, boolean like) {
        WallElement e = findElement(wall, elementId);
        if (e == null) return;
        if (e.likes == null) e.likes = new java.util.LinkedHashMap<>();
        String k = String.valueOf(userId);
        if (like) {
            e.likes.put(k, true);
        } else {
            e.likes.remove(k);
        }
        saveWall(wall);
    }

    // ---------- коментарі ----------

    public static void addComment(Wall wall, String elementId, WallComment comment) {
        WallElement e = findElement(wall, elementId);
        if (e == null || comment == null) return;
        if (e.comments == null) e.comments = new ArrayList<>();
        e.comments.add(comment);
        saveWall(wall);
    }

    public static void removeComment(Wall wall, String elementId, String commentId) {
        WallElement e = findElement(wall, elementId);
        if (e == null || e.comments == null || commentId == null) return;
        for (int i = 0; i < e.comments.size(); i++) {
            if (commentId.equals(e.comments.get(i).id)) {
                e.comments.remove(i);
                saveWall(wall);
                return;
            }
        }
    }

    public static void toggleCommentLike(Wall wall, String elementId, String commentId, long userId, boolean like) {
        WallElement e = findElement(wall, elementId);
        if (e == null || e.comments == null) return;
        for (WallComment c : e.comments) {
            if (commentId != null && commentId.equals(c.id)) {
                if (c.likes == null) c.likes = new java.util.LinkedHashMap<>();
                if (like) {
                    c.likes.put(String.valueOf(userId), true);
                } else {
                    c.likes.remove(String.valueOf(userId));
                }
                saveWall(wall);
                return;
            }
        }
    }

    // ---------- опитування ----------

    public static void vote(Wall wall, String elementId, long userId, int value) {
        WallElement e = findElement(wall, elementId);
        if (e == null || e.poll == null) return;
        int min = Math.min(e.poll.min, e.poll.max);
        int max = Math.max(e.poll.min, e.poll.max);
        int clamped = Math.max(min, Math.min(max, value));
        if (e.poll.votes == null) e.poll.votes = new java.util.LinkedHashMap<>();
        e.poll.votes.put(String.valueOf(userId), clamped);
        saveWall(wall);
    }

    // ---------- права ----------

    /** Власник стіни. */
    public static boolean isOwner(Wall wall, long userId) {
        return wall != null && wall.ownerId == userId;
    }

    /** Автор елемента (може редагувати/видаляти своє навіть після зміни прав). */
    public static boolean isAuthor(WallElement element, long userId) {
        return element != null && element.authorId == userId;
    }

    /** Чи може користувач поставити/змінити свій елемент. */
    public static boolean canEdit(Wall wall, long userId) {
        if (wall == null) return false;
        if (isOwner(wall, userId)) return true;
        if (findElementOfAuthor(wall, userId) != null) return true;
        String mode = wall.editMode == null ? WallModels.EDIT_EVERYONE : wall.editMode;
        switch (mode) {
            case WallModels.EDIT_OWNER:
                return false;
            case WallModels.EDIT_EVERYONE:
                return true;
            case WallModels.EDIT_CONTACTS:
                return isContact(userId);
            case WallModels.EDIT_SELECTED:
                return wall.editorIds != null && wall.editorIds.contains(userId);
            case WallModels.EDIT_FOLDER:
                return isInFolder(userId, wall.folderIndex);
            default:
                return false;
        }
    }

    /** Чи може користувач видаляти чужі елементи (автор стіни). */
    public static boolean canModerate(Wall wall, long userId) {
        return isOwner(wall, userId);
    }

    public static boolean isContact(long userId) {
        try {
            ContactsController cc = ContactsController.getInstance(UserConfig.selectedAccount);
            return cc != null && cc.isContact(userId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Чи є діалог користувача у вибраній папці чатів. */
    public static boolean isInFolder(long userId, int folderIndex) {
        try {
            MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            if (mc == null || mc.dialogFilters == null || mc.dialogFilters.isEmpty()) return false;
            int idx = Math.max(0, Math.min(mc.dialogFilters.size() - 1, folderIndex));
            MessagesController.DialogFilter filter = mc.dialogFilters.get(idx);
            if (filter == null) return false;
            return filter.includesDialog(AccountInstance.getInstance(UserConfig.selectedAccount), userId);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static String folderName(int index) {
        try {
            MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            if (mc != null && mc.dialogFilters != null && index >= 0 && index < mc.dialogFilters.size()) {
                MessagesController.DialogFilter f = mc.dialogFilters.get(index);
                if (f != null && !TextUtils.isEmpty(f.name)) return f.name;
            }
        } catch (Throwable ignore) {
        }
        return "Папка " + (index + 1);
    }

    // ---------- користувачі ----------

    public static long currentUserId() {
        try {
            return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignore) {
            return 0;
        }
    }

    public static String displayName(long userId) {
        try {
            MessagesController mc = MessagesController.getInstance(UserConfig.selectedAccount);
            TLRPC.User user = mc.getUser(userId);
            if (user != null) {
                String name = UserObject.getUserName(user);
                if (!TextUtils.isEmpty(name)) return name;
            }
        } catch (Throwable ignore) {
        }
        return "Користувач";
    }

    public static String username(long userId) {
        try {
            TLRPC.User user = MessagesController.getInstance(UserConfig.selectedAccount).getUser(userId);
            if (user != null && !TextUtils.isEmpty(user.username)) return user.username;
        } catch (Throwable ignore) {
        }
        return "";
    }

    // ---------- медіа ----------

    public static File mediaDir(long ownerId) {
        Context ctx = ApplicationLoader.applicationContext;
        File dir = new File(ctx.getFilesDir(), "wall/" + ownerId);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /** Копіює обраний файл у сховище стіни (фон, з колбеком на UI-потоці). */
    public static void importFile(Context context, Uri uri, long ownerId, String hintMime, SavedFileCallback cb, ErrorCallback err) {
        if (context == null || uri == null) {
            if (err != null) err.onError("Файл недоступний");
            return;
        }
        final Context app = context.getApplicationContext();
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ContentResolver cr = app.getContentResolver();
                String mime = hintMime;
                if (TextUtils.isEmpty(mime) || "application/octet-stream".equals(mime)) {
                    mime = cr.getType(uri);
                }
                if (TextUtils.isEmpty(mime)) mime = "application/octet-stream";
                String display = queryDisplayName(cr, uri);
                if (TextUtils.isEmpty(display)) {
                    display = "file_" + System.currentTimeMillis();
                }
                long size = querySize(cr, uri);
                String ext = extensionFor(mime, display);
                String fileName = sanitizeName(display);
                if (TextUtils.isEmpty(ext) || !fileName.toLowerCase(Locale.ROOT).endsWith("." + ext)) {
                    fileName = fileName + (_extOk(ext) ? "." + ext : "");
                }
                File dir = mediaDir(ownerId);
                File out = uniqueFile(dir, fileName);
                try (InputStream in = cr.openInputStream(uri);
                     OutputStream os = new FileOutputStream(out)) {
                    if (in == null) throw new IllegalStateException("empty stream");
                    byte[] buf = new byte[32 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        os.write(buf, 0, n);
                    }
                }
                if (size <= 0) size = out.length();
                int width = 0, height = 0;
                String thumb = "";
                String lowerMime = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
                if (lowerMime.startsWith("image/") || lowerMime.startsWith("video/")) {
                    if (lowerMime.startsWith("video/")) {
                        try {
                            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                            mmr.setDataSource(out.getAbsolutePath());
                            String w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                            String h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                            if (w != null) width = Integer.parseInt(w);
                            if (h != null) height = Integer.parseInt(h);
                            Bitmap frame = mmr.getFrameAtTime(700 * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
                            if (frame != null) {
                                File tf = new File(dir, out.getName() + ".thumb.jpg");
                                try (OutputStream tos = new FileOutputStream(tf)) {
                                    frame.compress(Bitmap.CompressFormat.JPEG, 82, tos);
                                }
                                frame.recycle();
                                thumb = tf.getAbsolutePath();
                            }
                            mmr.release();
                        } catch (Throwable ignore) {
                        }
                    } else if (!lowerMime.contains("gif")) {
                        try {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(out.getAbsolutePath(), o);
                            width = o.outWidth;
                            height = o.outHeight;
                        } catch (Throwable ignore) {
                        }
                    }
                }
                final String fPath = out.getAbsolutePath();
                final String fThumb = thumb;
                final String fName = fileName;
                final String fMime = mime;
                final long fSize = size;
                final int fW = width, fH = height;
                AndroidUtilities.runOnUIThread(() -> {
                    if (cb != null) cb.onSaved(fPath, fThumb, fName, fMime, fSize, fW, fH);
                });
            } catch (Throwable e) {
                FileLog.e("wall: import failed", e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (err != null) err.onError(e.getMessage() == null ? "Не вдалося скопіювати файл" : e.getMessage());
                });
            }
        });
    }

    private static boolean _extOk(String ext) {
        return !TextUtils.isEmpty(ext) && ext.length() <= 5;
    }

    private static String queryDisplayName(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignore) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "" : path;
    }

    private static long querySize(ContentResolver cr, Uri uri) {
        try (Cursor c = cr.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !c.isNull(idx)) return c.getLong(idx);
            }
        } catch (Throwable ignore) {
        }
        return 0;
    }

    private static String extensionFor(String mime, String display) {
        if (!TextUtils.isEmpty(display)) {
            int dot = display.lastIndexOf('.');
            if (dot >= 0 && dot < display.length() - 1) {
                String ext = display.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.length() <= 5 && ext.matches("[a-z0-9]+")) return ext;
            }
        }
        if (!TextUtils.isEmpty(mime)) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (!TextUtils.isEmpty(ext)) return ext;
        }
        return "bin";
    }

    private static String sanitizeName(String name) {
        if (TextUtils.isEmpty(name)) return "file";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == ' ') {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String s = sb.toString().trim();
        return s.isEmpty() ? "file" : s;
    }

    private static File uniqueFile(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (f.exists()) {
            f = new File(dir, base + "_" + i + ext);
            i++;
        }
        return f;
    }

    /** Зберегти намальоване полотно як PNG. */
    public static String savePng(long ownerId, Bitmap bitmap) {
        try {
            File dir = mediaDir(ownerId);
            File out = new File(dir, "draw_" + System.currentTimeMillis() + ".png");
            try (OutputStream os = new FileOutputStream(out)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os);
            }
            return out.getAbsolutePath();
        } catch (Throwable e) {
            FileLog.e("wall: png save failed", e);
            return "";
        }
    }

    public static void deleteFile(String path) {
        if (TextUtils.isEmpty(path)) return;
        try {
            File f = new File(path);
            if (f.exists()) f.delete();
        } catch (Throwable ignore) {
        }
    }

    // ---------- серіалізація / синк ----------

    public static String exportWall(Wall wall) {
        try {
            return gson.toJson(wall);
        } catch (Throwable e) {
            return "";
        }
    }

    public static Wall importWall(String json, long ownerId, String ownerName) {
        try {
            Wall wall = gson.fromJson(json, Wall.class);
            if (wall == null) return null;
            wall.ownerId = ownerId;
            if (TextUtils.isEmpty(wall.ownerName)) wall.ownerName = ownerName;
            sanitize(wall);
            saveWall(wall);
            return wall;
        } catch (Throwable e) {
            FileLog.e("wall: import failed", e);
            return null;
        }
    }

    public static void clearWall(Wall wall) {
        if (wall == null) return;
        for (WallElement e : wall.elements) {
            deleteFile(e.mediaPath);
            deleteFile(e.thumbPath);
        }
        wall.elements = new ArrayList<>();
        saveWall(wall);
    }

    public static int totalLikes(Wall wall) {
        int n = 0;
        if (wall != null && wall.elements != null) {
            for (WallElement e : wall.elements) n += e.likeCount();
        }
        return n;
    }

    public static int totalComments(Wall wall) {
        int n = 0;
        if (wall != null && wall.elements != null) {
            for (WallElement e : wall.elements) n += e.commentCount();
        }
        return n;
    }

    /** Заготовка під майбутній синк з VPS: JSON-конверт. */
    public static String buildSyncEnvelope(Wall wall) {
        if (wall == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"version\":1,\"ownerId\":").append(wall.ownerId)
                    .append(",\"updatedAt\":").append(wall.updatedAt)
                    .append(",\"payload\":").append(exportWall(wall)).append("}");
            return sb.toString();
        } catch (Throwable e) {
            return "";
        }
    }
}
