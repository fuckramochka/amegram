package com.amegram.mods.ai;

import android.content.Context;

import java.util.List;

import app.amegram.hot.api.HotAiText;
import app.amegram.hot.api.HotHost;
import app.amegram.hot.api.HotModule;
import app.amegram.hot.api.HotRow;
import app.amegram.hot.api.HotServices;

/**
 * ШІ-супутник та функції штучного інтелекту:
 * - Підтримка Gemini Nano (локально на пристрої без відправки на сервер)
 * - Сервіси Perplexity, Gemini Cloud, OpenAI та Responses API
 * - Заміна функцій Telegram AI без Telegram Premium:
 *   * Редагування та виправлення чернеток
 *   * Переказ довгих повідомлень та постів
 * - Історія спілкування та персона Ame / KAngel
 */
public class AiModule implements HotModule, HotAiText {

    private HotHost host;

    private static final String PERSONA_AME =
            "You are Ame, a playful neon companion inside a Telegram client. "
            + "Answer briefly, warmly, in the user's language (Ukrainian/Russian/English).";
    private static final String PERSONA_KANGEL =
            "You are KAngel, a calm celestial companion inside a Telegram client. "
            + "Answer briefly and kindly, in the user's language (Ukrainian/Russian/English).";

    @Override
    public String moduleId() {
        return "ai";
    }

    @Override
    public void onAttach(Context appContext, HotHost host) {
        this.host = host;
        host.registerService(HotServices.AI_TEXT, this);
    }

    @Override
    public void onDetach() {
        if (host != null) host.unregisterService(HotServices.AI_TEXT);
        host = null;
    }

    private String persona() {
        if (host != null && host.getBool("persona_kangel", false)) return PERSONA_KANGEL;
        return PERSONA_AME;
    }

    public boolean isGeminiNanoEnabled() {
        return host != null && host.getBool("gemini_nano", false);
    }

    public boolean isReplaceEditor() {
        return host != null && host.getBool("replace_editor", true);
    }

    public boolean isReplaceSummaries() {
        return host != null && host.getBool("replace_summaries", true);
    }

