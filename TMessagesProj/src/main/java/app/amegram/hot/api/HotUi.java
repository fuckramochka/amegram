package app.amegram.hot.api;

import android.content.Context;
import android.view.View;

/**
 * Контракт модуля "ui": повний контроль над зовнішнім виглядом інтерфейсу Yumigram.
 * Дозволяє модулю керувати кольорами, розмірами, розташуванням елементів
 * та віджетами без потреби оновлювати APK.
 */
public interface HotUi {

    // Стандартні слоти інтерфейсу
    String SLOT_MAIN_NAVIGATION = "main_navigation";
    String SLOT_CHAT_ROW_DECORATOR = "chat_row_decorator";
    String SLOT_PLAYER_SHEET = "player_sheet";
    String SLOT_DRAWER_HEADER = "drawer_header";

    // Стандартні токени кольорів
    String COLOR_PRIMARY = "primary";
    String COLOR_ON_PRIMARY = "onPrimary";
    String COLOR_PRIMARY_CONTAINER = "primaryContainer";
    String COLOR_ON_PRIMARY_CONTAINER = "onPrimaryContainer";
    String COLOR_SURFACE = "surface";
    String COLOR_SURFACE_CONTAINER = "surfaceContainer";
    String COLOR_SURFACE_CONTAINER_LOW = "surfaceContainerLow";
    String COLOR_SURFACE_CONTAINER_HIGH = "surfaceContainerHigh";
    String COLOR_ON_SURFACE = "onSurface";
    String COLOR_ON_SURFACE_VARIANT = "onSurfaceVariant";
    String COLOR_OUTLINE = "outline";
    String COLOR_NAV_SURFACE = "navSurface";
    String COLOR_NAV_INDICATOR = "navIndicator";
    String COLOR_NAV_ACTIVE = "navActive";
    String COLOR_NAV_INACTIVE = "navInactive";
    String COLOR_NAV_BORDER = "navBorder";
    String COLOR_CARD_SURFACE = "cardSurface";
    String COLOR_CARD_RIM = "cardRim";
    String COLOR_SECONDARY_FILL = "secondaryFill";

    // Стандартні токени розмірів (у dp)
    String DIMEN_CARD_RADIUS = "card_radius";
    String DIMEN_CARD_MARGIN_SIDE = "card_margin_side";
    String DIMEN_CARD_MARGIN_V = "card_margin_v";
    String DIMEN_NAV_HEIGHT = "nav_height";
    String DIMEN_NAV_RADIUS = "nav_radius";
    String DIMEN_NAV_PILL_RADIUS = "nav_pill_radius";
    String DIMEN_AVATAR_RADIUS = "avatar_radius";

    // Стандартні токени розташування та видимості
    String PLACEMENT_TIME_BADGE_STYLE = "time_badge_style";
    String PLACEMENT_NAV_EXPAND_PILL = "nav_expand_pill";
    String PLACEMENT_NAV_FLOATING = "nav_floating";
    String PLACEMENT_BLUR_FOLDERS = "blur_folders";
    String PLACEMENT_BLUR_NAV = "blur_nav";

    /** 1 = Yougram Expressive (рідке скло + M3 сегменти), 0 = Classic. */
    int styleMode();

    /** true = Expressive активний зараз. */
    default boolean isExpressive() {
        return styleMode() == 1;
    }

    // ---- Кольори (Colors) ----

    /**
     * Отримати колірний токен з модуля (наприклад, "primary", "primaryContainer", "navIndicator").
     * Якщо модуль не перевизначає токен, повертає fallback.
     */
    default int getColor(String token, int fallback) {
        return fallback;
    }

    // ---- Розміри (Sizes & Dimensions) ----

    /**
     * Отримати розмір у dp (наприклад, "card_radius", "card_margin_v", "nav_height").
     * Якщо модуль не перевизначає токен, повертає fallbackDp.
     */
    default float getDimension(String token, float fallbackDp) {
        return fallbackDp;
    }

    // ---- Місце та розташування (Placement & Layout) ----

    /**
     * Отримати режим розташування або видимість:
     * наприклад, "time_badge_style", "nav_floating", "nav_expand_pill".
     */
    default int getPlacement(String token, int fallback) {
        return fallback;
    }

    default boolean isPlacementEnabled(String token, boolean fallback) {
        int v = getPlacement(token, fallback ? 1 : 0);
        return v != 0;
    }

    // ---- Слотна архітектура (UI Slots) ----

    /**
     * Створити кастомну View або компонент для конкретного слоту інтерфейсу.
     */
    default View createSlotView(String slotId, Context context, Object... args) {
        return null;
    }
}
