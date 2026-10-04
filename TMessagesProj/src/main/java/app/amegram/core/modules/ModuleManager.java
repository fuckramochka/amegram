package app.amegram.core.modules;

import android.content.Context;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import app.amegram.core.AmegramCore;
import app.amegram.core.security.AuditLog;

/**
 * Установка, перевірка і життєвий цикл `.amod`-модулів.
 * Правила:
 * - без валідного підпису Ed25519 — ніякого коду (виняток: папка
 *   розробника, явно увімкнена в налаштуваннях);
 * - завантаження entry-point — рефлексією через DexClassLoader для dex,
 *   через Python-движок для python (див. PythonContract);
 * - unload() відпускає лоадер; живі сокети рвати не вміємо і не обіцяємо.
 */
public final class ModuleManager {

    public interface Callback {
        void onDone(boolean ok, String message);
    }

    public static final class Installed {
        public final ModuleManifest manifest;
        public final File dir;
        public boolean loaded;

        Installed(ModuleManifest manifest, File dir) {
            this.manifest = manifest;
            this.dir = dir;
        }
    }

    private static final Map<String, Installed> INSTALLED = new LinkedHashMap<>();
    /** Авто-відкатів поспіль до ручного вибору (spec: 5, далі — вручну або вимкнути). */
    public static final int MAX_AUTO_ROLLBACKS = 5;
    public static final int ROLLBACK_OK = 0;
    public static final int ROLLBACK_NEED_MANUAL = 1;
    public static final int ROLLBACK_NONE = 2;

    private static final String MM_PREFS = "amegram_mm_prefs";

    private static volatile Context appContext;
    private static volatile boolean initialized;
    /** Ed25519-ключ мейнтейнера (32 байти, hex). Порожньо = тільки папка розробника. */
    private static volatile byte[] maintainerKey = new byte[0];

