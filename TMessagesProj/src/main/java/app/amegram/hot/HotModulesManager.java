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
import app.miogram.bridge.MiogramLocale;

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
        /** Ізольований ClassLoader цього модуля (один на модуль, drop при видаленні). */
        public final ClassLoader loader;

        Handle(InstalledInfo info, HotModule instance, ClassLoader loader) {
            this.info = info;
            this.instance = instance;
            this.loader = loader;
        }
    }

    private static volatile Context appContext;
    private static final Map<String, Handle> LOADED = new HashMap<>();
    private static final Map<String, Object> SERVICES = new HashMap<>();

    /** Історичні ID колись вшитих модулів. APK тепер чистий (0 вбудованих),
     *  тому карти лишаються порожніми — лише для міграції старих префів. */
    public static final Map<String, String> BUILTIN_ENTRIES = new HashMap<>();
    public static final Map<String, String> BUILTIN_NAMES = new HashMap<>();
    static {
        // Навмисно порожньо: жоден модуль не вшитий в APK.
        // Весь код приїжджає як .hmod з репозиторію або assets/hotmodules/ (offline seed).
    }

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
        try {
            File dir = new File(appContext.getCodeCacheDir(), "hot_dex");
            if (!dir.exists()) dir.mkdirs();
            if (dir.exists()) return dir;
        } catch (Throwable ignore) {
        }
        // Запасний варіант: getCodeCacheDir іноді недоступний — беремо filesDir.
        File dir = new File(appContext.getFilesDir(), "hot_dex");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    // ---------- attach включённых при старте (фон, без спешки) ----------

    public static void attachEnabledAsync() {
        init(ApplicationLoader.applicationContext);
        if (appContext == null) return;
        new Thread(() -> {
            try {
                ensureBundledModulesInstalled();
            } catch (Throwable ignore) {
            }
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
     * Автовимога: НІЧОГО не копіюємо мовчки (політика "0 вбудованих").
     * При першому запуску показуємо онбординг "Зберіть свій AmeGram",
     * а .hmod з assets/hotmodules/ використовуються лише як офлайн-seed
     * при ручній установці (installBundledModule / downloadBuild fallback).
     */
    public static void ensureBundledModulesInstalled() {
        init(ApplicationLoader.applicationContext);
        // Навмисно no-op: жодного авто-копіювання.
    }

    public static boolean isBuiltin(String moduleId) {
        // Політика "0 вбудованих": нічого не вважаємо вшитим в APK.
        // Метод лишено для сумісності — завжди false.
        return false;
    }

    public static boolean isModuleInstalled(String moduleId) {
        if (moduleId == null) return false;
        if (appContext == null) init(ApplicationLoader.applicationContext);
        if (appContext == null) return false;
        if (prefs().getBoolean("uninstalled_" + moduleId, false)) return false;
        String active = prefs().getString("installed_" + moduleId, "");
        if (!active.isEmpty()) {
            File c = new File(new File(new File(root(), moduleId), active), "module.hmod");
            if (c.exists()) return true;
        }
        File modDir = new File(root(), moduleId);
        if (!modDir.exists() || !modDir.isDirectory()) return false;
        File[] vers = modDir.listFiles();
        if (vers == null) return false;
        for (File vdir : vers) {
            if (vdir.isDirectory() && new File(vdir, "module.hmod").exists()) return true;
        }
        return false;
    }

    public static boolean isModuleEnabled(String moduleId) {
        if (moduleId == null) return false;
        if (appContext == null) init(ApplicationLoader.applicationContext);
        if (appContext == null) return false;
        if (prefs().getBoolean("uninstalled_" + moduleId, false)) return false;
        // Жодних авто-true: вимкнено за замовчуванням, вмикає лише юзер.
        return prefs().getBoolean("enabled_" + moduleId, false);
    }

    public static boolean isModuleActive(String moduleId) {
        if (!isModuleEnabled(moduleId)) return false;
        Handle h = getHandle(moduleId);
        return h != null && h.instance != null;
    }

    // ---------- crash-ізоляція: карантин після 3 провалів ----------

    private static final int MAX_LOAD_FAILURES = 3;

    /** Скільки разів модуль падав при завантаженні поспіль. */
    public static int getLoadFailures(String moduleId) {
        if (appContext == null || moduleId == null) return 0;
        return prefs().getInt("loadfail_" + moduleId, 0);
    }

    /** Модуль у карантині: падав 3+ рази, вимкнений автоматично. */
    public static boolean isQuarantined(String moduleId) {
        return getLoadFailures(moduleId) >= MAX_LOAD_FAILURES;
    }

    private static void recordLoadFailure(String moduleId) {
        if (appContext == null || moduleId == null) return;
        int n = getLoadFailures(moduleId) + 1;
        prefs().edit().putInt("loadfail_" + moduleId, n).apply();
        if (n >= MAX_LOAD_FAILURES) {
            // Не роняємо апку: гасимо модуль, лишаємо запис для екрана версій.
            prefs().edit().putBoolean("enabled_" + moduleId, false).apply();
            FileLog.e("hotmods: quarantined after " + n + " failures: " + moduleId);
        }
        notifyChanged();
    }

    public static void clearLoadFailures(String moduleId) {
        if (appContext == null || moduleId == null) return;
        prefs().edit().remove("loadfail_" + moduleId).apply();
    }

    // ---------- сумісність і підписи ----------

    /** Чи підходить збірка під поточний білд апки. */
    public static boolean isCompatible(HotCatalog.Build build) {
        if (build == null) return true;
        if (build.minApp <= 0) return true;
        int appVer = appVersion();
        if (appVer <= 0) return true;
        return build.minApp <= appVer;
    }

    /**
     * Статус довіри збірки: SIGNED (є detached-підпис) / SHA256 (лише хеш з каталога)
     * / UNSIGNED (нічого). Повну Ed25519-перевірку вмикає SIGNING_PUBKEY, коли ключ видано.
     */
    public static String signatureStatus(HotCatalog.Build build) {
        if (build == null) return "UNSIGNED";
        if (build.isSigned()) return "SIGNED";
        if (build.sha256 != null && !build.sha256.isEmpty()) return "SHA256";
        return "UNSIGNED";
    }

    // ---------- перевірка оновлень (фон, без WorkManager-залежності) ----------

    /** Опитати каталог і записати доступні оновлення в префи. */
    public static void checkUpdatesAsync(Callback<Map<String, HotCatalog.Build>> cb) {
        init(ApplicationLoader.applicationContext);
        new Thread(() -> {
            Map<String, HotCatalog.Build> found = new HashMap<>();
            try {
                String json = HotDownloader.get(HotConfig.CATALOG_URL);
                HotCatalog catalog = HotCatalog.parse(json);
                int appVer = appVersion();
                for (HotModulesManager.InstalledInfo info : listInstalled()) {
                    if (!info.active) continue;
                    HotCatalog.Entry e = catalog.find(info.manifest.id);
                    if (e == null) continue;
                    HotCatalog.Build latest = e.latestCompatible(appVer);
                    if (latest != null && latest.version.compareTo(info.manifest.version) > 0) {
                        found.put(info.manifest.id, latest);
                        prefs().edit().putString("update_available_" + info.manifest.id, latest.version).apply();
                    } else {
                        prefs().edit().remove("update_available_" + info.manifest.id).apply();
                    }
                }
                notifyChanged();
                post(cb, true, "", found);
            } catch (Throwable e) {
                FileLog.e("hotmods: update check failed", e);
                post(cb, false, String.valueOf(e.getMessage()), found);
            }
        }, "hotmods-updates").start();
    }

    /** Версія доступного оновлення з останньої перевірки ("" = нема). */
    public static String getUpdateAvailable(String moduleId) {
        if (appContext == null || moduleId == null) return "";
        return prefs().getString("update_available_" + moduleId, "");
    }

    public static void clearUpdateAvailable(String moduleId) {
        if (appContext == null || moduleId == null) return;
        prefs().edit().remove("update_available_" + moduleId).apply();
    }

    // ---------- бекап набору модулів (експорт/імпорт без сервера) ----------

    /** JSON-набору: [{id,version,branch,enabled}] — для переносу на інший пристрій. */
    public static String exportModuleSet() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (InstalledInfo info : listInstalled()) {
            if (!info.active) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":\"").append(info.manifest.id)
                    .append("\",\"version\":\"").append(info.manifest.version)
                    .append("\",\"branch\":\"").append(info.manifest.branch)
                    .append("\",\"enabled\":").append(info.enabled).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    /** Розбір імпортованого набору в сирі записи (id/version/branch/enabled). */
    public static List<Map<String, String>> parseModuleSet(String json) {
        List<Map<String, String>> res = new ArrayList<>();
        if (json == null) return res;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                if (id.isEmpty()) continue;
                Map<String, String> m = new HashMap<>();
                m.put("id", id);
                m.put("version", o.optString("version", ""));
                m.put("branch", o.optString("branch", "stable"));
                m.put("enabled", o.optBoolean("enabled", true) ? "1" : "0");
                res.add(m);
            }
        } catch (Throwable ignore) {
        }
        return res;
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
        Set<String> listedIds = new HashSet<>();
        for (InstalledInfo info : res) {
            listedIds.add(info.manifest.id);
        }
        // 0 вбудованих: фейкові записи "1.0.0" більше не інжектимо.
        // Все, що на диску — реальні .hmod з валідним manifest.json.
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

    /** Чи є в .hmod код (classes.dex). Legacy source-only збірки — ні. */
    public static boolean zipHasDex(File hmod) {
        try (ZipFile zip = new ZipFile(hmod)) {
            java.util.Enumeration<? extends ZipEntry> en = zip.entries();
            while (en.hasMoreElements()) {
                if (en.nextElement().getName().endsWith(".dex")) return true;
            }
        } catch (Throwable ignore) {
        }
        return false;
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

    public static boolean getBool(String moduleId, String key, boolean def) {
        if (appContext == null) return def;
        return appContext.getSharedPreferences("hotmod_" + moduleId, Context.MODE_PRIVATE).getBoolean(key, def);
    }

    public static void putBool(String moduleId, String key, boolean value) {
        if (appContext == null) return;
        appContext.getSharedPreferences("hotmod_" + moduleId, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
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
                if (!zipHasDex(tmp)) {
                    // Legacy source-only артефакт (є в історії 1.0.0): маніфест валідний,
                    // але коду всередині нема — ставити нічого, ріжемо одразу зі змістом.
                    tmp.delete();
                    postProgress(cb, false, MiogramLocale.get(
                            "Збірка v" + m.version + " без коду (legacy) — виберіть новішу",
                            "Сборка v" + m.version + " без кода (legacy) — выберите новее",
                            "Build v" + m.version + " has no code (legacy) — pick a newer one"), null);
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
                boolean wasEnabled = prefs().getBoolean("enabled_" + moduleId, false);
                prefs().edit().putString("installed_" + moduleId, m.version).apply();
                // Старий Handle кешований — без dropLoaded новий код не підхопиться.
                dropLoaded(moduleId);
                clearLoadFailures(moduleId);
                boolean enableNow = autoEnable || wasEnabled;
                if (enableNow) {
                    prefs().edit().putBoolean("enabled_" + moduleId, true).apply();
                    Handle h = getHandle(moduleId);
                    if (h == null || h.instance == null) {
                        // Скачалось, але не стартує: лишаємо встановленим, але вимкненим,
                        // і віддаємо СПРАВЖНЮ причину замість німого "не завантажився".
                        prefs().edit().putBoolean("enabled_" + moduleId, false).apply();
                        pruneOld(moduleId);
                        prefs().edit().remove("uninstalled_" + moduleId).apply();
                        notifyChanged();
                        String why = getLastLoadError(moduleId);
                        postProgress(cb, false, MiogramLocale.get(
                                "Скачано v" + m.version + ", але не стартує"
                                        + (why.isEmpty() ? "" : ": " + why),
                                "Скачано v" + m.version + ", но не стартует"
                                        + (why.isEmpty() ? "" : ": " + why),
                                "Downloaded v" + m.version + " but failed to start"
                                        + (why.isEmpty() ? "" : ": " + why)), null);
                        return;
                    }
                }
                pruneOld(moduleId);
                clearUpdateAvailable(moduleId);
                prefs().edit().remove("uninstalled_" + moduleId).apply();
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
            if (enabled) {
                prefs().edit().remove("uninstalled_" + moduleId).putBoolean("enabled_" + moduleId, true).apply();
                Handle h = getHandle(moduleId);
                if (h == null || h.instance == null) {
                    prefs().edit().putBoolean("enabled_" + moduleId, false).apply();
                    notifyChanged();
                    String why = getLastLoadError(moduleId);
                    String q = isQuarantined(moduleId) ? MiogramLocale.get(
                            " Карантин: перевстановіть або скиньте в деталці.",
                            " Карантин: переустановите или сбросьте в деталке.",
                            " Quarantined: reinstall or reset in details.") : "";
                    post(cb, false, MiogramLocale.get(
                            "Модуль не стартував", "Модуль не стартовал", "Module failed to start")
                            + (why.isEmpty() ? "" : ": " + why) + q, null);
                    return;
                }
            } else {
                prefs().edit().putBoolean("enabled_" + moduleId, false).apply();
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
        prefs().edit()
                .putBoolean("uninstalled_" + moduleId, true)
                .remove("installed_" + moduleId)
                .remove("enabled_" + moduleId)
                .apply();
        notifyChanged();
    }

    // ---------- тонкий клієнт: вбудованих impl НЕМАЄ ----------
    // Весь код модулів живе в окремому репозиторії (amegram-modules) і в
    // assets/hotmodules/*.hmod як сід. Завантаження тільки через DexClassLoader
    // з диска. Той самий FQCN у APK відсутній, тому parent-first не тінить.

    public static HotModule createBuiltinModule(String moduleId) {
        return null;
    }

    // ---------- діагностика: ЧОМУ модуль не стартував ----------

    private static volatile String lastLoadErrorModule = "";
    private static volatile String lastLoadError = "";
    private static volatile String lastLoadStack = "";

    private static void setLoadError(String moduleId, String err, Throwable e) {
        lastLoadErrorModule = moduleId != null ? moduleId : "";
        lastLoadError = err != null ? err : "";
        if (e != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                e.printStackTrace(new java.io.PrintWriter(sw));
                String s = sw.toString();
                lastLoadStack = s.length() > 2000 ? s.substring(0, 2000) : s;
            } catch (Throwable ignore) {
                lastLoadStack = "";
            }
        } else {
            lastLoadStack = "";
        }
    }

    private static void setLoadError(String moduleId, String err) {
        setLoadError(moduleId, err, null);
    }

    /** Остання причина провалу старту ("" = невідомо/не було). */
    public static String getLastLoadError(String moduleId) {
        if (moduleId == null || !moduleId.equals(lastLoadErrorModule)) return "";
        return lastLoadError;
    }

    /** Повна діагностика для копіювання: модуль, версія, entry, помилка, стек. */
    public static String getLoadDiagnostics(String moduleId) {
        StringBuilder sb = new StringBuilder();
        sb.append("module=").append(moduleId).append("\n");
        try {
            String ver = appContext != null
                    ? prefs().getString("installed_" + moduleId, "?") : "?";
            sb.append("installed=").append(ver).append("\n");
            if (appContext != null) {
                File c = new File(new File(new File(root(), moduleId), ver), "module.hmod");
                sb.append("file=").append(c.exists() ? ("OK " + c.length() + "b") : "MISSING").append("\n");
                if (c.exists()) {
                    try {
                        Manifest m = readManifest(c);
                        sb.append("entry=").append(m.entry).append("\n");
                        sb.append("dex=").append(zipHasDex(c) ? "yes" : "NO").append("\n");
                    } catch (Throwable e) {
                        sb.append("manifest=BROKEN ").append(shortErr(e)).append("\n");
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        sb.append("enabled=").append(isModuleEnabled(moduleId)).append("\n");
        sb.append("quarantine=").append(getLoadFailures(moduleId)).append("\n");
        sb.append("error=").append(getLastLoadError(moduleId)).append("\n");
        if (moduleId != null && moduleId.equals(lastLoadErrorModule) && !lastLoadStack.isEmpty()) {
            sb.append("stack:\n").append(lastLoadStack);
        }
        return sb.toString();
    }

    private static String shortErr(Throwable e) {
        if (e == null) return "невідома помилка";
        String m = e.getMessage();
        String n = e.getClass().getSimpleName();
        if (m == null || m.isEmpty()) return n;
        if (m.length() > 140) m = m.substring(0, 140) + "…";
        return n + ": " + m;
    }

    // ---------- загрузка кода (только включённые) ----------

    public static synchronized Handle getHandle(String moduleId) {
        Handle cached = LOADED.get(moduleId);
        if (cached != null) return cached;
        if (appContext == null) {
            init(ApplicationLoader.applicationContext);
            if (appContext == null) return null;
        }
        if (!isModuleEnabled(moduleId)) return null;
        if (isQuarantined(moduleId)) {
            // Карантин: модуль роняв клієнт 3+ рази. Юзер знімає вручну (перевстановлення).
            FileLog.e("hotmods: quarantined, skip load: " + moduleId);
            return null;
        }

        String entryClass = null;
        String version = prefs().getString("installed_" + moduleId, "");
        String name = moduleId;

        File hmod = null;
        Manifest m = null;
        if (version != null && !version.isEmpty()) {
            File candidate = new File(new File(new File(root(), moduleId), version), "module.hmod");
            if (candidate.exists()) {
                hmod = candidate;
                try {
                    m = readManifest(hmod);
                    if (m != null && m.entry != null) {
                        entryClass = m.entry;
                        name = m.name;
                    }
                } catch (Throwable ignore) {
                }
            }
        }

        if (m == null) {
            // Активна версія не знайдена (розсинхрон префів) — шукаємо будь-яку
            // реальну версію на диску і беремо найновішу, одразу чинимо преф.
            try {
                File modDir = new File(root(), moduleId);
                File[] vers = modDir.listFiles();
                Manifest best = null;
                File bestFile = null;
                if (vers != null) {
                    for (File vdir : vers) {
                        if (!vdir.isDirectory()) continue;
                        File c = new File(vdir, "module.hmod");
                        if (!c.exists()) continue;
                        try {
                            Manifest cm = readManifest(c);
                            if (!moduleId.equals(cm.id)) continue;
                            if (best == null || cm.version.compareTo(best.version) > 0) {
                                best = cm;
                                bestFile = c;
                            }
                        } catch (Throwable ignore) {
                        }
                    }
                }
                if (best != null) {
                    m = best;
                    hmod = bestFile;
                    entryClass = m.entry;
                    name = m.name;
                    prefs().edit().putString("installed_" + moduleId, m.version).apply();
                }
            } catch (Throwable ignore) {
            }
        }

        if (m == null || entryClass == null) {
            // Немає реального .hmod на диску — модуля фактично немає (0 вбудованих).
            setLoadError(moduleId, MiogramLocale.get("немає файлу модуля на диску",
                    "нет файла модуля на диске", "module file missing on disk"));
            return null;
        }

        Class<?> cls = null;
        ClassLoader modLoader = null;
        Throwable dexErr = null;
        // 1. Динамічний .hmod з диска — основний шлях (0 вбудованих в APK).
        // Кожен модуль вантажиться у ВЛАСНИЙ ізольований ClassLoader.
        if (hmod != null && hmod.exists()) {
            try {
                DexClassLoader loader = new DexClassLoader(hmod.getAbsolutePath(),
                        dexOptDir().getAbsolutePath(), null, appContext.getClassLoader());
                modLoader = loader;
                cls = Class.forName(entryClass, true, loader);
            } catch (Throwable dexEx) {
                dexErr = dexEx;
                FileLog.e("hotmods: DexClassLoader failed: " + moduleId, dexEx);
            }
        }
        // 2. Запасний шлях: клас, реально скомпільований в APK (майбутні COMPILED_IN).
        if (cls == null) {
            try {
                cls = appContext.getClassLoader().loadClass(entryClass);
                modLoader = appContext.getClassLoader();
            } catch (ClassNotFoundException ignore) {
            }
        }

        if (cls == null) {
            FileLog.e("hotmods: load failed, class not found: " + moduleId + " (" + entryClass + ")");
            setLoadError(moduleId, MiogramLocale.get("клас не знайдено",
                    "класс не найден", "class not found")
                    + " " + entryClass
                    + (dexErr != null ? " (" + shortErr(dexErr) + ")" : ""), dexErr);
            recordLoadFailure(moduleId);
            return null;
        }

        try {
            Object obj = cls.newInstance();
            if (!(obj instanceof HotModule)) {
                FileLog.e("hotmods: entry is not HotModule: " + entryClass);
                setLoadError(moduleId, "entry is not HotModule: " + entryClass);
                recordLoadFailure(moduleId);
                return null;
            }
            HotModule mod = (HotModule) obj;
            InstalledInfo info = new InstalledInfo(m, hmod, true, true);
            Handle handle = new Handle(info, mod, modLoader);
            LOADED.put(moduleId, handle);
            try {
                mod.onAttach(appContext, new HostImpl(moduleId));
            } catch (Throwable e) {
                // Падіння в onAttach не роняє апку: знімаємо хендл, рахуємо провал.
                FileLog.e("hotmods: onAttach failed: " + moduleId, e);
                LOADED.remove(moduleId);
                try {
                    mod.onDetach();
                } catch (Throwable ignore) {
                }
                setLoadError(moduleId, "onAttach: " + shortErr(e), e);
                recordLoadFailure(moduleId);
                return null;
            }
            clearLoadFailures(moduleId);
            setLoadError(moduleId, "");
            return handle;
        } catch (Throwable e) {
            FileLog.e("hotmods: instantiation failed for " + moduleId, e);
            setLoadError(moduleId, shortErr(e), e);
            recordLoadFailure(moduleId);
            return null;
        }
    }

    /** Зняти карантин вручну (перевстановлення / кнопка "Спробувати знову"). */
    public static void unquarantine(String moduleId) {
        clearLoadFailures(moduleId);
        notifyChanged();
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
        public void setBool(String key, boolean value) {
            putBool(key, value);
        }

        @Override
        public void setString(String key, String value) {
            putString(key, value);
        }

        @Override
        public void setInt(String key, int value) {
            putInt(key, value);
        }

        @Override
        public void openModuleScreen(String screenId) {
            HotModulesManager.openModuleScreen(moduleId, screenId);
        }

        @Override
        public void openSettings() {
            openModuleScreen("settings");
        }
    }

    /** Хост для экрана настроек: модуль уже загружен, префы те же. */
    public static HotHost hostFor(String moduleId) {
        init(ApplicationLoader.applicationContext);
        return new HostImpl(moduleId);
    }
}
