package app.amegram.hot.api;

/** Контракт модуля "ui": стиль інтерфейсу Yumi (Yougram Expressive ↔ Classic). Реєструється під "ui". */
public interface HotUi {

    /** 1 = Yougram Expressive (рідке скло + M3 сегменти), 0 = Classic. */
    int styleMode();

    /** true = Expressive активний зараз. */
    default boolean isExpressive() {
        return styleMode() == 1;
    }
}
