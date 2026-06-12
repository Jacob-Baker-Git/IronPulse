package com.ironpulse.data;

import org.junit.Test;
import static org.junit.Assert.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class StreakCalculatorTest {

    /** Wednesday, so a whole work week surrounds it in both directions. */
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 10);

    /** Simple fake: planned/completed day sets + recurring rest weekdays. */
    private static class FakeSchedule implements StreakCalculator.Schedule {
        final Set<LocalDate> planned = new HashSet<>();
        final Set<LocalDate> completed = new HashSet<>();
        final Set<DayOfWeek> restDays = new HashSet<>();

        FakeSchedule plan(LocalDate... days)     { for (LocalDate d : days) planned.add(d); return this; }
        FakeSchedule complete(LocalDate... days) { for (LocalDate d : days) { planned.add(d); completed.add(d); } return this; }
        FakeSchedule rest(DayOfWeek... days)     { for (DayOfWeek d : days) restDays.add(d); return this; }

        @Override public boolean hasPlanned(LocalDate date) { return planned.contains(date); }
        @Override public boolean isComplete(LocalDate date) { return completed.contains(date); }
        @Override public boolean isRestWeekday(LocalDate date) { return restDays.contains(date.getDayOfWeek()); }
    }

    // ── computeStreak ────────────────────────────────────────────────────────

    @Test public void zeroWhenNothingPlannedToday() {
        assertEquals(0, StreakCalculator.computeStreak(TODAY, new FakeSchedule()));
    }

    @Test public void zeroWhenTodayIncomplete() {
        FakeSchedule s = new FakeSchedule().plan(TODAY).complete(TODAY.minusDays(1));
        assertEquals(0, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void zeroWhenTodayIsRestDay() {
        FakeSchedule s = new FakeSchedule().complete(TODAY).rest(TODAY.getDayOfWeek());
        assertEquals(0, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void oneWhenOnlyTodayComplete() {
        FakeSchedule s = new FakeSchedule().complete(TODAY);
        assertEquals(1, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void countsConsecutiveCompletedDays() {
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));
        assertEquals(4, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void incompletePlannedDayBreaksChain() {
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY, TODAY.minusDays(1))
                .plan(TODAY.minusDays(2))                 // planned but missed
                .complete(TODAY.minusDays(3));            // unreachable
        assertEquals(2, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void restDaysAreSkippedSilently() {
        // Mon/Tue rest; trained Wed and the previous Fri/Sat/Sun
        FakeSchedule s = new FakeSchedule()
                .rest(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
                .complete(TODAY, TODAY.minusDays(3), TODAY.minusDays(4), TODAY.minusDays(5));
        assertEquals(4, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void emptyDaysAreSkippedSilently() {
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY, TODAY.minusDays(3)); // nothing planned in between
        assertEquals(2, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void chainStopsAfterMaxConsecutiveSkips() {
        // A completed day beyond a 7-day empty gap must NOT count (6-skip cap)
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY, TODAY.minusDays(8));
        assertEquals(1, StreakCalculator.computeStreak(TODAY, s));
    }

    @Test public void chainSurvivesExactlyMaxSkips() {
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY, TODAY.minusDays(StreakCalculator.MAX_SILENT_SKIPS + 1));
        assertEquals(2, StreakCalculator.computeStreak(TODAY, s));
    }

    // ── computePotentialStreak ───────────────────────────────────────────────

    @Test public void potentialZeroWhenYesterdayIncomplete() {
        FakeSchedule s = new FakeSchedule().plan(TODAY.minusDays(1));
        assertEquals(0, StreakCalculator.computePotentialStreak(TODAY, s));
    }

    @Test public void potentialZeroWhenYesterdayWasRest() {
        FakeSchedule s = new FakeSchedule()
                .complete(TODAY.minusDays(1))
                .rest(TODAY.minusDays(1).getDayOfWeek());
        assertEquals(0, StreakCalculator.computePotentialStreak(TODAY, s));
    }

    @Test public void potentialCountsBackFromYesterday() {
        FakeSchedule s = new FakeSchedule()
                .plan(TODAY)
                .complete(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));
        assertEquals(3, StreakCalculator.computePotentialStreak(TODAY, s));
    }

    @Test public void potentialMatchesStreakOnceTodayCompletes() {
        // Invariant: completing today turns potential N into streak N+1
        FakeSchedule s = new FakeSchedule()
                .plan(TODAY)
                .complete(TODAY.minusDays(1), TODAY.minusDays(2));
        int potential = StreakCalculator.computePotentialStreak(TODAY, s);
        s.complete(TODAY);
        assertEquals(potential + 1, StreakCalculator.computeStreak(TODAY, s));
    }
}
