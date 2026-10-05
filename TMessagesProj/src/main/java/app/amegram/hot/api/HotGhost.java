package app.amegram.hot.api;

/** Контракт модуля "ghost": політика невидимості. Реєструється під "ghost". */
public interface HotGhost {

    boolean hideRead();

    boolean hideStories();

    boolean hideOnline();

    boolean hideTyping();
}
