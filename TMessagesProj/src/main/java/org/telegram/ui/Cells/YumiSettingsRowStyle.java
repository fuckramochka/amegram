package org.telegram.ui.Cells;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

import app.amegram.theme.YumiMd3;
import app.miogram.ui.yumi.YumiUi;
import app.exteraless.appearance.YougramExpressive;

/** Shared visual treatment for legacy rows used by nested settings screens. */
final class YumiSettingsRowStyle {

    private YumiSettingsRowStyle() {}

    static void applySurface(android.view.View row, Theme.ResourcesProvider resourcesProvider, float radiusDp) {
        if (!YougramExpressive.enabled()) {
            return;
        }
        int surface = YumiMd3.surfaceContainerLow();
        int outline = YumiMd3.withAlpha(YumiMd3.primary(), YumiMd3.isDark() ? 0.30f : 0.20f);
        int rippleColor = YumiMd3.withAlpha(YumiMd3.primary(), 0.16f);
        Drawable rounded = YumiUi.rounded(surface, radiusDp, outline, 0.85f);
        Drawable ripple = YumiUi.ripple(rippleColor, rounded);
        row.setBackground(new InsetDrawable(ripple,
                AndroidUtilities.dp(10), AndroidUtilities.dp(2), AndroidUtilities.dp(10), AndroidUtilities.dp(2)));
    }

    static void title(TextView view) {
        if (!YougramExpressive.enabled()) {
            return;
        }
        view.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        view.setTextColor(YumiMd3.onSurface());
    }

    static void detail(TextView view) {
        if (!YougramExpressive.enabled()) {
            return;
        }
        view.setTypeface(YumiMd3.typeface(YumiMd3.REGULAR));
    }
}
