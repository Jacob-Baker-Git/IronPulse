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
 * Hosts ONE of the page classes (PRs / Food / Programme check / Awards /
 * Settings), picked by argument. Each page builds its own UI in its own class;
 * this fragment owns the title, the shared view helpers, the FAB, and backup
 * import/export plumbing.
 */
public class MoreFragment extends Fragment {
    static final int PRS = 0, FOOD = 1, CHECK = 2, AWARDS = 3, SETTINGS = 4;
    private static final String[] TITLES = {"PRs", "Food", "Programme check", "Awards", "Settings"};

    AppRepository repo;
    private int tab;
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

    static MoreFragment page(int which) {
        MoreFragment f = new MoreFragment();
        Bundle b = new Bundle();
        b.putInt("page", which);
        f.setArguments(b);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup p, @Nullable Bundle s) {
        repo = AppRepository.get(requireContext());
        tab = getArguments() == null ? PRS : getArguments().getInt("page", PRS);
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
        ((TextView) root.findViewById(R.id.page_title)).setText(TITLES[tab]);
        buildContent(content);
        return root;
    }

    /** The floating + only makes sense on the PRs page. */
    private void updateFab() {
        if (prFab != null) prFab.setVisibility(tab == PRS ? View.VISIBLE : View.GONE);
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
        // Stage the whole backup and parse every file BEFORE touching live data,
        // so a broken zip or bad JSON can't leave a mix of old and new.
        java.io.File stage = new java.io.File(requireContext().getCacheDir(), "import");
        deleteDir(stage);
        stage.mkdirs();
        Set<String> restored = new HashSet<>();
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                requireContext().getContentResolver().openInputStream(uri))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                // Whitelist match also guards against zip-slip paths
                if (!known.contains(e.getName())) continue;
                java.io.File staged = new java.io.File(stage, e.getName());
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(staged)) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                }
                try (java.io.Reader r = new java.io.FileReader(staged)) {
                    com.google.gson.JsonParser.parseReader(r); // throws on malformed JSON
                }
                restored.add(e.getName());
            }
        } catch (Exception ex) {
            deleteDir(stage);
            Toast.makeText(requireContext(), "Import failed — not a valid IronPulse backup", Toast.LENGTH_LONG).show();
            return;
        }
        if (restored.isEmpty()) {
            deleteDir(stage);
            Toast.makeText(requireContext(), "No IronPulse data found in that file", Toast.LENGTH_LONG).show();
            return;
        }
        // Let queued saves from the current data finish so none lands on top of the import
        repo.awaitPendingSaves();
        for (String name : AppRepository.DATA_FILES) {
            java.io.File live = new java.io.File(requireContext().getFilesDir(), name);
            live.delete(); // files missing from the backup are cleared too — import replaces
            if (restored.contains(name)) new java.io.File(stage, name).renameTo(live);
        }
        deleteDir(stage);
        Toast.makeText(requireContext(), "Backup imported", Toast.LENGTH_SHORT).show();
        AppRepository.invalidate();
        requireActivity().recreate(); // every screen rebuilds against the imported data
    }

    private static void deleteDir(java.io.File dir) {
        java.io.File[] files = dir.listFiles();
        if (files != null) for (java.io.File f : files) f.delete();
        dir.delete();
    }

    // ── Shared view helpers (used by the tab classes) ────────────────────────

    void hdr(LinearLayout c, String t) {
        TextView tv = new TextView(requireContext()); tv.setText(t);
        tv.setTextColor(themeColor(R.attr.colorTextMuted)); tv.setTextSize(11);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(dp(2), 0, 0, dp(8)); c.addView(tv);
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
        v.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(dp)));
        c.addView(v);
    }

    Button btn(LinearLayout c, String t, int col) {
        Button b = new Button(requireContext()); b.setText(t);
        if (col == color(R.color.danger)) ButtonStyles.delete(b);
        else                              ButtonStyles.log(b);
        b.setPadding(b.getPaddingLeft(), dp(13), b.getPaddingRight(), dp(13));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8)); b.setLayoutParams(lp);
        c.addView(b); return b;
    }

    /** A horizontal card-style row (caller adds it to the container). */
    LinearLayout row() {
        LinearLayout r = new LinearLayout(requireContext());
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.tile_bg);
        r.setPadding(dp(16), dp(14), dp(16), dp(14));
        r.setMinimumHeight(dp(52));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(6)); r.setLayoutParams(lp);
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
