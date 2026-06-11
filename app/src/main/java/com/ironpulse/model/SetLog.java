package com.ironpulse.model;
import java.time.LocalDate;
public class SetLog {
    private final LocalDate date;
    private final String exerciseName;
    private final int setNumber;
    private final double weightKg;
    private final int reps;
    private final boolean isBodyweight;
    public SetLog(LocalDate date, String exerciseName, int setNumber, double weightKg, int reps, boolean isBodyweight) {
        this.date=date; this.exerciseName=exerciseName; this.setNumber=setNumber;
        this.weightKg=weightKg; this.reps=reps; this.isBodyweight=isBodyweight;
    }
    public LocalDate getDate() { return date; }
    public String getExerciseName() { return exerciseName; }
    public int getSetNumber() { return setNumber; }
    public double getWeightKg() { return weightKg; }
    public int getReps() { return reps; }
    public boolean isBodyweight() { return isBodyweight; }
    public double volume() { return isBodyweight?reps:weightKg*reps; }
    public String formatDisplay() {
        if (isBodyweight) return "Set "+setNumber+": BW × "+reps+" reps";
        String w=weightKg==Math.floor(weightKg)?String.valueOf((int)weightKg):String.valueOf(weightKg);
        return "Set "+setNumber+": "+w+"kg × "+reps+" reps";
    }
}
