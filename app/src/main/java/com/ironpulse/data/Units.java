package com.ironpulse.data;

import java.util.Locale;

/**
 * Weight unit display/input conversion. Kilograms are ALWAYS what gets stored;
 * this class only converts at the UI boundary. The flag mirrors
 * {@link AppRepository#useLbs} and is set on load and from the Settings toggle.
 */
public final class Units {
    public static final double LB_PER_KG = 2.2046226218;
    private static boolean useLbs = false;

    private Units() {}

    public static void setUseLbs(boolean v) { useLbs = v; }
    public static boolean useLbs() { return useLbs; }

    /** Unit suffix for the current setting: "kg" or "lbs". */
    public static String unit() { return useLbs ? "lbs" : "kg"; }

    /** kg → display unit value. */
    public static double toDisplay(double kg) { return useLbs ? kg * LB_PER_KG : kg; }

    /** Display unit value → kg. */
    public static double toKg(double display) { return useLbs ? display / LB_PER_KG : display; }

    /** Number-only display string for a kg value, e.g. "82.5" or "185". */
    public static String num(double kg) {
        double v = Math.round(toDisplay(kg) * 10) / 10.0;
        return v == Math.floor(v) ? String.valueOf((long) v)
                : String.format(Locale.US, "%.1f", v);
    }

    /** Full display string for a kg value, e.g. "82.5 kg" or "182 lbs". */
    public static String fmt(double kg) { return num(kg) + " " + unit(); }

    /**
     * Parses user input in the CURRENT display unit and returns kg.
     * Accepts comma decimals and stray unit suffixes; an explicit "kg"/"lb"
     * suffix overrides the current setting. Returns 0 when unparseable.
     */
    public static double parseToKg(String raw) {
        if (raw == null) return 0;
        String t = raw.trim().toLowerCase(Locale.US);
        boolean saysKg = t.endsWith("kg") || t.endsWith("kgs");
        boolean saysLb = t.endsWith("lb") || t.endsWith("lbs");
        t = t.replaceAll("(?i)\\s*(kgs?|lbs?)\\s*$", "").trim().replace(',', '.')
                .replaceAll("[^0-9.]", "");
        if (t.isEmpty()) return 0;
        double v;
        try { v = Double.parseDouble(t); } catch (Exception e) { return 0; }
        if (saysKg) return v;
        if (saysLb) return v / LB_PER_KG;
        return toKg(v);
    }

    /**
     * Converts a weight input string to the kg-string form the models store.
     * Blank / "bw" / "bodyweight" pass through unchanged so bodyweight
     * detection in {@code ExerciseData.parseWeight} still works.
     */
    public static String inputToKgString(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("bw") || t.equalsIgnoreCase("bodyweight") || t.equals("0"))
            return t;
        double kg = parseToKg(t);
        if (kg <= 0) return t; // unparseable — let the model's own fallback decide
        double r = Math.round(kg * 100) / 100.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
