package com.ironpulse.ui;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.data.Units;
import com.ironpulse.model.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HistoryFragment extends Fragment {
    private AppRepository repo;
    private Spinner spinner;
    private HistoryChartView chartView;
    private MonthHeatmapView heatmap;
    private TextView volBtn, e1rmBtn;
    /** 0 = training volume, 1 = estimated 1RM (Epley) */
    private int metric = 0;
    private YearMonth heatmapMonth = YearMonth.now();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_history, p, false);
        spinner   = v.findViewById(R.id.exercise_spinner);
        chartView = v.findViewById(R.id.history_chart);
        heatmap   = v.findViewById(R.id.month_heatmap);
        volBtn    = v.findViewById(R.id.metric_volume);
        e1rmBtn   = v.findViewById(R.id.metric_e1rm);
        if (chartView != null) {
            android.util.TypedValue tv2 = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(R.attr.colorCardBg, tv2, true);
            chartView.setBgColor(tv2.data);
            if (heatmap != null) heatmap.setBgColor(tv2.data);
            requireContext().getTheme().resolveAttribute(R.attr.colorTextMuted, tv2, true);
            chartView.setTextColor(tv2.data);
            if (heatmap != null) heatmap.setTextColor(tv2.data);
        }
        volBtn.setOnClickListener(x -> { metric = 0; updateMetricUI(); buildChart(); });
        e1rmBtn.setOnClickListener(x -> { metric = 1; updateMetricUI(); buildChart(); });
        updateMetricUI();
        if (heatmap != null) {
            heatmap.setOnMonthChangeListener(m -> { heatmapMonth = m; rebuildHeatmap(); });
        }
        buildSpinner();
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v2, int pos, long id) { buildChart(); }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        rebuildHeatmap();
        return v;
    }

    @Override public void onResume() { super.onResume(); buildSpinner(); rebuildHeatmap(); }

    private void updateMetricUI() {
        int accent = requireContext().getResources().getColor(R.color.accent, requireContext().getTheme());
        android.util.TypedValue tv2 = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(R.attr.colorTextMuted, tv2, true);
        int muted = tv2.data;
        volBtn.setTextColor(metric == 0 ? accent : muted);
        volBtn.setTypeface(null, metric == 0 ? Typeface.BOLD : Typeface.NORMAL);
        e1rmBtn.setTextColor(metric == 1 ? accent : muted);
        e1rmBtn.setTypeface(null, metric == 1 ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void buildSpinner() {
        Set<String> activeNames = new HashSet<>();
        for (ExerciseData ex : repo.exercises) activeNames.add(ex.getName());
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, List<ExerciseData>> e : repo.completed.entrySet())
            for (ExerciseData ex : e.getValue())
                if (activeNames.contains(ex.getName())) names.add(ex.getName());
        for (Map.Entry<LocalDate, List<ExerciseData>> e : repo.completed.entrySet())
            for (ExerciseData ex : e.getValue())
                if (!activeNames.contains(ex.getName())) names.add(ex.getName() + " (deleted)");
        List<String> list = new ArrayList<>(names);
        if (list.isEmpty()) list.add("No completed exercises yet");
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, list);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(a);
        buildChart();
    }

    private void buildChart() {
        if (spinner.getAdapter() == null || chartView == null) return;
        String rawName = spinner.getSelectedItem().toString();
        if (rawName.equals("No completed exercises yet")) { chartView.setData(null, null); return; }
        String name = rawName.endsWith(" (deleted)") ? rawName.substring(0, rawName.length() - 10) : rawName;
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("d/MM");
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        // 60 days of data so user can scroll back ~2 months
        for (int i = 59; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            double val;
            if (metric == 1) {
                // Best estimated 1RM that day (Epley: w × (1 + reps/30)) from real sets
                val = repo.setLogs.stream()
                        .filter(s2 -> s2.getExerciseName().equals(name) && s2.getDate().equals(d)
                                  && !s2.isBodyweight() && s2.getWeightKg() > 0)
                        .mapToDouble(s2 -> s2.getWeightKg() * (1 + s2.getReps() / 30.0))
                        .max().orElse(0);
            } else {
                // Prefer the actual logged sets — the truth of what was lifted that day.
                val = repo.setLogs.stream()
                        .filter(s2 -> s2.getExerciseName().equals(name) && s2.getDate().equals(d))
                        .mapToDouble(SetLog::volume).sum();
                // Fall back to the planned-volume estimate for days completed via the
                // checkbox without logging individual sets.
                if (val == 0) {
                    List<ExerciseData> done = repo.completed.getOrDefault(d, Collections.emptyList());
                    val = done.stream().filter(ex -> ex.getName().equals(name))
                            .mapToDouble(repo::estimateVolume).sum();
                }
            }
            labels.add(d.format(fmt));
            values.add(Units.toDisplay(val)); // shown in the display unit
        }
        chartView.setData(values, labels);
    }

    // ── Month heatmap ─────────────────────────────────────────────────────────

    private void rebuildHeatmap() {
        if (heatmap == null) return;
        YearMonth m = heatmapMonth;
        LocalDate today = LocalDate.now();
        int[] statuses = new int[m.lengthOfMonth()];
        for (int day = 1; day <= m.lengthOfMonth(); day++) {
            LocalDate d = m.atDay(day);
            boolean hasPlan = !repo.getExercisesForDate(d).isEmpty();
            boolean hasDone = !repo.completed.getOrDefault(d, Collections.emptyList()).isEmpty();
            if (repo.isRestWeekday(d))      statuses[day - 1] = MonthHeatmapView.REST;
            else if (d.isAfter(today))      statuses[day - 1] = hasPlan ? MonthHeatmapView.PLANNED : MonthHeatmapView.EMPTY;
            else if (d.isEqual(today))      statuses[day - 1] = !hasPlan ? MonthHeatmapView.EMPTY
                    : repo.isDateComplete(d) ? MonthHeatmapView.COMPLETE : MonthHeatmapView.PLANNED;
            else if (repo.isDateComplete(d)) statuses[day - 1] = MonthHeatmapView.COMPLETE;
            else if (hasPlan)                statuses[day - 1] = MonthHeatmapView.MISSED;
            else if (hasDone)                statuses[day - 1] = MonthHeatmapView.COMPLETE; // done via since-deleted plan
            else                             statuses[day - 1] = MonthHeatmapView.EMPTY;
        }
        heatmap.setMonth(m, statuses);
    }
}
