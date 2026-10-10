package app.amegram.settings;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Animated top banner for Yumigram Updates matching the MD3 Yumi UI concept.
 * Features a 26dp rounded container, central 12-lobed scalloped badge with
 * Telegram plane, and organic floating geometric shapes that drift gently
 * in idle state and actively animate during update checking/downloading.
 */
public class YumiUpdateBannerView extends View {

    private final Paint bannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shapePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bannerRect = new RectF();
    private final Path clipPath = new Path();

    private boolean isChecking = false;
    private boolean isDownloading = false;
    private long startTime;
    private Drawable telegramIcon;

    private static class FloatingShape {
        float relX, relY;
        float sizeDp;
        int color;
        int type; // 0 = scalloped blob, 1 = rounded triangle, 2 = rounded squircle
        int lobes;
        float rotSpeed;
        float speedX, speedY;
        float phase;

        FloatingShape(float relX, float relY, float sizeDp, int color, int type, int lobes, float rotSpeed, float speedX, float speedY, float phase) {
            this.relX = relX;
            this.relY = relY;
            this.sizeDp = sizeDp;
            this.color = color;
            this.type = type;
            this.lobes = lobes;
            this.rotSpeed = rotSpeed;
            this.speedX = speedX;
            this.speedY = speedY;
            this.phase = phase;
        }
    }

    private final FloatingShape[] shapes = new FloatingShape[]{
            // Top-left rounded triangle
            new FloatingShape(0.22f, 0.22f, 32f, 0xFF6C6C72, 1, 3, 0.02f, 0.0012f, 0.0010f, 0.0f),
            // Top-right rounded wedge
            new FloatingShape(0.60f, 0.18f, 48f, 0xFF66666C, 1, 3, -0.015f, 0.0009f, 0.0014f, 1.2f),
            // Bottom-left rounded triangle
            new FloatingShape(0.31f, 0.80f, 38f, 0xFF68686E, 1, 3, 0.025f, 0.0014f, 0.0011f, 2.5f),
            // Bottom-right scalloped blob
            new FloatingShape(0.84f, 0.82f, 62f, 0xFF424246, 0, 10, -0.018f, 0.0011f, 0.0013f, 3.8f),
            // Left edge scalloped blob
            new FloatingShape(0.04f, 0.38f, 46f, 0xFF68686E, 0, 8, 0.02f, 0.0008f, 0.0012f, 4.5f),
            // Top-right far scalloped blob
            new FloatingShape(0.92f, 0.20f, 54f, 0xFF424246, 0, 10, 0.016f, 0.0013f, 0.0009f, 5.2f)
    };

    public YumiUpdateBannerView(Context context) {
        super(context);
        init();
    }

