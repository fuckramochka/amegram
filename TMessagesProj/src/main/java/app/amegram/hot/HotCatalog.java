package app.amegram.hot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель каталога modules.json из отдельного репозитория.
 * Формат (см. tools/hotmod-sdk/ + docs/HOTMOD_SDK.md):
 * { modules: [ { id, name, description, entry, author, category, featured,
 *   branches: { stable: {version,url,sha256,signature,sizeBytes,minApp,changelog,permissions[]}, beta: {...} },
 *   history: [ {branch,version,url,sha256,...} ] } ] }
 * Усі нові поля опціональні — старий modules.json парситься як раніше.
 */
public final class HotCatalog {

    public static final class Build {
        public final String branch;
        public final String version;
        public final String url;
        public final String sha256;
        /** Опціональний detached-підпис (base64). Порожньо = лише sha256-довіра. */
        public final String signature;
        /** Розмір .hmod у байтах (0 = невідомо). */
        public final long sizeBytes;
        public final int minApp;
        public final String changelog;
        public final List<String> permissions;

        Build(String branch, String version, String url, String sha256, String signature,
              long sizeBytes, int minApp, String changelog, List<String> permissions) {
            this.branch = branch;
            this.version = version;
            this.url = url;
            this.sha256 = sha256;
            this.signature = signature != null ? signature : "";
            this.sizeBytes = sizeBytes;
            this.minApp = minApp;
            this.changelog = changelog != null ? changelog : "";
            this.permissions = permissions != null ? permissions : new ArrayList<>();
        }

        public boolean isSigned() {
            return signature != null && !signature.isEmpty();
        }
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String description;
        public final String entry;
        public final String author;
        public final String category;
        public final boolean featured;
        public final Map<String, Build> branches = new LinkedHashMap<>();
        public final List<Build> history = new ArrayList<>();

        Entry(String id, String name, String description, String entry,
              String author, String category, boolean featured) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.entry = entry;
            this.author = author != null ? author : "";
            this.category = category != null && !category.isEmpty() ? category : "other";
            this.featured = featured;
        }

        public Build defaultBuild() {
            Build b = branches.get("stable");
            if (b != null) return b;
            for (Build x : branches.values()) return x;
            return null;
        }

        /** Усі збірки: гілки + історія (для каруселі версій). */
        public List<Build> allBuilds() {
            List<Build> all = new ArrayList<>(branches.values());
            all.addAll(history);
            return all;
        }

        /** Найновіша сумісна збірка (minApp <= appVer), інакше defaultBuild. */
        public Build latestCompatible(int appVer) {
            Build def = defaultBuild();
            if (appVer <= 0) return def;
            for (Build b : branches.values()) {
                if (b.minApp > 0 && b.minApp <= appVer) return b;
            }
            return def;
        }
    }

    public final List<Entry> modules = new ArrayList<>();
    public final long updated;

    private HotCatalog(long updated) {
        this.updated = updated;
    }

    public Entry find(String id) {
        for (Entry e : modules) {
            if (e.id.equals(id)) return e;
        }
        return null;
    }

    /** Унікальні категорії в порядку появи (для чипів фільтра). */
    public List<String> categories() {
        List<String> res = new ArrayList<>();
        for (Entry e : modules) {
            if (!res.contains(e.category)) res.add(e.category);
        }
        return res;
    }

    public static HotCatalog parse(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("empty catalog");
        }
        try {
            JSONObject root = new JSONObject(json);
            HotCatalog catalog = new HotCatalog(root.optLong("updated", 0));
            JSONArray arr = root.optJSONArray("modules");
            if (arr == null) return catalog;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                String entry = o.optString("entry", "");
                if (id.isEmpty() || entry.isEmpty()) continue;
                Entry e = new Entry(id, o.optString("name", id),
                        o.optString("description", ""), entry,
                        o.optString("author", ""), o.optString("category", "other"),
                        o.optBoolean("featured", false));
                JSONObject branches = o.optJSONObject("branches");
                if (branches != null) {
                    for (String branch : new String[]{"stable", "beta", "alpha", "dev"}) {
                        JSONObject b = branches.optJSONObject(branch);
                        if (b != null) {
                            Build build = parseBuild(branch, b);
                            if (build != null) e.branches.put(branch, build);
                        }
                    }
                    JSONArray names = branches.names();
                    if (names != null) {
                        for (int k = 0; k < names.length(); k++) {
                            String branch = names.optString(k, "");
                            if (!e.branches.containsKey(branch)) {
                                Build build = parseBuild(branch, branches.optJSONObject(branch));
                                if (build != null) e.branches.put(branch, build);
                            }
                        }
                    }
                }
                JSONArray history = o.optJSONArray("history");
                if (history != null) {
                    for (int j = 0; j < history.length(); j++) {
                        JSONObject h = history.optJSONObject(j);
                        if (h == null) continue;
                        Build build = parseBuild(h.optString("branch", "stable"), h);
                        if (build != null) e.history.add(build);
                    }
                }
                catalog.modules.add(e);
            }
            return catalog;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("bad catalog: " + e.getMessage());
        }
    }

    private static Build parseBuild(String branch, JSONObject b) {
        if (b == null) return null;
        String version = b.optString("version", "");
        String url = b.optString("url", "");
        if (version.isEmpty() || url.isEmpty()) return null;
        if (!url.startsWith("https://")) return null;
        List<String> perms = new ArrayList<>();
        JSONArray pa = b.optJSONArray("permissions");
        if (pa != null) {
            for (int i = 0; i < pa.length(); i++) {
                String p = pa.optString(i, "");
                if (!p.isEmpty()) perms.add(p);
            }
        }
        return new Build(branch, version, url, b.optString("sha256", ""),
                b.optString("signature", ""), b.optLong("sizeBytes", 0),
                b.optInt("minApp", 0), b.optString("changelog", ""), perms);
    }

    /** Людський розмір: 48 КБ / 1.9 МБ. */
    public static String formatSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " КБ";
        return String.format("%.1f МБ", bytes / (1024f * 1024f));
    }
}
