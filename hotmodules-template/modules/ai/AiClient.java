package com.amegram.mods.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Gemini-генерація тексту. Тільки JDK + org.json.
 * Портовано з MiogramAiService (generateContent-гілка).
 */
public final class AiClient {

    private AiClient() {
    }

    public static String generate(String apiKey, String model,
                                  String systemPrompt, String userText) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("Нема API-ключа. Вкажи його в налаштуваннях модуля.");
        }
        if (model == null || model.isEmpty()) model = "gemini-2.5-flash";
        String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        JSONObject sys = new JSONObject();
        sys.put("text", systemPrompt != null ? systemPrompt : "You are a helpful assistant.");

        JSONObject sysContent = new JSONObject();
        JSONArray sysParts = new JSONArray();
        sysParts.put(sys);
        sysContent.put("parts", sysParts);

        JSONObject body = new JSONObject();
        body.put("system_instruction", sysContent);
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        JSONObject p = new JSONObject();
        p.put("text", userText);
        parts.put(p);
        content.put("parts", parts);
        JSONArray contents = new JSONArray();
        contents.put(content);
        body.put("contents", contents);
        body.put("generationConfig", new JSONObject().put("maxOutputTokens", 1024));

        HttpURLConnection conn = (HttpURLConnection) new URL(endpoint).openConnection();
        conn.setConnectTimeout(20000);
        conn.setReadTimeout(120000);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] bytes = body.toString().getBytes("UTF-8");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
        }
        int code = conn.getResponseCode();
        InputStream in = code >= 200 && code < 300
                ? conn.getInputStream() : conn.getErrorStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        in.close();
        conn.disconnect();
        JSONObject resp = new JSONObject(new String(out.toByteArray(), "UTF-8"));
        if (code < 200 || code >= 300) {
            String msg = resp.has("error")
                    ? resp.optJSONObject("error").optString("message", "HTTP " + code)
                    : "HTTP " + code;
            throw new IllegalStateException(msg);
        }
        JSONArray candidates = resp.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new IllegalStateException("Порожня відповідь");
        }
        JSONObject rc = candidates.optJSONObject(0).optJSONObject("content");
        JSONArray rp = rc != null ? rc.optJSONArray("parts") : null;
        if (rp == null || rp.length() == 0) throw new IllegalStateException("Нема тексту");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rp.length(); i++) {
            sb.append(rp.optJSONObject(i).optString("text", ""));
        }
        return sb.toString().trim();
    }
}
