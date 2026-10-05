package app.amegram.hot;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

/**
 * Скачивание .hmod с прогрессом + проверка sha256.
 * Только https, таймауты короткие, докачка не нужна (модули маленькие).
 */
public final class HotDownloader {

    public interface Progress {
        void onProgress(long downloaded, long total);
    }

    private HotDownloader() {
    }

    public static File download(String url, File destTmp, Progress progress) throws Exception {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("only https allowed");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Amegram-HotModules/1.0");
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("http " + code);
            }
            long total = conn.getContentLengthLong();
            File parent = destTmp.getParentFile();
            if (parent != null) parent.mkdirs();
            try (InputStream in = new BufferedInputStream(conn.getInputStream());
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
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    public static String get(String url) throws Exception {
        if (url == null || !url.startsWith("https://")) {
            throw new IllegalArgumentException("only https allowed");
        }
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Amegram-HotModules/1.0");
            conn.connect();
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("http " + code);
            }
            try (InputStream in = new BufferedInputStream(conn.getInputStream())) {
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[16 * 1024];
                int n;
                while ((n = in.read(buf)) != -1) {
                    sb.append(new String(buf, 0, n, "UTF-8"));
                    if (sb.length() > 512 * 1024) break;
                }
                return sb.toString();
            }
        } finally {
            if (conn != null) conn.disconnect();
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
