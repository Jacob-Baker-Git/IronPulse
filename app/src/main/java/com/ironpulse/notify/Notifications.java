package com.ironpulse.notify;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Notification channels + permission helper shared by the rest timer and reminders. */
public final class Notifications {
    public static final String CHANNEL_REST     = "rest_timer";
    public static final String CHANNEL_REMINDER = "workout_reminder";
    public static final int ID_REST     = 1;
    public static final int ID_REMINDER = 2;

    private Notifications() {}

    public static void ensureChannels(Context ctx) {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel rest = new NotificationChannel(CHANNEL_REST,
                "Rest timer", NotificationManager.IMPORTANCE_LOW); // silent live countdown
        rest.setShowBadge(false);
        nm.createNotificationChannel(rest);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL_REMINDER,
                "Workout reminders", NotificationManager.IMPORTANCE_DEFAULT));
    }

    /** POST_NOTIFICATIONS is a runtime permission from API 33. */
    public static boolean canPost(Context ctx) {
        return Build.VERSION.SDK_INT < 33
                || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                   == PackageManager.PERMISSION_GRANTED;
    }
}
