package com.ironpulse.ui;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;

public class MainActivity extends AppCompatActivity {

    private static final String KEY_NAV = "selected_nav";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme BEFORE super.onCreate and setContentView
        AppRepository repo = AppRepository.get(this);
        setTheme(repo.darkMode ? R.style.Theme_IronPulse : R.style.Theme_IronPulse_Light);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Edge-to-edge (enforced when targeting SDK 35+): inset the root so content
        // is never hidden behind the status/navigation bars. On older versions the
        // decor consumes these insets, so the padding stays 0 — safe everywhere.
        View root = findViewById(R.id.main_root);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if      (id == R.id.nav_workout) load(new WorkoutFragment());
            else if (id == R.id.nav_history) load(new HistoryFragment());
            else if (id == R.id.nav_body)    load(new BodyFragment());
            else if (id == R.id.nav_cardio)  load(new CardioFragment());
            else if (id == R.id.nav_more)    load(new MoreFragment());
            return true;
        });

        if (savedInstanceState == null) {
            load(new WorkoutFragment());
        } else {
            int selectedId = savedInstanceState.getInt(KEY_NAV, R.id.nav_workout);
            nav.setSelectedItemId(selectedId);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        if (nav != null) outState.putInt(KEY_NAV, nav.getSelectedItemId());
    }

    /** Called by Settings tab when light mode toggle changes */
    public void applyTheme(boolean darkMode) {
        recreate(); // theme is re-applied in onCreate from repo.darkMode which was already saved
    }

    private void load(Fragment f) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, f)
                .commit();
    }
}
