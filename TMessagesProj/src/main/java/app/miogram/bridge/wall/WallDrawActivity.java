package app.miogram.bridge.wall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import app.miogram.bridge.MiogramLocale;

import java.util.ArrayList;
import java.util.List;

/**
 * Полотно малювання: неонова кисть зі сяйвом, палітра кольорів,
 * товщина, ластик, крок назад, збереження PNG у сховище стіни.
 */
public class WallDrawActivity extends Activity {

    public static final String EXTRA_PATH = "path";
    public static final String EXTRA_W = "width";
    public static final String EXTRA_H = "height";

    private static final int[] PALETTE = {
            0xFFFF4D8D, 0xFF8A5CFF, 0xFF35E0FF, 0xFF9DFF5C, 0xFFFFC24D, 0xFFFF5CD6, 0xFFFFFFFF
    };

    private DrawCanvas canvas;
    private LinearLayout paletteRow;
    private int brushColor = 0xFFFF4D8D;
    private float brushSize = 12f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF070512);

        View bg = new View(this) {
            private final android.graphics.Paint p = new android.graphics.Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas c) {
                p.setShader(new RadialGradient(getWidth() * 0.2f, getHeight() * 0.08f, Math.max(getWidth(), getHeight()) * 0.8f,
                        new int[]{0x2EFF4D8D, 0x00FF4D8D}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
                p.setShader(new RadialGradient(getWidth() * 0.92f, getHeight() * 0.8f, Math.max(getWidth(), getHeight()) * 0.7f,
                        new int[]{0x2E8A5CFF, 0x008A5CFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, 0, getWidth(), getHeight(), p);
            }
        };
        root.addView(bg, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // шапка
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(10));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
        back.setTypeface(Typeface.DEFAULT_BOLD);
        back.setOnClickListener(v -> {
            setResult(Activity.RESULT_CANCELED);
            finish();
        });
        header.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout titleWrap = new LinearLayout(this);
        titleWrap.setOrientation(LinearLayout.VERTICAL);
        titleWrap.setPadding(AndroidUtilities.dp(12), 0, 0, 0);
        TextView title = new TextView(this);
        title.setText(MiogramLocale.get("✏ Малюй", "✏ Рисуй", "✏ Draw"));
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        titleWrap.addView(title);
        TextView sub = new TextView(this);
        sub.setText(MiogramLocale.get("Намальоване стане прозорою картиною на стіні",
                "Нарисованное станет прозрачной картинкой на стене",
                "Your drawing becomes a transparent sticker on the wall"));
        sub.setTextColor(0x88FFFFFF);
        sub.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 11);
        titleWrap.addView(sub);
        header.addView(titleWrap, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView undo = new TextView(this);
        undo.setText("↺");
        undo.setTextColor(0x99FFFFFF);
        undo.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 18);
        undo.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        undo.setOnClickListener(v -> {
            undo.setTextColor(0xFFFFFFFF);
            if (canvas != null) canvas.undo();
        });
        header.addView(undo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView clear = new TextView(this);
        clear.setText("🗑");
        clear.setTextColor(0x99FFFFFF);
        clear.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        clear.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        clear.setBackground(WallUi.pill(0x00000000, 0, 16));
        clear.setOnClickListener(v -> {
            clear.setTextColor(0xFFFF6B81);
            if (canvas != null) canvas.clear();
        });
        header.addView(clear, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView eraser = new TextView(this);
        eraser.setText("⌫");
        eraser.setTextColor(0x99FFFFFF);
        eraser.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15);
        eraser.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(6), 0);
        eraser.setBackground(WallUi.pill(0x00000000, 0, 14));
        eraser.setOnClickListener(v -> {
            if (canvas == null) return;
            boolean on = canvas.toggleEraser();
            eraser.setTextColor(on ? 0xFF35E0FF : 0x99FFFFFF);
            eraser.setBackground(WallUi.pill(on ? 0x3335E0FF : 0x00000000, 0, 14));
        });
        header.addView(eraser, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // полотно
        canvas = new DrawCanvas(this);
        canvas.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(AndroidUtilities.dp(14), 0, view.getWidth(), view.getHeight(), AndroidUtilities.dp(4));
            }
        });
        canvas.setClipToOutline(true);
        LinearLayout.LayoutParams canvasLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        canvasLp.setMargins(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), 0);
        column.addView(canvas, canvasLp);

        // палітра
        paletteRow = new LinearLayout(this);
        paletteRow.setOrientation(LinearLayout.HORIZONTAL);
        paletteRow.setGravity(Gravity.CENTER);
        paletteRow.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(12));
        column.addView(paletteRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        buildPalette();

        // кнопка зберегти
        TextView save = new TextView(this);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(0xFFFFFFFF);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        save.setText(MiogramLocale.get("Зберегти на стіну", "Сохранить на стену", "Save to wall"));
        save.setBackground(WallUi.brandPill(24));
        save.setMinHeight(AndroidUtilities.dp(50));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.setMargins(AndroidUtilities.dp(14), 0, AndroidUtilities.dp(14), AndroidUtilities.dp(14));
        column.addView(save, saveLp);
        save.setOnClickListener(v -> {
            WallUi.hapticClick(v);
            saveImage();
        });

        setContentView(root);
    }

    private void buildPalette() {
        paletteRow.removeAllViews();
        for (final int color : PALETTE) {
            View swatch = new View(this);
            swatch.setBackground(WallUi.pill(color, 0, 18));
            swatch.setOnClickListener(v -> {
                brushColor = color;
                if (canvas != null) {
                    canvas.setStrokeColor(color);
                    canvas.setEraser(false);
                }
                for (int i = 0; i < paletteRow.getChildCount(); i++) {
                    View c = paletteRow.getChildAt(i);
                    if (c instanceof TextView) continue;
                    boolean selected = ((LinearLayout.LayoutParams) c.getLayoutParams()).width == AndroidUtilities.dp(34);
                    c.animate().scaleX(c == v ? 1.3f : 1f).scaleY(c == v ? 1.3f : 1f).setDuration(150).start();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(AndroidUtilities.dp(32), AndroidUtilities.dp(32));
            lp.rightMargin = AndroidUtilities.dp(10);
            paletteRow.addView(swatch, lp);
        }

        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        float[] sizes = {6f, 12f, 22f, 34f};
        for (final float size : sizes) {
            TextView sizeBtn = new TextView(this);
            sizeBtn.setText("●");
            sizeBtn.setTextColor(size == brushSize ? 0xFFFFFFFF : 0x55FFFFFF);
            sizeBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 9 + size / 3.4f);
            sizeBtn.setPadding(AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8), 0);
            sizeBtn.setOnClickListener(v -> {
                brushSize = size;
                if (canvas != null) {
                    canvas.setStrokeWidth(size);
                    canvas.setEraser(false);
                }
                for (int i = 0; i < sizeRow.getChildCount(); i++) {
                    TextView b = (TextView) sizeRow.getChildAt(i);
                    b.setTextColor(0x55FFFFFF);
                }
                sizeBtn.setTextColor(0xFFFFFFFF);
            });
            sizeRow.addView(sizeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        paletteRow.addView(sizeRow, lp);
    }

    private void saveImage() {
        if (canvas == null || !canvas.hasStrokes()) {
            Toast.makeText(this, MiogramLocale.get("Намалюй щось спочатку", "Нарисуй что-нибудь сначала", "Draw something first"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bmp = canvas.renderBitmap(1080);
        if (bmp == null) {
            return;
        }
        long ownerId = WallPrefs.lastOwner();
        if (ownerId == 0) {
            ownerId = WallStorage.currentUserId();
        }
        String path = WallStorage.savePng(ownerId, bmp);
        bmp.recycle();
        Intent data = new Intent();
        data.putExtra(EXTRA_PATH, path);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, o);
        data.putExtra(EXTRA_W, o.outWidth);
        data.putExtra(EXTRA_H, o.outHeight);
        setResult(Activity.RESULT_OK, data);
        finish();
    }

    /** Полотно з неоновими штрихами. */
    private class DrawCanvas extends View {

        private final List<Stroke> strokes = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Stroke current;
        private int color = 0xFFFF4D8D;
        private float width = 12f;
        private boolean eraserMode;

        private class Stroke {
            final Path path = new Path();
            final int color;
            final float width;
            final boolean eraser;
            float lastX, lastY;

            Stroke(int color, float width, boolean eraser) {
                this.color = color;
                this.width = width;
                this.eraser = eraser;
            }
        }

        DrawCanvas(Context context) {
            super(context);
            setLayerType(LAYER_TYPE_SOFTWARE, null);
            gridPaint.setStrokeWidth(AndroidUtilities.dp(0.7f));
            gridPaint.setColor(0x12FFFFFF);
        }

        void setStrokeColor(int c) {
            color = c;
        }

        void setStrokeWidth(float w) {
            width = w;
        }

        void setEraser(boolean e) {
            eraserMode = e;
        }

        boolean toggleEraser() {
            eraserMode = !eraserMode;
            return eraserMode;
        }

        boolean hasStrokes() {
            return !strokes.isEmpty();
        }

        void undo() {
            if (!strokes.isEmpty()) {
                strokes.remove(strokes.size() - 1);
                invalidate();
            }
        }

        void clear() {
            strokes.clear();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getMeasuredWidth(), h = getMeasuredHeight();
            int step = AndroidUtilities.dp(26);
            for (int x = step; x < w; x += step) {
                canvas.drawLine(x, AndroidUtilities.dp(10), x, h - AndroidUtilities.dp(10), gridPaint);
            }
            for (int y = step; y < h; y += step) {
                canvas.drawLine(AndroidUtilities.dp(10), y, w - AndroidUtilities.dp(10), y, gridPaint);
            }
            for (Stroke s : strokes) {
                drawStroke(canvas, s);
            }
            if (current != null) {
                drawStroke(canvas, current);
            }
        }

        private void drawStroke(Canvas c, Stroke s) {
            if (s.eraser) {
                paint.reset();
                paint.setAntiAlias(true);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(AndroidUtilities.dp(20), s.width * 3.4f));
                paint.setStrokeCap(Paint.Cap.ROUND);
                paint.setStrokeJoin(Paint.Join.ROUND);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                c.drawPath(s.path, paint);
                paint.setXfermode(null);
                return;
            }
            // сяйво
            glowPaint.reset();
            glowPaint.setAntiAlias(true);
            glowPaint.setStyle(Paint.Style.STROKE);
            glowPaint.setStrokeCap(Paint.Cap.ROUND);
            glowPaint.setStrokeJoin(Paint.Join.ROUND);
            glowPaint.setStrokeWidth(Math.max(AndroidUtilities.dp(1.5f), s.width * 1.9f));
            glowPaint.setColor(s.color);
            glowPaint.setAlpha(70);
            glowPaint.setMaskFilter(new android.graphics.BlurMaskFilter(Math.max(2, s.width * 0.8f), android.graphics.BlurMaskFilter.Blur.NORMAL));
            c.drawPath(s.path, glowPaint);
            // основний штрих
            paint.reset();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Math.max(AndroidUtilities.dp(1.5f), s.width));
            paint.setColor(s.color);
            c.drawPath(s.path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float x = event.getX(), y = event.getY();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    current = new Stroke(brushColor, brushSize, eraserMode);
                    current.lastX = x;
                    current.lastY = y;
                    current.path.moveTo(x, y);
                    invalidate();
                    WallUi.hapticTap(this);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (current != null) {
                        float midX = (current.lastX + x) / 2f;
                        float midY = (current.lastY + y) / 2f;
                        current.path.quadTo(current.lastX, current.lastY, midX, midY);
                        current.lastX = x;
                        current.lastY = y;
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (current != null) {
                        current.path.lineTo(x, y);
                        strokes.add(current);
                        current = null;
                        invalidate();
                    }
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        }

        /** Рендер у PNG: білий світиться поверх прозорого. */
        public Bitmap renderBitmap(int maxSide) {
            int w = Math.max(1, getMeasuredWidth());
            int h = Math.max(1, getMeasuredHeight());
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            for (Stroke s : strokes) {
                drawStroke(c, s);
            }
            return bmp;
        }
    }
}
