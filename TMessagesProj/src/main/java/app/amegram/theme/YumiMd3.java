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
        return isDark() ? 0xFF0D0B10 : 0xFFFBF7FB;
    }

    public static int surfaceContainerLow() {
        return isDark() ? 0xFF141118 : 0xFFFFFFFF;
    }

    public static int surfaceContainer() {
        return isDark() ? 0xFF18141D : 0xFFFFFFFF;
    }

    public static int surfaceContainerHigh() {
        return isDark() ? 0xFF211B27 : 0xFFF5EDF5;
    }

    public static int surfaceContainerHighest() {
        return isDark() ? 0xFF2A2231 : 0xFFEFE5EF;
    }

    public static int surfaceDim() {
        return isDark() ? 0xFF08070B : 0xFFF2ECF2;
    }

    public static int surfaceBright() {
        return isDark() ? 0xFF2E2636 : 0xFFFFFBFF;
    }

    public static int inverseSurface() {
        return isDark() ? 0xFFF5EFF6 : 0xFF332C37;
    }

    public static int onInverseSurface() {
        return isDark() ? 0xFF201923 : 0xFFF9EFF8;
    }

    public static int scrim() {
        return 0x99000000;
    }

    public static int onSurface() {
        return isDark() ? 0xFFF5EFF6 : 0xFF201923;
    }

    public static int onSurfaceVariant() {
        return isDark() ? 0xFFBBB0C4 : 0xFF685C6A;
    }

    public static int outline() {
        return isDark() ? 0xFF3B3345 : 0xFFE6DAE6;
    }

    public static int outlineStrong() {
        return isDark() ? 0xFF8E8197 : 0xFF8C7E8D;
    }

    public static int primary() {
        return isDark() ? 0xFFFF9DB2 : 0xFFC42F52;
    }

    public static int onPrimary() {
        return isDark() ? 0xFF4A0A1B : 0xFFFFFFFF;
    }

    public static int primaryContainer() {
        return isDark() ? 0xFF4D1E2C : 0xFFFFDDE6;
    }

    public static int onPrimaryContainer() {
        return isDark() ? 0xFFFFD9E2 : 0xFF3D0013;
    }

    public static int secondary() {
        return isDark() ? 0xFFE0BFD4 : 0xFF7C526F;
    }

    public static int onSecondary() {
        return isDark() ? 0xFF3F1D34 : 0xFFFFFFFF;
    }

    public static int secondaryContainer() {
        return isDark() ? 0xFF3C2538 : 0xFFF3DCEB;
    }

    public static int onSecondaryContainer() {
        return isDark() ? 0xFFF3DCEB : 0xFF31102A;
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

    public static int gradientStart() {
        return 0xFF8B5CF6;
    }

    public static int gradientEnd() {
        return 0xFFE5486B;
    }

    public static int heroStart() {
        return 0xFF3A1E5C;
    }

    public static int heroMid() {
        return 0xFF6B1F4B;
    }

    public static int heroEnd() {
        return 0xFF7D1F3C;
    }

    /**
     * A deeper cut of the brand gradient for filled buttons with white labels.
     * Keeps the violet → pink story while clearing 4.5:1 contrast at 13–14sp.
     */
    public static int actionGradientStart() {
        return 0xFF7C3AED;
    }

    public static int actionGradientEnd() {
        return 0xFFD92D5C;
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
