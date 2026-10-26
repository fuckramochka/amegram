package app.amegram.hot.api;

/**
 * Имена нативных сервисов, которыми модули расширяют сам Yumigram.
 * Модуль отдаёт реализацию через HotHost.registerService(),
 * ядро забирает через HotModulesManager.getService() и использует
 * вместо стокового поведения. Нет сервиса — работает сток.
 */
public final class HotServices {

    private HotServices() {
    }

    /** {@link HotTranslator}: встроенный переводчик. */
    public static final String TRANSLATOR = "translator";

    /** {@link HotPlayer}: додатковий пошук музики, відкривається з MD3. */
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

    /** Optional file destination and organization rules. */
    public static final String FILE_ORGANIZATION = "file_organization";

    /** {@link HotUi}: стиль інтерфейсу (Yougram Expressive ↔ Classic) на льоту. */
    public static final String UI = "ui";

    /** {@link HotSettingsUi}: інтерфейс налаштувань Yumigram (Внутрішній світ). */
    public static final String SETTINGS_UI = "settings_ui";

    /** Ігрові статуси та Rich Presence (Steam, Discord, Roblox). */
    public static final String GAMING = "gaming";

    /** Стрічка новин і каналів у стилі соцмереж. */
    public static final String FEED = "feed";

    /** Канбан-дошка та менеджер задач. */
    public static final String KANBAN = "kanban";

    /** Кастомізація профілю, бейджі та банери AmeProfile. */
    public static final String PROFILE = "profile";

    /** {@link HotBadges}: системний модуль бейджів та статусів спільноти. */
    public static final String BADGES = "badges";
}
