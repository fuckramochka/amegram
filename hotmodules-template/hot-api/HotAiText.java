package app.amegram.hot.api;

/** Контракт AI-модуля: генерація тексту. Реєструється під "ai_text". */
public interface HotAiText {

    interface Callback {
        void onResult(String text);
        void onError(String error);
    }

    void generate(String systemPrompt, String userText, Callback callback);
}
