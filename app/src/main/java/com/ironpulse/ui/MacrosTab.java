package com.ironpulse.ui;

import android.app.AlertDialog;
import android.widget.*;
import com.ironpulse.R;
import com.ironpulse.data.Units;
import com.ironpulse.model.Food;
import java.time.LocalDate;
import java.util.*;

/** "Macros" tab of the More screen: rings, quick-add foods, daily targets + calculator. */
class MacrosTab {
    private static final String[] MACRO_LABELS = {"Calories","Protein (g)","Carbs (g)","Fats (g)"};

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

    private final MoreFragment host;

    MacrosTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        // ── Today's rings (consumed vs goal) ──
        host.hdr(c, "TODAY");
        double[] consumed = { host.repo.todayMacro(0), host.repo.todayMacro(1), host.repo.todayMacro(2), host.repo.todayMacro(3) };
        double[] goals = new double[4];
        boolean anyGoal = false;
        for (int i = 0; i < 4; i++) { goals[i] = host.parseD(host.repo.macroGoals[i]); if (goals[i] > 0) anyGoal = true; }
        if (!anyGoal) {
            TextView note = new TextView(host.requireContext());
            note.setText("Set your daily targets to fill the rings  ›");
            note.setTextColor(host.color(R.color.accent)); note.setTextSize(14);
            note.setPadding(0, 0, 0, host.dp(8));
            note.setOnClickListener(x -> showTargetsDialog());
            c.addView(note);
        }
        MacroRingsView rings = new MacroRingsView(host.requireContext());
        rings.setTextColors(host.themeColor(R.attr.colorTextMuted), host.themeColor(R.attr.colorTextPrimary));
        rings.setData(consumed, goals);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, host.dp(150));
        rlp.setMargins(0, 0, 0, 8); rings.setLayoutParams(rlp);
        c.addView(rings);

        // ── Calorie adherence history (only once something was logged in the window) ──
        boolean anyIn30 = false;
        for (int i = 0; i < 30 && !anyIn30; i++)
            anyIn30 = !host.repo.foodLog.getOrDefault(LocalDate.now().minusDays(i), Collections.emptyList()).isEmpty();
        if (anyIn30) {
            double goalCals = host.parseD(host.repo.macroGoals[0]);
            host.hdr(c, "Calories — last 30 days"
                    + (goalCals > 0 ? "  ·  goal " + (int) goalCals + " kcal" : ""));
            HistoryChartView chart = new HistoryChartView(host.requireContext());
            chart.setBackgroundResource(R.drawable.tile_bg); chart.setClipToOutline(true);
            chart.setBgColor(host.themeColor(R.attr.colorCardBg));
            chart.setTextColor(host.themeColor(R.attr.colorTextMuted));
            List<Double> vals = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("d/MM");
            for (int i = 29; i >= 0; i--) {
                LocalDate d = LocalDate.now().minusDays(i);
                double sum = 0;
                for (Food f : host.repo.foodLog.getOrDefault(d, Collections.emptyList())) sum += f.getCals();
                vals.add(sum);
                labels.add(d.format(fmt));
            }
            chart.setData(vals, labels);
            LinearLayout.LayoutParams clp2 = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, host.dp(170));
            clp2.setMargins(0, 0, 0, 12); chart.setLayoutParams(clp2);
            c.addView(chart);
        }

        // ── Quick-add chips: + Custom, presets, then your saved foods ──
        host.sp(c, 8);
        host.hdr(c, "QUICK ADD  ·  tap to log, hold a ★ food to edit");
        HorizontalScrollView hsv = new HorizontalScrollView(host.requireContext());
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(host.requireContext());
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView custom = chip("+ Custom", true);
        custom.setOnClickListener(x -> showAddFoodDialog(c));
        chipRow.addView(custom);
        for (Food f : host.repo.savedFoods) chipRow.addView(foodChip(f, true));
        for (Food f : PRESET_FOODS) chipRow.addView(foodChip(f, false));
        hsv.addView(chipRow);
        c.addView(hsv);

        // ── Today's logged foods ──
        List<Food> today = host.repo.foodLog.getOrDefault(LocalDate.now(), Collections.emptyList());
        host.sp(c, 20);
        host.hdr(c, "TODAY'S LOG" + (today.isEmpty() ? "" : "  ·  hold to remove"));
        if (today.isEmpty()) {
            TextView empty = new TextView(host.requireContext());
            empty.setText("Nothing logged yet — tap a food above.");
            empty.setTextColor(host.themeColor(R.attr.colorTextMuted)); empty.setTextSize(14);
            c.addView(empty);
        } else {
            for (int i = today.size() - 1; i >= 0; i--) c.addView(loggedFoodRow(today.get(i)));
        }

        // ── Goal targets (one row; the dialog holds the fields + calculator) ──
        host.sp(c, 20);
        host.hdr(c, "DAILY TARGETS");
        LinearLayout tr = host.row();
        TextView tl = new TextView(host.requireContext());
        tl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tl.setText(anyGoal
                ? String.format(java.util.Locale.US, "%d kcal  ·  P %d  ·  C %d  ·  F %d",
                    (int) goals[0], (int) goals[1], (int) goals[2], (int) goals[3])
                : "Not set");
        tl.setTextColor(host.themeColor(R.attr.colorTextPrimary)); tl.setTextSize(15);
        TextView tv = new TextView(host.requireContext());
        tv.setText("›"); tv.setTextColor(host.themeColor(R.attr.colorTextMuted)); tv.setTextSize(15);
        tr.addView(tl); tr.addView(tv);
        tr.setOnClickListener(x -> showTargetsDialog());
        c.addView(tr);
    }

    /** Daily targets: four fields, plus the preset calculator one tap away. */
    private void showTargetsDialog() {
        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(host.dp(24), host.dp(8), host.dp(24), 0);
        EditText[] fields = new EditText[4];
        for (int i = 0; i < 4; i++) {
            fields[i] = new EditText(host.requireContext());
            fields[i].setHint(MACRO_LABELS[i]);
            fields[i].setText(host.repo.macroGoals[i]);
            fields[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            TextView lbl = new TextView(host.requireContext());
            lbl.setText(MACRO_LABELS[i]); lbl.setTextSize(12);
            lbl.setTextColor(host.themeColor(R.attr.colorTextMuted));
            l.addView(lbl); l.addView(fields[i]);
        }
        new AlertDialog.Builder(host.requireContext())
            .setTitle("Daily targets")
            .setView(l)
            .setPositiveButton("Save", (d, w) -> {
                for (int i = 0; i < 4; i++) host.repo.macroGoals[i] = fields[i].getText().toString().trim();
                host.repo.saveAsync(); host.rebuild();
            })
            .setNeutralButton("Calculator", (d, w) -> showMacroPreset())
            .setNegativeButton("Cancel", null).show();
    }

    private TextView chip(String text, boolean highlight) {
        TextView chip = new TextView(host.requireContext());
        chip.setText(text);
        chip.setTextSize(13);
        chip.setTextColor(highlight ? host.color(R.color.accent) : host.themeColor(R.attr.colorTextPrimary));
        chip.setBackgroundResource(R.drawable.btn_quiet);
        chip.setPadding(host.dp(14), host.dp(9), host.dp(14), host.dp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, host.dp(8), 0); chip.setLayoutParams(lp);
        return chip;
    }

    /** A food pill. Tap → log to today; long-press a saved (★) food → edit or delete it. */
    private TextView foodChip(Food f, boolean saved) {
        TextView chip = chip((saved ? "★ " : "") + f.getName() + "  ·  " + (int) f.getCals(), false);
        chip.setOnClickListener(x -> {
            addFoodToday(new Food(f.getName(), f.getCals(), f.getProtein(), f.getCarbs(), f.getFats()));
            host.rebuild();
            Toast.makeText(host.requireContext(), "Added " + f.getName(), Toast.LENGTH_SHORT).show();
        });
        if (saved) chip.setOnLongClickListener(x -> { showEditFoodDialog(f); return true; });
        return chip;
    }

    /** Edit-mode dialog for a saved food: change name/macros, or delete it. */
    private void showEditFoodDialog(Food f) {
        EditText nf = new EditText(host.requireContext()); nf.setHint("Food name"); nf.setText(f.getName());
        EditText cf = new EditText(host.requireContext()); cf.setHint("Calories"); cf.setText(fmtD(f.getCals()));
        EditText pf = new EditText(host.requireContext()); pf.setHint("Protein (g)"); pf.setText(fmtD(f.getProtein()));
        EditText cbf = new EditText(host.requireContext()); cbf.setHint("Carbs (g)"); cbf.setText(fmtD(f.getCarbs()));
        EditText ff = new EditText(host.requireContext()); ff.setHint("Fats (g)"); ff.setText(fmtD(f.getFats()));
        for (EditText e : new EditText[]{cf, pf, cbf, ff})
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 16, 48, 0);
        l.addView(nf); l.addView(cf); l.addView(pf); l.addView(cbf); l.addView(ff);
        new AlertDialog.Builder(host.requireContext()).setTitle("Edit: " + f.getName()).setView(l)
            .setPositiveButton("Save", (d, w) -> {
                String nm = nf.getText().toString().trim();
                if (nm.isEmpty()) { Toast.makeText(host.requireContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                f.setName(nm);
                f.setCals(host.parseD(cf.getText().toString()));
                f.setProtein(host.parseD(pf.getText().toString()));
                f.setCarbs(host.parseD(cbf.getText().toString()));
                f.setFats(host.parseD(ff.getText().toString()));
                host.repo.saveAsync(); host.rebuild();
            })
            .setNeutralButton("Delete", (d, w) ->
                Dialogs.confirmDelete(host.requireContext(), "\"" + f.getName() + "\" from your saved foods", () -> {
                    host.repo.savedFoods.remove(f); host.repo.saveAsync(); host.rebuild();
                }))
            .setNegativeButton("Cancel", null).show();
    }

    /** Plain (Locale.US) number for prefilling edit fields — never a comma decimal. */
    private String fmtD(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v)
                : String.format(java.util.Locale.US, "%.1f", v);
    }

    private LinearLayout loggedFoodRow(Food f) {
        LinearLayout row = new LinearLayout(host.requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.tile_bg);
        row.setPadding(host.dp(16), host.dp(12), host.dp(16), host.dp(12));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, host.dp(6)); row.setLayoutParams(rlp);

        LinearLayout txt = new LinearLayout(host.requireContext());
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView nm = new TextView(host.requireContext());
        nm.setText(f.getName()); nm.setTextColor(host.themeColor(R.attr.colorTextPrimary)); nm.setTextSize(15);
        TextView mc = new TextView(host.requireContext());
        mc.setText(String.format(java.util.Locale.US, "%d kcal · P%d C%d F%d",
                (int) f.getCals(), (int) f.getProtein(), (int) f.getCarbs(), (int) f.getFats()));
        mc.setTextColor(host.themeColor(R.attr.colorTextMuted)); mc.setTextSize(13);
        txt.addView(nm); txt.addView(mc);
        row.addView(txt);

        Dialogs.longPressEditDelete(row, null, () ->
            Dialogs.confirmDelete(host.requireContext(), "\"" + f.getName() + "\" from today's log", () -> {
                List<Food> list = host.repo.foodLog.get(LocalDate.now());
                if (list != null) { list.remove(f); if (list.isEmpty()) host.repo.foodLog.remove(LocalDate.now()); }
                host.repo.saveAsync(); host.rebuild();
            }));
        return row;
    }

    private void addFoodToday(Food f) {
        host.repo.foodLog.computeIfAbsent(LocalDate.now(), d -> new ArrayList<>()).add(f);
        host.repo.saveAsync();
    }

    private void showAddFoodDialog(LinearLayout c) {
        EditText nf = new EditText(host.requireContext()); nf.setHint("Food name");
        EditText cf = new EditText(host.requireContext()); cf.setHint("Calories");
        EditText pf = new EditText(host.requireContext()); pf.setHint("Protein (g)");
        EditText cbf = new EditText(host.requireContext()); cbf.setHint("Carbs (g)");
        EditText ff = new EditText(host.requireContext()); ff.setHint("Fats (g)");
        for (EditText e : new EditText[]{cf, pf, cbf, ff})
            e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        CheckBox saveIt = new CheckBox(host.requireContext());
        saveIt.setText("Save for quick-add next time");
        saveIt.setTextColor(host.themeColor(R.attr.colorTextPrimary));

        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 16, 48, 0);
        l.addView(nf); l.addView(cf); l.addView(pf); l.addView(cbf); l.addView(ff); l.addView(saveIt);

        new AlertDialog.Builder(host.requireContext()).setTitle("Add Custom Food").setView(l)
            .setPositiveButton("Log", (d, w) -> {
                String nm = nf.getText().toString().trim();
                if (nm.isEmpty()) { Toast.makeText(host.requireContext(), "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                Food f = new Food(nm, host.parseD(cf.getText().toString()), host.parseD(pf.getText().toString()),
                        host.parseD(cbf.getText().toString()), host.parseD(ff.getText().toString()));
                addFoodToday(new Food(f.getName(), f.getCals(), f.getProtein(), f.getCarbs(), f.getFats()));
                if (saveIt.isChecked()) host.repo.savedFoods.add(f);
                host.repo.saveAsync(); host.rebuild();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showMacroPreset() {
        String[] goals = {"Weight Loss","Muscle Building","Maintenance","Lean Bulk","Cut (aggressive)"};
        Spinner gs = new Spinner(host.requireContext());
        EditText bw = new EditText(host.requireContext()); bw.setHint("Current bodyweight (" + Units.unit() + ")");
        bw.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (host.repo.startWeightKg > 0) bw.setText(Units.num(host.repo.startWeightKg));
        EditText gw = new EditText(host.requireContext()); gw.setHint("Goal bodyweight (" + Units.unit() + ")");
        gw.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        if (host.repo.goalWeightKg > 0) gw.setText(Units.num(host.repo.goalWeightKg));
        ArrayAdapter<String> ga = new ArrayAdapter<>(host.requireContext(), android.R.layout.simple_spinner_item, goals);
        ga.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); gs.setAdapter(ga);
        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0);
        l.addView(host.tv("Goal")); l.addView(gs);
        // Sex comes from the app-wide setting (asked on first launch).
        TextView sexNote = host.tv("Calculating for: "
                + (host.repo.gender == null || host.repo.gender.isEmpty() ? "Male" : host.repo.gender)
                + "  ·  change in Settings");
        sexNote.setTextSize(11);
        l.addView(sexNote);
        l.addView(host.tv("Current bodyweight (" + Units.unit() + ")")); l.addView(bw);
        l.addView(host.tv("Goal bodyweight (" + Units.unit() + ")")); l.addView(gw);
        new AlertDialog.Builder(host.requireContext()).setTitle("Macro Preset").setView(l)
            .setPositiveButton("Apply", (d, w) -> {
                try {
                    // Inputs are in the display unit; formulas and storage use kg
                    double b = Units.toKg(host.parseD(bw.getText().toString()));
                    if (b <= 0) throw new NumberFormatException();
                    // Capture start (current) and goal weight for the Body goal bar.
                    host.repo.startWeightKg = b;
                    double goalW = Units.toKg(host.parseD(gw.getText().toString()));
                    if (goalW > 0) host.repo.goalWeightKg = goalW;
                    boolean male = !"Female".equals(host.repo.gender);
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
                    host.repo.macroGoals[0]=String.format(java.util.Locale.US, "%.0f",cal); host.repo.macroGoals[1]=String.format(java.util.Locale.US, "%.0f",pro);
                    host.repo.macroGoals[2]=String.format(java.util.Locale.US, "%.0f",carb); host.repo.macroGoals[3]=String.format(java.util.Locale.US, "%.0f",fat);
                    host.repo.saveAsync(); host.rebuild();
                } catch (Exception e) { Toast.makeText(host.requireContext(),"Enter valid bodyweight",Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton("Cancel", null).show();
    }
}
