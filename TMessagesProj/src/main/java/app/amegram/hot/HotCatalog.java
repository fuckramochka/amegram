package app.amegram.hot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Модель каталога modules.json из отдельного репозитория.
 * Формат (см. hotmodules-template/modules.json):
 * { modules: [ { id, name, description, entry,
 *   branches: { stable: {version,url,sha256,minApp,changelog}, beta: {...} },
 *   history: [ {branch,version,url,sha256,minApp,changelog} ] } ] }
 */
public final class HotCatalog {

    public static final class Build {
        public final String branch;
        public final String version;
        public final String url;
        public final String sha256;
        public final int minApp;
        public final String changelog;

        Build(String branch, String version, String url, String sha256, int minApp, String changelog) {
            this.branch = branch;
            this.version = version;
            this.url = url;
            this.sha256 = sha256;
            this.minApp = minApp;
            this.changelog = changelog;
        }
    }

    public static final class Entry {
        public final String id;
        public final String name;
        public final String description;
        public final String entry;
        public final Map<String, Build> branches = new LinkedHashMap<>();
        public final List<Build> history = new ArrayList<>();

        Entry(String id, String name, String description, String entry) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.entry = entry;
        }

        public Build defaultBuild() {
            Build b = branches.get("stable");
            if (b != null) return b;
            for (Build x : branches.values()) return x;
            return null;
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
                        o.optString("description", ""), entry);
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
        return new Build(branch, version, url, b.optString("sha256", ""),
                b.optInt("minApp", 0), b.optString("changelog", ""));
    }
}
