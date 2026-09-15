package app.miogram.bridge.ui.xp;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;

import org.telegram.messenger.AndroidUtilities;

/**
 * Windows XP Luna visual language for Miogram.
 *
 * Authentic reference values (Luna blue / Olive green / Silver):
 * - Active title bar: horizontal gradient #0058E6 -> #3A93FF, white bold text.
 * - Start button: green gradient, italic bold white label.
 * - Face/buttons: #ECE9D8 face, #D4D0C8 borders, XP blue #0055EA accents.
 */
public final class MiogramXpTheme {

    private MiogramXpTheme() {}

    // Luna title bar (active window).
    public static final int TITLE_LEFT = 0xFF0058E6;
    public static final int TITLE_RIGHT = 0xFF3A93FF;
    public static final int TITLE_TEXT = 0xFFFFFFFF;
    public static final int TITLE_SUBTEXT = 0xFFD6E6FF;

    // XP green (Start button, online states).
    public static final int START_TOP = 0xFF5CB85C;
    public static final int START_BOTTOM = 0xFF2F8C2F;
    public static final int START_TEXT = 0xFFFFFFFF;

    // Face + controls.
    public static final int FACE = 0xFFECE9D8;
    public static final int FACE_DARK = 0xFFD4D0C8;
    public static final int ACCENT_BLUE = 0xFF0055EA;
    public static final int ACCENT_GREEN = 0xFF228B22;
    public static final int TEXT_BLACK = 0xFF000000;
    public static final int TEXT_GRAY = 0xFF555555;

    public static boolean isXpActive() {
        try {
            return app.miogram.bridge.divine.MiogramDivineEngine.isWindowsXpPresetActive(null);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isXpActive(Context context) {
        try {
            return app.miogram.bridge.divine.MiogramDivineEngine.isWindowsXpPresetActive(context);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Luna active title bar: horizontal blue gradient, square (maximized window). */
    public static GradientDrawable titleBarBackground() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{TITLE_LEFT, TITLE_RIGHT});
        d.setShape(GradientDrawable.RECTANGLE);
        return d;
    }

    /** XP Start button: green gradient, softly rounded. */
    public static GradientDrawable startButtonBackground() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{START_TOP, START_BOTTOM});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(14));
        return d;
    }

    /** Classic XP push button: white -> pale blue, 1px navy border, 3dp radius. */
    public static GradientDrawable xpButtonBackground() {
        GradientDrawable d = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFFFFFFFF, 0xFFDCE9FC});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(AndroidUtilities.dp(3));
        d.setStroke(AndroidUtilities.dp(1), 0xFF003C74);
        return d;
    }

    /** XP face panel: beige with a darker 1px edge. */
    public static GradientDrawable facePanelBackground() {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(FACE);
        d.setStroke(AndroidUtilities.dp(1), FACE_DARK);
        return d;
    }
}
