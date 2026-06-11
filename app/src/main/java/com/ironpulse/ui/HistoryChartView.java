package com.ironpulse.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.*;
import android.widget.OverScroller;

import java.util.List;

/**
 * Scrollable bar chart showing ~7 bars at a time. Scrolling is continuous
 * (pixel-smooth) with momentum fling, not snapped a whole bar at a time.
 */
public class HistoryChartView extends View {

    // Full dataset — index 0 = oldest, last = most recent (today)
    private List<Double> values;
    private List<String> labels;
    private int bgColor = 0xFF181E2C;
    private int textCol = 0xFF72889E;

    private static final int VISIBLE_BARS = 7;
    private static final float PAD_L = 96f, PAD_R = 16f;

    private float scrollPx = 0f;          // continuous scroll offset in pixels
    private boolean needInitScroll = true; // jump to the most-recent end on first layout
    private boolean hasScrolled = false;
    private float lastTouchX;

    private final OverScroller scroller;
    private VelocityTracker velocityTracker;
    private final int touchSlop, minFling, maxFling;

    private final Paint barPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint goldPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public HistoryChartView(Context ctx) { this(ctx, null); }
    public HistoryChartView(Context ctx, AttributeSet a) { this(ctx, a, 0); }
    public HistoryChartView(Context ctx, AttributeSet a, int def) {
        super(ctx, a, def);
        scroller = new OverScroller(ctx);
        ViewConfiguration vc = ViewConfiguration.get(ctx);
        touchSlop = vc.getScaledTouchSlop();
        minFling = vc.getScaledMinimumFlingVelocity();
        maxFling = vc.getScaledMaximumFlingVelocity();
        init();
    }

    private void init() {
        barPaint.setColor(0xFF22C882);
        goldPaint.setColor(0xFFFFB937);
        textPaint.setTextAlign(Paint.Align.CENTER);
        axisPaint.setTextAlign(Paint.Align.RIGHT);
        gridPaint.setStrokeWidth(1f);
        basePaint.setStrokeWidth(2f);
        basePaint.setStyle(Paint.Style.STROKE);
        setClickable(true);
    }

    public void setBgColor(int c)   { bgColor = c; invalidate(); }
    public void setTextColor(int c) { textCol = c; invalidate(); }

    public void setData(List<Double> v, List<String> l) {
        values = v;
        labels = l;
        scrollPx = 0;
        needInitScroll = true;       // jump to most-recent once we know our width
        hasScrolled = false;
        scroller.forceFinished(true);
        invalidate();
    }

    private float barWidth() {
        float chartW = getWidth() - PAD_L - PAD_R;
        return chartW <= 0 ? 1f : chartW / VISIBLE_BARS;
    }

    private float maxScroll() {
        if (values == null || values.size() <= VISIBLE_BARS) return 0;
        return (values.size() - VISIBLE_BARS) * barWidth();
    }

