package app.miogram.ui.yumi;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.telegram.messenger.AndroidUtilities;

/**
 * A rounded rect with independent corners, used by grouped cells and cards.
 * Paths are rebuilt only when bounds or corner values change; drawing does not
 * allocate.
 */
public class YumiRoundRectDrawable extends Drawable {

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path fillPath = new Path();
    private final Path strokePath = new Path();
    private final RectF rect = new RectF();
    private final float[] radii = new float[8];
    private final float[] strokeRadii = new float[8];

    private int fillColor;
    private int strokeColor;
    private float strokeWidthPx;
    private int alpha = 255;

    public YumiRoundRectDrawable(int fillColor, float radiusDp) {
        this(fillColor, radiusDp, 0, 0);
    }

    public YumiRoundRectDrawable(int fillColor, float radiusDp, int strokeColor, float strokeWidthDp) {
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.strokeWidthPx = AndroidUtilities.dp(strokeWidthDp);
        setCornerRadiiDp(radiusDp, radiusDp, radiusDp, radiusDp);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(this.strokeWidthPx);
        updatePaints();
    }

    public void setFillColor(int color) {
        if (fillColor != color) {
            fillColor = color;
            updatePaints();
            invalidateSelf();
        }
    }

    public void setStroke(int color, float widthDp) {
        strokeColor = color;
        strokeWidthPx = AndroidUtilities.dp(widthDp);
        strokePaint.setStrokeWidth(strokeWidthPx);
        rebuildPaths();
        invalidateSelf();
    }

    public void setCornerRadiiDp(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        final float tl = AndroidUtilities.dp(topLeft);
        final float tr = AndroidUtilities.dp(topRight);
        final float br = AndroidUtilities.dp(bottomRight);
        final float bl = AndroidUtilities.dp(bottomLeft);
        radii[0] = tl;
        radii[1] = tl;
        radii[2] = tr;
        radii[3] = tr;
        radii[4] = br;
        radii[5] = br;
        radii[6] = bl;
        radii[7] = bl;
        rebuildPaths();
        invalidateSelf();
    }

    public void setUniformRadiusDp(float radiusDp) {
        setCornerRadiiDp(radiusDp, radiusDp, radiusDp, radiusDp);
    }

    private void updatePaints() {
        fillPaint.setColor(fillColor);
        fillPaint.setAlpha(alpha);
        strokePaint.setColor(strokeColor);
        strokePaint.setAlpha(alpha);
        invalidateSelf();
    }

    private void rebuildPaths() {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            fillPath.reset();
            strokePath.reset();
            return;
        }
        fillPath.reset();
        rect.set(bounds);
        fillPath.addRoundRect(rect, radii, Path.Direction.CW);

        strokePath.reset();
        final float inset = strokeWidthPx / 2f;
        rect.set(bounds.left + inset, bounds.top + inset,
                bounds.right - inset, bounds.bottom - inset);
        final float max = Math.max(0, Math.max(Math.max(radii[0], radii[2]),
                Math.max(radii[4], radii[6])) - inset);
        for (int i = 0; i < strokeRadii.length; i++) {
            strokeRadii[i] = Math.max(0, radii[i] - inset);
        }
        if (strokeWidthPx > 0) {
            strokePath.addRoundRect(rect, strokeRadii, Path.Direction.CW);
        }
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        super.onBoundsChange(bounds);
        rebuildPaths();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (fillPath.isEmpty()) {
            return;
        }
        canvas.drawPath(fillPath, fillPaint);
        if (strokeWidthPx > 0 && strokeColor != 0 && !strokePath.isEmpty()) {
            canvas.drawPath(strokePath, strokePaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
        updatePaints();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void getOutline(@NonNull Outline outline) {
        final Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        final float radius = (radii[0] + radii[2] + radii[4] + radii[6]) / 4f;
        outline.setRoundRect(bounds, radius);
    }
}
