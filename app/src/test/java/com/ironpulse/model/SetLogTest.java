package com.ironpulse.model;

import static org.junit.Assert.*;

import java.time.LocalDate;
import org.junit.Test;

public class SetLogTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);

    /** Two exercises with the same name must not share each other's sets. */
    @Test public void idKeepsSameNamedExercisesApart() {
        ExerciseData mon = new ExerciseData("Bench Press", "60", "3x10", 90, MONDAY);
        ExerciseData thu = new ExerciseData("Bench Press", "60", "3x10", 90, MONDAY.plusDays(3));
        SetLog s = new SetLog(MONDAY, mon.getId(), "Bench Press", 1, 60, 10, false);
        assertTrue(s.belongsTo(mon));
        assertFalse(s.belongsTo(thu));
    }

    /** Logs saved before ids existed still match by name. */
    @Test public void legacyLogMatchesByName() {
        ExerciseData ex = new ExerciseData("Squat", "100", "5x5", 180, MONDAY);
        SetLog legacy = new SetLog(MONDAY, null, "Squat", 1, 100, 5, false);
        assertTrue(legacy.belongsTo(ex));
        assertFalse(legacy.belongsTo(new ExerciseData("Deadlift", "140", "1x5", 180, MONDAY)));
    }
}
