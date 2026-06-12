package com.ironpulse.ui;
import android.app.AlertDialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.BodyWeightEntry;
import java.time.LocalDate;
import java.util.*;

public class BodyFragment extends Fragment {
    private AppRepository repo;
    private TextView lastWeighedText, heightBtn;
    private WeightChartView chartView;
    private LinearLayout goalContainer;
    private RecyclerView recycler;
    private EntryAdapter adapter;
    private LinearLayoutManager layoutManager;
    private Button editBtn;
    private boolean editMode = false;
    /** Newest-first list backing the log (sorted ascending, then reversed for display). */
    private final List<BodyWeightEntry> sortedAsc = new ArrayList<>();

    private int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
    private int color(int res) { return requireContext().getResources().getColor(res, requireContext().getTheme()); }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_body, p, false);
        lastWeighedText = v.findViewById(R.id.last_weighed);
        heightBtn       = v.findViewById(R.id.btn_set_height);
        chartView       = v.findViewById(R.id.weight_chart);
        goalContainer   = v.findViewById(R.id.goal_container);
        recycler        = v.findViewById(R.id.body_recycler);
        editBtn         = v.findViewById(R.id.btn_body_edit);

        heightBtn.setText(repo.heightCm > 0 ? String.format("%.0f cm", repo.heightCm) : "Set Height");
        heightBtn.setOnClickListener(x -> showHeightDialog());
        Button addBtn = v.findViewById(R.id.btn_add_weight);
        ButtonStyles.log(addBtn);
        addBtn.setOnClickListener(x -> showWeightDialog(null));

        ButtonStyles.toggle(editBtn, false);
        editBtn.setOnClickListener(x -> {
            editMode = !editMode;
            editBtn.setText(editMode ? "Done" : "Edit");
            ButtonStyles.toggle(editBtn, editMode);
            if (adapter != null) adapter.notifyDataSetChanged();
        });

        Button clearBtn = v.findViewById(R.id.btn_clear_weight);
        ButtonStyles.delete(clearBtn);
        clearBtn.setOnClickListener(x -> {
            if (repo.bodyEntries.isEmpty()) {
                Toast.makeText(requireContext(), "No entries to clear", Toast.LENGTH_SHORT).show();
                return;
            }
            Dialogs.confirm(requireContext(), "Clear all entries",
                    "Are you sure you want to delete ALL " + repo.bodyEntries.size()
                            + " weight entries? This cannot be undone.", "Delete All", () -> {
                repo.bodyEntries.clear();
                repo.saveAsync();
                editMode = false; editBtn.setText("Edit"); ButtonStyles.toggle(editBtn, false);
                refresh();
            });
        });

        if (chartView != null) {
            chartView.setBgColor(themeColor(R.attr.colorCardBg));
            chartView.setTextColor(themeColor(R.attr.colorTextMuted));
            // Dragging the graph scrolls the log, which in turn drives the graph.
            chartView.setOnGraphDragListener(points -> {
                if (sortedAsc.isEmpty()) return;
                int rh = dragRowHeight();
                recycler.scrollBy(0, Math.round(points * rh));
            });
        }

        adapter = new EntryAdapter();
        layoutManager = new LinearLayoutManager(requireContext());
        recycler.setLayoutManager(layoutManager);
        recycler.setAdapter(adapter);
        recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateGraphFromLog();
            }
        });

        // Cap the log viewport at 5 rows so the chart's green bar underlines a
        // 5-entry window inside the 7-point graph instead of spanning all of it.
        // On screens too short for 5 rows the weight-based height stays as-is.
        recycler.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (recycler.getChildCount() == 0) return;
                View first = recycler.getChildAt(0);
                if (first == null || first.getHeight() == 0) return;
                recycler.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int target = first.getHeight() * 5;
                if (recycler.getHeight() > target) {
                    LinearLayout.LayoutParams lp =
                            (LinearLayout.LayoutParams) recycler.getLayoutParams();
                    lp.height = target;
                    lp.weight = 0;
                    recycler.setLayoutParams(lp);
                    recycler.post(BodyFragment.this::updateGraphFromLog);
                }
            }
        });

        refresh();
        return v;
    }

    @Override public void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        sortedAsc.clear();
        sortedAsc.addAll(repo.bodyEntries);
        sortedAsc.sort(Comparator.comparing(BodyWeightEntry::getDate));

        if (sortedAsc.isEmpty()) {
            lastWeighedText.setText("No entries yet");
            if (chartView != null) chartView.setData(Collections.emptyList());
            if (adapter != null) adapter.notifyDataSetChanged();
            buildGoalBar();
            return;
        }

        if (chartView != null) chartView.setData(sortedAsc);

        BodyWeightEntry latest = sortedAsc.get(sortedAsc.size() - 1);
        long ago = java.time.temporal.ChronoUnit.DAYS.between(latest.getDate(), LocalDate.now());
        // Future-dated entries would read "-7 days ago" — show the date instead
        String when = ago == 0 ? "today" : ago == 1 ? "yesterday"
                : ago > 1 ? ago + " days ago" : formatDate(latest.getDate());
        lastWeighedText.setText(String.format("Last: %.1f kg — %s", latest.getWeightKg(), when));

        buildGoalBar();
        if (adapter != null) adapter.notifyDataSetChanged();
        // Align the log with the chart's default (most-recent) window.
        recycler.post(this::updateGraphFromLog);
    }

    // ── Chart <-> log sync ────────────────────────────────────────────────────

    /**
     * Log → graph: report the chronological index range currently visible in the log
     * so the graph can underline it with the green bar and shift its window if needed.
     * The log is newest-first (position 0 = newest), the graph is oldest→newest.
     */
    private void updateGraphFromLog() {
        if (chartView == null || sortedAsc.isEmpty()) return;
        int p = layoutManager.findFirstVisibleItemPosition();
        if (p == RecyclerView.NO_POSITION) return;
        View first = layoutManager.findViewByPosition(p);
        if (first == null || first.getHeight() == 0) return;
        float rowH = first.getHeight();
        float fracTop = -first.getTop() / rowH;          // part of top row scrolled off-screen
        float topRev = p + fracTop;                       // reversed index at the viewport top
        float rows = recycler.getHeight() / rowH;         // how many rows fit
        int n = sortedAsc.size();
        // 5 visible rows = 4 gaps between their points, so the span is (rows - 1).
        float visLast  = (n - 1) - topRev;                // newest visible → right side of graph
        float visFirst = visLast - Math.max(0, rows - 1); // oldest visible → left side of graph
        chartView.setVisibleRange(visFirst, visLast);
    }

    /** Height of a log row, used to map a graph drag onto a log scroll. */
    private int dragRowHeight() {
        int p = layoutManager.findFirstVisibleItemPosition();
        if (p != RecyclerView.NO_POSITION) {
            View v = layoutManager.findViewByPosition(p);
            if (v != null && v.getHeight() > 0) return v.getHeight();
        }
        return dp(48);
    }

    // ── Goal progress bar ──────────────────────────────────────────────────────

    private void buildGoalBar() {
        if (goalContainer == null) return;
        goalContainer.removeAllViews();

        double goal = repo.goalWeightKg;
        if (goal <= 0) {
            goalContainer.setVisibility(View.GONE);
            return;
        }
        goalContainer.setVisibility(View.VISIBLE);

        double start = repo.startWeightKg > 0 ? repo.startWeightKg
                : (sortedAsc.isEmpty() ? 0 : sortedAsc.get(0).getWeightKg());
        double current = sortedAsc.isEmpty() ? start : sortedAsc.get(sortedAsc.size() - 1).getWeightKg();

        float fill;
        if (start == goal) fill = 1f;
        else fill = (float) ((start - current) / (start - goal));
        fill = Math.max(0f, Math.min(1f, fill));
        boolean reached = fill >= 1f;

        // Title row: label + big percent
        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(requireContext());
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        title.setText("GOAL PROGRESS");
        title.setTextColor(themeColor(R.attr.colorTextMuted)); title.setTextSize(11);
        title.setLetterSpacing(0.08f);
        TextView pct = new TextView(requireContext());
        pct.setText((reached ? "✓ " : "") + String.format("%.0f%%", fill * 100));
        pct.setTextColor(color(R.color.accent)); pct.setTextSize(18);
        pct.setTypeface(null, android.graphics.Typeface.BOLD);
        titleRow.addView(title); titleRow.addView(pct);
        goalContainer.addView(titleRow);

        // Rounded gradient pill track + fill
        int trackH = dp(18), inset = dp(2);
        GradientDrawable trackBg = new GradientDrawable();
        trackBg.setCornerRadius(trackH / 2f);
        trackBg.setColor(themeColor(R.attr.streakTrack));
        LinearLayout track = new LinearLayout(requireContext());
        track.setOrientation(LinearLayout.HORIZONTAL);
        track.setWeightSum(1f);
        track.setBackground(trackBg);
        track.setPadding(inset, inset, inset, inset);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, trackH);
        tlp.setMargins(0, 8, 0, 0); track.setLayoutParams(tlp);

        GradientDrawable fillBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFF0E8E5A, 0xFF3BEFA0});
        fillBg.setCornerRadius((trackH - 2 * inset) / 2f);
        View filled = new View(requireContext());
        filled.setBackground(fillBg);
        filled.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, Math.max(0.0001f, fill)));
        track.addView(filled);
        if (fill < 1f) {
            View rest = new View(requireContext());
            rest.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f - fill));
            track.addView(rest);
        }
        goalContainer.addView(track);

        // Labels row: Start • Now • Goal + percent
        LinearLayout labels = new LinearLayout(requireContext());
        labels.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        llp.setMargins(0, 8, 0, 0); labels.setLayoutParams(llp);

        labels.addView(label(String.format("Start %.1f kg", start), 1f, Gravity.START, themeColor(R.attr.colorTextMuted)));
        labels.addView(label(String.format("Goal %.1f kg", goal), 1f, Gravity.END, color(R.color.accent)));
        goalContainer.addView(labels);

        // Current weight caption
        TextView now = new TextView(requireContext());
        double remaining = current - goal;
        String dir = (start - goal) >= 0 ? "to lose" : "to gain";
        now.setText(Math.abs(remaining) < 0.05
                ? String.format("Now %.1f kg — goal reached!", current)
                : String.format("Now %.1f kg  ·  %.1f kg %s", current, Math.abs(remaining), dir));
        now.setTextColor(themeColor(R.attr.colorTextMuted)); now.setTextSize(11);
        now.setPadding(0, 6, 0, 0);
        goalContainer.addView(now);
    }

    private TextView label(String text, float weight, int gravity, int color) {
        TextView t = new TextView(requireContext());
        t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight));
        t.setText(text); t.setTextColor(color); t.setTextSize(11); t.setGravity(gravity);
        return t;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    // ── Entry log adapter (newest first) ───────────────────────────────────────

    private class EntryAdapter extends RecyclerView.Adapter<EntryAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            LinearLayout row; TextView date, val; View divider;
            VH(LinearLayout container) { super(container);
                row = (LinearLayout) ((LinearLayout) container).getChildAt(0);
                date = (TextView) row.getChildAt(0);
                val  = (TextView) row.getChildAt(1);
                divider = container.getChildAt(1);
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int t) {
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp(14), 0, dp(14));
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView dv = new TextView(requireContext());
            dv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
            dv.setTextSize(13);
            TextView vv = new TextView(requireContext());
            vv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f));
            vv.setTextSize(12);
            row.addView(dv); row.addView(vv);
            container.addView(row);

            View div = new View(requireContext());
            div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
            container.addView(div);
            return new VH(container);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            // Display newest first
            BodyWeightEntry e = sortedAsc.get(sortedAsc.size() - 1 - pos);
            int textPrimary = themeColor(R.attr.colorTextPrimary);
            int textMuted   = themeColor(R.attr.colorTextMuted);
            h.date.setTextColor(textPrimary);
            h.date.setText(formatDate(e.getDate()));

            double hm = repo.heightCm / 100.0;
            StringBuilder val = new StringBuilder(String.format("%.1f kg", e.getWeightKg()));
            if (hm > 0) {
                double bmi = e.getWeightKg() / (hm * hm);
                String cat = bmi < 18.5 ? "Underweight" : bmi < 25 ? "Normal" : bmi < 30 ? "Overweight" : "Obese";
                val.append(String.format("  ·  BMI %.1f (%s)", bmi, cat));
            }
            h.val.setTextColor(textMuted);
            h.val.setText(val.toString());
            h.divider.setBackgroundColor(color(R.color.border));

            // Edit-mode buttons (added/removed dynamically)
            while (h.row.getChildCount() > 2) h.row.removeViewAt(2);
            if (editMode) {
                Button eb = new Button(requireContext());
                eb.setText("Edit"); ButtonStyles.edit(eb);
                LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                elp.setMargins(6, 0, 0, 0); eb.setLayoutParams(elp);
                eb.setOnClickListener(x -> showWeightDialog(e));

                Button db = new Button(requireContext());
                db.setText("Delete"); ButtonStyles.delete(db);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                dlp.setMargins(6, 0, 0, 0); db.setLayoutParams(dlp);
                db.setOnClickListener(x -> Dialogs.confirmDelete(requireContext(),
                        String.format("the entry for %s (%.1f kg)", formatDate(e.getDate()), e.getWeightKg()), () -> {
                    repo.bodyEntries.remove(e);
                    repo.saveAsync();
                    if (repo.bodyEntries.isEmpty()) { editMode = false; editBtn.setText("Edit");
                        ButtonStyles.toggle(editBtn, false); }
                    refresh();
                }));
                h.row.addView(eb); h.row.addView(db);
            }
        }

        @Override public int getItemCount() { return sortedAsc.size(); }
    }

    private String formatDate(LocalDate d) {
        return d.getDayOfWeek().toString().substring(0, 3) + " "
                + d.getDayOfMonth() + " "
                + d.getMonth().toString().substring(0, 3) + " "
                + d.getYear();
    }

    /** Pass null to add a new entry, pass existing entry to edit it */
    private void showWeightDialog(BodyWeightEntry existing) {
        boolean isEdit = existing != null;
        EditText df = new EditText(requireContext());
        df.setHint("YYYY-MM-DD");
        df.setInputType(android.text.InputType.TYPE_CLASS_DATETIME | android.text.InputType.TYPE_DATETIME_VARIATION_DATE);
        df.setText(isEdit ? existing.getDate().toString() : LocalDate.now().toString());

        EditText wf = new EditText(requireContext());
        wf.setHint("Weight (kg)  e.g. 82.5");
        wf.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        // Locale.US: a comma decimal ("82,5") can be mangled by the digits filter
        if (isEdit) wf.setText(String.format(java.util.Locale.US, "%.1f", existing.getWeightKg()));

        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 8);
        TextView dateLbl = new TextView(requireContext());
        dateLbl.setText("Date"); dateLbl.setTextColor(themeColor(R.attr.colorTextPrimary));
        TextView weightLbl = new TextView(requireContext());
        weightLbl.setText("Weight (kg)"); weightLbl.setTextColor(themeColor(R.attr.colorTextPrimary));
        l.addView(dateLbl); l.addView(df);
        l.addView(weightLbl); l.addView(wf);

        new AlertDialog.Builder(requireContext())
            .setTitle(isEdit ? "Edit Entry" : "Log Weight")
            .setView(l)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    LocalDate date = LocalDate.parse(df.getText().toString().trim());
                    double kg = Double.parseDouble(wf.getText().toString().trim().replace(",", "."));
                    if (kg <= 0 || kg > 700) throw new NumberFormatException();
                    Runnable commit = () -> {
                        if (isEdit) repo.bodyEntries.remove(existing);
                        repo.bodyEntries.removeIf(e -> e.getDate().equals(date) && e != existing);
                        repo.bodyEntries.add(new BodyWeightEntry(date, kg));
                        repo.saveAsync(); refresh();
                    };
                    // One entry per day — warn before overwriting an existing one
                    BodyWeightEntry dup = null;
                    for (BodyWeightEntry e2 : repo.bodyEntries)
                        if (e2.getDate().equals(date) && e2 != existing) { dup = e2; break; }
                    if (dup != null) {
                        Dialogs.confirm(requireContext(), "Replace entry",
                                String.format("You already logged %.1f kg on %s. Replace it with %.1f kg?",
                                        dup.getWeightKg(), formatDate(date), kg), "Replace", commit);
                    } else {
                        commit.run();
                    }
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Invalid input", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null).show();
    }

    private void showHeightDialog() {
        EditText f = new EditText(requireContext());
        f.setHint("Height in cm  e.g. 178");
        f.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (repo.heightCm > 0) f.setText(String.valueOf((int) repo.heightCm));
        new AlertDialog.Builder(requireContext()).setTitle("Set Height").setView(f)
            .setPositiveButton("Save", (d, w) -> {
                try {
                    double cm = Double.parseDouble(f.getText().toString().trim().replace(",", "."));
                    if (cm <= 0 || cm > 300) throw new NumberFormatException();
                    repo.heightCm = cm;
                    repo.saveAsync();
                    heightBtn.setText(String.format("%.0f cm", repo.heightCm));
                    refresh();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), "Invalid height", Toast.LENGTH_SHORT).show();
                }
            }).setNegativeButton("Cancel", null).show();
    }
}
