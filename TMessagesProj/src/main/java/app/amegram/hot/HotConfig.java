package app.amegram.hot;

/**
 * Конфиг хот-модулей. Отдельный репозиторий разработчика Амэграм:
 * положи туда modules.json + .hmod файлы — новые модули появятся
 * в каталоге сами, без обновления приложения.
 */
public final class HotConfig {

    private HotConfig() {
    }

    /** Каталог модулей. Поменяй на свой репозиторий одной строкой. */
    public static volatile String CATALOG_URL =
            "https://raw.githubusercontent.com/fuckramochka/yuimodules/main/modules.json";

    public static void setCatalogUrl(String url) {
        if (url != null && !url.isEmpty()) {
            CATALOG_URL = url;
        }
    }

    /** Папка установленных модулей внутри filesDir. */
    public static final String DIR_NAME = "hotmodules";

    /** Кэш каталога: не чаще раза в N. */
    public static final long CATALOG_TTL_MS = 6L * 60 * 60 * 1000L;

    /** Сколько старых версий держать на диске (текущая + предыдущая для отката). */
    public static final int KEEP_VERSIONS = 2;

    public static final String PREFS = "hotmodules_prefs";
    public static final String ASSET_FALLBACK = "hotmodules/modules.json";
}
