package app.miogram.bridge.roblox;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.miogram.bridge.MiogramLocale;

/**
 * Public Roblox Presence Bridge for Miogram.
 *
 * - Username resolving, avatars and game names are keyless public REST.
 * - Live presence (online / in-game / in-studio + current game) requires a
 *   .ROBLOSECURITY cookie, because Roblox presence endpoints are auth-only.
 *   The cookie is stored in private app prefs and never leaves the device
 *   except for direct api.roblox.com presence calls.
 */
public class MiogramRobloxManager {

    public static final int TYPE_UNKNOWN = -1;
    public static final int TYPE_OFFLINE = 0;
    public static final int TYPE_ONLINE = 1;
    public static final int TYPE_IN_GAME = 2;
    public static final int TYPE_IN_STUDIO = 3;

    public static class RobloxPresence {
        public long userId;
        public int type = TYPE_UNKNOWN;
        public String lastLocation = "";
        public long placeId;
        public long rootPlaceId;
        public long universeId;
        public String gameName = "";
        public String lastOnline = "";
        public long lastUpdated;

        public boolean isInGame() {
            return type == TYPE_IN_GAME && !TextUtils.isEmpty(gameName);
        }

        public String getStatusText() {
            switch (type) {
                case TYPE_IN_GAME:
                    return !TextUtils.isEmpty(gameName) ? gameName : (!TextUtils.isEmpty(lastLocation) ? lastLocation : "In-Game");
                case TYPE_IN_STUDIO:
                    return MiogramLocale.get("У Roblox Studio", "В Roblox Studio", "In Roblox Studio");
                case TYPE_ONLINE:
                    return MiogramLocale.get("В мережі", "В сети", "Online");
                case TYPE_OFFLINE:
                    return MiogramLocale.get("Не в мережі", "Не в сети", "Offline");
                default:
                    return MiogramLocale.get("Статус невідомий", "Статус неизвестен", "Unknown status");
            }
        }
    }

    public static class RobloxUser {
        public long userId;
        public String username = "";
        public String displayName = "";
        public String avatarUrl = "";
    }

    public interface UserCallback {
        void onUserLoaded(RobloxUser user);
    }

    public interface PresenceCallback {
        void onPresenceLoaded(RobloxPresence presence);
    }

    public interface PresenceListCallback {
        void onPresenceLoaded(List<RobloxPresence> list);
    }

    private static volatile MiogramRobloxManager instance;

    public static MiogramRobloxManager getInstance() {
        if (instance == null) {
            synchronized (MiogramRobloxManager.class) {
                if (instance == null) {
                    instance = new MiogramRobloxManager();
                }
            }
        }
        return instance;
    }

    private static final String PREFS_NAME = "miogram_roblox_prefs";
    private static final String KEY_USERNAME = "roblox_username";
    private static final String KEY_USER_ID = "roblox_user_id";
    private static final String KEY_COOKIE = "roblox_cookie";
    private static final String KEY_DISPLAY = "roblox_display";
    private static final String KEY_AVATAR = "roblox_avatar";
    private static final String KEY_GAME = "roblox_game";
    private static final String KEY_UNIVERSE = "roblox_universe";
    private static final String KEY_PTYPE = "roblox_ptype";

    private final Map<Long, String> universeNameCache = new ConcurrentHashMap<>();
    private volatile RobloxPresence selfPresence;

