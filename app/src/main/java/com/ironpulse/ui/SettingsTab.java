package com.ironpulse.ui;

import android.app.AlertDialog;
import android.widget.*;
import com.ironpulse.R;
import com.ironpulse.data.Units;
import com.ironpulse.notify.Notifications;
import com.ironpulse.notify.Reminders;

/** "Settings" tab of the More screen: theme, sex, units, reminders, backup, clear. */
class SettingsTab {
    private final MoreFragment host;

    SettingsTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        host.hdr(c, "Settings"); host.sp(c, 8);

        // Light Mode toggle (off by default = dark mode on)
        LinearLayout dmRow = host.row();
        TextView dml = new TextView(host.requireContext());
        dml.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        dml.setText("Light Mode"); dml.setTextColor(host.themeColor(R.attr.colorTextPrimary)); dml.setTextSize(14);
        Switch dms = new Switch(host.requireContext());
        // Switch ON = light mode. repo.darkMode=true means dark is on, so lightMode = !darkMode
        dms.setChecked(!host.repo.darkMode);
        dms.setOnCheckedChangeListener((b, lightModeOn) -> {
            host.repo.darkMode = !lightModeOn; host.repo.saveAsync();
            if (host.getActivity() instanceof MainActivity) {
                ((MainActivity) host.getActivity()).applyTheme(!lightModeOn);
            }
        });
        dmRow.addView(dml); dmRow.addView(dms); c.addView(dmRow); host.sp(c, 6);

        // Sex — universal value used by the macro calculator and body calculations
        LinearLayout sexRow = host.row();
        TextView sexLbl = new TextView(host.requireContext());
        sexLbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        sexLbl.setText("Sex"); sexLbl.setTextColor(host.themeColor(R.attr.colorTextPrimary)); sexLbl.setTextSize(14);
        TextView sexVal = new TextView(host.requireContext());
        sexVal.setText((host.repo.gender == null || host.repo.gender.isEmpty() ? "Not set" : host.repo.gender) + "  ›");
        sexVal.setTextColor(host.color(R.color.accent)); sexVal.setTextSize(13);
        sexRow.addView(sexLbl); sexRow.addView(sexVal);
        sexRow.setOnClickListener(x -> new AlertDialog.Builder(host.requireContext())
            .setTitle("Sex")
            .setItems(new String[]{"Male", "Female"}, (d, which) -> {
                host.repo.gender = which == 0 ? "Male" : "Female";
                host.repo.saveAsync(); host.rebuild();
            }).show());
        c.addView(sexRow); host.sp(c, 6);

        // Weight unit — display-only; kilograms are always what gets stored
        LinearLayout unitRow = host.row();
        TextView unitLbl = new TextView(host.requireContext());
        unitLbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        unitLbl.setText("Show weights in pounds (lbs)");
        unitLbl.setTextColor(host.themeColor(R.attr.colorTextPrimary)); unitLbl.setTextSize(14);
        Switch unitSw = new Switch(host.requireContext());
        unitSw.setChecked(host.repo.useLbs);
        unitSw.setOnCheckedChangeListener((b, lbs) -> {
            host.repo.useLbs = lbs;
            Units.setUseLbs(lbs);
            host.repo.saveAsync();
        });
        unitRow.addView(unitLbl); unitRow.addView(unitSw);
        c.addView(unitRow); host.sp(c, 6);

        // Daily workout reminder — only fires on days with unfinished exercises
        LinearLayout remRow = host.row();
        TextView remLbl = new TextView(host.requireContext());
        remLbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        remLbl.setText("Workout day reminder");
        remLbl.setTextColor(host.themeColor(R.attr.colorTextPrimary)); remLbl.setTextSize(14);
        Switch remSw = new Switch(host.requireContext());
        remSw.setChecked(host.repo.reminderEnabled);
        remSw.setOnCheckedChangeListener((b, on) -> {
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
        remRow.addView(remLbl); remRow.addView(remSw);
        c.addView(remRow); host.sp(c, 6);

        if (host.repo.reminderEnabled) {
            LinearLayout timeRow = host.row();
            TextView timeLbl = new TextView(host.requireContext());
            timeLbl.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            timeLbl.setText("Reminder time");
            timeLbl.setTextColor(host.themeColor(R.attr.colorTextPrimary)); timeLbl.setTextSize(14);
            TextView timeVal = new TextView(host.requireContext());
            timeVal.setText(String.format(java.util.Locale.US, "%02d:%02d  ›",
                    host.repo.reminderHour, host.repo.reminderMinute));
            timeVal.setTextColor(host.color(R.color.accent)); timeVal.setTextSize(13);
            timeRow.addView(timeLbl); timeRow.addView(timeVal);
            timeRow.setOnClickListener(x -> new android.app.TimePickerDialog(host.requireContext(),
                (picker, h, m) -> {
                    host.repo.reminderHour = h; host.repo.reminderMinute = m;
                    host.repo.saveAsync();
                    Reminders.schedule(host.requireContext(), h, m);
                    host.rebuild();
                }, host.repo.reminderHour, host.repo.reminderMinute, true).show());
            c.addView(timeRow); host.sp(c, 6);
        }
        host.sp(c, 6);
        host.infoRow(c, "Data location", "App internal storage");
        host.infoRow(c, "Auto-save", "On every change");
        host.infoRow(c, "Exercise schedule", "Every 7 days from added date"); host.sp(c, 16);

        // Backup — everything lives in internal storage, so give users a way out
        Button exp = host.btn(c, "Export Data (backup)", host.color(R.color.accent));
        exp.setOnClickListener(x -> host.exportData());
        Button imp = host.btn(c, "Import Data (restore backup)", host.color(R.color.accent));
        imp.setOnClickListener(x ->
                host.importPicker.launch(new String[]{"application/zip", "application/octet-stream"}));
        host.sp(c, 8);

        Button clr = host.btn(c, "Clear All Data", host.color(R.color.danger));
        clr.setOnClickListener(x -> new AlertDialog.Builder(host.requireContext())
            .setTitle("Clear All Data")
            .setMessage("Are you sure? This will permanently delete ALL your workout, body, cardio, and PR data.")
            .setPositiveButton("Yes, delete everything", (d, w) ->
                new AlertDialog.Builder(host.requireContext())
                    .setTitle("Final Confirmation")
                    .setMessage("This cannot be undone. Delete ALL data permanently?")
                    .setPositiveButton("Delete Everything", (d2, w2) -> {
                        host.repo.clearAll();
                        Toast.makeText(host.requireContext(), "All data cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null).show()
            )
            .setNegativeButton("Cancel", null).show());
    }
}
