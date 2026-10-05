package app.amegram.hot;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;

import dalvik.system.DexClassLoader;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;

/**
 * Хот-модули: нативные доверенные расширения из отдельного репозитория.
 * Правила нагрузки: выключенный модуль = ноль кода в памяти (читаются только
 * манифест и префы); включённый грузится лениво, один DexClassLoader на модуль.
 * Доверие = твой репозиторий + https + sha256 из каталога.
 */
public final class HotModulesManager {

    public interface Callback<T> {
        void onDone(boolean ok, String message, T data);
    }

    public interface ProgressCallback<T> extends Callback<T> {
        void onProgress(long downloaded, long total);
    }

    /** Манифест внутри .hmod (manifest.json). */
    public static final class Manifest {
        public final String id;
        public final String version;
        public final String branch;
        public final String entry;
        public final String name;
        public final int minApp;

        Manifest(String id, String version, String branch, String entry, String name, int minApp) {
            this.id = id;
            this.version = version;
            this.branch = branch;
            this.entry = entry;
            this.name = name;
            this.minApp = minApp;
        }
    }

    public static final class InstalledInfo {
        public final Manifest manifest;
        public final File file;
        public final boolean enabled;
        public final boolean active;

        InstalledInfo(Manifest manifest, File file, boolean enabled, boolean active) {
            this.manifest = manifest;
            this.file = file;
            this.enabled = enabled;
            this.active = active;
        }
    }

    public static final class Handle {
        public final InstalledInfo info;
        public final HotModule instance;

        Handle(InstalledInfo info, HotModule instance) {
            this.info = info;
            this.instance = instance;
        }
    }

    private static volatile Context appContext;
    private static final Map<String, Handle> LOADED = new HashMap<>();
    private static final Map<String, Object> SERVICES = new HashMap<>();

    private HotModulesManager() {
    }

    public static synchronized void init(Context context) {
        if (appContext == null && context != null) {
            appContext = context.getApplicationContext();
        }
    }

    private static SharedPreferences prefs() {
        return appContext.getSharedPreferences(HotConfig.PREFS, Context.MODE_PRIVATE);
    }