    private MiogramRobloxManager() {
        loadSelfFromCache();
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLinked() {
        return getLinkedUserId() > 0;
    }

    public boolean hasCookie() {
        return !TextUtils.isEmpty(getCookie());
    }

    public String getLinkedUsername() {
        return getPrefs().getString(KEY_USERNAME, "");
    }

    public long getLinkedUserId() {
        return getPrefs().getLong(KEY_USER_ID, 0);
    }

    public String getCookie() {
        return getPrefs().getString(KEY_COOKIE, "");
    }

    public RobloxPresence getSelfPresence() {
        return selfPresence;
    }

    public void setCookie(String cookie) {
        String clean = cookie != null ? cookie.trim() : "";
        if (clean.startsWith(".ROBLOSECURITY=")) clean = clean.substring(".ROBLOSECURITY=".length());
        if (clean.endsWith(";")) clean = clean.substring(0, clean.length() - 1);
        getPrefs().edit().putString(KEY_COOKIE, clean.trim()).apply();
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public void setLinkedUsername(String username, UserCallback callback) {
        String clean = sanitizeUsername(username);
        if (TextUtils.isEmpty(clean)) {
            if (callback != null) callback.onUserLoaded(null);
            return;
        }
        resolveUser(clean, user -> {
            if (user != null) {
                SharedPreferences.Editor ed = getPrefs().edit();
                ed.putString(KEY_USERNAME, user.username);
                ed.putLong(KEY_USER_ID, user.userId);
                ed.putString(KEY_DISPLAY, user.displayName);
                ed.putString(KEY_AVATAR, user.avatarUrl);
                ed.apply();
                selfPresence = null;
                refreshSelf(p -> app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0));
            }
            if (callback != null) callback.onUserLoaded(user);
        });
    }

    public void unlink() {
        getPrefs().edit().clear().apply();
        selfPresence = null;
        universeNameCache.clear();
        app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
    }

    public static String sanitizeUsername(String input) {
        if (input == null) return "";
        String s = input.trim();
        if (s.startsWith("https://www.roblox.com/users/")) s = s.substring("https://www.roblox.com/users/".length());
        else if (s.startsWith("https://roblox.com/users/")) s = s.substring("https://roblox.com/users/".length());
        else if (s.startsWith("http://www.roblox.com/users/")) s = s.substring("http://www.roblox.com/users/".length());
        else if (s.startsWith("www.roblox.com/users/")) s = s.substring("www.roblox.com/users/".length());
        else if (s.startsWith("roblox.com/users/")) s = s.substring("roblox.com/users/".length());
        if (s.startsWith("@")) s = s.substring(1);
        if (s.contains("?")) s = s.substring(0, s.indexOf("?"));
        if (s.contains("#")) s = s.substring(0, s.indexOf("#"));
        if (s.contains("/")) s = s.substring(0, s.indexOf("/"));
        return s.trim();
    }

    /**
     * Resolves a Roblox username to userId (keyless public API) + avatar.
     */
    public void resolveUser(String username, UserCallback callback) {
        final String clean = sanitizeUsername(username);
        if (TextUtils.isEmpty(clean)) {
            if (callback != null) callback.onUserLoaded(null);
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            RobloxUser result = null;
            try {
                JSONObject body = new JSONObject();
                JSONArray names = new JSONArray();
                names.put(clean);
                body.put("usernames", names);
                body.put("excludeBannedUsers", true);
                String resp = httpPostJson("https://users.roblox.com/v1/usernames/users", body.toString(), null);
                if (!TextUtils.isEmpty(resp)) {
                    JSONArray data = new JSONObject(resp).optJSONArray("data");
                    if (data != null && data.length() > 0) {
                        JSONObject o = data.getJSONObject(0);
                        result = new RobloxUser();
                        result.userId = o.optLong("id", 0);
                        result.username = o.optString("name", clean);
                        result.displayName = o.optString("displayName", result.username);
                        if (result.userId > 0) {
                            result.avatarUrl = fetchAvatarUrl(result.userId);
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e("MiogramRoblox: resolve error", t);
            }
            final RobloxUser f = result;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onUserLoaded(f);
            });
        });
    }

    /**
     * Live presence for the linked self account (requires cookie).
     */
    public void refreshSelf(PresenceCallback callback) {
        long uid = getLinkedUserId();
        if (uid <= 0) {
            if (callback != null) callback.onPresenceLoaded(null);
            return;
        }
        ArrayList<Long> ids = new ArrayList<>(1);
        ids.add(uid);
        fetchPresenceList(ids, list -> {
            RobloxPresence p = (list != null && !list.isEmpty()) ? list.get(0) : null;
            if (p != null) {
                selfPresence = p;
                saveSelfToCache(p);
            }
            if (callback != null) callback.onPresenceLoaded(p);
        });
    }

    /**
     * Live presence for arbitrary users. Viewers query with THEIR OWN cookie —
     * without a linked cookie only the cloud snapshot is available.
     */
    public void fetchPresenceList(List<Long> userIds, PresenceListCallback callback) {
        if (userIds == null || userIds.isEmpty()) {
            if (callback != null) callback.onPresenceLoaded(null);
            return;
        }
        final String cookie = getCookie();
        if (TextUtils.isEmpty(cookie)) {
            // No cookie: live presence API is auth-only.
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onPresenceLoaded(null);
            });
            return;
        }
        final ArrayList<Long> ids = new ArrayList<>(userIds);
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<RobloxPresence> out = new ArrayList<>();
            try {
                JSONObject body = new JSONObject();
                JSONArray arr = new JSONArray();
                for (Long id : ids) arr.put(id);
                body.put("userIds", arr);
                String resp = httpPostJson("https://presence.roblox.com/v1/presence/users", body.toString(), cookie);
                if (!TextUtils.isEmpty(resp)) {
                    JSONArray data = new JSONObject(resp).optJSONArray("userPresences");
                    if (data != null) {
                        for (int i = 0; i < data.length(); i++) {
                            JSONObject o = data.getJSONObject(i);
                            RobloxPresence p = new RobloxPresence();
                            p.userId = o.optLong("userId", 0);
                            p.type = o.optInt("userPresenceType", TYPE_UNKNOWN);
                            p.lastLocation = o.optString("lastLocation", "");
                            p.placeId = o.optLong("placeId", 0);
                            p.rootPlaceId = o.optLong("rootPlaceId", 0);
                            p.universeId = o.optLong("universeId", 0);
                            p.lastOnline = o.optString("lastOnline", "");
                            p.lastUpdated = System.currentTimeMillis();
                            if (p.type == TYPE_IN_GAME) {
                                p.gameName = resolveGameName(p.universeId, p.lastLocation);
                            }
                            out.add(p);
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e("MiogramRoblox: presence error", t);
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onPresenceLoaded(out);
            });
        });
    }

    private String resolveGameName(long universeId, String fallback) {
        if (universeId <= 0) return fallback != null ? fallback : "";
        String cached = universeNameCache.get(universeId);
        if (cached != null) return cached;
        try {
            String resp = httpGet("https://games.roblox.com/v1/games?universeIds=" + universeId);
            if (!TextUtils.isEmpty(resp)) {
                JSONArray data = new JSONObject(resp).optJSONArray("data");
                if (data != null && data.length() > 0) {
                    String name = data.getJSONObject(0).optString("name", "");
                    if (!TextUtils.isEmpty(name)) {
                        universeNameCache.put(universeId, name);
                        SharedPreferences.Editor ed = getPrefs().edit();
                        ed.putString("universe_name_" + universeId, name);
                        ed.apply();
                        return name;
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.d("MiogramRoblox: game name error " + t.getMessage());
        }
        String stored = getPrefs().getString("universe_name_" + universeId, "");
        if (!TextUtils.isEmpty(stored)) {
            universeNameCache.put(universeId, stored);
            return stored;
        }
        return fallback != null ? fallback : "";
    }

    private String fetchAvatarUrl(long userId) {
        try {
            String resp = httpGet("https://thumbnails.roblox.com/v1/users/avatar-headshot?userIds=" + userId + "&size=150x150&format=Png&isCircular=false");
            if (!TextUtils.isEmpty(resp)) {
                JSONArray data = new JSONObject(resp).optJSONArray("data");
                if (data != null && data.length() > 0) {
                    return data.getJSONObject(0).optString("imageUrl", "");
                }
            }
        } catch (Throwable t) {
            FileLog.d("MiogramRoblox: avatar error " + t.getMessage());
        }
        return "";
    }

    public String getDisplayName() {
        String d = getPrefs().getString(KEY_DISPLAY, "");
        return !TextUtils.isEmpty(d) ? d : getLinkedUsername();
    }

    public String getAvatarUrl() {
        return getPrefs().getString(KEY_AVATAR, "");
    }

    private void saveSelfToCache(RobloxPresence p) {
        SharedPreferences.Editor ed = getPrefs().edit();
        if (!TextUtils.isEmpty(p.gameName)) ed.putString(KEY_GAME, p.gameName);
        else ed.remove(KEY_GAME);
        ed.putLong(KEY_UNIVERSE, p.universeId);
        ed.putInt(KEY_PTYPE, p.type);
        ed.apply();
    }

    private void loadSelfFromCache() {
        if (getLinkedUserId() <= 0) return;
        RobloxPresence p = new RobloxPresence();
        p.userId = getLinkedUserId();
        p.type = getPrefs().getInt(KEY_PTYPE, TYPE_UNKNOWN);
        p.gameName = getPrefs().getString(KEY_GAME, "");
        p.universeId = getPrefs().getLong(KEY_UNIVERSE, 0);
        selfPresence = p;
    }

    public void openProfile(Context context, long userId) {
        if (context == null || userId <= 0) return;
        try {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.roblox.com/users/" + userId + "/profile"));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable ignore) {}
    }

    public void openGame(Context context, long placeId, long universeId) {
        if (context == null) return;
        try {
            String url = placeId > 0
                    ? "roblox://experiences/start?placeId=" + placeId
                    : "https://www.roblox.com/games/" + universeId;
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            try {
                android.content.Intent web = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://www.roblox.com/home"));
                web.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(web);
            } catch (Throwable ignore) {}
        }
    }

    public void showLinkDialog(Context context, UserCallback callback) {
        if (context == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Roblox Акаунт", "Roblox Аккаунт", "Roblox Account"));
        builder.setMessage(MiogramLocale.get(
                "Введіть ваш Roblox username (наприклад, builderman):",
                "Введите ваш Roblox username (например, builderman):",
                "Enter your Roblox username (e.g. builderman):"
        ));
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(true);
        input.setText(getLinkedUsername());
        builder.setView(input);
        builder.setPositiveButton(MiogramLocale.get("Підключити", "Подключить", "Connect"), (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                setLinkedUsername(val, callback);
            }
        });
        builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        builder.show();
    }

    public void showCookieDialog(Context context, Runnable onDone) {
        if (context == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Roblox Cookie", "Roblox Cookie", "Roblox Cookie"));
        builder.setMessage(MiogramLocale.get(
                "Для живого статусу (онлайн / у грі / назва гри) вставте .ROBLOSECURITY cookie.\n\nДе взяти: увійдіть на roblox.com у браузері → DevTools (F12) → Application → Cookies → скопіюйте .ROBLOSECURITY.\n\nУВАГА: нікому не показуйте цей ключ — він дає доступ до акаунта. Зберігається лише на цьому пристрої. Краще використати альт-акаунт для друзів.",
                "Для живого статуса (онлайн / в игре / название игры) вставьте .ROBLOSECURITY cookie.\n\nГде взять: войдите на roblox.com в браузере → DevTools (F12) → Application → Cookies → скопируйте .ROBLOSECURITY.\n\nВНИМАНИЕ: никому не показывайте этот ключ — он даёт доступ к аккаунту. Хранится только на этом устройстве. Для друзей лучше взять альт-аккаунт.",
                "For live status (online / in-game / game name) paste your .ROBLOSECURITY cookie.\n\nHow: log in at roblox.com in a browser → DevTools (F12) → Application → Cookies → copy .ROBLOSECURITY.\n\nWARNING: never share this key — it grants account access. Stored on this device only. Use an alt to watch friends."
        ));
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(true);
        input.setHint(".ROBLOSECURITY");
        builder.setView(input);
        builder.setPositiveButton(MiogramLocale.get("Зберегти", "Сохранить", "Save"), (dialog, which) -> {
            String val = input.getText().toString().trim();
            if (!TextUtils.isEmpty(val)) {
                setCookie(val);
                refreshSelf(p -> {
                    app.miogram.bridge.presence.MiogramCloudPresence.syncSelfToCloud(0);
                    if (onDone != null) onDone.run();
                });
            }
        });
        builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);
        builder.show();
    }

    // ---------- HTTP ----------

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Miogram-App");
            conn.setRequestProperty("Accept", "application/json");
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                InputStream in = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                return sb.toString();
            }
        } catch (Throwable ignore) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String httpPostJson(String urlStr, String jsonBody, String cookie) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Miogram-App");
            if (!TextUtils.isEmpty(cookie)) {
                conn.setRequestProperty("Cookie", ".ROBLOSECURITY=" + cookie);
            }
            byte[] bodyBytes = jsonBody.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(bodyBytes.length);
            OutputStream out = conn.getOutputStream();
            out.write(bodyBytes);
            out.flush();
            out.close();
            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                if (code >= 200 && code < 300) return sb.toString();
                FileLog.d("MiogramRoblox POST " + urlStr + " -> HTTP " + code + " " + sb);
            }
        } catch (Throwable e) {
            FileLog.d("MiogramRoblox POST failed: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }
}
