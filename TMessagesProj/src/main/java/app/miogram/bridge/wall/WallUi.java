package app.miogram.bridge.wall;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.miogram.bridge.customui.MiogramHaptic;

/**
 * Дрібна фабрика стіни: кольори, фони, шрифти, час, тактильність.
 * Ніяких алокацій у onDraw — це лише фабрика для побудови в'ю.
 */
public final class WallUi {

    public static final int PINK = 0xFFFF4D8D;
    public static final int VIOLET = 0xFF8A5CFF;
    public static final int CYAN = 0xFF35E0FF;
    public static final int LIME = 0xFF9DFF5C;
    public static final int AMBER = 0xFFFFC24D;
    public static final int MAGENTA = 0xFFFF5CD6;

    public static final int[] NEON_PALETTE = {PINK, VIOLET, CYAN, LIME, AMBER, MAGENTA};

    private WallUi() {
    }

    public static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    public static boolean dark() {
        return Theme.isCurrentThemeDark();
    }

    public static int textPrimary() {
        return dark() ? 0xFFFFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    public static int textSecondary() {
        return dark() ? 0xB3FFFFFF : Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    public static int textTertiary() {
        return dark() ? 0x80FFFFFF : 0x99000000;
    }

    public static int surface() {
        return dark() ? 0xFF0E0A1A : 0xFFF6F3FF;
    }

    public static int surfaceElevated() {
        return dark() ? 0xFF18122E : 0xFFFFFFFF;
    }

    public static int divider() {
        return dark() ? 0x1FFFFFFF : 0x14000000;
    }

    public static GradientDrawable cardBackground(String style) {
        return cardBackground(style, dark());
    }

    public static GradientDrawable cardBackground(String style, boolean dark) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(20));
        String s = style == null ? WallModels.STYLE_GLASS : style;
        switch (s) {
            case WallModels.STYLE_NEON:
                if (dark) {
                    d.setColors(new int[]{0x59FF4D8D, 0x2E8A5CFF});
                    d.setOrientation(GradientDrawable.Orientation.TL_BR);
                    d.setStroke(dp(1.2f), 0x99FF4D8D);
                } else {
                    d.setColors(new int[]{0xFFFFE4F1, 0xFFEDE4FF});
                    d.setOrientation(GradientDrawable.Orientation.TL_BR);
                    d.setStroke(dp(1.2f), 0x66FF4D8D);
                }
                break;
            case WallModels.STYLE_PAPER:
                if (dark) {
                    d.setColor(0xFF2B2338);
                    d.setStroke(dp(1), 0x1FFFFFFF);
                } else {
                    d.setColor(0xFFFFF4D6);
                    d.setStroke(dp(1), 0x1F000000);
                }
                break;
            case WallModels.STYLE_NIGHT:
                if (dark) {
                    d.setColors(new int[]{0xF2150F2E, 0xF20A0718});
                    d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                    d.setStroke(dp(1), 0x33FFFFFF);
                } else {
                    d.setColors(new int[]{0xFF1B1436, 0xFF0D0920});
                    d.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
                }
                break;
            case WallModels.STYLE_GLASS:
            default:
                if (dark) {
                    d.setColors(new int[]{0x26FFFFFF, 0x0DFFFFFF});
                    d.setOrientation(GradientDrawable.Orientation.TL_BR);
                    d.setStroke(dp(1), 0x33FFFFFF);
                } else {
                    d.setColors(new int[]{0xF2FFFFFF, 0xE6FFFFFF});
                    d.setOrientation(GradientDrawable.Orientation.TL_BR);
                    d.setStroke(dp(1), 0x14000000);
                }
                break;
        }
        return d;
    }

    public static int styleTextColor(String style) {
        if (WallModels.STYLE_NIGHT.equals(style)) return 0xFFFFFFFF;
        if (WallModels.STYLE_PAPER.equals(style) && !dark()) return 0xFF2B2338;
        return textPrimary();
    }