    @Override
    public void generate(String systemPrompt, String userText, Callback callback) {
        HotHost h = host;
        if (h == null) {
            callback.onError("модуль вимкнено");
            return;
        }
        try {
            if ("nano".equals(provider(h))) {
                callback.onError("Nano-режим: дочекайся завантаження on-device моделі");
                return;
            }
            String text = AiClient.generate(h.getString("gemini_key", ""),
                    "gemini-3.5-flash-lite",
                    systemPrompt != null ? systemPrompt : persona(), userText);
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
        return "Штучний інтелект (ШІ)";
    }

    public static String provider(HotHost h) {
        String p = h != null ? h.getString("provider", "api") : "api";
        return "nano".equals(p) ? "nano" : "api";
    }

    public static String personaName(HotHost h) {
        boolean kangel = h != null && h.getBool("persona_kangel", false);
        return kangel ? "KAngel" : "Ame";
    }

    @Override
    public void fillSettings(List<HotRow> rows) {
        HotHost h = host;
        String prov = provider(h);

        rows.add(HotRow.header("Telegram AI без Premium"));
        boolean replaceEd = h == null || h.getBool("replace_editor", true);
        rows.add(HotRow.switchRow("replace_editor", "Робота з чернетками (Editor)",
                "Переклад, стилізація та виправлення чернеток через свій ШІ", replaceEd));

        boolean replaceSum = h == null || h.getBool("replace_summaries", true);
        rows.add(HotRow.switchRow("replace_summaries", "Переказ повідомлень (Summaries)",
                "Швидкий переказ довгих публікацій у каналах без Premium", replaceSum));

        rows.add(HotRow.header("Провайдер: тільки Nano або Gemini API"));
        rows.add(HotRow.button("provider", "Провайдер: " + ("nano".equals(prov) ? "Gemini Nano (на пристрої)" : "Gemini API (3.5 Flash-Lite)"),
                "nano".equals(prov) ? "Локально, без ключа. Качає Nano + STT-модель." : "Хмара Google. Потрібен ключ AI Studio."));

        if ("nano".equals(prov)) {
            rows.add(HotRow.info("Nano працює офлайн. При першому запуску докачається Nano-модель"
                    + " і модель розшифровки голосу (якщо чип не тягне STT). Ключ не потрібен."));
            rows.add(HotRow.button("download_nano", "Завантажити моделі Nano",
                    "Nano + голосова модель за потреби"));
        } else {
            rows.add(HotRow.inputRow("gemini_key", "API-ключ Gemini",
                    h != null ? h.getString("gemini_key", "") : ""));
            rows.add(HotRow.button("get_key", "Отримати ключ",
                    "Відкриє AI Studio (aistudio.google.com)"));
            rows.add(HotRow.info("Модель фіксована: gemini-3.5-flash-lite. Тільки Gemini API, без сторонніх."));
        }

        rows.add(HotRow.header("Персона супутника"));
        rows.add(HotRow.button("persona", "Персона: " + personaName(h),
                "Ame — неонова, KAngel — спокійна. Натисни для вибору."));

        boolean history = h != null && h.getBool("save_ai_history", true);
        rows.add(HotRow.switchRow("save_ai_history", "Зберігати історію чату",
                "Збереження діалогу з ШІ між перезапусками", history));

        rows.add(HotRow.button("open_chat", "Відкрити ШІ-чат", ""));
    }

    @Override
    public void onSettingsToggle(String key, boolean value) {
        if (host != null) {
            host.setBool(key, value);
        }
    }

    @Override
    public void onSettingsAction(String rowId) {
        if (host == null) return;
        if ("open_chat".equals(rowId)) {
            host.openModuleScreen("companion");
        } else if ("get_key".equals(rowId)) {
            try {
                android.content.Context ctx = host.context();
                android.content.Intent it = new android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://aistudio.google.com/app/apikey"));
                it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(it);
            } catch (Throwable ignore) {
            }
        } else if ("provider".equals(rowId)) {
            cycleProvider();
        } else if ("persona".equals(rowId)) {
            host.setBool("persona_kangel", !"KAngel".equals(personaName(host)));
        } else if ("download_nano".equals(rowId)) {
            host.toast("Nano-моделі докачаються при першому запуску Nano-режиму (потрібен перезапуск чату)");
        }
    }

    private void cycleProvider() {
        if (host == null) return;
        String cur = provider(host);
        host.setString("provider", "nano".equals(cur) ? "api" : "nano");
    }

    @Override
    public void fillHubRows(List<HotRow> rows) {
        rows.add(HotRow.button("ai_chat", "Штучний інтелект", "ШІ-чат та помічник"));
    }

    @Override
    public void onHubAction(String rowId) {
        if ("ai_chat".equals(rowId) && host != null) {
            host.openModuleScreen("companion");
        }
    }

    @Override
    public Object createScreen(String screenId) {
        if (!"companion".equals(screenId)) return null;
        boolean kangel = host != null && host.getBool("persona_kangel", false);
        return new CompanionFragment(text -> {
            final String[] out = new String[1];
            final Exception[] err = new Exception[1];
            Thread t = new Thread(() -> {
                try {
                    out[0] = AiClient.generate(
                            host.getString("gemini_key", ""),
                            "gemini-3.5-flash-lite",
                            persona(), text);
                } catch (Exception e) {
                    err[0] = e;
                }
            });
            t.start();
            try {
                t.join(120000);
            } catch (InterruptedException e) {
                throw new RuntimeException("перервано");
            }
            if (err[0] != null) throw err[0];
            if (out[0] == null) throw new RuntimeException("порожньо");
            return out[0];
        }, kangel ? "KAngel" : "Ame");
    }
}
