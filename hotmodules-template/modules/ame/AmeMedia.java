package com.amegram.mods.ame;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ✦ AME MEDIA ENGINE ✦
 * Manages custom local and remote profile media assets (photos, GIFs, videos):
 * - Seamless local media resolution ("local:photo1", "local:banner_1").
 * - Gallery file picking and persistent caching in internal storage.
 * - Format detection (JPG, PNG, GIF, MP4/video).
 * - Bitmap memory cache for instant, stutter-free profile and studio preview rendering.
 */
public class AmeMedia implements NotificationCenter.NotificationCenterDelegate {

    public static final int REQ_PICK_AME_MEDIA = 9982;
    private static final String DIR_NAME = "ame_media";
    private static AmeMedia instance;

    private static String pendingKey = null;
    private static Runnable onMediaPickedCallback = null;

    private final LruCache<String, Bitmap> bitmapCache = new LruCache<>(30);

    public static synchronized AmeMedia getInstance() {
        if (instance == null) {
            instance = new AmeMedia();
            NotificationCenter.getGlobalInstance().addObserver(instance, NotificationCenter.onActivityResultReceived);
        }
        return instance;
    }

    public static File getMediaDir() {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        File dir = new File(ctx.getFilesDir(), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public static String cleanKey(String key) {
        if (key == null) return "";
        String clean = key.trim().toLowerCase();
        if (clean.startsWith("local:")) {
            clean = clean.substring("local:".length()).trim();
        }
        return clean.replaceAll("[\\s/\\\\:]+", "_");
    }

    public static File getMediaFile(String key) {
        String clean = cleanKey(key);
        if (TextUtils.isEmpty(clean)) return null;
        File dir = getMediaDir();
        if (dir == null) return null;
        return new File(dir, clean + ".dat");
    }

    public static boolean hasMedia(String key) {
        File file = getMediaFile(key);
        return file != null && file.exists() && file.length() > 0;
    }

    public static String getMediaType(String key) {
        File file = getMediaFile(key);
        if (file == null || !file.exists() || file.length() < 12) return "image";
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] header = new byte[12];
            int read = in.read(header);
            if (read >= 3 && header[0] == 'G' && header[1] == 'I' && header[2] == 'F') {
                return "gif";
            }
            if (read >= 8 && header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') {
                return "video";
            }
        } catch (Throwable ignore) {}
        return "image";
    }

    public boolean saveMedia(String key, Uri sourceUri) {
        Context ctx = ApplicationLoader.applicationContext;
        if (ctx == null || sourceUri == null) return false;
        String clean = cleanKey(key);
        if (TextUtils.isEmpty(clean)) return false;

        File outFile = getMediaFile(clean);
        if (outFile == null) return false;

        try {
            try {
                ctx.getContentResolver().takePersistableUriPermission(sourceUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Throwable ignore) {}

            try (InputStream in = ctx.getContentResolver().openInputStream(sourceUri);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                if (in == null) return false;
                byte[] buf = new byte[8192];
                int n;
                long total = 0;
                while ((n = in.read(buf)) != -1) {
                    total += n;
                    if (total > 150L * 1024 * 1024) { // 150MB limit
                        throw new Exception("File exceeds 150MB");
                    }
                    fos.write(buf, 0, n);
                }
                fos.flush();
            }

            bitmapCache.remove(clean);

            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme);
            return true;
        } catch (Throwable t) {
            FileLog.e("AmeMedia: Failed to save media for key=" + key, t);
            if (outFile.exists()) outFile.delete();
            return false;
        }
    }

    public Bitmap getBitmap(String key) {
        String clean = cleanKey(key);
        if (TextUtils.isEmpty(clean)) return null;

        Bitmap cached = bitmapCache.get(clean);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        File file = getMediaFile(clean);
        if (file == null || !file.exists()) return null;

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            int maxDim = 1920;
            opts.inSampleSize = 1;
            while ((opts.outWidth / opts.inSampleSize) > maxDim || (opts.outHeight / opts.inSampleSize) > maxDim) {
                opts.inSampleSize *= 2;
            }
            opts.inJustDecodeBounds = false;
            Bitmap bmp = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bmp != null) {
                bitmapCache.put(clean, bmp);
                return bmp;
            }
        } catch (Throwable t) {
            FileLog.e("AmeMedia: Failed to decode bitmap", t);
        }
        return null;
    }

    public static List<String> findMissingLocalKeys(String xml) {
        List<String> missing = new ArrayList<>();
        if (TextUtils.isEmpty(xml)) return missing;

        Pattern pattern = Pattern.compile("src=[\"']local:([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String rawKey = matcher.group(1);
            if (!TextUtils.isEmpty(rawKey)) {
                String clean = cleanKey(rawKey);
                if (!hasMedia(clean) && !missing.contains(clean)) {
                    missing.add(clean);
                }
            }
        }
        return missing;
    }

    public static List<String> findAllLocalKeys(String xml) {
        List<String> keys = new ArrayList<>();
        if (TextUtils.isEmpty(xml)) return keys;

        Pattern pattern = Pattern.compile("src=[\"']local:([^\"']+)[\"']");
        Matcher matcher = pattern.matcher(xml);
        while (matcher.find()) {
            String rawKey = matcher.group(1);
            if (!TextUtils.isEmpty(rawKey)) {
                String clean = cleanKey(rawKey);
                if (!keys.contains(clean)) {
                    keys.add(clean);
                }
            }
        }
        return keys;
    }

    public static void pickMedia(Activity activity, String key, Runnable onPicked) {
        if (activity == null) return;
        pendingKey = cleanKey(key);
        onMediaPickedCallback = onPicked;

        getInstance(); // ensure registered

        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(Intent.createChooser(intent, "Виберіть фото або відео для «" + pendingKey + "»"), REQ_PICK_AME_MEDIA);
        } catch (Throwable t) {
            FileLog.e("AmeMedia: pickMedia failed", t);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.onActivityResultReceived) {
            if (args != null && args.length >= 3) {
                int reqCode = (int) args[0];
                int resultCode = (int) args[1];
                Intent data = (Intent) args[2];

                if (reqCode == REQ_PICK_AME_MEDIA) {
                    if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null && !TextUtils.isEmpty(pendingKey)) {
                        Uri uri = data.getData();
                        saveMedia(pendingKey, uri);
                        if (onMediaPickedCallback != null) {
                            AndroidUtilities.runOnUIThread(onMediaPickedCallback);
                            onMediaPickedCallback = null;
                        }
                    }
                    pendingKey = null;
                }
            }
        }
    }
}
