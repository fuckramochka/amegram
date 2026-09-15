package app.miogram.bridge.modapi;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import app.miogram.bridge.updater.MiogramUpdater;

/**
 * Parameterized GitHub-release checker: the same logic Miogram uses for
 * itself, reusable by any mod (e.g. a TikTok mod gets free OTA updates).
 * Answers on the UI thread.
 */
public final class MioUpdate {

    private MioUpdate() {}

    public interface ReleaseCallback {
        void onResult(boolean hasUpdate, String version, String changelog, String apkUrl);
    }

    /**
     * @param ownerRepo        "owner/repo" on github.com.
     * @param includePrerelease newest non-draft release with an APK (beta
     *                         channel) when true, otherwise /latest (stable).
     */
    public static void check(final String ownerRepo, final boolean includePrerelease, final ReleaseCallback callback) {
        if (ownerRepo == null || !ownerRepo.contains("/")) {
            if (callback != null) callback.onResult(false, MiogramUpdater.getCurrentAppVersion(), null, null);
            return;
        }
        new Thread(() -> {
            try {
                URL url = new URL(includePrerelease
                        ? "https://api.github.com/repos/" + ownerRepo + "/releases?per_page=10"
                        : "https://api.github.com/repos/" + ownerRepo + "/releases/latest");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                String tag = "";
                String body = "";
                String apkUrl = "";
                boolean ok = false;
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject json;
                    if (includePrerelease) {
                        json = pickReleaseWithApk(new JSONArray(sb.toString()));
                    } else {
                        json = new JSONObject(sb.toString());
                    }
                    if (json != null) {
                        ok = true;
                        tag = json.optString("tag_name", "");
                        body = json.optString("body", "");
                        JSONArray assets = json.optJSONArray("assets");
                        if (assets != null) {
                            for (int i = 0; i < assets.length(); i++) {
                                JSONObject asset = assets.getJSONObject(i);
                                if (asset.optString("name", "").endsWith(".apk")) {
                                    apkUrl = asset.optString("browser_download_url", "");
                                    break;
                                }
                            }
                        }
                    }
                }
                final boolean fOk = ok;
                final String finalVersion = tag.replace("v", "").replace("V", "").trim();
                final String finalBody = body;
                final String finalApk = apkUrl;
                AndroidUtilities.runOnUIThread(() -> {
                    if (!fOk || finalVersion.isEmpty()) {
                        if (callback != null) {
                            callback.onResult(false, MiogramUpdater.getCurrentAppVersion(), null, null);
                        }
                        return;
                    }
                    boolean newer = MiogramUpdater.isNewerVersion(
                            MiogramUpdater.getCurrentAppVersion(), finalVersion, tag, finalBody);
                    if (callback != null) callback.onResult(newer, finalVersion, finalBody, finalApk);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) callback.onResult(false, MiogramUpdater.getCurrentAppVersion(), null, null);
                });
            }
        }).start();
    }

    /** Newest non-draft release shipping an APK (prereleases included). */
    private static JSONObject pickReleaseWithApk(JSONArray releases) {
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
}
