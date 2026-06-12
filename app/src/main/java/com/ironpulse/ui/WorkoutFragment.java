package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.*;
import com.google.android.material.chip.Chip;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import com.ironpulse.model.ExerciseData;

import java.time.*;
import java.time.format.*;
import java.util.*;

public class WorkoutFragment extends Fragment {

    private AppRepository repo;
    private LocalDate selectedDate = LocalDate.now();
    private RecyclerView dateStripRv;
    private DateStripAdapter dateStripAdapter;
    private androidx.recyclerview.widget.LinearSnapHelper snapHelper;
    private RecyclerView exerciseRecycler;
    private TextView streakText;
    private ProgressBar streakBar;
    private boolean editMode = false;
    private Button editBtn;
    private ExerciseAdapter adapter;
    private LinearLayout emptyState;
    private Button restDayBtn;
    private TextView emptyText;
    private ItemTouchHelper touchHelper;
    private LocalDate stripBaseDate = LocalDate.now();

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        repo = AppRepository.get(requireContext());
        View v = inf.inflate(R.layout.fragment_workout, parent, false);

        dateStripRv    = v.findViewById(R.id.date_strip_rv);
        exerciseRecycler = v.findViewById(R.id.exercise_recycler);
        streakText     = v.findViewById(R.id.streak_text);
        streakBar      = v.findViewById(R.id.streak_bar);
        editBtn        = v.findViewById(R.id.btn_edit);
        emptyState     = v.findViewById(R.id.empty_state);
        emptyText      = v.findViewById(R.id.empty_text);
        restDayBtn     = v.findViewById(R.id.btn_rest_day);

        // Set up RecyclerView
        adapter = new ExerciseAdapter();
        exerciseRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        exerciseRecycler.setAdapter(adapter);

