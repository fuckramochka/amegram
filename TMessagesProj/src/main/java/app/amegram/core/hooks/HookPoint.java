package app.amegram.core.hooks;

/**
 * Рівно 10 точок врізки в клієнт. Більше — тільки через зміну плану.
 * Ловиться греп-тестом: кількість значень enum == кількість викликів
 * HookRegistry.emit у org.telegram.
 */
public enum HookPoint {
    APP_CREATE,
    LAUNCH_CREATED,
    PRE_REQUEST,
    POST_RESPONSE,
    CHAT_OPEN,
    PROFILE_OPEN,
    PLAYER_OPEN,
    MENU_BUILD,
    SEARCH_QUERY,
    NOTIFICATION_INCOMING
}
