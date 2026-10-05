package app.amegram.hot.api;

/** Контракт модуля розшифровки: файл -> текст. Реєструється під "transcribe". */
public interface HotTranscribe {

    interface Callback {
        void onResult(String text);
        void onError(String error);
    }

    /** audioPath — локальний файл голосового/відео. Виклики — фон, колбек — будь-який потік. */
    void transcribe(String audioPath, boolean isVideo, Callback callback);
}
