package com.ironpulse.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.ironpulse.data.AppRepository;

/** Alarms don't survive a reboot — re-arm the daily reminder if it's enabled. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        AppRepository repo = AppRepository.get(ctx);
        if (repo.reminderEnabled)
            Reminders.schedule(ctx, repo.reminderHour, repo.reminderMinute);
    }
}
