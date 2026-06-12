package com.ironpulse.ui;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ironpulse.R;
import com.ironpulse.data.AppRepository;
import java.time.LocalDate;
import java.util.*;

/**
 * Host for the four "More" tabs (PRs / Macros / Assessment / Settings). Each
 * tab builds its own UI in its own class; this fragment owns tab switching,
 * the shared view helpers, the FAB, and backup import/export plumbing.
 */
public class MoreFragment extends Fragment {
    AppRepository repo;
    private static int tab = 0; // static: survives recreate (e.g. theme toggle)
    private TextView[] tabs;
    private LinearLayout contentRef;
    private FloatingActionButton prFab;

    private PRsTab prsTab;
    private MacrosTab macrosTab;
    private AssessmentTab assessmentTab;
    private AwardsTab awardsTab;
    private SettingsTab settingsTab;

    /** SAF picker for restoring a backup zip. */
    final androidx.activity.result.ActivityResultLauncher<String[]> importPicker =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
            uri -> { if (uri != null) confirmImport(uri); });

    /** POST_NOTIFICATIONS prompt when enabling workout reminders (API 33+). */
    final androidx.activity.result.ActivityResultLauncher<String> notifPermission =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
            granted -> {});

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        prsTab        = new PRsTab(this);
        macrosTab     = new MacrosTab(this);
        assessmentTab = new AssessmentTab(this);
        awardsTab     = new AwardsTab(this);
        settingsTab   = new SettingsTab(this);

        View root = inf.inflate(R.layout.fragment_more, p, false);
        LinearLayout content = root.findViewById(R.id.more_content);
        contentRef = content;
        prFab = root.findViewById(R.id.fab_add_pr);
        prFab.setOnClickListener(x -> prsTab.showAddPR());
        tabs = new TextView[]{
            root.findViewById(R.id.tab_prs),
            root.findViewById(R.id.tab_macros),
            root.findViewById(R.id.tab_assessment),
            root.findViewById(R.id.tab_awards),
            root.findViewById(R.id.tab_settings)
        };
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            tabs[i].setOnClickListener(x -> {
                tab = idx;
                prsTab.resetEditMode();
                macrosTab.resetEditMode();
                updateTabUI();
                buildContent(content);
            });
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
            case 0: prsTab.build(c); break;
            case 1: macrosTab.build(c); break;
            case 2: assessmentTab.build(c); break;
            case 3: awardsTab.build(c); break;
            case 4: settingsTab.build(c); break;
        }
        updateFab();
    }

    /** Re-renders the active tab — tabs call this after they mutate data. */
    void rebuild() {
        if (contentRef != null) buildContent(contentRef);
    }

    // ── Backup / restore ─────────────────────────────────────────────────────

    /** Zips every data file into the cache dir and hands it to the share sheet. */
    void exportData() {
        try {
            java.io.File zip = new java.io.File(requireContext().getCacheDir(),
                    "ironpulse-backup-" + LocalDate.now() + ".zip");
            int added = 0;
            try (java.util.zip.ZipOutputStream zos =
                         new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
                for (String name : AppRepository.DATA_FILES) {
                    java.io.File f = new java.io.File(requireContext().getFilesDir(), name);
                    if (!f.exists()) continue;
                    zos.putNextEntry(new java.util.zip.ZipEntry(name));
                    try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                        byte[] buf = new byte[8192]; int n;
                        while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
                    }
                    zos.closeEntry();
                    added++;
                }
            }
            if (added == 0) {
                Toast.makeText(requireContext(), "Nothing to export yet", Toast.LENGTH_SHORT).show();
                return;
            }
            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    requireContext(), "com.ironpulse.fileprovider", zip);
            android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND)
                    .setType("application/zip")
                    .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(send, "Export IronPulse data"));
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmImport(android.net.Uri uri) {
        Dialogs.confirm(requireContext(), "Import data",
                "Importing replaces your current data with the backup. Continue?",
                "Import", () -> importData(uri));
    }

    private void importData(android.net.Uri uri) {
        Set<String> known = new HashSet<>(Arrays.asList(AppRepository.DATA_FILES));
        int restored = 0;
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                requireContext().getContentResolver().openInputStream(uri))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                // Whitelist match also guards against zip-slip paths
                if (!known.contains(e.getName())) continue;
                java.io.File target = new java.io.File(requireContext().getFilesDir(), e.getName());
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(target)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                }
                restored++;
            }
        } catch (Exception ex) {
            Toast.makeText(requireContext(), "Import failed — not a valid IronPulse backup", Toast.LENGTH_LONG).show();
            return;
        }
        if (restored == 0) {
            Toast.makeText(requireContext(), "No IronPulse data found in that file", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(requireContext(), "Backup imported", Toast.LENGTH_SHORT).show();
        AppRepository.invalidate();
        requireActivity().recreate(); // every screen rebuilds against the imported data
    }

    // ── Shared view helpers (used by the tab classes) ────────────────────────

    void hdr(LinearLayout c, String t) {
        TextView tv = new TextView(requireContext()); tv.setText(t);
        tv.setTextColor(themeColor(R.attr.colorTextMuted)); tv.setTextSize(11);
        tv.setPadding(0, 0, 0, 6); c.addView(tv);
    }

    TextView tv(String text) {
        TextView t = new TextView(requireContext()); t.setText(text); t.setTextColor(themeColor(R.attr.colorTextPrimary)); return t;
    }

    int color(int res) { return requireContext().getResources().getColor(res, requireContext().getTheme()); }

    /** Resolve a theme attribute colour — correct in both light and dark mode */
    int themeColor(int attr) {
        android.util.TypedValue tv = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    void sp(LinearLayout c, int dp) {
        View v = new View(requireContext());
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp));
        c.addView(v);
    }

    Button btn(LinearLayout c, String t, int col) {
        Button b = new Button(requireContext()); b.setText(t);
        if (col == color(R.color.danger)) b.setBackgroundResource(R.drawable.btn_danger);
        else                              b.setBackgroundResource(R.drawable.btn_primary);
        b.setTextColor(color(R.color.white));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 8); b.setLayoutParams(lp);
        c.addView(b); return b;
    }

    /** A horizontal card-style row (caller adds it to the container). */
    LinearLayout row() {
        LinearLayout r = new LinearLayout(requireContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setBackgroundColor(themeColor(R.attr.colorCardBg));
        r.setPadding(20, 14, 20, 14);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, 6); r.setLayoutParams(lp);
        return r;
    }

    void infoRow(LinearLayout c, String label, String value) {
        LinearLayout row = row();
        TextView l = new TextView(requireContext());
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        l.setText(label); l.setTextColor(themeColor(R.attr.colorTextPrimary)); l.setTextSize(13);
        TextView v = new TextView(requireContext()); v.setText(value);
        v.setTextColor(themeColor(R.attr.colorTextMuted)); v.setTextSize(12);
        row.addView(l); row.addView(v); c.addView(row); sp(c, 6);
    }

    int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    double parseD(String s) {
        if (s == null) return 0;
        try { return Double.parseDouble(s.trim().replace(",", ".")); } catch (Exception e) { return 0; }
    }
}
