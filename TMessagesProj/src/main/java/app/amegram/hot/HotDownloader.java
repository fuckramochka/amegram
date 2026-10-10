package app.amegram.hot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Скачивание .hmod с прогрессом + проверка sha256 через OkHttp.
 * Поддерживает редиректы, современные протоколы TLS, таймауты и прогресс.
 */
public final class HotDownloader {

    public interface Progress {
        void onProgress(long downloaded, long total);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    private HotDownloader() {
    }

    public static File download(String url, File destTmp, Progress progress) throws Exception {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("only https allowed");
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) Amegram-HotModules/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            long total = body.contentLength();
            File parent = destTmp.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = body.byteStream();
                 OutputStream out = new FileOutputStream(destTmp)) {
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int n;
                while ((n = in.read(buf)) != -1) {
                    out.write(buf, 0, n);
                    done += n;
                    if (progress != null) progress.onProgress(done, total);
                }
            }
            return destTmp;
        }
    }

    public static String get(String url) throws Exception {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("only https allowed");
        }
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) Amegram-HotModules/1.0")
                .build();
        try (Response response = client.newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IllegalStateException("HTTP " + response.code());
            }
            return body.string();
        }
    }

    public static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
