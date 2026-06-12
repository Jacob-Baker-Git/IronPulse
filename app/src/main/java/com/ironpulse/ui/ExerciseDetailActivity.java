package com.ironpulse.ui;

import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.data.Units;
import com.ironpulse.model.*;
import com.ironpulse.notify.Notifications;
import com.ironpulse.notify.RestNotifier;
import com.ironpulse.notify.RestTimerState;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class ExerciseDetailActivity extends AppCompatActivity {

    private AppRepository repo;
    private ExerciseData  exercise;
    private LocalDate     date;
    private int           loggedSets = 0;
    private int           targetSets = 0;
    private boolean       paused;
    private int           remainingSec;
    /** Last weight/reps actually logged — what the notification quick-log repeats. */
    private double        lastWKg;
    private int           lastReps;

    private CountDownTimer restCountDown;

    private TextView  titleView, statsView, prevBestView, timerView, historyView;
    private EditText  weightField, repsField;
    private Button    logSetBtn, pauseBtn;

    @Override
    protected void onCreate(Bundle state) {
        // Match the user's theme choice (MainActivity does the same)
        repo = AppRepository.get(this);
        setTheme(repo.darkMode ? R.style.Theme_IronPulse : R.style.Theme_IronPulse_Light);
        super.onCreate(state);
        setContentView(R.layout.activity_exercise_detail);

        // Edge-to-edge: keep content clear of the status/navigation bars
        android.view.View root = findViewById(R.id.detail_root);
        final int basePad = root.getPaddingLeft();
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            androidx.core.graphics.Insets bars = windowInsets.getInsets(
                    androidx.core.view.WindowInsetsCompat.Type.systemBars()
                            | androidx.core.view.WindowInsetsCompat.Type.displayCutout());
            v.setPadding(basePad + bars.left, basePad + bars.top,
                    basePad + bars.right, basePad + bars.bottom);
            return androidx.core.view.WindowInsetsCompat.CONSUMED;
        });

        String exId = getIntent().getStringExtra("exercise_id");
        String name = getIntent().getStringExtra("exercise_name");
        String ds   = getIntent().getStringExtra("date");
        try { date = ds != null ? LocalDate.parse(ds) : LocalDate.now(); }
        catch (Exception e) { date = LocalDate.now(); }

        // Stable id first (rename/duplicate-proof), name as the legacy fallback
        exercise = repo.exercises.stream()
                .filter(ex -> exId != null ? exId.equals(ex.getId()) : ex.getName().equals(name))
                .findFirst().orElse(null);
        // Deleted exercises still appear on past days via the completion snapshot —
        // fall back to that copy so the user can view their logged sets.
        if (exercise == null && name != null) {
            for (ExerciseData ex : repo.completed.getOrDefault(date, Collections.emptyList())) {
                if (ex.getName().equals(name)) { exercise = ex; break; }
            }
        }

        titleView    = findViewById(R.id.exercise_title);
        statsView    = findViewById(R.id.exercise_stats);
        prevBestView = findViewById(R.id.prev_best);
        timerView    = findViewById(R.id.timer_label);
        historyView  = findViewById(R.id.set_history);
        weightField  = findViewById(R.id.field_weight);
        repsField    = findViewById(R.id.field_reps);
        logSetBtn    = findViewById(R.id.btn_log_set);
        pauseBtn     = findViewById(R.id.btn_pause);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.weight_field_label))
                .setText("Weight (" + Units.unit() + " or BW)");
        findViewById(R.id.btn_plates).setOnClickListener(v -> {
            // Prefer what's typed in the weight field, fall back to the plan weight
            double kg = Units.parseToKg(weightField.getText().toString());
            if (kg <= 0 && exercise != null) kg = exercise.getWeightKg();
            PlateCalculator.show(this, kg);
        });

        if (exercise == null) {
            titleView.setText("Exercise not found");
            logSetBtn.setEnabled(false);
            pauseBtn.setVisibility(android.view.View.GONE);
            weightField.setEnabled(false);
            repsField.setEnabled(false);
            timerView.setText("");
            return;
        }

        targetSets = exercise.getSets();
        // Sensible quick-log defaults until the first in-app set overrides them
        lastWKg  = exercise.isBodyweight() ? 0 : exercise.getWeightKg();
        lastReps = exercise.getRepsPerSet();

        // Count sets already logged for this exercise on this date
        loggedSets = (int) repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(exercise.getName())
                          && s.getDate().equals(date))
                .count();

        refresh();

        // Only allow logging sets for TODAY — past days are a snapshot,
        // future days haven't happened yet
        boolean isToday = date.equals(LocalDate.now());
        if (!isToday) {
            logSetBtn.setEnabled(false);
            logSetBtn.setText(date.isBefore(LocalDate.now()) ? "Past day — view only" : "Future day — view only");
            pauseBtn.setVisibility(android.view.View.GONE);
            timerView.setText("");
            weightField.setEnabled(false);
            repsField.setEnabled(false);
        }

        logSetBtn.setOnClickListener(v -> logSet());
        pauseBtn.setOnClickListener(v -> {
            if (!paused && restCountDown != null) {
                // Actually stop the countdown — not just the display
                paused = true;
                restCountDown.cancel();
                restCountDown = null;
                RestTimerState.clear();
                RestNotifier.cancelAlarm(this);
                RestNotifier.cancel(this);
                setKeepScreenOn(false);
                pauseBtn.setText("Resume");
            } else if (paused && remainingSec > 0) {
                paused = false;
                pauseBtn.setText("Pause");
                RestTimerState.start(exercise.getName(), remainingSec);
                RestNotifier.postCountdown(this, restBundle());
                RestNotifier.scheduleAlarm(this, restBundle());
                startCountdown();
            }
        });
    }

    @Override protected void onResume() {
        super.onResume();
        if (exercise == null) return;
        // The notification quick-log may have advanced things while we were away
        int count = (int) repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(exercise.getName())
                          && s.getDate().equals(date))
                .count();
        if (count != loggedSets) { loggedSets = count; refresh(); }
        // A rest is anchored to wall-clock time — pick it up where it really is.
        if (date.equals(LocalDate.now()) && !paused
                && RestTimerState.isActiveFor(exercise.getName())
                && loggedSets < targetSets) {
            cancelTimer();
            remainingSec = RestTimerState.remainingSeconds();
            pauseBtn.setText("Pause");
            pauseBtn.setVisibility(android.view.View.VISIBLE);
            timerView.setTextColor(getResources().getColor(R.color.accent_2, getTheme()));
            startCountdown();
        }
    }

    /** Everything the quick-log receiver needs to repeat the last set. */
    private android.os.Bundle restBundle() {
        return RestNotifier.extras(exercise.getId(), exercise.getName(), date.toString(),
                lastWKg, lastReps, exercise.getRestSeconds(), targetSets, loggedSets);
    }

    private void refresh() {
        titleView.setText(exercise.getName());
        String w = exercise.isBodyweight() ? "Bodyweight" : Units.fmt(exercise.getWeightKg());

        statsView.setText(w + "  ·  " + exercise.getReps()
                + "  ·  " + exercise.getRestSeconds() + "s"
                + "  ·  " + loggedSets + "/" + targetSets + " sets");

        // Progressive overload: compare against the most recent earlier session,
        // however long ago it was — skipping a week must not lose the reference.
        LocalDate prevDate = repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(exercise.getName())
                          && s.getDate().isBefore(date))
                .map(SetLog::getDate)
                .max(Comparator.naturalOrder()).orElse(null);
        if (prevDate != null) {
            List<SetLog> prev = repo.setLogs.stream()
                    .filter(s -> s.getExerciseName().equals(exercise.getName())
                              && s.getDate().equals(prevDate))
                    .collect(Collectors.toList());
            SetLog best = prev.stream()
                    .max(Comparator.comparingDouble(SetLog::volume))
                    .orElse(prev.get(0));
            long daysAgo = java.time.temporal.ChronoUnit.DAYS.between(prevDate, date);
            String when = daysAgo == 1 ? "yesterday" : daysAgo + " days ago";
            prevBestView.setText("↑ Last session (" + when + "): " + best.formatDisplay());
        } else {
            prevBestView.setText("• First session — log your sets to start tracking");
        }

        if (weightField.getText().toString().isEmpty())
            weightField.setText(exercise.isBodyweight() ? "BW" : Units.num(exercise.getWeightKg()));
        if (repsField.getText().toString().isEmpty())
            repsField.setText(String.valueOf(exercise.getRepsPerSet()));

        List<SetLog> todaySets = repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(exercise.getName())
                          && s.getDate().equals(date))
                .collect(Collectors.toList());
        historyView.setText(todaySets.isEmpty() ? "No sets logged yet"
                : todaySets.stream().map(SetLog::formatDisplay).collect(Collectors.joining("\n")));

        // Lock the button if already at or past target — prevents logging extra sets on re-entry
        if (loggedSets >= targetSets && date.equals(LocalDate.now())) {
            logSetBtn.setEnabled(false);
            logSetBtn.setText("✓ Complete");
            timerView.setText("✓ All sets done");
            timerView.setTextColor(getResources().getColor(R.color.accent, getTheme()));
            pauseBtn.setVisibility(android.view.View.GONE);
        }
    }

    private void logSet() {
        // Guard: only today, never beyond target
        if (!date.equals(LocalDate.now())) return;
        if (loggedSets >= targetSets) return;

        String  wt   = weightField.getText().toString().trim();
        boolean isBW = wt.equalsIgnoreCase("BW") || wt.equalsIgnoreCase("bodyweight") || wt.isEmpty();
        double  wKg  = 0;
        if (!isBW) {
            // Input is in the current display unit; storage is always kg
            wKg = Units.parseToKg(wt);
            if (wKg <= 0) wKg = exercise.getWeightKg();
        }
        int reps = pi(repsField.getText().toString(), 10);

        loggedSets++;
        lastWKg = isBW || exercise.isBodyweight() ? 0 : wKg;
        lastReps = reps;
        repo.setLogs.add(new SetLog(date, exercise.getName(), loggedSets,
                wKg, reps, isBW || exercise.isBodyweight()));
        RecordData pr = repo.checkForNewPR(exercise.getName(),
                isBW || exercise.isBodyweight() ? 0 : wKg);
        if (pr != null) Toast.makeText(this, "🎉 New PR: " + exercise.getName()
                + " " + Units.fmt(wKg) + "!", Toast.LENGTH_LONG).show();
        repo.saveAsync();
        for (com.ironpulse.data.Achievements.Def d :
                com.ironpulse.data.Achievements.checkAndUnlock(repo))
            Toast.makeText(this, "🏆 Achievement unlocked: " + d.emoji + " " + d.title,
                    Toast.LENGTH_LONG).show();

        if (loggedSets >= targetSets) {
            // All sets done (markComplete saves internally)
            repo.markComplete(date, exercise, true);
            cancelTimer();
            RestTimerState.clear();
            RestNotifier.cancelAlarm(this);
            RestNotifier.cancel(this);
            setKeepScreenOn(false);
            timerView.setText("✓ All sets done!");
            timerView.setTextColor(getResources().getColor(R.color.accent, getTheme()));
            logSetBtn.setEnabled(false);
            logSetBtn.setText("✓ Complete");
            pauseBtn.setVisibility(android.view.View.GONE);
        } else {
            startRestTimer(exercise.getRestSeconds());
        }
        refresh();
    }

    private void startRestTimer(int totalSec) {
        cancelTimer();
        paused = false;
        remainingSec = totalSec;
        RestTimerState.start(exercise.getName(), totalSec);
        maybeRequestNotifPermission();
        RestNotifier.postCountdown(this, restBundle());
        RestNotifier.scheduleAlarm(this, restBundle());
        pauseBtn.setText("Pause");
        pauseBtn.setVisibility(android.view.View.VISIBLE);
        timerView.setTextColor(getResources().getColor(R.color.accent_2, getTheme()));
        timerView.setText(String.format(Locale.US, "Rest %d:%02d", totalSec / 60, totalSec % 60));
        startCountdown();
    }

    /** (Re)starts the countdown from {@link #remainingSec} — used on start and resume. */
    private void startCountdown() {
        setKeepScreenOn(true); // don't let the screen lock mid-rest
        restCountDown = new CountDownTimer(remainingSec * 1000L, 500) {
            @Override public void onTick(long remaining) {
                remainingSec = (int)(remaining / 1000);
                timerView.setText(String.format(Locale.US, "Rest %d:%02d",
                        remainingSec / 60, remainingSec % 60));
            }
            @Override public void onFinish() {
                remainingSec = 0;
                RestTimerState.clear();
                RestNotifier.cancelAlarm(ExerciseDetailActivity.this);
                RestNotifier.cancel(ExerciseDetailActivity.this);
                setKeepScreenOn(false);
                timerView.setText("Time's up — start your next set!");
                timerView.setTextColor(getResources().getColor(R.color.danger, getTheme()));
                pauseBtn.setVisibility(android.view.View.GONE);
                playBeepAndVibrate();
                restCountDown = null;
            }
        }.start();
    }

    // ── Rest notification + background alarm (delegated to RestNotifier) ────

    private void maybeRequestNotifPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this)) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
    }

    private void setKeepScreenOn(boolean on) {
        if (on) getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else    getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void playBeepAndVibrate() {
        // Beep
        try {
            android.media.ToneGenerator tg = new android.media.ToneGenerator(
                    android.media.AudioManager.STREAM_NOTIFICATION, 90);
            // Three short beeps
            tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 300);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 300);
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    tg.startTone(android.media.ToneGenerator.TONE_PROP_BEEP2, 400);
                    new Handler(Looper.getMainLooper()).postDelayed(tg::release, 500);
                }, 400);
            }, 400);
        } catch (Exception ignored) {}

        // Vibrate
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v == null || !v.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200, 100, 300}, -1));
        } else {
            v.vibrate(new long[]{0, 200, 100, 200, 100, 300}, -1);
        }
    }

    private void cancelTimer() {
        if (restCountDown != null) { restCountDown.cancel(); restCountDown = null; }
    }

    @Override protected void onDestroy() { cancelTimer(); super.onDestroy(); }

    private int pi(String s, int fb) {
        try { return Math.max(1, Integer.parseInt(s.trim())); } catch (Exception e) { return fb; }
    }
}
