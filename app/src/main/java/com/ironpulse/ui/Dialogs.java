package com.ironpulse.ui;

import android.app.AlertDialog;
import android.content.Context;

/** Shared confirmation dialogs so every destructive action asks first. */
final class Dialogs {
    private Dialogs() {}

    /** "Are you sure you want to delete <what>?" → runs onYes only if confirmed. */
    static void confirmDelete(Context c, String what, Runnable onYes) {
        new AlertDialog.Builder(c)
            .setTitle("Delete")
            .setMessage("Are you sure you want to delete " + what + "?")
            .setPositiveButton("Delete", (d, w) -> onYes.run())
            .setNegativeButton("Cancel", null)
            .show();
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
