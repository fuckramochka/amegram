package app.miogram.bridge.modapi;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.net.URLEncoder;

import app.miogram.bridge.hooks.MioHook;
import app.miogram.bridge.media.MiogramMediaDownloader;

/**
 * File-first media resolver for mods (e.g. a TikTok mod): page URL in,
 * local file out. No chat involved — unlike
 * {@link MiogramMediaDownloader#downloadAndSend}, which sends straight
 * into a dialog. Progress and lifecycle go through the
 * {@code MEDIA_DOWNLOAD} MioHook point.
 *
 * <p>Thread contract: every method posts work to {@code globalQueue} and
 * answers on the UI thread.
 */
public final class MioDownload {

    private MioDownload() {}

    /** Resolved direct stream. */
    public static final class StreamInfo {
        public final String pageUrl;
        public final String streamUrl;
        public final String title;
        public final String filename;

        public StreamInfo(String pageUrl, String streamUrl, String title, String filename) {
            this.pageUrl = pageUrl;
            this.streamUrl = streamUrl;
            this.title = title != null ? title : "";
            this.filename = filename != null && !filename.isEmpty()
                    ? filename : ("media_" + System.currentTimeMillis() + ".mp4");
        }
    }

    public interface ResolveCallback {
        void onResolved(StreamInfo info);
        void onError(String error);
    }

    public interface FileCallback {
        void onDone(File file);
        void onError(String error);
    }

    private static final String[] TIKWM_HOSTS = new String[]{
            "https://www.tikwm.com/api/?url=",
            "https://tikwm.com/api/?url="
    };

    private static final String[] COBALT_INSTANCES = new String[]{
            "https://co.wuk.sh/api/json",
            "https://cobalt-api.kwiatekm.tokyo/",
            "https://cobalt.api.scity.network/",
            "https://api.cobalt.tools/"
    };

