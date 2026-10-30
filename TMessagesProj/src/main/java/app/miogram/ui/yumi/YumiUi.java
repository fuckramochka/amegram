package app.miogram.ui.yumi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import java.util.Locale;

import app.amegram.theme.YumiMd3;
import app.miogram.bridge.MiogramLocale;

/** Small shared helpers for the Yumi component package. */
public final class YumiUi {

    private static volatile Boolean forceEnabled;

    private YumiUi() {
    }

    /**
     * The modern Yumi layer follows the existing "UI module + Expressive style"
     * switch. There is deliberately no second appearance switch.
     */
    public static boolean enabled() {
        final Boolean forced = forceEnabled;
        if (forced != null) {
            return forced;
        }
        try {
            return app.exteraless.appearance.AppearanceConfig.isYougramExpressive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Test/preview only. */
    public static void setForceEnabled(@Nullable Boolean value) {
        forceEnabled = value;
    }

    public static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    public static boolean isRtl() {
        try {
            return org.telegram.messenger.LocaleController.isRTL;
        } catch (Throwable ignored) {
            return "ar".equals(Locale.getDefault().getLanguage())
                    || "he".equals(Locale.getDefault().getLanguage());
        }
    }

    public static int startGravity() {
        return isRtl() ? Gravity.RIGHT : Gravity.LEFT;
    }

    public static int endGravity() {
        return isRtl() ? Gravity.LEFT : Gravity.RIGHT;
    }

    public static TextView text(Context context, CharSequence value, float sp, int color, int weight) {
        return YumiMd3.text(context, value, sp, color, weight);
    }

    public static GradientDrawable rounded(int color, float radiusDp) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        // Коли модуль "ui" вимкнено, прибираємо Yumi-скруглення: лишається
        // звичайна фонова поверхня замість характерних карток/пігулок.
        drawable.setCornerRadius(enabled() ? dp(radiusDp) : 0f);
        return drawable;
    }

    public static GradientDrawable rounded(int color, float radiusDp, int strokeColor, float strokeDp) {
        final GradientDrawable drawable = rounded(color, radiusDp);
        if (enabled()) {
            drawable.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        }
        return drawable;
    }

    public static GradientDrawable oval(int color) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    public static GradientDrawable oval(int color, int strokeColor, float strokeDp) {
        final GradientDrawable drawable = oval(color);
        drawable.setStroke(Math.max(1, dp(strokeDp)), strokeColor);
        return drawable;
    }

    public static RippleDrawable ripple(int color, Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(color), content, null);
    }

    public static RippleDrawable selectable(int contentColor, float radiusDp) {
        return ripple(YumiMd3.withAlpha(YumiMd3.onSurface(), 0.10f), rounded(contentColor, radiusDp));
    }

    public static void haptic(View view) {
        if (view != null) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        }
    }

    public static void marginStart(View view, int marginPx) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginStart(marginPx);
        }
    }

    public static void marginEnd(View view, int marginPx) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).setMarginEnd(marginPx);
        }
    }

    public static void setTextSize(TextView view, float sp) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    public static int muted(int color) {
        return YumiMd3.withAlpha(color, 0.62f);
    }

    public static int primaryContainer(int tone) {
        return YumiMd3.blend(YumiMd3.primaryContainer(), tone, 0.18f);
    }

    public static int contentWidth(int availableWidthPx) {
        return Math.min(availableWidthPx, dp(YumiMd3.CONTENT_MAX_DP));
    }

    public static int sideInset(int availableWidthPx) {
        return Math.max(0, (availableWidthPx - dp(YumiMd3.CONTENT_MAX_DP)) / 2);
    }

    public static void applyContentCap(View view) {
        final ViewGroup parent = view.getParent() instanceof ViewGroup ? (ViewGroup) view.getParent() : null;
        if (parent == null) {
            return;
        }
        parent.setClipChildren(false);
    }

    public static int contentColor() {
        return YumiMd3.onSurface();
    }

    public static int surfaceColor() {
        return YumiMd3.surface();
    }

    public static void announce(View view, CharSequence text) {
        if (view != null) {
            view.announceForAccessibility(text);
        }
    }

    public static int resolveThemeColor(Context context, int fallback) {
        return fallback != 0 ? fallback : Theme.getColor(Theme.key_windowBackgroundWhite,
                context instanceof Theme.ResourcesProvider ? (Theme.ResourcesProvider) context : null);
    }
}
