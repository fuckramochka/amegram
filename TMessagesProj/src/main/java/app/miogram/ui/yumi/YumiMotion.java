package app.miogram.ui.yumi;

import android.animation.TimeInterpolator;
import android.view.View;
import android.view.animation.PathInterpolator;

import app.amegram.theme.YumiMd3;

/** Motion vocabulary for Yumi: expressive, short, and Power-Saving aware. */
public final class YumiMotion {

    public static final TimeInterpolator STANDARD =
            new PathInterpolator(0.2f, 0f, 0f, 1f);
    public static final TimeInterpolator EMPHASIZED =
            new PathInterpolator(0.05f, 0.7f, 0.1f, 1f);
    public static final TimeInterpolator EXIT =
            new PathInterpolator(0.3f, 0f, 0.8f, 0.15f);

    private YumiMotion() {
    }

    public static long duration(long baseMs) {
        return YumiPowerSave.animations() ? baseMs : 0L;
    }

    public static void enter(View view, int index) {
        if (view == null) {
            return;
        }
        if (!YumiPowerSave.animations()) {
            view.setAlpha(1f);
            view.setTranslationY(0f);
            return;
        }
        view.setAlpha(0f);
        view.setTranslationY(YumiUi.dp(YumiMd3.SPACE_M_DP));
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * YumiMd3.STAGGER_MS)
                .setDuration(YumiMd3.DURATION_ENTER_MS)
                .setInterpolator(EMPHASIZED)
                .start();
    }

    public static void press(View view, boolean pressed) {
        if (view == null) {
            return;
        }
        final float scale = YumiPowerSave.animations() ? (pressed ? 0.97f : 1f) : 1f;
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .setDuration(YumiMd3.DURATION_PRESS_MS)
                .setInterpolator(STANDARD)
                .start();
    }

    public static void expand(View view, boolean visible) {
        if (view == null) {
            return;
        }
        if (!YumiPowerSave.animations()) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
            view.setAlpha(visible ? 1f : 0f);
            view.setScaleY(visible ? 1f : 0.96f);
            return;
        }
        if (visible) {
            view.setVisibility(View.VISIBLE);
            view.setAlpha(0f);
            view.setScaleY(0.96f);
            view.animate().alpha(1f).scaleY(1f)
                    .setDuration(YumiMd3.DURATION_STANDARD_MS)
                    .setInterpolator(EMPHASIZED).start();
        } else {
            view.animate().alpha(0f).scaleY(0.96f)
                    .setDuration(YumiMd3.DURATION_PRESS_MS)
                    .setInterpolator(EXIT)
                    .withEndAction(() -> view.setVisibility(View.GONE)).start();
        }
    }
}
