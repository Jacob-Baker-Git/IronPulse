package com.ironpulse.ui;

import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.ironpulse.R;
import com.ironpulse.model.ExerciseData;
import java.util.*;

/** "Assessment" tab of the More screen: scores the weekly split and gives advice. */
class AssessmentTab {
    private final MoreFragment host;

    AssessmentTab(MoreFragment host) { this.host = host; }

    void build(LinearLayout c) {
        host.hdr(c, "Split Assessment");
        TextView result = new TextView(host.requireContext());
        result.setTextColor(host.themeColor(R.attr.colorTextMuted)); result.setTextSize(12);
        result.setTypeface(android.graphics.Typeface.MONOSPACE);
        result.setText("Tap Analyse to assess your weekly split.");
        Button runBtn = host.btn(c, "Analyse My Programme", host.color(R.color.accent));
        runBtn.setOnClickListener(x -> result.setText(runAssessment()));
        host.sp(c, 12); c.addView(result);
    }

    private String runAssessment() {
        if (host.repo.exercises.isEmpty()) return "No exercises found. Add exercises first.";
        Map<String,Integer> setCounts = new HashMap<>();
        Map<java.time.DayOfWeek,List<ExerciseData>> byDay = new LinkedHashMap<>();
        for (java.time.DayOfWeek d : java.time.DayOfWeek.values()) byDay.put(d, new ArrayList<>());
        int totalSets=0, shortRest=0, longRest=0, restCount=0; double avgRest=0;
        for (ExerciseData ex : host.repo.exercises) {
            if (host.repo.restDays.contains(ex.getDayOfWeek())) continue;
            byDay.get(ex.getDayOfWeek()).add(ex);
            String cat = host.repo.classifyExercise(ex.getName());
            int sets = ex.getSets();
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
        String splitType=detectSplit(byDay,trainingDays);
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
        if (legs==0) r.append("✗ No leg work — don't skip leg day!\n"); else r.append("✓ Leg work present\n");
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
        r.append("\n").append(getSplitAdvice(splitType,trainingDays,setsPerSession));
        return r.toString();
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

    private String getSplitAdvice(String split,int days,int sps) {
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
}
