package com.ironpulse.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import com.ironpulse.data.AppRepository;
import com.ironpulse.data.Units;
import com.ironpulse.model.ExerciseData;
import com.ironpulse.model.RecordData;
import com.ironpulse.model.SetLog;
import java.time.LocalDate;

/**
 * "Log set" notification action: appends the next set with the same
 * weight/reps, then either restarts the rest cycle or finishes the exercise —
 * all without opening the app.
 */
public class QuickLogReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        Bundle b = intent.getExtras();
        if (b == null) return;
        AppRepository repo = AppRepository.get(ctx);

        String exId = b.getString(RestNotifier.EX_ID);
        String name = b.getString(RestNotifier.EX_NAME);
        LocalDate date;
        try { date = LocalDate.parse(b.getString(RestNotifier.DATE)); }
        catch (Exception e) { date = null; }
        // Only today is loggable — a notification surviving past midnight is stale
        if (name == null || date == null || !date.equals(LocalDate.now())) {
            RestNotifier.cancel(ctx);
            return;
        }
        ExerciseData ex = null;
        for (ExerciseData x : repo.exercises)
            if (x.getId().equals(exId)) { ex = x; break; }
        if (ex == null) { RestNotifier.cancel(ctx); return; }

        final String fName = name;
        final LocalDate fDate = date;
        int logged = (int) repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(fName) && s.getDate().equals(fDate))
                .count();
        int target = b.getInt(RestNotifier.TARGET, ex.getSets());
        if (logged >= target) { RestNotifier.cancel(ctx); return; }

        double wKg = b.getDouble(RestNotifier.WEIGHT, ex.getWeightKg());
        int reps   = b.getInt(RestNotifier.REPS, ex.getRepsPerSet());
        logged++;
        repo.setLogs.add(new SetLog(date, name, logged, wKg, reps, wKg <= 0));
        RecordData pr = repo.checkForNewPR(name, wKg);
        b.putInt(RestNotifier.LOGGED, logged);

        if (logged >= target) {
            repo.markComplete(date, ex, true); // saves internally
            RestTimerState.clear();
            RestNotifier.cancelAlarm(ctx);
            RestNotifier.postAllDone(ctx, b,
                    pr != null ? "🎉 New PR: " + Units.fmt(wKg) + "!" : null);
        } else {
            repo.saveAsync();
            int rest = b.getInt(RestNotifier.REST, ex.getRestSeconds());
            RestTimerState.start(name, rest);
            RestNotifier.postCountdown(ctx, b);
            RestNotifier.scheduleAlarm(ctx, b);
        }
    }
}
