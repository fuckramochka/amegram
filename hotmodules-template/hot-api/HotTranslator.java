package app.amegram.hot.api;

/**
 * Пример контракта нативного сервиса: встроенный переводчик.
 * Модуль реализует и регистрирует под {@link HotServices#TRANSLATOR}.
 */
public interface HotTranslator {

    String id();

    /** Перевести text с fromLang на toLang ("auto", "ru", "en"...). Бросает — хост покажет ошибку. */
    String translate(String text, String fromLang, String toLang) throws Exception;
}
