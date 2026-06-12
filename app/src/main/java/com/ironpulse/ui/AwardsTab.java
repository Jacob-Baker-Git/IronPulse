package com.ironpulse.ui;

import android.widget.LinearLayout;
import android.widget.TextView;
import com.ironpulse.R;
import com.ironpulse.data.Achievements;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** "Awards" tab of the More screen: earned and still-locked achievements. */
class AwardsTab {
    private final MoreFragment host;

    AwardsTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        // Opening the tab is also a natural moment to catch anything earned offline
        Achievements.checkAndUnlock(host.repo);

        long earned = Achievements.ALL.stream()
                .filter(d -> host.repo.achievements.containsKey(d.id)).count();
        host.hdr(c, "Achievements  ·  " + earned + " of " + Achievements.ALL.size() + " earned");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d MMM yyyy");
        for (Achievements.Def d : Achievements.ALL) {
            String unlockedOn = host.repo.achievements.get(d.id);
            boolean unlocked = unlockedOn != null;

            LinearLayout row = host.row();
            LinearLayout txt = new LinearLayout(host.requireContext());
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView title = new TextView(host.requireContext());
            title.setText((unlocked ? d.emoji : "🔒") + "  " + d.title);
            title.setTextSize(14);
            title.setTextColor(unlocked ? host.themeColor(R.attr.colorTextPrimary)
                                        : host.themeColor(R.attr.colorTextMuted));
            if (unlocked) title.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView desc = new TextView(host.requireContext());
            String when = "";
            if (unlocked) {
                try { when = "  ·  earned " + LocalDate.parse(unlockedOn).format(fmt); }
                catch (Exception ignored) {}
            }
            desc.setText(d.desc + when);
            desc.setTextSize(11);
            desc.setTextColor(host.themeColor(R.attr.colorTextMuted));
            desc.setPadding(0, 2, 0, 0);

            txt.addView(title); txt.addView(desc);
            row.addView(txt);
            row.setAlpha(unlocked ? 1f : 0.55f);
            c.addView(row);
        }
    }
}
