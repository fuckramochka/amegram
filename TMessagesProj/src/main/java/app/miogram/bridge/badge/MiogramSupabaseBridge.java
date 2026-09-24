package app.miogram.bridge.badge;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.LongSparseArray;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.os.Build;
import android.widget.Toast;
import java.net.URLEncoder;
import java.util.TimeZone;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.LaunchActivity;

import app.miogram.bridge.MiogramLocale;

/**
 * Cloud Bridge connecting Miogram clients to the Supabase community badge database.
 * Supports:
 * - Real-time cloud badge resolution with full lore & obtain history
 * - Multi-account strict isolation (only authorized user accounts show badges)
 * - In-memory and SharedPreferences caching for zero-latency offline performance
 */
public class MiogramSupabaseBridge {

    public static class BadgeRecord {
        public final long userId;
        public final MiogramBadgeType badgeType;
        public final java.util.List<MiogramBadgeType> badgeTypes;
        public final String badgeIdString;
        public final String title;
        public final String obtainedReason;
        public final String obtainedAt;
        public final boolean isActive;
        /** Server-staff verification. Self-claimed rows are always false. */
        public final boolean verified;
        /** user_id of the granter (founder grants carry FOUNDER_USER_ID). 0 = self-selected style. */
        public final long grantorId;

        public BadgeRecord(long userId, MiogramBadgeType badgeType, String title, String obtainedReason, String obtainedAt, boolean isActive) {
            this(userId, badgeType != null ? badgeType.getId() : "original", title, obtainedReason, obtainedAt, isActive, userId == MiogramBadgeManager.FOUNDER_USER_ID, 0);
        }

        public BadgeRecord(long userId, MiogramBadgeType badgeType, String title, String obtainedReason, String obtainedAt, boolean isActive, boolean verified) {
            this(userId, badgeType != null ? badgeType.getId() : "original", title, obtainedReason, obtainedAt, isActive, verified, 0);
        }

        public BadgeRecord(long userId, MiogramBadgeType badgeType, String title, String obtainedReason, String obtainedAt, boolean isActive, boolean verified, long grantorId) {
            this(userId, badgeType != null ? badgeType.getId() : "original", title, obtainedReason, obtainedAt, isActive, verified, grantorId);
        }

        public BadgeRecord(long userId, String badgeIds, String title, String obtainedReason, String obtainedAt, boolean isActive, boolean verified, long grantorId) {
            this.userId = userId;
            this.badgeIdString = badgeIds != null && !badgeIds.isEmpty() ? badgeIds : "original";
            java.util.List<MiogramBadgeType> list = new java.util.ArrayList<>();
            String[] parts = this.badgeIdString.split(",");
            for (String p : parts) {
                String clean = p.trim();
                if (!clean.isEmpty()) {
                    list.add(MiogramBadgeType.fromId(clean));
                }
            }
            if (list.isEmpty()) {
                list.add(MiogramBadgeType.ORIGINAL);
            }
            this.badgeTypes = java.util.Collections.unmodifiableList(list);
            this.badgeType = list.get(0);
            this.title = title != null ? title : "Miogram Community ໒꒱";
            this.obtainedReason = obtainedReason != null ? obtainedReason : "Верифікований учасник спільноти Miogram";
            this.obtainedAt = obtainedAt != null ? obtainedAt : "01.09.2026";
            this.isActive = isActive;
            this.verified = verified;
            this.grantorId = grantorId;
        }
    }

    private static final String PREFS_NAME = "miogram_supabase_prefs";
    private static final String KEY_OPTIN_COMPLETED = "badge_optin_completed";
    private static final String KEY_SYNC_ENABLED = "badge_sync_enabled_";
    private static final String KEY_SELECTED_BADGE = "badge_selected_style_";
    private static final String KEY_CACHE_JSON = "badge_cache_cloud_v10";
    private static final String KEY_TELEMETRY_ENABLED = "telemetry_enabled";

    public static boolean isTelemetryEnabled() {
        return getPrefs(null).getBoolean(KEY_TELEMETRY_ENABLED, true);
    }

    public static void setTelemetryEnabled(boolean enabled) {
        getPrefs(null).edit().putBoolean(KEY_TELEMETRY_ENABLED, enabled).apply();
    }

    public static final String DEFAULT_SUPABASE_URL = "https://dbxsnjoeyiqvqtrluvwu.supabase.co";
    public static final String DEFAULT_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRieHNuam9leWlxdnF0cmx1dnd1Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODg1NDI1MzEsImV4cCI6MjEwNDExODUzMX0.KJ0kvON1HXZu4MzlZjapSJEhEzWYlEqQoNEstWCgIjA";
    /**
     * Founder grant secret. Checked server-side by miogram_grant_badge /
     * miogram_revoke_badge RPCs (see supabase_schema.sql). Old builds don't
     * send it, so they fail closed. Keep in sync with the SQL migration.
     */
    private static final String GRANT_SECRET = "MIO-GRANT-mxxFB90ocCzVW9Fh8-AKiko5mANKNO5T";

