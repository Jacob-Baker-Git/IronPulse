package com.ironpulse.ui;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.CardioEntry;
import java.time.LocalDate;
import java.util.*;

public class CardioFragment extends Fragment {
    private AppRepository repo;
    private LinearLayout entryList;
    private TextView weekDist, totalSessions, bestPace;
    private Button editBtn;
    private boolean editMode = false;

    private int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_cardio, p, false);
        entryList     = v.findViewById(R.id.cardio_list);
        weekDist      = v.findViewById(R.id.stat_week_dist);
        totalSessions = v.findViewById(R.id.stat_total_sessions);
        bestPace      = v.findViewById(R.id.stat_best_pace);
        editBtn       = v.findViewById(R.id.btn_cardio_edit);

        Button addBtn = v.findViewById(R.id.btn_add_cardio);
        ButtonStyles.log(addBtn);
        addBtn.setOnClickListener(x -> showCardioDialog(null));
        ButtonStyles.toggle(editBtn, false);
        editBtn.setOnClickListener(x -> {
            editMode = !editMode;
            editBtn.setText(editMode ? "Done" : "Edit");
            ButtonStyles.toggle(editBtn, editMode);
            refresh();
        });

        refresh();
        return v;
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        LocalDate ws = LocalDate.now().with(java.time.DayOfWeek.MONDAY);
        double wd = repo.cardio.stream()
                .filter(e -> !e.getDate().isBefore(ws))
                .mapToDouble(CardioEntry::getDistanceKm).sum();
        double bp = repo.cardio.stream()
                .filter(e -> e.getDistanceKm() > 0 && e.getDurationMinutes() > 0)
                .mapToDouble(CardioEntry::getPaceMinPerKm).min().orElse(0);
        weekDist.setText(wd > 0 ? String.format("%.1f km", wd) : "—");
        totalSessions.setText(String.valueOf(repo.cardio.size()));
        bestPace.setText(bp > 0 ? String.format("%.1f min/km", bp) : "—");

        entryList.removeAllViews();
        List<CardioEntry> sorted = new ArrayList<>(repo.cardio);
        sorted.sort(Comparator.comparing(CardioEntry::getDate).reversed());

        if (sorted.isEmpty()) {
            TextView t = new TextView(requireContext());
            t.setText("No cardio logged yet.\nTap + to log a session.");
            t.setTextColor(themeColor(R.attr.colorTextMuted));
            t.setPadding(0, 32, 0, 0);
            entryList.addView(t);
            return;
        }

        int cardBg = themeColor(R.attr.colorCardBg);
        int textPrimary = themeColor(R.attr.colorTextPrimary);
        int textMuted = themeColor(R.attr.colorTextMuted);

        for (CardioEntry e : sorted) {
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(cardBg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, 10); card.setLayoutParams(lp);
            card.setPadding(20, 16, 20, 16);

            // Top row: type + date
            LinearLayout top = new LinearLayout(requireContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView td = new TextView(requireContext());
            td.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            td.setText(e.getType() + "  •  " + e.getDate());
            td.setTextColor(textPrimary); td.setTextSize(14);
            top.addView(td);

            if (editMode) {
                Button eb = new Button(requireContext());
                eb.setText("Edit"); ButtonStyles.edit(eb);
                LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                ep.setMargins(0, 0, 6, 0); eb.setLayoutParams(ep);
                eb.setOnClickListener(x -> showCardioDialog(e));

                Button db = new Button(requireContext());
                db.setText("Delete"); ButtonStyles.delete(db);
                db.setOnClickListener(x -> Dialogs.confirmDelete(requireContext(),
                        "this " + e.getType() + " session (" + e.getDate() + ")", () -> {
                    repo.cardio.remove(e); repo.saveAsync();
                    if (repo.cardio.isEmpty()) { editMode = false; editBtn.setText("Edit"); ButtonStyles.toggle(editBtn, false); }
                    refresh();
                }));
                top.addView(eb); top.addView(db);
            }

            // Detail row
            String det;
            if (e.getDistanceKm() > 0 && e.getDurationMinutes() > 0)
                det = String.format("%.1f km  |  %d min  |  %.1f min/km",
                        e.getDistanceKm(), e.getDurationMinutes(), e.getPaceMinPerKm());
            else if (e.getDistanceKm() > 0)
                det = String.format("%.1f km", e.getDistanceKm());
            else
                det = e.getDurationMinutes() + " min";
            if (e.getNotes() != null && !e.getNotes().isEmpty()) det += "  |  " + e.getNotes();

            TextView dv = new TextView(requireContext());
            dv.setText(det); dv.setTextColor(textMuted); dv.setTextSize(12);
            dv.setPadding(0, 4, 0, 0);

            card.addView(top); card.addView(dv);
            entryList.addView(card);
        }
    }

    private void showCardioDialog(CardioEntry existing) {
        String[] types = {"Run","Cycle","Row","Swim","Walk","Other"};
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0);

        Spinner ts = new Spinner(requireContext());
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, types);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ts.setAdapter(a);
        if (existing != null)
            for (int i = 0; i < types.length; i++)
                if (types[i].equals(existing.getType())) { ts.setSelection(i); break; }

        EditText df = new EditText(requireContext());
        df.setText(existing != null ? existing.getDate().toString() : LocalDate.now().toString());
        EditText distF = new EditText(requireContext());
        distF.setHint("Distance (km)");
        distF.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        distF.setText(existing != null ? String.valueOf(existing.getDistanceKm()) : "");
        EditText durF = new EditText(requireContext());
        durF.setHint("Duration (min)");
        durF.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        durF.setText(existing != null ? String.valueOf(existing.getDurationMinutes()) : "");
        EditText nf = new EditText(requireContext());
        nf.setHint("Notes (optional)");
        nf.setText(existing != null ? existing.getNotes() : "");

        int tc = themeColor(R.attr.colorTextPrimary);
        l.addView(lbl("Type", tc));      l.addView(ts);
        l.addView(lbl("Date (YYYY-MM-DD)", tc)); l.addView(df);
        l.addView(lbl("Distance (km)", tc)); l.addView(distF);
        l.addView(lbl("Duration (minutes)", tc)); l.addView(durF);
        l.addView(lbl("Notes", tc));     l.addView(nf);

        new AlertDialog.Builder(requireContext())
            .setTitle(existing != null ? "Edit Cardio" : "Log Cardio")
            .setView(l)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    LocalDate date = LocalDate.parse(df.getText().toString().trim());
                    double dist = distF.getText().toString().trim().isEmpty() ? 0
                            : Double.parseDouble(distF.getText().toString().trim().replace(',', '.'));
                    int dur = durF.getText().toString().trim().isEmpty() ? 0
                            : Integer.parseInt(durF.getText().toString().trim());
                    if (existing != null) repo.cardio.remove(existing);
                    repo.cardio.add(new CardioEntry(date, ts.getSelectedItem().toString(),
                            dist, dur, nf.getText().toString().trim()));
                    repo.saveAsync(); refresh();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Check your inputs", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("Cancel", null).show();
    }

    private TextView lbl(String text, int color) {
        TextView t = new TextView(requireContext());
        t.setText(text); t.setTextColor(color); t.setPadding(0, 8, 0, 2);
        return t;
    }
}