    public YumiUpdateBannerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        startTime = SystemClock.uptimeMillis();
        badgePaint.setColor(0xFF525258);
        try {
            telegramIcon = ContextCompat.getDrawable(getContext(), R.drawable.msg_send);
        } catch (Throwable ignore) {
        }
    }

    public void setChecking(boolean checking) {
        this.isChecking = checking;
        postInvalidateOnAnimation();
    }

    public void setDownloading(boolean downloading) {
        this.isDownloading = downloading;
        postInvalidateOnAnimation();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = AndroidUtilities.dp(196);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final float radius = AndroidUtilities.dp(26);
        bannerRect.set(0, 0, w, h);

        // Dark background matching screenshot 15 (#1B1B1E)
        int bgColor = Theme.isCurrentThemeDark() ? 0xFF1D1D21 : 0xFF2A2A2E;
        bannerPaint.setColor(bgColor);

        clipPath.rewind();
        clipPath.addRoundRect(bannerRect, radius, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawRect(bannerRect, bannerPaint);

        final long now = SystemClock.uptimeMillis();
        final long elapsed = now - startTime;

        final float animSpeedMult = (isChecking || isDownloading) ? 3.5f : 1.0f;
        final float driftAmp = (isChecking || isDownloading) ? AndroidUtilities.dp(12) : AndroidUtilities.dp(4);

        // 1. Draw floating surrounding shapes
        for (FloatingShape shape : shapes) {
            float basePxX = w * shape.relX;
            float basePxY = h * shape.relY;

            float offsetX = (float) Math.sin((elapsed * shape.speedX * animSpeedMult) + shape.phase) * driftAmp;
            float offsetY = (float) Math.cos((elapsed * shape.speedY * animSpeedMult) + shape.phase) * driftAmp;

            float curX = basePxX + offsetX;
            float curY = basePxY + offsetY;
            float curRot = (elapsed * shape.rotSpeed * animSpeedMult) % 360f;

            shapePaint.setColor(shape.color);
            float sizePx = AndroidUtilities.dp(shape.sizeDp);

            canvas.save();
            canvas.translate(curX, curY);
            canvas.rotate(curRot);

            if (shape.type == 0) {
                // Scalloped blob
                Path blob = createScallopedPath(0, 0, sizePx * 0.42f, sizePx * 0.08f, shape.lobes, 0);
                canvas.drawPath(blob, shapePaint);
            } else if (shape.type == 1) {
                // Rounded triangle / wedge
                Path tri = createRoundedTrianglePath(sizePx * 0.48f, AndroidUtilities.dp(8));
                canvas.drawPath(tri, shapePaint);
            } else {
                // Rounded squircle
                RectF sqRect = new RectF(-sizePx * 0.4f, -sizePx * 0.4f, sizePx * 0.4f, sizePx * 0.4f);
                canvas.drawRoundRect(sqRect, AndroidUtilities.dp(12), AndroidUtilities.dp(12), shapePaint);
            }
            canvas.restore();
        }

        // 2. Center Scalloped Badge (12 lobes, radius ~46dp)
        final float cx = w / 2f;
        final float cy = h / 2f;
        final float badgeBaseR = AndroidUtilities.dp(44);
        final float badgeLobeDepth = AndroidUtilities.dp(5.5f);

        // Center badge rotation & subtle breathing
        float badgeRot = (isChecking || isDownloading)
                ? (elapsed * 0.04f) % 360f
                : (float) Math.sin(elapsed * 0.001f) * 4f;

        float badgeScale = (isChecking || isDownloading)
                ? 1.0f + (float) Math.sin(elapsed * 0.006f) * 0.06f
                : 1.0f;

        // Rotating accent ring during active checking / downloading
        if (isChecking || isDownloading) {
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            ringPaint.setStyle(Paint.Style.STROKE);
            ringPaint.setStrokeCap(Paint.Cap.ROUND);
            ringPaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
            ringPaint.setColor(0x77FFFFFF);
            float ringR = badgeBaseR * badgeScale + AndroidUtilities.dp(9);
            RectF arcRect = new RectF(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            float startAngle = (elapsed * 0.18f) % 360f;
            canvas.drawArc(arcRect, startAngle, 85f, false, ringPaint);
            canvas.drawArc(arcRect, (startAngle + 180f) % 360f, 85f, false, ringPaint);
        }

        Path centerBadgePath = createScallopedPath(cx, cy, badgeBaseR * badgeScale, badgeLobeDepth * badgeScale, 12, badgeRot);
        badgePaint.setColor(0xFF55555C);
        canvas.drawPath(centerBadgePath, badgePaint);

        // 3. Central Telegram Plane Icon in pure white with lively motion
        if (telegramIcon != null) {
            final int iconSize = AndroidUtilities.dp(42);
            canvas.save();
            canvas.translate(cx, cy);
            if (isChecking || isDownloading) {
                float tilt = (float) Math.sin(elapsed * 0.005f) * 7f;
                float planeScale = 1.0f + (float) Math.sin(elapsed * 0.006f) * 0.07f;
                canvas.rotate(tilt);
                canvas.scale(planeScale, planeScale);
            }
            telegramIcon.setBounds(-iconSize / 2, -iconSize / 2, iconSize / 2, iconSize / 2);
            telegramIcon.setColorFilter(new android.graphics.PorterDuffColorFilter(0xFFFFFFFF, android.graphics.PorterDuff.Mode.SRC_IN));
            telegramIcon.draw(canvas);
            canvas.restore();
        }

        canvas.restore();

        // Keep continuous animation running smoothly when visible
        if (getVisibility() == VISIBLE) {
            postInvalidateOnAnimation();
        }
    }

    private Path createScallopedPath(float cx, float cy, float radius, float lobeDepth, int lobes, float rotationDeg) {
        Path path = new Path();
        int points = lobes * 10;
        for (int i = 0; i <= points; i++) {
            double angle = Math.toRadians((i * 360.0 / points) + rotationDeg);
            double r = radius + lobeDepth * Math.cos(lobes * angle);
            float x = (float) (cx + r * Math.cos(angle));
            float y = (float) (cy + r * Math.sin(angle));
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.close();
        return path;
    }

    private Path createRoundedTrianglePath(float radius, float cornerRadius) {
        Path path = new Path();
        float h = radius * 1.5f;
        float w = radius * 1.732f;
        RectF r = new RectF(-w / 2f, -h / 2f, w / 2f, h / 2f);
        path.moveTo(0, r.top);
        path.lineTo(r.right, r.bottom);
        path.lineTo(r.left, r.bottom);
        path.close();
        return path;
    }
}