        // Swipe to delete + drag to reorder
        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                    @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder t) {
                int from = vh.getAdapterPosition();
                int to   = t.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
                List<ExerciseData> exercises = repo.getExercisesForDate(selectedDate);
                if (from < exercises.size() && to < exercises.size()) {
                    ExerciseData moved = exercises.get(from);
                    int fi = repo.exercises.indexOf(moved);
                    ExerciseData target = exercises.get(to);
                    int ti = repo.exercises.indexOf(target);
                    if (fi >= 0 && ti >= 0) {
                        repo.exercises.remove(fi);
                        repo.exercises.add(ti, moved);
                        adapter.notifyItemMoved(from, to);
                        repo.saveAsync();
                    }
                }
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {
                int pos = vh.getAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                List<ExerciseData> exercises = repo.getExercisesForDate(selectedDate);
                if (pos >= exercises.size()) { adapter.notifyItemChanged(pos); return; }
                ExerciseData ex = exercises.get(pos);
                new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Exercise")
                    .setMessage("Remove \"" + ex.getName() + "\" from every "
                            + ex.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "?")
                    .setPositiveButton("Delete", (d, w) -> {
                        repo.exercises.remove(ex);
                        if (repo.getExercisesForDate(selectedDate).isEmpty()) {
                            editMode = false; editBtn.setText("Edit");
                            editBtn.setBackgroundResource(R.drawable.btn_primary);
                        }
                        repo.saveAsync(); refreshDateStrip(); refreshExercises();
                    })
                    .setNegativeButton("Cancel", (d, w) -> adapter.notifyItemChanged(pos))
                    // Dismiss via back/outside-tap must also restore the swiped card
                    .setOnCancelListener(d -> adapter.notifyItemChanged(pos))
                    .show();
            }
            // Only allow swipe in edit mode
            @Override public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                return editMode ? super.getSwipeDirs(rv, vh) : 0;
            }
        });
        touchHelper.attachToRecyclerView(exerciseRecycler);

        v.findViewById(R.id.btn_template).setOnClickListener(x -> showTemplateDialog());
        ((FloatingActionButton) v.findViewById(R.id.fab_add)).setOnClickListener(x -> showAddDialog());

        restDayBtn.setOnClickListener(x -> {
            java.time.DayOfWeek dow = selectedDate.getDayOfWeek();
            if (repo.restDays.contains(dow)) {
                repo.restDays.remove(dow);
            } else {
                repo.restDays.add(dow);
            }
            repo.saveAsync();
            refreshDateStrip();
            refreshExercises();
            updateStreak();
        });

        editBtn.setOnClickListener(x -> {
            editMode = !editMode;
            editBtn.setText(editMode ? "Done" : "Edit");
            editBtn.setBackgroundResource(editMode ? R.drawable.btn_danger : R.drawable.btn_primary);
            adapter.notifyDataSetChanged();
        });

        buildDateStrip(); // centres on today once the strip is laid out
        refreshExercises();
        updateStreak();
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        // If the app stayed open past midnight, rebuild the strip around the new today
        if (!stripBaseDate.equals(LocalDate.now())) {
            stripBaseDate = LocalDate.now();
            selectedDate = LocalDate.now();
            buildDateStrip(); // re-centres via its layout listener
        }
        refreshDateStrip();
        refreshExercises();
        updateStreak();
    }

    // ── Date strip ───────────────────────────────────────────────────────────

    private void buildDateStrip() {
        LocalDate today = LocalDate.now();
        stripBaseDate = today;

        androidx.recyclerview.widget.LinearLayoutManager lm =
                new androidx.recyclerview.widget.LinearLayoutManager(
                        getContext(), androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false);
        dateStripRv.setLayoutManager(lm);

        // Padding so first/last items can be centered. Must wait for a real layout
        // (width > 0), then apply padding + scroll anchor together so they take
        // effect in the same layout pass — posting them separately lets scroll
        // listeners run against a half-initialised strip and select the wrong day.
        dateStripRv.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (dateStripRv.getWidth() == 0) return;
                dateStripRv.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int half = dateStripRv.getWidth() / 2 - dpToPx(28); // 28 = half of 56dp cell
                dateStripRv.setPadding(half, 0, half, 0);
                dateStripRv.stopScroll();
                selectedDate = stripBaseDate; // undo any selection made mid-initialisation
                lm.scrollToPositionWithOffset(7, 0); // today is index 7 (strip spans -7..+21)
                dateStripRv.post(() -> applyCompassScale(dateStripRv));
                refreshExercises();
            }
        });

        dateStripAdapter = new DateStripAdapter(today, -7, 21);
        dateStripRv.setAdapter(dateStripAdapter);

        // buildDateStrip can run again (midnight rebuild) — never stack listeners.
        // Must run BEFORE attaching the snap helper: it registers its own scroll
        // listener, and clearing afterwards kills snap-on-release (strip would rest
        // between two cells instead of settling on the nearest one).
        dateStripRv.clearOnScrollListeners();

        // Detach any previous helper first — attaching a second one throws
        if (snapHelper != null) snapHelper.attachToRecyclerView(null);
        snapHelper = new androidx.recyclerview.widget.LinearSnapHelper();
        snapHelper.attachToRecyclerView(dateStripRv);

        dateStripRv.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                applyCompassScale(rv);
            }
            @Override public void onScrollStateChanged(@NonNull RecyclerView rv, int state) {
                if (state == RecyclerView.SCROLL_STATE_IDLE) {
                    if (rv.getPaddingLeft() <= 0) return; // strip not centred yet
                    View snap = snapHelper.findSnapView(lm);
                    if (snap != null) {
                        int pos = rv.getChildAdapterPosition(snap);
                        if (pos != RecyclerView.NO_POSITION) {
                            LocalDate snapped = today.plusDays(pos - 7);
                            if (!snapped.equals(selectedDate)) {
                                selectedDate = snapped;
                                if (selectedDate.isBefore(LocalDate.now()) && editMode) {
                                    editMode = false;
                                    editBtn.setText("Edit");
                                    editBtn.setBackgroundResource(R.drawable.btn_primary);
                                }
                                dateStripAdapter.notifyDataSetChanged();
                                refreshExercises();
                            }
                        }
                    }
                }
            }
        });
    }

    private void applyCompassScale(RecyclerView rv) {
        float centerX = rv.getWidth() / 2f;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            View box = child.findViewById(R.id.cell_box);
            if (box == null) continue;
            float itemCenter = child.getLeft() + child.getWidth() / 2f;
            float distance = Math.abs(centerX - itemCenter);
            // Distance measured in cell-widths so the falloff is sharp:
            // only the centered cell grows, neighbours sit back at 1.0.
            // Cells sit close together (3dp margins → 6dp gaps) so the grown centre
            // is capped at 1.18x, leaving a ~1dp gap to neighbours instead of overlapping.
            float ratio = Math.min(distance / child.getWidth(), 1f);
            float scale = 1.18f - (0.18f * ratio);
            box.setScaleX(scale);
            box.setScaleY(scale);
        }
    }

    private void refreshDateStrip() {
        if (dateStripAdapter != null) {
            dateStripAdapter.notifyDataSetChanged();
            dateStripRv.post(() -> applyCompassScale(dateStripRv));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Date strip adapter ────────────────────────────────────────────────────

    private class DateStripAdapter extends RecyclerView.Adapter<DateStripAdapter.DVH> {
        private final LocalDate origin;
        private final int start, end;

        DateStripAdapter(LocalDate today, int start, int end) {
            this.origin = today;
            this.start = start;
            this.end = end;
        }

        @NonNull @Override
        public DVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View cell = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_cell, parent, false);
            return new DVH(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull DVH h, int position) {
            LocalDate date = origin.plusDays(start + position);
            LocalDate today = LocalDate.now();
            boolean hasWork = !repo.getExercisesForDate(date).isEmpty();
            boolean isRest  = repo.isRestDay(date);
            boolean isPast  = date.isBefore(today);

            h.dayLbl.setText(date.format(DateTimeFormatter.ofPattern("EEE")).toUpperCase());
            h.numLbl.setText(date.format(DateTimeFormatter.ofPattern("d")));

            android.util.TypedValue tv = new android.util.TypedValue();
            android.content.res.Resources.Theme theme = requireContext().getTheme();

            // Today is the ONLY blue cell. The selected day is shown purely by the
            // compass-enlarge, so it gets no distinct background colour.
            if (date.equals(today)) {
                theme.resolveAttribute(R.attr.calToday, tv, true);
            } else if (isRest) {
                theme.resolveAttribute(R.attr.calRest, tv, true);
            } else if (hasWork) {
                theme.resolveAttribute(R.attr.calWorkout, tv, true);
            } else {
                theme.resolveAttribute(R.attr.calEmpty, tv, true);
            }
            h.box.setBackgroundColor(tv.data);

            if (isPast && hasWork) {
                h.dot.setBackgroundColor(repo.isDateComplete(date)
                        ? getColor(R.color.accent) : getColor(R.color.danger));
                h.dot.setVisibility(View.VISIBLE);
            } else if (isRest || hasWork) {
                h.dot.setBackgroundColor(isRest ? getColor(R.color.accent) : getColor(R.color.accent_2));
                h.dot.setVisibility(View.VISIBLE);
            } else {
                h.dot.setVisibility(View.INVISIBLE);
            }

            float alpha = isPast ? 0.55f : 1.0f;
            h.dayLbl.setAlpha(alpha);
            h.numLbl.setAlpha(alpha);

            h.itemView.setOnClickListener(x -> {
                if (date.isBefore(LocalDate.now()) && editMode) {
                    editMode = false;
                    editBtn.setText("Edit");
                    editBtn.setBackgroundResource(R.drawable.btn_primary);
                }
                // Smooth scroll so the tapped cell glides to the centre;
                // selection is finalised when the scroll settles (IDLE handler)
                int offset = dateStripRv.getWidth() / 2 - dpToPx(28);
                int dx = h.itemView.getLeft() - offset;
                if (dx != 0) {
                    dateStripRv.smoothScrollBy(dx, 0);
                } else {
                    selectedDate = date;
                    notifyDataSetChanged();
                    refreshExercises();
                }
            });
        }

        @Override public int getItemCount() { return end - start + 1; }

        class DVH extends RecyclerView.ViewHolder {
            TextView dayLbl, numLbl;
            View dot, box;
            DVH(View v) {
                super(v);
                dayLbl = v.findViewById(R.id.day_label);
                numLbl = v.findViewById(R.id.num_label);
                dot    = v.findViewById(R.id.status_dot);
                box    = v.findViewById(R.id.cell_box);
            }
        }
    }

    // ── Streak ───────────────────────────────────────────────────────────────

    private void updateStreak() {
        LocalDate today = LocalDate.now();
        int streak = repo.computeStreak();
        int potentialStreak = streak > 0 ? 0 : repo.computePotentialStreak();

        if (streak > 0) {
            streakText.setText("🔥 " + streak + " day streak");
        } else if (repo.isRestDay(today)) {
            streakText.setText("🛌 Rest day — streak protected");
        } else {
            if (potentialStreak > 0) {
                streakText.setText("🔥 " + potentialStreak + " day streak — complete today to extend!");
            } else {
                List<ExerciseData> planned = repo.getExercisesForDate(today);
                if (planned.isEmpty()) {
                    streakText.setText("No exercises today");
                } else {
                    streakText.setText("Complete today to start a streak");
                }
            }
        }

        streakBar.setMax(30);
        int displayStreak = streak > 0 ? streak : potentialStreak;
        streakBar.setProgress(Math.min(30, displayStreak));
        int barColor;
        if (displayStreak >= 90)      barColor = getColor(R.color.danger);
        else if (displayStreak >= 30) barColor = getColor(R.color.accent_2);
        else                          barColor = getColor(R.color.accent);
        streakBar.setProgressTintList(android.content.res.ColorStateList.valueOf(barColor));
    }

    // ── Exercise list ─────────────────────────────────────────────────────────

    private void refreshExercises() {
        boolean isRest   = repo.isRestDay(selectedDate);
        boolean isPast   = selectedDate.isBefore(LocalDate.now());
        // Past days are an immutable record of what was actually logged that day —
        // render them from the saved completion snapshot, not the recurring schedule.
        List<ExerciseData> exercises = isPast
                ? new ArrayList<>(repo.completed.getOrDefault(selectedDate, Collections.emptyList()))
                : repo.getExercisesForDate(selectedDate);
        adapter.setExercises(exercises);

        FloatingActionButton fab = getView() == null ? null :
                getView().findViewById(R.id.fab_add);
        if (fab != null) fab.setVisibility(isPast ? View.GONE : View.VISIBLE);
        if (editBtn != null) editBtn.setVisibility(isPast ? View.GONE : View.VISIBLE);
        if (restDayBtn != null) restDayBtn.setVisibility(isPast ? View.GONE : View.VISIBLE);
        View templateBtn = getView() == null ? null : getView().findViewById(R.id.btn_template);
        if (templateBtn != null) templateBtn.setVisibility(isPast ? View.GONE : View.VISIBLE);

        if (exercises.isEmpty()) {
            exerciseRecycler.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            if (isPast) {
                emptyText.setText("No exercises were completed this day");
            } else if (isRest) {
                emptyText.setText("Rest day — recovery is part of the plan!");
                if (restDayBtn != null) restDayBtn.setText("Remove Rest Day");
            } else {
                emptyText.setText("No exercises yet — tap + to add or use a Template");
                if (restDayBtn != null) restDayBtn.setText("Mark as Rest Day");
            }
        } else {
            exerciseRecycler.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
        }
        refreshDateStrip();
        updateStreak();
    }

    private int getColor(int resId) {
        return requireContext().getResources().getColor(resId, requireContext().getTheme());
    }

    // ── RecyclerView Adapter ──────────────────────────────────────────────────

    private class ExerciseAdapter extends RecyclerView.Adapter<ExerciseAdapter.VH> {
        private List<ExerciseData> items = new ArrayList<>();

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
            String w = ex.isBodyweight() ? "BW"
                    : (ex.getWeightKg() == Math.floor(ex.getWeightKg())
                       ? (int)ex.getWeightKg() + " kg" : ex.getWeightKg() + " kg");
            h.stats.setText(w + "  |  " + ex.getReps() + "  |  " + ex.getRestSeconds() + "s rest");

            // Muscle group pill
            String group = repo.classifyExercise(ex.getName());
            h.pill.setText(group);
            int pillColor;
            switch (group) {
                case "Push": pillColor = getColor(R.color.pill_push); break;
                case "Pull": pillColor = getColor(R.color.pill_pull); break;
                case "Legs": pillColor = getColor(R.color.pill_legs); break;
                case "Arms": pillColor = getColor(R.color.pill_arms); break;
                case "Core": pillColor = getColor(R.color.pill_core); break;
                default:     pillColor = getColor(R.color.pill_other); break;
            }
            h.pill.setChipBackgroundColor(
                    android.content.res.ColorStateList.valueOf(pillColor));

            // Completion state
            List<ExerciseData> done = repo.completed.getOrDefault(
                    selectedDate, Collections.emptyList());
            h.check.setOnCheckedChangeListener(null);
            h.check.setChecked(done.contains(ex));
            boolean isToday = selectedDate.equals(LocalDate.now());
            h.check.setEnabled(isToday); // past days are read-only for streak integrity
            h.check.setAlpha(isToday ? 1.0f : 0.6f);
            h.check.setOnCheckedChangeListener((b, checked) -> {
                if (!selectedDate.equals(LocalDate.now())) return; // double-guard
                repo.markComplete(selectedDate, ex, checked); // saves internally
                refreshDateStrip();
                updateStreak();
            });

            // Edit mode
            if (editMode) {
                h.editButtons.setVisibility(View.VISIBLE);
                h.delBtn.setBackgroundResource(R.drawable.btn_danger);
                h.delBtn.setBackgroundTintList(null);
                h.editBtn.setOnClickListener(v -> showEditDialog(ex));
                // Wire drag handle
                if (h.dragHandle != null) {
                    h.dragHandle.setOnTouchListener((v, event) -> {
                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                            touchHelper.startDrag(h);
                        }
                        return false;
                    });
                }
                h.delBtn.setOnClickListener(v ->
                    new AlertDialog.Builder(requireContext())
                        .setTitle("Delete")
                        .setMessage("Remove \"" + ex.getName() + "\" from every "
                                + ex.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + "?")
                        .setPositiveButton("Delete", (d, ww) -> {
                            repo.exercises.remove(ex);
                            repo.saveAsync();
                            if (repo.getExercisesForDate(selectedDate).isEmpty()) {
                                editMode = false; editBtn.setText("Edit");
                                editBtn.setBackgroundResource(R.drawable.btn_primary);
                            }
                            refreshExercises();
                        })
                        .setNegativeButton("Cancel", null).show());
                h.itemView.setOnClickListener(null);
            } else {
                h.editButtons.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> {
                    Intent i = new Intent(getContext(), ExerciseDetailActivity.class);
                    i.putExtra("exercise_name", ex.getName());
                    i.putExtra("date", selectedDate.toString());
                    startActivity(i);
                });
            }
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView name, stats;
            CheckBox check;
            Chip pill;
            LinearLayout editButtons;
            Button editBtn, delBtn;
            android.widget.TextView dragHandle;
            VH(View v) {
                super(v);
                name       = v.findViewById(R.id.exercise_name);
                stats      = v.findViewById(R.id.exercise_stats);
                check      = v.findViewById(R.id.check_complete);
                pill       = v.findViewById(R.id.muscle_pill);
                editButtons = v.findViewById(R.id.edit_buttons);
                editBtn    = v.findViewById(R.id.btn_edit_exercise);
                delBtn     = v.findViewById(R.id.btn_delete_exercise);
                dragHandle = v.findViewById(R.id.drag_handle);
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────────────────

    private void showAddDialog() {
        View dlg = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_exercise, null);
        EditText nameF = dlg.findViewById(R.id.field_name),
                 wF    = dlg.findViewById(R.id.field_weight),
                 sF    = dlg.findViewById(R.id.field_sets),
                 rF    = dlg.findViewById(R.id.field_reps),
                 restF = dlg.findViewById(R.id.field_rest);
        // Fields start empty (hints show the defaults); pi() falls back to
        // 3 sets / 10 reps / 90s rest if the user leaves them blank.

        new AlertDialog.Builder(requireContext()).setTitle("Add Exercise").setView(dlg)
            .setPositiveButton("Add", (d, w) -> {
                String nm = nameF.getText().toString().trim();
                if (nm.isEmpty()) return;
                Runnable add = () -> {
                    repo.exercises.add(new ExerciseData(nm,
                            wF.getText().toString().trim(),
                            pi(sF.getText().toString(), 3) + "x" + pi(rF.getText().toString(), 10),
                            pi(restF.getText().toString(), 90), selectedDate));
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
        String[] rp = ex.getReps() == null ? new String[]{"3","10"} : ex.getReps().split("x");
        View dlg = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_add_exercise, null);
        EditText nameF = dlg.findViewById(R.id.field_name),
                 wF    = dlg.findViewById(R.id.field_weight),
                 sF    = dlg.findViewById(R.id.field_sets),
                 rF    = dlg.findViewById(R.id.field_reps),
                 restF = dlg.findViewById(R.id.field_rest);
        nameF.setText(ex.getName()); nameF.setEnabled(false);
        wF.setText(ex.getWeight());
        sF.setText(rp.length > 0 ? rp[0] : "3");
        rF.setText(rp.length > 1 ? rp[1] : "10");
        restF.setText(String.valueOf(ex.getRestSeconds()));

        new AlertDialog.Builder(requireContext()).setTitle("Edit: " + ex.getName()).setView(dlg)
            .setPositiveButton("Save", (d, w) -> {
                ex.setWeight(wF.getText().toString().trim());
                ex.setReps(pi(sF.getText().toString(), 3) + "x" + pi(rF.getText().toString(), 10));
                ex.setRestSeconds(pi(restF.getText().toString(), 90));
                repo.saveAsync(); refreshExercises();
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
                    + (ex.isBodyweight() ? "  BW" : "  " + ex.getWeightKg() + "kg"));
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
}
