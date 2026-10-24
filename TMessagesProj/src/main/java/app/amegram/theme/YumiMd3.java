package app.amegram.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

/**
 * Yumi Material 3 Expressive design tokens.
 *
 * <p>This is intentionally separate from {@link YumiTheme}: the old token set is
 * still used by legacy screens, while this one describes the new expressive
 * settings surface. Everything visual in the settings screen must come from
 * here — no raw hex values or bare dp/sp magic numbers in screen code.</p>
 */
public final class YumiMd3 {

    private YumiMd3() {
    }

    // ---------------------------------------------------------------------
    // Typography
    // ---------------------------------------------------------------------

    public static final int REGULAR = 0;
    public static final int MEDIUM = 1;
    public static final int BOLD = 2;

    public static final float DISPLAY = 30f;
    public static final float HEADLINE = 24f;
    public static final float TITLE = 20f;
    public static final float TITLE_SMALL = 16f;
    public static final float BODY = 15f;
    public static final float BODY_SMALL = 13.5f;
    public static final float LABEL = 13f;
    public static final float CAPTION = 11.5f;
    public static final float TITLE_LARGE = 22f;
    public static final float LABEL_LARGE = 14f;

    // ---------------------------------------------------------------------
    // Spacing, shape and motion scales
    // ---------------------------------------------------------------------

    public static final float SPACE_XS_DP = 4f;
    public static final float SPACE_S_DP = 8f;
    public static final float SPACE_M_DP = 12f;
    public static final float SPACE_L_DP = 16f;
    public static final float SPACE_XL_DP = 20f;
    public static final float SPACE_XXL_DP = 24f;
    public static final float SPACE_XXXL_DP = 32f;
    public static final float CONTENT_MAX_DP = 620f;

    public static final float RADIUS_XS_DP = 9f;
    public static final float RADIUS_S_DP = 13f;
    public static final float RADIUS_M_DP = 18f;
    public static final float RADIUS_L_DP = 22f;
    public static final float RADIUS_XL_DP = 26f;
    public static final float RADIUS_XXL_DP = 28f;
    public static final float RADIUS_FULL_DP = 99f;

    public static final long DURATION_PRESS_MS = 110L;
    public static final long DURATION_STANDARD_MS = 280L;
    public static final long DURATION_ENTER_MS = 360L;
    public static final long STAGGER_MS = 35L;

    private static Typeface regularFace;
    private static Typeface mediumFace;
    private static Typeface boldFace;

    public static Typeface typeface(int weight) {
        if (weight == MEDIUM) {
            if (mediumFace == null) {
                mediumFace = Typeface.create("sans-serif-medium", Typeface.NORMAL);
            }
            return mediumFace;
        }
        if (weight == BOLD) {
            if (boldFace == null) {
                boldFace = Typeface.create("sans-serif", Typeface.BOLD);
            }
            return boldFace;
        }
        if (regularFace == null) {
            regularFace = Typeface.create("sans-serif", Typeface.NORMAL);
        }
        return regularFace;
    }

