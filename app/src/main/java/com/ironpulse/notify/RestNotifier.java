package com.ironpulse.notify;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;

/**
 * One place for all rest-timer notifications (live countdown, rest-over alert,
 * exercise-complete) and the background end-of-rest alarm. Both the exercise
 * screen and the quick-log receiver drive rests through here, so the "Log set"
 * action keeps working across cycles without the app open.
 */
public final class RestNotifier {
    // Extras describing the exercise a rest belongs to — enough for QuickLogReceiver
    // to append the next set without any UI.
    public static final String EX_ID   = "exercise_id";
    public static final String EX_NAME = "exercise_name";
    public static final String DATE    = "date";
    public static final String WEIGHT  = "weightKg";
    public static final String REPS    = "reps";
    public static final String REST    = "restSeconds";
    public static final String TARGET  = "targetSets";
    public static final String LOGGED  = "loggedSets";

    private RestNotifier() {}

    public static Bundle extras(String exId, String exName, String date, double weightKg,
                                int reps, int restSeconds, int targetSets, int loggedSets) {
        Bundle b = new Bundle();
        b.putString(EX_ID, exId);
        b.putString(EX_NAME, exName);
        b.putString(DATE, date);
        b.putDouble(WEIGHT, weightKg);
        b.putInt(REPS, reps);
        b.putInt(REST, restSeconds);
        b.putInt(TARGET, targetSets);
        b.putInt(LOGGED, loggedSets);
        return b;
    }

    /** Live countdown in the shade — keeps ticking even if the app is left. */
    public static void postCountdown(Context ctx, Bundle ex) {
        if (!Notifications.canPost(ctx)) return;
        Notifications.ensureChannels(ctx);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifications.CHANNEL_REST)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Resting — " + ex.getString(EX_NAME))
                .setContentText("Next set when the timer hits zero")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(RestTimerState.endsAt())
                .setOnlyAlertOnce(true)
                .setAutoCancel(true)
                .setContentIntent(openIntent(ctx, ex))
                .addAction(0, logActionLabel(ex), quickLogIntent(ctx, ex));
        notify(ctx, b);
    }

    /** Audible "rest over" swap-in, fired by the background alarm. */
    public static void postRestOver(Context ctx, Bundle ex) {
        if (!Notifications.canPost(ctx)) return;
        Notifications.ensureChannels(ctx);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Rest over — next set!")
                .setContentText(ex.getString(EX_NAME, "Back to it 💪"))
                .setAutoCancel(true)
                .setContentIntent(openIntent(ctx, ex))
                .addAction(0, logActionLabel(ex), quickLogIntent(ctx, ex));
        notify(ctx, b);
    }

    /** Final state after the last set was quick-logged from the shade. */
    public static void postAllDone(Context ctx, Bundle ex, String prNote) {
        if (!Notifications.canPost(ctx)) return;
        Notifications.ensureChannels(ctx);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("✓ " + ex.getString(EX_NAME) + " complete")
                .setContentText(prNote != null ? prNote : "All sets logged — nice work!")
                .setAutoCancel(true)
                .setContentIntent(openIntent(ctx, ex));
        notify(ctx, b);
    }

    public static void cancel(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(Notifications.ID_REST);
    }

    /** Backgrounded "rest over" alert — inexact is fine for a gym timer. */
    public static void scheduleAlarm(Context ctx, Bundle ex) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                RestTimerState.endsAt(), alarmIntent(ctx, ex));
    }

    public static void cancelAlarm(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(alarmIntent(ctx, new Bundle()));
    }

    private static String logActionLabel(Bundle ex) {
        int next = ex.getInt(LOGGED) + 1, target = ex.getInt(TARGET);
        return "Log set " + next + "/" + target;
    }

    private static void notify(Context ctx, NotificationCompat.Builder b) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notifications.ID_REST, b.build());
    }

    private static PendingIntent openIntent(Context ctx, Bundle ex) {
        Intent i = new Intent(ctx, com.ironpulse.ui.ExerciseDetailActivity.class)
                .putExtras(ex)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 1, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent quickLogIntent(Context ctx, Bundle ex) {
        Intent i = new Intent(ctx, QuickLogReceiver.class).putExtras(ex);
        return PendingIntent.getBroadcast(ctx, 102, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent alarmIntent(Context ctx, Bundle ex) {
        Intent i = new Intent(ctx, RestAlarmReceiver.class).putExtras(ex);
        return PendingIntent.getBroadcast(ctx, 101, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
