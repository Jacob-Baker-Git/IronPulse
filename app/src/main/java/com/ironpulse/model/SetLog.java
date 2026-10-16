package com.ironpulse.model;
import java.time.LocalDate;
public class SetLog {
    private final LocalDate date;
    private String exerciseName; // mutable so exercise renames carry history along
    /** Owning exercise's stable id. Null on logs saved before ids were recorded. */
    private String exerciseId;
    private final int setNumber;
    private final double weightKg;
    private final int reps;
    private final boolean isBodyweight;
    public SetLog(LocalDate date, String exerciseId, String exerciseName, int setNumber, double weightKg, int reps, boolean isBodyweight) {
        this.date=date; this.exerciseId=exerciseId; this.exerciseName=exerciseName; this.setNumber=setNumber;
        this.weightKg=weightKg; this.reps=reps; this.isBodyweight=isBodyweight;
    }
    public LocalDate getDate() { return date; }
    public String getExerciseName() { return exerciseName; }
    public void setExerciseName(String n) { this.exerciseName=n; }
    public String getExerciseId() { return exerciseId; }
    public void setExerciseId(String id) { this.exerciseId=id; }
    /** Id match when recorded (two same-named exercises stay apart), name for legacy logs. */
    public boolean belongsTo(ExerciseData ex) {
        return exerciseId != null ? exerciseId.equals(ex.getId()) : exerciseName.equals(ex.getName());
    }
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
