package app.miogram.bridge.updater;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.ui.MiogramUpdateBottomSheet;

/**
 * Updater service for Miogram.
 * Automatically checks GitHub releases for updates and prompts the user.
 */
public class MiogramUpdater {

    private static final String GITHUB_API_LATEST = "https://api.github.com/repos/fuckramochka/miogram/releases/latest";
    private static final String GITHUB_API_RELEASES = "https://api.github.com/repos/fuckramochka/miogram/releases?per_page=10";
    private static final String PREFS_NAME = "miogram_updater_prefs";
    private static final String KEY_LAST_SEEN_TAG = "last_seen_tag";
    private static final String KEY_UPDATE_CHANNEL = "update_channel"; // "beta" (default) | "stable"
    private static final String KEY_CHANNEL_CHOICE_VERSION = "channel_choice_version";
    private static final String KEY_PROMO_VERSION = "channel_promo_version";
    // Once-ever flags: user asked once -> never ask again after updates.
    private static final String KEY_CHANNEL_CHOSEN_ONCE = "channel_chosen_once";
    private static final String KEY_PROMO_DONE_ONCE = "promo_done_once";
    public static final String CHANNEL_BETA = "beta";
    public static final String CHANNEL_STABLE = "stable";

    public static String getUpdateChannel() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return CHANNEL_BETA;
            return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_UPDATE_CHANNEL, CHANNEL_BETA);
        } catch (Throwable ignore) {
            return CHANNEL_BETA;
        }
    }

    public static void setUpdateChannel(String channel) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx == null) return;
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_UPDATE_CHANNEL, CHANNEL_STABLE.equals(channel) ? CHANNEL_STABLE : CHANNEL_BETA).apply();
        } catch (Throwable ignore) {}
    }

    private static final String KEY_LAST_CHECK_TIME = "last_check_timestamp";
    private static final long CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L; // 24 hours cooldown
    private static volatile boolean autoUpdateStarted = false;

    /**
     * Safe, battery-friendly launch check (at most once every 24 hours).
     */
    public static synchronized void initAutoUpdate(Context context) {
        if (autoUpdateStarted) return;
        autoUpdateStarted = true;

        Utilities.globalQueue.postRunnable(() -> {
            try {
                Context ctx = ApplicationLoader.applicationContext != null ? ApplicationLoader.applicationContext : context;
                if (ctx == null) return;
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                long lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L);
                long now = System.currentTimeMillis();
                if (now - lastCheck < CHECK_INTERVAL_MS) {
                    return; // Skip if checked within the last 24 hours
                }
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply();
                performBackgroundCheck();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 10000L); // Delayed by 10s so it doesn't block app launch
    }

    /**
     * Checks for updates immediately upon entry/unlock.
     * Silent if on the latest version; presents update bottom sheet if a newer version is found.
     */
    public static void checkOnEntry(Context context) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                performBackgroundCheck();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 1500L); // 1.5s post-unlock delay so it doesn't stutter unlock animations
        Utilities.globalQueue.postRunnable(() -> {
            try {
                maybeShowPostUpdateNotices();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }, 4000L);
    }

    /**
     * Once-per-version notices: mandatory update-channel chooser (beta/stable
     * + companion) and the community channel promo.
     */
    public static void maybeShowPostUpdateNotices() {
        LaunchActivity act = LaunchActivity.instance;
        if (act == null || act.isFinishing()) return;
        BaseFragment fragment = act.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) return;
        String ver = getCurrentAppVersion();
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        // Migrate old per-version keys: any stored value means the user already answered once.
        boolean chosenOnce = prefs.getBoolean(KEY_CHANNEL_CHOSEN_ONCE, false);
        if (!chosenOnce && !prefs.getString(KEY_CHANNEL_CHOICE_VERSION, "").isEmpty()) {
            chosenOnce = true;
            prefs.edit().putBoolean(KEY_CHANNEL_CHOSEN_ONCE, true).apply();
        }
        boolean promoDone = prefs.getBoolean(KEY_PROMO_DONE_ONCE, false);
        if (!promoDone && !prefs.getString(KEY_PROMO_VERSION, "").isEmpty()) {
            promoDone = true;
            prefs.edit().putBoolean(KEY_PROMO_DONE_ONCE, true).apply();
        }
        boolean needChoice = !chosenOnce;
        // Don't promo the community channel if the user is already in it.
        boolean needPromo = !promoDone && !isAlreadyInCommunityChannel();
        if (!needChoice && !needPromo) return;
        new Handler(Looper.getMainLooper()).post(() -> {
            LaunchActivity currentAct = LaunchActivity.instance;
            if (currentAct == null || currentAct.isFinishing()) return;
            BaseFragment currentFrag = currentAct.getSafeLastFragment();
            if (currentFrag == null || currentFrag.getParentActivity() == null) return;
            if (needChoice) {
                showChannelChooser(currentFrag, () -> {
                    if (needPromo) showChannelPromo(currentFrag);
                });
            } else {
                showChannelPromo(currentFrag);
            }
        });
    }

    /** True if @dkmiogram is already resolvable from the local cache (user already joined). */
    private static boolean isAlreadyInCommunityChannel() {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                try {
                    MessagesController mc = MessagesController.getInstance(a);
                    if (mc == null) continue;
                    if (mc.getUserOrChat("dkmiogram") != null) return true;
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}
        return false;
    }

    /** Mandatory, non-dismissible: pick update channel + companion. Beta↔Ame, stable↔KAngel by default. */
    private static void showChannelChooser(BaseFragment fragment, Runnable onDone) {
        Context context = fragment.getParentActivity();
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Як оновлювати Miogram?", "Как обновлять Miogram?", "How should Miogram update?"));

        android.widget.LinearLayout root = new android.widget.LinearLayout(context);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(20);
        root.setPadding(pad, AndroidUtilities.dp(8), pad, AndroidUtilities.dp(4));

        android.widget.TextView info = new android.widget.TextView(context);
        info.setText(MiogramLocale.get(
                "БЕТА (за замовчуванням): кожне оновлення одразу. Плюси — нові фічі першим, мінуси — можливі баги.\n\nСТАБІЛЬНА: тільки перевірені релізи. Плюси — спокій, мінуси — фічі приходять пізніше.",
                "БЕТА (по умолчанию): каждое обновление сразу. Плюсы — новые фичи первым, минусы — возможны баги.\n\nСТАБИЛЬНАЯ: только проверенные релизы. Плюсы — спокойствие, минусы — фичи приходят позже.",
                "BETA (default): every update immediately. Pros — features first, cons — possible bugs.\n\nSTABLE: only vetted releases. Pros — peace of mind, cons — features arrive later."));
        info.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        root.addView(info, new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT));

        final String[] channel = new String[]{CHANNEL_BETA};
        final String[] companion = new String[]{app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_AME};
        final android.widget.TextView[] channelViews = new android.widget.TextView[2];
        final android.widget.TextView[] companionViews = new android.widget.TextView[2];

        String[] channelNames = new String[]{
                MiogramLocale.get("Бета (все оновлення)", "Бета (все обновления)", "Beta (every update)"),
                MiogramLocale.get("Стабільна (рідко)", "Стабильная (редко)", "Stable (rarely)")};
        String[] channelVals = new String[]{CHANNEL_BETA, CHANNEL_STABLE};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            android.widget.TextView row = new android.widget.TextView(context);
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            root.addView(row);
            channelViews[i] = row;
            row.setOnClickListener(v -> {
                channel[0] = channelVals[idx];
                // Suggested pair: beta↔Ame, stable↔KAngel (still changeable below).
                companion[0] = CHANNEL_BETA.equals(channel[0])
                        ? app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_AME
                        : app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_KANGEL;
                paintChooserRows(channelViews, channel[0], channelVals, companionViews, companion[0],
                        new String[]{app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_AME,
                                app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_KANGEL});
            });
        }

        android.widget.TextView compLabel = new android.widget.TextView(context);
        compLabel.setText(MiogramLocale.get("Супутниця:", "Спутница:", "Companion:"));
        compLabel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 13);
        compLabel.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        android.widget.LinearLayout.LayoutParams lpLabel = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
        lpLabel.topMargin = AndroidUtilities.dp(10);
        root.addView(compLabel, lpLabel);

        String[] compNames = new String[]{"Ame-chan", "KAngel"};
        String[] compVals = new String[]{
                app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_AME,
                app.miogram.bridge.ai.companion.MiogramCompanionPrefs.COMPANION_KANGEL};
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            android.widget.TextView row = new android.widget.TextView(context);
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
            row.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
            root.addView(row);
            companionViews[i] = row;
            row.setOnClickListener(v -> {
                companion[0] = compVals[idx];
                paintChooserRows(channelViews, channel[0], channelVals, companionViews, companion[0], compVals);
            });
        }
        for (int i = 0; i < 2; i++) channelViews[i].setText(channelNames[i]);
        for (int i = 0; i < 2; i++) companionViews[i].setText(compNames[i]);
        paintChooserRows(channelViews, channel[0], channelVals, companionViews, companion[0], compVals);

        builder.setView(root);
        builder.setPositiveButton(MiogramLocale.get("Зберегти", "Сохранить", "Save"), (d, which) -> {
            setUpdateChannel(channel[0]);
            try {
                app.miogram.bridge.ai.companion.MiogramCompanionPrefs.setActiveCompanion(companion[0]);
                app.miogram.bridge.ai.companion.MiogramCompanionPrefs.setOnboardingCompleted(true);
            } catch (Throwable ignore) {}
            try {
                ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit().putString(KEY_CHANNEL_CHOICE_VERSION, getCurrentAppVersion())
                        .putBoolean(KEY_CHANNEL_CHOSEN_ONCE, true).apply();
            } catch (Throwable ignore) {}
            d.dismiss();
            if (onDone != null) onDone.run();
        });
        org.telegram.ui.ActionBar.AlertDialog dialog = builder.create();
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        try {
            dialog.show();
        } catch (Throwable ignore) {}
    }

    private static void paintChooserRows(android.widget.TextView[] channelViews, String channel, String[] channelVals,
                                         android.widget.TextView[] companionViews, String companion, String[] compVals) {
        for (int i = 0; i < channelViews.length && i < channelVals.length; i++) {
            boolean sel = channelVals[i].equals(channel);
            channelViews[i].setText((sel ? "● " : "○ ") + channelViews[i].getText().toString().replaceAll("^[●○] ", ""));
            channelViews[i].setTypeface(sel ? AndroidUtilities.bold() : null);
            channelViews[i].setTextColor(Theme.getColor(sel ? Theme.key_dialogTextLink : Theme.key_dialogTextBlack));
        }
        for (int i = 0; i < companionViews.length && i < compVals.length; i++) {
            boolean sel = compVals[i].equals(companion);
            companionViews[i].setText((sel ? "● " : "○ ") + companionViews[i].getText().toString().replaceAll("^[●○] ", ""));
            companionViews[i].setTypeface(sel ? AndroidUtilities.bold() : null);
            companionViews[i].setTextColor(Theme.getColor(sel ? Theme.key_dialogTextLink : Theme.key_dialogTextBlack));
        }
    }

    /** Post-update community promo: join t.me/dkmiogram for news, bug reports and ideas. */
    private static void showChannelPromo(BaseFragment fragment) {
        Context context = fragment.getParentActivity();
        try {
            ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit().putString(KEY_PROMO_VERSION, getCurrentAppVersion())
                    .putBoolean(KEY_PROMO_DONE_ONCE, true).apply();
        } catch (Throwable ignore) {}
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle(MiogramLocale.get("Наш Telegram-канал", "Наш Telegram-канал", "Our Telegram channel"));
        builder.setMessage(MiogramLocale.get(
                "Заходь на @dkmiogram: новини оновлень, можна кидати помилки та пропонувати ідеї. Нам важливий кожен!",
                "Заходи на @dkmiogram: новости обновлений, можно кидать ошибки и предлагать идеи. Нам важен каждый!",
                "Join @dkmiogram: update news, bug reports and your ideas are welcome. Every member counts!"));
        builder.setPositiveButton(MiogramLocale.get("Приєднатися", "Присоединиться", "Join"), (d, which) -> {
            d.dismiss();
            try {
                int account = fragment.getCurrentAccount();
                MessagesController.getInstance(account).openByUserName("dkmiogram", fragment, 1);
            } catch (Throwable t) {
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://t.me/dkmiogram"));
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                } catch (Throwable ignore) {}
            }
        });
        builder.setNegativeButton(MiogramLocale.get("Пізніше", "Позже", "Later"), (d, which) -> d.dismiss());
        try {
            org.telegram.ui.ActionBar.AlertDialog dialog = builder.create();
            dialog.show();
            android.view.View posBtn = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
            if (posBtn instanceof android.widget.TextView) {
                android.widget.TextView tv = (android.widget.TextView) posBtn;
                tv.setTypeface(AndroidUtilities.bold());
                tv.setTextColor(Theme.getColor(Theme.key_dialogTextLink));
            }
        } catch (Throwable ignore) {}
    }

    private static void performBackgroundCheck() {
        LaunchActivity act = LaunchActivity.instance;
        if (act == null || act.isFinishing()) return;

        BaseFragment fragment = act.getSafeLastFragment();
        if (fragment == null) return;

        fetchLatestRelease((hasUpdate, version, changelog, apkUrl) -> {
            if (hasUpdate) {
                SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String lastSeen = prefs.getString(KEY_LAST_SEEN_TAG, "");
                if (version != null && version.equalsIgnoreCase(lastSeen)) {
                    return; // Already notified the user about this specific release
                }
                prefs.edit().putString(KEY_LAST_SEEN_TAG, version).apply();

                new Handler(Looper.getMainLooper()).post(() -> {
                    LaunchActivity currentAct = LaunchActivity.instance;
                    if (currentAct != null && !currentAct.isFinishing()) {
                        BaseFragment currentFrag = currentAct.getSafeLastFragment();
                        if (currentFrag != null) {
                            MiogramUpdateBottomSheet sheet = new MiogramUpdateBottomSheet(currentFrag, true, version, changelog, apkUrl);
                            sheet.show();
                        }
                    }
                });
            }
        });
    }

    public static void checkAndShowUpdate(BaseFragment fragment, boolean manualCheck) {
        if (fragment == null || fragment.getParentActivity() == null) return;

        if (manualCheck) {
            Toast.makeText(fragment.getParentActivity(), MiogramLocale.get("Перевірка оновлень Miogram...", "Проверка обновлений Miogram...", "Checking for Miogram updates..."), Toast.LENGTH_SHORT).show();
        }

        fetchLatestRelease((hasUpdate, version, changelog, apkUrl) -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (fragment.getParentActivity() == null || fragment.getParentActivity().isFinishing()) {
                    return;
                }
                final String finalVer = (version != null && !version.isEmpty()) ? version : getCurrentAppVersion();
                MiogramUpdateBottomSheet sheet = new MiogramUpdateBottomSheet(fragment, hasUpdate, finalVer, changelog, apkUrl);
                sheet.show();
            });
        });
    }

    private interface UpdateCallback {
        void onResult(boolean hasUpdate, String version, String changelog, String apkUrl);
    }

    private static void fetchLatestRelease(UpdateCallback callback) {
        final boolean beta = !CHANNEL_STABLE.equals(getUpdateChannel());
        new Thread(() -> {
            try {
                URL url = new URL(beta ? GITHUB_API_RELEASES : GITHUB_API_LATEST);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject json;
                    if (beta) {
                        json = pickBetaRelease(new JSONArray(sb.toString()));
                        if (json == null) {
                            callback.onResult(false, getCurrentAppVersion(), null, null);
                            return;
                        }
                    } else {
                        json = new JSONObject(sb.toString());
                    }
                    String tag = json.optString("tag_name", "v12.10.1");
                    String body = json.optString("body", "");
                    String apkUrl = "";

                    JSONArray assets = json.optJSONArray("assets");
                    if (assets != null) {
                        for (int i = 0; i < assets.length(); i++) {
                            JSONObject asset = assets.getJSONObject(i);
                            String name = asset.optString("name", "");
                            if (name.endsWith(".apk")) {
                                apkUrl = asset.optString("browser_download_url", "");
                                break;
                            }
                        }
                    }

                    final String finalVersion = tag.replace("v", "").replace("V", "").trim();
                    final String currentVersion = getCurrentAppVersion();
                    boolean isNewer = isNewerVersion(currentVersion, finalVersion, tag, body);

                    callback.onResult(isNewer, finalVersion, body, apkUrl);
                } else {
                    callback.onResult(false, getCurrentAppVersion(), null, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
                callback.onResult(false, getCurrentAppVersion(), null, null);
            }
        }).start();
    }

    /** Beta channel: newest non-draft release (prereleases included) that ships an APK. */
    private static JSONObject pickBetaRelease(JSONArray releases) {
        if (releases == null) return null;
        for (int i = 0; i < releases.length(); i++) {
            try {
                JSONObject r = releases.getJSONObject(i);
                if (r.optBoolean("draft", false)) continue;
                JSONArray assets = r.optJSONArray("assets");
                if (assets == null) continue;
                for (int j = 0; j < assets.length(); j++) {
                    if (assets.getJSONObject(j).optString("name", "").endsWith(".apk")) {
                        return r;
                    }
                }
            } catch (Throwable ignore) {}
        }
        return null;
    }

    public static String getCurrentAppVersion() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            PackageInfo pInfo = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pInfo.versionName != null ? pInfo.versionName : BuildVars.BUILD_VERSION_STRING;
        } catch (Exception e) {
            return BuildVars.BUILD_VERSION_STRING != null ? BuildVars.BUILD_VERSION_STRING : "12.10.1";
        }
    }

    public static boolean isNewerVersion(String currentVersion, String remoteVersion, String remoteTag, String changelog) {
        if (TextUtils.isEmpty(remoteVersion)) return false;

        String c = currentVersion != null ? currentVersion.replace("v", "").replace("V", "").trim() : "";
        String r = remoteVersion.trim();

        // 1. Exact match or prefix match
        if (c.equalsIgnoreCase(r)) return false;
        if (!c.isEmpty() && !r.isEmpty()) {
            if (c.startsWith(r) || r.startsWith(c)) {
                // If it's the exact same base release with a commit hash (e.g. 12.10.1-83b6b68 vs 12.10.1), it is up to date
                return false;
            }
        }

        // 2. If changelog or remote tag contains commit hash that matches installed app
        if (!TextUtils.isEmpty(changelog) && !TextUtils.isEmpty(c)) {
            String[] parts = c.split("-");
            if (parts.length > 1) {
                String currentHash = parts[parts.length - 1].trim();
                if (currentHash.length() >= 4 && changelog.contains(currentHash)) {
                    return false; // Same commit already running!
                }
            }
        }

        // 3. Compare numeric version components
        String[] cParts = c.split("[^0-9]+");
        String[] rParts = r.split("[^0-9]+");

        int len = Math.max(cParts.length, rParts.length);
        for (int i = 0; i < len; i++) {
            int cVal = 0;
            int rVal = 0;
            if (i < cParts.length && !cParts[i].isEmpty()) {
                try { cVal = Integer.parseInt(cParts[i]); } catch (Exception ignored) {}
            }
            if (i < rParts.length && !rParts[i].isEmpty()) {
                try { rVal = Integer.parseInt(rParts[i]); } catch (Exception ignored) {}
            }
            if (rVal > cVal) return true;
            if (rVal < cVal) return false;
        }
        return false;
    }
}
