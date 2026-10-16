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

        // Same wording as the Today card: "Leg day", "0 of 5 done · 🔥 3 day streak"
        boolean rest = repo.isRestDay(today);
        List<ExerciseData> planned = repo.getExercisesForDate(today);
        String title = com.ironpulse.ui.WorkoutFragment.dayTitle(planned, false, rest);
        List<ExerciseData> doneList = repo.completed.getOrDefault(today, Collections.emptyList());
        int done = 0;
        for (ExerciseData ex : planned) if (doneList.contains(ex)) done++;

        String streakPart = streak > 0 ? "🔥 " + streak + " day streak"
                : potential > 0 ? "train to make it " + (potential + 1) + " days" : "";
        String todayLine;
        if (rest)                       todayLine = "Recovery is part of the plan";
        else if (planned.isEmpty())     todayLine = "Nothing planned — tap to add";
        else if (done >= planned.size()) todayLine = "✓ Workout complete";
        else                            todayLine = done + " of " + planned.size() + " done";
        if (!streakPart.isEmpty()) todayLine += "  ·  " + streakPart;

        RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_workout);
        rv.setTextViewText(R.id.widget_streak, title);
        rv.setTextViewText(R.id.widget_today, todayLine);
        rv.setViewVisibility(R.id.widget_bar, planned.isEmpty() || rest
                ? android.view.View.GONE : android.view.View.VISIBLE);
        rv.setProgressBar(R.id.widget_bar, Math.max(1, planned.size()), done, false);
        rv.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(ctx, 0,
                new Intent(ctx, com.ironpulse.ui.MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        mgr.updateAppWidget(ids, rv);
    }
}
