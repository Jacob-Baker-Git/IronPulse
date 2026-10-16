package com.ironpulse.ui;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.ironpulse.R;
import com.ironpulse.model.ExerciseData;
import java.util.*;

/** Programme check: scores the weekly split and explains the score. */
class AssessmentTab {
    private final MoreFragment host;

    AssessmentTab(MoreFragment host) { this.host = host; }

    /** A coach note: BAD (red ✗), WARN (amber !) or GOOD (mint ✓). Bad ones are listed first. */
    private static final int BAD = 0, WARN = 1, GOOD = 2;
    private static final class Note {
        final int level; final String text;
        Note(int level, String text) { this.level = level; this.text = text; }
    }

    private static final class Result {
        int score, trainingDays, totalSets, setsPerSession, avgRest;
        String grade, split;
        final Map<String, Integer> groups = new LinkedHashMap<>();
        final List<Note> notes = new ArrayList<>();
        final List<String> advice = new ArrayList<>();
    }

    void build(LinearLayout c) {
        // Runs on open — it's cheap, and a button in front of it was one more step for nothing
        if (host.repo.exercises.isEmpty()) {
            TextView t = text("Add exercises on Today and your weekly programme gets scored here.", 15, R.attr.colorTextMuted);
            c.addView(t);
            return;
        }
        Result r = assess();

        // ── Score card ──
        LinearLayout hero = new LinearLayout(host.requireContext());
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setBackgroundResource(R.drawable.hero_bg);
        hero.setPadding(host.dp(18), host.dp(16), host.dp(18), host.dp(16));
        LinearLayout top = new LinearLayout(host.requireContext());
        top.setGravity(Gravity.BOTTOM);
        TextView score = new TextView(host.requireContext());
        score.setText(String.valueOf(r.score));
        score.setTextSize(44); score.setTypeface(null, Typeface.BOLD);
        score.setTextColor(host.color(R.color.on_accent));
        score.setIncludeFontPadding(false);
        TextView outOf = new TextView(host.requireContext());
        outOf.setText(" / 100");
        outOf.setTextSize(16); outOf.setTypeface(null, Typeface.BOLD);
        outOf.setTextColor(host.color(R.color.on_accent)); outOf.setAlpha(0.7f);
        outOf.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView grade = new TextView(host.requireContext());
        grade.setText(r.grade);
        grade.setTextSize(22); grade.setTypeface(null, Typeface.BOLD);
        grade.setTextColor(host.color(R.color.on_accent));
        top.addView(score); top.addView(outOf); top.addView(grade);
        hero.addView(top);
        TextView sub = new TextView(host.requireContext());
        sub.setText(r.split + "  ·  " + r.trainingDays + (r.trainingDays == 1 ? " day" : " days") + " a week");
        sub.setTextSize(13); sub.setTypeface(null, Typeface.BOLD);
        sub.setTextColor(host.color(R.color.on_accent)); sub.setAlpha(0.8f);
        sub.setPadding(0, host.dp(4), 0, 0);
        hero.addView(sub);
        ProgressBar bar = new ProgressBar(host.requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        bar.setProgressDrawable(androidx.appcompat.content.res.AppCompatResources.getDrawable(host.requireContext(), R.drawable.hero_progress));
        bar.setMax(100); bar.setProgress(r.score);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, host.dp(6));
        bp.topMargin = host.dp(12);
        hero.addView(bar, bp);
        c.addView(hero);
        host.sp(c, 12);

        // ── Four numbers ──
        String[][] stats = {
            {r.trainingDays + "/7", "training days"},
            {String.valueOf(r.totalSets), "sets a week"},
            {String.valueOf(r.setsPerSession), "sets a session"},
            {r.avgRest + " s", "average rest"}};
        for (int row = 0; row < 2; row++) {
            LinearLayout line = new LinearLayout(host.requireContext());
            for (int col = 0; col < 2; col++) {
                String[] s = stats[row * 2 + col];
                LinearLayout tile = new LinearLayout(host.requireContext());
                tile.setOrientation(LinearLayout.VERTICAL);
                tile.setBackgroundResource(R.drawable.tile_bg);
                tile.setPadding(host.dp(16), host.dp(12), host.dp(12), host.dp(12));
                TextView v = text(s[0], 20, R.attr.colorTextPrimary);
                v.setTypeface(null, Typeface.BOLD);
                tile.addView(v);
                tile.addView(text(s[1], 12, R.attr.colorTextMuted));
                LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                if (col == 0) tp.rightMargin = host.dp(4); else tp.leftMargin = host.dp(4);
                line.addView(tile, tp);
            }
            c.addView(line);
            host.sp(c, 8);
        }

        // ── Muscle groups ──
        host.sp(c, 12);
        host.hdr(c, "SETS PER MUSCLE GROUP");
        LinearLayout groups = new LinearLayout(host.requireContext());
        groups.setOrientation(LinearLayout.VERTICAL);
        groups.setBackgroundResource(R.drawable.tile_bg);
        groups.setPadding(host.dp(16), host.dp(8), host.dp(16), host.dp(14));
        int max = 1;
        for (int v : r.groups.values()) max = Math.max(max, v);
        for (Map.Entry<String, Integer> g : r.groups.entrySet()) {
            LinearLayout line = new LinearLayout(host.requireContext());
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(0, host.dp(6), 0, 0);
            TextView name = text(g.getKey(), 14, R.attr.colorTextPrimary);
            name.setLayoutParams(new LinearLayout.LayoutParams(host.dp(52), LinearLayout.LayoutParams.WRAP_CONTENT));
            ProgressBar pb = new ProgressBar(host.requireContext(), null, android.R.attr.progressBarStyleHorizontal);
            pb.setMax(max); pb.setProgress(g.getValue());
            pb.setProgressTintList(ColorStateList.valueOf(groupColor(g.getKey())));
            pb.setProgressBackgroundTintList(ColorStateList.valueOf(host.themeColor(R.attr.colorBorder)));
            TextView n = text(g.getValue() + "", 14, R.attr.colorTextMuted);
            n.setGravity(Gravity.END);
            n.setLayoutParams(new LinearLayout.LayoutParams(host.dp(36), LinearLayout.LayoutParams.WRAP_CONTENT));
            line.addView(name);
            line.addView(pb, new LinearLayout.LayoutParams(0, host.dp(8), 1f));
            line.addView(n);
            groups.addView(line);
        }
        c.addView(groups);

        // ── Coach notes, problems first ──
        host.sp(c, 20);
        host.hdr(c, "COACH NOTES");
        r.notes.sort(Comparator.comparingInt(n -> n.level));
        for (Note note : r.notes) {
            LinearLayout line = new LinearLayout(host.requireContext());
            line.setPadding(0, host.dp(5), 0, host.dp(5));
            TextView mark = new TextView(host.requireContext());
            mark.setText(note.level == GOOD ? "✓" : note.level == WARN ? "!" : "✗");
            mark.setTypeface(null, Typeface.BOLD);
            mark.setTextSize(15);
            mark.setTextColor(host.color(note.level == GOOD ? R.color.accent
                    : note.level == WARN ? R.color.accent_2 : R.color.danger));
            mark.setLayoutParams(new LinearLayout.LayoutParams(host.dp(24), LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView t = text(note.text, 15, note.level == GOOD ? R.attr.colorTextMuted : R.attr.colorTextPrimary);
            t.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            line.addView(mark); line.addView(t);
            c.addView(line);
        }

        // ── Split advice ──
        host.sp(c, 20);
        host.hdr(c, "ABOUT YOUR SPLIT");
        TextView adv = text(String.join("\n", r.advice), 15, R.attr.colorTextPrimary);
        adv.setLineSpacing(0, 1.2f);
        adv.setBackgroundResource(R.drawable.tile_bg);
        adv.setPadding(host.dp(16), host.dp(14), host.dp(16), host.dp(14));
        c.addView(adv);
    }

    private TextView text(String s, int sp, int colorAttr) {
        TextView t = new TextView(host.requireContext());
        t.setText(s); t.setTextSize(sp);
        t.setTextColor(host.themeColor(colorAttr));
        return t;
    }

    private int groupColor(String g) {
        switch (g) {
            case "Push": return host.color(R.color.pill_push);
            case "Pull": return host.color(R.color.pill_pull);
            case "Legs": return host.color(R.color.pill_legs);
            case "Arms": return host.color(R.color.pill_arms);
            case "Core": return host.color(R.color.pill_core);
            default:     return host.color(R.color.pill_other);
        }
    }

    private Result assess() {
        Result r = new Result();
        Map<String,Integer> setCounts = new HashMap<>();
        Map<java.time.DayOfWeek,List<ExerciseData>> byDay = new LinkedHashMap<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) byDay.put(d, new ArrayList<>());
        int totalSets=0, shortRest=0, longRest=0, restCount=0; double avgRest=0;
        for (ExerciseData ex : host.repo.exercises) {
            String cat = host.repo.classifyExercise(ex.getName());
            int sets = ex.getSets();
            boolean countedAnyDay = false;
            // An exercise contributes weekly volume once per scheduled (non-rest) day
            for (java.time.DayOfWeek d : ex.getDays()) {
                if (host.repo.restDays.contains(d)) continue;
                byDay.get(d).add(ex);
                setCounts.merge(cat,sets,Integer::sum);
                totalSets+=sets;
                countedAnyDay = true;
            }
            if (!countedAnyDay) continue;
            avgRest+=ex.getRestSeconds(); restCount++;
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

        r.score=Math.min(100,score);
        r.grade=r.score>=90?"A+":r.score>=80?"A":r.score>=70?"B+":r.score>=60?"B":r.score>=50?"C+":r.score>=40?"C":"D";
        r.split=detectSplit(byDay,trainingDays);
        r.trainingDays=trainingDays; r.totalSets=totalSets; r.setsPerSession=setsPerSession;
        r.avgRest=(int)Math.round(avgRest);
        for (String g : new String[]{"Push","Pull","Legs","Arms","Core"}) {
            int s = setCounts.getOrDefault(g, 0);
            if (s > 0) r.groups.put(g, s);
        }

        List<Note> n = r.notes;
        n.add(push==0 ? new Note(BAD, "No push work — add bench, press or dips") : new Note(GOOD, "Push work present"));
        n.add(pull==0 ? new Note(BAD, "No pull work — add rows or pulldowns") : new Note(GOOD, "Pull work present"));
        n.add(legs==0 ? new Note(BAD, "No leg work — don't skip leg day") : new Note(GOOD, "Leg work present"));
        if (core==0) n.add(new Note(WARN, "No core work — add planks or abs"));
        if (push>0&&pull>0) {
            double ratio=(double)Math.min(push,pull)/Math.max(push,pull);
            if (ratio>=0.8) n.add(new Note(GOOD, "Push and pull are balanced (" + push + ":" + pull + ")"));
            else n.add(new Note(WARN, (push>pull ? "Push" : "Pull") + "-heavy (" + push + " vs " + pull
                    + " sets) — even them out to avoid imbalance"));
        }
        if (trainingDays<3) n.add(new Note(WARN, "Low frequency (" + trainingDays + " days) — aim for 3–5"));
        else if (trainingDays<=5) n.add(new Note(GOOD, "Good training frequency (" + trainingDays + " days a week)"));
        else n.add(new Note(WARN, "Very high frequency (" + trainingDays + " days) — watch recovery"));
        if (setsPerSession<8) n.add(new Note(WARN, "Low volume (" + setsPerSession + " sets a session) — aim for 10–20"));
        else if (setsPerSession<=20) n.add(new Note(GOOD, "Good session volume (" + setsPerSession + " sets)"));
        else n.add(new Note(WARN, "High volume (" + setsPerSession + " sets a session) — monitor recovery"));
        if (shortRest>0) n.add(new Note(WARN, shortRest + " exercise" + (shortRest==1?"":"s") + " with very short rest (under 45 s)"));
        if (longRest>0) n.add(new Note(GOOD, longRest + " exercise" + (longRest==1?"":"s") + " with long rest (over 3 min) — fine for big lifts"));
        if (avgRest>=60&&avgRest<=180) n.add(new Note(GOOD, "Rest times are sensible (" + r.avgRest + " s average)"));

        r.advice.addAll(splitAdvice(r.split, trainingDays, setsPerSession));
        return r;
    }

    private String detectSplit(Map<java.time.DayOfWeek,List<ExerciseData>> byDay, int trainingDays) {
        if (trainingDays==0) return "None";
        long fbDays=byDay.values().stream().filter(day->{
            boolean hp=day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Push"));
            boolean hl=day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Legs"));
            boolean hr=day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Pull"));
            return hp&&hl&&hr;
        }).count();
        if (fbDays>=trainingDays-1&&trainingDays>0) return "Full Body";
        long pushDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Push"))&&
            day.stream().noneMatch(e->host.repo.classifyExercise(e.getName()).equals("Pull"))).count();
        long pullDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Pull"))&&
            day.stream().noneMatch(e->host.repo.classifyExercise(e.getName()).equals("Push"))).count();
        if (pushDays>=1&&pullDays>=1&&trainingDays>=3) return "Push/Pull/Legs";
        long upperDays=byDay.values().stream().filter(day->{
            boolean upper=day.stream().anyMatch(e->{String cl=host.repo.classifyExercise(e.getName());return cl.equals("Push")||cl.equals("Pull")||cl.equals("Arms");});
            boolean noLegs=day.stream().noneMatch(e->host.repo.classifyExercise(e.getName()).equals("Legs"));
            return upper&&noLegs&&!day.isEmpty();
        }).count();
        long lowerDays=byDay.values().stream().filter(day->
            day.stream().anyMatch(e->host.repo.classifyExercise(e.getName()).equals("Legs"))).count();
        if (upperDays>=1&&lowerDays>=1) return "Upper/Lower";
        return trainingDays<=3?"Bro Split / Custom":"Custom";
    }

