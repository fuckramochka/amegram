package app.miogram.bridge.ecosystem;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Locale;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;
import app.miogram.bridge.presence.MiogramCloudPresence;

/**
 * Account Manager & Profile Synchronizer for TikTok MI in Amegram.
 * Manages linked user profile (username, nickname, avatar, followers, following, likes)
 * and synchronizes live presence to the cloud so other users see rich TikTok profile cards.
 */
public class AmegramTikTokManager {

    private static final String PREFS_NAME = "amegram_tiktok_prefs";
    private static final String KEY_USERNAME = "tiktok_username";
    private static final String KEY_NICKNAME = "tiktok_nickname";
    private static final String KEY_AVATAR = "tiktok_avatar";
    private static final String KEY_FOLLOWERS = "tiktok_followers";
    private static final String KEY_FOLLOWING = "tiktok_following";
    private static final String KEY_LIKES = "tiktok_likes";
    private static final String KEY_BIO = "tiktok_bio";
    private static final String KEY_LAST_UPDATED = "tiktok_last_updated";

    private static volatile AmegramTikTokManager instance;

    public static AmegramTikTokManager getInstance() {
        if (instance == null) {
            synchronized (AmegramTikTokManager.class) {
                if (instance == null) {
                    instance = new AmegramTikTokManager();
                }
            }
        }
        return instance;
    }

    public static class TikTokUser {
        public String username = "";
        public String nickname = "";
        public String avatarUrl = "";
        public long followersCount = 0;
        public long followingCount = 0;
        public long likesCount = 0;
        public String bio = "";
        public long lastUpdated = 0;

        public String getDisplayName() {
            if (!TextUtils.isEmpty(nickname)) return nickname;
            if (!TextUtils.isEmpty(username)) return "@" + username;
            return "TikTok User";
        }

        public boolean isLinked() {
            return !TextUtils.isEmpty(username);
        }
    }

    public interface UserCallback {
        void onUserLoaded(TikTokUser user);
    }

