package com.ironpulse.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * Fires (inexactly) when a rest period ends while the app is backgrounded:
 * swaps the silent countdown notification for an audible "rest over" one that
 * carries the quick "Log set" action. The in-app timer handles the foreground
 * case itself.
 */
public class RestAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Bundle ex = intent.getExtras();
        if (ex == null) ex = new Bundle();
        // The end time rides in the intent: RestTimerState is in-memory and reads 0
        // when Android killed the backgrounded app — the case this alarm exists for.
        // Pause/finish cancel the alarm and a restart replaces it, so this is current.
        long endsAt = ex.getLong(RestNotifier.ENDS_AT, RestTimerState.endsAt());
        if (endsAt == 0 || endsAt - System.currentTimeMillis() > 2000) return;
        RestNotifier.postRestOver(ctx, ex);
        RestTimerState.clear();
    }
}