    public static TextView text(Context context, CharSequence value, float sp, int color, int weight) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(typeface(weight));
        view.setIncludeFontPadding(false);
        return view;
    }

    // ---------------------------------------------------------------------
    // Palette
    // ---------------------------------------------------------------------

    public static boolean isDark() {
        return Theme.isCurrentThemeDark();
    }

    public static int surface() {
        try {
            int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
            if (bg != 0 && (bg & 0xFF000000) != 0) {
                return bg;
            }
        } catch (Throwable ignore) {
        }
        return isDark() ? 0xFF141218 : 0xFFFAF9FD;
    }

    public static int surfaceContainerLow() {
        return isDark()
                ? blend(surface(), 0xFF1D1B20, 0.5f)
                : blend(surface(), 0xFFF7F2FA, 0.5f);
    }

    public static int surfaceContainer() {
        try {
            int gray = Theme.getColor(Theme.key_windowBackgroundGray);
            if (gray != 0 && (gray & 0xFF000000) != 0) {
                return gray;
            }
        } catch (Throwable ignore) {
        }
        return isDark()
                ? blend(surface(), 0xFF211F26, 0.6f)
                : blend(surface(), 0xFFF3EDF7, 0.6f);
    }

    public static int surfaceContainerHigh() {
        return isDark()
                ? blend(surfaceContainer(), 0xFF2B2930, 0.6f)
                : blend(surfaceContainer(), 0xFFECE6F0, 0.6f);
    }

    public static int surfaceContainerHighest() {
        return isDark()
                ? blend(surfaceContainer(), 0xFF36343B, 0.8f)
                : blend(surfaceContainer(), 0xFFE6E0E9, 0.8f);
    }

    public static int surfaceDim() {
        return isDark() ? 0xFF100E14 : 0xFFDED8E1;
    }

    public static int surfaceBright() {
        return isDark() ? 0xFF3B383E : 0xFFFEF7FF;
    }

    public static int inverseSurface() {
        return isDark() ? 0xFFE6E0E9 : 0xFF313033;
    }

    public static int onInverseSurface() {
        return isDark() ? 0xFF313033 : 0xFFF4EFF4;
    }

    public static int scrim() {
        return 0x99000000;
    }

    public static int onSurface() {
        try {
            int text = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
            if (text != 0 && (text & 0xFF000000) != 0) {
                return text;
            }
        } catch (Throwable ignore) {
        }
        return isDark() ? 0xFFE6E0E9 : 0xFF1D1B20;
    }

    public static int onSurfaceVariant() {
        try {
            int sub = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2);
            if (sub != 0 && (sub & 0xFF000000) != 0) {
                return sub;
            }
        } catch (Throwable ignore) {
        }
        return isDark() ? 0xFFCAC4D0 : 0xFF49454F;
    }

    public static int outline() {
        try {
            int div = Theme.getColor(Theme.key_divider);
            if (div != 0 && (div & 0xFF000000) != 0) {
                return div;
            }
        } catch (Throwable ignore) {
        }
        return isDark() ? 0xFF938F99 : 0xFF79747E;
    }

    public static int outlineStrong() {
        return isDark() ? 0xFF938F99 : 0xFF79747E;
    }

    public static int outlineVariant() {
        return isDark() ? 0xFF49454F : 0xFFCAC4D0;
    }

    public static int primary() {
        try {
            int themeAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
            if (themeAccent == 0 || (themeAccent & 0xFF000000) == 0) {
                themeAccent = Theme.getColor(Theme.key_chats_actionBackground);
            }
            if (themeAccent == 0 || (themeAccent & 0xFF000000) == 0) {
                themeAccent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText);
            }
            if (themeAccent != 0 && (themeAccent & 0xFF000000) != 0) {
                return themeAccent;
            }
        } catch (Throwable ignore) {
        }
        return isDark() ? 0xFFD0BCFF : 0xFF6750A4;
    }

    public static int onPrimary() {
        int p = primary();
        return ColorUtils.calculateLuminance(p) > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int primaryContainer() {
        int p = primary();
        return isDark()
                ? blend(surface(), p, 0.28f)
                : blend(0xFFFFFFFF, p, 0.16f);
    }

    public static int onPrimaryContainer() {
        int p = primary();
        return isDark()
                ? blend(p, 0xFFFFFFFF, 0.85f)
                : blend(p, 0xFF000000, 0.85f);
    }

    public static int secondary() {
        return blend(primary(), isDark() ? 0xFFCCC2DC : 0xFF625B71, 0.55f);
    }

    public static int onSecondary() {
        int s = secondary();
        return ColorUtils.calculateLuminance(s) > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    public static int secondaryContainer() {
        return blend(surfaceContainer(), secondary(), isDark() ? 0.24f : 0.14f);
    }

    public static int onSecondaryContainer() {
        int s = secondary();
        return isDark()
                ? blend(s, 0xFFFFFFFF, 0.85f)
                : blend(s, 0xFF000000, 0.85f);
    }

    public static int tertiary() {
        return isDark() ? 0xFF9FDCEE : 0xFF00677A;
    }

    public static int tertiaryContainer() {
        return isDark() ? 0xFF12414E : 0xFFC4F1FA;
    }

    public static int onTertiaryContainer() {
        return isDark() ? 0xFFC4F1FA : 0xFF00323D;
    }

    // Navigation roles -----------------------------------------------------

    public static int navSurface() {
        return withAlpha(surfaceContainerHigh(), isDark() ? 0.98f : 0.99f);
    }

    public static int navBorder() {
        return withAlpha(outline(), isDark() ? 0.8f : 0.9f);
    }

    public static int navIndicator() {
        return withAlpha(primary(), isDark() ? 0.26f : 0.14f);
    }

    public static int navActive() {
        return primary();
    }

    public static int navInactive() {
        return onSurfaceVariant();
    }

    // Chat roles -----------------------------------------------------------

    public static int chatWallpaper() {
        return isDark() ? 0xFF100D14 : 0xFFF7F1F6;
    }

    public static int chatInBubble() {
        return isDark() ? 0xFF211B27 : 0xFFFFFFFF;
    }

    public static int chatOutBubble() {
        return isDark()
                ? blend(surfaceContainerHigh(), gradientEnd(), 0.34f)
                : blend(surfaceContainerHighest(), gradientEnd(), 0.20f);
    }

    public static int chatOutBubbleEnd() {
        return isDark()
                ? blend(surfaceContainerHigh(), gradientStart(), 0.30f)
                : blend(surfaceContainerHighest(), gradientStart(), 0.15f);
    }

    public static int chatBubbleText() {
        return onSurface();
    }

    public static int chatBubbleMeta() {
        return withAlpha(onSurface(), isDark() ? 0.58f : 0.52f);
    }

    public static int chatField() {
        return isDark() ? 0xFF19141E : 0xFFF7F0F6;
    }

    public static int chatFieldBorder() {
        return withAlpha(outline(), isDark() ? 0.72f : 0.8f);
    }

    public static int composerSurface() {
        return surfaceContainerLow();
    }

    public static int composerText() {
        return onSurface();
    }

    public static int composerHint() {
        return onSurfaceVariant();
    }

    public static int composerIcon() {
        return onSurfaceVariant();
    }

    public static int composerSend() {
        return primary();
    }

    public static int online() {
        return isDark() ? 0xFF6FE29B : 0xFF15A35A;
    }

    // Feedback roles -------------------------------------------------------

    public static int snackbarBackground() {
        return inverseSurface();
    }

    public static int snackbarText() {
        return onInverseSurface();
    }

    public static int popupSurface() {
        return isDark() ? 0xFF241E2B : 0xFFFFFFFF;
    }

    public static int dialogSurface() {
        return isDark() ? 0xFF1D1822 : 0xFFFFFBFF;
    }

    public static int sliderTrack() {
        return withAlpha(onSurface(), isDark() ? 0.20f : 0.14f);
    }

    public static int sliderActive() {
        return primary();
    }

    public static int violet() {
        return isDark() ? 0xFFC8B8FF : 0xFF6544C4;
    }

    public static int cyan() {
        return isDark() ? 0xFF7FE8F5 : 0xFF0E7490;
    }

    public static int amber() {
        return isDark() ? 0xFFFFD66B : 0xFFA85A00;
    }

    public static int green() {
        return isDark() ? 0xFF9BE8B5 : 0xFF147A45;
    }

    public static int blue() {
        return isDark() ? 0xFF9CC7FF : 0xFF1A5FBF;
    }

    public static int orange() {
        return isDark() ? 0xFFFFBE8C : 0xFFA93E0B;
    }

    public static int teal() {
        return isDark() ? 0xFF87E4D5 : 0xFF0D7267;
    }

    public static int neutral() {
        return isDark() ? 0xFFC8BECF : 0xFF6D5F6E;
    }

    public static int success() {
        return isDark() ? 0xFF89E3AC : 0xFF127240;
    }

    public static int warning() {
        return isDark() ? 0xFFFFD16B : 0xFF8A5300;
    }

    public static int error() {
        return isDark() ? 0xFFFFB4AB : 0xFFB3261E;
    }

    public static int errorContainer() {
        return isDark() ? 0xFF5C1A16 : 0xFFFFDAD6;
    }

    public static int onErrorContainer() {
        return isDark() ? 0xFFFFDAD6 : 0xFF410002;
    }

    public static int gradientStart() {
        return primary();
    }

    public static int gradientEnd() {
        return blend(primary(), 0xFF7C3AED, 0.4f);
    }

    public static int heroStart() {
        return blend(isDark() ? 0xFF14101E : 0xFFEDE7F6, primary(), 0.30f);
    }

    public static int heroMid() {
        return blend(isDark() ? 0xFF1E1022 : 0xFFF3E5F5, primary(), 0.22f);
    }

    public static int heroEnd() {
        return blend(isDark() ? 0xFF241020 : 0xFFFCE4EC, primary(), 0.18f);
    }

    /**
     * A deeper cut of the brand gradient for filled buttons with white labels.
     * Keeps the violet → pink story while clearing 4.5:1 contrast at 13–14sp.
     */
    public static int actionGradientStart() {
        return primary();
    }

    public static int actionGradientEnd() {
        return blend(primary(), 0xFF7C3AED, 0.30f);
    }

    /** Content colour on the deep action gradient, readable in both themes. */
    public static int onActionGradient() {
        return 0xFFFFFFFF;
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    public static int blend(int first, int second, float ratio) {
        return ColorUtils.blendARGB(first, second, Math.max(0f, Math.min(1f, ratio)));
    }

    public static int tone(int accent, float tint) {
        return blend(surfaceContainerHigh(), accent, tint);
    }

    public static int iconTone(int accent, float tint) {
        return blend(surfaceContainerHigh(), accent,
                isDark() ? Math.min(0.34f, tint + 0.06f) : Math.min(0.22f, tint + 0.04f));
    }

    public static int muted(int color) {
        return withAlpha(color, isDark() ? 0.62f : 0.74f);
    }

    public static int hairline() {
        return withAlpha(outline(), isDark() ? 0.9f : 0.85f);
    }

    public static GradientDrawable rounded(int fill, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    public static GradientDrawable rounded(int fill, int stroke, float radiusDp, float strokeWidthDp) {
        GradientDrawable drawable = rounded(fill, radiusDp);
        drawable.setStroke(Math.max(1, dp(strokeWidthDp)), stroke);
        return drawable;
    }

    public static GradientDrawable gradient(int start, int end, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, new int[]{start, end});
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    public static RippleDrawable ripple(int fill, float radiusDp, int rippleColor) {
        GradientDrawable content = rounded(fill, radiusDp);
        GradientDrawable mask = rounded(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static RippleDrawable transparentRipple(float radiusDp, int rippleColor) {
        GradientDrawable content = rounded(Color.TRANSPARENT, radiusDp);
        GradientDrawable mask = rounded(0xFFFFFFFF, radiusDp);
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask);
    }

    public static int defaultRippleColor() {
        return isDark() ? 0x22FFFFFF : 0x1A000000;
    }

    public static int accentRippleColor(int accent) {
        return withAlpha(accent, isDark() ? 0.18f : 0.14f);
    }
}
