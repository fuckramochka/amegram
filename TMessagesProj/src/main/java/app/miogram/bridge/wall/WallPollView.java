package app.miogram.bridge.wall;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PathEffect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import org.telegram.messenger.AndroidUtilities;

import java.util.Locale;

import app.miogram.bridge.wall.WallModels.WallElement;
import app.miogram.bridge.wall.WallModels.WallPoll;

/**
 * Опитування-слайдер: тягнеш повзунок, після голосування бачиш
 * граф розподілу відповідей (10 кошиків) з маркером «свого» голосу та середнім.
 */
public class WallPollView extends View {

    public interface PollListener {
        void onVoteChanged(int value);
    }

    private static final int MODE_SLIDER = 0;
    private static final int MODE_GRAPH = 1;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubblePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private WallElement element;
    private PollListener listener;
    private int mode = MODE_SLIDER;
    private int value = 50;
    private float anim = 1f;
    private ValueAnimator animator;
    private boolean dragging;
    private Shader fillShader;
    private Shader barShader;
    private int cachedWidthForShader = -1;
    private int cachedColorA = 0, cachedColorB = 0;
    private long myId;

    public WallPollView(Context context) {
        super(context);
        labelPaint.setTextSize(AndroidUtilities.dp(11));
        labelPaint.setColor(0x99FFFFFF);
        valuePaint.setTextSize(AndroidUtilities.dp(12.5f));
        valuePaint.setTypeface(AndroidUtilities.bold());
        valuePaint.setColor(0xFFFFFFFF);
        valuePaint.setTextAlign(Paint.Align.CENTER);
        dashPaint.setStyle(Paint.Style.STROKE);
        dashPaint.setStrokeWidth(AndroidUtilities.dp(1.2f));
        dashPaint.setColor(0x88FFFFFF);
        PathEffect effect = new DashPathEffect(new float[]{AndroidUtilities.dp(6), AndroidUtilities.dp(5)}, 0);
        dashPaint.setPathEffect(effect);
        thumbPaint.setColor(0xFFFFFFFF);
        avPaint.setStyle(Paint.Style.STROKE);
        avPaint.setStrokeWidth(AndroidUtilities.dp(2));
    }

    public void setData(WallElement e, long myId, PollListener l) {
        element = e;
        this.myId = myId;
        listener = l;
        Integer my = e.myVote(myId);
        if (my != null) {
            mode = MODE_GRAPH;
            value = my;
        } else {
            mode = MODE_SLIDER;
            value = 50;
        }
        int min = e.poll == null ? 0 : e.poll.min;
        int max = e.poll == null ? 100 : e.poll.max;
        if (max <= min) max = min + 100;
        float span = max - min;
        // нормалізуємо у відсотки діапазону 0..100
        value = Math.max(0, Math.min(100, Math.round((value - min) / span * 100f)));
        prepareShaders();
        animateIn();
        requestLayout();
        invalidate();
    }

