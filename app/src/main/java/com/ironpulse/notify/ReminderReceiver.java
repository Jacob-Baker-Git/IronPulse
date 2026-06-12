package com.ironpulse.notify;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.ExerciseData;
import java.time.LocalDate;
import java.util.List;

/**
 * Daily reminder: only notifies when today actually has unfinished scheduled
 * exercises and isn't a rest day. Always reschedules tomorrow's check.
 */
public class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        AppRepository repo = AppRepository.get(ctx);
        if (!repo.reminderEnabled) return;
        // Chain the next firing first so a crash below can't kill the schedule
        Reminders.schedule(ctx, repo.reminderHour, repo.reminderMinute);

        LocalDate today = LocalDate.now();
        List<ExerciseData> planned = repo.getExercisesForDate(today);
        if (planned.isEmpty() || repo.isRestDay(today) || repo.isDateComplete(today)) return;
        if (!Notifications.canPost(ctx)) return;

        Notifications.ensureChannels(ctx);
        int remaining = planned.size()
                - repo.completed.getOrDefault(today, java.util.Collections.emptyList()).size();
        PendingIntent open = PendingIntent.getActivity(ctx, 0,
                ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, Notifications.CHANNEL_REMINDER)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Workout day 💪")
                .setContentText(remaining + (remaining == 1 ? " exercise" : " exercises")
                        + " left today — keep the streak alive!")
                .setAutoCancel(true)
                .setContentIntent(open);
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Notifications.ID_REMINDER, b.build());
    }
}
