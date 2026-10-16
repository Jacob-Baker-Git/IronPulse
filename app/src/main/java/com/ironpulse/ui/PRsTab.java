package com.ironpulse.ui;

import android.app.AlertDialog;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.*;
import com.ironpulse.R;
import com.ironpulse.data.Units;
import com.ironpulse.model.RecordData;
import java.util.Collections;

/** PRs page: best lifts. Tap to update, long-press for reorder / delete. */
class PRsTab {
    private final MoreFragment host;
    private boolean editMode = false;
    private RecyclerView recycler;
    private PRAdapter adapter;
    private ItemTouchHelper touchHelper;

    PRsTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        // Adding a PR is the floating +; tap a lift to update it, hold it for more.
        host.hdr(c, editMode ? "DRAG ≡ TO REORDER  ·  hold a lift when done"
                : "YOUR BEST LIFTS  ·  tap to update, hold for more");

        // RecyclerView for drag support
        recycler = new RecyclerView(host.requireContext());
        recycler.setLayoutManager(new LinearLayoutManager(host.requireContext()));
        recycler.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        recycler.setNestedScrollingEnabled(false);

        adapter = new PRAdapter();
        recycler.setAdapter(adapter);

        touchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView rv,
                    @NonNull RecyclerView.ViewHolder vh, @NonNull RecyclerView.ViewHolder target) {
                int from = vh.getAdapterPosition(), to = target.getAdapterPosition();
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION
                        || from >= host.repo.records.size() || to >= host.repo.records.size()) return false;
                Collections.swap(host.repo.records, from, to);
                adapter.notifyItemMoved(from, to);
                host.repo.saveAsync();
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
            @Override public boolean isLongPressDragEnabled() { return false; } // only handle drag
        });
        touchHelper.attachToRecyclerView(recycler);
        c.addView(recycler);
    }

    private class PRAdapter extends RecyclerView.Adapter<PRAdapter.VH> {
        class VH extends RecyclerView.ViewHolder {
            android.widget.FrameLayout card;
            TextView handle, name, weight, muscleGroup;
            VH(View v) {
                super(v);
                card        = (android.widget.FrameLayout) v;
                handle      = v.findViewWithTag("handle");
                name        = v.findViewWithTag("name");
                weight      = v.findViewWithTag("weight");
                muscleGroup = v.findViewWithTag("muscle");
            }
        }

        @NonNull @Override public VH onCreateViewHolder(@NonNull android.view.ViewGroup p, int t) {
            // Outer card
            android.widget.FrameLayout card = new android.widget.FrameLayout(host.requireContext());
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, 0, 0, host.dp(8));
            card.setLayoutParams(cardLp);
            card.setBackgroundResource(R.drawable.tile_bg);
            card.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14));

            LinearLayout inner = new LinearLayout(host.requireContext());
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.WRAP_CONTENT));

            // Top row: drag handle + name + muscle pill
            LinearLayout topRow = new LinearLayout(host.requireContext());
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            topRow.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

            TextView handle = new TextView(host.requireContext()); handle.setTag("handle");
            handle.setText("≡"); handle.setTextSize(20);
            handle.setTextColor(host.themeColor(R.attr.colorTextMuted));
            handle.setPadding(0, 0, 10, 0);
            handle.setGravity(android.view.Gravity.CENTER_VERTICAL);
            topRow.addView(handle);

            TextView nm = new TextView(host.requireContext()); nm.setTag("name");
            nm.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            nm.setTextColor(host.themeColor(R.attr.colorTextPrimary)); nm.setTextSize(15);
            nm.setTypeface(null, android.graphics.Typeface.BOLD);
            topRow.addView(nm);

            TextView muscle = new TextView(host.requireContext()); muscle.setTag("muscle");
            muscle.setTextSize(12);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            mp.setMargins(8, 0, 0, 0); muscle.setLayoutParams(mp);
            topRow.addView(muscle);
            inner.addView(topRow);

            // Weight row
            TextView wv = new TextView(host.requireContext()); wv.setTag("weight");
            wv.setTextColor(host.themeColor(R.attr.colorTextMuted)); wv.setTextSize(13);
            LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            wlp.setMargins(0, 6, 0, 0); wv.setLayoutParams(wlp);
            inner.addView(wv);

            card.addView(inner);
            return new VH(card);
        }

        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            RecordData r = host.repo.records.get(pos);
            h.name.setText(r.getName());
            String wd;
            if (r.getWeight() == null || r.getWeight().isEmpty()) wd = "— not set";
            else if (r.getWeight().matches(".*[a-zA-Z].*")) wd = r.getWeight();
            else {
                try { wd = Units.fmt(Double.parseDouble(r.getWeight())); }
                catch (Exception e) { wd = r.getWeight(); }
            }
            h.weight.setText("Best: " + wd);

            // Muscle group pill
            String group = host.repo.classifyExercise(r.getName());
            int pillColor;
            switch (group) {
                case "Push": pillColor = host.color(R.color.pill_push); break;
                case "Pull": pillColor = host.color(R.color.pill_pull); break;
                case "Legs": pillColor = host.color(R.color.pill_legs); break;
                case "Arms": pillColor = host.color(R.color.pill_arms); break;
                case "Core": pillColor = host.color(R.color.pill_core); break;
                default:     pillColor = host.color(R.color.pill_other); break;
            }
            h.muscleGroup.setText(group);
            h.muscleGroup.setTextColor(pillColor);

            // Edit mode
            h.handle.setVisibility(editMode ? View.VISIBLE : View.GONE);
            if (editMode) {
                h.handle.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN)
                        touchHelper.startDrag(h);
                    return false;
                });
            }
            h.card.setOnClickListener(x -> showEditPR(r));
            h.card.setOnLongClickListener(v -> {
                android.widget.PopupMenu m = new android.widget.PopupMenu(v.getContext(), h.name);
                m.getMenu().add(0, 1, 0, "Update best");
                m.getMenu().add(0, 2, 1, editMode ? "Done reordering" : "Reorder lifts");
                m.getMenu().add(0, 3, 2, "Delete");
                m.setOnMenuItemClickListener(item -> {
                    switch (item.getItemId()) {
                        case 1: showEditPR(r); break;
                        case 2: editMode = !editMode; host.rebuild(); break;
                        default: Dialogs.confirmDelete(host.requireContext(), "the \"" + r.getName() + "\" PR", () -> {
                            host.repo.records.remove(r); host.repo.saveAsync(); notifyDataSetChanged();
                        });
                    }
                    return true;
                });
                m.show();
                return true;
            });
        }
        @Override public int getItemCount() { return host.repo.records.size(); }
    }

    void showAddPR() {
        EditText nf = new EditText(host.requireContext()); nf.setHint("Lift name");
        EditText wf = new EditText(host.requireContext()); wf.setHint("Best weight (" + Units.unit() + ")");
        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0);
        l.addView(nf); l.addView(wf);
        new AlertDialog.Builder(host.requireContext()).setTitle("Add PR").setView(l)
            .setPositiveButton("Add", (d, w) -> {
                String nm = nf.getText().toString().trim(); if (nm.isEmpty()) return;
                host.repo.records.add(new RecordData(nm, nw(wf.getText().toString())));
                host.repo.saveAsync(); host.rebuild();
            }).setNegativeButton("Cancel", null).show();
    }

    private void showEditPR(RecordData r) {
        EditText wf = new EditText(host.requireContext());
        wf.setHint("Best weight (" + Units.unit() + ")");
        try { wf.setText(Units.num(Double.parseDouble(r.getWeight()))); }
        catch (Exception e) { wf.setText(r.getWeight()); }
        LinearLayout l = new LinearLayout(host.requireContext());
        l.setOrientation(LinearLayout.VERTICAL); l.setPadding(48, 24, 48, 0); l.addView(wf);
        new AlertDialog.Builder(host.requireContext()).setTitle("Edit: " + r.getName()).setView(l)
            .setPositiveButton("Save", (d, w) -> {
                r.setWeight(nw(wf.getText().toString())); host.repo.saveAsync();
                if (adapter != null) adapter.notifyDataSetChanged();
            }).setNegativeButton("Cancel", null).show();
    }

    /** Normalises a PR weight input (current display unit) to the stored kg string. */
    private String nw(String raw) {
        double kg = Units.parseToKg(raw);
        if (kg <= 0) return "";
        double r = Math.round(kg * 100) / 100.0;
        return r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r);
    }
}
