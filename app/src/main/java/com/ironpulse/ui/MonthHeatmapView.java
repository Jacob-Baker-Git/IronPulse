package com.ironpulse.ui;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Month-at-a-glance training consistency grid. Tap the ‹ › ends of the header
 * to change months; the fragment recomputes day statuses via the listener.
 */
public class MonthHeatmapView extends View {
    public static final int EMPTY = 0, COMPLETE = 1, MISSED = 2, REST = 3, PLANNED = 4;

    public interface OnMonthChangeListener { void onMonthChange(YearMonth newMonth); }

    private YearMonth month = YearMonth.now();
    private int[] statuses = new int[0];
    private OnMonthChangeListener listener;

    private int bgColor   = 0xFF181E2C;
    private int textCol   = 0xFF72889E;
    private static final int COL_COMPLETE = 0xFF22C882;
    private static final int COL_MISSED   = 0x99E64444;
    private static final int COL_REST     = 0x66128E8A;
    private static final int COL_PLANNED  = 0x44888888;
    private static final int COL_EMPTY    = 0x16888888;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MonthHeatmapView(Context ctx) { this(ctx, null); }
    public MonthHeatmapView(Context ctx, AttributeSet a) {
        super(ctx, a);
        textPaint.setTextAlign(Paint.Align.CENTER);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(3f);
    }

    public void setBgColor(int c)   { bgColor = c; invalidate(); }
    public void setTextColor(int c) { textCol = c; invalidate(); }
    public void setOnMonthChangeListener(OnMonthChangeListener l) { listener = l; }

    public void setMonth(YearMonth m, int[] dayStatuses) {
        month = m;
        statuses = dayStatuses;
        invalidate();
    }

    private float headerH() { return getHeight() * 0.18f; }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP && listener != null
                && e.getY() < headerH()) {
            if (e.getX() < getWidth() / 4f)      listener.onMonthChange(month.minusMonths(1));
            else if (e.getX() > getWidth() * 3 / 4f) listener.onMonthChange(month.plusMonths(1));
            return true;
        }
        return e.getActionMasked() == MotionEvent.ACTION_DOWN || super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(bgColor);
        int w = getWidth(), h = getHeight();
        float headerH = headerH();
        float weekRowH = h * 0.10f;
        float gridTop = headerH + weekRowH;
        float cellW = w / 7f;
        float cellH = (h - gridTop - 8) / 6f;

        // Header: ‹ Month Year ›
        textPaint.setColor(textCol);
        textPaint.setTextSize(headerH * 0.45f);
        textPaint.setFakeBoldText(true);
        String title = month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toUpperCase()
                + " " + month.getYear();
        canvas.drawText(title, w / 2f, headerH * 0.62f, textPaint);
        canvas.drawText("‹", w * 0.08f, headerH * 0.62f, textPaint);
        canvas.drawText("›", w * 0.92f, headerH * 0.62f, textPaint);
        textPaint.setFakeBoldText(false);

        // Weekday letters, Monday-first
        textPaint.setTextSize(weekRowH * 0.6f);
        String[] letters = {"M","T","W","T","F","S","S"};
        for (int i = 0; i < 7; i++)
            canvas.drawText(letters[i], cellW * i + cellW / 2f, gridTop - weekRowH * 0.25f, textPaint);

        // Day cells
        int firstCol = month.atDay(1).getDayOfWeek().getValue() - 1; // Monday = 0
        LocalDate today = LocalDate.now();
        float inset = Math.min(cellW, cellH) * 0.10f;
        float corner = Math.min(cellW, cellH) * 0.18f;
        textPaint.setTextSize(cellH * 0.42f);
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            int idx = firstCol + day - 1;
            int row = idx / 7, col = idx % 7;
            float l = col * cellW + inset, t = gridTop + row * cellH + inset;
            float r = (col + 1) * cellW - inset, b = gridTop + (row + 1) * cellH - inset;

            int st = day - 1 < statuses.length ? statuses[day - 1] : EMPTY;
            switch (st) {
                case COMPLETE: cellPaint.setColor(COL_COMPLETE); break;
                case MISSED:   cellPaint.setColor(COL_MISSED);   break;
                case REST:     cellPaint.setColor(COL_REST);     break;
                case PLANNED:  cellPaint.setColor(COL_PLANNED);  break;
                default:       cellPaint.setColor(COL_EMPTY);    break;
            }
            RectF rect = new RectF(l, t, r, b);
            canvas.drawRoundRect(rect, corner, corner, cellPaint);

            boolean isToday = month.atDay(day).isEqual(today);
            if (isToday) {
                ringPaint.setColor(0xFFFFB937);
                canvas.drawRoundRect(rect, corner, corner, ringPaint);
            }
            textPaint.setColor(st == COMPLETE ? 0xFF06281A : textCol);
            canvas.drawText(String.valueOf(day), (l + r) / 2f,
                    (t + b) / 2f + textPaint.getTextSize() * 0.35f, textPaint);
        }
    }
}