    /**
     * Resolves a TikTok / YouTube / Instagram / X / Pinterest page (or a
     * direct media link) to a downloadable stream URL.
     */
    public static void resolve(final String pageUrl, final ResolveCallback callback) {
        if (TextUtils.isEmpty(pageUrl)) {
            if (callback != null) callback.onError("Empty link");
            return;
        }
        final String url = pageUrl.trim().split("\\s")[0];
        MioHook.dispatchDownload("started", url, null, 0, null);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                StreamInfo info = resolveSync(url);
                if (info != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (callback != null) callback.onResolved(info);
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        MioHook.dispatchDownload("failed", url, null, 0, "No stream found");
                        if (callback != null) callback.onError("No stream found");
                    });
                }
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> {
                    MioHook.dispatchDownload("failed", url, null, 0, t.getMessage());
                    if (callback != null) callback.onError(t.getMessage());
                });
            }
        });
    }

    private static StreamInfo resolveSync(String url) throws Exception {
        if (MiogramMediaDownloader.isDirectMediaUrl(url)) {
            String name = url.substring(url.lastIndexOf('/') + 1);
            int cut = name.indexOf('?');
            if (cut > 0) name = name.substring(0, cut);
            if (TextUtils.isEmpty(name)) name = "media_" + System.currentTimeMillis() + ".mp4";
            return new StreamInfo(url, url, "", name);
        }
        String lower = url.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("tiktok.com/") || lower.contains("vt.tiktok") || lower.contains("vm.tiktok")) {
            StreamInfo tiktok = resolveTikTok(url);
            if (tiktok != null) return tiktok;
        }
        return resolveCobalt(url);
    }

    private static StreamInfo resolveTikTok(String pageUrl) {
        try {
            String target = pageUrl;
            String resolved = MiogramMediaDownloader.resolveRedirects(pageUrl);
            if (!TextUtils.isEmpty(resolved)) target = resolved;

            for (String host : TIKWM_HOSTS) {
                String jsonStr;
                try {
                    jsonStr = MiogramMediaDownloader.httpGet(host + URLEncoder.encode(target, "UTF-8") + "&hd=1");
                } catch (Throwable e) {
                    jsonStr = null;
                }
                if (TextUtils.isEmpty(jsonStr)) continue;
                JSONObject json = new JSONObject(jsonStr);
                if (json.optInt("code", -1) != 0 || !json.has("data")) continue;
                JSONObject data = json.getJSONObject("data");
                String title = data.optString("title", "");
                String video = data.optString("hdplay", "");
                if (TextUtils.isEmpty(video)) video = data.optString("play", "");
                if (TextUtils.isEmpty(video)) video = data.optString("wmplay", "");
                if (!TextUtils.isEmpty(video)) {
                    if (video.startsWith("/")) video = "https://www.tikwm.com" + video;
                    return new StreamInfo(pageUrl, video, title, "tiktok_" + System.currentTimeMillis() + ".mp4");
                }
                // Photo slideshow: expose the first frame.
                if (data.has("images")) {
                    JSONArray images = data.getJSONArray("images");
                    if (images.length() > 0) {
                        String img = images.getString(0);
                        if (!TextUtils.isEmpty(img)) {
                            return new StreamInfo(pageUrl, img, title, "tiktok_" + System.currentTimeMillis() + ".jpg");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    private static StreamInfo resolveCobalt(String pageUrl) {
        try {
            for (String instance : COBALT_INSTANCES) {
                try {
                    JSONObject postBody = new JSONObject();
                    postBody.put("url", pageUrl);
                    postBody.put("videoQuality", "720");
                    postBody.put("downloadMode", "auto");
                    String response = MiogramMediaDownloader.httpPostJson(instance, postBody.toString());
                    if (TextUtils.isEmpty(response)) continue;
                    JSONObject json = new JSONObject(response);
                    String status = json.optString("status", "");
                    if ("tunnel".equals(status) || "redirect".equals(status)) {
                        String dl = json.optString("url", "");
                        if (!TextUtils.isEmpty(dl)) {
                            return new StreamInfo(pageUrl, dl, "", json.optString("filename", ""));
                        }
                    } else if ("picker".equals(status) && json.has("picker")) {
                        JSONArray picker = json.getJSONArray("picker");
                        if (picker.length() > 0) {
                            String dl = picker.getJSONObject(0).optString("url", "");
                            if (!TextUtils.isEmpty(dl)) {
                                return new StreamInfo(pageUrl, dl, "", json.optString("filename", ""));
                            }
                        }
                    }
                } catch (Throwable ignore) {}
            }
            if (pageUrl.contains("twitter.com") || pageUrl.contains("x.com")) {
                String vx = pageUrl.replace("twitter.com", "api.vxtwitter.com").replace("x.com", "api.vxtwitter.com");
                String vxRes = MiogramMediaDownloader.httpGet(vx);
                if (!TextUtils.isEmpty(vxRes)) {
                    JSONObject vxJson = new JSONObject(vxRes);
                    if (vxJson.has("mediaURLs")) {
                        JSONArray urls = vxJson.getJSONArray("mediaURLs");
                        if (urls.length() > 0) {
                            String dl = urls.getString(0);
                            if (!TextUtils.isEmpty(dl)) return new StreamInfo(pageUrl, dl, "", "");
                        }
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    /** Downloads an already resolved stream to {@code destFile}. */
    public static void fetch(final StreamInfo info, final File destFile,
                             final MiogramMediaDownloader.ProgressListener listener,
                             final FileCallback callback) {
        if (info == null || TextUtils.isEmpty(info.streamUrl) || destFile == null) {
            if (callback != null) callback.onError("Bad stream");
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                MiogramMediaDownloader.downloadFileWithProgress(info.streamUrl, destFile,
                        (pct, status) -> {
                            MioHook.dispatchDownload("progress", info.pageUrl, null, pct, null);
                            if (listener != null) listener.onProgress(pct, status);
                        });
                if (destFile.exists() && destFile.length() > 0) {
                    AndroidUtilities.runOnUIThread(() -> {
                        MioHook.dispatchDownload("completed", info.pageUrl, destFile.getAbsolutePath(), 100, null);
                        if (callback != null) callback.onDone(destFile);
                    });
                } else {
                    throw new Exception("Empty file");
                }
            } catch (Throwable t) {
                FileLog.e(t);
                try {
                    destFile.delete();
                } catch (Throwable ignore) {}
                AndroidUtilities.runOnUIThread(() -> {
                    MioHook.dispatchDownload("failed", info.pageUrl, null, 0,
                            t.getMessage() != null ? t.getMessage() : "Download error");
                    if (callback != null) callback.onError(t.getMessage());
                });
            }
        });
    }

    /**
     * One call: resolve + download into {@code destDir} (filename from the
     * stream). Answers with the local file.
     */
    public static void download(final String pageUrl, final File destDir,
                                final MiogramMediaDownloader.ProgressListener listener,
                                final FileCallback callback) {
        resolve(pageUrl, new ResolveCallback() {
            @Override
            public void onResolved(StreamInfo info) {
                try {
                    File dir = destDir != null ? destDir
                            : FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                    if (!dir.exists()) dir.mkdirs();
                    File dest = new File(dir, info.filename);
                    fetch(info, dest, listener, callback);
                } catch (Throwable t) {
                    if (callback != null) callback.onError(t.getMessage());
                }
            }

            @Override
            public void onError(String error) {
                if (callback != null) callback.onError(error);
            }
        });
    }
}
