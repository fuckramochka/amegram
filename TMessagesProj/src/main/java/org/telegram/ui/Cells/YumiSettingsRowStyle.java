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
        // No-op: settings rows are hosted within section cards and must not have
        // individual nested outline capsules or artificial inset margins.
    }

    static void title(TextView view) {
        if (!YougramExpressive.enabled() || view == null) {
            return;
        }
        view.setTypeface(YumiMd3.typeface(YumiMd3.MEDIUM));
        view.setTextColor(YumiMd3.onSurface());
    }

    static void detail(TextView view) {
        if (!YougramExpressive.enabled() || view == null) {
            return;
        }
        view.setTypeface(YumiMd3.typeface(YumiMd3.REGULAR));
        view.setTextColor(YumiMd3.onSurfaceVariant());
    }
}
