package com.ironpulse.notify;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

/**
 * Fires (inexactly) when a rest period ends while the app is backgrounded:
 * swaps the silent countdown notification for an audible "rest over" one.
 * The in-app timer handles the foreground case itself.
 */
public class RestAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        // Stale alarm (user paused/finished or started a different rest) — ignore.
        if (RestTimerState.endsAt() == 0
                || RestTimerState.endsAt() - System.currentTimeMillis() > 2000) return;
        if (!Notifications.canPost(ctx)) return;
        Notifications.ensureChannels(ctx);
        String exercise = intent.getStringExtra("exercise");
        PendingIntent open = PendingIntent.getActivity(ctx, 0,
                ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Rest over — next set!")
                .setContentText(exercise != null ? exercise : "Back to it 💪")
                .setAutoCancel(true)
                .setContentIntent(open);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notifications.ID_REST, b.build());
        RestTimerState.clear();
    }
}
