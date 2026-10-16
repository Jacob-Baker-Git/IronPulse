package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.ironpulse.R;
import com.ironpulse.data.Units;
import com.ironpulse.notify.Notifications;
import com.ironpulse.notify.Reminders;
import java.util.*;

/** Settings page: look & units, bottom bar, reminders, your data. */
class SettingsTab {
    private final MoreFragment host;

    SettingsTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        host.hdr(c, "LOOK & UNITS");
        // Switch ON = light mode. repo.darkMode=true means dark is on, so lightMode = !darkMode
        switchRow(c, "Light mode", !host.repo.darkMode, lightModeOn -> {
            host.repo.darkMode = !lightModeOn; host.repo.saveAsync();
            if (host.getActivity() instanceof MainActivity)
                ((MainActivity) host.getActivity()).applyTheme(!lightModeOn);
        });
        // Weight unit — display-only; kilograms are always what gets stored
        switchRow(c, "Weights in pounds (lbs)", host.repo.useLbs, lbs -> {
            host.repo.useLbs = lbs;
            Units.setUseLbs(lbs);
            host.repo.saveAsync();
        });
        // Sex — universal value used by the macro calculator and body calculations
        linkRow(c, "Sex", host.repo.gender == null || host.repo.gender.isEmpty() ? "Not set" : host.repo.gender,
            () -> new AlertDialog.Builder(host.requireContext())
                .setTitle("Sex")
                .setItems(new String[]{"Male", "Female"}, (d, which) -> {
                    host.repo.gender = which == 0 ? "Male" : "Female";
                    host.repo.saveAsync(); host.rebuild();
                }).show());

        host.sp(c, 14);
        host.hdr(c, "BOTTOM BAR");
        linkRow(c, "Choose and order bar items", host.repo.navBar.size() + " pinned", this::showBarEditor);

        host.sp(c, 14);
        host.hdr(c, "REMINDERS");
        // Daily workout reminder — only fires on days with unfinished exercises
        switchRow(c, "Workout day reminder", host.repo.reminderEnabled, on -> {
            host.repo.reminderEnabled = on;
            host.repo.saveAsync();
            if (on) {
                if (android.os.Build.VERSION.SDK_INT >= 33
                        && !Notifications.canPost(host.requireContext()))
                    host.notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                Reminders.schedule(host.requireContext(),
                        host.repo.reminderHour, host.repo.reminderMinute);
            } else {
                Reminders.cancel(host.requireContext());
            }
            host.rebuild(); // show/hide the time row
        });
        if (host.repo.reminderEnabled) {
            linkRow(c, "Reminder time", String.format(java.util.Locale.US, "%02d:%02d",
                    host.repo.reminderHour, host.repo.reminderMinute),
                () -> new android.app.TimePickerDialog(host.requireContext(),
                    (picker, h, m) -> {
                        host.repo.reminderHour = h; host.repo.reminderMinute = m;
                        host.repo.saveAsync();
                        Reminders.schedule(host.requireContext(), h, m);
                        host.rebuild();
                    }, host.repo.reminderHour, host.repo.reminderMinute, true).show());
        }

        host.sp(c, 14);
        host.hdr(c, "YOUR DATA · stored only on this phone");
        linkRow(c, "Export backup", "", host::exportData);
        linkRow(c, "Restore from backup", "", () ->
                host.importPicker.launch(new String[]{"application/zip", "application/octet-stream"}));
        TextView clear = linkRow(c, "Clear all data", "", () -> new AlertDialog.Builder(host.requireContext())
            .setTitle("Clear All Data")
            .setMessage("Are you sure? This will permanently delete ALL your workout, body, cardio, and PR data.")
            .setPositiveButton("Yes, delete everything", (d, w) ->
                new AlertDialog.Builder(host.requireContext())
                    .setTitle("Final Confirmation")
                    .setMessage("This cannot be undone. Delete ALL data permanently?")
                    .setPositiveButton("Delete Everything", (d2, w2) -> {
                        host.repo.clearAll();
                        Toast.makeText(host.requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                        host.requireActivity().recreate(); // every screen rebuilds against the empty data
                    })
                    .setNegativeButton("Cancel", null).show()
            )
            .setNegativeButton("Cancel", null).show());
        clear.setTextColor(host.color(R.color.danger));
    }

    // ── Rows ─────────────────────────────────────────────────────────────────

    private interface OnToggle { void set(boolean on); }

    private void switchRow(LinearLayout c, String label, boolean checked, OnToggle onToggle) {
        LinearLayout row = host.row();
        TextView l = label(label);
        Switch s = new Switch(host.requireContext());
        s.setChecked(checked);
        s.setOnCheckedChangeListener((b, on) -> onToggle.set(on));
        row.setOnClickListener(v -> s.toggle());
        row.addView(l); row.addView(s); c.addView(row);
    }

