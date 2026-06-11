package com.ironpulse.ui;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Four circular progress rings (Calories / Protein / Carbs / Fat) filling toward
 * their daily goals. Over-goal rings turn red. Pure-draw view, sized by the host.
 */
public class MacroRingsView extends View {
    private static final String[] LABELS = {"Calories", "Protein", "Carbs", "Fats"};
    private static final String[] UNITS  = {"kcal", "g", "g", "g"};
    private final int[] ringColors = {0xFF22C882, 0xFF1E64C8, 0xFFC8A014, 0xFF963CC0};
    private static final int OVER = 0xFFE64444;

    private final double[] consumed = new double[4];
    private final double[] goal = new double[4];
    private int textCol = 0xFF72889E;
    private int strongText = 0xFFF0F4F8;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arcPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint valPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lblPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MacroRingsView(Context c) { super(c); init(); }
    public MacroRingsView(Context c, android.util.AttributeSet a) { super(c, a); init(); }

    private void init() {
        trackPaint.setStyle(Paint.Style.STROKE); trackPaint.setStrokeCap(Paint.Cap.ROUND);
        arcPaint.setStyle(Paint.Style.STROKE);   arcPaint.setStrokeCap(Paint.Cap.ROUND);
        valPaint.setTextAlign(Paint.Align.CENTER);
        lblPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setTextColors(int muted, int strong) { textCol = muted; strongText = strong; invalidate(); }

    public void setData(double[] consumed, double[] goal) {
        for (int i = 0; i < 4; i++) {
            this.consumed[i] = i < consumed.length ? consumed[i] : 0;
            this.goal[i]     = i < goal.length ? goal[i] : 0;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        float cellW = w / 4f;
        float stroke = Math.max(8f, cellW * 0.09f);
        trackPaint.setStrokeWidth(stroke);
        arcPaint.setStrokeWidth(stroke);
        trackPaint.setColor((textCol & 0x00FFFFFF) | 0x33000000);

        float radius = Math.min(cellW, h) * 0.30f;
        float cy = h * 0.40f;

        valPaint.setTextSize(radius * 0.45f);
        lblPaint.setTextSize(Math.min(28f, radius * 0.42f));
        subPaint.setTextSize(Math.min(24f, radius * 0.36f));
        lblPaint.setColor(textCol);
        subPaint.setColor(textCol);

        for (int i = 0; i < 4; i++) {
            float cx = cellW * i + cellW / 2f;
            RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);

            canvas.drawArc(oval, 0, 360, false, trackPaint);

            double g = goal[i];
            double frac = g > 0 ? consumed[i] / g : 0;
            boolean over = frac > 1.0;
            float sweep = (float) Math.min(frac, 1.0) * 360f;
            arcPaint.setColor(over ? OVER : ringColors[i]);
            if (sweep > 0) canvas.drawArc(oval, -90, sweep, false, arcPaint);

            // Center value (consumed)
            valPaint.setColor(over ? OVER : strongText);
            canvas.drawText(fmt(consumed[i]), cx, cy + radius * 0.16f, valPaint);

            // Label under ring
            canvas.drawText(LABELS[i], cx, cy + radius + lblPaint.getTextSize() + 6, lblPaint);
            // goal sub-line
            String sub = g > 0 ? "/ " + fmt(g) + " " + UNITS[i] : "no goal";
            canvas.drawText(sub, cx, cy + radius + lblPaint.getTextSize() + subPaint.getTextSize() + 12, subPaint);
        }
    }

    private String fmt(double v) {
        if (v >= 1000) return String.format("%.1fk", v / 1000);
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format("%.0f", v);
    }
}
