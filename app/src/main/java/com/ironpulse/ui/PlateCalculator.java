package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.*;
import com.ironpulse.data.Units;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "What do I put on the bar?" — shows the per-side plate breakdown for a target
 * weight. Works entirely in the current display unit with that unit's standard
 * plate denominations.
 */
final class PlateCalculator {
    private static final double[] PLATES_KG = {25, 20, 15, 10, 5, 2.5, 1.25};
    private static final double[] PLATES_LB = {45, 35, 25, 10, 5, 2.5};

    private PlateCalculator() {}

    /** @param targetKg the working weight in kg (storage unit). */
    static void show(Context ctx, double targetKg) {
        boolean lbs = Units.useLbs();
        double target = Units.toDisplay(targetKg);
        String unit = Units.unit();
        // Standard bars; "0" covers dumbbell/machine where the whole weight counts
        double[] bars = lbs ? new double[]{45, 35, 15, 0} : new double[]{20, 15, 10, 0};
        String[] barNames = new String[bars.length];
        for (int i = 0; i < bars.length; i++)
            barNames[i] = bars[i] == 0 ? "No bar (total weight)" : fmt(bars[i]) + " " + unit + " bar";

        LinearLayout l = new LinearLayout(ctx);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 24, 48, 8);

        TextView title = new TextView(ctx);
        title.setText("Target: " + fmt(target) + " " + unit);
        title.setTextSize(16);
        l.addView(title);

        Spinner barSpin = new Spinner(ctx);
        ArrayAdapter<String> ba = new ArrayAdapter<>(ctx, android.R.layout.simple_spinner_item, barNames);
        ba.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        barSpin.setAdapter(ba);
        l.addView(barSpin);

        TextView result = new TextView(ctx);
        result.setTextSize(14);
        result.setPadding(0, 16, 0, 0);
        l.addView(result);

        barSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> a, android.view.View v, int pos, long id) {
                result.setText(breakdown(target, bars[pos], lbs ? PLATES_LB : PLATES_KG, unit));
            }
            @Override public void onNothingSelected(AdapterView<?> a) {}
        });

        new AlertDialog.Builder(ctx).setTitle("Plate Calculator").setView(l)
                .setPositiveButton("Close", null).show();
    }

    private static String breakdown(double target, double bar, double[] plates, String unit) {
        if (target <= 0) return "Enter a weight first.";
        if (bar > 0 && target < bar)
            return "Target is lighter than the bar (" + fmt(bar) + " " + unit + ").";
        double perSide = bar > 0 ? (target - bar) / 2 : target;
        String sideLabel = bar > 0 ? "Per side:" : "Plates:";
        if (perSide < plates[plates.length - 1] / 2)
            return bar > 0 ? "Empty bar — no plates needed." : "Below the smallest plate.";

        List<String> rows = new ArrayList<>();
        double remaining = perSide;
        for (double p : plates) {
            int n = (int) Math.floor(remaining / p + 1e-9);
            if (n > 0) {
                rows.add(String.format(Locale.US, "%d × %s %s", n, fmt(p), unit));
                remaining -= n * p;
            }
        }
        StringBuilder sb = new StringBuilder(sideLabel + "\n");
        if (rows.isEmpty()) sb.append("  (none)\n");
        for (String r : rows) sb.append("  ").append(r).append("\n");
        if (remaining > 0.01)
            sb.append(String.format(Locale.US, "\n%.2f %s short — closest below target", remaining * (bar > 0 ? 2 : 1), unit));
        return sb.toString().trim();
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.format(Locale.US, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }
}