    private void animateIn() {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(520);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            anim = (float) a.getAnimatedValue();
            if (anim > 1f) anim = 1f;
            invalidate();
        });
        animator.start();
    }

    private int colorA() {
        return element != null && element.poll != null && element.poll.color1 != null
                ? WallUi.resolveColor(getContext(), element.poll.color1, WallUi.PINK) : WallUi.PINK;
    }

    private int colorB() {
        return element != null && element.poll != null && element.poll.color2 != null
                ? WallUi.resolveColor(getContext(), element.poll.color2, WallUi.VIOLET) : WallUi.VIOLET;
    }

    private void prepareShaders() {
        int a = colorA(), b = colorB();
        if (cachedWidthForShader != getMeasuredWidth() || cachedColorA != a || cachedColorB != b) {
            int w = Math.max(200, getMeasuredWidth());
            fillShader = new LinearGradient(0, 0, w, 0, new int[]{a, b}, null, Shader.TileMode.CLAMP);
            barShader = new LinearGradient(0, 0, 0, AndroidUtilities.dp(110), new int[]{a, b}, null, Shader.TileMode.CLAMP);
            cachedWidthForShader = getMeasuredWidth();
            cachedColorA = a;
            cachedColorB = b;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int h = mode == MODE_GRAPH ? AndroidUtilities.dp(158) : AndroidUtilities.dp(84);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animator != null) animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mode == MODE_SLIDER) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    dragging = true;
                    float x = event.getX();
                    float pad = AndroidUtilities.dp(18);
                    float t = (x - pad) / (getMeasuredWidth() - pad * 2f);
                    value = Math.round(Math.max(0, Math.min(1f, t)) * 100f);
                    invalidate();
                    if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
                        WallUi.hapticTap(this);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    if (listener != null && element != null && element.poll != null) {
                        int min = Math.min(element.poll.min, element.poll.max);
                        int max = Math.max(element.poll.min, element.poll.max);
                        float span = Math.max(1, max - min);
                        int raw = Math.round(min + value / 100f * span);
                        listener.onVoteChanged(raw);
                    }
                    WallUi.hapticClick(this);
                    return true;
                default:
                    return super.onTouchEvent(event);
            }
        } else {
            if (element == null || element.poll == null) {
                return super.onTouchEvent(event);
            }
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                Integer my = element.myVote(myId);
                mode = MODE_SLIDER;
                int min = element.poll.min, max = Math.max(element.poll.max, element.poll.min + 1);
                float span = max - min;
                int raw = my != null ? my : Math.round(min + span / 2f);
                value = Math.max(0, Math.min(100, Math.round((raw - min) / span * 100f)));
                requestLayout();
                invalidate();
                WallUi.hapticTap(this);
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        prepareShaders();
        int pad = AndroidUtilities.dp(18);
        int w = getMeasuredWidth() - pad * 2;
        if (mode == MODE_SLIDER) {
            drawSlider(canvas, pad, w);
        } else {
            drawGraph(canvas, pad, w);
        }
    }

    private void drawSlider(Canvas canvas, int pad, int w) {
        int cy = AndroidUtilities.dp(34);
        float half = AndroidUtilities.dp(7);
        trackPaint.setColor(0x2EFFFFFF);
        rect.set(pad, cy - half, pad + w, cy + half);
        canvas.drawRoundRect(rect, half, half, trackPaint);

        float fx = pad + w * (value / 100f) * anim;
        rect.set(pad, cy - half, fx, cy + half);
        fillPaint.setShader(fillShader);
        fillPaint.setAlpha(255);
        canvas.drawRoundRect(rect, half, half, fillPaint);

        labelPaint.setTextAlign(Paint.Align.LEFT);
        String minLabel = element != null && element.poll != null && !android.text.TextUtils.isEmpty(element.poll.minLabel)
                ? element.poll.minLabel : "0";
        String maxLabel = element != null && element.poll != null && !android.text.TextUtils.isEmpty(element.poll.maxLabel)
                ? element.poll.maxLabel : "100";
        canvas.drawText(minLabel, pad, cy + AndroidUtilities.dp(30), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(maxLabel, pad + w, cy + AndroidUtilities.dp(30), labelPaint);

        // бібл значення
        String txt = value + "%";
        float tw = valuePaint.measureText(txt) + AndroidUtilities.dp(16);
        float bx = Math.max(pad + tw / 2f, Math.min(pad + w - tw / 2f, fx));
        float bTop = cy - AndroidUtilities.dp(30);
        rect.set(bx - tw / 2f, bTop, bx + tw / 2f, bTop + AndroidUtilities.dp(20));
        bubblePaint.setColor(0xE6140F2E);
        bubblePaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), bubblePaint);
        fillPaint.setShader(fillShader);
        fillPaint.setAlpha(60);
        rect.inset(AndroidUtilities.dp(0.7f), AndroidUtilities.dp(3.3f));
        canvas.drawRoundRect(rect, AndroidUtilities.dp(6), AndroidUtilities.dp(6), fillPaint);
        canvas.drawText(txt, bx, bTop + AndroidUtilities.dp(14), valuePaint);

        // thumb
        thumbPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(fx, cy, AndroidUtilities.dp(9), thumbPaint);
        fillPaint.setShader(fillShader);
        fillPaint.setAlpha(255);
        canvas.drawCircle(fx, cy, AndroidUtilities.dp(5), fillPaint);
    }

    private void drawGraph(Canvas canvas, int pad, int w) {
        if (element == null || element.poll == null) return;
        WallPoll poll = element.poll;
        int[] buckets = new int[10];
        int votes = 0;
        if (poll.votes != null) {
            int min = poll.min, max = Math.max(poll.max, poll.min + 1);
            float span = max - min;
            for (Integer v : poll.votes.values()) {
                if (v == null) continue;
                float norm = (v - min) / span;
                int idx = (int) (Math.max(0, Math.min(0.999f, norm)) * 10f);
                buckets[idx]++;
                votes++;
            }
        }
        int maxCount = 1;
        for (int c : buckets) maxCount = Math.max(maxCount, c);

        int base = AndroidUtilities.dp(104);
        int barW = w / 10;
        barPaint.setShader(barShader);
        for (int i = 0; i < 10; i++) {
            float h = (buckets[i] / (float) maxCount) * AndroidUtilities.dp(68) * anim;
            if (h < AndroidUtilities.dp(2)) h = AndroidUtilities.dp(2);
            float bx = pad + i * barW + AndroidUtilities.dp(3);
            rect.set(bx, base - h, bx + barW - AndroidUtilities.dp(5), base);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), barPaint);
            if (buckets[i] > 0) {
                valuePaint.setTextAlign(Paint.Align.CENTER);
                String cnt = String.valueOf(buckets[i]);
                canvas.drawText(cnt, bx + (barW - AndroidUtilities.dp(5)) / 2f, base - h - AndroidUtilities.dp(4), valuePaint);
            }
        }
        // оцінка 0..100 по осі
        labelPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("0", pad, base + AndroidUtilities.dp(16), labelPaint);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("50", pad + w / 2f, base + AndroidUtilities.dp(16), labelPaint);
        labelPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("100", pad + w, base + AndroidUtilities.dp(16), labelPaint);
        // підпис
        valuePaint.setTextAlign(Paint.Align.RIGHT);
        String votesText = votes + (votes == 1 ? " голос" : " голосів");
        canvas.drawText(votesText, pad + w, base - AndroidUtilities.dp(80), valuePaint);

        // мій голос: крапка
        Integer my = element.myVote(myId);
        if (my != null && poll.votes != null) {
            int min = poll.min, max = Math.max(poll.max, poll.min + 1);
            float norm = Math.max(0, Math.min(1f, (my - min) / (float) (max - min)));
            float mx = pad + w * norm;
            thumbPaint.setColor(0xFFFFFFFF);
            canvas.drawCircle(mx, AndroidUtilities.dp(14), AndroidUtilities.dp(4), thumbPaint);
            canvas.drawLine(mx, AndroidUtilities.dp(18), mx, base, dashPaint);
            bubblePaint.setColor(0xFF14102A);
            String myTxt = "ти: " + my + "%";
            float tw = valuePaint.measureText(myTxt) + AndroidUtilities.dp(14);
            float bxp = Math.min(pad + w - tw / 2f, mx + AndroidUtilities.dp(14));
            rect.set(bxp - tw / 2f, AndroidUtilities.dp(4), bxp + tw / 2f, AndroidUtilities.dp(24));
            canvas.drawRoundRect(rect, AndroidUtilities.dp(10), AndroidUtilities.dp(10), bubblePaint);
            valuePaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(myTxt, bxp, AndroidUtilities.dp(18), valuePaint);
        }
        // середнє
        int avg = element.averageVote();
        if (avg >= 0) {
            int min = poll.min, max = Math.max(poll.max, poll.min + 1);
            float norm = Math.max(0, Math.min(1f, (avg - min) / (float) (max - min)));
            float cx = pad + w * norm;
            dashPaint.setColor(0x66FFFFFF);
            canvas.drawLine(cx, AndroidUtilities.dp(24), cx, base, dashPaint);
            valuePaint.setTextAlign(Paint.Align.CENTER);
            String avgTxt = "сер. " + avg + "%";
            float tw = labelPaint.measureText(avgTxt) + AndroidUtilities.dp(12);
            float bx2 = Math.max(pad + tw / 2f, Math.min(pad + w - tw / 2f, cx));
            rect.set(bx2 - tw / 2f, base - AndroidUtilities.dp(4), bx2 + tw / 2f, base + AndroidUtilities.dp(16));
            bubblePaint.setColor(0x33000000);
            canvas.drawRoundRect(rect, AndroidUtilities.dp(8), AndroidUtilities.dp(8), bubblePaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(avgTxt, bx2, base + AndroidUtilities.dp(9), labelPaint);
        }
        valuePaint.setTextAlign(Paint.Align.CENTER);
    }
}