    public static final String[] BADGES_TABLES = new String[]{"amegram_badges", "miogram_badges"};
    public static final String[] USERS_TABLES = new String[]{"amegram_users", "miogram_users"};
    public static final String[] RPC_GRANT = new String[]{"amegram_grant_badge", "miogram_grant_badge"};
    public static final String[] RPC_REVOKE = new String[]{"amegram_revoke_badge", "miogram_revoke_badge"};
    public static final String[] RPC_STATS = new String[]{"amegram_community_stats", "miogram_community_stats"};

    private static final LongSparseArray<BadgeRecord> badgeCache = new LongSparseArray<>();
    private static boolean initialized = false;

    private static SharedPreferences getPrefs(Context context) {
        Context ctx = context != null ? context : ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        synchronized (badgeCache) {
            badgeCache.clear();
            badgeCache.put(MiogramBadgeManager.FOUNDER_USER_ID, createDefaultFounderRecord());
        }

        // 1. Restore cached cloud badges from local storage
        try {
            SharedPreferences prefs = getPrefs(null);
            String cachedJson = prefs.getString(KEY_CACHE_JSON, null);
            if (!TextUtils.isEmpty(cachedJson)) {
                parseAndApplyBadgesJson(cachedJson);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        // 2. Trigger asynchronous background fetch from Supabase
        fetchBadgesFromCloud(null);

        // 3. Report active user account(s) presence into users table
        reportCurrentUserPresence();
    }

    private static BadgeRecord createDefaultFounderRecord() {
        return new BadgeRecord(
                MiogramBadgeManager.FOUNDER_USER_ID,
                MiogramBadgeType.ORIGINAL,
                "Засновник & Архітектор Miogram ໒꒱",
                "Особиста відзнака засновника та головного архітектора екосистеми Miogram (@fuckramochka).",
                "01.09.2026",
                true,
                true,
                MiogramBadgeManager.FOUNDER_USER_ID
        );
    }

    public static boolean hasCloudBadge(long userId) {
        if (userId <= 0) {
            return false;
        }
        if (userId == MiogramBadgeManager.FOUNDER_USER_ID) {
            return true;
        }
        init();
        synchronized (badgeCache) {
            BadgeRecord record = badgeCache.get(userId);
            if (record != null) {
                return record.isActive;
            }
            return isSyncEnabledForAccount(null, userId);
        }
    }

    public static BadgeRecord getBadgeRecord(long userId) {
        if (userId <= 0) {
            return null;
        }
        init();
        synchronized (badgeCache) {
            BadgeRecord record = badgeCache.get(userId);
            if (record == null && userId == MiogramBadgeManager.FOUNDER_USER_ID) {
                record = createDefaultFounderRecord();
                badgeCache.put(userId, record);
            } else if (record == null && isSyncEnabledForAccount(null, userId)) {
                MiogramBadgeType selected = getSelectedBadgeForAccount(null, userId);
                record = new BadgeRecord(userId, selected, fallbackTitle(false), fallbackReason(false), "2026", true);
                badgeCache.put(userId, record);
            }
            return record;
        }
    }

    public static MiogramBadgeType getCachedBadgeType(long userId) {
        BadgeRecord record = getBadgeRecord(userId);
        return record != null ? record.badgeType : MiogramBadgeType.ORIGINAL;
    }

    public static boolean isOptInCompleted(Context context) {
        return getPrefs(context).getBoolean(KEY_OPTIN_COMPLETED, false);
    }

    public static boolean isSyncEnabledForAccount(Context context, long userId) {
        return getPrefs(context).getBoolean(KEY_SYNC_ENABLED + userId, false);
    }

    /** Fallback title/reason shown in UI when cloud has no record yet — always trilingual. */
    public static String fallbackTitle(boolean founder) {
        return founder
                ? MiogramLocale.get("Засновник & Архітектор Miogram ໒꒱", "Основатель & Архитектор Miogram ໒꒱", "Miogram Founder & Architect ໒꒱")
                : MiogramLocale.get("Учасник спільноти Miogram", "Участник сообщества Miogram", "Miogram community member");
    }

    public static String fallbackReason(boolean founder) {
        return founder
                ? MiogramLocale.get("Особиста відзнака засновника Miogram", "Личная награда основателя Miogram", "Personal founder badge of Miogram")
                : MiogramLocale.get("Отримано через хмарну синхронізацію спільноти", "Получено через облачную синхронизацию сообщества", "Granted via community cloud sync");
    }

    /** True when a row claims founder status (title/reason/id) without staff verification. */
    private static boolean looksLikeFounderClaim(long userId, String title, String reason) {
        if (userId == MiogramBadgeManager.FOUNDER_USER_ID) return true;
        String t = (title != null ? title : "").toLowerCase(java.util.Locale.ROOT);
        String r = (reason != null ? reason : "").toLowerCase(java.util.Locale.ROOT);
        return t.contains("засновник") || t.contains("основатель") || t.contains("founder")
                || t.contains("архітектор") || t.contains("архитектор") || t.contains("architect")
                || r.contains("засновник") || r.contains("основатель") || r.contains("founder");
    }

    public static void setSyncEnabledForAccount(Context context, long userId, boolean enabled) {
        getPrefs(context).edit()
                .putBoolean(KEY_OPTIN_COMPLETED, true)
                .putBoolean(KEY_SYNC_ENABLED + userId, enabled)
                .apply();

        if (userId != 0) {
            if (enabled) {
                MiogramBadgeType selected = getSelectedBadgeForAccount(context, userId);
                synchronized (badgeCache) {
                    badgeCache.put(userId, new BadgeRecord(userId, selected, fallbackTitle(false), fallbackReason(false), "2026", true));
                }
                syncUserBadgeToCloud(userId, selected.getId(), true, null);
            } else {
                syncUserBadgeToCloud(userId, "original", false, null);
            }
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static MiogramBadgeType getSelectedBadgeForAccount(Context context, long userId) {
        String id = getPrefs(context).getString(KEY_SELECTED_BADGE + userId, "original");
        return MiogramBadgeType.fromId(id);
    }

    public static void setSelectedBadgeForAccount(Context context, long userId, MiogramBadgeType type) {
        if (type == null) type = MiogramBadgeType.ORIGINAL;
        getPrefs(context).edit()
                .putString(KEY_SELECTED_BADGE + userId, type.getId())
                .putBoolean(KEY_SYNC_ENABLED + userId, true)
                .apply();

        if (userId != 0) {
            boolean founder = userId == MiogramBadgeManager.FOUNDER_USER_ID;
            String title = fallbackTitle(founder);
            String reason = fallbackReason(founder);
            String date = "2026";
            synchronized (badgeCache) {
                BadgeRecord existing = badgeCache.get(userId);
                if (existing != null) {
                    if (existing.title != null) title = existing.title;
                    if (existing.obtainedReason != null) reason = existing.obtainedReason;
                    if (existing.obtainedAt != null) date = existing.obtainedAt;
                }
                badgeCache.put(userId, new BadgeRecord(userId, type, title, reason, date, true));
            }
            syncUserBadgeToCloud(userId, type.getId(), true, null);
        }
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public static void fetchBadgesFromCloud(Runnable onComplete) {
        Utilities.globalQueue.postRunnable(() -> {
            for (String table : BADGES_TABLES) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = DEFAULT_SUPABASE_URL + "/rest/v1/" + table + "?select=*&is_active=eq.true";
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Accept", "application/json");

                    int code = connection.getResponseCode();
                    if (code >= 200 && code < 300) {
                        InputStream in = connection.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                        reader.close();

                        String resultJson = sb.toString();
                        parseAndApplyBadgesJson(resultJson);

                        getPrefs(null).edit().putString(KEY_CACHE_JSON, resultJson).apply();

                        AndroidUtilities.runOnUIThread(() -> {
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            if (onComplete != null) onComplete.run();
                        });
                        return;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            if (onComplete != null) {
                AndroidUtilities.runOnUIThread(onComplete);
            }
        });
    }

    private static void parseAndApplyBadgesJson(String jsonStr) {
        try {
            JSONArray arr = new JSONArray(jsonStr);
            synchronized (badgeCache) {
                badgeCache.clear();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    long uid = obj.optLong("user_id");
                    boolean active = obj.optBoolean("is_active", true);
                    String badgeId = obj.optString("badge_id", "original");
                    String title = obj.optString("title", "Miogram Community ໒꒱");
                    String reason = obj.optString("obtained_reason", "Верифікований учасник спільноти Miogram");
                    String date = obj.optString("obtained_at", "01.09.2026");
                    boolean verified = obj.optBoolean("verified", uid == MiogramBadgeManager.FOUNDER_USER_ID);
                    long grantorId = obj.optLong("grantor_id", 0);

                    if (uid != 0 && active) {
                        // Anti-abuse: unverified founder claims render as plain member rows.
                        if (!verified && looksLikeFounderClaim(uid, title, reason)) {
                            title = fallbackTitle(false);
                            reason = fallbackReason(false);
                        }
                        badgeCache.put(uid, new BadgeRecord(uid, badgeId, title, reason, date, true, verified, grantorId));

                        String clientVersion = obj.optString("client_version", "");
                        if (!TextUtils.isEmpty(clientVersion)) {
                            app.miogram.bridge.presence.MiogramCloudPresence p = app.miogram.bridge.presence.MiogramCloudPresence.extractPresence(uid, clientVersion);
                            if (p != null) {
                                app.miogram.bridge.presence.MiogramCloudPresence.putPresence(uid, p);
                            }
                        }
                    }
                }
                if (badgeCache.get(MiogramBadgeManager.FOUNDER_USER_ID) == null) {
                    badgeCache.put(MiogramBadgeManager.FOUNDER_USER_ID, createDefaultFounderRecord());
                }
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    long uid = UserConfig.getInstance(a).getClientUserId();
                    if (uid > 0 && isSyncEnabledForAccount(null, uid)) {
                        if (badgeCache.get(uid) == null) {
                            MiogramBadgeType selected = getSelectedBadgeForAccount(null, uid);
                            badgeCache.put(uid, new BadgeRecord(uid, selected, fallbackTitle(uid == MiogramBadgeManager.FOUNDER_USER_ID), fallbackReason(uid == MiogramBadgeManager.FOUNDER_USER_ID), "2026", true));
                        }
                    }
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static long lastFetchTime = 0;

    /** Reads the server error body (PostgREST explains the 400 here). */
    static String readErrorBody(HttpURLConnection connection) {
        try {
            java.io.InputStream err = connection != null ? connection.getErrorStream() : null;
            if (err == null) return "";
            BufferedReader reader = new BufferedReader(new InputStreamReader(err, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null && sb.length() < 600) sb.append(line);
            reader.close();
            return sb.toString();
        } catch (Throwable ignore) {
            return "";
        }
    }

    public static void checkRefreshBadges() {
        long now = System.currentTimeMillis();
        if (now - lastFetchTime > 60_000L) {
            lastFetchTime = now;
            fetchBadgesFromCloud(null);
        }
    }

    /**
     * Self opt-in/out. Badge identity is founder-only (server RLS + RPC), so
     * this NEVER creates or edits badge rows — it only refreshes presence
     * (client_version) on an existing row, if any. Users without a
     * founder-granted row simply have no cloud row until granted one.
     */
    public static void syncUserBadgeToCloud(long userId, String badgeId, boolean isActive, Runnable onComplete) {
        if (userId <= 0) return;
        Utilities.globalQueue.postRunnable(() -> {
            for (String table : BADGES_TABLES) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = DEFAULT_SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + userId;
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PATCH");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("client_version", "Amegram " + BuildVars.BUILD_VERSION_STRING);

                    byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    FileLog.d("MiogramSupabaseBridge presence patch status (" + table + "): " + code);
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        break;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
            if (onComplete != null) {
                AndroidUtilities.runOnUIThread(onComplete);
            }
        });
    }

    /**
     * Presence heartbeat. PATCHes ONLY client_version (presence payload) —
     * never badge identity: identity writes are founder-only via RPC and
     * RLS rejects them for anon. PATCH on a missing row is a harmless no-op.
     */
    public static void syncPresenceToCloud(long userId, app.miogram.bridge.presence.MiogramCloudPresence presence, Runnable onComplete) {
        if (userId <= 0 || presence == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        app.miogram.bridge.presence.MiogramCloudPresence.putPresence(userId, presence);

        final String encodedClientVersion = app.miogram.bridge.presence.MiogramCloudPresence.encodeClientVersion(presence);

        Utilities.globalQueue.postRunnable(() -> {
            for (String table : BADGES_TABLES) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = DEFAULT_SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + userId;
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PATCH");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("client_version", encodedClientVersion);

                    byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    FileLog.d("MiogramSupabaseBridge syncPresenceToCloud status (" + table + "): " + code);
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        break;
                    }
                } catch (Exception e) {
                    FileLog.e("MiogramSupabaseBridge: syncPresenceToCloud error", e);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            if (onComplete != null) {
                AndroidUtilities.runOnUIThread(onComplete);
            }
        });
    }

    public static void fetchUserPresence(long userId, Utilities.Callback<app.miogram.bridge.presence.MiogramCloudPresence> callback) {
        fetchUserPresence(userId, false, callback);
    }

    public static void fetchUserPresence(long userId, boolean force, Utilities.Callback<app.miogram.bridge.presence.MiogramCloudPresence> callback) {
        if (userId <= 0) {
            if (callback != null) callback.run(null);
            return;
        }

        if (!force) {
            app.miogram.bridge.presence.MiogramCloudPresence cached = app.miogram.bridge.presence.MiogramCloudPresence.getPresence(userId);
            if (cached != null) {
                if (callback != null) callback.run(cached);
                return;
            }
        }

        Utilities.globalQueue.postRunnable(() -> {
            app.miogram.bridge.presence.MiogramCloudPresence presence = null;
            for (String table : BADGES_TABLES) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = DEFAULT_SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + userId + "&select=*";
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Accept", "application/json");

                    int code = connection.getResponseCode();
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();

                        JSONArray arr = new JSONArray(sb.toString());
                        if (arr.length() > 0) {
                            JSONObject obj = arr.getJSONObject(0);
                            String clientVer = obj.optString("client_version", "");
                            presence = app.miogram.bridge.presence.MiogramCloudPresence.extractPresence(userId, clientVer);
                            if (presence != null) {
                                app.miogram.bridge.presence.MiogramCloudPresence.putPresence(userId, presence);
                                long selfUserId = 0;
                                try {
                                    selfUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                                } catch (Throwable ignore) {}
                                if (selfUserId != 0 && selfUserId == userId) {
                                    restoreLocalManagersFromPresence(presence);
                                }
                            }
                        }
                        break;
                    }
                } catch (Throwable t) {
                    FileLog.e("MiogramSupabaseBridge: fetchUserPresence error", t);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }

            final app.miogram.bridge.presence.MiogramCloudPresence res = presence;
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.run(res);
            });
        });
    }

    private static void restoreLocalManagersFromPresence(app.miogram.bridge.presence.MiogramCloudPresence presence) {
        if (presence == null) return;
        try {
            // Steam
            if (!TextUtils.isEmpty(presence.steamId) && !app.miogram.bridge.steam.MiogramSteamManager.getInstance().isLinked()) {
                app.miogram.bridge.steam.MiogramSteamManager.getInstance().setLinkedSteamId(presence.steamId);
            }
            // GitHub
            if (!TextUtils.isEmpty(presence.githubUser) && !app.miogram.bridge.github.MiogramGitHubManager.getInstance().isLinked()) {
                app.miogram.bridge.github.MiogramGitHubManager.getInstance().setLinkedUsername(presence.githubUser);
            }
            // Discord
            if (!TextUtils.isEmpty(presence.discordId) && !app.miogram.bridge.discord.MiogramDiscordManager.getInstance().isLinked()) {
                app.miogram.bridge.discord.MiogramDiscordManager.getInstance().setLinkedUserId(presence.discordId);
            }
            // Spotify
            if (!TextUtils.isEmpty(presence.spotifyUser) && !"live".equals(presence.spotifyUser) && !app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().isLinked()) {
                app.miogram.bridge.spotify.MiogramSpotifyManager.getInstance().setLinkedUsername(presence.spotifyUser);
            }
            // Roblox
            if (!TextUtils.isEmpty(presence.robloxUser) && !app.miogram.bridge.roblox.MiogramRobloxManager.getInstance().isLinked()) {
                app.miogram.bridge.roblox.MiogramRobloxManager.getInstance().setLinkedUsername(presence.robloxUser, null);
            }
            // TikTok
            if (!TextUtils.isEmpty(presence.tiktokUser) && !app.miogram.bridge.ecosystem.AmegramTikTokManager.getInstance().isLinked()) {
                app.miogram.bridge.ecosystem.AmegramTikTokManager.getInstance().restoreFromCloud(
                        presence.tiktokUser, presence.tiktokName, presence.tiktokAvatar,
                        presence.tiktokFollowers, presence.tiktokFollowing, presence.tiktokLikes
                );
            }
        } catch (Throwable t) {
            FileLog.e("MiogramSupabaseBridge: restoreLocalManagers error", t);
        }
    }

    private static long lastSyncErrorDialogTime = 0;

    public static void openBugReportChat(Context context, String issueType, String errorDetails) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                int account = UserConfig.selectedAccount;
                StringBuilder sb = new StringBuilder();
                sb.append("Hello @dkramochka,\n\n");
                sb.append("I am reporting an issue encountered in Miogram:\n\n");
                sb.append("[Miogram Bug Report]\n");
                sb.append("Issue: ").append(issueType != null && !issueType.isEmpty() ? issueType : "Runtime Issue").append("\n");
                sb.append("App Version: Miogram ").append(BuildVars.BUILD_VERSION_STRING).append("\n");
                sb.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
                sb.append("OS: Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
                try {
                    long userId = UserConfig.getInstance(account).getClientUserId();
                    if (userId != 0) {
                        sb.append("User ID: ").append(userId).append("\n");
                    }
                } catch (Throwable ignore) {}
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'", Locale.US);
                    sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                    sb.append("Timestamp: ").append(sdf.format(new Date())).append("\n");
                } catch (Throwable ignore) {}
                sb.append("\nError Log / Details:\n");
                sb.append(errorDetails != null && !errorDetails.trim().isEmpty() ? errorDetails.trim() : "No extra logs provided.");

                final String fullReport = sb.toString();

                // 1. Copy to clipboard
                AndroidUtilities.addToClipboard(fullReport);

                final Context ctx = (context != null)
                        ? context
                        : (LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext);

                try {
                    Toast.makeText(ctx, MiogramLocale.get(
                            "Звіт скопійовано, надсилаємо...",
                            "Отчет скопирован, отправляем...",
                            "Report copied, sending..."
                    ), Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {}

                // 2. Route: channel members -> logs thread (t.me/dkmiogram/8),
                //    everyone else -> creator PM.
                final BaseFragment lastFragment = LaunchActivity.getLastFragment();
                final MessagesController mc = MessagesController.getInstance(account);
                routeBugReport(account, lastFragment, ctx, fullReport,
                        () -> sendBugReportToCreatorPm(account, lastFragment, ctx, fullReport));
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    /** Logs thread root message in @dkmiogram. */
    private static final int BUG_LOG_THREAD_MSG_ID = 8;
    private static final String BUG_LOG_CHANNEL = "dkmiogram";
    private static final String BUG_CREATOR = "dkramochka";

    /**
     * If the user participates in @dkmiogram, posts the report as a reply in
     * the logs thread (message 8). Otherwise (or on any failure) runs pmFallback.
     */
    private static void routeBugReport(int account, BaseFragment lastFragment, Context ctx,
                                       String fullReport, Runnable pmFallback) {
        try {
            final MessagesController mc = MessagesController.getInstance(account);
            mc.getUserNameResolver().resolve(BUG_LOG_CHANNEL, (peerId) -> {
                if (peerId == null || peerId >= 0) {
                    pmFallback.run();
                    return;
                }
                final long channelDialogId = peerId;
                TLRPC.Chat chat = mc.getChat(-channelDialogId);
                if (chat == null || chat.access_hash == 0) {
                    pmFallback.run();
                    return;
                }
                TLRPC.TL_inputChannel inputChannel = new TLRPC.TL_inputChannel();
                inputChannel.channel_id = -channelDialogId;
                inputChannel.access_hash = chat.access_hash;

                // Membership check: only participants may write into the logs thread.
                TLRPC.TL_channels_getParticipant partReq = new TLRPC.TL_channels_getParticipant();
                partReq.channel = inputChannel;
                partReq.participant = new TLRPC.TL_inputPeerSelf();
                ConnectionsManager.getInstance(account).sendRequest(partReq, (partResp, partErr) -> AndroidUtilities.runOnUIThread(() -> {
                    boolean member = false;
                    if (partResp instanceof TLRPC.TL_channels_channelParticipant) {
                        TLRPC.ChannelParticipant p = ((TLRPC.TL_channels_channelParticipant) partResp).participant;
                        member = p != null && !(p instanceof TLRPC.TL_channelParticipantLeft)
                                && !(p instanceof TLRPC.TL_channelParticipantBanned);
                    }
                    if (!member) {
                        pmFallback.run();
                        return;
                    }
                    // Fetch thread root (message 8) to reply inside the logs thread.
                    TLRPC.TL_channels_getMessages msgsReq = new TLRPC.TL_channels_getMessages();
                    msgsReq.channel = inputChannel;
                    msgsReq.id.add(BUG_LOG_THREAD_MSG_ID);
                    ConnectionsManager.getInstance(account).sendRequest(msgsReq, (msgsResp, msgsErr) -> AndroidUtilities.runOnUIThread(() -> {
                        MessageObject replyTo = null;
                        try {
                            if (msgsResp instanceof TLRPC.messages_Messages) {
                                TLRPC.messages_Messages mm = (TLRPC.messages_Messages) msgsResp;
                                mc.putUsers(mm.users, false);
                                mc.putChats(mm.chats, false);
                                if (!mm.messages.isEmpty() && mm.messages.get(0) != null
                                        && !(mm.messages.get(0) instanceof TLRPC.TL_messageEmpty)) {
                                    replyTo = new MessageObject(account, mm.messages.get(0), false, false);
                                }
                            }
                        } catch (Throwable ignore) {}
                        final MessageObject fReplyTo = replyTo;
                        try {
                            SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                                    fullReport, channelDialogId, fReplyTo, fReplyTo, null, true, null, null, null, true, 0, 0, null, false
                            );
                            SendMessagesHelper.getInstance(account).sendMessage(params);
                        } catch (Throwable t) {
                            FileLog.e(t);
                            pmFallback.run();
                            return;
                        }
                        try {
                            Toast.makeText(ctx, MiogramLocale.get(
                                    "Звіт надіслано у гілку логів @dkmiogram",
                                    "Отчет отправлен в ветку логов @dkmiogram",
                                    "Report posted to @dkmiogram logs thread"
                            ), Toast.LENGTH_SHORT).show();
                        } catch (Throwable ignore) {}
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                if (lastFragment != null) {
                                    mc.openByUserName(BUG_LOG_CHANNEL, lastFragment, 1);
                                } else {
                                    Browser.openUrl(ctx, "https://t.me/" + BUG_LOG_CHANNEL);
                                }
                            } catch (Throwable t) {
                                FileLog.e(t);
                            }
                        });
                    }));
                }));
            });
        } catch (Throwable t) {
            FileLog.e(t);
            pmFallback.run();
        }
    }

    /** Original behavior: send the report to the creator's PM. */
    private static void sendBugReportToCreatorPm(int account, BaseFragment lastFragment, Context ctx, String fullReport) {
        AndroidUtilities.runOnUIThread(() -> {
            try {
                final MessagesController mc = MessagesController.getInstance(account);
                mc.getUserNameResolver().resolve(BUG_CREATOR, (peerId) -> {
                    if (peerId != null && peerId > 0) {
                        try {
                            SendMessagesHelper.getInstance(account).sendMessage(
                                    SendMessagesHelper.SendMessageParams.of(fullReport, peerId, null, null, null, true, null, null, null, true, 0, 0, null, false)
                            );
                        } catch (Throwable t) {
                            FileLog.e(t);
                            try {
                                MediaDataController.getInstance(account).saveDraft(peerId, 0, fullReport, null, null, true, 0);
                            } catch (Throwable ignore) {}
                        }
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            if (lastFragment != null) {
                                mc.openByUserName(BUG_CREATOR, lastFragment, 1);
                            } else {
                                Browser.openUrl(ctx, "https://t.me/" + BUG_CREATOR);
                            }
                        } catch (Throwable t) {
                            FileLog.e(t);
                            Browser.openUrl(ctx, "https://t.me/" + BUG_CREATOR);
                        }
                    });
                });
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void showBugReportDialog(Context context, String title, String message, String issueType, String errorDetails) {
        AndroidUtilities.runOnUIThread(() -> {
            final Context ctx = (context != null)
                    ? context
                    : (LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext);
            if (ctx == null) return;
            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setTitle(title != null ? title : MiogramLocale.get("Звіт про помилку", "Отчет об ошибке", "Bug Report"));
                builder.setMessage(message != null ? message : MiogramLocale.get(
                        "Бажаєте надіслати звіт із логами творцю @dkramochka?",
                        "Желаете отправить отчет с логами создателю @dkramochka?",
                        "Would you like to send a bug report with logs to creator @dkramochka?"
                ));
                final String fIssue = issueType;
                final String fDetails = errorDetails;
                builder.setPositiveButton(MiogramLocale.get("Відправити баг", "Отправить баг", "Send Bug"), (d, which) -> {
                    d.dismiss();
                    openBugReportChat(ctx, fIssue, fDetails);
                });
                builder.setNegativeButton(LocaleController.getString(R.string.Cancel), (d, which) -> d.dismiss());
                builder.create().show();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void showSyncErrorDialog(Context context, String errorDetails) {
        AndroidUtilities.runOnUIThread(() -> {
            long now = System.currentTimeMillis();
            if (now - lastSyncErrorDialogTime < 60_000L) {
                return;
            }
            lastSyncErrorDialogTime = now;

            final Context ctx = (context != null)
                    ? context
                    : (LaunchActivity.instance != null ? LaunchActivity.instance : ApplicationLoader.applicationContext);
            if (ctx == null) return;

            try {
                AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
                builder.setTitle(MiogramLocale.get("Критична помилка синхронізації", "Критическая ошибка синхронизации", "Critical Sync Error"));
                builder.setMessage(MiogramLocale.get(
                        "Критична помилка синхронізації. Щоб уникнути проблем, надішліть помилку творцю",
                        "Критическая ошибка синхронизации. Чтобы избежать проблем, отправьте ошибку создателю",
                        "Critical synchronization error. To avoid issues, please send this error to the creator."
                ));
                final String finalError = (errorDetails != null && !errorDetails.trim().isEmpty())
                        ? errorDetails.trim()
                        : "Unknown error occurred during Supabase synchronization.";

                builder.setPositiveButton(MiogramLocale.get("Відправити баг", "Отправить баг", "Send Bug"), (d, which) -> {
                    d.dismiss();
                    openBugReportChat(ctx, "Critical Supabase Sync Error", finalError);
                });
                builder.setNegativeButton(MiogramLocale.get("Не зараз", "Не сейчас", "Not now"), (d, which) -> {
                    d.dismiss();
                });

                AlertDialog dialog = builder.create();
                dialog.setCanceledOnTouchOutside(true);
                dialog.setCancelable(true);
                dialog.show();
            } catch (Throwable t) {
                FileLog.e(t);
            }
        });
    }

    public static void reportCurrentUserPresence() {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                    if (UserConfig.getInstance(a).isClientActivated()) {
                        long uid = UserConfig.getInstance(a).getClientUserId();
                        if (uid != 0) {
                            reportUserPresence(uid);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
    }

    public static void reportUserPresence(long userId) {
        if (userId == 0 || !isTelemetryEnabled()) return;
        Utilities.globalQueue.postRunnable(() -> {
            for (String table : BADGES_TABLES) {
                HttpURLConnection connection = null;
                try {
                    String endpoint = DEFAULT_SUPABASE_URL + "/rest/v1/" + table + "?user_id=eq." + userId;
                    URL url = new URL(endpoint);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("PATCH");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("client_version", "Amegram " + BuildVars.BUILD_VERSION_STRING);

                    byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    FileLog.d("MiogramSupabaseBridge presence reported (" + table + ") for user " + userId + ": " + code);
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        break;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }
        });
    }

    /**
     * Founder grant: goes through the miogram_grant_badge RPC, which checks
     * GRANT_SECRET server-side. Direct table writes are locked down by RLS,
     * so old clients (and curl) fail closed. The row carries grantor_id so
     * clients can render "Granted by Founder".
     */
    public static void grantBadgeToUser(long targetUserId, String badgeId, String title, String reason, Runnable onComplete) {
        if (targetUserId <= 0) return;
        final String fBadge = badgeId != null ? badgeId : "original";
        final String fTitle = title != null ? title : fallbackTitle(false);
        final String fReason = reason != null ? reason : fallbackReason(false);
        long granter = 0;
        try {
            granter = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignored) {}
        final long fGranter = granter;
        synchronized (badgeCache) {
            badgeCache.put(targetUserId, new BadgeRecord(targetUserId,
                    MiogramBadgeType.fromId(fBadge), fTitle, fReason,
                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()), true, true, fGranter));
        }
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR);
        });
        Utilities.globalQueue.postRunnable(() -> {
            for (String rpc : RPC_GRANT) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(DEFAULT_SUPABASE_URL + "/rest/v1/rpc/" + rpc);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("p_secret", GRANT_SECRET);
                    body.put("p_target", targetUserId);
                    body.put("p_badge_id", fBadge);
                    body.put("p_title", fTitle);
                    body.put("p_reason", fReason);

                    byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    FileLog.d("MiogramSupabaseBridge grant badge status (" + rpc + "): " + code);
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        synchronized (badgeCache) {
                            badgeCache.put(targetUserId, new BadgeRecord(targetUserId,
                                    fBadge, fTitle, fReason,
                                    new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()), true, true, fGranter));
                        }
                        break;
                    } else {
                        showSyncErrorDialog(null, "Grant badge HTTP " + code);
                        break;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    String msg = e.getMessage();
                    showSyncErrorDialog(null, (msg != null && !msg.isEmpty()) ? (e.getClass().getSimpleName() + ": " + msg) : e.toString());
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            if (onComplete != null) {
                AndroidUtilities.runOnUIThread(onComplete);
            }
        });
    }

    public static void revokeBadge(long targetUserId, Runnable onComplete) {
        if (targetUserId <= 0) return;
        synchronized (badgeCache) {
            badgeCache.remove(targetUserId);
        }
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_AVATAR);
        });

        Utilities.globalQueue.postRunnable(() -> {
            for (String rpc : RPC_REVOKE) {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(DEFAULT_SUPABASE_URL + "/rest/v1/rpc/" + rpc);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");

                    JSONObject body = new JSONObject();
                    body.put("p_secret", GRANT_SECRET);
                    body.put("p_target", targetUserId);

                    byte[] outBytes = body.toString().getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    FileLog.d("MiogramSupabaseBridge revoke badge status (" + rpc + "): " + code);
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        break;
                    } else {
                        showSyncErrorDialog(null, "Revoke badge HTTP " + code);
                        break;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                    String msg = e.getMessage();
                    showSyncErrorDialog(null, (msg != null && !msg.isEmpty()) ? (e.getClass().getSimpleName() + ": " + msg) : e.toString());
                    break;
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
            if (onComplete != null) {
                AndroidUtilities.runOnUIThread(onComplete);
            }
        });
    }

    /** Public community counters for the website/app (via SECURITY DEFINER RPC, no table scan). */
    public static void getCommunityStats(Utilities.Callback2<Long, Long> callback) {
        Utilities.globalQueue.postRunnable(() -> {
            for (String rpc : RPC_STATS) {
                HttpURLConnection connection = null;
                long users = -1;
                long badges = -1;
                try {
                    URL url = new URL(DEFAULT_SUPABASE_URL + "/rest/v1/rpc/" + rpc);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("POST");
                    connection.setDoOutput(true);
                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);
                    connection.setRequestProperty("apikey", DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Authorization", "Bearer " + DEFAULT_ANON_KEY);
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestProperty("Accept", "application/json");
                    byte[] outBytes = "{}".getBytes(StandardCharsets.UTF_8);
                    connection.setFixedLengthStreamingMode(outBytes.length);
                    OutputStream os = connection.getOutputStream();
                    os.write(outBytes);
                    os.flush();
                    os.close();

                    int code = connection.getResponseCode();
                    if (code == 404) {
                        continue;
                    }
                    if (code >= 200 && code < 300) {
                        InputStream in = connection.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        reader.close();
                        JSONObject obj = new JSONObject(sb.toString());
                        users = obj.optLong("users_count", -1);
                        badges = obj.optLong("badges_count", -1);
                        final long fUsers = users;
                        final long fBadges = badges;
                        AndroidUtilities.runOnUIThread(() -> callback.run(fUsers, fBadges));
                        return;
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            }
        });
    }
}
