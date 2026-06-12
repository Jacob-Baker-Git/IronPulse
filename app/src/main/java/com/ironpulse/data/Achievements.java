package com.ironpulse.data;

import com.ironpulse.model.BodyWeightEntry;
import com.ironpulse.model.SetLog;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

/**
 * Achievement rules, all derived from existing data. Unlock dates persist in
 * {@link AppRepository#achievements} (id → ISO date) so a badge, once earned,
 * stays earned even if the underlying streak later breaks.
 */
public final class Achievements {

    public static final class Def {
        public final String id, emoji, title, desc;
        final Predicate<AppRepository> test;
        Def(String id, String emoji, String title, String desc, Predicate<AppRepository> test) {
            this.id = id; this.emoji = emoji; this.title = title; this.desc = desc; this.test = test;
        }
    }

    public static final List<Def> ALL = Arrays.asList(
        new Def("first-workout", "🏁", "First Workout",
                "Complete your first training day",
                r -> !r.completed.isEmpty()),
        new Def("streak-7", "🔥", "Week Warrior",
                "Hold a 7-day streak",
                r -> r.computeStreak() >= 7),
        new Def("streak-30", "⚡", "Iron Month",
                "Hold a 30-day streak",
                r -> r.computeStreak() >= 30),
        new Def("days-50", "📅", "Fifty Sessions",
                "Train on 50 different days",
                r -> r.completed.size() >= 50),
        new Def("sets-100", "💪", "Century of Sets",
                "Log 100 sets",
                r -> r.setLogs.size() >= 100),
        new Def("sets-1000", "🏗", "Thousand Club",
                "Log 1,000 sets",
                r -> r.setLogs.size() >= 1000),
        new Def("volume-10t", "🐘", "10 Tonnes Moved",
                "Lift 10,000 kg of total volume",
                r -> totalVolumeKg(r) >= 10_000),
        new Def("volume-100t", "🚂", "100 Tonnes Moved",
                "Lift 100,000 kg of total volume",
                r -> totalVolumeKg(r) >= 100_000),
        new Def("set-100kg", "🏋", "Triple Digits",
                "Log a set at 100 kg or more",
                r -> r.setLogs.stream().anyMatch(s -> s.getWeightKg() >= 100)),
        new Def("cardio-10", "🏃", "Cardio Regular",
                "Log 10 cardio sessions",
                r -> r.cardio.size() >= 10),
        new Def("goal-weight", "🎯", "Goal Weight",
                "Reach your bodyweight goal",
                Achievements::goalReached)
    );

    private Achievements() {}

    private static double totalVolumeKg(AppRepository r) {
        double sum = 0;
        for (SetLog s : r.setLogs) if (!s.isBodyweight()) sum += s.volume();
        return sum;
    }

    private static boolean goalReached(AppRepository r) {
        if (r.goalWeightKg <= 0 || r.startWeightKg <= 0 || r.bodyEntries.isEmpty()) return false;
        BodyWeightEntry latest = null;
        for (BodyWeightEntry e : r.bodyEntries)
            if (latest == null || e.getDate().isAfter(latest.getDate())) latest = e;
        boolean cutting = r.startWeightKg >= r.goalWeightKg;
        return cutting ? latest.getWeightKg() <= r.goalWeightKg + 0.05
                       : latest.getWeightKg() >= r.goalWeightKg - 0.05;
    }

    /** Evaluates every rule, persists fresh unlocks, and returns them for toasting. */
    public static List<Def> checkAndUnlock(AppRepository repo) {
        List<Def> newly = new ArrayList<>();
        for (Def d : ALL) {
            if (repo.achievements.containsKey(d.id)) continue;
            boolean hit;
            try { hit = d.test.test(repo); } catch (Exception e) { hit = false; }
            if (hit) {
                repo.achievements.put(d.id, LocalDate.now().toString());
                newly.add(d);
            }
        }
        if (!newly.isEmpty()) repo.saveAsync();
        return newly;
    }
}