    private static File root() {
        File dir = new File(appContext.getFilesDir(), HotConfig.DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static File dexOptDir() {
        File dir = new File(appContext.getCodeCacheDir(), "hot_dex");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------- attach включённых при старте (фон, без спешки) ----------

    public static void attachEnabledAsync() {
        init(ApplicationLoader.applicationContext);
        if (appContext == null) return;
        new Thread(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }
            for (InstalledInfo info : listInstalled()) {
                if (info.enabled && info.active) {
                    getHandle(info.manifest.id);
                }
            }
        }, "hotmods-attach").start();
    }

    /**
     * Автоматичне копіювання вбудованих .hmod з assets/hotmodules/ на диск
     * під час першого запуску, щоб клієнт працював із коробки ("і то і то працювало").
     */
    public static void ensureBundledModulesInstalled() {
        init(ApplicationLoader.applicationContext);
        if (appContext == null) return;
        try {
            android.content.res.AssetManager am = appContext.getAssets();
            String[] list = am.list("hotmodules");
            if (list == null || list.length == 0) return;
            for (String file : list) {
                if (!file.endsWith(".hmod")) continue;
                String modId = file.replace(".hmod", "");
                int dash = modId.lastIndexOf('-');
                if (dash > 0) modId = modId.substring(0, dash);

                File modDir = new File(root(), modId);
                if (modDir.exists() && modDir.list() != null && modDir.list().length > 0) {
                    continue; // вже встановлено
                }
                File tmp = new File(root(), file + ".tmp");
                try (InputStream in = am.open("hotmodules/" + file);
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                Manifest m = readManifest(tmp);
                File vdir = new File(new File(root(), m.id), m.version);
                vdir.mkdirs();
                File dest = new File(vdir, "module.hmod");
                if (tmp.renameTo(dest)) {
                    prefs().edit().putString("installed_" + m.id, m.version)
                            .putBoolean("enabled_" + m.id, true).apply();
                } else {
                    tmp.delete();
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static boolean isModuleInstalled(String moduleId) {
        if (moduleId == null || appContext == null) return false;
        String active = prefs().getString("installed_" + moduleId, "");
        if (!active.isEmpty()) return true;
        File modDir = new File(root(), moduleId);
        return modDir.exists() && modDir.isDirectory();
    }

    public static boolean isModuleEnabled(String moduleId) {
        if (moduleId == null || appContext == null) return false;
        return prefs().getBoolean("enabled_" + moduleId, false);
    }

    // ---------- каталог ----------

    public static void fetchCatalog(boolean force, Callback<HotCatalog> cb) {
        init(ApplicationLoader.applicationContext);
        new Thread(() -> {
            HotCatalog cached = readCachedCatalog();
            long cacheTime = prefs().getLong("catalog_time", 0);
            boolean fresh = System.currentTimeMillis() - cacheTime < HotConfig.CATALOG_TTL_MS;
            if (!force && cached != null && fresh) {
                post(cb, true, "", cached);
                return;
            }
            try {
                String json = HotDownloader.get(HotConfig.CATALOG_URL);
                HotCatalog catalog = HotCatalog.parse(json);
                prefs().edit().putString("catalog_json", json)
                        .putLong("catalog_time", System.currentTimeMillis()).apply();
                post(cb, true, "", catalog);
            } catch (Throwable e) {
                FileLog.e("hotmods: catalog fetch failed", e);
                if (cached != null) {
                    post(cb, true, "cached", cached);
                } else {
                    HotCatalog asset = readAssetCatalog();
                    if (asset != null) {
                        post(cb, true, "asset", asset);
                    } else {
                        post(cb, false, e.getMessage(), null);
                    }
                }
            }
        }, "hotmods-catalog").start();
    }

    private static HotCatalog readCachedCatalog() {
        try {
            String json = prefs().getString("catalog_json", "");
            if (json.isEmpty()) return null;
            return HotCatalog.parse(json);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static HotCatalog readAssetCatalog() {
        try (InputStream in = appContext.getAssets().open(HotConfig.ASSET_FALLBACK);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return HotCatalog.parse(new String(out.toByteArray(), "UTF-8"));
        } catch (Throwable ignore) {
            return null;
        }
    }

    // ---------- установленные ----------

    public static synchronized List<InstalledInfo> listInstalled() {
        List<InstalledInfo> res = new ArrayList<>();
        if (appContext == null) return res;
        File r = root();
        File[] modDirs = r.listFiles();
        if (modDirs == null) return res;
        for (File modDir : modDirs) {
            if (!modDir.isDirectory()) continue;
            String id = modDir.getName();
            File[] vers = modDir.listFiles();
            if (vers == null) continue;
            String activeVersion = prefs().getString("installed_" + id, "");
            boolean enabled = prefs().getBoolean("enabled_" + id, false);
            for (File vdir : vers) {
                if (!vdir.isDirectory()) continue;
                File hmod = new File(vdir, "module.hmod");
                if (!hmod.exists()) continue;
                try {
                    Manifest m = readManifest(hmod);
                    if (!m.id.equals(id)) continue;
                    res.add(new InstalledInfo(m, hmod, enabled, m.version.equals(activeVersion)));
                } catch (Throwable e) {
                    FileLog.e("hotmods: bad " + hmod, e);
                }
            }
        }
        Collections.sort(res, (a, b) -> {
            int c = a.manifest.id.compareTo(b.manifest.id);
            if (c != 0) return c;
            return b.manifest.version.compareTo(a.manifest.version);
        });
        return res;
    }

    public static Manifest readManifest(File hmod) throws Exception {
        try (ZipFile zip = new ZipFile(hmod)) {
            ZipEntry e = zip.getEntry("manifest.json");
            if (e == null) throw new IllegalStateException("no manifest.json");
            try (InputStream in = zip.getInputStream(e);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                JSONObject o = new JSONObject(new String(out.toByteArray(), "UTF-8"));
                String id = o.optString("id", "");
                String version = o.optString("version", "");
                String entry = o.optString("entry", "");
                if (id.isEmpty() || version.isEmpty() || entry.isEmpty()) {
                    throw new IllegalStateException("bad manifest");
                }
                return new Manifest(id, version, o.optString("branch", "stable"),
                        entry, o.optString("name", id), o.optInt("minApp", 0));
            }
        }
    }

    // ---------- реактивні слухачі змін ----------

    public interface ModulesChangeListener {
        void onModulesChanged();
    }

    private static final List<ModulesChangeListener> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addListener(ModulesChangeListener l) {
        if (l != null && !LISTENERS.contains(l)) {
            LISTENERS.add(l);
        }
    }

    public static void removeListener(ModulesChangeListener l) {
        LISTENERS.remove(l);
    }

    public static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            for (ModulesChangeListener l : LISTENERS) {
                try {
                    l.onModulesChanged();
                } catch (Throwable ignore) {
                }
            }
        });
    }

    public static int countEnabledModules() {
        int cnt = 0;
        for (InstalledInfo i : listInstalled()) {
            if (i.enabled && i.active) cnt++;
        }
        return cnt;
    }

    public static String getModuleLoadEstimate(String moduleId) {
        Handle h = LOADED.get(moduleId);
        if (h != null && h.instance != null) {
            long size = 0;
            if (h.info != null && h.info.file != null && h.info.file.exists()) {
                size = h.info.file.length();
            }
            long kb = Math.max(1, size / 1024);
            return MiogramLocale.get(
                    "🟢 Активний у пам'яті • ~" + kb + " KB DEX • 0% CPU",
                    "🟢 Активен в памяти • ~" + kb + " KB DEX • 0% CPU",
                    "🟢 Active in RAM • ~" + kb + " KB DEX • 0% CPU");
        } else {
            return MiogramLocale.get(
                    "⚪ Вимкнено • 0 KB RAM • Вивантажено",
                    "⚪ Выключен • 0 KB RAM • Выгружен",
                    "⚪ Disabled • 0 KB RAM • Unloaded");
        }
    }

    public static void installBundledModule(String modId, boolean enable, Callback<Void> cb) {
        init(ApplicationLoader.applicationContext);
        if (appContext == null) {
            post(cb, false, "no context", null);
            return;
        }
        new Thread(() -> {
            try {
                android.content.res.AssetManager am = appContext.getAssets();
                String[] list = am.list("hotmodules");
                if (list == null) {
                    post(cb, false, "no assets", null);
                    return;
                }
                String targetFile = null;
                for (String f : list) {
                    if (f.equals(modId + ".hmod") || f.startsWith(modId + "-")) {
                        targetFile = f;
                        break;
                    }
                }
                if (targetFile == null) {
                    post(cb, false, "bundled module not found: " + modId, null);
                    return;
                }
                File tmp = new File(root(), targetFile + ".tmp");
                try (InputStream in = am.open("hotmodules/" + targetFile);
                     FileOutputStream out = new FileOutputStream(tmp)) {
                    byte[] buf = new byte[16 * 1024];
                    int n;
                    while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                }
                Manifest m = readManifest(tmp);
                File vdir = new File(new File(root(), m.id), m.version);
                vdir.mkdirs();
                File dest = new File(vdir, "module.hmod");
                if (!tmp.renameTo(dest)) {
                    tmp.delete();
                    post(cb, false, "rename failed", null);
                    return;
                }
                prefs().edit().putString("installed_" + m.id, m.version)
                        .putBoolean("enabled_" + m.id, enable).apply();
                if (enable) {
                    getHandle(m.id);
                }
                notifyChanged();
                post(cb, true, m.version, null);
            } catch (Throwable e) {
                FileLog.e("hotmods: installBundled failed", e);
                post(cb, false, e.getMessage(), null);
            }
        }, "hotmods-install-bundle").start();
    }

    // ---------- скачивание и установка ----------

    public static void downloadBuild(final String moduleId, final HotCatalog.Build build,
                                     final boolean autoEnable,
                                     final ProgressCallback<Void> cb) {
        init(ApplicationLoader.applicationContext);
        new Thread(() -> {
            try {
                int appVer = appVersion();
                if (build.minApp > 0 && appVer > 0 && build.minApp > appVer) {
                    postProgress(cb, false, "Потрібен новіший Amegram (min " + build.minApp + ")", null);
                    return;
                }
                File tmp = new File(root(), moduleId + ".tmp");
                boolean downloaded = false;
                if (build.url != null && build.url.startsWith("https://")) {
                    try {
                        HotDownloader.download(build.url, tmp, (d, t) -> postProgressOnly(cb, d, t));
                        downloaded = true;
                    } catch (Throwable netErr) {
                        FileLog.e("hotmods: download error, trying asset fallback: " + moduleId, netErr);
                    }
                }
                if (!downloaded) {
                    // Офлайн фолбек з вбудованих ресурсів
                    android.content.res.AssetManager am = appContext.getAssets();
                    String[] list = am.list("hotmodules");
                    String assetName = null;
                    if (list != null) {
                        for (String f : list) {
                            if (f.equals(moduleId + ".hmod") || f.startsWith(moduleId + "-" + build.version) || f.startsWith(moduleId + "-")) {
                                assetName = f;
                                break;
                            }
                        }
                    }
                    if (assetName != null) {
                        try (InputStream in = am.open("hotmodules/" + assetName);
                             FileOutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[16 * 1024];
                            int n;
                            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                        }
                        downloaded = true;
                    }
                }
                if (!downloaded) {
                    postProgress(cb, false, "Не вдалося завантажити модуль", null);
                    return;
                }

                if (build.sha256 != null && !build.sha256.isEmpty()) {
                    String actual = HotDownloader.sha256(tmp);
                    if (!build.sha256.equalsIgnoreCase(actual)) {
                        tmp.delete();
                        postProgress(cb, false, "Помилка контрольної суми sha256", null);
                        return;
                    }
                }
                Manifest m = readManifest(tmp);
                if (!m.id.equals(moduleId)) {
                    tmp.delete();
                    postProgress(cb, false, "Невідповідність id: " + m.id, null);
                    return;
                }
                File vdir = new File(new File(root(), moduleId), m.version);
                vdir.mkdirs();
                File dest = new File(vdir, "module.hmod");
                if (dest.exists()) dest.delete();
                if (!tmp.renameTo(dest)) {
                    tmp.delete();
                    postProgress(cb, false, "Не вдалося зберегти модуль", null);
                    return;
                }
                prefs().edit().putString("installed_" + moduleId, m.version).apply();
                if (autoEnable) {
                    prefs().edit().putBoolean("enabled_" + moduleId, true).apply();
                    getHandle(moduleId);
                }
                pruneOld(moduleId);
                notifyChanged();
                postProgress(cb, true, m.version, null);
            } catch (Throwable e) {
                FileLog.e("hotmods: download failed", e);
                postProgress(cb, false, String.valueOf(e.getMessage()), null);
            }
        }, "hotmods-dl").start();
    }

    public static void downloadBuild(final String moduleId, final HotCatalog.Build build,
                                     final ProgressCallback<Void> cb) {
        downloadBuild(moduleId, build, false, cb);
    }

    private static void pruneOld(String moduleId) {
        try {
            List<InstalledInfo> all = new ArrayList<>();
            for (InstalledInfo i : listInstalled()) {
                if (i.manifest.id.equals(moduleId)) all.add(i);
            }
            Collections.sort(all, (a, b) -> b.manifest.version.compareTo(a.manifest.version));
            Set<String> keep = new HashSet<>();
            String active = prefs().getString("installed_" + moduleId, "");
            keep.add(active);
            for (InstalledInfo i : all) {
                if (keep.size() >= HotConfig.KEEP_VERSIONS) break;
                keep.add(i.manifest.version);
            }
            File modDir = new File(root(), moduleId);
            File[] vers = modDir.listFiles();
            if (vers == null) return;
            for (File vdir : vers) {
                if (vdir.isDirectory() && !keep.contains(vdir.getName())) {
                    deleteRecursive(vdir);
                }
            }
        } catch (Throwable ignore) {
        }
    }

    public static void activateVersion(String moduleId, String version, Callback<Void> cb) {
        new Thread(() -> {
            dropLoaded(moduleId);
            prefs().edit().putString("installed_" + moduleId, version).apply();
            if (prefs().getBoolean("enabled_" + moduleId, false)) {
                Handle h = getHandle(moduleId);
                if (h == null || h.instance == null) {
                    notifyChanged();
                    post(cb, false, "не завантажився", null);
                    return;
                }
            }
            notifyChanged();
            post(cb, true, version, null);
        }, "hotmods-activate").start();
    }

    public static void setEnabled(String moduleId, boolean enabled, Callback<Void> cb) {
        new Thread(() -> {
            prefs().edit().putBoolean("enabled_" + moduleId, enabled).apply();
            if (enabled) {
                Handle h = getHandle(moduleId);
                if (h == null || h.instance == null) {
                    prefs().edit().putBoolean("enabled_" + moduleId, false).apply();
                    notifyChanged();
                    post(cb, false, "модуль не завантажився, залишено вимкненим", null);
                    return;
                }
            } else {
                dropLoaded(moduleId);
            }
            notifyChanged();
            post(cb, true, "", null);
        }, "hotmods-enable").start();
    }

    public static void deleteVersion(String moduleId, String version) {
        dropIfLoaded(moduleId, version);
        deleteRecursive(new File(new File(root(), moduleId), version));
        if (version.equals(prefs().getString("installed_" + moduleId, ""))) {
            String next = "";
            for (InstalledInfo i : listInstalled()) {
                if (i.manifest.id.equals(moduleId)) {
                    next = i.manifest.version;
                    break;
                }
            }
            prefs().edit().putString("installed_" + moduleId, next).apply();
        }
        notifyChanged();
    }

    public static void deleteModule(String moduleId) {
        dropLoaded(moduleId);
        deleteRecursive(new File(root(), moduleId));
        prefs().edit().remove("installed_" + moduleId).remove("enabled_" + moduleId).apply();
        notifyChanged();
    }

    // ---------- загрузка кода (только включённые) ----------

    public static synchronized Handle getHandle(String moduleId) {
        Handle cached = LOADED.get(moduleId);
        if (cached != null) return cached;
        if (appContext == null) return null;
        if (!prefs().getBoolean("enabled_" + moduleId, false)) return null;
        String version = prefs().getString("installed_" + moduleId, "");
        if (version.isEmpty()) return null;
        File hmod = new File(new File(new File(root(), moduleId), version), "module.hmod");
        if (!hmod.exists()) return null;
        try {
            Manifest m = readManifest(hmod);
            DexClassLoader loader = new DexClassLoader(hmod.getAbsolutePath(),
                    dexOptDir().getAbsolutePath(), null, appContext.getClassLoader());
            Class<?> cls = Class.forName(m.entry, true, loader);
            Object obj = cls.newInstance();
            if (!(obj instanceof HotModule)) {
                FileLog.e("hotmods: entry is not HotModule: " + m.entry);
                return null;
            }
            HotModule mod = (HotModule) obj;
            InstalledInfo info = new InstalledInfo(m, hmod, true, true);
            Handle handle = new Handle(info, mod);
            LOADED.put(moduleId, handle);
            try {
                mod.onAttach(appContext, new HostImpl(moduleId));
            } catch (Throwable e) {
                FileLog.e("hotmods: onAttach failed: " + moduleId, e);
            }
            return handle;
        } catch (Throwable e) {
            FileLog.e("hotmods: load failed: " + moduleId, e);
            return null;
        }
    }

    private static synchronized void dropLoaded(String moduleId) {
        Handle h = LOADED.remove(moduleId);
        if (h != null && h.instance != null) {
            try {
                h.instance.onDetach();
            } catch (Throwable ignore) {
            }
        }
    }

    private static synchronized void dropIfLoaded(String moduleId, String version) {
        Handle h = LOADED.get(moduleId);
        if (h != null && h.info.manifest.version.equals(version)) {
            dropLoaded(moduleId);
        }
    }

    /** Включённые модули с вкладкой настроек — для хаба Амэграм. */
    public static List<Handle> settingsHandles() {
        List<Handle> res = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (InstalledInfo info : listInstalled()) {
            if (info.enabled && info.active) ids.add(info.manifest.id);
        }
        List<String> sorted = new ArrayList<>(ids);
        Collections.sort(sorted);
        for (String id : sorted) {
            Handle h = getHandle(id);
            if (h != null && h.instance != null) {
                try {
                    if (h.instance.hasSettings()) res.add(h);
                } catch (Throwable ignore) {
                }
            }
        }
        return res;
    }

    // ---------- сервисы и события ----------

    public static void registerService(String name, Object service) {
        synchronized (SERVICES) {
            SERVICES.put(name, service);
        }
    }

    public static void unregisterService(String name) {
        synchronized (SERVICES) {
            SERVICES.remove(name);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getService(String name) {
        synchronized (SERVICES) {
            try {
                return (T) SERVICES.get(name);
            } catch (ClassCastException e) {
                return null;
            }
        }
    }

    public static void emit(String event, String json) {
        FileLog.d("hotmods event " + event + ": " + json);
    }

    /** Показати екран модуля: createScreen -> presentFragment. */
    public static void openModuleScreen(String moduleId, String screenId) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                Handle h = getHandle(moduleId);
                if (h == null || h.instance == null) return;
                Object screen;
                try {
                    screen = h.instance.createScreen(screenId);
                } catch (Throwable e) {
                    return;
                }
                if (!(screen instanceof org.telegram.ui.ActionBar.BaseFragment)) return;
                org.telegram.ui.ActionBar.BaseFragment last;
                try {
                    last = org.telegram.ui.LaunchActivity.getLastFragment();
                } catch (Throwable e) {
                    return;
                }
                if (last != null) {
                    last.presentFragment((org.telegram.ui.ActionBar.BaseFragment) screen);
                }
            } catch (Throwable ignore) {
            }
        });
    }

    // ---------- misc ----------

    public static int appVersion() {
        try {
            PackageInfo pi = appContext.getPackageManager()
                    .getPackageInfo(appContext.getPackageName(), 0);
            return pi.versionCode / 10;
        } catch (Throwable ignore) {
            return 0;
        }
    }

    private static void deleteRecursive(File f) {
        try {
            if (f.isDirectory()) {
                File[] kids = f.listFiles();
                if (kids != null) {
                    for (File k : kids) deleteRecursive(k);
                }
            }
            f.delete();
        } catch (Throwable ignore) {
        }
    }

    private static <T> void post(Callback<T> cb, boolean ok, String msg, T data) {
        if (cb == null) return;
        AndroidUtilities.runOnUIThread(() -> cb.onDone(ok, msg, data));
    }

    private static <T> void postProgress(ProgressCallback<T> cb, boolean ok, String msg, T data) {
        post(cb, ok, msg, data);
    }

    private static <T> void postProgressOnly(ProgressCallback<T> cb, long d, long t) {
        if (cb == null) return;
        AndroidUtilities.runOnUIThread(() -> cb.onProgress(d, t));
    }

    /** Хост одного модуля: префы лежат в hotmod_<id>, отдельно от остальных. */
    private static final class HostImpl implements HotHost {
        private final String moduleId;

        HostImpl(String moduleId) {
            this.moduleId = moduleId;
        }

        @Override
        public Context context() {
            return appContext;
        }

        @Override
        public String moduleId() {
            return moduleId;
        }

        private SharedPreferences modPrefs() {
            return appContext.getSharedPreferences("hotmod_" + moduleId, Context.MODE_PRIVATE);
        }

        @Override
        public boolean getBool(String key, boolean def) {
            return modPrefs().getBoolean(key, def);
        }

        @Override
        public void putBool(String key, boolean value) {
            modPrefs().edit().putBoolean(key, value).apply();
        }

        @Override
        public String getString(String key, String def) {
            return modPrefs().getString(key, def);
        }

        @Override
        public void putString(String key, String value) {
            modPrefs().edit().putString(key, value).apply();
        }

        @Override
        public int getInt(String key, int def) {
            return modPrefs().getInt(key, def);
        }

        @Override
        public void putInt(String key, int value) {
            modPrefs().edit().putInt(key, value).apply();
        }

        @Override
        public void toast(String text) {
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    android.widget.Toast.makeText(appContext, text,
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {
                }
            });
        }

        @Override
        public void log(String text) {
            FileLog.d("hotmod[" + moduleId + "]: " + text);
        }

        @Override
        public void registerService(String name, Object service) {
            HotModulesManager.registerService(name, service);
        }

        @Override
        public void unregisterService(String name) {
            HotModulesManager.unregisterService(name);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getService(String name) {
            return HotModulesManager.getService(name);
        }

        @Override
        public void emit(String event, String jsonPayload) {
            HotModulesManager.emit(event, jsonPayload);
        }

        @Override
        public void openModuleScreen(String screenId) {
            HotModulesManager.openModuleScreen(moduleId, screenId);
        }
    }

    /** Хост для экрана настроек: модуль уже загружен, префы те же. */
    public static HotHost hostFor(String moduleId) {
        init(ApplicationLoader.applicationContext);
        return new HostImpl(moduleId);
    }
}
