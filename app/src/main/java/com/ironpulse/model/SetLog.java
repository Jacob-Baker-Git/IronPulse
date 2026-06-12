package com.ironpulse.model;
import java.time.LocalDate;
public class SetLog {
    private final LocalDate date;
    private String exerciseName; // mutable so exercise renames carry history along
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
    public void setExerciseName(String n) { this.exerciseName=n; }
    public int getSetNumber() { return setNumber; }
    public double getWeightKg() { return weightKg; }
    public int getReps() { return reps; }
    public boolean isBodyweight() { return isBodyweight; }
    public double volume() { return isBodyweight?reps:weightKg*reps; }
    public String formatDisplay() {
        if (isBodyweight) return "Set "+setNumber+": BW × "+reps+" reps";
        return "Set "+setNumber+": "+com.ironpulse.data.Units.fmt(weightKg)+" × "+reps+" reps";
    }
}
