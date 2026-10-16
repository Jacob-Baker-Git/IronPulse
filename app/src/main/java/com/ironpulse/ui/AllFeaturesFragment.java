package com.ironpulse.ui;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;

/**
 * The ••• page: every feature as a tile. Tap a tile to open it; tap its star to
 * pin it to (or unpin it from) the bottom bar. Settings lives at the bottom.
 */
public class AllFeaturesFragment extends Fragment {
    private AppRepository repo;
    private LinearLayout grid;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        repo = AppRepository.get(requireContext());
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayout col = new LinearLayout(requireContext());
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(24));
        scroll.addView(col);

        TextView title = new TextView(requireContext());
        title.setText("Everything");
        title.setTextAppearance(R.style.TextAppearance_IronPulse_PageTitle);
        col.addView(title);
        TextView hint = new TextView(requireContext());
        hint.setText("Tap to open · ★ keeps it on your bar");
        hint.setTextColor(themeColor(R.attr.colorTextMuted));
        hint.setTextSize(13);
        hint.setPadding(0, dp(4), 0, dp(16));
        col.addView(hint);

        grid = new LinearLayout(requireContext());
        grid.setOrientation(LinearLayout.VERTICAL);
        col.addView(grid);
        buildGrid();

        View settings = tile(null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(64));
        lp.topMargin = dp(10);
        settings.setLayoutParams(lp);
        col.addView(settings);
        return scroll;
    }

    private void buildGrid() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < Features.ALL.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(92));
                rp.bottomMargin = dp(10);
                grid.addView(row, rp);
            }
            View t = tile(Features.ALL.get(i));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
            if (i % 2 == 0) tp.rightMargin = dp(5); else tp.leftMargin = dp(5);
            row.addView(t, tp);
        }
        // Odd count: keep the last tile half-width
        if (Features.ALL.size() % 2 == 1 && row != null)
            row.addView(new View(requireContext()), new LinearLayout.LayoutParams(0, 1, 1f));
    }

    /** A feature tile, or the Settings tile when {@code f} is null. */
    private View tile(@Nullable Features.Feature f) {
        FrameLayout box = new FrameLayout(requireContext());
        box.setBackgroundResource(R.drawable.tile_bg);
        box.setPadding(dp(14), dp(12), dp(10), dp(12));
        String id = f != null ? f.id : Features.SETTINGS_ID;
        box.setOnClickListener(v -> ((MainActivity) requireActivity()).open(id));

        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(f != null ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        body.setGravity(f != null ? Gravity.BOTTOM : Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(requireContext());
        icon.setImageResource(f != null ? f.icon : R.drawable.ic_settings);
        icon.setImageTintList(ColorStateList.valueOf(themeColor(R.attr.colorTextPrimary)));
        body.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView label = new TextView(requireContext());
        label.setText(f != null ? f.label : "Settings");
        label.setTextColor(themeColor(R.attr.colorTextPrimary));
        label.setTextSize(15);
        label.setPadding(f != null ? 0 : dp(12), f != null ? dp(8) : 0, 0, 0);
        body.addView(label);
        box.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (f != null) {
            boolean pinned = repo.navBar.contains(f.id);
            ImageView star = new ImageView(requireContext());
            star.setImageResource(pinned ? R.drawable.ic_star : R.drawable.ic_star_outline);
            star.setImageTintList(ColorStateList.valueOf(pinned
                    ? requireContext().getColor(R.color.accent) : themeColor(R.attr.colorTextMuted)));
            star.setPadding(dp(6), dp(6), dp(6), dp(6));
            star.setContentDescription(pinned ? "Remove " + f.label + " from bar" : "Add " + f.label + " to bar");
            star.setOnClickListener(v -> {
                if (pinned) repo.navBar.remove(f.id); else repo.navBar.add(f.id);
                repo.saveAsync();
                ((MainActivity) requireActivity()).rebuildBar();
                buildGrid();
            });
            box.addView(star, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP | Gravity.END));
        }
        return box;
    }

    private int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
