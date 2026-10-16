package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.widget.EditText;
import java.time.LocalDate;

/** Shared confirmation dialogs so every destructive action asks first. */
final class Dialogs {
    private Dialogs() {}

    /**
     * Turns a date EditText into a tap-to-pick field backed by a DatePickerDialog.
     * The field keeps showing/holding ISO yyyy-MM-dd so existing parse code works.
     */
    static void attachDatePicker(EditText field) {
        field.setFocusable(false);
        field.setClickable(true);
        field.setOnClickListener(v -> {
            LocalDate current;
            try { current = LocalDate.parse(field.getText().toString().trim()); }
            catch (Exception e) { current = LocalDate.now(); }
            new android.app.DatePickerDialog(field.getContext(), (picker, y, m, d) ->
                    field.setText(LocalDate.of(y, m + 1, d).toString()),
                    current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()
            ).show();
        });
    }

    /** "Are you sure you want to delete <what>?" → runs onYes only if confirmed. */
    static void confirmDelete(Context c, String what, Runnable onYes) {
        new AlertDialog.Builder(c)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete " + what + "?")
            .setPositiveButton("Delete", (d, w) -> onYes.run())
            .setNegativeButton("Cancel", null)
            .show();
    }

    /**
     * Replaces the old per-screen "Edit" mode: long-press any row for Edit / Delete.
     * {@code onEdit} may be null for rows that can only be deleted.
     */
    static void longPressEditDelete(android.view.View row, Runnable onEdit, Runnable onDelete) {
        row.setOnLongClickListener(v -> {
            android.widget.PopupMenu m = new android.widget.PopupMenu(v.getContext(), v);
            if (onEdit != null) m.getMenu().add(0, 1, 0, "Edit");
            m.getMenu().add(0, 2, 1, "Delete");
            m.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == 1) onEdit.run(); else onDelete.run();
                return true;
            });
            m.show();
            return true;
        });
    }

    /** Generic confirm with a custom message + positive label. */
    static void confirm(Context c, String title, String message, String positive, Runnable onYes) {
        new AlertDialog.Builder(c)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive, (d, w) -> onYes.run())
            .setNegativeButton("Cancel", null)
            .show();
    }
}
