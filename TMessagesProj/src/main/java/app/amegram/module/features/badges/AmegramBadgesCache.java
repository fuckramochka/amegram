package app.amegram.module.features.badges;

import android.os.Handler;
import android.os.Looper;

import app.amegram.module.AmegramConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline-first badge cloud. No network on cold start, no Supabase SDK,
 * no API key in the module transport — the endpoint URL is configurable
 * and reads are plain public REST. Fetch happens only when a profile
 * with an unknown badge opens, on a background thread.
 *
 * Legacy MiogramSupabaseBridge stays as fallback while the module is dormant;
 * once badges are enabled in the hub, this cache is the source of truth.
 */
public final class AmegramBadgesCache {

    public interface Callback {
        void onResult(String badgeIdOrNull);
    }

    private static final String KEY_JSON = "badges_cloud_json_v1";
    private static final int TIMEOUT_MS = 8000;

    private static final Map<Long, String> memory = new HashMap<>();
    private static volatile boolean restored;

    private AmegramBadgesCache() {
    }

    /** Cached badge id for user, or null when unknown. Never touches network. */
    public static synchronized String getCached(long userId) {
        ensureRestored();
        return memory.get(userId);
    }

    /** Resolve badge, fetching from cloud once when unknown. Callback on UI thread. */
    public static void resolve(final long userId, final Callback cb) {
        String hit = getCached(userId);
        if (hit != null) {
            post(cb, hit);
            return;
        }
        final String endpoint = AmegramConfig.getString("badges_endpoint", "");
        if (endpoint.isEmpty()) {
            post(cb, null);
            return;
        }
        new Thread(() -> {
            String fetched = fetchBadge(endpoint, userId);
            if (fetched != null) {
                synchronized (AmegramBadgesCache.class) {
                    memory.put(userId, fetched);
                    persist();
                }
            }
            post(cb, fetched);
        }, "amegram-badges").start();
    }

    public static synchronized void putLocal(long userId, String badgeId) {
        ensureRestored();
        if (badgeId == null) {
            memory.remove(userId);
        } else {
            memory.put(userId, badgeId);
        }
        persist();
    }

    private static void ensureRestored() {
        if (restored) {
            return;
        }
        restored = true;
        try {
            String json = AmegramConfig.getString(KEY_JSON, "");
            if (!json.isEmpty()) {
                JSONObject root = new JSONObject(json);
                JSONArray keys = root.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String k = keys.getString(i);
                        memory.put(Long.parseLong(k), root.getString(k));
                    }
                }
            }
        } catch (Throwable ignore) {
        }
    }

    private static void persist() {
        try {
            JSONObject root = new JSONObject();
            for (Map.Entry<Long, String> e : memory.entrySet()) {
                root.put(Long.toString(e.getKey()), e.getValue());
            }
            AmegramConfig.setString(KEY_JSON, root.toString());
        } catch (Throwable ignore) {
        }
    }

    private static String fetchBadge(String endpoint, long userId) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint + "?select=badge_id&user_id=eq." + userId + "&limit=1");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() != 200) {
                return null;
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            JSONArray arr = new JSONArray(sb.toString());
            if (arr.length() == 0) {
                return null;
            }
            String id = arr.getJSONObject(0).optString("badge_id", null);
            return isKnownId(id) ? id : null;
        } catch (Throwable ignore) {
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignore) {
                }
            }
        }
    }

    private static boolean isKnownId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        for (String known : AmegramBadgesFeature.CANON_BADGES) {
            if (known.equalsIgnoreCase(id)) {
                return true;
            }
        }
        return false;
    }

    private static void post(Callback cb, String value) {
        if (cb == null) {
            return;
        }
        try {
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(value));
        } catch (Throwable t) {
            try {
                cb.onResult(value);
            } catch (Throwable ignore) {
            }
        }
    }
}
