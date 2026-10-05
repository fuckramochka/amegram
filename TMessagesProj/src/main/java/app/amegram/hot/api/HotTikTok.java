package app.amegram.hot.api;

/** Контракт TikTok-модуля: відкрити посилання у внутрішньому плеєрі. */
public interface HotTikTok {

    /** true = модуль обробив (ядро далі не йде). */
    boolean openUrl(String url);
}
