package app.amegram.hot.api;

/** Контракт модуля "ghost": політика невидимості + анти-видалення. Реєструється під "ghost". */
public interface HotGhost {

    boolean hideRead();

    boolean hideStories();

    boolean hideOnline();

    boolean hideTyping();

    /** Зберігати видалені повідомлення (єдине ціле з привидом). */
    default boolean isSaveDeletedMessages() {
        return false;
    }

    /** Зберігати історію редагувань. */
    default boolean isSaveEditHistory() {
        return false;
    }

    /** Зберігати медіа з видалених. */
    default boolean isSaveDeletedMedia() {
        return false;
    }
}
