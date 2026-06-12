package com.ironpulse.ui;
import android.app.AlertDialog;
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
import com.ironpulse.model.*;
import java.time.LocalDate;
import java.util.*;

public class MoreFragment extends Fragment {
    private AppRepository repo;
    private static int tab = 0; // static: survives recreate (e.g. theme toggle)
    private static final String[] MACRO_LABELS = {"Calories","Protein (g)","Carbs (g)","Fats (g)"};
    private TextView[] tabs;
    private boolean prEditMode = false;
    /** Macros tab edit mode: unlocks saved-food editing and manual Daily Targets. */
    private boolean foodEditMode = false;
    private Button prEditBtn;
    private LinearLayout prListContainer;
    // PR RecyclerView drag support
    private RecyclerView prRecycler;
    private PRAdapter prAdapter;
    private ItemTouchHelper prTouchHelper;
    private LinearLayout contentRef;
    private FloatingActionButton prFab;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        View root = inf.inflate(R.layout.fragment_more, p, false);
        LinearLayout content = root.findViewById(R.id.more_content);
        contentRef = content;
        prFab = root.findViewById(R.id.fab_add_pr);
        prFab.setOnClickListener(x -> showAddPR(contentRef));
        tabs = new TextView[]{
            root.findViewById(R.id.tab_prs),
            root.findViewById(R.id.tab_macros),
            root.findViewById(R.id.tab_assessment),
            root.findViewById(R.id.tab_settings)
        };
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(x -> { tab = idx; prEditMode = false; foodEditMode = false; updateTabUI(); buildContent(content); });
        }
        updateTabUI();
        buildContent(content);
        return root;
    }

    /** The floating + only makes sense on the PRs tab. */
    private void updateFab() {
        if (prFab != null) prFab.setVisibility(tab == 0 ? View.VISIBLE : View.GONE);
    }

    private void updateTabUI() {
        if (tabs == null) return;
        int accent = color(R.color.accent), muted = themeColor(R.attr.colorTextMuted);
        for (int i = 0; i < tabs.length; i++) {
            if (i == tab) {
                tabs[i].setTextColor(accent);
                tabs[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13);
                tabs[i].setTypeface(null, android.graphics.Typeface.BOLD);
                tabs[i].setBackgroundResource(R.drawable.tab_selected_bg);
            } else {
                tabs[i].setTextColor(muted);
                tabs[i].setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                tabs[i].setTypeface(null, android.graphics.Typeface.NORMAL);
                tabs[i].setBackground(null);
            }
        }
    }

    private void buildContent(LinearLayout c) {
        c.removeAllViews();
        switch (tab) {
            case 0: buildPRs(c); break;
            case 1: buildMacros(c); break;
            case 2: buildAssessment(c); break;
            case 3: buildSettings(c); break;
        }
        updateFab();
    }

    // ── PRs ──────────────────────────────────────────────────────────────────

    private void buildPRs(LinearLayout c) {
        // Top bar
        LinearLayout topBar = new LinearLayout(requireContext());
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tbLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tbLp.setMargins(0, 0, 0, 12); topBar.setLayoutParams(tbLp);
        TextView title = new TextView(requireContext());
        title.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        title.setText("1RM / Personal Records"); title.setTextColor(themeColor(R.attr.colorTextMuted)); title.setTextSize(11);
        topBar.addView(title);
        // Adding a PR is done via the floating green + (matches the Workout screen).
        prEditBtn = new Button(requireContext());
        prEditBtn.setText(prEditMode ? "Done" : "Edit");
        ButtonStyles.toggle(prEditBtn, prEditMode);
        prEditBtn.setOnClickListener(x -> {
            prEditMode = !prEditMode;
            prEditBtn.setText(prEditMode ? "Done" : "Edit");
            ButtonStyles.toggle(prEditBtn, prEditMode);
            if (prAdapter != null) prAdapter.notifyDataSetChanged();
        });
        topBar.addView(prEditBtn); c.addView(topBar);

        // Muscle group legend
        LinearLayout legend = new LinearLayout(requireContext());
        legend.setOrientation(LinearLayout.HORIZONTAL); legend.setPadding(0, 0, 0, 12);
        String[][] groups = {{"Push","#D25A1E"},{"Pull","#1E64C8"},{"Legs","#1EA050"},{"Arms","#963CC0"},{"Core","#C8A014"}};
        for (String[] g : groups) {
            TextView pill = new TextView(requireContext());
            pill.setText(g[0]); pill.setTextSize(9); pill.setTextColor(0xFFFFFFFF);
            pill.setBackgroundColor(android.graphics.Color.parseColor(g[1]));
            pill.setPadding(12, 4, 12, 4);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 6, 0); pill.setLayoutParams(lp); legend.addView(pill);
        }
        c.addView(legend);

        // RecyclerView for drag support
        prRecycler = new RecyclerView(requireContext());
        prRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        prRecycler.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        prRecycler.setNestedScrollingEnabled(false);

        prAdapter = new PRAdapter();
        prRecycler.setAdapter(prAdapter);

        prTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                    @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition(), to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                        || from >= repo.records.size() || to >= repo.records.size()) return false;
                Collections.swap(repo.records, from, to);
                prAdapter.notifyItemMoved(from, to);
                repo.saveAsync();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
            @Override public boolean isLongPressDragEnabled() { return false; } // only handle drag
        });
        prTouchHelper.attachToRecyclerView(prRecycler);
        c.addView(prRecycler);
    }

    private class PRAdapter extends RecyclerView.Adapter<PRAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            android.widget.FrameLayout card;
            TextView handle, name, weight, muscleGroup;
            Button editBtn, delBtn;
            VH(View v) {
                super(v);
                card        = (android.widget.FrameLayout) v;
                handle      = v.findViewWithTag("handle");
                name        = v.findViewWithTag("name");
                weight      = v.findViewWithTag("weight");
                muscleGroup = v.findViewWithTag("muscle");
                editBtn     = v.findViewWithTag("editBtn");
                delBtn      = v.findViewWithTag("delBtn");
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup p, int t) {
            // Outer card
            android.widget.FrameLayout card = new android.widget.FrameLayout(requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, 8);
            card.setLayoutParams(cardLp);
            card.setBackgroundResource(R.drawable.card_bg);
            card.setPadding(16, 16, 16, 16);

            LinearLayout inner = new LinearLayout(requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

            // Top row: drag handle + name + muscle pill
            LinearLayout topRow = new LinearLayout(requireContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            topRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView handle = new TextView(requireContext()); handle.setTag("handle");
            handle.setText("≡"); handle.setTextSize(20);
            handle.setTextColor(themeColor(R.attr.colorTextMuted));
            handle.setPadding(0, 0, 10, 0);
            handle.setGravity(android.view.Gravity.CENTER_VERTICAL);
            topRow.addView(handle);

            TextView nm = new TextView(requireContext()); nm.setTag("name");
            nm.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nm.setTextColor(themeColor(R.attr.colorTextPrimary)); nm.setTextSize(15);
            nm.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(nm);

            TextView muscle = new TextView(requireContext()); muscle.setTag("muscle");
            muscle.setTextSize(10); muscle.setTextColor(0xFFFFFFFF);
            muscle.setPadding(10, 4, 10, 4);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mp.setMargins(8, 0, 0, 0); muscle.setLayoutParams(mp);
            topRow.addView(muscle);
            inner.addView(topRow);

            // Weight row
            TextView wv = new TextView(requireContext()); wv.setTag("weight");
            wv.setTextColor(themeColor(R.attr.colorTextMuted)); wv.setTextSize(13);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wlp.setMargins(0, 6, 0, 0); wv.setLayoutParams(wlp);
            inner.addView(wv);

            // Edit mode buttons row
            LinearLayout btnRow = new LinearLayout(requireContext()); btnRow.setTag("btnRow");
            btnRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams brlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            brlp.setMargins(0, 8, 0, 0); btnRow.setLayoutParams(brlp);
            btnRow.setVisibility(View.GONE);

            // Universal green Edit button (same as Body/Cardio).
            Button eb = new Button(requireContext()); eb.setTag("editBtn");
            eb.setText("Edit"); ButtonStyles.edit(eb);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            ep.setMargins(0, 0, 8, 0); eb.setLayoutParams(ep);
            btnRow.addView(eb);

            Button db = new Button(requireContext()); db.setTag("delBtn");
            db.setText("Delete"); ButtonStyles.delete(db);
            btnRow.addView(db);
            inner.addView(btnRow);

            card.addView(inner);
            return new VH(card);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecordData r = repo.records.get(pos);
            h.name.setText(r.getName());
            String wd = r.getWeight() == null || r.getWeight().isEmpty() ? "— not set"
                    : r.getWeight().matches(".*[a-zA-Z].*") ? r.getWeight() : r.getWeight() + " kg";
            h.weight.setText("Best: " + wd);

            // Muscle group pill
            String group = repo.classifyExercise(r.getName());
            int pillColor;
            switch (group) {
                case "Push": pillColor = color(R.color.pill_push); break;
                case "Pull": pillColor = color(R.color.pill_pull); break;
                case "Legs": pillColor = color(R.color.pill_legs); break;
                case "Arms": pillColor = color(R.color.pill_arms); break;
                case "Core": pillColor = color(R.color.pill_core); break;
                default:     pillColor = color(R.color.pill_other); break;
            }
            h.muscleGroup.setText(group);
            h.muscleGroup.setBackgroundColor(pillColor);

            // Edit mode
            View btnRow = h.card.findViewWithTag("btnRow");
            h.handle.setVisibility(prEditMode ? View.VISIBLE : View.GONE);
            if (btnRow != null) btnRow.setVisibility(prEditMode ? View.VISIBLE : View.GONE);

            if (prEditMode) {
                h.handle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                        prTouchHelper.startDrag(h);
                    return false;
                });
                h.editBtn.setOnClickListener(x -> showEditPR(r));
                h.delBtn.setOnClickListener(x -> Dialogs.confirmDelete(requireContext(), "the \"" + r.getName() + "\" PR", () -> {
                    repo.records.remove(r); repo.saveAsync(); notifyDataSetChanged();
                }));
            }
        }
        @Override public int getItemCount() { return repo.records.size(); }
    }

    private void showAddPR(LinearLayout c) {
        EditText nf = new EditText(requireContext()); nf.setHint("Lift name");
        EditText wf = new EditText(requireContext()); wf.setHint("Best weight (kg)");
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0);
        l.addView(nf); l.addView(wf);
        new AlertDialog.Builder(requireContext()).setTitle("Add PR").setView(l)
            .setPositiveButton("Add", (d, w) -> {
                String nm = nf.getText().toString().trim(); if (nm.isEmpty()) return;
                repo.records.add(new RecordData(nm, nw(wf.getText().toString())));
                repo.saveAsync(); buildContent(c);
            }).setNegativeButton("Cancel", null).show();
    }

    private void showEditPR(RecordData r) {
        EditText wf = new EditText(requireContext());
        wf.setHint("Best weight (kg)"); wf.setText(r.getWeight());
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0); l.addView(wf);
        new AlertDialog.Builder(requireContext()).setTitle("Edit: " + r.getName()).setView(l)
            .setPositiveButton("Save", (d, w) -> {
                r.setWeight(nw(wf.getText().toString())); repo.saveAsync();
                if (prAdapter != null) prAdapter.notifyDataSetChanged();
            }).setNegativeButton("Cancel", null).show();
    }

    // ── Macros ────────────────────────────────────────────────────────────────

    /** Built-in common foods for one-tap logging. name, cals, protein, carbs, fats. */
    private static final Food[] PRESET_FOODS = {
        new Food("Chicken breast 100g", 165, 31, 0, 3.6),
        new Food("White rice 1 cup", 205, 4, 45, 0.4),
        new Food("Egg", 78, 6, 0.6, 5),
        new Food("Oats 40g", 150, 5, 27, 3),
        new Food("Banana", 105, 1.3, 27, 0.4),
        new Food("Protein shake", 120, 24, 3, 1.5),
        new Food("Greek yogurt 170g", 100, 17, 6, 0.7),
        new Food("Almonds 28g", 164, 6, 6, 14),
        new Food("Salmon 100g", 208, 20, 0, 13),
        new Food("Peanut butter 1 tbsp", 94, 4, 3, 8),
    };

    private void buildMacros(LinearLayout c) {
        // ── Today's rings (consumed vs goal) ──
        hdr(c, "Today");
        double[] consumed = { repo.todayMacro(0), repo.todayMacro(1), repo.todayMacro(2), repo.todayMacro(3) };
        double[] goals = new double[4];
        boolean anyGoal = false;
        for (int i = 0; i < 4; i++) { goals[i] = parseD(repo.macroGoals[i]); if (goals[i] > 0) anyGoal = true; }
        if (!anyGoal) {
            TextView note = new TextView(requireContext());
            note.setText("⤵  Rings stay empty until you set your Daily Targets below");
            note.setTextColor(color(R.color.accent_2)); note.setTextSize(12);
            note.setTypeface(null, android.graphics.Typeface.BOLD);
            note.setPadding(0, 0, 0, 8);
            c.addView(note);
        }
        MacroRingsView rings = new MacroRingsView(requireContext());
        rings.setTextColors(themeColor(R.attr.colorTextMuted), themeColor(R.attr.colorTextPrimary));
        rings.setData(consumed, goals);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(150));
        rlp.setMargins(0, 0, 0, 8); rings.setLayoutParams(rlp);
        c.addView(rings);

        // ── Quick-add chips (presets + saved foods) ──
        hdr(c, "Quick Add  ·  tap to log");
        HorizontalScrollView hsv = new HorizontalScrollView(requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(requireContext());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (Food f : PRESET_FOODS) chipRow.addView(foodChip(c, f, false));
        for (Food f : repo.savedFoods) chipRow.addView(foodChip(c, f, true));
        hsv.addView(chipRow);
        c.addView(hsv);
        sp(c, 6);

        Button custom = btn(c, "+ Add Custom Food", color(R.color.accent_dark));
        custom.setOnClickListener(x -> showAddFoodDialog(c));

        // ── Today's logged foods ──
        List<Food> today = repo.foodLog.getOrDefault(LocalDate.now(), Collections.emptyList());
        sp(c, 12);
        LinearLayout logHdr = new LinearLayout(requireContext());
        logHdr.setOrientation(LinearLayout.HORIZONTAL);
        logHdr.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView lh = new TextView(requireContext());
        lh.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        lh.setText("Logged Today (" + today.size() + ")");
        lh.setTextColor(themeColor(R.attr.colorTextMuted)); lh.setTextSize(11);
        logHdr.addView(lh);
        // Edit toggle: unlocks saved-food chips and the Daily Targets fields below
        Button fe = new Button(requireContext());
        fe.setText(foodEditMode ? "Done" : "Edit"); ButtonStyles.toggle(fe, foodEditMode);
        LinearLayout.LayoutParams felp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        felp.setMargins(0, 0, 6, 0); fe.setLayoutParams(felp);
        fe.setOnClickListener(x -> { foodEditMode = !foodEditMode; buildContent(c); });
        logHdr.addView(fe);
        if (!today.isEmpty()) {
            Button clr = new Button(requireContext());
            clr.setText("Clear All"); ButtonStyles.delete(clr);
            clr.setOnClickListener(x -> Dialogs.confirm(requireContext(), "Clear log",
                    "Are you sure you want to clear everything logged today?", "Clear All", () -> {
                repo.foodLog.remove(LocalDate.now()); repo.saveAsync(); buildContent(c);
            }));
            logHdr.addView(clr);
        }
        c.addView(logHdr);

        if (today.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("Nothing logged yet — tap a food above to add it.");
            empty.setTextColor(themeColor(R.attr.colorTextMuted)); empty.setTextSize(12);
            empty.setPadding(0, 8, 0, 0);
            c.addView(empty);
        } else {
            for (int i = today.size() - 1; i >= 0; i--) c.addView(loggedFoodRow(c, today.get(i)));
        }

        // ── Goal targets + calculator ──
        sp(c, 16);
        hdr(c, foodEditMode ? "Daily Targets (Goal)"
                : "Daily Targets (Goal)  ·  tap Edit above to change manually");
        for (int i = 0; i < 4; i++) { final int idx = i; editRow(c, MACRO_LABELS[i], repo.macroGoals[i],
                v -> { repo.macroGoals[idx] = v; repo.saveDebounced(); }); }
        sp(c, 12);
        Button pb = btn(c, "Use Macro Preset Calculator", color(R.color.accent_dark));
        pb.setOnClickListener(x -> showMacroPreset(c));
    }

    /** A tappable food pill. Tap → log to today; in edit mode, tap a saved food → edit/delete it. */
    private TextView foodChip(LinearLayout c, Food f, boolean saved) {
        TextView chip = new TextView(requireContext());
        chip.setText((saved ? "★ " : "") + f.getName() + "  ·  " + (int) f.getCals());
        chip.setTextSize(11); chip.setTextColor(color(R.color.white));
        chip.setBackgroundResource(saved ? R.drawable.btn_accent_dark : R.drawable.btn_secondary);
        chip.setPadding(20, 12, 20, 12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 8, 0); chip.setLayoutParams(lp);
        chip.setOnClickListener(x -> {
            if (foodEditMode) {
                if (saved) showEditFoodDialog(c, f);
                else Toast.makeText(requireContext(),
                        "Built-in food — can't be edited", Toast.LENGTH_SHORT).show();
                return;
            }
            addFoodToday(new Food(f.getName(), f.getCals(), f.getProtein(), f.getCarbs(), f.getFats()));
            buildContent(c);
            Toast.makeText(requireContext(), "Added " + f.getName(), Toast.LENGTH_SHORT).show();
        });
        return chip;
    }

    /** Edit-mode dialog for a saved food: change name/macros, or delete it. */
    private void showEditFoodDialog(LinearLayout c, Food f) {
        EditText nf = new EditText(requireContext()); nf.setHint("Food name"); nf.setText(f.getName());
        EditText cf = new EditText(requireContext()); cf.setHint("Calories"); cf.setText(fmtD(f.getCals()));
        EditText pf = new EditText(requireContext()); pf.setHint("Protein (g)"); pf.setText(fmtD(f.getProtein()));
        EditText cbf = new EditText(requireContext()); cbf.setHint("Carbs (g)"); cbf.setText(fmtD(f.getCarbs()));
        EditText ff = new EditText(requireContext()); ff.setHint("Fats (g)"); ff.setText(fmtD(f.getFats()));
        for (EditText e : new EditText[]{cf, pf, cbf, ff})
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 16, 48, 0);
        l.addView(nf); l.addView(cf); l.addView(pf); l.addView(cbf); l.addView(ff);
        new AlertDialog.Builder(requireContext()).setTitle("Edit: " + f.getName()).setView(l)
            .setPositiveButton("Save", (d, w) -> {
                String nm = nf.getText().toString().trim();
                if (nm.isEmpty()) { Toast.makeText(requireContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                f.setName(nm);
                f.setCals(parseD(cf.getText().toString()));
                f.setProtein(parseD(pf.getText().toString()));
                f.setCarbs(parseD(cbf.getText().toString()));
                f.setFats(parseD(ff.getText().toString()));
                repo.saveAsync(); buildContent(c);
            })
            .setNeutralButton("Delete", (d, w) ->
                Dialogs.confirmDelete(requireContext(), "\"" + f.getName() + "\" from your saved foods", () -> {
                    repo.savedFoods.remove(f); repo.saveAsync(); buildContent(c);
                }))
            .setNegativeButton("Cancel", null).show();
    }

    /** Plain (Locale.US) number for prefilling edit fields — never a comma decimal. */
    private String fmtD(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v)
                : String.format(java.util.Locale.US, "%.1f", v);
    }

    private LinearLayout loggedFoodRow(LinearLayout c, Food f) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundColor(themeColor(R.attr.colorCardBg));
        row.setPadding(16, 12, 16, 12);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, 6); row.setLayoutParams(rlp);

        LinearLayout txt = new LinearLayout(requireContext());
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView nm = new TextView(requireContext());
        nm.setText(f.getName()); nm.setTextColor(themeColor(R.attr.colorTextPrimary)); nm.setTextSize(13);
        TextView mc = new TextView(requireContext());
        mc.setText(String.format("%d kcal · P%d C%d F%d",
                (int) f.getCals(), (int) f.getProtein(), (int) f.getCarbs(), (int) f.getFats()));
        mc.setTextColor(themeColor(R.attr.colorTextMuted)); mc.setTextSize(11);
        txt.addView(nm); txt.addView(mc);
        row.addView(txt);

        Button rm = new Button(requireContext());
        rm.setText("Delete"); ButtonStyles.delete(rm);
        rm.setOnClickListener(x -> Dialogs.confirmDelete(requireContext(), "\"" + f.getName() + "\" from today's log", () -> {
            List<Food> list = repo.foodLog.get(LocalDate.now());
            if (list != null) { list.remove(f); if (list.isEmpty()) repo.foodLog.remove(LocalDate.now()); }
            repo.saveAsync(); buildContent(c);
        }));
        row.addView(rm);
        return row;
    }

    private void addFoodToday(Food f) {
        repo.foodLog.computeIfAbsent(LocalDate.now(), d -> new ArrayList<>()).add(f);
        repo.saveAsync();
    }

    private void showAddFoodDialog(LinearLayout c) {
        EditText nf = new EditText(requireContext()); nf.setHint("Food name");
        EditText cf = new EditText(requireContext()); cf.setHint("Calories");
        EditText pf = new EditText(requireContext()); pf.setHint("Protein (g)");
        EditText cbf = new EditText(requireContext()); cbf.setHint("Carbs (g)");
        EditText ff = new EditText(requireContext()); ff.setHint("Fats (g)");
        for (EditText e : new EditText[]{cf, pf, cbf, ff})
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox saveIt = new CheckBox(requireContext());
        saveIt.setText("Save for quick-add next time");
        saveIt.setTextColor(themeColor(R.attr.colorTextPrimary));

        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 16, 48, 0);
        l.addView(nf); l.addView(cf); l.addView(pf); l.addView(cbf); l.addView(ff); l.addView(saveIt);

        new AlertDialog.Builder(requireContext()).setTitle("Add Custom Food").setView(l)
            .setPositiveButton("Log", (d, w) -> {
                String nm = nf.getText().toString().trim();
                if (nm.isEmpty()) { Toast.makeText(requireContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                Food f = new Food(nm, parseD(cf.getText().toString()), parseD(pf.getText().toString()),
                        parseD(cbf.getText().toString()), parseD(ff.getText().toString()));
                addFoodToday(new Food(f.getName(), f.getCals(), f.getProtein(), f.getCarbs(), f.getFats()));
                if (saveIt.isChecked()) repo.savedFoods.add(f);
                repo.saveAsync(); buildContent(c);
            }).setNegativeButton("Cancel", null).show();
    }

    private double parseD(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return 0; }
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void editRow(LinearLayout container, String label, String value, java.util.function.Consumer<String> onSave) {
        LinearLayout row = row(container);
        TextView lbl = new TextView(requireContext());
        lbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        lbl.setText(label); lbl.setTextColor(themeColor(R.attr.colorTextPrimary)); lbl.setTextSize(13);
        EditText field = new EditText(requireContext());
        field.setText(value);
        field.setHint("0");
        field.setTextColor(themeColor(R.attr.colorTextPrimary));
        field.setHintTextColor(themeColor(R.attr.colorTextMuted));
        field.setTextSize(13);
        field.setBackgroundResource(R.drawable.field_bg);
        field.setPadding(16, 8, 16, 8);
        field.setMinWidth(120);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.setMargins(8, 0, 0, 0);
        field.setLayoutParams(fp);
        // Manual changes require Edit mode — the preset calculator still works anytime.
        field.setEnabled(foodEditMode);
        field.setAlpha(foodEditMode ? 1f : 0.6f);
        field.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(android.text.Editable s) { onSave.accept(s.toString().trim()); }
        });
        row.addView(lbl); row.addView(field); container.addView(row);
    }

    private void showMacroPreset(LinearLayout c) {
        String[] goals = {"Weight Loss","Muscle Building","Maintenance","Lean Bulk","Cut (aggressive)"};
        String[] sexes = {"Male","Female"};
        Spinner gs = new Spinner(requireContext()), ss = new Spinner(requireContext());
        EditText bw = new EditText(requireContext()); bw.setHint("Current bodyweight (kg)");
        bw.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        // Locale.US: a comma decimal separator ("82,5") would be rejected by the
        // field's digits filter and by the parse on Apply.
        if (repo.startWeightKg > 0) bw.setText(String.format(java.util.Locale.US, "%.1f", repo.startWeightKg));
        EditText gw = new EditText(requireContext()); gw.setHint("Goal bodyweight (kg)");
        gw.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (repo.goalWeightKg > 0) gw.setText(String.format(java.util.Locale.US, "%.1f", repo.goalWeightKg));
        ArrayAdapter<String> ga = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, goals);
        ga.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); gs.setAdapter(ga);
        ArrayAdapter<String> sa = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, sexes);
        sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); ss.setAdapter(sa);
        LinearLayout l = new LinearLayout(requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0);
        l.addView(tv("Goal")); l.addView(gs); l.addView(tv("Sex")); l.addView(ss);
        l.addView(tv("Current bodyweight (kg)")); l.addView(bw);
        l.addView(tv("Goal bodyweight (kg)")); l.addView(gw);
        new AlertDialog.Builder(requireContext()).setTitle("Macro Preset").setView(l)
            .setPositiveButton("Apply", (d, w) -> {
                try {
                    double b = parseD(bw.getText().toString());
                    if (b <= 0) throw new NumberFormatException();
                    // Capture start (current) and goal weight for the Body goal bar.
                    repo.startWeightKg = b;
                    double goalW = parseD(gw.getText().toString());
                    if (goalW > 0) repo.goalWeightKg = goalW;
                    boolean male = ss.getSelectedItemPosition() == 0;
                    String g = gs.getSelectedItem().toString();
                    double cal, pro, fat, carb;
                    switch (g) {
                        case "Weight Loss":      cal=b*(male?28:26); pro=b*2.0; fat=b*0.8; break;
                        case "Muscle Building":  cal=b*(male?38:34); pro=b*2.2; fat=b*1.0; break;
                        case "Lean Bulk":        cal=b*(male?35:32); pro=b*2.0; fat=b*0.9; break;
                        case "Cut (aggressive)": cal=b*(male?24:22); pro=b*2.4; fat=b*0.7; break;
                        default:                 cal=b*(male?33:30); pro=b*1.8; fat=b*1.0; break;
                    }
                    carb = Math.max(50, (cal - pro*4 - fat*9) / 4);
                    repo.macroGoals[0]=String.format("%.0f",cal); repo.macroGoals[1]=String.format("%.0f",pro);
                    repo.macroGoals[2]=String.format("%.0f",carb); repo.macroGoals[3]=String.format("%.0f",fat);
                    repo.saveAsync(); buildContent(c);
                } catch (Exception e) { Toast.makeText(requireContext(),"Enter valid bodyweight",Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("Cancel", null).show();
    }

    // ── Assessment ────────────────────────────────────────────────────────────

    private void buildAssessment(LinearLayout c) {
        hdr(c, "Split Assessment");
        TextView result = new TextView(requireContext());
        result.setTextColor(themeColor(R.attr.colorTextMuted)); result.setTextSize(12);
        result.setTypeface(android.graphics.Typeface.MONOSPACE);
        result.setText("Tap Analyse to assess your weekly split.");
        Button runBtn = btn(c, "Analyse My Programme", color(R.color.accent));
        runBtn.setOnClickListener(x -> result.setText(runAssessment()));
        sp(c, 12); c.addView(result);
    }

    private String runAssessment() {
        if (repo.exercises.isEmpty()) return "No exercises found. Add exercises first.";
        Map<String,Integer> setCounts = new HashMap<>();
        Map<java.time.DayOfWeek,List<ExerciseData>> byDay = new LinkedHashMap<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) byDay.put(d, new ArrayList<>());
        int totalSets=0, shortRest=0, longRest=0, restCount=0; double avgRest=0;
        for (ExerciseData ex : repo.exercises) {
            if (repo.restDays.contains(ex.getDayOfWeek())) continue;
            byDay.get(ex.getDayOfWeek()).add(ex);
            String cat = repo.classifyExercise(ex.getName());
            String[] parts = ex.getReps()==null?new String[0]:ex.getReps().split("x");
            int sets = parts.length>0?pi(parts[0],1):1;
            setCounts.merge(cat,sets,Integer::sum);
            totalSets+=sets; avgRest+=ex.getRestSeconds(); restCount++;
            if (ex.getRestSeconds()<45) shortRest++; if (ex.getRestSeconds()>180) longRest++;
        }
        if (restCount>0) avgRest/=restCount;
        int trainingDays=(int)byDay.values().stream().filter(d->!d.isEmpty()).count();
        int setsPerSession=trainingDays>0?totalSets/trainingDays:0;
        int push=setCounts.getOrDefault("Push",0), pull=setCounts.getOrDefault("Pull",0);
        int legs=setCounts.getOrDefault("Legs",0), arms=setCounts.getOrDefault("Arms",0);
        int core=setCounts.getOrDefault("Core",0);
        // Scoring
        int score=0;
        if (push>0) score+=10; if (pull>0) score+=10; if (legs>0) score+=10;
        if (push>0&&pull>0) score+=(int)((double)Math.min(push,pull)/Math.max(push,pull)*15);
        if (trainingDays>=3&&trainingDays<=5) score+=15;
        else if (trainingDays==2||trainingDays==6) score+=8;
        else if (trainingDays==1) score+=3;
        if (setsPerSession>=12&&setsPerSession<=20) score+=15;
        else if (setsPerSession>=8) score+=10;
        else if (setsPerSession>20&&setsPerSession<=25) score+=8;
        else if (setsPerSession>0) score+=4;
        if (shortRest==0&&longRest==0) score+=10; else if (shortRest==0||longRest==0) score+=6;
        if (core>0) score+=5; if (arms>0) score+=3;
        if (trainingDays>=4) score+=4; if (totalSets>=15&&totalSets<=25) score+=3;
        score=Math.min(100,score);
        String grade=score>=90?"A+":score>=80?"A":score>=70?"B+":score>=60?"B":score>=50?"C+":score>=40?"C":"D";
        String splitType=detectSplit(byDay,setCounts,trainingDays);
        StringBuilder r=new StringBuilder();
        r.append("━━━━━━━━━━━━━━━━━━━━━━━\n");
        r.append(String.format("  SCORE  %d%%  (%s)\n",score,grade));
        r.append("━━━━━━━━━━━━━━━━━━━━━━━\n\n");
        r.append(String.format("Split type:      %s\n",splitType));
        r.append(String.format("Training days:   %d/7\n",trainingDays));
        r.append(String.format("Total sets/wk:   %d\n",totalSets));
        r.append(String.format("Sets/session:    %d\n",setsPerSession));
        r.append(String.format("Avg rest:        %.0fs\n\n",avgRest));
        r.append("MUSCLE GROUPS\n─────────────────────\n");
        int maxS=Math.max(1,Math.max(push,Math.max(pull,legs)));
        for (String[] g:new String[][]{{"Push",String.valueOf(push)},{"Pull",String.valueOf(pull)},
                {"Legs",String.valueOf(legs)},{"Arms",String.valueOf(arms)},{"Core",String.valueOf(core)}}) {
            int s=Integer.parseInt(g[1]); if (s==0) continue;
            int bars=Math.max(1,(int)((double)s/maxS*10));
            r.append(String.format("%-5s  %-11s  %2d sets\n",g[0],"█".repeat(bars)+"░".repeat(10-bars),s));
        }
        if (push>0&&pull>0) {
            double ratio=(double)Math.min(push,pull)/Math.max(push,pull);
            r.append(String.format("\nPush:Pull  %d:%d  %s\n",push,pull,
                ratio>=0.8?"✓ Balanced":push>pull?"⚠ Push-heavy":"⚠ Pull-heavy"));
        }
        r.append("\nCOACH NOTES\n─────────────────────\n");
        if (push==0) r.append("✗ No push work — add bench/press/dip\n"); else r.append("✓ Push work present\n");
        if (pull==0) r.append("✗ No pull work — add rows/pulldowns\n"); else r.append("✓ Pull work present\n");
        if (legs==0) r.append("✗ No leg work — don\'t skip leg day!\n"); else r.append("✓ Leg work present\n");
        if (core==0) r.append("⚠ No core work — add planks/abs\n");
        if (push>0&&pull>0&&Math.abs(push-pull)>4) r.append("⚠ Push/pull gap ("+push+" vs "+pull+" sets) — risk of imbalance\n");
        if (trainingDays<3) r.append("⚠ Low frequency ("+trainingDays+" days) — aim for 3–5\n");
        else if (trainingDays<=5) r.append("✓ Good training frequency ("+trainingDays+" days/wk)\n");
        else r.append("⚠ Very high frequency ("+trainingDays+" days) — watch recovery\n");
        if (setsPerSession<8) r.append("⚠ Low volume ("+setsPerSession+" sets/session) — aim 10–20\n");
        else if (setsPerSession<=20) r.append("✓ Good session volume ("+setsPerSession+" sets)\n");
        else r.append("⚠ High volume ("+setsPerSession+" sets/session) — monitor recovery\n");
        if (shortRest>0) r.append("⚠ "+shortRest+" exercise(s) with very short rest (<45s)\n");
        if (longRest>0) r.append("  "+longRest+" exercise(s) with long rest (>3min) — fine for compounds\n");
        if (avgRest>=60&&avgRest<=180) r.append("✓ Rest time appropriate ("+((int)avgRest)+"s avg)\n");
        r.append("\n").append(getSplitAdvice(splitType,trainingDays,push,pull,legs,setsPerSession));
        return r.toString();
    }

    private String detectSplit(Map<java.time.DayOfWeek,List<ExerciseData>> byDay,
                                Map<String,Integer> sc, int trainingDays) {
        if (trainingDays==0) return "None";
        long fbDays=byDay.values().stream().filter(day->{
            boolean hp=day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Push"));
            boolean hl=day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Legs"));
            boolean hr=day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Pull"));
            return hp&&hl&&hr;
        }).count();
        if (fbDays>=trainingDays-1&&trainingDays>0) return "Full Body";
        long pushDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Push"))&&
            day.stream().noneMatch(e->repo.classifyExercise(e.getName()).equals("Pull"))).count();
        long pullDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Pull"))&&
            day.stream().noneMatch(e->repo.classifyExercise(e.getName()).equals("Push"))).count();
        if (pushDays>=1&&pullDays>=1&&trainingDays>=3) return "Push/Pull/Legs";
        long upperDays=byDay.values().stream().filter(day->{
            boolean upper=day.stream().anyMatch(e->{String c=repo.classifyExercise(e.getName());return c.equals("Push")||c.equals("Pull")||c.equals("Arms");});
            boolean noLegs=day.stream().noneMatch(e->repo.classifyExercise(e.getName()).equals("Legs"));
            return upper&&noLegs&&!day.isEmpty();
        }).count();
        long lowerDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->repo.classifyExercise(e.getName()).equals("Legs"))).count();
        if (upperDays>=1&&lowerDays>=1) return "Upper/Lower";
        return trainingDays<=3?"Bro Split / Custom":"Custom";
    }

    private String getSplitAdvice(String split,int days,int push,int pull,int legs,int sps) {
        StringBuilder a=new StringBuilder("SPLIT ADVICE\n─────────────────────\n");
        switch (split) {
            case "Push/Pull/Legs":
                a.append("PPL is excellent for intermediate+.\n");
                if (days==6) a.append("✓ 6-day PPL: optimal frequency for hypertrophy.\n");
                else if (days==3) a.append("3-day PPL hits each muscle 1x/wk. Consider 6-day for 2x frequency.\n");
                else a.append("Consider 6-day PPL for maximum frequency.\n"); break;
            case "Upper/Lower":
                a.append("Upper/Lower is highly efficient.\n");
                if (days==4) a.append("✓ 4-day U/L: ideal balance of frequency and recovery.\n");
                else a.append("Aim for 4 days to hit each muscle 2x/week.\n"); break;
            case "Full Body":
                a.append("Full Body suits beginners and strength athletes.\n");
                if (days>=3) a.append("✓ "+days+" full body sessions: good stimulus frequency.\n");
                if (sps>20) a.append("⚠ Sessions may run long — consider supersets.\n"); break;
            case "Bro Split / Custom":
                a.append("Bro split trains each muscle once/week.\n");
                a.append("✓ Good for high per-session volume.\n");
                a.append("⚠ Only 1x/week frequency — consider Upper/Lower for more growth.\n"); break;
            default:
                a.append("Custom split detected.\n");
                a.append("Aim to hit each muscle 2x/week for optimal hypertrophy.\n");
        }
        a.append("\nLog sets in Exercise Detail to\ntrack progressive overload over time.\n");
        return a.toString();
    }

    // ── Settings ──────────────────────────────────────────────────────────────

    private void buildSettings(LinearLayout c) {
        hdr(c, "Settings"); sp(c, 8);
        // Light Mode toggle (off by default = dark mode on)
        LinearLayout dmRow = row(c);
        TextView dml = new TextView(requireContext());
        dml.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dml.setText("Light Mode"); dml.setTextColor(themeColor(R.attr.colorTextPrimary)); dml.setTextSize(14);
        Switch dms = new Switch(requireContext());
        // Switch ON = light mode. repo.darkMode=true means dark is on, so lightMode = !darkMode
        dms.setChecked(!repo.darkMode);
        dms.setOnCheckedChangeListener((b, lightModeOn) -> {
            repo.darkMode = !lightModeOn; repo.saveAsync();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).applyTheme(!lightModeOn);
            }
        });
        dmRow.addView(dml); dmRow.addView(dms); c.addView(dmRow); sp(c, 12);
        infoRow(c, "Data location", "App internal storage");
        infoRow(c, "Auto-save", "On every change");
        infoRow(c, "Exercise schedule", "Every 7 days from added date"); sp(c, 16);
        Button clr = btn(c, "Clear All Data", color(R.color.danger));
        clr.setOnClickListener(x -> new AlertDialog.Builder(requireContext())
            .setTitle("Clear All Data")
            .setMessage("Are you sure? This will permanently delete ALL your workout, body, cardio, and PR data.")
            .setPositiveButton("Yes, delete everything", (d, w) ->
                new AlertDialog.Builder(requireContext())
                    .setTitle("Final Confirmation")
                    .setMessage("This cannot be undone. Delete ALL data permanently?")
                    .setPositiveButton("Delete Everything", (d2, w2) -> {
                        repo.clearAll();
                        Toast.makeText(requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null).show()
            )
            .setNegativeButton("Cancel", null).show());
    }

    private void infoRow(LinearLayout c, String label, String value) {
        LinearLayout row = row(c);
        TextView l = new TextView(requireContext());
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.setText(label); l.setTextColor(themeColor(R.attr.colorTextPrimary)); l.setTextSize(13);
        TextView v = new TextView(requireContext()); v.setText(value);
        v.setTextColor(themeColor(R.attr.colorTextMuted)); v.setTextSize(12);
        row.addView(l); row.addView(v); c.addView(row); sp(c, 6);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void hdr(LinearLayout c, String t) {
        TextView tv = new TextView(requireContext()); tv.setText(t);
        tv.setTextColor(themeColor(R.attr.colorTextMuted)); tv.setTextSize(11);
        tv.setPadding(0, 0, 0, 6); c.addView(tv);
    }
    private TextView tv(String text) {
        TextView t = new TextView(requireContext()); t.setText(text); t.setTextColor(themeColor(R.attr.colorTextPrimary)); return t;
    }
    private int color(int res) { return requireContext().getResources().getColor(res, requireContext().getTheme()); }

    /** Resolve a theme attribute colour — correct in both light and dark mode */
    private int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }
    private void sp(LinearLayout c, int dp) {
        View v = new View(requireContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp));
        c.addView(v);
    }
    private Button btn(LinearLayout c, String t, int col) {
        Button b = new Button(requireContext()); b.setText(t);
        if (col == color(R.color.danger))      b.setBackgroundResource(R.drawable.btn_danger);
        else if (col == color(R.color.accent_dark)) b.setBackgroundResource(R.drawable.btn_accent_dark);
        else                                   b.setBackgroundResource(R.drawable.btn_primary);
        b.setTextColor(color(R.color.white));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8); b.setLayoutParams(lp);
        c.addView(b); return b;
    }
    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(requireContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBackgroundColor(themeColor(R.attr.colorCardBg));
        r.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6); r.setLayoutParams(lp);
        return r;
    }
    private String nw(String raw) {
        if (raw==null||raw.trim().isEmpty()) return "";
        String t = raw.trim().replaceAll("(?i)\\s*(kgs?|lbs?)\\s*$","").trim();
        if (t.matches("\\d+,\\d+")) t = t.replace(",",".");
        t = t.replaceAll("[^0-9.]","").trim();
        return t.isEmpty() ? "" : t;
    }
    private int pi(String s, int fb) { try { return Math.max(1,Integer.parseInt(s.trim())); } catch(Exception e) { return fb; } }
}
