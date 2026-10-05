package app.amegram.hot.api;

/**
 * Имена нативных сервисов, которыми модули расширяют сам Амэграм.
 * Модуль отдаёт реализацию через HotHost.registerService(),
 * ядро забирает через HotModulesManager.getService() и использует
 * вместо стокового поведения. Нет сервиса — работает сток.
 */
public final class HotServices {

    private HotServices() {
    }

    /** {@link HotTranslator}: встроенный переводчик. */
    public static final String TRANSLATOR = "translator";

    /** Произвольная фабрика/провайдер от модуля, контракт — в доке модуля. */
    public static final String PLAYER = "player";

    /** {@link HotGhost}: режим привида. */
    public static final String GHOST = "ghost";

    /** {@link HotTranscribe}: розшифровка голосових. */
    public static final String TRANSCRIBE = "transcribe";

    /** {@link HotAiText}: генерація тексту. */
    public static final String AI_TEXT = "ai_text";

    /** {@link HotTikTok}: відкриття TikTok-посилань. */
    public static final String TIKTOK = "tiktok";

    /** {@link HotVault}: заливка файлів у хмарне сховище. */
    public static final String VAULT = "vault";

    /** {@link HotExperimental}: експериментальні налаштування. */
    public static final String EXPERIMENTAL = "experimental";

    /** {@link HotAutomation}: автоматизація та фонові задачі. */
    public static final String AUTOMATION = "automation";
}
