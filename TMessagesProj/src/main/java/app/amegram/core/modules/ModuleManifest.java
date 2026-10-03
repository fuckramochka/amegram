package app.amegram.core.modules;

import org.json.JSONObject;

/**
 * Маніфест модуля `.amod`. Парсинг строгий: невідомі обов'язкові поля
 * або невідомі дозволи = модуль відхилено, без винятків назовні.
 */
public final class ModuleManifest {

    public final String id;
    public final String version;
    public final int minCoreAbi;
    public final long ramBudgetBytes;
    public final String entry;
    public final String[] permissions;
    public final String kind;

    private ModuleManifest(String id, String version, int minCoreAbi,
                           long ramBudgetBytes, String entry,
                           String[] permissions, String kind) {
        this.id = id;
        this.version = version;
        this.minCoreAbi = minCoreAbi;
        this.ramBudgetBytes = ramBudgetBytes;
        this.entry = entry;
        this.permissions = permissions;
        this.kind = kind;
    }

    public static ModuleManifest parse(String json) {
        if (json == null || json.isEmpty()) {
            throw new IllegalArgumentException("empty manifest");
        }
        try {
            return parseInner(json);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("bad manifest: " + e.getMessage());
        }
    }

    private static ModuleManifest parseInner(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        String id = o.optString("id", "");
        String version = o.optString("version", "");
        String entry = o.optString("entry", "");
        String kind = o.optString("kind", "python");
        if (id.isEmpty() || version.isEmpty() || entry.isEmpty()) {
            throw new IllegalArgumentException("id/version/entry required");
        }
        if (!"python".equals(kind) && !"dex".equals(kind) && !"hybrid".equals(kind)) {
            throw new IllegalArgumentException("unknown kind: " + kind);
        }
        org.json.JSONArray perms = o.optJSONArray("permissions");
        String[] permissions = new String[perms != null ? perms.length() : 0];
        for (int i = 0; i < permissions.length; i++) {
            String p = perms.optString(i, "");
            if (!app.amegram.core.security.Permissions.isKnown(p)) {
                throw new IllegalArgumentException("unknown permission: " + p);
            }
            permissions[i] = p;
        }
        return new ModuleManifest(id, version, o.optInt("minCoreAbi", 1),
                o.optLong("ramBudgetBytes", 4 * 1024 * 1024), entry, permissions, kind);
    }
}