    public static GradientDrawable pill(int fill, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radius));
        d.setColor(fill);
        if (stroke != 0) {
            d.setStroke(dp(1), stroke);
        }
        return d;
    }

    public static GradientDrawable brandPill(float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{PINK, VIOLET, CYAN});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radius));
        return d;
    }

    public static GradientDrawable brandPillSoft(float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x33FF4D8D, 0x338A5CFF, 0x3335E0FF});
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radius));
        d.setStroke(dp(1), 0x55FFFFFF);
        return d;
    }

    /** Градієнт кнопки-елемента за її типом дії. */
    public static GradientDrawable actionPill(String kind) {
        int[] colors;
        if ("copy".equals(kind)) {
            colors = new int[]{0xFF35E0FF, 0xFF9DFF5C};
        } else if ("chat".equals(kind)) {
            colors = new int[]{0xFFFFC24D, 0xFFFF5CD6};
        } else {
            colors = new int[]{PINK, VIOLET};
        }
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(18));
        return d;
    }

    public static void styleTitle(TextView tv, float sp) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTypeface(AndroidUtilities.bold());
        tv.setTextColor(textPrimary());
        tv.setIncludeFontPadding(false);
    }

    public static void styleBody(TextView tv, float sp, int color) {
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setLineSpacing(dp(3), 1.05f);
        tv.setIncludeFontPadding(false);
    }

    public static CharSequence gradientTitle(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        if (text != null && text.length() > 0) {
            sb.setSpan(new ForegroundColorSpan(PINK), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }

    public static CharSequence bold(String text) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text == null ? "" : text);
        sb.setSpan(new StyleSpan(Typeface.BOLD), 0, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    public static String timeAgo(long time) {
        if (time <= 0) return "";
        long diff = System.currentTimeMillis() - time;
        if (diff < 60_000) return "щойно";
        if (diff < 3_600_000) return (diff / 60_000) + " хв";
        if (diff < 86_400_000) return (diff / 3_600_000) + " год";
        if (diff < 172_800_000) return "вчора";
        SimpleDateFormat fmt = new SimpleDateFormat("d MMM", Locale.getDefault());
        return fmt.format(new Date(time));
    }

    public static String formatCount(int n) {
        if (n >= 100000) return (n / 1000) + "K";
        if (n >= 1000) return String.format(Locale.US, "%.1fK", n / 1000f);
        return String.valueOf(n);
    }

    public static String formatSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
        return String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f));
    }

    public static String initials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    public static int colorForId(long id) {
        int idx = (int) Math.abs(id % NEON_PALETTE.length);
        return NEON_PALETTE[idx];
    }

    public static int colorForString(String s) {
        if (s == null) return PINK;
        int h = 0;
        for (int i = 0; i < s.length(); i++) h = h * 31 + s.charAt(i);
        return NEON_PALETTE[Math.abs(h) % NEON_PALETTE.length];
    }

    public static int cycle(int color, int shift) {
        for (int i = 0; i < NEON_PALETTE.length; i++) {
            if (NEON_PALETTE[i] == color) {
                int next = (i + shift) % NEON_PALETTE.length;
                if (next < 0) next += NEON_PALETTE.length;
                return NEON_PALETTE[next];
            }
        }
        return color;
    }

    public static float rotationFor(String id) {
        if (id == null) return 0f;
        int h = 0;
        for (int i = 0; i < id.length(); i++) h = h * 31 + id.charAt(i);
        int mod = Math.abs(h % 100);
        return (mod - 50) / 100f * 3.2f; // -1.6..1.6 deg
    }

    public static void hapticTap(View view) {
        if (WallPrefs.hapticsEnabled()) {
            MiogramHaptic.tap(view);
        }
    }

    public static void hapticClick(View view) {
        if (WallPrefs.hapticsEnabled()) {
            MiogramHaptic.click(view);
        }
    }

    public static void hapticSelect(View view) {
        if (WallPrefs.hapticsEnabled()) {
            MiogramHaptic.select(view);
        }
    }

    public static void hapticToggle(View view, boolean on) {
        if (WallPrefs.hapticsEnabled()) {
            MiogramHaptic.toggle(view, on);
        }
    }

    public static int blend(int from, int to, float t) {
        return androidx.core.graphics.ColorUtils.blendARGB(from, to, t);
    }

    public static int alpha(int color, float a) {
        return Color.argb((int) (Color.alpha(color) * a), Color.red(color), Color.green(color), Color.blue(color));
    }

    public static void setMargins(View v, int l, int t, int r, int b) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMargins(l, t, r, b);
            v.setLayoutParams(lp);
        }
    }

    public static int resolveColor(Context context, String hex, int def) {        try {
            if (hex != null && hex.startsWith("#") && (hex.length() == 7 || hex.length() == 9)) {
                return Color.parseColor(hex);
            }
        } catch (Throwable ignore) {
        }
        return def;
    }
}
