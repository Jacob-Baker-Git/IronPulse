package com.ironpulse.ui;

import android.util.TypedValue;
import android.widget.Button;
import com.ironpulse.R;

/**
 * One look for the small action buttons. Only the screen's main action is filled
 * (mint, {@link #log}); everything else is a quiet outline so a screen never has
 * a row of competing green blocks. Destructive actions are outlined with red text.
 */
final class ButtonStyles {
    private ButtonStyles() {}

    static void edit(Button b)   { apply(b, R.drawable.btn_quiet, textPrimary(b)); }
    static void delete(Button b) { apply(b, R.drawable.btn_quiet, b.getResources().getColor(R.color.danger, null)); }
    static void log(Button b)    { apply(b, R.drawable.btn_primary, b.getResources().getColor(R.color.on_accent, null)); }

    /** Edit/Done toggle: quiet when idle, filled while active so "Done" is obvious. */
    static void toggle(Button b, boolean active) {
        if (active) log(b); else edit(b);
    }

    private static int textPrimary(Button b) {
        TypedValue tv = new TypedValue();
        b.getContext().getTheme().resolveAttribute(R.attr.colorTextPrimary, tv, true);
        return tv.data;
    }

    private static void apply(Button b, int bgRes, int textColor) {
        float d = b.getResources().getDisplayMetrics().density;
        // XML-inflated buttons are MaterialButtons whose default backgroundTint
        // (colorPrimary) paints over the custom drawable — clear it.
        b.setBackgroundTintList(null);
        b.setBackgroundResource(bgRes);
        b.setStateListAnimator(null); // no Material shadow on flat buttons
        int ph = Math.round(18 * d), pv = Math.round(9 * d);
        b.setPadding(ph, pv, ph, pv);
        b.setMinHeight(0); b.setMinimumHeight(0);
        b.setMinWidth(0);  b.setMinimumWidth(0);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setAllCaps(false);
        b.setLetterSpacing(0f); // Material buttons default to wide tracking
        b.setTextColor(textColor);
    }
}
