package com.ironpulse.ui;

import androidx.fragment.app.Fragment;
import com.ironpulse.R;
import java.util.Arrays;
import java.util.List;

/**
 * Every feature the app has, in one list. The bottom bar, the ••• page and the
 * Settings bar editor all read from here, so adding a feature means one line.
 */
final class Features {
    static final String ALL_ID = "all", SETTINGS_ID = "settings";

    static final class Feature {
        final String id, label;
        final int icon;
        Feature(String id, String label, int icon) { this.id = id; this.label = label; this.icon = icon; }
    }

    static final List<Feature> ALL = Arrays.asList(
            new Feature("today",    "Today",           R.drawable.ic_today),
            new Feature("progress", "Progress",        R.drawable.ic_progress),
            new Feature("food",     "Food",            R.drawable.ic_food),
            new Feature("body",     "Body",            R.drawable.ic_body),
            new Feature("cardio",   "Cardio",          R.drawable.ic_cardio),
            new Feature("prs",      "PRs",             R.drawable.ic_prs),
            new Feature("awards",   "Awards",          R.drawable.ic_awards),
            new Feature("check",    "Programme check", R.drawable.ic_check));

    private Features() {}

    static Feature byId(String id) {
        for (Feature f : ALL) if (f.id.equals(id)) return f;
        return null;
    }

    static Fragment create(String id) {
        switch (id) {
            case "today":    return new WorkoutFragment();
            case "progress": return new HistoryFragment();
            case "food":     return MoreFragment.page(MoreFragment.FOOD);
            case "body":     return new BodyFragment();
            case "cardio":   return new CardioFragment();
            case "prs":      return MoreFragment.page(MoreFragment.PRS);
            case "awards":   return MoreFragment.page(MoreFragment.AWARDS);
            case "check":    return MoreFragment.page(MoreFragment.CHECK);
            case SETTINGS_ID: return MoreFragment.page(MoreFragment.SETTINGS);
            default:         return new AllFeaturesFragment();
        }
    }
}
