package com.ironpulse.ui;

import android.content.*;
import android.os.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.*;

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

        String name = getIntent().getStringExtra("exercise_name");
        String ds   = getIntent().getStringExtra("date");
        try { date = ds != null ? LocalDate.parse(ds) : LocalDate.now(); }
        catch (Exception e) { date = LocalDate.now(); }

        exercise = repo.exercises.stream()
                .filter(ex -> ex.getName().equals(name))
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

        if (exercise == null) {
            titleView.setText("Exercise not found");
            logSetBtn.setEnabled(false);
            pauseBtn.setVisibility(android.view.View.GONE);
            weightField.setEnabled(false);
            repsField.setEnabled(false);
            timerView.setText("");
            return;
        }

        String[] rp = exercise.getReps() == null ? new String[]{"3"} : exercise.getReps().split("x");
        targetSets  = rp.length > 0 ? pi(rp[0], 3) : 3;

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
                pauseBtn.setText("Resume");
            } else if (paused && remainingSec > 0) {
                paused = false;
                pauseBtn.setText("Pause");
                startCountdown();
            }
        });
    }

    private void refresh() {
        titleView.setText(exercise.getName());
        String w = exercise.isBodyweight() ? "Bodyweight"
                : (exercise.getWeightKg() == Math.floor(exercise.getWeightKg())
                   ? (int)exercise.getWeightKg() + " kg"
                   : exercise.getWeightKg() + " kg");

        statsView.setText(w + "  ·  " + exercise.getReps()
                + "  ·  " + exercise.getRestSeconds() + "s"
                + "  ·  " + loggedSets + "/" + targetSets + " sets");

        // Progressive overload
        LocalDate lastWeek = date.minusDays(7);
        List<SetLog> prev = repo.setLogs.stream()
                .filter(s -> s.getExerciseName().equals(exercise.getName())
                          && s.getDate().equals(lastWeek))
                .collect(Collectors.toList());
        if (!prev.isEmpty()) {
            SetLog best = prev.stream()
                    .max(Comparator.comparingDouble(SetLog::volume))
                    .orElse(prev.get(0));
            prevBestView.setText("↑ Last week: " + best.formatDisplay());
        } else {
            prevBestView.setText("• First time on this date");
        }

        if (weightField.getText().toString().isEmpty())
            weightField.setText(exercise.isBodyweight() ? "BW" : exercise.getWeight());
        if (repsField.getText().toString().isEmpty()) {
            String[] rp = exercise.getReps() == null ? new String[]{"3"} : exercise.getReps().split("x");
            repsField.setText(rp.length > 1 ? rp[1] : "10");
        }

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
            // Accept comma decimals ("82,5") — common on European keyboards
            try { wKg = Double.parseDouble(wt.replace(',', '.').replaceAll("[^0-9.]", "")); }
            catch (Exception e) { wKg = exercise.getWeightKg(); }
        }
        int reps = pi(repsField.getText().toString(), 10);

        loggedSets++;
        repo.setLogs.add(new SetLog(date, exercise.getName(), loggedSets,
                wKg, reps, isBW || exercise.isBodyweight()));
        repo.saveAsync();

        if (loggedSets >= targetSets) {
            // All sets done (markComplete saves internally)
            repo.markComplete(date, exercise, true);
            cancelTimer();
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
        pauseBtn.setText("Pause");
        pauseBtn.setVisibility(android.view.View.VISIBLE);
        timerView.setTextColor(getResources().getColor(R.color.accent_2, getTheme()));
        timerView.setText(String.format(Locale.US, "Rest %d:%02d", totalSec / 60, totalSec % 60));
        startCountdown();
    }

    /** (Re)starts the countdown from {@link #remainingSec} — used on start and resume. */
    private void startCountdown() {
        restCountDown = new CountDownTimer(remainingSec * 1000L, 500) {
            @Override public void onTick(long remaining) {
                remainingSec = (int)(remaining / 1000);
                timerView.setText(String.format(Locale.US, "Rest %d:%02d",
                        remainingSec / 60, remainingSec % 60));
            }
            @Override public void onFinish() {
                remainingSec = 0;
                timerView.setText("Time's up — start your next set!");
                timerView.setTextColor(getResources().getColor(R.color.danger, getTheme()));
                pauseBtn.setVisibility(android.view.View.GONE);
                playBeepAndVibrate();
                restCountDown = null;
            }
        }.start();
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