    private float clampScroll(float s) { return Math.max(0, Math.min(s, maxScroll())); }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (values == null || values.size() <= VISIBLE_BARS) return false;
        if (velocityTracker == null) velocityTracker = VelocityTracker.obtain();
        velocityTracker.addMovement(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                scroller.forceFinished(true);
                lastTouchX = e.getX();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastTouchX;
                lastTouchX = e.getX();
                if (Math.abs(dx) > touchSlop / 2f) hasScrolled = true;
                scrollPx = clampScroll(scrollPx - dx);
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                velocityTracker.computeCurrentVelocity(1000, maxFling);
                float vx = velocityTracker.getXVelocity();
                if (Math.abs(vx) > minFling) {
                    scroller.fling((int) scrollPx, 0, (int) -vx, 0, 0, (int) maxScroll(), 0, 0);
                    postInvalidateOnAnimation();
                }
                if (velocityTracker != null) { velocityTracker.recycle(); velocityTracker = null; }
                return true;
        }
        return false;
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollPx = clampScroll(scroller.getCurrX());
            postInvalidateOnAnimation();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bgColor);
        int w = getWidth(), h = getHeight();

        textPaint.setColor(textCol);
        axisPaint.setColor(textCol);
        gridPaint.setColor(0x22888888);
        basePaint.setColor(0x55888888);

        if (values == null || values.isEmpty()) {
            textPaint.setTextSize(34f); textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No completed workouts yet", w / 2f, h / 2f, textPaint);
            return;
        }

        if (needInitScroll && w > 0) { scrollPx = maxScroll(); needInitScroll = false; }

        float padL = PAD_L, padB = 46f, padT = 28f, padR = PAD_R;
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;
        float barW = chartW / VISIBLE_BARS;

        double globalMax = values.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (globalMax == 0) globalMax = 1;
        double tickStep = niceStep(globalMax / 4.0);
        int numTicks = (int) Math.ceil(globalMax / tickStep) + 1;
        double axisMax = tickStep * (numTicks - 1);

        axisPaint.setTextSize(30f);
        for (int i = 0; i < numTicks; i++) {
            double tickVal = tickStep * i;
            float y = padT + chartH * (float)(1.0 - tickVal / axisMax);
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
            canvas.drawText(formatVol(tickVal), padL - 8, y + 10, axisPaint);
        }
        canvas.drawLine(padL, padT + chartH, w - padR, padT + chartH, basePaint);

        // Bars — continuous: draw the window plus one extra on each side, clipped.
        int first = (int) Math.floor(scrollPx / barW);
        float off = scrollPx - first * barW;
        float gap = Math.max(4f, barW * 0.15f);
        float cornerR = Math.min(8f, (barW - gap * 2) / 2f);

        canvas.save();
        canvas.clipRect(padL, 0, w - padR, h);
        for (int i = -1; i <= VISIBLE_BARS; i++) {
            int absIdx = first + i;
            if (absIdx < 0 || absIdx >= values.size()) continue;
            double vol = values.get(absIdx);
            boolean isToday = (absIdx == values.size() - 1);
            Paint p = isToday ? goldPaint : barPaint;

            float left  = padL + i * barW - off + gap;
            float right = padL + (i + 1) * barW - off - gap;
            float barH = vol > 0 ? (float) Math.min(vol / axisMax * chartH, chartH) : 0;
            float top   = padT + chartH - barH;
            float bot   = padT + chartH;

            if (vol > 0) {
                RectF rect = new RectF(left, top, right, bot);
                canvas.drawRoundRect(rect, cornerR, cornerR, p);
                if (barH > cornerR) canvas.drawRect(left, top + cornerR, right, bot, p);
                if (barH > 30) {
                    textPaint.setTextSize(26f); textPaint.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(formatVol(vol), (left + right) / 2f, top - 6, textPaint);
                }
            }
            if (labels != null && absIdx < labels.size()) {
                textPaint.setTextSize(24f); textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor(isToday ? 0xFFFFB937 : textCol);
                canvas.drawText(labels.get(absIdx), (left + right) / 2f, padT + chartH + padB - 8, textPaint);
                textPaint.setColor(textCol);
            }
        }
        canvas.restore();

        // Scroll position track + thumb, and one-time hint
        if (values.size() > VISIBLE_BARS) {
            float trackY = h - 7f, trackH = 5f;
            gridPaint.setStyle(Paint.Style.FILL);
            gridPaint.setColor(0x33888888);
            canvas.drawRoundRect(new RectF(padL, trackY, w - padR, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
            float frac = (float) VISIBLE_BARS / values.size();
            float thumbW = Math.max(chartW * frac, 28f);
            float ms = maxScroll();
            float thumbX = padL + (chartW - thumbW) * (ms <= 0 ? 0 : scrollPx / ms);
            gridPaint.setColor(0xFF22C882);
            canvas.drawRoundRect(new RectF(thumbX, trackY, thumbX + thumbW, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
            gridPaint.setStyle(Paint.Style.STROKE);

            if (!hasScrolled) {
                textPaint.setTextSize(26f); textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setColor((textCol & 0x00FFFFFF) | 0x99000000);
                canvas.drawText("‹  swipe to scroll history  ›", w / 2f, padT + chartH / 2f, textPaint);
                textPaint.setColor(textCol);
            }
        }
    }

    private double niceStep(double raw) {
        if (raw <= 0) return 1;
        double mag  = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        return (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
    }

    private String formatVol(double v) {
        if (v == 0) return "0";
        if (v >= 1000) return String.format("%.1fk", v / 1000);
        return v == Math.floor(v) ? String.valueOf((int)v) : String.format("%.0f", v);
    }
}
