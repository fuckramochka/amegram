package app.miogram.bridge.updater;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import app.miogram.bridge.MiogramLocale;

/**
 * Robust, Resumable Update Download Controller for Amegram:
 * - Ongoing background download notification with speed, ETA, and progress bar
 * - Partial wake lock to prevent connection drop when screen is locked
 * - Resumable HTTP download with safety checks (doesn't stitch mismatched files)
 * - Cached APK detection and safe cleanup (only Amegram update files)
 * - Android 8+ Unknown sources and Android 12+ unattended session install
 */
public class MiogramDownloadManager {

    private static volatile MiogramDownloadManager instance;
    private static final String NOTIFICATION_CHANNEL_ID = "amegram_updates_channel";
    private static final int NOTIFICATION_ID = 126148;

    public interface DownloadListener {
        void onProgress(int percent, long downloadedBytes, long totalBytes);
        default void onProgressDetailed(int percent, long downloadedBytes, long totalBytes, long speedBytesPerSec, int etaSeconds) {
            onProgress(percent, downloadedBytes, totalBytes);
        }
        void onComplete(File apkFile);
        void onError(String error);
    }

    private volatile boolean isDownloading = false;
    private volatile boolean isCancelled = false;
    private Thread downloadThread = null;
    private PowerManager.WakeLock wakeLock = null;

    private int currentPercent = 0;
    private long bytesDownloaded = 0;
    private long bytesTotal = 0;
    private long speedBytesPerSec = 0;
    private int etaSeconds = 0;
    private String currentVersion = "";
    private String currentChangelog = "";
    private String currentDownloadUrl = "";
    private File currentApkFile = null;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<DownloadListener> listeners = new ArrayList<>();

    public static MiogramDownloadManager getInstance() {
        if (instance == null) {
            synchronized (MiogramDownloadManager.class) {
                if (instance == null) {
                    instance = new MiogramDownloadManager();
                }
            }
        }
        return instance;
    }

