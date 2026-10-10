package app.amegram.hot;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import java.io.File;

import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotVault;

/**
 * Гейт хмарного сховища у ядрі.
 * Дозволяє модулю 'vault' керувати завантаженням та шифруванням файлів.
 */
public final class HotVaultGate {

    private HotVaultGate() {
    }

    private static HotVault service() {
        try {
            return HotModulesManager.getService(HotServices.VAULT);
        } catch (Throwable ignore) {
            return null;
        }
    }

    public static boolean isLinked(int account) {
        HotVault v = service();
        return v != null && v.isLinked(account);
    }

    public static void upload(int account, File file, String fileName, String mimeType, HotVault.Callback callback) {
        HotVault v = service();
        if (v != null) {
            v.upload(account, file, fileName, mimeType, callback);
        } else if (callback != null) {
            callback.onDone(false, "Модуль хмарного сховища не підключено");
        }
    }

    public static void openBrowser() {
        HotModulesManager.openModuleScreen("vault", "browser");
    }

    /**
     * Блок 13: UI-хелпер прев'ю розшифрованого файлу сховища.
     * Фото/відео/аудіо — прямий VIEW з MIME, невідоме — chooser.
     * Протокол чанків/шифрування не чіпає, тільки Intent-шар.
     */
    public static void openPreview(Context context, File file) {
        if (context == null || file == null || !file.exists()) return;
        try {
            Uri uri;
            try {
                uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider",
                        file);
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
                return;
            }
            String mime = mimeForFile(file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mime);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (isKnownMedia(mime)) {
                    context.startActivity(intent);
                } else {
                    context.startActivity(Intent.createChooser(intent,
                            app.miogram.bridge.MiogramLocale.get("Відкрити через…", "Открыть через…", "Open with…")));
                }
            } catch (android.content.ActivityNotFoundException e) {
                try {
                    context.startActivity(Intent.createChooser(intent,
                            app.miogram.bridge.MiogramLocale.get("Відкрити через…", "Открыть через…", "Open with…")));
                } catch (Throwable ignore) {
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static String mimeForFile(File file) {
        try {
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toLowerCase();
                String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                if (m != null && !m.isEmpty()) return m;
                if (ext.equals("mp4") || ext.equals("mkv") || ext.equals("mov") || ext.equals("webm")) return "video/*";
                if (ext.equals("mp3") || ext.equals("flac") || ext.equals("ogg") || ext.equals("m4a") || ext.equals("wav")) return "audio/*";
                if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("webp") || ext.equals("gif")) return "image/*";
            }
        } catch (Throwable ignore) {
        }
        return "application/octet-stream";
    }

    private static boolean isKnownMedia(String mime) {
        if (mime == null) return false;
        return mime.startsWith("image/") || mime.startsWith("video/") || mime.startsWith("audio/");
    }
}
