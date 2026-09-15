package app.miogram.bridge.modapi;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bound-service bridge for external mod APKs (e.g. a TikTok mod).
 *
 * <p>Client integration (TT app side): copy
 * {@code src/main/aidl/app/miogram/bridge/modapi/IMioBridge.aidl} into the
 * client project under the same path, then:
 * <pre>
 * Intent i = new Intent("app.miogram.bridge.modapi.BIND_BRIDGE");
 * i.setPackage("com.exteraless.app"); // Miogram applicationId
 * bindService(i, connection, Context.BIND_AUTO_CREATE);
 * ...
 * int v = bridge.getApiVersion(); // must be &lt;= yours
 * String json = bridge.resolveMedia("https://vt.tiktok.com/...");
 * </pre>
 *
 * <p>Security: same-signer callers holding the signature permission pass
 * automatically. Everyone else needs the explicit user opt-in
 * ({@link #isExternalModsEnabled()}) in Miogram Settings -&gt; System, and
 * every call is still verified per-transaction. File serving is jailed to
 * Miogram's own files/cache dirs; vault writes need a world-readable source
 * path (e.g. shared Downloads).
 */
public class MioBridgeService extends Service {

    public static final String ACTION_BIND = "app.miogram.bridge.modapi.BIND_BRIDGE";
    public static final String PERMISSION = "app.miogram.bridge.modapi.permission.MIO_BRIDGE";

    private static final String PREFS = "miogram_modapi_prefs";
    private static final String KEY_EXTERNAL_MODS = "external_mods_allowed";

    public static boolean isExternalModsEnabled() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return false;
            return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getBoolean(KEY_EXTERNAL_MODS, false);
        } catch (Throwable ignore) {
            return false;
        }
    }

    public static void setExternalModsEnabled(boolean enabled) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean(KEY_EXTERNAL_MODS, enabled).apply();
        } catch (Throwable ignore) {}
    }

    private boolean isCallerAllowed() {
        try {
            int uid = Binder.getCallingUid();
            if (uid == Process.myUid()) return true;
            if (checkCallingPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED) return true;
            if (!isExternalModsEnabled()) return false;
            // Opt-in is on: log who is calling, allow.
            try {
                String[] pkgs = getPackageManager().getPackagesForUid(uid);
                FileLog.d("MioBridge external caller: "
                        + (pkgs != null ? TextUtils.join(",", pkgs) : String.valueOf(uid)));
            } catch (Throwable ignore) {}
            return true;
        } catch (Throwable ignore) {
            return false;
        }
    }

    private void enforceCaller() {
        if (!isCallerAllowed()) {
            throw new SecurityException("MioBridge: external mods not allowed");
        }
    }

    private static String err(String message) {
        try {
            JSONObject o = new JSONObject();
            o.put("error", message != null ? message : "error");
            return o.toString();
        } catch (Throwable ignore) {
            return "{\"error\":\"error\"}";
        }
    }

    private final IMioBridge.Stub binder = new IMioBridge.Stub() {
        @Override
        public int getApiVersion() {
            enforceCaller();
            return MioApi.MIO_API_VERSION;
        }

        @Override
        public String getFeatures() {
            enforceCaller();
            try {
                JSONObject o = new JSONObject();
                for (java.util.Map.Entry<String, Boolean> e : MioApi.features().entrySet()) {
                    o.put(e.getKey(), e.getValue());
                }
                return o.toString();
            } catch (Throwable t) {
                return err(t.getMessage());
            }
        }

        @Override
        public String resolveMedia(String pageUrl) {
            enforceCaller();
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            MioDownload.resolve(pageUrl, new MioDownload.ResolveCallback() {
                @Override
                public void onResolved(MioDownload.StreamInfo info) {
                    try {
                        JSONObject o = new JSONObject();
                        o.put("url", info.streamUrl);
                        o.put("title", info.title);
                        o.put("filename", info.filename);
                        out[0] = o.toString();
                    } catch (Throwable ignore) {
                        out[0] = err("encode");
                    }
                    latch.countDown();
                }

                @Override
                public void onError(String error) {
                    out[0] = err(error);
                    latch.countDown();
                }
            });
            try {
                latch.await(90, TimeUnit.SECONDS);
            } catch (Throwable ignore) {}
            return out[0] != null ? out[0] : err("timeout");
        }

        @Override
        public String fetchToCache(String pageUrl, String filename) {
            enforceCaller();
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            MioDownload.resolve(pageUrl, new MioDownload.ResolveCallback() {
                @Override
                public void onResolved(MioDownload.StreamInfo info) {
                    try {
                        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "miobridge");
                        if (!dir.exists()) dir.mkdirs();
                        String name = !TextUtils.isEmpty(filename) ? filename : info.filename;
                        File dest = new File(dir, sanitize(name));
                        MioDownload.fetch(info, dest, null, new MioDownload.FileCallback() {
                            @Override
                            public void onDone(File file) {
                                try {
                                    JSONObject o = new JSONObject();
                                    o.put("path", file.getAbsolutePath());
                                    out[0] = o.toString();
                                } catch (Throwable ignore) {
                                    out[0] = err("encode");
                                }
                                latch.countDown();
                            }

                            @Override
                            public void onError(String error) {
                                out[0] = err(error);
                                latch.countDown();
                            }
                        });
                    } catch (Throwable t) {
                        out[0] = err(t.getMessage());
                        latch.countDown();
                    }
                }

                @Override
                public void onError(String error) {
                    out[0] = err(error);
                    latch.countDown();
                }
            });
            try {
                latch.await(8, TimeUnit.MINUTES);
            } catch (Throwable ignore) {}
            return out[0] != null ? out[0] : err("timeout");
        }

        @Override
        public ParcelFileDescriptor openFile(String path) throws SecurityException {
            enforceCaller();
            try {
                File f = new File(path);
                File filesDir = ApplicationLoader.applicationContext.getFilesDir();
                File cacheDir = ApplicationLoader.applicationContext.getCacheDir();
                String canon = f.getCanonicalPath();
                boolean inside = canon.startsWith(filesDir.getCanonicalPath())
                        || canon.startsWith(cacheDir.getCanonicalPath());
                if (!inside || !f.isFile()) {
                    throw new SecurityException("MioBridge: path not servable");
                }
                return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
            } catch (SecurityException se) {
                throw se;
            } catch (Throwable t) {
                FileLog.e(t);
                return null;
            }
        }

        @Override
        public String aiTranslate(String text, String targetLang) {
            enforceCaller();
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                app.miogram.bridge.ai.MiogramAiService.translateText(text, targetLang,
                        (result, error) -> {
                            try {
                                JSONObject o = new JSONObject();
                                if (result != null) o.put("text", result);
                                else o.put("error", error != null ? error : "error");
                                out[0] = o.toString();
                            } catch (Throwable ignore) {
                                out[0] = err("encode");
                            }
                            latch.countDown();
                        });
            } catch (Throwable t) {
                out[0] = err(t.getMessage());
                latch.countDown();
            }
            try {
                latch.await(90, TimeUnit.SECONDS);
            } catch (Throwable ignore) {}
            return out[0] != null ? out[0] : err("timeout");
        }

        @Override
        public String aiSummarize(String text) {
            enforceCaller();
            final String[] out = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            try {
                app.miogram.bridge.ai.MiogramAiService.summarizeText(text, result -> {
                    try {
                        JSONObject o = new JSONObject();
                        if (result != null) o.put("text", result);
                        else o.put("error", "error");
                        out[0] = o.toString();
                    } catch (Throwable ignore) {
                        out[0] = err("encode");
                    }
                    latch.countDown();
                });
            } catch (Throwable t) {
                out[0] = err(t.getMessage());
                latch.countDown();
            }
            try {
                latch.await(90, TimeUnit.SECONDS);
            } catch (Throwable ignore) {}
            return out[0] != null ? out[0] : err("timeout");
        }

        @Override
        public String vaultPut(int account, String name, String srcPath) {
            enforceCaller();
            try {
                if (!UserConfig.isValidAccount(account)) return err("bad account");
                if (TextUtils.isEmpty(srcPath)) return err("no file");
                File src = new File(srcPath);
                if (!src.isFile() || !src.canRead()) return err("unreadable file (use shared Downloads)");
                long vaultChatId = app.miogram.bridge.cloudvault.MiogramCloudVaultEngine.getVaultChatId(account);
                if (vaultChatId == 0) return err("vault not linked");
                String mime = "application/octet-stream";
                String lower = src.getName().toLowerCase(java.util.Locale.ROOT);
                if (lower.endsWith(".mp4")) mime = "video/mp4";
                else if (lower.endsWith(".mp3")) mime = "audio/mpeg";
                else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) mime = "image/jpeg";
                else if (lower.endsWith(".png")) mime = "image/png";
                else if (lower.endsWith(".json")) mime = "application/json";
                final String fName = !TextUtils.isEmpty(name) ? name : src.getName();
                final String fMime = mime;
                Utilities.globalQueue.postRunnable(() ->
                        app.miogram.bridge.cloudvault.MiogramCloudVaultEngine.uploadFileToVault(
                                account, src, fName, fMime, null, null, null));
                JSONObject o = new JSONObject();
                o.put("accepted", true);
                o.put("name", fName);
                return o.toString();
            } catch (Throwable t) {
                return err(t.getMessage());
            }
        }

        @Override
        public String vaultList(int account, int limit) {
            enforceCaller();
            try {
                if (!UserConfig.isValidAccount(account)) return err("bad account");
                int cap = Math.max(1, Math.min(limit <= 0 ? 200 : limit, 200));
                JSONArray arr = new JSONArray();
                java.util.ArrayList<app.miogram.bridge.cloudvault.MiogramCloudVaultFile> files =
                        app.miogram.bridge.cloudvault.MiogramCloudVaultEngine.getFilesForTopic(0);
                for (int i = 0; i < files.size() && arr.length() < cap; i++) {
                    try {
                        app.miogram.bridge.cloudvault.MiogramCloudVaultFile f = files.get(i);
                        if (f == null) continue;
                        JSONObject o = new JSONObject();
                        o.put("fileId", f.fileId);
                        o.put("name", f.name);
                        o.put("size", f.totalSize);
                        o.put("mime", f.mimeType);
                        o.put("date", f.date);
                        arr.put(o);
                    } catch (Throwable ignore) {}
                }
                return arr.toString();
            } catch (Throwable t) {
                return err(t.getMessage());
            }
        }
    };

    private static String sanitize(String name) {
        if (TextUtils.isEmpty(name)) return "media_" + System.currentTimeMillis() + ".mp4";
        return name.replaceAll("[/\\\\?%*:|\"<>]", "_");
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !ACTION_BIND.equals(intent.getAction())) return null;
        if (!isCallerAllowed()) return null;
        return binder;
    }
}
