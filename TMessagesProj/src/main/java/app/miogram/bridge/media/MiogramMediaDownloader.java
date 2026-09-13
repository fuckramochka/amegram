package app.miogram.bridge.media;

import android.content.Context;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.SendMessageChatArguments;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.miogram.bridge.MiogramLocale;
import app.miogram.bridge.customui.MiogramHaptic;

/**
 * High-speed autonomous media extractor & downloader for:
 * - TikTok (No-watermark MP4 video OR Photo Slideshow Album + Audio)
 * - YouTube & YouTube Shorts
 * - Instagram (Reels, Posts, Stories)
 * - Twitter / X
 * - Pinterest, Reddit, Twitch
 */
public class MiogramMediaDownloader {

    public static class MediaLinkInfo {
        public final String url;
        public final String platform;
        public final String platformName;
        public final boolean isLikelySlideshow;

        public MediaLinkInfo(String url, String platform, String platformName, boolean isLikelySlideshow) {
            this.url = url;
            this.platform = platform;
            this.platformName = platformName;
            this.isLikelySlideshow = isLikelySlideshow;
        }
    }

    public interface ProgressListener {
        void onProgress(int percent, String statusText);
    }

    public interface CompletionCallback {
        void onSuccess(String message);
        void onError(String errorText);
    }

