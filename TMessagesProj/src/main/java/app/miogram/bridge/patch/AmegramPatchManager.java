package app.miogram.bridge.patch;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

/**
 * Amegram Dynamic Hotpatch & Micro-Update Engine.
 *
 * Allows instant over-the-air bugfixes without downloading/reinstalling a full 100+ MB APK.
 * Features:
 * 1. Dynamic Config Rules (0 KB): remote flags, regex fixes, endpoint redirections, NPE guards.
 * 2. DEX Bytecode Micro-patches (10-30 KB): loads small compiled bytecode fixes via DexClassLoader.
 * 3. Local persistence & automatic verification on startup.
 */
public class AmegramPatchManager {

    private static final String PREFS_NAME = "amegram_hotpatches";
    private static final String KEY_APPLIED_PATCHES = "applied_patch_ids";
    private static final String KEY_RULES_JSON = "active_rules_json";
    private static final String KEY_LAST_CHECK_TIME = "last_patch_check";

    private static final String PATCHES_MANIFEST_URL =
            "https://raw.githubusercontent.com/fuckramochka/amegram/main/hotfix/patches.json";

    private static volatile AmegramPatchManager instance;

    public static AmegramPatchManager getInstance() {
        if (instance == null) {
            synchronized (AmegramPatchManager.class) {
                if (instance == null) {
                    instance = new AmegramPatchManager();
                }
            }
        }
        return instance;
    }

    private final ConcurrentHashMap<String, Object> activeRules = new ConcurrentHashMap<>();
    private final Set<String> appliedPatchIds = Collections.synchronizedSet(new HashSet<>());
    private volatile boolean initialized = false;

    public interface PatchCheckCallback {
        void onCheckComplete(int newPatchesApplied, String message);
    }

    private AmegramPatchManager() {
        loadPersistedState();
    }