    private List<String> splitAdvice(String split, int days, int sps) {
        List<String> a = new ArrayList<>();
        switch (split) {
            case "Push/Pull/Legs":
                a.add("PPL is excellent for intermediate lifters and up.");
                if (days==6) a.add("6-day PPL is the optimal frequency for muscle growth.");
                else if (days==3) a.add("3-day PPL hits each muscle once a week. A 6-day version doubles that.");
                else a.add("Consider 6-day PPL for maximum frequency."); break;
            case "Upper/Lower":
                a.add("Upper/Lower is highly efficient.");
                if (days==4) a.add("4 days is the ideal balance of frequency and recovery.");
                else a.add("Aim for 4 days to hit each muscle twice a week."); break;
            case "Full Body":
                a.add("Full body suits beginners and strength athletes.");
                if (days>=3) a.add(days + " full-body sessions give a good training frequency.");
                if (sps>20) a.add("Sessions may run long — consider supersets."); break;
            case "Bro Split / Custom":
                a.add("A bro split trains each muscle once a week, with lots of volume per session.");
                a.add("Upper/Lower would hit each muscle twice a week for more growth."); break;
            default:
                a.add("Custom split.");
                a.add("Aim to hit each muscle twice a week for the best growth.");
        }
        a.add("");
        a.add("Log your sets on each exercise to track progressive overload.");
        return a;
    }
}
