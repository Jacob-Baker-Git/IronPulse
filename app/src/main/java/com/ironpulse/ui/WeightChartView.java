package com.ironpulse.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import com.ironpulse.data.Units;
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
    private int bgColor  = 0xFF151A22;
    private int textCol  = 0xFF6B7686;

    public static final int VISIBLE_POINTS = 7;
    /** Everything is sized in dp so the chart looks the same on every screen density. */
    private final float d = getResources().getDisplayMetrics().density;
    private final float padL = 40 * d, padR = 12 * d, padT = 30 * d, padB = 26 * d;
    /** Keeps the first/last point (and its labels) away from the plot edges. */
    private final float inset = 18 * d;
    private double goalKg = 0;

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
    // Reused every frame: the chart redraws continuously while it is dragged
    private final RectF rect = new RectF();
    private final Path fillPath = new Path(), linePath = new Path();
    private LinearGradient fillShader;
    private float shaderTop = -1, shaderBot = -1;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("d MMM");

    private float[] xs = new float[0], ys = new float[0];
    private RectF r(float l, float t, float rr, float b) { rect.set(l, t, rr, b); return rect; }

    public WeightChartView(Context ctx) { this(ctx, null); }
    public WeightChartView(Context ctx, AttributeSet a) { this(ctx, a, 0); }
    public WeightChartView(Context ctx, AttributeSet a, int def) { super(ctx, a, def); init(); }

    private final Paint goalPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void init() {
        linePaint.setColor(0xFF3EE08F); linePaint.setStrokeWidth(2.5f * d);
        linePaint.setStyle(Paint.Style.STROKE); linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        fillPaint.setStyle(Paint.Style.FILL);
        dotPaint.setColor(0xFF3EE08F); dotPaint.setStyle(Paint.Style.FILL);
        dotRing.setColor(0xFF151A22); dotRing.setStyle(Paint.Style.FILL);
        labelPaint.setTextAlign(Paint.Align.CENTER); labelPaint.setTextSize(12 * d);
        axisPaint.setTextAlign(Paint.Align.RIGHT); axisPaint.setTextSize(11 * d);
        gridPaint.setStrokeWidth(1f);
        trendPaint.setTextSize(13 * d); trendPaint.setTextAlign(Paint.Align.RIGHT);
        trendPaint.setTypeface(Typeface.DEFAULT_BOLD);
        goalPaint.setColor(0xFFFFB937); goalPaint.setStyle(Paint.Style.STROKE);
        goalPaint.setStrokeWidth(1.5f * d);
        goalPaint.setPathEffect(new DashPathEffect(new float[]{6 * d, 5 * d}, 0));
        setClickable(true);
    }

    /** Draws a dashed goal line (and keeps it in range); 0 = no goal. */
    public void setGoal(double kg) { goalKg = kg; goalLabel = "goal " + Units.num(kg); invalidate(); }
    private String goalLabel = "";

    /** Spacing between points: the plot width minus an inset at each end. */
    private float pointSpacing() {
        return (getWidth() - padL - padR - 2 * inset) / (VISIBLE_POINTS - 1);
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
                float sp = pointSpacing();
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
            labelPaint.setTextSize(14 * d); labelPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(data != null && data.size() == 1
                    ? "Log one more weigh-in to see your trend" : "Tap + to log your first weigh-in",
                    w / 2f, h / 2f, labelPaint);
            return;
        }

        float chartH = h - padT - padB;
        float sp = pointSpacing();
        float x0 = padL + inset;
        int n = data.size();

        // All chart math happens in the display unit so axis ticks stay round
        double minW = data.stream().mapToDouble(e -> Units.toDisplay(e.getWeightKg())).min().orElse(0);
        double maxW = data.stream().mapToDouble(e -> Units.toDisplay(e.getWeightKg())).max().orElse(1);
        double goal = goalKg > 0 ? Units.toDisplay(goalKg) : 0;
        if (goal > 0) { minW = Math.min(minW, goal); maxW = Math.max(maxW, goal); }
        double spread = maxW - minW;
        double pad = Math.max(1.5, spread * 0.2);
        minW -= pad; maxW += pad;
        double range = maxW - minW;

        double tickStep = niceStep(range / 4.0);
        double firstTick = Math.ceil(minW / tickStep) * tickStep;
        for (double tick = firstTick; tick <= maxW + 0.001; tick += tickStep) {
            float y = yPos(tick, minW, range, padT, chartH);
            canvas.drawLine(padL, y, w - padR, y, gridPaint);
            axisPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(tickLabel(tick, tickStep), padL - 6 * d, y + 4 * d, axisPaint);
        }

        // Goal: a dashed amber line; its label sits in the header (see below) so it
        // can never collide with a point's value label
        if (goal > 0) {
            float gy = yPos(goal, minW, range, padT, chartH);
            canvas.drawLine(padL, gy, w - padR, gy, goalPaint);
        }

        // Point positions: index i at x0 + (i - graphStartF) * sp
        if (xs.length < n) { xs = new float[n]; ys = new float[n]; } // grow only when needed
        for (int i = 0; i < n; i++) {
            xs[i] = x0 + (i - graphStartF) * sp;
            ys[i] = yPos(Units.toDisplay(data.get(i).getWeightKg()), minW, range, padT, chartH);
        }

        // Line + gradient fill (clipped to the plot area so it runs to the edges cleanly)
        canvas.save();
        canvas.clipRect(padL - 1, 0, w - padR + 1, h);
        fillPath.reset();
        fillPath.moveTo(xs[0], padT + chartH);
        fillPath.lineTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) fillPath.lineTo(xs[i], ys[i]);
        fillPath.lineTo(xs[n - 1], padT + chartH);
        fillPath.close();
        if (fillShader == null || shaderTop != padT || shaderBot != padT + chartH) {
            shaderTop = padT; shaderBot = padT + chartH;
            fillShader = new LinearGradient(0, shaderTop, 0, shaderBot,
                    0x443EE08F, 0x003EE08F, Shader.TileMode.CLAMP);
        }
        fillPaint.setShader(fillShader);
        canvas.drawPath(fillPath, fillPaint);
        Path path = linePath;
        path.reset();
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        canvas.drawPath(path, linePaint);
        canvas.restore();

        // Dots + labels. A label goes below its point when the point is a dip, so it
        // never sits on the line; dates run along the bottom.
        DateTimeFormatter fmt = DATE_FMT;
        for (int i = 0; i < n; i++) {
            if (xs[i] < padL - 0.5f || xs[i] > w - padR + 0.5f) continue;
            dotRing.setColor(bgColor);
            canvas.drawCircle(xs[i], ys[i], 4.5f * d, dotRing);
            canvas.drawCircle(xs[i], ys[i], 3f * d, dotPaint);
            boolean dip = (i > 0 && ys[i] > ys[i - 1]) && (i == n - 1 || ys[i] >= ys[i + 1]);
            boolean roomBelow = ys[i] + 18 * d < padT + chartH;
            labelPaint.setTextAlign(Paint.Align.CENTER);
            labelPaint.setTextSize(12 * d);
            canvas.drawText(Units.num(data.get(i).getWeightKg()), xs[i],
                    dip && roomBelow ? ys[i] + 17 * d : ys[i] - 8 * d, labelPaint);
            labelPaint.setTextSize(11 * d);
            canvas.drawText(data.get(i).getDate().format(fmt), xs[i], padT + chartH + 17 * d, labelPaint);
        }

        // Trend across the range currently visible in the log
        int fv = Math.max(0, Math.round(visFirst)), lv = Math.min(n - 1, Math.round(visLast));
        double diff = Units.toDisplay(data.get(lv).getWeightKg() - data.get(fv).getWeightKg());
        String trend = diff > 0.05 ? String.format(java.util.Locale.US, "▲ +%.1f %s", diff, Units.unit())
                     : diff < -0.05 ? String.format(java.util.Locale.US, "▼ %.1f %s", diff, Units.unit()) : "→ stable";
        trendPaint.setColor(diff > 0.05 ? 0xFFE5484D : diff < -0.05 ? 0xFF3EE08F : 0xFFFFB937);
        trendPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(trend, w - padR, 20 * d, trendPaint);
        trendPaint.setColor(textCol);
        trendPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(Units.unit(), padL - 30 * d, 20 * d, trendPaint);
        if (goal > 0) {
            // "- - goal 82" in amber, after the unit, matching the dashed line
            float gx = padL - 30 * d + trendPaint.measureText(Units.unit()) + 14 * d;
            canvas.drawLine(gx, 15 * d, gx + 14 * d, 15 * d, goalPaint);
            axisPaint.setColor(0xFFFFB937);
            axisPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(goalLabel, gx + 20 * d, 19 * d, axisPaint);
            axisPaint.setColor(textCol);
        }

        // Green bar: underlines exactly the points currently shown in the log below.
        float trackH = 3f * d, trackY = h - trackH - 2 * d;
        gridPaint.setStyle(Paint.Style.FILL);
        gridPaint.setColor(0x33888888);
        canvas.drawRoundRect(r(padL, trackY, w - padR, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
        float overhang = 10 * d; // let the bar reach slightly past its end points
        float bx1 = x0 + (visFirst - graphStartF) * sp - overhang;
        float bx2 = x0 + (visLast - graphStartF) * sp + overhang;
        bx1 = Math.max(padL, Math.min(bx1, w - padR));
        bx2 = Math.max(padL, Math.min(bx2, w - padR));
        if (bx2 - bx1 < 12 * d) bx2 = Math.min(w - padR, bx1 + 12 * d);
        gridPaint.setColor(0xFF3EE08F);
        canvas.drawRoundRect(r(bx1, trackY, bx2, trackY + trackH), trackH / 2, trackH / 2, gridPaint);
        gridPaint.setStyle(Paint.Style.STROKE);
    }

    private float yPos(double val, double minW, double range, float padT, float chartH) {
        return (float)(padT + chartH * (1.0 - (val - minW) / range));
    }

    /** "80" for whole-number steps, "82.5" only when the step needs the decimal. */
    private static String tickLabel(double tick, double step) {
        return step >= 1 ? String.valueOf(Math.round(tick)) : String.format(java.util.Locale.US, "%.1f", tick);
    }

    private double niceStep(double raw) {
        if (raw <= 0) return 0.5;
        double mag  = Math.pow(10, Math.floor(Math.log10(raw)));
        double norm = raw / mag;
        return (norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 5 ? 5 : 10) * mag;
    }
}