    private SharedPreferences getPrefs() {
        Context ctx = ApplicationLoader.applicationContext;
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isLinked() {
        return !TextUtils.isEmpty(getLinkedUsername());
    }

    public boolean isActiveFor(long userId) {
        if (userId <= 0) return isLinked();
        try {
            long selfId = org.telegram.messenger.UserConfig.getInstance(org.telegram.messenger.UserConfig.selectedAccount).getClientUserId();
            return selfId == userId && isLinked();
        } catch (Throwable ignore) {
            return isLinked();
        }
    }

    public String getLinkedUsername() {
        return getPrefs().getString(KEY_USERNAME, "").trim();
    }

    public String getNickname() {
        return getPrefs().getString(KEY_NICKNAME, "").trim();
    }

    public String getAvatarUrl() {
        return getPrefs().getString(KEY_AVATAR, "").trim();
    }

    public long getFollowersCount() {
        return getPrefs().getLong(KEY_FOLLOWERS, 0);
    }

    public long getFollowingCount() {
        return getPrefs().getLong(KEY_FOLLOWING, 0);
    }

    public long getLikesCount() {
        return getPrefs().getLong(KEY_LIKES, 0);
    }

    public String getBio() {
        return getPrefs().getString(KEY_BIO, "").trim();
    }

    public TikTokUser getSelfUser() {
        if (!isLinked()) return null;
        TikTokUser u = new TikTokUser();
        u.username = getLinkedUsername();
        u.nickname = getNickname();
        u.avatarUrl = getAvatarUrl();
        u.followersCount = getFollowersCount();
        u.followingCount = getFollowingCount();
        u.likesCount = getLikesCount();
        u.bio = getBio();
        u.lastUpdated = getPrefs().getLong(KEY_LAST_UPDATED, 0);
        return u;
    }

    public void setLinkedProfile(String username, String nickname, String avatarUrl,
                                 long followers, long following, long likes, String bio) {
        String cleanUser = cleanUsername(username);
        getPrefs().edit()
                .putString(KEY_USERNAME, cleanUser)
                .putString(KEY_NICKNAME, nickname != null ? nickname.trim() : "")
                .putString(KEY_AVATAR, avatarUrl != null ? avatarUrl.trim() : "")
                .putLong(KEY_FOLLOWERS, followers)
                .putLong(KEY_FOLLOWING, following)
                .putLong(KEY_LIKES, likes)
                .putString(KEY_BIO, bio != null ? bio.trim() : "")
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply();

        MiogramCloudPresence.syncSelfToCloud(0);
    }

    public void restoreFromCloud(String username, String nickname, String avatar,
                                 long followers, long following, long likes) {
        if (TextUtils.isEmpty(username) || isLinked()) {
            return;
        }
        getPrefs().edit()
                .putString(KEY_USERNAME, cleanUsername(username))
                .putString(KEY_NICKNAME, nickname != null ? nickname : "")
                .putString(KEY_AVATAR, avatar != null ? avatar : "")
                .putLong(KEY_FOLLOWERS, followers)
                .putLong(KEY_FOLLOWING, following)
                .putLong(KEY_LIKES, likes)
                .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
                .apply();
    }

    public void unlink() {
        getPrefs().edit().clear().apply();
        MiogramCloudPresence.syncSelfToCloud(0);
    }

    // ------------------------------------------------------------- Automatic binding

    private static final String KEY_LAST_AUTOBIND = "tiktok_last_autobind";
    private static final long AUTOBIND_INTERVAL_MS = 12 * 60 * 60 * 1000L; // 12 hours
    private static final long STATS_STALE_MS = 24 * 60 * 60 * 1000L; // refresh stats at most once a day
    private static volatile boolean autoBindRunning = false;

    private static void runDone(Runnable done) {
        if (done == null) return;
        try {
            AndroidUtilities.runOnUIThread(done);
        } catch (Throwable ignore) {
            try { done.run(); } catch (Throwable ignore2) {}
        }
    }

    /**
     * Silent self-binding: tries cloud snapshot -> TikTok MI IPC -> watched-video author,
     * then pulls full public stats. Never shows UI, never overwrites an existing link
     * except to refresh its stats. Throttled to once per {@link #AUTOBIND_INTERVAL_MS}.
     */
    public void tryAutoBind(Runnable done) {
        try {
            long last = getPrefs().getLong(KEY_LAST_AUTOBIND, 0L);
            if (System.currentTimeMillis() - last < AUTOBIND_INTERVAL_MS) {
                if (isLinked()) refreshSelf(false, u -> runDone(done));
                else runDone(done);
                return;
            }
        } catch (Throwable ignore) {}
        if (autoBindRunning) { runDone(done); return; }
        autoBindRunning = true;

        if (isLinked()) {
            stampAutobind();
            autoBindRunning = false;
            refreshSelf(false, u -> runDone(done));
            return;
        }

        long selfId = 0;
        try {
            selfId = org.telegram.messenger.UserConfig.getInstance(
                    org.telegram.messenger.UserConfig.selectedAccount).getClientUserId();
        } catch (Throwable ignore) {}
        final long fSelfId = selfId;

        // 1. Cloud snapshot (another device may have linked already) — restore happens inside.
        if (fSelfId != 0) {
            try {
                app.miogram.bridge.badge.MiogramSupabaseBridge.fetchUserPresence(fSelfId, presence -> {
                    if (isLinked()) {
                        stampAutobind();
                        autoBindRunning = false;
                        refreshSelf(false, u -> runDone(done));
                    } else {
                        bindFromLocalSources(done);
                    }
                });
                return;
            } catch (Throwable t) {
                FileLog.e("AmegramTikTokManager: cloud autobind error", t);
            }
        }
        bindFromLocalSources(done);
    }

    private void stampAutobind() {
        try {
            getPrefs().edit().putLong(KEY_LAST_AUTOBIND, System.currentTimeMillis()).apply();
        } catch (Throwable ignore) {}
    }

    /** Steps 2-3 of auto-bind: TikTok MI app IPC, then watched-video author fallback. */
    private void bindFromLocalSources(Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            boolean bound = false;
            // 2. Ask the installed TikTok MI app who is logged in (forward-compatible IPC).
            try {
                Context ctx = ApplicationLoader.applicationContext;
                android.os.Bundle acc = AmegramTikTokBridge.queryTikTokMiAccount(ctx);
                if (acc != null) {
                    String u = cleanUsername(acc.getString("username", ""));
                    if (!TextUtils.isEmpty(u)) {
                        setLinkedProfile(u,
                                acc.getString("nickname", ""),
                                acc.getString("avatar", ""),
                                acc.getLong("followers", 0),
                                acc.getLong("following", 0),
                                acc.getLong("likes", 0),
                                acc.getString("bio", ""));
                        bound = true;
                    }
                }
            } catch (Throwable t) {
                FileLog.e("AmegramTikTokManager: MI IPC autobind error", t);
            }
            // 3. Fallback: username parsed from the last watched TikTok URL.
            if (!bound) {
                try {
                    AmegramTikTokBridge.WatchingVideo w = AmegramTikTokBridge.getCurrentlyWatching();
                    if (w != null && w.isRecent() && !TextUtils.isEmpty(w.url)) {
                        java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("/@([a-zA-Z0-9_.-]+)").matcher(w.url);
                        if (m.find()) {
                            String u = cleanUsername(m.group(1));
                            if (!TextUtils.isEmpty(u)) {
                                setLinkedProfile(u, "", "", 0, 0, 0, "");
                                bound = true;
                            }
                        }
                    }
                } catch (Throwable t) {
                    FileLog.e("AmegramTikTokManager: watching autobind error", t);
                }
            }
            stampAutobind();
            autoBindRunning = false;
            if (bound) {
                refreshSelf(true, u -> runDone(done));
            } else {
                runDone(done);
            }
        });
    }

    /**
     * Pulls full public stats for the linked username (tikwm user/info, oEmbed fallback)
     * and persists them + pushes to cloud. Skips network when stats are fresh
     * unless {@code force} is true.
     */
    public void refreshSelf(boolean force, UserCallback callback) {
        final String username = getLinkedUsername();
        if (TextUtils.isEmpty(username)) {
            if (callback != null) callback.onUserLoaded(null);
            return;
        }
        try {
            long lastUpdated = getPrefs().getLong(KEY_LAST_UPDATED, 0L);
            if (!force && System.currentTimeMillis() - lastUpdated < STATS_STALE_MS) {
                TikTokUser cached = getSelfUser();
                if (callback != null) {
                    final TikTokUser fCached = cached;
                    AndroidUtilities.runOnUIThread(() -> callback.onUserLoaded(fCached));
                }
                return;
            }
        } catch (Throwable ignore) {}

        Utilities.globalQueue.postRunnable(() -> {
            TikTokUser fresh = fetchFullProfile(username);
            if (fresh != null) {
                TikTokUser prev = getSelfUser();
                String nickname = !TextUtils.isEmpty(fresh.nickname) ? fresh.nickname
                        : (prev != null ? prev.nickname : "");
                String avatar = !TextUtils.isEmpty(fresh.avatarUrl) ? fresh.avatarUrl
                        : (prev != null ? prev.avatarUrl : "");
                String bio = !TextUtils.isEmpty(fresh.bio) ? fresh.bio
                        : (prev != null ? prev.bio : "");
                long followers = fresh.followersCount > 0 ? fresh.followersCount
                        : (prev != null ? prev.followersCount : 0);
                long following = fresh.followingCount > 0 ? fresh.followingCount
                        : (prev != null ? prev.followingCount : 0);
                long likes = fresh.likesCount > 0 ? fresh.likesCount
                        : (prev != null ? prev.likesCount : 0);
                setLinkedProfile(username, nickname, avatar, followers, following, likes, bio);
            }
            final TikTokUser result = getSelfUser();
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onUserLoaded(result);
            });
        });
    }

    /** Full public profile via tikwm user/info, with oEmbed nickname fallback. Never throws. */
    private TikTokUser fetchFullProfile(String username) {
        final String clean = cleanUsername(username);
        if (TextUtils.isEmpty(clean)) return null;
        TikTokUser user = new TikTokUser();
        user.username = clean;
        boolean gotStats = false;
        try {
            String apiUrl = "https://www.tikwm.com/api/user/info?unique_id=" + URLEncoder.encode(clean, "UTF-8");
            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();
                JSONObject root = new JSONObject(sb.toString());
                JSONObject data = root.optJSONObject("data");
                JSONObject u = data != null ? data.optJSONObject("user") : null;
                if (u != null) {
                    user.nickname = u.optString("nickname", "");
                    user.avatarUrl = u.optString("avatarLarger", u.optString("avatarMedium",
                            u.optString("avatarThumb", "")));
                    user.bio = u.optString("signature", "");
                    user.followersCount = u.optLong("followerCount", u.optLong("follower_count", 0));
                    user.followingCount = u.optLong("followingCount", u.optLong("following_count", 0));
                    user.likesCount = u.optLong("heartCount", u.optLong("heart_count",
                            u.optLong("totalFavorited", 0)));
                    gotStats = user.followersCount > 0 || user.likesCount > 0
                            || !TextUtils.isEmpty(user.nickname);
                }
            }
        } catch (Throwable t) {
            FileLog.e("AmegramTikTokManager: tikwm stats error", t);
        }
        if (!gotStats) {
            // Fallback: official oEmbed gives at least the verified display name.
            final TikTokUser[] holder = new TikTokUser[1];
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            resolvePublicProfile(clean, u -> { holder[0] = u; latch.countDown(); });
            try { latch.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Throwable ignore) {}
            if (holder[0] != null && !TextUtils.isEmpty(holder[0].nickname)) {
                user.nickname = holder[0].nickname;
                return user;
            }
            if (!gotStats && TextUtils.isEmpty(user.nickname)) return null;
        }
        return user;
    }

    public static String cleanUsername(String username) {
        if (TextUtils.isEmpty(username)) return "";
        String clean = username.trim();
        if (clean.startsWith("@")) clean = clean.substring(1);
        if (clean.contains("tiktok.com/@")) {
            int idx = clean.indexOf("tiktok.com/@");
            clean = clean.substring(idx + "tiktok.com/@".length());
            int slash = clean.indexOf('/');
            if (slash != -1) clean = clean.substring(0, slash);
            int q = clean.indexOf('?');
            if (q != -1) clean = clean.substring(0, q);
        }
        return clean.trim();
    }

    public static String formatCount(long count) {
        if (count < 1000) return String.valueOf(count);
        if (count < 1_000_000) {
            double v = count / 1000.0;
            return new DecimalFormat("#.#K").format(v);
        }
        if (count < 1_000_000_000) {
            double v = count / 1_000_000.0;
            return new DecimalFormat("#.#M").format(v);
        }
        double v = count / 1_000_000_000.0;
        return new DecimalFormat("#.#B").format(v);
    }

    public void resolvePublicProfile(String username, UserCallback callback) {
        final String clean = cleanUsername(username);
        if (TextUtils.isEmpty(clean)) {
            if (callback != null) callback.onUserLoaded(null);
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            TikTokUser user = new TikTokUser();
            user.username = clean;
            try {
                // Official TikTok oEmbed endpoint gives verified display name
                String oembedUrl = "https://www.tiktok.com/oembed?url=" + URLEncoder.encode("https://www.tiktok.com/@" + clean, "UTF-8");
                HttpURLConnection conn = (HttpURLConnection) new URL(oembedUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json = new JSONObject(sb.toString());
                    user.nickname = json.optString("author_name", clean);
                }
            } catch (Throwable t) {
                FileLog.e("AmegramTikTokManager: resolve error", t);
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onUserLoaded(user);
            });
        });
    }

    /**
     * Shows polished dialog to connect or edit TikTok MI account and stats.
     */
    public void showLinkDialog(Context context, Runnable onComplete) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Прив'язка акаунта TikTok MI", "Привязка аккаунта TikTok MI", "Link TikTok MI Account"));

        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(12));

        TextView hint = new TextView(context);
        hint.setText(MiogramLocale.get(
                "Введіть ваш юзернейм або посилання на профіль TikTok. Інші користувачі Amegram бачитимуть ваш статус, підписників та вподобання у вашому профілі.",
                "Введите ваш юзернейм или ссылку на профиль TikTok. Другие пользователи Amegram будут видеть ваш статус, подписчиков и лайки в вашем профиле.",
                "Enter your TikTok username or profile link. Other Amegram users will see your profile, followers, and likes in your live presence card."
        ));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(0xAAFFFFFF);
        layout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        final EditText etUsername = createInput(context, "@username / tiktok.com/@...", getLinkedUsername());
        layout.addView(createLabel(context, MiogramLocale.get("Юзернейм TikTok", "Юзернейм TikTok", "TikTok Username")));
        layout.addView(etUsername, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final EditText etNickname = createInput(context, MiogramLocale.get("Ім'я профілю (напр. Amegram Creator)", "Имя профиля", "Display Nickname"), getNickname());
        layout.addView(createLabel(context, MiogramLocale.get("Відображуване ім'я (опціонально)", "Отображаемое имя (опционально)", "Display Name (optional)")));
        layout.addView(etNickname, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final EditText etFollowers = createInput(context, "0", getFollowersCount() > 0 ? String.valueOf(getFollowersCount()) : "");
        etFollowers.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(createLabel(context, MiogramLocale.get("Кількість підписників", "Количество подписчиков", "Followers count")));
        layout.addView(etFollowers, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final EditText etLikes = createInput(context, "0", getLikesCount() > 0 ? String.valueOf(getLikesCount()) : "");
        etLikes.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(createLabel(context, MiogramLocale.get("Всього лайків", "Всего лайков", "Total likes count")));
        layout.addView(etLikes, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        final EditText etAvatar = createInput(context, "https://...", getAvatarUrl());
        layout.addView(createLabel(context, MiogramLocale.get("Посилання на аватар (опціонально)", "Ссылка на аватар (опционально)", "Avatar URL (optional)")));
        layout.addView(etAvatar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        scroll.addView(layout);
        builder.setView(scroll);

        builder.setPositiveButton(MiogramLocale.get("Зберегти", "Сохранить", "Save"), (dialog, which) -> {
            String u = cleanUsername(etUsername.getText().toString());
            if (TextUtils.isEmpty(u)) {
                Toast.makeText(context, MiogramLocale.get("Вкажіть юзернейм TikTok", "Укажите юзернейм TikTok", "Enter TikTok username"), Toast.LENGTH_SHORT).show();
                return;
            }

            String nick = etNickname.getText().toString().trim();
            long followers = 0;
            try {
                String fStr = etFollowers.getText().toString().trim();
                if (!TextUtils.isEmpty(fStr)) followers = Long.parseLong(fStr);
            } catch (Throwable ignore) {}

            long likes = 0;
            try {
                String lStr = etLikes.getText().toString().trim();
                if (!TextUtils.isEmpty(lStr)) likes = Long.parseLong(lStr);
            } catch (Throwable ignore) {}

            String avatar = etAvatar.getText().toString().trim();

            setLinkedProfile(u, nick, avatar, followers, 0, likes, "");
            // Amegram: auto-fetch full public stats (nickname/avatar/followers/likes)
            // so the user doesn't have to type numbers by hand.
            refreshSelf(true, null);
            MiogramHaptic.success();
            Toast.makeText(context, MiogramLocale.get("Акаунт TikTok MI підключено!", "Аккаунт TikTok MI подключен!", "TikTok MI account connected!"), Toast.LENGTH_SHORT).show();

            if (onComplete != null) {
                onComplete.run();
            }
        });

        builder.setNegativeButton(MiogramLocale.get("Скасувати", "Отмена", "Cancel"), null);

        builder.show();
    }

    private TextView createLabel(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tv.setTextColor(0xFF00F2FE);
        tv.setTypeface(AndroidUtilities.bold());
        return tv;
    }

    private EditText createInput(Context context, String hint, String initialText) {
        EditText et = new EditText(context);
        et.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        et.setTextColor(0xFFFFFFFF);
        et.setHint(hint);
        et.setHintTextColor(0x55FFFFFF);
        if (!TextUtils.isEmpty(initialText)) {
            et.setText(initialText);
            et.setSelection(initialText.length());
        }

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x18FFFFFF);
        bg.setCornerRadius(AndroidUtilities.dp(10));
        bg.setStroke(AndroidUtilities.dp(1), 0x28FFFFFF);
        et.setBackground(bg);
        et.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        return et;
    }
}
