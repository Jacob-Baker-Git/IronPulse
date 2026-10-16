package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.data.Units;
import com.ironpulse.model.ExerciseData;

import java.time.*;
import java.time.format.*;
import java.util.*;

/**
 * Today: a headline card (what the day is + progress), the week, and the day's
 * exercises. Tap a row to log it, tap its ring to tick it off, long-press to edit
 * or delete. Rarer actions (templates, rest day, reorder) live behind ⋮.
 */
public class WorkoutFragment extends Fragment {

    private AppRepository repo;
    private LocalDate today = LocalDate.now();
    private LocalDate selectedDate = today;
    private LinearLayout weekRow;
    private TextView dateLabel, heroTitle, heroSub, emptyText;
    private ProgressBar heroBar;
    private RecyclerView exerciseRecycler;
    private FloatingActionButton fab;
    private ImageButton menuBtn;
    private boolean reorderMode = false;
    private ExerciseAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_workout, parent, false);

        weekRow          = v.findViewById(R.id.week_row);
        dateLabel        = v.findViewById(R.id.date_label);
        heroTitle        = v.findViewById(R.id.hero_title);
        heroSub          = v.findViewById(R.id.hero_sub);
        heroBar          = v.findViewById(R.id.hero_bar);
        emptyText        = v.findViewById(R.id.empty_text);
        exerciseRecycler = v.findViewById(R.id.exercise_recycler);
        fab              = v.findViewById(R.id.fab_add);
        menuBtn          = v.findViewById(R.id.btn_menu);

        adapter = new ExerciseAdapter();
        exerciseRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        exerciseRecycler.setAdapter(adapter);

        // Drag to reorder — only in reorder mode, started from the ≡ handle
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                    @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
                int from = vh.getAdapterPosition();
                int to   = t.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                List<ExerciseData> exercises = repo.getExercisesForDate(selectedDate);
                if (from < exercises.size() && to < exercises.size()) {
                    ExerciseData moved = exercises.get(from);
                    int fi = repo.exercises.indexOf(moved);
                    int ti = repo.exercises.indexOf(exercises.get(to));
                    if (fi >= 0 && ti >= 0) {
                        repo.exercises.remove(fi);
                        repo.exercises.add(ti, moved);
                        adapter.items.add(to, adapter.items.remove(from));
                        adapter.notifyItemMoved(from, to);
                        repo.saveAsync();
                    }
                }
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
            @Override public boolean isLongPressDragEnabled() { return false; }
        });
        touchHelper.attachToRecyclerView(exerciseRecycler);

        fab.setOnClickListener(x -> showAddDialog());
        menuBtn.setOnClickListener(x -> showMenu());
        dateLabel.setOnClickListener(x -> select(today)); // "tap for today" when looking elsewhere

        // Swipe the week row for the previous / next week
        GestureDetector swipe = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(@NonNull MotionEvent e) { return true; }
            @Override public boolean onFling(@Nullable MotionEvent a, @NonNull MotionEvent b, float vx, float vy) {
                if (a == null || Math.abs(b.getX() - a.getX()) < dp(40) || Math.abs(vx) < Math.abs(vy)) return false;
                select(selectedDate.plusWeeks(b.getX() < a.getX() ? 1 : -1));
                return true;
            }
            @Override public boolean onSingleTapUp(@NonNull MotionEvent e) {
                int i = (int) (e.getX() / (weekRow.getWidth() / 7f));
                select(monday(selectedDate).plusDays(Math.max(0, Math.min(6, i))));
                return true;
            }
        });
        weekRow.setOnTouchListener((w, e) -> swipe.onTouchEvent(e));

        refreshExercises();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        // If the app stayed open past midnight, move to the new today
        if (!today.equals(LocalDate.now())) {
            today = LocalDate.now();
            selectedDate = today;
        }
        refreshExercises();
    }

    private void select(LocalDate d) {
        selectedDate = d;
        if (d.isBefore(today)) reorderMode = false;
        refreshExercises();
    }

    private static LocalDate monday(LocalDate d) { return d.with(DayOfWeek.MONDAY); }

    // ── Week row ─────────────────────────────────────────────────────────────

    /** Kept under its old name: the dialogs below call it after changing the plan. */
    private void refreshDateStrip() {
        weekRow.removeAllViews();
        LocalDate start = monday(selectedDate);
        for (int i = 0; i < 7; i++) weekRow.addView(dayCell(start.plusDays(i)));
    }

    private View dayCell(LocalDate d) {
        LinearLayout cell = new LinearLayout(requireContext());
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER_HORIZONTAL);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView letter = new TextView(requireContext());
        letter.setText(d.getDayOfWeek().getDisplayName(TextStyle.NARROW, Locale.ENGLISH));
        letter.setTextSize(11);
        letter.setTextColor(themeColor(R.attr.colorTextMuted));
        letter.setGravity(Gravity.CENTER);
        letter.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cell.addView(letter);

        TextView num = new TextView(requireContext());
        num.setText(String.valueOf(d.getDayOfMonth()));
        num.setGravity(Gravity.CENTER);
        num.setTextSize(15);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(dp(34), dp(34));
        np.topMargin = dp(4);
        np.gravity = Gravity.CENTER_HORIZONTAL;
        num.setLayoutParams(np);
        if (d.equals(selectedDate)) {
            num.setBackgroundResource(R.drawable.sel_day_bg);
            num.setTextColor(getColor(R.color.on_accent));
            num.setTypeface(null, Typeface.BOLD);
        } else {
            num.setTextColor(d.equals(today) ? getColor(R.color.accent) : themeColor(R.attr.colorTextPrimary));
            if (d.equals(today)) num.setTypeface(null, Typeface.BOLD);
            if (d.isBefore(today)) num.setAlpha(0.55f);
        }
        cell.addView(num);

        // Dot: done (mint) / missed (red) for past days, grey for planned days ahead
        View dot = new View(requireContext());
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dotLp.topMargin = dp(5);
        dotLp.gravity = Gravity.CENTER_HORIZONTAL;
        dot.setLayoutParams(dotLp);
        boolean planned = !repo.getExercisesForDate(d).isEmpty() && !repo.isRestWeekday(d);
        int dotColor = 0;
        if (d.isBefore(today)) {
            if (repo.isDateComplete(d) || !repo.completed.getOrDefault(d, Collections.emptyList()).isEmpty())
                dotColor = getColor(R.color.accent);
            else if (planned) dotColor = getColor(R.color.danger);
        } else if (planned) {
            dotColor = themeColor(R.attr.colorTextMuted);
        }
        if (dotColor != 0) {
            android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
            g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            g.setColor(dotColor);
            dot.setBackground(g);
        }
        cell.addView(dot);
        return cell;
    }

    // ── Headline card ────────────────────────────────────────────────────────

    private void updateHero(List<ExerciseData> shown, boolean isPast, boolean isRest) {
        String date = selectedDate.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH))
                .toUpperCase(Locale.ROOT);
        dateLabel.setText(selectedDate.equals(today) ? date : date + "  ·  TAP FOR TODAY");

        heroTitle.setText(dayTitle(shown, isPast, isRest));
        List<ExerciseData> done = repo.completed.getOrDefault(selectedDate, Collections.emptyList());
        int planned = shown.size(), doneCount = 0;
        for (ExerciseData ex : shown) if (done.contains(ex)) doneCount++;

        String sub;
        if (planned == 0) {
            sub = isRest ? "Recovery is part of the plan"
                    : isPast ? "Nothing was logged this day" : "Nothing planned — tap + to add";
        } else if (isPast) {
            sub = doneCount + " exercise" + (doneCount == 1 ? "" : "s") + " logged";
        } else {
            sub = doneCount == planned ? "All done — nice work" : doneCount + " of " + planned + " done";
            if (selectedDate.equals(today)) sub += "  ·  " + streakLine();
        }
        heroSub.setText(sub);
        heroBar.setVisibility(planned == 0 || isPast ? View.GONE : View.VISIBLE);
        heroBar.setMax(Math.max(1, planned));
        heroBar.setProgress(doneCount);
    }

    private String streakLine() {
        int streak = repo.computeStreak();
        if (streak > 0) return "🔥 " + streak + " day streak";
        int potential = repo.computePotentialStreak();
        return potential > 0 ? "finish to make it " + (potential + 1) + " days" : "finish to start a streak";
    }

    /** "Leg day", "Push day"… when one muscle group is most of the plan, else "Full body". */
    /** Public: the home-screen widget shows the same title. */
    public static String dayTitle(List<ExerciseData> shown, boolean isPast, boolean isRest) {
        if (shown.isEmpty()) return isRest ? "Rest day" : isPast ? "No workout" : "Free day";
        Map<String, Integer> count = new HashMap<>();
        for (ExerciseData ex : shown) count.merge(AppRepository.classifyExercise(ex.getName()), 1, Integer::sum);
        String top = null; int best = 0;
        for (Map.Entry<String, Integer> e : count.entrySet())
            if (e.getValue() > best) { best = e.getValue(); top = e.getKey(); }
        if (best * 2 <= shown.size() || top == null) return "Full body";
        switch (top) {
            case "Legs": return "Leg day";
            case "Push": return "Push day";
            case "Pull": return "Pull day";
            case "Arms": return "Arm day";
            case "Core": return "Core day";
            default:     return "Workout";
        }
    }

    // ── ⋮ menu ───────────────────────────────────────────────────────────────

    private void showMenu() {
        boolean isPast = selectedDate.isBefore(today);
        PopupMenu m = new PopupMenu(requireContext(), menuBtn);
        if (isPast) {
            m.getMenu().add(0, 4, 0, "Back to today");
        } else {
            m.getMenu().add(0, 1, 0, "Templates…");
            m.getMenu().add(0, 2, 1, repo.restDays.contains(selectedDate.getDayOfWeek())
                    ? "Remove rest day" : "Make " + selectedDate.getDayOfWeek()
                        .getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "s rest days");
            if (adapter.getItemCount() > 1)
                m.getMenu().add(0, 3, 2, reorderMode ? "Done reordering" : "Reorder exercises");
        }
        m.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showTemplateDialog(); break;
                case 2: {
                    DayOfWeek dow = selectedDate.getDayOfWeek();
                    if (!repo.restDays.remove(dow)) repo.restDays.add(dow);
                    repo.saveAsync();
                    refreshExercises();
                    break;
                }
                case 3: reorderMode = !reorderMode; refreshExercises(); break;
                case 4: select(today); break;
            }
            return true;
        });
        m.show();
    }

    // ── Exercise list ─────────────────────────────────────────────────────────

    private void refreshExercises() {
        boolean isRest = repo.isRestDay(selectedDate);
        boolean isPast = selectedDate.isBefore(today);
        // Past days are an immutable record of what was actually logged that day —
        // render them from the saved completion snapshot, not the recurring schedule.
        List<ExerciseData> exercises = isPast
                ? new ArrayList<>(repo.completed.getOrDefault(selectedDate, Collections.emptyList()))
                : repo.getExercisesForDate(selectedDate);
        if (exercises.size() < 2) reorderMode = false;
        adapter.setExercises(exercises);

        fab.setVisibility(isPast ? View.GONE : View.VISIBLE);
        exerciseRecycler.setVisibility(exercises.isEmpty() ? View.GONE : View.VISIBLE);
        emptyText.setVisibility(exercises.isEmpty() ? View.VISIBLE : View.GONE);
        if (exercises.isEmpty()) {
            emptyText.setText(isPast ? "Swipe the week or tap a day to look back."
                    : isRest ? "Rest day. Change it under ⋮ if you want to train."
                    : "Tap + to add an exercise,\nor load a template from ⋮");
        }
        refreshDateStrip();
        updateHero(exercises, isPast, isRest);
    }

    private int getColor(int resId) {
        return requireContext().getResources().getColor(resId, requireContext().getTheme());
    }

    private int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    /** "45 s", "90 s", "3 min", "2 min 30 s" — shared with the exercise screen. */
    static String restLabel(int s) {
        if (s < 120) return s + " s";
        return s % 60 == 0 ? (s / 60) + " min" : (s / 60) + " min " + (s % 60) + " s";
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    private class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.VH> {
        List<ExerciseData> items = new ArrayList<>();

        void setExercises(List<ExerciseData> list) {
            items = new ArrayList<>(list);
            notifyDataSetChanged();
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_exercise_card, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            ExerciseData ex = items.get(pos);
            h.name.setText(ex.getName());
            String w = ex.isBodyweight() ? "Bodyweight" : Units.fmt(ex.getWeightKg());
            h.stats.setText(w + "  ·  " + ex.getSets() + "×" + ex.getRepsPerSet()
                    + "  ·  " + restLabel(ex.getRestSeconds()) + " rest");

            int stripColor;
            switch (AppRepository.classifyExercise(ex.getName())) {
                case "Push": stripColor = getColor(R.color.pill_push); break;
                case "Pull": stripColor = getColor(R.color.pill_pull); break;
                case "Legs": stripColor = getColor(R.color.pill_legs); break;
                case "Arms": stripColor = getColor(R.color.pill_arms); break;
                case "Core": stripColor = getColor(R.color.pill_core); break;
                default:     stripColor = getColor(R.color.pill_other); break;
            }
            h.strip.setBackgroundColor(stripColor);

            // Completion state — only today can be ticked (past days are a record)
            List<ExerciseData> done = repo.completed.getOrDefault(selectedDate, Collections.emptyList());
            boolean isToday = selectedDate.equals(today);
            h.check.setOnCheckedChangeListener(null);
            h.check.setChecked(done.contains(ex));
            h.check.setEnabled(isToday);
            h.check.setVisibility(isToday || done.contains(ex) ? View.VISIBLE : View.INVISIBLE);
            h.check.setOnCheckedChangeListener((b, checked) -> {
                if (!selectedDate.equals(LocalDate.now())) return; // double-guard
                repo.markComplete(selectedDate, ex, checked); // saves internally
                if (checked) toastNewAchievements();
                refreshDateStrip();
                updateHero(items, false, repo.isRestDay(selectedDate));
            });

            h.handle.setVisibility(reorderMode ? View.VISIBLE : View.GONE);
            if (reorderMode) {
                h.handle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) touchHelper.startDrag(h);
                    return false;
                });
                h.itemView.setOnClickListener(null);
                h.itemView.setOnLongClickListener(null);
                return;
            }
            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(getContext(), ExerciseDetailActivity.class);
                i.putExtra("exercise_id", ex.getId());
                i.putExtra("exercise_name", ex.getName());
                i.putExtra("date", selectedDate.toString());
                startActivity(i);
            });
            if (selectedDate.isBefore(today)) { h.itemView.setOnLongClickListener(null); return; }
            h.itemView.setOnLongClickListener(v -> {
                PopupMenu m = new PopupMenu(requireContext(), h.name);
                m.getMenu().add(0, 1, 0, "Edit");
                m.getMenu().add(0, 2, 1, "Delete");
                m.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) showEditDialog(ex);
                    else new AlertDialog.Builder(requireContext())
                        .setTitle("Delete " + ex.getName() + "?")
                        .setMessage("It will be removed from every " + ex.daysLabel() + ".")
                        .setPositiveButton("Delete", (d, ww) -> {
                            repo.exercises.remove(ex);
                            repo.saveAsync();
                            refreshExercises();
                        })
                        .setNegativeButton("Cancel", null).show();
                    return true;
                });
                m.show();
                return true;
            });
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, stats;
            CheckBox check;
            View strip;
            ImageView handle;
            VH(View v) {
                super(v);
                name   = v.findViewById(R.id.exercise_name);
                stats  = v.findViewById(R.id.exercise_stats);
                check  = v.findViewById(R.id.check_complete);
                strip  = v.findViewById(R.id.muscle_strip);
                handle = v.findViewById(R.id.drag_handle);
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    /** Common lifts offered alongside the user's own names in autocomplete. */
    private static final String[] COMMON_EXERCISES = {
        "Bench Press","Incline Bench Press","Incline DB Press","Overhead Press","Lateral Raise",
        "Cable Fly","Dip","Push-Up","Tricep Pushdown","Overhead Tricep Extension","Skullcrusher",
        "Deadlift","Romanian Deadlift","Pull-Up","Chin-Up","Lat Pulldown","Barbell Row","Cable Row",
        "Dumbbell Row","Face Pull","Shrug","Bicep Curl","Hammer Curl","Preacher Curl",
        "Squat","Front Squat","Leg Press","Bulgarian Split Squat","Lunge","Leg Extension",
        "Leg Curl","Hip Thrust","Calf Raise","Plank","Crunch","Hanging Leg Raise","Russian Twist"
    };

    /**
     * History, PRs and set logs all link by exercise NAME — autocomplete keeps
     * "Bench press" from silently splitting off from "Bench Press".
     */
    private void attachNameSuggestions(EditText nameField) {
        if (!(nameField instanceof AutoCompleteTextView)) return;
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (ExerciseData ex : repo.exercises) names.add(ex.getName());
        for (List<ExerciseData> day : repo.completed.values())
            for (ExerciseData ex : day) names.add(ex.getName());
        for (com.ironpulse.model.RecordData r : repo.records) names.add(r.getName());
        names.addAll(Arrays.asList(COMMON_EXERCISES));
        AutoCompleteTextView actv = (AutoCompleteTextView) nameField;
        actv.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(names)));
        actv.setThreshold(1);
    }

    /** The 7 weekday checkboxes inside dialog_add_exercise, Monday-first. */
    private CheckBox[] dayBoxes(View dlg) {
        return new CheckBox[]{
            dlg.findViewById(R.id.day_mon), dlg.findViewById(R.id.day_tue),
            dlg.findViewById(R.id.day_wed), dlg.findViewById(R.id.day_thu),
            dlg.findViewById(R.id.day_fri), dlg.findViewById(R.id.day_sat),
            dlg.findViewById(R.id.day_sun)};
    }

    /** Selected days, falling back to {@code fallback} when none are ticked. */
    private Set<DayOfWeek> selectedDays(CheckBox[] boxes, DayOfWeek fallback) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (int i = 0; i < 7; i++) if (boxes[i].isChecked()) days.add(DayOfWeek.of(i + 1));
        if (days.isEmpty()) days.add(fallback);
        return days;
    }

    private void showAddDialog() {
        View dlg = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_exercise, null);
        EditText nameF = dlg.findViewById(R.id.field_name),
                 wF    = dlg.findViewById(R.id.field_weight),
                 sF    = dlg.findViewById(R.id.field_sets),
                 rF    = dlg.findViewById(R.id.field_reps),
                 restF = dlg.findViewById(R.id.field_rest);
        wF.setHint("Weight (" + Units.unit() + " or BW)");
        attachNameSuggestions(nameF);
        CheckBox[] boxes = dayBoxes(dlg);
        boxes[selectedDate.getDayOfWeek().getValue() - 1].setChecked(true);
        // Fields start empty (hints show the defaults); pi() falls back to
        // 3 sets / 10 reps / 90s rest if the user leaves them blank.

        new AlertDialog.Builder(requireContext()).setTitle("Add Exercise").setView(dlg)
            .setPositiveButton("Add", (d, w) -> {
                String nm = nameF.getText().toString().trim();
                if (nm.isEmpty()) return;
                Runnable add = () -> {
                    ExerciseData ex = new ExerciseData(nm,
                            Units.inputToKgString(wF.getText().toString()),
                            pi(sF.getText().toString(), 3) + "x" + pi(rF.getText().toString(), 10),
                            pi(restF.getText().toString(), 90), selectedDate);
                    ex.setDays(selectedDays(boxes, selectedDate.getDayOfWeek()));
                    repo.exercises.add(ex);
                    repo.saveAsync();
                    refreshDateStrip();
                    refreshExercises();
                };
                boolean dup = repo.getExercisesForDate(selectedDate).stream()
                        .anyMatch(e -> e.getName().equalsIgnoreCase(nm));
                if (dup) new AlertDialog.Builder(requireContext())
                    .setMessage(nm + " already on this day. Add again?")
                    .setPositiveButton("Yes", (d2, w2) -> add.run())
                    .setNegativeButton("No", null).show();
                else add.run();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showEditDialog(ExerciseData ex) {
        View dlg = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_exercise, null);
        EditText nameF = dlg.findViewById(R.id.field_name),
                 wF    = dlg.findViewById(R.id.field_weight),
                 sF    = dlg.findViewById(R.id.field_sets),
                 rF    = dlg.findViewById(R.id.field_reps),
                 restF = dlg.findViewById(R.id.field_rest);
        wF.setHint("Weight (" + Units.unit() + " or BW)");
        attachNameSuggestions(nameF);
        nameF.setText(ex.getName()); // renaming carries set-log history along
        wF.setText(ex.isBodyweight() ? "" : Units.num(ex.getWeightKg()));
        sF.setText(String.valueOf(ex.getSets()));
        rF.setText(String.valueOf(ex.getRepsPerSet()));
        restF.setText(String.valueOf(ex.getRestSeconds()));
        CheckBox[] boxes = dayBoxes(dlg);
        for (DayOfWeek dw : ex.getDays()) boxes[dw.getValue() - 1].setChecked(true);

        new AlertDialog.Builder(requireContext()).setTitle("Edit: " + ex.getName()).setView(dlg)
            .setPositiveButton("Save", (d, w) -> {
                ex.setWeight(Units.inputToKgString(wF.getText().toString()));
                ex.setReps(pi(sF.getText().toString(), 3) + "x" + pi(rF.getText().toString(), 10));
                ex.setRestSeconds(pi(restF.getText().toString(), 90));
                ex.setDays(selectedDays(boxes, ex.getDayOfWeek()));
                repo.renameExercise(ex, nameF.getText().toString()); // saves internally
                repo.saveAsync(); refreshDateStrip(); refreshExercises();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showTemplateDialog() {
        // Built-in templates
        String[][] BUILTIN = {
            {"PPL – Push","Barbell Bench Press|60|4x6|180","Overhead Press|40|3x8|150","Incline DB Press|22|3x10|90","Lateral Raise|8|4x15|45","Tricep Pushdown|15|3x12|60"},
            {"PPL – Pull","Deadlift|80|3x5|240","Pull-Up|0|4x6|150","Cable Row|50|4x8|90","Lat Pulldown|55|3x10|90","Bicep Curl|14|3x12|60"},
            {"PPL – Legs","Squat|80|4x6|180","Romanian Deadlift|70|3x8|120","Leg Press|120|3x12|90","Leg Curl|40|3x12|60","Calf Raise|60|4x15|45"},
            {"Upper/Lower – Upper","Bench Press|60|4x6|180","Barbell Row|60|4x6|180","Overhead Press|40|3x8|120","Lat Pulldown|55|3x10|90","Bicep Curl|12|3x12|60"},
            {"Upper/Lower – Lower","Squat|80|4x6|180","Romanian Deadlift|70|3x8|120","Leg Press|120|3x10|90","Leg Curl|40|3x12|60","Calf Raise|50|4x15|45"},
            {"Full Body","Squat|70|3x5|180","Bench Press|55|3x5|180","Row|55|3x5|180","Overhead Press|35|3x8|120","RDL|60|3x8|120"},
            {"Bro Split – Chest & Tris","Barbell Bench Press|65|4x8|120","Incline DB Press|24|3x10|90","Cable Fly|14|3x12|60","Tricep Pushdown|18|3x12|60","Overhead Tricep Extension|12|3x12|60"},
            {"Bro Split – Back & Bis","Deadlift|80|3x5|240","Pull-Up|0|4x6|150","Barbell Row|60|4x8|120","Bicep Curl|14|3x12|60","Hammer Curl|12|3x12|60"},
            {"Bro Split – Legs & Shoulders","Squat|80|4x6|180","Leg Press|120|3x12|90","Leg Curl|40|3x12|60","Overhead Press|42|4x8|150","Lateral Raise|8|4x15|45"},
        };

        // Build combined list: built-ins + custom + save option
        List<String> names = new ArrayList<>();
        for (String[] t : BUILTIN) names.add(t[0]);
        for (List<String> c : repo.customSplits) names.add("★ " + c.get(0));
        if (!repo.customSplits.isEmpty()) names.add("✕ Delete a custom template…");
        names.add("+ Save today's exercises as template…");

        new AlertDialog.Builder(requireContext()).setTitle("Choose Template")
            .setItems(names.toArray(new String[0]), (d, which) -> {
                int builtinCount = BUILTIN.length;
                int customCount  = repo.customSplits.size();
                int saveIdx = builtinCount + customCount + (customCount > 0 ? 1 : 0);
                int deleteIdx = customCount > 0 ? builtinCount + customCount : -1;

                if (which == saveIdx) {
                    saveCurrentAsTemplate();
                    return;
                }
                if (which == deleteIdx) {
                    String[] customNames = repo.customSplits.stream()
                            .map(c -> c.get(0)).toArray(String[]::new);
                    new AlertDialog.Builder(requireContext()).setTitle("Delete Custom Template")
                        .setItems(customNames, (d2, i) -> {
                            repo.customSplits.remove(i);
                            repo.saveAsync();
                            Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show();
                        }).show();
                    return;
                }

                // Load template (built-in or custom)
                Runnable load;
                if (which < builtinCount) {
                    String[] t = BUILTIN[which];
                    load = () -> {
                        for (int i = 1; i < t.length; i++) {
                            String[] p = t[i].split("\\|");
                            repo.exercises.add(new ExerciseData(p[0], p[1], p[2], Integer.parseInt(p[3]), selectedDate));
                        }
                        repo.saveAsync(); refreshDateStrip(); refreshExercises();
                    };
                } else {
                    List<String> custom = repo.customSplits.get(which - builtinCount);
                    load = () -> {
                        for (int i = 1; i < custom.size(); i++) {
                            String[] p = custom.get(i).split("\\|");
                            if (p.length < 4) continue;
                            repo.exercises.add(new ExerciseData(p[0], p[1], p[2], pi(p[3], 90), selectedDate));
                        }
                        repo.saveAsync(); refreshDateStrip(); refreshExercises();
                    };
                }

                List<ExerciseData> existing = repo.getExercisesForDate(selectedDate);
                if (!existing.isEmpty()) {
                    new AlertDialog.Builder(requireContext())
                        .setMessage(selectedDate.getDayOfWeek()
                                .getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                                + " already has " + existing.size() + " exercise(s). Add on top?")
                        .setPositiveButton("Yes", (d2, w) -> load.run())
                        .setNegativeButton("No", null).show();
                } else {
                    load.run();
                }
            })
            .show();
    }

    private void saveCurrentAsTemplate() {
        List<ExerciseData> exercises = repo.getExercisesForDate(selectedDate);
        if (exercises.isEmpty()) {
            Toast.makeText(requireContext(), "No exercises on this day to save", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText nameField = new EditText(requireContext());
        nameField.setHint("Template name  e.g. My Push Day");
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 8);
        l.addView(nameField);
        // Preview of exercises being saved
        for (ExerciseData ex : exercises) {
            TextView preview = new TextView(requireContext());
            preview.setText("• " + ex.getName() + "  " + ex.getReps()
                    + (ex.isBodyweight() ? "  BW" : "  " + Units.fmt(ex.getWeightKg())));
            preview.setTextColor(0xFF72889E); preview.setTextSize(12);
            preview.setPadding(0, 2, 0, 2);
            l.addView(preview);
        }
        new AlertDialog.Builder(requireContext())
            .setTitle("Save as Template")
            .setView(l)
            .setPositiveButton("Save", (d, w) -> {
                String name = nameField.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(requireContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                List<String> template = new ArrayList<>();
                template.add(name);
                for (ExerciseData ex : exercises) {
                    template.add(ex.getName() + "|" + ex.getWeight() + "|" + ex.getReps() + "|" + ex.getRestSeconds());
                }
                repo.customSplits.add(template);
                repo.saveAsync();
                Toast.makeText(requireContext(), "Saved \"" + name + "\"", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null).show();
    }

    private int pi(String s, int fb) {
        try { return Math.max(1, Integer.parseInt(s.trim())); } catch (Exception e) { return fb; }
    }

    private void toastNewAchievements() {
        for (com.ironpulse.data.Achievements.Def d :
                com.ironpulse.data.Achievements.checkAndUnlock(repo))
            Toast.makeText(requireContext(),
                    "🏆 Achievement unlocked: " + d.emoji + " " + d.title, Toast.LENGTH_LONG).show();
    }
}