    /**
     * Initializes hotpatch engine on app launch (called from ApplicationLoader).
     */
    public void init(Context context) {
        if (initialized) return;
        initialized = true;

        loadPersistedState();

        // Background check at startup with cooldown (at most once every 6 hours)
        Utilities.globalQueue.postRunnable(() -> {
            try {
                SharedPreferences prefs = getPrefs();
                long lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0);
                long now = System.currentTimeMillis();
                if (now - lastCheck > 6 * 60 * 60 * 1000L) {
                    checkForPatches(null);
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }, 5000L);
    }

    private SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private void loadPersistedState() {
        try {
            SharedPreferences prefs = getPrefs();
            Set<String> savedIds = prefs.getStringSet(KEY_APPLIED_PATCHES, null);
            if (savedIds != null) {
                appliedPatchIds.addAll(savedIds);
            }
            String rulesJson = prefs.getString(KEY_RULES_JSON, "");
            if (!TextUtils.isEmpty(rulesJson)) {
                JSONObject obj = new JSONObject(rulesJson);
                JSONArray keys = obj.names();
                if (keys != null) {
                    for (int i = 0; i < keys.length(); i++) {
                        String k = keys.getString(i);
                        activeRules.put(k, obj.get(k));
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e("AmegramPatchManager: loadPersistedState error", t);
        }
    }

    private void savePersistedState() {
        try {
            SharedPreferences prefs = getPrefs();
            JSONObject obj = new JSONObject();
            for (String k : activeRules.keySet()) {
                obj.put(k, activeRules.get(k));
            }
            prefs.edit()
                    .putStringSet(KEY_APPLIED_PATCHES, new HashSet<>(appliedPatchIds))
                    .putString(KEY_RULES_JSON, obj.toString())
                    .putLong(KEY_LAST_CHECK_TIME, System.currentTimeMillis())
                    .apply();
        } catch (Throwable t) {
            FileLog.e("AmegramPatchManager: savePersistedState error", t);
        }
    }

    /**
     * Checks if a dynamic rule / bugfix guard is active.
     */
    public boolean isRuleEnabled(String ruleKey, boolean defaultValue) {
        Object val = activeRules.get(ruleKey);
        if (val instanceof Boolean) {
            return (Boolean) val;
        }
        return defaultValue;
    }

    /**
     * Gets a string rule override (e.g. redirected endpoint or regex).
     */
    public String getRuleString(String ruleKey, String defaultValue) {
        Object val = activeRules.get(ruleKey);
        if (val instanceof String) {
            return (String) val;
        }
        return defaultValue;
    }

    public int getAppliedPatchCount() {
        return appliedPatchIds.size();
    }

    /**
     * Manual or automated check for micro-patches on GitHub.
     */
    public void checkForPatches(PatchCheckCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            int newApplied = 0;
            String message = "All up to date";
            try {
                URL url = new URL(PATCHES_MANIFEST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    JSONArray patches = root.optJSONArray("patches");
                    if (patches != null) {
                        for (int i = 0; i < patches.length(); i++) {
                            JSONObject patch = patches.getJSONObject(i);
                            String patchId = patch.optString("id");
                            if (TextUtils.isEmpty(patchId) || appliedPatchIds.contains(patchId)) {
                                continue;
                            }

                            String type = patch.optString("type", "config");
                            if ("config".equalsIgnoreCase(type)) {
                                JSONObject rules = patch.optJSONObject("rules");
                                if (rules != null) {
                                    JSONArray rKeys = rules.names();
                                    if (rKeys != null) {
                                        for (int k = 0; k < rKeys.length(); k++) {
                                            String rk = rKeys.getString(k);
                                            activeRules.put(rk, rules.get(rk));
                                        }
                                    }
                                }
                                appliedPatchIds.add(patchId);
                                newApplied++;
                            } else if ("bytecode".equalsIgnoreCase(type)) {
                                String dexUrl = patch.optString("url");
                                if (!TextUtils.isEmpty(dexUrl)) {
                                    boolean success = downloadAndLoadDexPatch(patchId, dexUrl, patch.optString("entrypoint"));
                                    if (success) {
                                        appliedPatchIds.add(patchId);
                                        newApplied++;
                                    }
                                }
                            }
                        }
                    }

                    if (newApplied > 0) {
                        savePersistedState();
                        message = "Applied " + newApplied + " hotpatch(es)";
                    }
                }
            } catch (Throwable t) {
                FileLog.e("AmegramPatchManager: checkForPatches error", t);
                message = "Error: " + t.getMessage();
            }

            final int finalNew = newApplied;
            final String finalMsg = message;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onCheckComplete(finalNew, finalMsg));
            }
        });
    }

    private boolean downloadAndLoadDexPatch(String patchId, String dexUrl, String entrypoint) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            File patchesDir = ctx.getDir("amegram_patches", Context.MODE_PRIVATE);
            File dexFile = new File(patchesDir, patchId + ".dex");
            File optDir = ctx.getDir("amegram_patches_opt", Context.MODE_PRIVATE);

            // Download small .dex (typically 10-30 KB)
            URL u = new URL(dexUrl);
            HttpURLConnection conn = (HttpURLConnection) u.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            if (conn.getResponseCode() != 200) return false;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(dexFile)) {
                byte[] buf = new byte[4096];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }

            // Load dynamically via DexClassLoader
            DexClassLoader loader = new DexClassLoader(
                    dexFile.getAbsolutePath(),
                    optDir.getAbsolutePath(),
                    null,
                    ctx.getClassLoader()
            );

            if (!TextUtils.isEmpty(entrypoint)) {
                Class<?> patchClass = loader.loadClass(entrypoint);
                java.lang.reflect.Method method = patchClass.getMethod("apply", Context.class);
                method.invoke(null, ctx);
            }

            FileLog.d("AmegramPatchManager: Successfully loaded DEX patch " + patchId);
            return true;
        } catch (Throwable t) {
            FileLog.e("AmegramPatchManager: failed loading DEX patch " + patchId, t);
            return false;
        }
    }
}
