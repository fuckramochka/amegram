package app.miogram.bridge.wall;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;

/** Аватарка автора елемента: неоновий градієнт + ініціали. Малюється вручну. */
public class WallAvatarView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String text = "?";
    private Shader shader;
    private int color = WallUi.PINK;

    public WallAvatarView(android.content.Context context) {
        super(context);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextSize(AndroidUtilities.dp(13));
        textPaint.setTypeface(AndroidUtilities.bold());
        textPaint.setTextAlign(Paint.Align.CENTER);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(AndroidUtilities.dp(1));
        ringPaint.setColor(0x40FFFFFF);
    }

    public void setData(String name, long id) {
        text = WallUi.initials(name);
        color = WallUi.colorForId(id);
        shader = new LinearGradient(0, 0, AndroidUtilities.dp(30), AndroidUtilities.dp(30),
                new int[]{color, WallUi.cycle(color, -2)}, null, Shader.TileMode.CLAMP);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = getMeasuredWidth() / 2f;
        paint.setShader(shader);
        paint.setAlpha(255);
        canvas.drawCircle(r, r, r - AndroidUtilities.dp(1), paint);
        canvas.drawCircle(r, r, r - AndroidUtilities.dp(1), ringPaint);
        float y = r + AndroidUtilities.dp(4.6f);
        canvas.drawText(text, r, y, textPaint);
    }

    private static int cycle(int color, int shift) {
        int[] palette = WallUi.NEON_PALETTE;
        int idx = 0;
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] == color) {
                idx = i;
                break;
            }
        }
        return palette[(idx + shift + palette.length) % palette.length];
    }
}
