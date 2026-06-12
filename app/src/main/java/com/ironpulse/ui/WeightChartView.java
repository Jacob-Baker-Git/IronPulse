package com.ironpulse.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.ironpulse.model.BodyWeightEntry;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Weight trend line chart showing {@link #VISIBLE_POINTS} points at a time. It is a
 * passive display driven by the entry log below it: the log reports which entries are
 * visible, the green bar underlines exactly those points, and the 7-point window only
 * shifts (smoothly) once the visible range reaches its edge.
 */
public class WeightChartView extends View {
    private List<BodyWeightEntry> data = new ArrayList<>();
    private int bgColor  = 0xFF181E2C;
    private int textCol  = 0xFF72889E;

    public static final int VISIBLE_POINTS = 7;
    private static final float PAD_L = 88f, PAD_R = 20f;

    private float graphStartF = 0f;   // leftmost graph index (continuous)
    private float visFirst = 0f;      // oldest entry visible in the log (chronological idx)
    private float visLast = 0f;       // newest entry visible in the log

    private float lastTouchX;
    /** Dragging the graph scrolls the log below it (in units of points/entries). */
    public interface OnGraphDragListener { void onDragPoints(float points); }
    private OnGraphDragListener dragListener;
    public void setOnGraphDragListener(OnGraphDragListener l) { dragListener = l; }

    private final Paint linePaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotRing    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trendPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WeightChartView(Context ctx) { this(ctx, null); }
    public WeightChartView(Context ctx, AttributeSet a) { this(ctx, a, 0); }
    public WeightChartView(Context ctx, AttributeSet a, int def) { super(ctx, a, def); init(); }

    private void init() {
        linePaint.setColor(0xFF22C882); linePaint.setStrokeWidth(5f);
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFF22C882); dotPaint.setStyle(Paint.Style.FILL);
        dotRing.setColor(0xFF181E2C); dotRing.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER); labelPaint.setTextSize(36f);
        axisPaint.setTextAlign(Paint.Align.RIGHT); axisPaint.setTextSize(32f);
        gridPaint.setStrokeWidth(1.5f);
        trendPaint.setTextSize(36f); trendPaint.setTextAlign(Paint.Align.LEFT);
        trendPaint.setTypeface(Typeface.DEFAULT_BOLD);
        setClickable(true);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (data == null || data.size() < 2) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = e.getX();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = e.getX() - lastTouchX;
                lastTouchX = e.getX();
                float sp = (getWidth() - PAD_L - PAD_R) / (VISIBLE_POINTS - 1);
                // Drag right → reveal older data → scroll the log to older entries.
                if (sp > 0 && dragListener != null) dragListener.onDragPoints(dx / sp);
                return true;
        }
        return false;
    }

    public void setBgColor(int c)  { bgColor = c; }
    public void setTextColor(int c){ textCol = c; invalidate(); }

    private int maxStart() { return Math.max(0, data.size() - VISIBLE_POINTS); }

    public void setData(List<BodyWeightEntry> d) {
        data = d != null ? new ArrayList<>(d) : new ArrayList<>();
        int n = data.size();
        graphStartF = maxStart();                 // start showing the 7 most recent
        visLast  = Math.max(0, n - 1);
        visFirst = Math.max(0, n - 5);            // most recent ~5 until the log reports
        invalidate();
    }

    /**
     * Reported by the log: the chronological index range currently visible below.
     * The green bar underlines this range; the 7-point window only moves to keep the
     * range inside it (so the bar slides within a stationary window most of the time).
     */
    public void setVisibleRange(float vf, float vl) {
        int n = data.size();
        if (n == 0) return;
        visFirst = Math.max(0, Math.min(vf, n - 1));
        visLast  = Math.max(0, Math.min(vl, n - 1));
        float span = VISIBLE_POINTS - 1;
        if (visFirst < graphStartF) graphStartF = visFirst;
        if (visLast > graphStartF + span) graphStartF = visLast - span;
        graphStartF = Math.max(0, Math.min(graphStartF, maxStart()));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawColor(bgColor);

        labelPaint.setColor(textCol);
        axisPaint.setColor(textCol);
        trendPaint.setColor(textCol);
        gridPaint.setColor((textCol & 0x00FFFFFF) | 0x22000000);

        if (data == null || data.size() < 2) {
            labelPaint.setTextSize(34f); labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Add entries to see trend", w / 2f, h / 2f, labelPaint);
            return;
        }

        float padL = PAD_L, padB = 44f, padR = PAD_R, padT = 36f;
        float chartW = w - padL - padR;
        float chartH = h - padT - padB;
        float sp = chartW / (VISIBLE_POINTS - 1);
        int n = data.size();

        double minW = data.stream().mapToDouble(BodyWeightEntry::getWeightKg).min().orElse(0);
        double maxW = data.stream().mapToDouble(BodyWeightEntry::getWeightKg).max().orElse(1);
        double spread = maxW - minW;
        double pad = Math.max(1.5, spread * 0.2);
        minW -= pad; maxW += pad;
        double range = maxW - minW;

        double tickStep = niceStep(range / 4.0);
        double firstTick = Math.ceil(minW / tickStep) * tickStep;
        List<Double> ticks = new ArrayList<>();
        for (double t = firstTick; t <= maxW + 0.001; t += tickStep) ticks.add(t);
        for (double tick : ticks) {
            float y = yPos(tick, minW, range, padT, chartH);
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
            axisPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(String.format("%.1f", tick), padL - 8, y + 9, axisPaint);
        }

        // Point positions: index i at padL + (i - graphStartF) * sp
        float[] xs = new float[n];
        float[] ys = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padL + (i - graphStartF) * sp;
            ys[i] = yPos(data.get(i).getWeightKg(), minW, range, padT, chartH);
        }

        // Line + gradient fill (clipped to the plot area so it runs to the edges cleanly)
        canvas.save();
        canvas.clipRect(padL - 1, 0, w - padR + 1, h);
        Path fillPath = new Path();
        fillPath.moveTo(xs[0], padT + chartH);
        fillPath.lineTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) fillPath.lineTo(xs[i], ys[i]);
        fillPath.lineTo(xs[n - 1], padT + chartH);
        fillPath.close();
        fillPaint.setShader(new LinearGradient(0, padT, 0, padT + chartH,
                0x4422C882, 0x0022C882, Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        canvas.drawPath(path, linePaint);
        canvas.restore();

        // Dots + labels at each point's true x — uniform spacing, no edge preview.
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM");
        for (int i = 0; i < n; i++) {
            if (xs[i] < padL - 0.5f || xs[i] > w - padR + 0.5f) continue;
            dotRing.setColor(bgColor);
            canvas.drawCircle(xs[i], ys[i], 9f, dotRing);
            canvas.drawCircle(xs[i], ys[i], 6f, dotPaint);
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(32f);
            canvas.drawText(String.format("%.1f", data.get(i).getWeightKg()), xs[i], ys[i] - 14, labelPaint);
            labelPaint.setTextSize(28f);
            canvas.drawText(data.get(i).getDate().format(fmt), xs[i], padT + chartH + 32, labelPaint);
        }

        // Trend across the range currently visible in the log
        int fv = Math.max(0, Math.round(visFirst)), lv = Math.min(n - 1, Math.round(visLast));
        double diff = data.get(lv).getWeightKg() - data.get(fv).getWeightKg();
        String trend = diff > 0.05 ? String.format("▲ +%.1f kg", diff)
                     : diff < -0.05 ? String.format("▼ %.1f kg", diff) : "→ stable";
        trendPaint.setColor(diff > 0.05 ? 0xFFE64444 : diff < -0.05 ? 0xFF22C882 : 0xFFFFB937);
        trendPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(trend, w - padR, padT - 8, trendPaint);

        // Green bar: underlines exactly the points currently shown in the log below.
        float trackY = h - 6f, trackH = 5f;
        gridPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0x33888888);
        canvas.drawRoundRect(new RectF(padL, trackY, w - padR, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
        float overhang = 20f; // let the bar reach slightly past its end points
        float bx1 = padL + (visFirst - graphStartF) * sp - overhang;
        float bx2 = padL + (visLast - graphStartF) * sp + overhang;
        bx1 = Math.max(padL, Math.min(bx1, w - padR));
        bx2 = Math.max(padL, Math.min(bx2, w - padR));
        if (bx2 - bx1 < 24f) bx2 = Math.min(w - padR, bx1 + 24f);
        gridPaint.setColor(0xFF22C882);
        canvas.drawRoundRect(new RectF(bx1, trackY, bx2, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
        gridPaint.setStyle(Paint.Style.STROKE);
    }

    private float yPos(double val, double minW, double range, float padT, float chartH) {
        return (float)(padT + chartH * (1.0 - (val - minW) / range));
    }

    private double niceStep(double raw) {
        if (raw <= 0) return 0.5;
        double mag  = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        return (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
    }
}
