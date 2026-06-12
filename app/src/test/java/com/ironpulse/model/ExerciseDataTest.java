package com.ironpulse.model;

import org.junit.Test;
import static org.junit.Assert.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;

public class ExerciseDataTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 8);

    @Test public void parsesSetsAndRepsFromLegacyString() {
        ExerciseData ex = new ExerciseData("Bench", "60", "4x8", 120, MONDAY);
        assertEquals(4, ex.getSets());
        assertEquals(8, ex.getRepsPerSet());
        assertEquals("4x8", ex.getReps());
    }

    @Test public void singleNumberRepsMeansOneSet() {
        ExerciseData ex = new ExerciseData("Plank", "BW", "12", 60, MONDAY);
        assertEquals(1, ex.getSets());
        assertEquals(12, ex.getRepsPerSet());
    }

    @Test public void garbageRepsFallBackToDefaults() {
        ExerciseData ex = new ExerciseData("Squat", "80", "??", 90, MONDAY);
        assertEquals(3, ex.getSets());
        assertEquals(10, ex.getRepsPerSet());
    }

    @Test public void normalizeAssignsMissingIdAndDays() {
        ExerciseData ex = new ExerciseData("Row", "50", "3x10", 90, MONDAY);
        ex.normalize();
        assertNotNull(ex.getId());
        assertFalse(ex.getId().isEmpty());
        assertEquals(EnumSet.of(DayOfWeek.MONDAY), ex.getDays());
    }

    @Test public void multiDayScheduleRoundTrips() {
        ExerciseData ex = new ExerciseData("Squat", "80", "4x6", 180, MONDAY);
        ex.setDays(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY));
        assertEquals(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), ex.getDays());
        assertEquals("Mon, Thu", ex.daysLabel());
    }

    @Test public void setDaysIgnoresEmptySelection() {
        ExerciseData ex = new ExerciseData("Squat", "80", "4x6", 180, MONDAY);
        ex.setDays(EnumSet.noneOf(DayOfWeek.class));
        assertEquals(EnumSet.of(DayOfWeek.MONDAY), ex.getDays());
    }

    @Test public void bodyweightDetection() {
        assertTrue(new ExerciseData("Pull-Up", "BW", "4x6", 150, MONDAY).isBodyweight());
        assertTrue(new ExerciseData("Pull-Up", "", "4x6", 150, MONDAY).isBodyweight());
        assertFalse(new ExerciseData("Bench", "60", "4x6", 150, MONDAY).isBodyweight());
    }

    @Test public void weightParsingHandlesCommaAndUnits() {
        assertEquals(82.5, new ExerciseData("Bench", "82,5", "3x10", 90, MONDAY).getWeightKg(), 0.001);
        assertEquals(60.0, new ExerciseData("Bench", "60 kg", "3x10", 90, MONDAY).getWeightKg(), 0.001);
    }
}