    private ModuleManager() {
    }

    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }
        initialized = true;
        appContext = context != null ? context.getApplicationContext() : null;
        rescan();
    }

    public static void setMaintainerKey(byte[] raw32) {
        maintainerKey = raw32 != null ? raw32.clone() : new byte[0];
    }

    private static File modulesDir() {
        if (appContext == null) {
            return null;
        }
        File dir = new File(appContext.getFilesDir(), "amod");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /** Перечитати встановлені модулі з диску (маніфести, без завантаження коду). */
    public static synchronized void rescan() {
        INSTALLED.clear();
        File root = modulesDir();
        if (root == null) {
            return;
        }
        File[] kids = root.listFiles();
        if (kids == null) {
            return;
        }
        for (File kid : kids) {
            try {
                File mf = new File(kid, "manifest.json");
                if (!mf.isFile()) {
                    continue;
                }
                ModuleManifest m = ModuleManifest.parse(readAll(mf));
                if (m.minCoreAbi > AmegramCore.CORE_ABI) {
                    continue;
                }
                INSTALLED.put(m.id, new Installed(m, kid));
            } catch (Throwable ignore) {
            }
        }
    }

    public static synchronized List<Installed> all() {
        return new ArrayList<>(INSTALLED.values());
    }

    /** Справжня перевірка Ed25519 (BouncyCastle, чистий Java). */
    public static boolean verifySignature(byte[] payload, byte[] signature) {
        try {
            byte[] key = maintainerKey;
            if (key == null || key.length != 32 || signature == null || signature.length != 64) {
                return false;
            }
            Ed25519Signer verifier = new Ed25519Signer();
            verifier.init(false, new Ed25519PublicKeyParameters(key, 0));
            verifier.update(payload, 0, payload.length);
            return verifier.verifySignature(signature);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Завантажити `.amod` за URL, перевірити підпис, розпакувати, зареєструвати. */
    public static void downloadAndInstall(final String url, final Callback callback) {
        new Thread(() -> {
            boolean ok = false;
            String message = "download failed";
            try {
                byte[] amod = fetch(url);
                // Формат: [payload zip][64 байти підпису].
                if (amod != null && amod.length > 64) {
                    byte[] payload = new byte[amod.length - 64];
                    byte[] sig = new byte[64];
                    System.arraycopy(amod, 0, payload, 0, payload.length);
                    System.arraycopy(amod, payload.length, sig, 0, 64);
                    if (verifySignature(payload, sig)) {
                        ok = installPayload(payload);
                        message = ok ? "installed" : "bad package";
                    } else {
                        message = "bad signature";
                    }
                }
            } catch (Throwable t) {
                message = "error: " + t.getMessage();
            }
            final boolean done = ok;
            final String msg = message;
            try {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(() -> callback.onDone(done, msg));
            } catch (Throwable t) {
                callback.onDone(done, msg);
            }
        }, "amod-install").start();
    }

    private static byte[] fetch(String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Accept", "application/octet-stream");
        if (conn.getResponseCode() != 200) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        java.io.InputStream in = conn.getInputStream();
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        conn.disconnect();
        return out.toByteArray();
    }

    private static synchronized boolean installPayload(byte[] payload) {
        try {
            File root = modulesDir();
            if (root == null) {
                return false;
            }
            File tmp = new File(root, "tmp_" + System.currentTimeMillis() + ".zip");
            FileOutputStream fos = new FileOutputStream(tmp);
            fos.write(payload);
            fos.close();
            ZipFile zip = new ZipFile(tmp);
            ZipEntry mfEntry = zip.getEntry("manifest.json");
            if (mfEntry == null) {
                zip.close();
                tmp.delete();
                return false;
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(zip.getInputStream(mfEntry), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            ModuleManifest m = ModuleManifest.parse(sb.toString());
            if (m.minCoreAbi > AmegramCore.CORE_ABI) {
                zip.close();
                tmp.delete();
                return false;
            }
            File dest = new File(root, m.id);
            if (new File(dest, "manifest.json").isFile()) {
                pushHistory(m.id, dest);
            }
            deleteRecursive(dest);
            dest.mkdirs();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || e.getName().contains("..")) {
                    continue;
                }
                File out = new File(dest, e.getName());
                if (!out.getCanonicalPath().startsWith(dest.getCanonicalPath())) {
                    continue;
                }
                out.getParentFile().mkdirs();
                java.io.InputStream zin = zip.getInputStream(e);
                FileOutputStream zout = new FileOutputStream(out);
                byte[] buf = new byte[8192];
                int n;
                while ((n = zin.read(buf)) > 0) {
                    zout.write(buf, 0, n);
                }
                zin.close();
                zout.close();
            }
            zip.close();
            tmp.delete();
            INSTALLED.put(m.id, new Installed(m, dest));
            AuditLog.record("modules", "installed " + m.id + " " + m.version);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void deleteRecursive(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) {
                for (File kid : kids) {
                    deleteRecursive(kid);
                }
            }
        }
        f.delete();
    }

    /** Видалити модуль: код, кеш і директорія. Ядро і чужі модулі не чіпає. */
    public static synchronized boolean uninstall(String id) {
        Installed inst = INSTALLED.remove(id);
        if (inst == null) {
            return false;
        }
        inst.loaded = false;
        deleteRecursive(inst.dir);
        AuditLog.record("modules", "uninstalled " + id);
        return true;
    }

    // ---------------- Версії та відкат (spec: до 5 авто, далі вручну) ----------------

    private static android.content.SharedPreferences mmPrefs() {
        try {
            if (appContext != null) {
                return appContext.getSharedPreferences(MM_PREFS, Context.MODE_PRIVATE);
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    private static File histDir(String id) {
        File root = modulesDir();
        if (root == null) {
            return null;
        }
        File dir = new File(root, ".hist/" + id);
        dir.mkdirs();
        return dir;
    }

    /** Зберегти поточний payload в історію (тримаємо останні 5). */
    private static void pushHistory(String id, File activeDir) {
        try {
            Installed inst = INSTALLED.get(id);
            String ver = inst != null ? inst.manifest.version : "unknown";
            File hist = histDir(id);
            if (hist == null) {
                return;
            }
            File zip = new File(hist, ver + ".zip");
            if (!zip.exists()) {
                zipDir(activeDir, zip);
            }
            File[] all = hist.listFiles();
            if (all != null && all.length > MAX_AUTO_ROLLBACKS) {
                java.util.Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
                for (int i = 0; i < all.length - MAX_AUTO_ROLLBACKS; i++) {
                    all[i].delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void zipDir(File dir, File zip) throws Exception {
        java.util.zip.ZipOutputStream out =
                new java.util.zip.ZipOutputStream(new FileOutputStream(zip));
        byte[] buf = new byte[8192];
        java.util.ArrayDeque<File> stack = new java.util.ArrayDeque<>();
        stack.push(dir);
        while (!stack.isEmpty()) {
            File f = stack.pop();
            File[] kids = f.listFiles();
            if (kids == null) {
                continue;
            }
            for (File kid : kids) {
                if (kid.isDirectory()) {
                    stack.push(kid);
                    continue;
                }
                String rel = dir.toURI().relativize(kid.toURI()).getPath();
                out.putNextEntry(new java.util.zip.ZipEntry(rel));
                FileInputStream in = new FileInputStream(kid);
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                in.close();
                out.closeEntry();
            }
        }
        out.close();
    }

    /** Версії в історії (новіші останні). */
    public static synchronized List<String> historyVersions(String id) {
        List<String> out = new ArrayList<>();
        try {
            File hist = histDir(id);
            File[] all = hist != null ? hist.listFiles() : null;
            if (all == null) {
                return out;
            }
            java.util.Arrays.sort(all, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
            for (File f : all) {
                String n = f.getName();
                if (n.endsWith(".zip")) {
                    out.add(n.substring(0, n.length() - 4));
                }
            }
        } catch (Throwable ignore) {
        }
        return out;
    }

    private static int badCount(String id) {
        try {
            android.content.SharedPreferences p = mmPrefs();
            return p != null ? p.getInt("bad_" + id, 0) : 0;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void setBadCount(String id, int n) {
        try {
            android.content.SharedPreferences p = mmPrefs();
            if (p != null) {
                p.edit().putInt("bad_" + id, n).apply();
            }
        } catch (Throwable ignore) {
        }
    }

    /** Чиста робота модуля — скидає лічильник багів (викликає хаб/монітор). */
    public static void markHealthy(String id) {
        setBadCount(id, 0);
    }

    /**
     * Баг у модулі: відкотити на попередню версію.
     * Повертає ROLLBACK_OK / ROLLBACK_NEED_MANUAL (>5 поспіль — вибір
     * версії вручну або вимкнення) / ROLLBACK_NONE (нема на що котити).
     */
    public static synchronized int rollback(String id) {
        try {
            Installed inst = INSTALLED.get(id);
            File hist = histDir(id);
            File[] all = hist != null ? hist.listFiles() : null;
            if (all == null || all.length == 0 || inst == null) {
                return ROLLBACK_NONE;
            }
            java.util.Arrays.sort(all, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            File prev = all[0];
            // Поточний битий стейт в історію не кладемо — просто перекриваємо.
            deleteRecursive(inst.dir);
            inst.dir.mkdirs();
            ZipFile zip = new ZipFile(prev);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || e.getName().contains("..")) {
                    continue;
                }
                File out = new File(inst.dir, e.getName());
                if (!out.getCanonicalPath().startsWith(inst.dir.getCanonicalPath())) {
                    continue;
                }
                out.getParentFile().mkdirs();
                java.io.InputStream zin = zip.getInputStream(e);
                FileOutputStream zout = new FileOutputStream(out);
                int n;
                while ((n = zin.read(buf)) > 0) {
                    zout.write(buf, 0, n);
                }
                zin.close();
                zout.close();
            }
            zip.close();
            prev.delete();
            inst.loaded = false;
            int bad = badCount(id) + 1;
            setBadCount(id, bad);
            AuditLog.record("modules", "rollback " + id + " bad=" + bad);
            rescan();
            if (bad >= MAX_AUTO_ROLLBACKS) {
                return ROLLBACK_NEED_MANUAL;
            }
            return ROLLBACK_OK;
        } catch (Throwable t) {
            return ROLLBACK_NONE;
        }
    }

    /** Встановити версію з історії вручну (шит після 5 авто-відкатів). */
    public static synchronized boolean installHistoryVersion(String id, String version) {
        try {
            Installed inst = INSTALLED.get(id);
            File hist = histDir(id);
            File zipFile = hist != null ? new File(hist, version + ".zip") : null;
            if (inst == null || zipFile == null || !zipFile.isFile()) {
                return false;
            }
            deleteRecursive(inst.dir);
            inst.dir.mkdirs();
            ZipFile zip = new ZipFile(zipFile);
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.isDirectory() || e.getName().contains("..")) {
                    continue;
                }
                File out = new File(inst.dir, e.getName());
                if (!out.getCanonicalPath().startsWith(inst.dir.getCanonicalPath())) {
                    continue;
                }
                out.getParentFile().mkdirs();
                java.io.InputStream zin = zip.getInputStream(e);
                FileOutputStream zout = new FileOutputStream(out);
                int n;
                while ((n = zin.read(buf)) > 0) {
                    zout.write(buf, 0, n);
                }
                zin.close();
                zout.close();
            }
            zip.close();
            inst.loaded = false;
            AuditLog.record("modules", "manual-version " + id + " " + version);
            rescan();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static String readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
