package com.ironpulse.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.ExerciseData;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/** Glanceable streak + today's plan on the launcher. Tap anywhere to open the app. */
public class WorkoutWidgetProvider extends AppWidgetProvider {

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        updateAll(ctx);
    }

    /** Called from the repository after every save so the widget never goes stale. */
    public static void updateAll(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        if (mgr == null) return;
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WorkoutWidgetProvider.class));
        if (ids == null || ids.length == 0) return;

        AppRepository repo = AppRepository.get(ctx);
        LocalDate today = LocalDate.now();
        int streak = repo.computeStreak();
        int potential = streak > 0 ? 0 : repo.computePotentialStreak();

        String streakLine;
        if (streak > 0)        streakLine = "🔥 " + streak + " day streak";
        else if (potential > 0) streakLine = "🔥 " + potential + " days — train today!";
        else                    streakLine = "IronPulse";

        String todayLine;
        List<ExerciseData> planned = repo.getExercisesForDate(today);
        if (repo.isRestDay(today))      todayLine = "Rest day — recover well 🛌";
        else if (planned.isEmpty())     todayLine = "Nothing scheduled today";
        else {
            int done = repo.completed.getOrDefault(today, Collections.emptyList()).size();
            todayLine = done >= planned.size() ? "✓ Workout complete!"
                    : done + " of " + planned.size() + " exercises done";
        }

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_workout);
        rv.setTextViewText(R.id.widget_streak, streakLine);
        rv.setTextViewText(R.id.widget_today, todayLine);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, com.ironpulse.ui.MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mgr.updateAppWidget(ids, rv);
    }
}