    /** A tappable row with an optional value and a chevron. Returns the label. */
    private TextView linkRow(LinearLayout c, String label, String value, Runnable onTap) {
        LinearLayout row = host.row();
        TextView l = label(label);
        TextView v = new TextView(host.requireContext());
        v.setText(value.isEmpty() ? "›" : value + "  ›");
        v.setTextColor(host.themeColor(R.attr.colorTextMuted)); v.setTextSize(14);
        row.setOnClickListener(x -> onTap.run());
        row.addView(l); row.addView(v); c.addView(row);
        return l;
    }

    private TextView label(String text) {
        TextView l = new TextView(host.requireContext());
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.setText(text); l.setTextColor(host.themeColor(R.attr.colorTextPrimary)); l.setTextSize(15);
        return l;
    }

    // ── Bottom bar editor ────────────────────────────────────────────────────

    /** Every feature with an on/off switch, drag ≡ to reorder. Pinned ones first, in bar order. */
    private void showBarEditor() {
        List<Features.Feature> order = new ArrayList<>();
        Set<String> on = new HashSet<>();
        for (String id : host.repo.navBar) {
            Features.Feature f = Features.byId(id);
            if (f != null && !order.contains(f)) { order.add(f); on.add(id); }
        }
        for (Features.Feature f : Features.ALL) if (!order.contains(f)) order.add(f);

        RecyclerView rv = new RecyclerView(host.requireContext());
        rv.setLayoutManager(new LinearLayoutManager(host.requireContext()));
        rv.setPadding(host.dp(8), host.dp(8), host.dp(8), 0);
        final ItemTouchHelper[] drag = new ItemTouchHelper[1];
        RecyclerView.Adapter<RecyclerView.ViewHolder> adapter = new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
                LinearLayout r = new LinearLayout(p.getContext());
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(host.dp(12), host.dp(10), host.dp(12), host.dp(10));
                r.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerView.ViewHolder(r) {};
            }
            @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int pos) {
                LinearLayout r = (LinearLayout) h.itemView;
                r.removeAllViews();
                Features.Feature f = order.get(pos);
                ImageView handle = icon(R.drawable.ic_drag, host.themeColor(R.attr.colorTextMuted));
                handle.setContentDescription("Drag to reorder");
                handle.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) drag[0].startDrag(h);
                    return false;
                });
                r.addView(handle);
                ImageView ic = icon(f.icon, host.themeColor(R.attr.colorTextPrimary));
                ((LinearLayout.LayoutParams) ic.getLayoutParams()).leftMargin = host.dp(14);
                r.addView(ic);
                TextView l = label(f.label);
                l.setPadding(host.dp(12), 0, 0, 0);
                r.addView(l);
                Switch s = new Switch(host.requireContext());
                s.setChecked(on.contains(f.id));
                s.setOnCheckedChangeListener((b, checked) -> { if (checked) on.add(f.id); else on.remove(f.id); });
                r.addView(s);
            }
            @Override public int getItemCount() { return order.size(); }
        };
        rv.setAdapter(adapter);
        drag[0] = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override public boolean onMove(@NonNull RecyclerView r, @NonNull RecyclerView.ViewHolder a, @NonNull RecyclerView.ViewHolder b) {
                int from = a.getAdapterPosition(), to = b.getAdapterPosition();
                Collections.swap(order, from, to);
                adapter.notifyItemMoved(from, to);
                return true;
            }
            @Override public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int dir) {}
            @Override public boolean isLongPressDragEnabled() { return false; }
        });
        drag[0].attachToRecyclerView(rv);

        new AlertDialog.Builder(host.requireContext())
            .setTitle("Bottom bar")
            .setMessage("Switch on what you want on the bar. ••• (everything + settings) is always last.")
            .setView(rv)
            .setPositiveButton("Done", (d, w) -> {
                host.repo.navBar.clear();
                for (Features.Feature f : order) if (on.contains(f.id)) host.repo.navBar.add(f.id);
                host.repo.saveAsync();
                if (host.getActivity() instanceof MainActivity) ((MainActivity) host.getActivity()).rebuildBar();
                host.rebuild();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private ImageView icon(int res, int tint) {
        ImageView iv = new ImageView(host.requireContext());
        iv.setImageResource(res);
        iv.setImageTintList(ColorStateList.valueOf(tint));
        iv.setLayoutParams(new LinearLayout.LayoutParams(host.dp(22), host.dp(22)));
        return iv;
    }
}
