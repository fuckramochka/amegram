package app.amegram.hot.api;

/**
 * Сервіс «Стіна профілю» (модуль wall).
 * Модуль реєструє реалізацію через HotHost.registerService(HotServices.WALL, this),
 * а ядро дізнається, що стіна доступна, і відкриває її екран.
 *
 * Вбудована стіна живе в APK (app.miogram.bridge.wall), а .hmod-модуль лише
 * вмикає її як окремий модуль та може делегувати відкриття.
 */
public interface HotWall {

    /** Стіна доступна користувачу (модуль увімкнений і готовий). */
    boolean isAvailable();

    /** Версія стіни, яку віддає модуль (наприклад, "1.0.0"). */
    default String versionName() {
        return "1.0.0";
    }

    /** Відкрити власну стіну з поточного екрана. */
    void openWallScreen();
}
