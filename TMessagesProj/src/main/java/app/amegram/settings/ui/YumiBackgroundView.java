package app.amegram.settings.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

import app.amegram.theme.YumiMd3;

/**
 * Clean, flat Material 3 surface backdrop.
 * Aurora gradients, noise grain and decorative rings have been removed.
 */
public class YumiBackgroundView extends View {

    public YumiBackgroundView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setBackgroundColor(YumiMd3.surface());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // Flat solid background handled by View.setBackgroundColor
    }
}
