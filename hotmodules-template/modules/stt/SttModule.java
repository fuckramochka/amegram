package com.amegram.mods.stt;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;
import app.amegram.hot.api.HotTranscribe;

/** ШІ-розшифровка голосових/кружечків. Портовано з TranscribeHelper. */
public class SttModule implements HotModule, HotTranscribe {

    private HotHost host;

    @Override
    public String moduleId() {
        return "stt";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.TRANSCRIBE, this);
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.TRANSCRIBE);
        host = null;
    }

    @Override
    public void transcribe(String audioPath, boolean isVideo, Callback callback) {
        HotHost h = host;
        if (h == null) {
            callback.onError("модуль вимкнено");
            return;
        }
        boolean useOpenAi = h.getBool("use_openai", false);
        String key = useOpenAi ? h.getString("openai_key", "") : h.getString("gemini_key", "");
        String model = h.getString("model", "");
        String base = h.getString("openai_base", "");
        try {
            String text = SttClient.transcribe(audioPath, isVideo, useOpenAi, base, key, model);
            callback.onResult(text);
        } catch (Exception e) {
            callback.onError(e.getMessage() != null ? e.getMessage() : "помилка");
        }
    }

    @Override
    public boolean hasSettings() {
        return true;
    }

    @Override
    public String settingsTitle() {
        return "Розшифровка";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        HotHost h = host;
        boolean useOpenAi = h != null && h.getBool("use_openai", false);
        rows.add(HotRow.header("Провайдер"));
        rows.add(HotRow.switchRow("use_openai", "OpenAI-сумісний",
                "Вимкнено = Gemini (за замовчуванням)", useOpenAi));
        if (useOpenAi) {
            rows.add(HotRow.inputRow("openai_base", "Base URL",
                    h != null ? h.getString("openai_base", "https://api.openai.com/v1") : ""));
            rows.add(HotRow.inputRow("openai_key", "API-ключ",
                    h != null ? h.getString("openai_key", "") : ""));
            rows.add(HotRow.inputRow("model", "Модель",
                    h != null ? h.getString("model", "whisper-1") : ""));
        } else {
            rows.add(HotRow.inputRow("gemini_key", "Gemini API-ключ (BYOK)",
                    h != null ? h.getString("gemini_key", "") : ""));
            rows.add(HotRow.inputRow("model", "Модель",
                    h != null ? h.getString("model", "gemini-2.5-flash") : ""));
        }
        rows.add(HotRow.info("Кнопка розшифровки на голосовому починає вести через модуль. "
                + "Ключ — твій (Google AI Studio)."));
    }
}
