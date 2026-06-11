package com.ironpulse.ui;

import android.util.TypedValue;
import android.widget.Button;
import com.ironpulse.R;

/**
 * One universal look & size for the small action buttons across the Body, Cardio
 * and More screens (Edit = green, Delete/Clear = red, Log/primary = accent green).
 * The Workout screen is intentionally left with its own styling.
 */
final class ButtonStyles {
    private ButtonStyles() {}

    static void edit(Button b)   { apply(b, R.drawable.btn_accent_dark); }   // green
    static void delete(Button b) { apply(b, R.drawable.btn_danger); }        // red
    static void log(Button b)    { apply(b, R.drawable.btn_primary); }       // accent green

    /** Edit/Done toggle: green when idle, red when active. */
    static void toggle(Button b, boolean active) {
        if (active) delete(b); else edit(b);
    }

    private static void apply(Button b, int bgRes) {
        float d = b.getResources().getDisplayMetrics().density;
        b.setBackgroundResource(bgRes);
        int ph = Math.round(18 * d), pv = Math.round(7 * d);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setMinWidth(0);  b.setMinimumWidth(0);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
    }
}
