package com.ironpulse.notify;

/**
 * Wall-clock anchor for the active rest timer. Keyed to an end TIMESTAMP, not a
 * tick count, so leaving the exercise screen (or locking the phone) and coming
 * back resumes from the right place instead of losing the timer.
 */
public final class RestTimerState {
    private static String exerciseName;
    private static long endsAtMillis;

    private RestTimerState() {}

    public static void start(String exercise, int seconds) {
        exerciseName = exercise;
        endsAtMillis = System.currentTimeMillis() + seconds * 1000L;
    }

    public static void clear() { exerciseName = null; endsAtMillis = 0; }

    public static boolean isActiveFor(String exercise) {
        return exercise != null && exercise.equals(exerciseName)
                && endsAtMillis > System.currentTimeMillis();
    }

    public static boolean isAnyActive() { return endsAtMillis > System.currentTimeMillis(); }

    public static long endsAt() { return endsAtMillis; }

    public static int remainingSeconds() {
        return (int) Math.max(0, (endsAtMillis - System.currentTimeMillis()) / 1000);
    }
}
