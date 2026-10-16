package com.ironpulse.ui;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_CURRENT = "current_feature";
    /** Bar items get equal widths up to this many; beyond it they keep a fixed width and scroll. */
    private static final int MAX_FITTED = 5;

    private AppRepository repo;
    private String current;
    private final OnBackPressedCallback backToHome = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() { open(homeId()); }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE super.onCreate and setContentView
        repo = AppRepository.get(this);
        setTheme(repo.darkMode ? R.style.Theme_IronPulse : R.style.Theme_IronPulse_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge (enforced when targeting SDK 35+): inset the root so content
        // is never hidden behind the status/navigation bars.
        View root = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        getOnBackPressedDispatcher().addCallback(this, backToHome);
        String start = savedInstanceState == null ? null : savedInstanceState.getString(KEY_CURRENT);
        open(start != null ? start : homeId());

        // Alarms vanish on force-stop/reboot edge cases — re-arm whenever we open
        if (repo.reminderEnabled)
            com.ironpulse.notify.Reminders.schedule(this, repo.reminderHour, repo.reminderMinute);

        // First launch: ask sex once — used by the macro calculator and body
        // calculations everywhere, changeable later in ••• → Settings.
        if (repo.gender == null || repo.gender.isEmpty()) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Welcome to IronPulse")
                .setMessage("Are you male or female?\n\nThis is used for macro and body calculations. You can change it any time in ••• → Settings.")
                .setPositiveButton("Male",   (d, w) -> { repo.gender = "Male";   repo.saveAsync(); })
                .setNegativeButton("Female", (d, w) -> { repo.gender = "Female"; repo.saveAsync(); })
                .setCancelable(false)
                .show();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_CURRENT, current);
    }

    /** Called by Settings when the light mode toggle changes */
    public void applyTheme(boolean darkMode) {
        recreate(); // theme is re-applied in onCreate from repo.darkMode which was already saved
    }

    /** The app opens on the first pinned feature; with nothing pinned, on •••. */
    private String homeId() {
        for (String id : repo.navBar) if (Features.byId(id) != null) return id;
        return Features.ALL_ID;
    }

    /** Opens a feature (or ••• / Settings) and highlights it on the bar. */
    public void open(String id) {
        if (id.equals(current)) return;
        current = id;
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, Features.create(id))
                .commit();
        rebuildBar();
        backToHome.setEnabled(!id.equals(homeId()));
    }

    /** Re-reads the pinned list — called after pins change on ••• or in Settings. */
    public void rebuildBar() {
        LinearLayout items = findViewById(R.id.nav_items);
        items.removeAllViews();
        List<String> ids = new ArrayList<>();
        for (String id : repo.navBar) if (Features.byId(id) != null) ids.add(id);
        ids.add(Features.ALL_ID);
        boolean fitted = ids.size() <= MAX_FITTED;
        // Something opened from ••• (not pinned) keeps ••• lit, so you know where you are
        String lit = ids.contains(current) ? current : Features.ALL_ID;
        for (String id : ids) items.addView(barItem(id, id.equals(lit), fitted));
        backToHome.setEnabled(current != null && !current.equals(homeId()));
    }

    private View barItem(String id, boolean selected, boolean fitted) {
        Features.Feature f = Features.byId(id);
        String label = f != null ? f.label : "More";
        int icon = f != null ? f.icon : R.drawable.ic_more;
        int color = selected ? getColor(R.color.accent) : themeColor(R.attr.colorTextMuted);

        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = fitted
                ? new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(dp(84), LinearLayout.LayoutParams.MATCH_PARENT); // wide enough that the next item peeks in, so it reads as scrollable
        item.setLayoutParams(lp);
        TypedValue ripple = new TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        item.setBackgroundResource(ripple.resourceId);
        item.setContentDescription(label);
        item.setOnClickListener(v -> open(id));

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(color));
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(color);
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(4), 0, dp(4), 0);
        tv.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
        tv.setPadding(0, dp(3), 0, 0);
        item.addView(iv);
        item.addView(tv);
        return item;
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
