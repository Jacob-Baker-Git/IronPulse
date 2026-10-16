package com.ironpulse.ui;

import android.content.res.ColorStateList;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.ironpulse.R;
import com.ironpulse.data.Achievements;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Awards page: progress, then earned achievements, then the ones still to unlock. */
class AwardsTab {
    private final MoreFragment host;

    AwardsTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        // Opening the page is also a natural moment to catch anything earned offline
        Achievements.checkAndUnlock(host.repo);

        List<Achievements.Def> earned = new ArrayList<>(), locked = new ArrayList<>();
        for (Achievements.Def d : Achievements.ALL)
            (host.repo.achievements.containsKey(d.id) ? earned : locked).add(d);

        host.hdr(c, earned.size() + " OF " + Achievements.ALL.size() + " EARNED");
        ProgressBar bar = new ProgressBar(host.requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(Achievements.ALL.size()); bar.setProgress(earned.size());
        bar.setProgressTintList(ColorStateList.valueOf(host.color(R.color.accent)));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(host.themeColor(R.attr.colorBorder)));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, host.dp(8));
        bp.bottomMargin = host.dp(16);
        c.addView(bar, bp);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.ENGLISH);
        for (Achievements.Def d : earned) {
            String when = "";
            try { when = "Earned " + LocalDate.parse(host.repo.achievements.get(d.id)).format(fmt); }
            catch (Exception ignored) {}
            c.addView(row(d.emoji + "  " + d.title, d.desc + (when.isEmpty() ? "" : "\n" + when), true));
        }
        if (!locked.isEmpty()) {
            host.sp(c, 14);
            host.hdr(c, "STILL TO UNLOCK");
            for (Achievements.Def d : locked) c.addView(row(d.title, d.desc, false));
        }
    }

    private LinearLayout row(String titleText, String descText, boolean unlocked) {
        LinearLayout row = host.row();
        LinearLayout txt = new LinearLayout(host.requireContext());
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(host.requireContext());
        title.setText(titleText);
        title.setTextSize(15);
        title.setTextColor(host.themeColor(unlocked ? R.attr.colorTextPrimary : R.attr.colorTextMuted));
        if (unlocked) title.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView desc = new TextView(host.requireContext());
        desc.setText(descText);
        desc.setTextSize(13);
        desc.setTextColor(host.themeColor(R.attr.colorTextMuted));
        desc.setPadding(0, host.dp(2), 0, 0);

        txt.addView(title); txt.addView(desc);
        row.addView(txt);
        return row;
    }
}
