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
        // Stale alarm (user paused/finished or started a different rest) — ignore.
        if (RestTimerState.endsAt() == 0
                || RestTimerState.endsAt() - System.currentTimeMillis() > 2000) return;
        Bundle ex = intent.getExtras();
        if (ex == null) ex = new Bundle();
        RestNotifier.postRestOver(ctx, ex);
        RestTimerState.clear();
    }
}