    // Supported URL patterns
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s]+)");

    private static final Pattern TIKTOK_PATTERN = Pattern.compile("https?://(?:vm\\.|vt\\.|www\\.)?tiktok\\.com/(?:@[^/]+/video/\\d+|t/[a-zA-Z0-9]+|[^\\s]+)");
    private static final Pattern YOUTUBE_PATTERN = Pattern.compile("https?://(?:www\\.|m\\.)?(?:youtube\\.com/(?:watch\\?v=[a-zA-Z0-9_\\-]+|shorts/[a-zA-Z0-9_\\-]+)|youtu\\.be/[a-zA-Z0-9_\\-]+)");
    private static final Pattern INSTAGRAM_PATTERN = Pattern.compile("https?://(?:www\\.)?instagram\\.com/(?:reel|reels|p|stories)/[a-zA-Z0-9_\\-]+");
    private static final Pattern TWITTER_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:twitter\\.com|x\\.com)/[^/]+/status/\\d+");
    private static final Pattern PINTEREST_PATTERN = Pattern.compile("https?://(?:www\\.)?(?:pinterest\\.com/pin/\\d+|pin\\.it/[a-zA-Z0-9]+)");

    /**
     * Inspects text and returns first supported video/media URL info, or null if none.
     */
    public static MediaLinkInfo extractSupportedUrl(CharSequence text) {
        if (TextUtils.isEmpty(text)) return null;
        String s = text.toString();
        Matcher m = URL_PATTERN.matcher(s);
        while (m.find()) {
            String candidate = m.group(1);
            if (candidate.endsWith(".") || candidate.endsWith(",") || candidate.endsWith(")") || candidate.endsWith("]")) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }

            if (TIKTOK_PATTERN.matcher(candidate).find() || candidate.contains("tiktok.com/")) {
                boolean isPhoto = candidate.contains("/photo/");
                return new MediaLinkInfo(candidate, "tiktok", "TikTok", isPhoto);
            }
            if (YOUTUBE_PATTERN.matcher(candidate).find() || candidate.contains("youtube.com/shorts/") || candidate.contains("youtu.be/")) {
                boolean isShorts = candidate.contains("/shorts/");
                return new MediaLinkInfo(candidate, "youtube", isShorts ? "YouTube Shorts" : "YouTube", false);
            }
            if (INSTAGRAM_PATTERN.matcher(candidate).find() || candidate.contains("instagram.com/reel/")) {
                return new MediaLinkInfo(candidate, "instagram", "Instagram Reel", false);
            }
            if (TWITTER_PATTERN.matcher(candidate).find() || candidate.contains("x.com/") || candidate.contains("twitter.com/")) {
                return new MediaLinkInfo(candidate, "twitter", "X / Twitter", false);
            }
            if (PINTEREST_PATTERN.matcher(candidate).find() || candidate.contains("pinterest.com/") || candidate.contains("pin.it/")) {
                return new MediaLinkInfo(candidate, "pinterest", "Pinterest", false);
            }
        }
        return null;
    }

    /**
     * Downloads media and sends it directly into chat.
     */
    public static void downloadAndSend(Context context, int currentAccount, long dialogId, MessageObject replyToMsg,
                                       String mediaUrl, ProgressListener progressListener, CompletionCallback callback) {
        if (TextUtils.isEmpty(mediaUrl)) {
            if (callback != null) callback.onError(MiogramLocale.get("Некоректне посилання", "Некорректная ссылка", "Invalid link"));
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                MediaLinkInfo info = extractSupportedUrl(mediaUrl);
                String platform = info != null ? info.platform : "";

                if ("tiktok".equals(platform)) {
                    downloadTikTok(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
                } else {
                    downloadViaCobalt(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) {
                        callback.onError(e.getMessage() != null ? e.getMessage() : "Download error");
                    }
                });
            }
        });
    }

    // =========================================================================
    // TIKTOK (TikWM API -> Direct MP4 / Photo Album + MP3)
    // =========================================================================

    private static void downloadTikTok(int currentAccount, long dialogId, MessageObject replyToMsg,
                                       String mediaUrl, ProgressListener progressListener, CompletionCallback callback) {
        try {
            reportProgress(progressListener, 10, MiogramLocale.get("Аналіз TikTok...", "Анализ TikTok...", "Analyzing TikTok..."));

            String apiUrl = "https://www.tikwm.com/api/?url=" + URLEncoder.encode(mediaUrl, "UTF-8") + "&hd=1";
            String jsonStr = httpGet(apiUrl);
            if (TextUtils.isEmpty(jsonStr)) {
                // Fallback to cobalt
                downloadViaCobalt(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
                return;
            }

            JSONObject json = new JSONObject(jsonStr);
            int code = json.optInt("code", -1);
            if (code != 0 || !json.has("data")) {
                // Fallback to cobalt
                downloadViaCobalt(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
                return;
            }

            JSONObject data = json.getJSONObject("data");
            String title = data.optString("title", "");

            // Case A: Photo Slideshow Album
            if (data.has("images")) {
                JSONArray images = data.getJSONArray("images");
                if (images.length() > 0) {
                    reportProgress(progressListener, 25, MiogramLocale.get("Завантаження фото (" + images.length() + " шт)...", "Загрузка фото (" + images.length() + " шт)...", "Downloading photos..."));

                    File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                    ArrayList<File> photoFiles = new ArrayList<>();
                    for (int i = 0; i < images.length(); i++) {
                        String imgUrl = images.getString(i);
                        File pf = new File(cacheDir, "tiktok_photo_" + System.currentTimeMillis() + "_" + i + ".jpg");
                        downloadFileWithProgress(imgUrl, pf, null);
                        if (pf.exists() && pf.length() > 0) {
                            photoFiles.add(pf);
                        }
                        int pct = 25 + (int) ((i + 1) / (float) images.length() * 45f);
                        reportProgress(progressListener, pct, MiogramLocale.get("Завантаження фото " + (i + 1) + "/" + images.length(), "Загрузка фото " + (i + 1) + "/" + images.length(), "Downloading " + (i + 1) + "/" + images.length()));
                    }

                    // Download background audio
                    File audioFile = null;
                    String musicUrl = data.optString("music", "");
                    if (TextUtils.isEmpty(musicUrl)) musicUrl = data.optString("play", "");
                    if (!TextUtils.isEmpty(musicUrl)) {
                        reportProgress(progressListener, 75, MiogramLocale.get("Завантаження аудіо...", "Загрузка аудио...", "Downloading audio..."));
                        audioFile = new File(cacheDir, "tiktok_audio_" + System.currentTimeMillis() + ".mp3");
                        downloadFileWithProgress(musicUrl, audioFile, null);
                    }

                    final File finalAudio = audioFile;
                    final String finalTitle = title;
                    reportProgress(progressListener, 90, MiogramLocale.get("Відправка в чат...", "Отправка в чат...", "Sending to chat..."));

                    AndroidUtilities.runOnUIThread(() -> {
                        AccountInstance ai = AccountInstance.getInstance(currentAccount);

                        // 1. Send photo album
                        if (!photoFiles.isEmpty()) {
                            ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
                            for (File pf : photoFiles) {
                                SendMessagesHelper.SendingMediaInfo mi = new SendMessagesHelper.SendingMediaInfo();
                                mi.path = pf.getAbsolutePath();
                                mi.isVideo = false;
                                mi.caption = media.isEmpty() ? finalTitle : null;
                                media.add(mi);
                            }
                            SendMessagesHelper.prepareSendingMedia(ai, media, dialogId, replyToMsg, null, null, null,
                                    false, true, null, true, 0, 0, 0, false, null, (SendMessageChatArguments) null, 0L, false, 0L, 0L, null);
                        }

                        // 2. Send background music
                        if (finalAudio != null && finalAudio.exists() && finalAudio.length() > 0) {
                            SendMessagesHelper.prepareSendingDocument(ai, finalAudio.getAbsolutePath(), finalAudio.getAbsolutePath(),
                                    null, "", "audio/mp3", dialogId, replyToMsg, null, null, null, null, true, 0, null, null, false);
                        }

                        MiogramHaptic.success();
                        if (callback != null) callback.onSuccess(MiogramLocale.get("Альбом фото та аудіо надіслано!", "Альбом фото и аудио отправлен!", "Photos and audio sent!"));
                    });
                    return;
                }
            }

            // Case B: Direct Video without watermark
            String videoDownloadUrl = data.optString("hdplay", "");
            if (TextUtils.isEmpty(videoDownloadUrl)) videoDownloadUrl = data.optString("play", "");
            if (TextUtils.isEmpty(videoDownloadUrl)) videoDownloadUrl = data.optString("wmplay", "");

            if (!TextUtils.isEmpty(videoDownloadUrl)) {
                if (videoDownloadUrl.startsWith("/")) {
                    videoDownloadUrl = "https://www.tikwm.com" + videoDownloadUrl;
                }
                File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                File videoFile = new File(cacheDir, "tiktok_video_" + System.currentTimeMillis() + ".mp4");

                reportProgress(progressListener, 20, MiogramLocale.get("Завантаження відео...", "Загрузка видео...", "Downloading video..."));
                downloadFileWithProgress(videoDownloadUrl, videoFile, (p, s) -> {
                    int calcP = 20 + (int) (p * 0.70f);
                    reportProgress(progressListener, calcP, MiogramLocale.get("Завантаження відео " + p + "%", "Загрузка видео " + p + "%", "Downloading " + p + "%"));
                });

                if (videoFile.exists() && videoFile.length() > 0) {
                    reportProgress(progressListener, 95, MiogramLocale.get("Відправка відео...", "Отправка видео...", "Sending video..."));
                    final String finalTitle = title;
                    AndroidUtilities.runOnUIThread(() -> {
                        AccountInstance ai = AccountInstance.getInstance(currentAccount);
                        SendMessagesHelper.prepareSendingVideo(ai, videoFile.getAbsolutePath(), null, null, null,
                                dialogId, replyToMsg, null, null, null, null, 0, null, true, 0, 0, false, false, finalTitle, (SendMessageChatArguments) null, 0L, 0L);
                        MiogramHaptic.success();
                        if (callback != null) callback.onSuccess(MiogramLocale.get("Відео успішно надіслано!", "Видео успешно отправлено!", "Video sent successfully!"));
                    });
                    return;
                }
            }

            // Fallback to cobalt
            downloadViaCobalt(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
        } catch (Throwable e) {
            FileLog.e(e);
            downloadViaCobalt(currentAccount, dialogId, replyToMsg, mediaUrl, progressListener, callback);
        }
    }

    // =========================================================================
    // COBALT (YouTube, Shorts, Instagram, Twitter/X, Pinterest, etc.)
    // =========================================================================

    private static final String[] COBALT_INSTANCES = new String[]{
            "https://api.cobalt.tools/",
            "https://cobalt.api.scity.network/",
            "https://co.wuk.sh/api/json",
            "https://cobalt-api.kwiatekm.tokyo/"
    };

    private static void downloadViaCobalt(int currentAccount, long dialogId, MessageObject replyToMsg,
                                          String mediaUrl, ProgressListener progressListener, CompletionCallback callback) {
        try {
            reportProgress(progressListener, 15, MiogramLocale.get("Отримання потоку відео...", "Получение потока видео...", "Extracting video stream..."));

            String downloadUrl = null;
            String filename = "media_" + System.currentTimeMillis() + ".mp4";

            for (String instance : COBALT_INSTANCES) {
                try {
                    JSONObject postBody = new JSONObject();
                    postBody.put("url", mediaUrl);
                    postBody.put("videoQuality", "720");
                    postBody.put("downloadMode", "auto");

                    String response = httpPostJson(instance, postBody.toString());
                    if (!TextUtils.isEmpty(response)) {
                        JSONObject json = new JSONObject(response);
                        String status = json.optString("status", "");
                        if ("tunnel".equals(status) || "redirect".equals(status)) {
                            downloadUrl = json.optString("url", "");
                            if (json.has("filename")) filename = json.optString("filename", filename);
                            break;
                        } else if ("picker".equals(status) && json.has("picker")) {
                            JSONArray picker = json.getJSONArray("picker");
                            if (picker.length() > 0) {
                                downloadUrl = picker.getJSONObject(0).optString("url", "");
                                break;
                            }
                        }
                    }
                } catch (Throwable ignore) {}
            }

            if (TextUtils.isEmpty(downloadUrl)) {
                // Secondary Fallback for Twitter / X
                if (mediaUrl.contains("twitter.com") || mediaUrl.contains("x.com")) {
                    String vxtwitter = mediaUrl.replace("twitter.com", "api.vxtwitter.com").replace("x.com", "api.vxtwitter.com");
                    String vxRes = httpGet(vxtwitter);
                    if (!TextUtils.isEmpty(vxRes)) {
                        JSONObject vxJson = new JSONObject(vxRes);
                        if (vxJson.has("mediaURLs")) {
                            JSONArray mUrls = vxJson.getJSONArray("mediaURLs");
                            if (mUrls.length() > 0) {
                                downloadUrl = mUrls.getString(0);
                            }
                        }
                    }
                }
            }

            if (TextUtils.isEmpty(downloadUrl)) {
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) callback.onError(MiogramLocale.get("Не вдалося отримати відео з цього посилання", "Не удалось извлечь видео по этой ссылке", "Could not extract video"));
                });
                return;
            }

            reportProgress(progressListener, 30, MiogramLocale.get("Завантаження файлу...", "Загрузка файла...", "Downloading file..."));

            File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            File localFile = new File(cacheDir, filename);

            final String fUrl = downloadUrl;
            downloadFileWithProgress(fUrl, localFile, (p, s) -> {
                int calcP = 30 + (int) (p * 0.65f);
                reportProgress(progressListener, calcP, MiogramLocale.get("Завантаження " + p + "%", "Загрузка " + p + "%", "Downloading " + p + "%"));
            });

            if (localFile.exists() && localFile.length() > 0) {
                reportProgress(progressListener, 96, MiogramLocale.get("Відправка в чат...", "Отправка в чат...", "Sending to chat..."));
                AndroidUtilities.runOnUIThread(() -> {
                    AccountInstance ai = AccountInstance.getInstance(currentAccount);
                    boolean isVideo = localFile.getName().endsWith(".mp4") || localFile.getName().endsWith(".webm") || localFile.getName().endsWith(".mkv");
                    if (isVideo) {
                        SendMessagesHelper.prepareSendingVideo(ai, localFile.getAbsolutePath(), null, null, null,
                                dialogId, replyToMsg, null, null, null, null, 0, null, true, 0, 0, false, false, "", (SendMessageChatArguments) null, 0L, 0L);
                    } else {
                        SendMessagesHelper.prepareSendingPhoto(ai, localFile.getAbsolutePath(), null,
                                dialogId, replyToMsg, null, null, "", null, null, null, 0, null, true, 0, 0, (SendMessageChatArguments) null);
                    }
                    MiogramHaptic.success();
                    if (callback != null) callback.onSuccess(MiogramLocale.get("Медіа успішно надіслано!", "Медиа успешно отправлено!", "Media sent successfully!"));
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (callback != null) callback.onError(MiogramLocale.get("Помилка завантаження потоку", "Ошибка загрузки потока", "Stream download failed"));
                });
            }

        } catch (Throwable e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) callback.onError(e.getMessage() != null ? e.getMessage() : "Cobalt error");
            });
        }
    }

    // =========================================================================
    // HTTP UTILITIES
    // =========================================================================

    private static String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            conn.setRequestProperty("Accept", "application/json, text/plain, */*");
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                InputStream in = conn.getInputStream();
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) != -1) {
                    out.write(buf, 0, r);
                }
                in.close();
                return out.toString("UTF-8");
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private static String httpPostJson(String urlStr, String jsonBody) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");

            byte[] bodyBytes = jsonBody.getBytes("UTF-8");
            conn.setFixedLengthStreamingMode(bodyBytes.length);
            OutputStream out = conn.getOutputStream();
            out.write(bodyBytes);
            out.flush();
            out.close();

            int code = conn.getResponseCode();
            InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            if (in != null) {
                java.io.ByteArrayOutputStream resOut = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = in.read(buf)) != -1) {
                    resOut.write(buf, 0, r);
                }
                in.close();
                return resOut.toString("UTF-8");
            }
        } catch (Throwable ignore) {}
        return null;
    }

    private static void downloadFileWithProgress(String fileUrl, File destFile, ProgressListener listener) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
        conn.connect();

        int responseCode = conn.getResponseCode();
        // Handle HTTP 301/302 redirects
        if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == 307 || responseCode == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            url = new URL(newUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            conn.connect();
        }

        long fileLength = conn.getContentLength();
        InputStream input = new BufferedInputStream(conn.getInputStream(), 8192);
        OutputStream output = new FileOutputStream(destFile);

        byte[] data = new byte[8192];
        long total = 0;
        int count;
        long lastReport = 0;

        while ((count = input.read(data)) != -1) {
            total += count;
            output.write(data, 0, count);

            if (fileLength > 0 && listener != null) {
                long now = System.currentTimeMillis();
                if (now - lastReport > 80) {
                    lastReport = now;
                    int pct = (int) (total * 100 / fileLength);
                    listener.onProgress(pct, pct + "%");
                }
            }
        }

        output.flush();
        output.close();
        input.close();
        conn.disconnect();
    }

    private static void reportProgress(ProgressListener listener, int pct, String status) {
        if (listener != null) {
            AndroidUtilities.runOnUIThread(() -> listener.onProgress(pct, status));
        }
    }
}
