package com.ironpulse.data;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The muscle-group guess drives Today's colour strip and its "Leg day" title. */
public class ClassifyExerciseTest {
    private static void is(String group, String... names) {
        for (String n : names) assertEquals(n, group, AppRepository.classifyExercise(n));
    }

    @Test public void legsBeatPushAndPull() {
        is("Legs", "Leg Press", "Back Squat", "Leg Curl", "Leg Extension", "Calf Raise", "Hip Thrust");
    }

    @Test public void coreBeatsLegs() {
        is("Core", "Hanging Leg Raise", "Plank", "Russian Twist");
    }

    @Test public void armsBeatPush() {
        is("Arms", "Tricep Pushdown", "Bicep Curl", "Skullcrusher", "Hammer Curl");
    }

    @Test public void pushAndPull() {
        is("Push", "Bench Press", "Overhead Press", "Dip", "Lateral Raise", "Cable Fly");
        is("Pull", "Barbell Row", "Lat Pulldown", "Pull-Up", "Chin-Up", "Deadlift", "Romanian Deadlift", "Shrug");
    }
}