    public synchronized void addListener(DownloadListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            if (isDownloading) {
                listener.onProgressDetailed(currentPercent, bytesDownloaded, bytesTotal, speedBytesPerSec, etaSeconds);
            }
        }
    }

    public synchronized void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    public boolean isDownloading() {
        return isDownloading;
    }

    public int getCurrentPercent() {
        return currentPercent;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public long getBytesTotal() {
        return bytesTotal;
    }

    public long getSpeedBytesPerSec() {
        return speedBytesPerSec;
    }

    public int getEtaSeconds() {
        return etaSeconds;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getCurrentChangelog() {
        return currentChangelog;
    }

    public String getCurrentDownloadUrl() {
        return currentDownloadUrl;
    }

    public static File getCachedApk(Context ctx, String version) {
        if (ctx == null) ctx = ApplicationLoader.applicationContext;
        if (ctx == null) return null;
        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = ctx.getFilesDir();

        if (version != null && !version.isEmpty()) {
            File apkFile = new File(dir, "amegram_update_v" + version + ".apk");
            if (apkFile.exists() && apkFile.length() > 20 * 1024 * 1024) {
                return apkFile;
            }
            apkFile = new File(dir, "miogram_update_v" + version + ".apk");
            if (apkFile.exists() && apkFile.length() > 20 * 1024 * 1024) {
                return apkFile;
            }
        } else {
            // Find latest valid cached APK
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile() && f.getName().startsWith("amegram_update_v") && f.getName().endsWith(".apk") && f.length() > 20 * 1024 * 1024) {
                        return f;
                    }
                }
            }
        }
        return null;
    }

    public static boolean isApkCached(Context ctx, String version) {
        return getCachedApk(ctx, version) != null;
    }

    public static boolean deleteCachedApk(Context ctx, String version) {
        File f = getCachedApk(ctx, version);
        if (f != null && f.exists()) {
            return f.delete();
        }
        return false;
    }

    public synchronized boolean startDownload(Context context, String apkUrl, String version, String changelog) {
        final Context ctx = context != null ? context.getApplicationContext() : ApplicationLoader.applicationContext;
        if (ctx == null) return false;

        // Check if APK already cached
        File cached = getCachedApk(ctx, version);
        if (cached != null) {
            this.currentApkFile = cached;
            this.currentVersion = version;
            this.currentPercent = 100;
            notifyComplete(cached);
            promptInstall(ctx, cached);
            return true;
        }

        if (isDownloading) {
            Toast.makeText(ctx, MiogramLocale.get("Оновлення вже завантажується...", "Обновление уже загружается...", "Update is already downloading..."), Toast.LENGTH_SHORT).show();
            MiogramUpdateBar.showGlobalBar();
            return true;
        }

        if (apkUrl == null || apkUrl.isEmpty()) {
            Toast.makeText(ctx, MiogramLocale.get("Посилання на оновлення відсутнє", "Ссылка на обновление отсутствует", "Update URL is missing"), Toast.LENGTH_SHORT).show();
            return false;
        }

        this.currentDownloadUrl = apkUrl;
        this.currentVersion = version;
        this.currentChangelog = changelog;
        this.isDownloading = true;
        this.isCancelled = false;
        this.currentPercent = 0;
        this.bytesDownloaded = 0;
        this.bytesTotal = 0;
        this.speedBytesPerSec = 0;
        this.etaSeconds = 0;

        File dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();

        final File apkFile = new File(dir, "amegram_update_v" + version + ".apk");
        final File partFile = new File(dir, "amegram_update_v" + version + ".apk.part");
        this.currentApkFile = apkFile;

        MiogramUpdateBar.showGlobalBar();
        ensureNotificationChannel(ctx);
        updateNotification(ctx, 0, 0, 0, 0, 0, false);

        downloadThread = new Thread(() -> executeDownload(ctx, apkUrl, apkFile, partFile), "AmegramUpdaterThread");
        downloadThread.start();
        return true;
    }

    private void executeDownload(Context ctx, String initialUrl, File targetApk, File partFile) {
        InputStream in = null;
        FileOutputStream out = null;
        HttpURLConnection conn = null;

        try {
            acquireWakeLock(ctx);

            long existingBytes = 0;
            if (partFile.exists()) {
                existingBytes = partFile.length();
            }

            List<String> candidateUrls = buildCandidateUrls(initialUrl, currentVersion);
            int responseCode = -1;
            boolean isResume = false;

            for (String tryUrl : candidateUrls) {
                if (isCancelled) return;
                String targetUrl = tryUrl;
                int redirects = 0;
                while (redirects < 6) {
                    URL url = new URL(targetUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(false);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile; rv:120.0) Gecko/120.0 Firefox/120.0");

                    // Only send Range header if we have existing bytes from the initial URL
                    if (existingBytes > 0 && tryUrl.equals(initialUrl)) {
                        conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                    }

                    conn.connect();
                    int code = conn.getResponseCode();
                    if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                        String newLoc = conn.getHeaderField("Location");
                        if (newLoc != null) {
                            conn.disconnect();
                            targetUrl = newLoc;
                            redirects++;
                            continue;
                        }
                    }
                    break;
                }

                if (conn == null) continue;
                responseCode = conn.getResponseCode();
                isResume = (responseCode == HttpURLConnection.HTTP_PARTIAL);

                if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_PARTIAL) {
                    break; // Connected
                } else if (responseCode == 416 && existingBytes > 0) {
                    // Range not satisfiable -> clean and restart
                    partFile.delete();
                    existingBytes = 0;
                    conn.disconnect();
                    URL url = new URL(targetUrl);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(30000);
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)");
                    conn.connect();
                    responseCode = conn.getResponseCode();
                    isResume = false;
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        break;
                    }
                }

                // If not successful with this candidate, disconnect and try next
                if (conn != null) {
                    try { conn.disconnect(); } catch (Exception ignored) {}
                    conn = null;
                }
                // When falling back to alternative URLs, discard partial from previous URL
                if (existingBytes > 0) {
                    partFile.delete();
                    existingBytes = 0;
                }
            }

            if (conn == null || (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL)) {
                throw new Exception("HTTP server error " + responseCode);
            }

            long contentLength = conn.getContentLengthLong();
            if (isResume) {
                bytesTotal = existingBytes + contentLength;
                bytesDownloaded = existingBytes;
                out = new FileOutputStream(partFile, true);
            } else {
                bytesTotal = contentLength > 0 ? contentLength : 0;
                bytesDownloaded = 0;
                out = new FileOutputStream(partFile, false);
            }

            in = conn.getInputStream();
            byte[] buffer = new byte[64 * 1024];
            int read;
            long lastNotifyTime = 0;
            long lastSpeedCalcTime = System.currentTimeMillis();
            long lastSpeedBytes = bytesDownloaded;

            while ((read = in.read(buffer)) != -1) {
                if (isCancelled) {
                    break;
                }
                out.write(buffer, 0, read);
                bytesDownloaded += read;

                if (bytesTotal > 0) {
                    currentPercent = (int) ((bytesDownloaded * 100L) / bytesTotal);
                }

                long now = System.currentTimeMillis();
                if (now - lastSpeedCalcTime >= 800) {
                    long bytesSince = bytesDownloaded - lastSpeedBytes;
                    float sec = Math.max(0.1f, (now - lastSpeedCalcTime) / 1000f);
                    speedBytesPerSec = (long) (bytesSince / sec);
                    lastSpeedBytes = bytesDownloaded;
                    lastSpeedCalcTime = now;
                    if (speedBytesPerSec > 0 && bytesTotal > bytesDownloaded) {
                        etaSeconds = (int) ((bytesTotal - bytesDownloaded) / speedBytesPerSec);
                    } else {
                        etaSeconds = 0;
                    }
                }

                if (now - lastNotifyTime > 200) {
                    lastNotifyTime = now;
                    postProgress(currentPercent, bytesDownloaded, bytesTotal, speedBytesPerSec, etaSeconds);
                    updateNotification(ctx, currentPercent, bytesDownloaded, bytesTotal, speedBytesPerSec, etaSeconds, false);
                }
            }

            out.flush();

            if (isCancelled) {
                isDownloading = false;
                cancelNotification(ctx);
                return;
            }

            if (bytesTotal > 0 && partFile.length() < bytesTotal) {
                throw new Exception("Incomplete download (" + partFile.length() + "/" + bytesTotal + " bytes)");
            }

            if (targetApk.exists()) targetApk.delete();
            if (!partFile.renameTo(targetApk)) {
                AndroidUtilities.copyFile(partFile, targetApk);
                partFile.delete();
            }

            isDownloading = false;
            currentPercent = 100;
            currentApkFile = targetApk;

            updateNotification(ctx, 100, bytesTotal, bytesTotal, 0, 0, true);

            mainHandler.post(() -> {
                notifyComplete(targetApk);
                promptInstall(ctx, targetApk);
            });

        } catch (Exception e) {
            FileLog.e(e);
            if (!isCancelled) {
                isDownloading = false;
                final String err = e.getMessage() != null ? e.getMessage() : "Download error";
                cancelNotification(ctx);
                mainHandler.post(() -> {
                    Toast.makeText(ctx, MiogramLocale.get("Помилка завантаження", "Ошибка загрузки", "Download error") + ": " + err, Toast.LENGTH_SHORT).show();
                    notifyError(err);
                });
            }
        } finally {
            releaseWakeLock();
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (conn != null) conn.disconnect(); } catch (Exception ignored) {}
        }
    }

    private List<String> buildCandidateUrls(String initialUrl, String version) {
        List<String> list = new ArrayList<>();
        if (initialUrl != null && !initialUrl.trim().isEmpty()) {
            String u = initialUrl.trim();
            list.add(u);

            if (u.contains("(") || u.contains(")")) {
                String dotUrl = u.replace("(", ".").replace(")", "");
                if (!list.contains(dotUrl)) list.add(dotUrl);
                String dotUrl2 = u.replaceAll("\\(([^)]+)\\)", ".$1");
                if (!list.contains(dotUrl2)) list.add(dotUrl2);
            }
        }

        String tag = null;
        if (version != null && !version.trim().isEmpty()) {
            tag = version.trim();
            if (!tag.startsWith("v") && !tag.startsWith("V")) {
                tag = "v" + tag;
            }
        } else if (initialUrl != null && initialUrl.contains("/releases/download/")) {
            try {
                String sub = initialUrl.substring(initialUrl.indexOf("/releases/download/") + 19);
                if (sub.contains("/")) {
                    tag = sub.substring(0, sub.indexOf("/"));
                }
            } catch (Exception ignored) {}
        }

        if (tag != null && !tag.isEmpty()) {
            try {
                String dynamicUrl = MiogramUpdater.fetchApkUrlFromExpandedAssets(tag);
                if (dynamicUrl != null && !dynamicUrl.isEmpty() && !list.contains(dynamicUrl)) {
                    list.add(Math.min(1, list.size()), dynamicUrl);
                }
            } catch (Throwable ignored) {}

            String verNum = tag.replace("v", "").replace("V", "").trim();
            String runNum = verNum.contains(".") ? verNum.substring(verNum.lastIndexOf('.') + 1) : verNum;

            int calculatedVerCode = 0;
            try {
                calculatedVerCode = 126100 + Integer.parseInt(runNum);
            } catch (Exception ignored) {}

            if (calculatedVerCode > 0) {
                String p1 = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + "." + calculatedVerCode + ".apk";
                String p2 = "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + "(" + calculatedVerCode + ").apk";
                if (!list.contains(p1)) list.add(p1);
                if (!list.contains(p2)) list.add(p2);
            }

            String[] genericPatterns = new String[] {
                    "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram-" + tag + ".apk",
                    "https://github.com/fuckramochka/amegram/releases/download/" + tag + "/amegram.apk",
                    "https://github.com/fuckramochka/amegram/releases/latest/download/amegram.apk"
            };
            for (String p : genericPatterns) {
                if (!list.contains(p)) list.add(p);
            }
        }

        return list;
    }

    private void postProgress(int percent, long downloaded, long total, long speed, int eta) {
        mainHandler.post(() -> notifyProgress(percent, downloaded, total, speed, eta));
    }

    public synchronized void cancelDownload() {
        if (!isDownloading) return;
        isCancelled = true;
        isDownloading = false;
        if (downloadThread != null) {
            downloadThread.interrupt();
            downloadThread = null;
        }
        releaseWakeLock();
        cancelNotification(ApplicationLoader.applicationContext);
        MiogramUpdateBar.hideGlobalBar();
    }

    private synchronized void notifyProgress(int percent, long down, long total, long speed, int eta) {
        for (DownloadListener l : listeners) {
            try {
                l.onProgressDetailed(percent, down, total, speed, eta);
            } catch (Exception ignored) {}
        }
    }

    private synchronized void notifyComplete(File apk) {
        for (DownloadListener l : listeners) {
            try { l.onComplete(apk); } catch (Exception ignored) {}
        }
        MiogramUpdateBar.hideGlobalBar();
    }

    private synchronized void notifyError(String error) {
        for (DownloadListener l : listeners) {
            try { l.onError(error); } catch (Exception ignored) {}
        }
        MiogramUpdateBar.hideGlobalBar();
    }

    private void acquireWakeLock(Context ctx) {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "amegram:updater_download");
                    wakeLock.setReferenceCounted(false);
                }
            }
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(20 * 60 * 1000L); // 20 minutes max
            }
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Throwable ignored) {}
    }

    private static void ensureNotificationChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && ctx != null) {
            try {
                NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm != null && nm.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
                    NotificationChannel ch = new NotificationChannel(
                            NOTIFICATION_CHANNEL_ID,
                            MiogramLocale.get("Оновлення додатку", "Обновления приложения", "App Updates"),
                            NotificationManager.IMPORTANCE_LOW
                    );
                    ch.setDescription(MiogramLocale.get("Сповіщення про завантаження оновлень", "Уведомления о загрузке обновлений", "Update download notifications"));
                    ch.setShowBadge(false);
                    nm.createNotificationChannel(ch);
                }
            } catch (Throwable ignore) {}
        }
    }

    private void updateNotification(Context ctx, int percent, long down, long total, long speed, int eta, boolean isComplete) {
        if (ctx == null) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;

            NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(MiogramLocale.format("Amegram v%s", "Amegram v%s", "Amegram v%s", currentVersion))
                    .setOnlyAlertOnce(true);

            if (isComplete) {
                builder.setContentText(MiogramLocale.get("Завантаження завершено • Натисніть для встановлення", "Загрузка завершена • Нажмите для установки", "Download complete • Tap to install"))
                        .setProgress(0, 0, false)
                        .setOngoing(false)
                        .setAutoCancel(true);

                if (currentApkFile != null && currentApkFile.exists()) {
                    Intent installIntent = getInstallIntent(ctx, currentApkFile);
                    PendingIntent pi = PendingIntent.getActivity(ctx, 0, installIntent, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
                    builder.setContentIntent(pi);
                }
            } else {
                String downFormatted = AndroidUtilities.formatFileSize(down);
                String totalFormatted = total > 0 ? AndroidUtilities.formatFileSize(total) : "...";
                String speedFormatted = speed > 0 ? AndroidUtilities.formatFileSize(speed) + "/s" : "";
                String subtitle = MiogramLocale.format("Завантаження: %d%% (%s / %s)", "Загрузка: %d%% (%s / %s)", "Downloading: %d%% (%s / %s)", percent, downFormatted, totalFormatted);
                if (!speedFormatted.isEmpty()) {
                    subtitle += " • " + speedFormatted;
                }

                builder.setContentText(subtitle)
                        .setProgress(100, Math.max(0, Math.min(100, percent)), total <= 0)
                        .setOngoing(true);
            }

            nm.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable ignore) {}
    }

    private static void cancelNotification(Context ctx) {
        if (ctx == null) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID);
            }
        } catch (Throwable ignore) {}
    }

    public static Intent getInstallIntent(Context ctx, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri uri;
        if (Build.VERSION.SDK_INT >= 24) {
            uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".provider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            uri = Uri.fromFile(file);
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    public static void promptInstall(Context ctx, File file) {
        if (file == null || !file.exists()) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.getPackageManager().canRequestPackageInstalls()) {
                    Intent permIntent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    permIntent.setData(Uri.parse("package:" + ctx.getPackageName()));
                    permIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(permIntent);
                    Toast.makeText(ctx, MiogramLocale.get("Увімкніть дозвіл на встановлення додатків для Amegram", "Включите разрешение на установку приложений для Amegram", "Enable install apps permission for Amegram"), Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Android 12+ (API 31+) Unattended Background Session
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (tryUnattendedInstall(ctx, file)) {
                    return;
                }
            }

            Intent intent = getInstallIntent(ctx, file);
            ctx.startActivity(intent);
        } catch (Exception e) {
            FileLog.e(e);
            Toast.makeText(ctx, MiogramLocale.get("Помилка встановлення", "Ошибка установки", "Install error") + (e.getMessage() != null ? ": " + e.getMessage() : ""), Toast.LENGTH_LONG).show();
        }
    }

    private static boolean tryUnattendedInstall(Context ctx, File file) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false;
        }
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
            if (info != null && !ctx.getPackageName().equals(info.packageName)) {
                return false;
            }
            PackageInstaller packageInstaller = pm.getPackageInstaller();
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(ctx.getPackageName());
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);

            int sessionId = packageInstaller.createSession(params);
            PackageInstaller.Session session = packageInstaller.openSession(sessionId);

            try (OutputStream out = session.openWrite("amegram_apk", 0, file.length());
                 InputStream in = new FileInputStream(file)) {
                byte[] buffer = new byte[65536];
                int c;
                while ((c = in.read(buffer)) != -1) {
                    out.write(buffer, 0, c);
                }
                session.fsync(out);
            }

            Intent intent = new Intent(ctx.getPackageName() + MiogramInstallReceiver.ACTION_INSTALL_STATUS);
            intent.setPackage(ctx.getPackageName());
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    ctx,
                    sessionId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
            );
            session.commit(pendingIntent.getIntentSender());
            session.close();
            FileLog.d("MiogramDownloadManager: committed unattended session " + sessionId);
            Toast.makeText(ctx, MiogramLocale.get("Оновлення встановлюється...", "Обновление устанавливается...", "Update is installing..."), Toast.LENGTH_SHORT).show();
            return true;
        } catch (Throwable t) {
            FileLog.e("MiogramDownloadManager: unattended install fallback to intent", t);
            return false;
        }
    }

    public static void cleanOldUpdateApks(Context context) {
        cleanOldUpdateApks(context, false);
    }

    public static void cleanOldUpdateApks(Context context, boolean forceAll) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                Context ctx = context != null ? context.getApplicationContext() : ApplicationLoader.applicationContext;
                if (ctx == null) return;

                PackageManager pm = ctx.getPackageManager();
                int currentVersionCode = 0;
                String currentVersionName = BuildVars.BUILD_VERSION_STRING;
                try {
                    PackageInfo myInfo = pm.getPackageInfo(ctx.getPackageName(), 0);
                    currentVersionCode = myInfo.versionCode;
                    if (myInfo.versionName != null) {
                        currentVersionName = myInfo.versionName;
                    }
                } catch (Throwable ignore) {}

                File[] searchDirs = new File[]{
                        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                        ctx.getFilesDir(),
                        ctx.getExternalCacheDir(),
                        ctx.getCacheDir()
                };

                MiogramDownloadManager dm = instance;
                File activeApk = (dm != null && dm.isDownloading) ? dm.currentApkFile : null;

                for (File dir : searchDirs) {
                    if (dir == null || !dir.exists() || !dir.isDirectory()) continue;
                    File[] files = dir.listFiles();
                    if (files == null) continue;

                    for (File file : files) {
                        if (file == null || !file.isFile()) continue;
                        String name = file.getName().toLowerCase();

                        // ONLY touch files explicitly named as Amegram / Miogram update files!
                        boolean isOurUpdateFile = (name.startsWith("amegram_update_") || name.startsWith("miogram_update_"))
                                && (name.endsWith(".apk") || name.endsWith(".part"));

                        if (!isOurUpdateFile) {
                            continue;
                        }

                        // Skip active download file
                        if (activeApk != null && (file.equals(activeApk) || file.getName().startsWith(activeApk.getName()))) {
                            continue;
                        }

                        if (forceAll) {
                            try { file.delete(); } catch (Exception ignored) {}
                            continue;
                        }

                        // Abandoned .part files older than 3 hours
                        if (name.endsWith(".part")) {
                            if (System.currentTimeMillis() - file.lastModified() > 3 * 60 * 60 * 1000L) {
                                try { file.delete(); } catch (Exception ignored) {}
                            }
                            continue;
                        }

                        // Check .apk age and version
                        boolean shouldDelete = false;
                        try {
                            PackageInfo archiveInfo = pm.getPackageArchiveInfo(file.getAbsolutePath(), 0);
                            if (archiveInfo != null) {
                                if (archiveInfo.packageName == null || archiveInfo.packageName.equals(ctx.getPackageName())) {
                                    if (currentVersionCode > 0 && archiveInfo.versionCode <= currentVersionCode) {
                                        shouldDelete = true;
                                    } else if (currentVersionName != null && !MiogramUpdater.isNewerVersion(currentVersionName, archiveInfo.versionName, null, null)) {
                                        shouldDelete = true;
                                    }
                                }
                            } else {
                                // Corrupted APK
                                shouldDelete = true;
                            }
                        } catch (Throwable t) {
                            shouldDelete = true;
                        }

                        if (shouldDelete) {
                            try { file.delete(); } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Throwable t) {
                FileLog.e("MiogramDownloadManager: error during cleanOldUpdateApks", t);
            }
        });
    }
}
