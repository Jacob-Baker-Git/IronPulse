package com.ironpulse.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import java.util.Calendar;

/** Schedules the daily workout-reminder alarm. Each firing schedules the next. */
public final class Reminders {
    private Reminders() {}

    private static PendingIntent pending(Context ctx) {
        return PendingIntent.getBroadcast(ctx, 100,
                new Intent(ctx, ReminderReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** Schedules the next firing at hour:minute (today if still ahead, else tomorrow). */
    public static void schedule(Context ctx, int hour, int minute) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis())
            c.add(Calendar.DAY_OF_YEAR, 1);
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, c.getTimeInMillis(), pending(ctx));
    }

    public static void cancel(Context ctx) {
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        if (am != null) am.cancel(pending(ctx));
    }
}
