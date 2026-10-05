package app.amegram.theme;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Единая дизайн-система Yumigram в стиле Material Design 3.
 * Централизованные токены цветов, скруглений, отступов и типографики.
 * Минимум градиентов — акцент на чистые тональные поверхности (surface containers),
 * безупречную читаемость и контрастность.
 */
public final class YumiTheme {

    private YumiTheme() {
    }

    // --- Токены скруглений (MD3 Radii) ---
    public static final int RADIUS_SM = 8;   // Чипы, бейджи, теги
    public static final int RADIUS_MD = 12;  // Иконки-сквирклы, внутренние элементы, поля ввода
    public static final int RADIUS_LG = 20;  // Секционные карточки, диалоги
    public static final int RADIUS_XL = 28;  // Bottom Sheets, FAB

    // --- Токены отступов (MD3 Spacing) ---
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;

    // --- Цвета: Светлая тема ---
    public static final int LIGHT_PRIMARY = 0xFFE5486B;
    public static final int LIGHT_ON_PRIMARY = 0xFFFFFFFF;
    public static final int LIGHT_PRIMARY_CONTAINER = 0xFFFFD9E0;
    public static final int LIGHT_ON_PRIMARY_CONTAINER = 0xFF3E0014;
    public static final int LIGHT_SURFACE = 0xFFFFF8F8;
    public static final int LIGHT_SURFACE_CONTAINER = 0xFFF7ECEE;
    public static final int LIGHT_SURFACE_VARIANT = 0xFFF6E8EB;
    public static final int LIGHT_ON_SURFACE = 0xFF201A1B;
    public static final int LIGHT_ON_SURFACE_VARIANT = 0xFF514347;
    public static final int LIGHT_OUTLINE = 0xFF857377;
    public static final int LIGHT_ERROR = 0xFFB3261E;
    public static final int LIGHT_LINK = 0xFFC42F52; // Контрастный оттенок для мелких ссылок

    // --- Цвета: Тёмная тема ---
    public static final int DARK_PRIMARY = 0xFFFF8FA6;
    public static final int DARK_ON_PRIMARY = 0xFF5F0F27;
    public static final int DARK_PRIMARY_CONTAINER = 0xFF8A1F3C;
    public static final int DARK_ON_PRIMARY_CONTAINER = 0xFFFFD9E0;
    public static final int DARK_SURFACE = 0xFF161214;
    public static final int DARK_SURFACE_CONTAINER = 0xFF231C1F;
    public static final int DARK_SURFACE_VARIANT = 0xFF2A2226;
    public static final int DARK_ON_SURFACE = 0xFFECE0E2;
    public static final int DARK_ON_SURFACE_VARIANT = 0xFFD3C2C6;
    public static final int DARK_OUTLINE = 0xFF9C8D91;
    public static final int DARK_ERROR = 0xFFFFB4AB;
    public static final int DARK_LINK = 0xFFFF8FA6;

    // --- Фирменный градиент логотипа ---
    public static final int LOGO_GRADIENT_START = 0xFF8B5CF6;
    public static final int LOGO_GRADIENT_END = 0xFFE5486B;

    public static boolean isDark() {
        return Theme.isCurrentThemeDark();
    }

    public static int getPrimary() {
        return isDark() ? DARK_PRIMARY : LIGHT_PRIMARY;
    }

    public static int getOnPrimary() {
        return isDark() ? DARK_ON_PRIMARY : LIGHT_ON_PRIMARY;
    }

    public static int getPrimaryContainer() {
        return isDark() ? DARK_PRIMARY_CONTAINER : LIGHT_PRIMARY_CONTAINER;
    }

    public static int getOnPrimaryContainer() {
        return isDark() ? DARK_ON_PRIMARY_CONTAINER : LIGHT_ON_PRIMARY_CONTAINER;
    }

    public static int getSurface() {
        return isDark() ? DARK_SURFACE : LIGHT_SURFACE;
    }

    public static int getSurfaceContainer() {
        return isDark() ? DARK_SURFACE_CONTAINER : LIGHT_SURFACE_CONTAINER;
    }

    public static int getSurfaceVariant() {
        return isDark() ? DARK_SURFACE_VARIANT : LIGHT_SURFACE_VARIANT;
    }

    public static int getOnSurface() {
        return isDark() ? DARK_ON_SURFACE : LIGHT_ON_SURFACE;
    }

    public static int getOnSurfaceVariant() {
        return isDark() ? DARK_ON_SURFACE_VARIANT : LIGHT_ON_SURFACE_VARIANT;
    }

    public static int getOutline() {
        return isDark() ? DARK_OUTLINE : LIGHT_OUTLINE;
    }

    public static int getError() {
        return isDark() ? DARK_ERROR : LIGHT_ERROR;
    }

    public static int getLink() {
        return isDark() ? DARK_LINK : LIGHT_LINK;
    }

    // --- Фабрики чистых MD3-фонов без градиентов ---

    /** Тональный фон карточки секции (скругление 20dp, мягкая поверхность) */
    public static GradientDrawable cardBackground() {
        return cardBackground(RADIUS_LG);
    }

    public static GradientDrawable cardBackground(int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(radiusDp));
        d.setColor(getSurfaceContainer());
        return d;
    }

    /** Фоновая плашка иконки (сквиркл 12dp) */
    public static GradientDrawable squircleIconBackground(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(AndroidUtilities.dp(RADIUS_MD));
        d.setColor(color);
        return d;
    }

    /** Кнопка с рипплом и скруглением */
    public static RippleDrawable buttonRipple(int backgroundColor, int radiusDp) {
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(AndroidUtilities.dp(radiusDp));
        mask.setColor(0xFFFFFFFF);

        GradientDrawable content = new GradientDrawable();
        content.setCornerRadius(AndroidUtilities.dp(radiusDp));
        content.setColor(backgroundColor);

        int rippleColor = isDark() ? 0x22FFFFFF : 0x1A000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }
}
