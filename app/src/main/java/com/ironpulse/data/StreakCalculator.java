package com.ironpulse.data;

import java.time.LocalDate;

/**
 * Pure streak rules — no Android or repository dependencies so the intricate
 * walk-back logic is unit-testable on the JVM.
 *
 * Streak rule: ONLY today affects the streak number.
 * - If today has no exercises or is a rest day → 0
 * - If today is not fully complete → 0
 * - If today IS complete → 1, then walk back through consecutive days:
 *   - Rest days: skip silently (don't break the chain)
 *   - Days with nothing planned: skip silently (don't break the chain)
 *   - To stop an exercise added today from inflating the streak across a long
 *     empty gap, at most {@link #MAX_SILENT_SKIPS} consecutive skips are allowed
 *   - Days with planned exercises NOT all complete: break the chain
 */
public final class StreakCalculator {
    public static final int MAX_SILENT_SKIPS = 6;
    private static final int LOOKBACK_DAYS = 730;

    /** The few facts about the plan the calculator needs, day by day. */
    public interface Schedule {
        boolean hasPlanned(LocalDate date);
        boolean isComplete(LocalDate date);
        boolean isRestWeekday(LocalDate date);
    }

    private StreakCalculator() {}

    public static int computeStreak(LocalDate today, Schedule s) {
        if (!s.hasPlanned(today) || s.isRestWeekday(today)) return 0;
        if (!s.isComplete(today)) return 0;
        return 1 + walkBack(today, 1, s);
    }

    /**
     * The streak that WOULD exist if today were completed — walks back from
     * yesterday exactly like computeStreak walks back from today. Used for
     * "X day streak — complete today to extend!".
     */
    public static int computePotentialStreak(LocalDate today, Schedule s) {
        LocalDate yesterday = today.minusDays(1);
        if (s.isRestWeekday(yesterday)) return 0;
        if (!s.hasPlanned(yesterday)) return 0;
        if (!s.isComplete(yesterday)) return 0;
        return 1 + walkBack(today, 2, s);
    }

    /** Counts completed days walking back from today-minus-startOffset. */
    private static int walkBack(LocalDate today, int startOffset, Schedule s) {
        int streak = 0;
        int consecutiveSkips = 0;
        for (int i = startOffset; i <= LOOKBACK_DAYS; i++) {
            LocalDate d = today.minusDays(i);
            if (s.isRestWeekday(d) || !s.hasPlanned(d)) {
                consecutiveSkips++;
                if (consecutiveSkips > MAX_SILENT_SKIPS) break;
                continue;
            }
            consecutiveSkips = 0;
            if (s.isComplete(d)) streak++;
            else break;
        }
        return streak;
    }
}
