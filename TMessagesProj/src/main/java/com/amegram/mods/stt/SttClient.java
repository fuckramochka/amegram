package com.amegram.mods.stt;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Розшифровка голосових: Gemini (за замовчуванням, BYOK) або OpenAI-сумісний
 * ендпоінт. Тільки JDK + android + org.json, без okhttp/gson.
 * Портовано з TranscribeHelper (Gemini-гілка) + AmegramLocalTranscriber-ідея.
 */
public final class SttClient {

    private SttClient() {
    }

    public static String transcribe(String audioPath, boolean isVideo,
                                    boolean useOpenAi, String openAiBase,
                                    String apiKey, String model) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Нема API-ключа. Вкажи його в налаштуваннях модуля.");
        }
        File f = new File(audioPath);
        if (!f.exists()) throw new IllegalStateException("Нема аудіофайлу");
        if (useOpenAi) {
            return openAiTranscribe(f, openAiBase, apiKey, model);
        }
        return geminiTranscribe(f, isVideo, apiKey, model);
    }

    private static String geminiTranscribe(File audio, boolean isVideo,
                                          String key, String model) throws Exception {
        if (model == null || model.isEmpty()) model = "gemini-2.5-flash";
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + key;

        JSONObject part1 = new JSONObject();
        part1.put("text", isVideo
                ? "Transcribe the speech from this video verbatim. Return only the transcription."
                : "Transcribe this voice message verbatim. Return only the transcription.");

        JSONObject inline = new JSONObject();
        inline.put("mime_type", "audio/ogg");
        inline.put("data", base64(audio));

        JSONObject part2 = new JSONObject();
        part2.put("inline_data", inline);

        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(part1);
        parts.put(part2);
        content.put("parts", parts);

        JSONObject body = new JSONObject();
        JSONArray contents = new JSONArray();
        contents.put(content);
        body.put("contents", contents);

        JSONObject resp = postJson(endpoint, body.toString(), null);
        return extractGeminiText(resp);
    }

    private static String extractGeminiText(JSONObject resp) throws Exception {
        if (resp.has("error")) {
            throw new IllegalStateException(resp.optJSONObject("error").optString("message", "gemini error"));
        }
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IllegalStateException("Порожня відповідь");
        }
        JSONObject content = candidates.optJSONObject(0).optJSONObject("content");
        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
        if (parts == null || parts.length() == 0) {
            throw new IllegalStateException("Нема тексту");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            sb.append(parts.optJSONObject(i).optString("text", ""));
        }
        return sb.toString().trim();
    }

    private static String openAiTranscribe(File audio, String base,
                                          String key, String model) throws Exception {
        if (base == null || base.isEmpty()) base = "https://api.openai.com/v1";
        if (model == null || model.isEmpty()) model = "whisper-1";
        String boundary = "----amegram" + System.currentTimeMillis();
        HttpURLConnection conn = (HttpURLConnection)
                new URL(base + "/audio/transcriptions").openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        try (OutputStream out = conn.getOutputStream()) {
            writeField(out, boundary, "model", model);
            writeFile(out, boundary, "file", audio);
            out.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
        }
        JSONObject resp = readJson(conn);
        String text = resp.optString("text", "");
        if (text.isEmpty()) throw new IllegalStateException("Порожня відповідь");
        return text;
    }

    private static void writeField(OutputStream out, String boundary,
                                   String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes("UTF-8"));
        out.write((value + "\r\n").getBytes("UTF-8"));
    }

    private static void writeFile(OutputStream out, String boundary,
                                  String name, File file) throws Exception {
        out.write(("--" + boundary + "\r\n").getBytes("UTF-8"));
        out.write(("Content-Disposition: form-data; name=\"" + name
                + "\"; filename=\"" + file.getName() + "\"\r\n").getBytes("UTF-8"));
        out.write("Content-Type: audio/ogg\r\n\r\n".getBytes("UTF-8"));
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
        out.write("\r\n".getBytes("UTF-8"));
    }

    private static JSONObject postJson(String url, String body,
                                       String bearer) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer " + bearer);
        byte[] bytes = body.getBytes("UTF-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
        return readJson(conn);
    }

    private static JSONObject readJson(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        String s = new String(out.toByteArray(), "UTF-8");
        if (code < 200 || code >= 300) {
            try {
                JSONObject err = new JSONObject(s);
                String msg = err.optString("message", err.optString("error", s));
                throw new IllegalStateException("HTTP " + code + ": " + msg);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("HTTP " + code);
            }
        }
        return new JSONObject(s);
    }

    private static String base64(File file) throws Exception {
        try (InputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[64 * 1024];
            int n;
            long total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > 24 * 1024 * 1024) {
                    throw new IllegalStateException("Файл завеликий (>24МБ)");
                }
                out.write(buf, 0, n);
            }
            return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
        }
    }
}
