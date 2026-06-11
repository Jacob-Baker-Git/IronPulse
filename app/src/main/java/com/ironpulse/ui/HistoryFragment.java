package com.ironpulse.ui;
import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class HistoryFragment extends Fragment {
    private AppRepository repo;
    private Spinner spinner;
    private HistoryChartView chartView;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_history, p, false);
        spinner   = v.findViewById(R.id.exercise_spinner);
        chartView = v.findViewById(R.id.history_chart);
        if (chartView != null) {
            android.util.TypedValue tv2 = new android.util.TypedValue();
            requireContext().getTheme().resolveAttribute(R.attr.colorCardBg, tv2, true);
            chartView.setBgColor(tv2.data);
            requireContext().getTheme().resolveAttribute(R.attr.colorTextMuted, tv2, true);
            chartView.setTextColor(tv2.data);
        }
        buildSpinner();
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> a, View v2, int pos, long id) { buildChart(); }
            public void onNothingSelected(AdapterView<?> a) {}
        });
        return v;
    }

    @Override public void onResume() { super.onResume(); buildSpinner(); }

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
            List<ExerciseData> done = repo.completed.getOrDefault(d, Collections.emptyList());
            double vol = done.stream().filter(ex -> ex.getName().equals(name))
                    .mapToDouble(repo::estimateVolume).sum();
            labels.add(d.format(fmt));
            values.add(vol);
        }
        chartView.setData(values, labels);
    }

}
