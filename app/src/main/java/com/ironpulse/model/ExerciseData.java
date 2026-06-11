package com.ironpulse.model;
import java.time.LocalDate;
import java.time.DayOfWeek;
public class ExerciseData {
    private final String name;
    private final LocalDate addedDate;
    private double weightKg;
    private boolean isBodyweight;
    private String reps;
    private int restSeconds;
    public ExerciseData(String name, String weightStr, String reps, int restSeconds, LocalDate addedDate) {
        this.name=name; this.reps=reps; this.restSeconds=restSeconds; this.addedDate=addedDate;
        parseWeight(weightStr);
    }
    public ExerciseData(String name, String weightStr, String reps, int restSeconds) { this(name,weightStr,reps,restSeconds,LocalDate.now()); }
    private void parseWeight(String w) {
        if (w==null||w.trim().isEmpty()||w.equalsIgnoreCase("bw")||w.equalsIgnoreCase("bodyweight")||w.trim().equals("0")) { isBodyweight=true; weightKg=0; return; }
        isBodyweight=false;
        String t=w.trim().replaceAll("(?i)\\s*(kgs?|lbs?)\\s*$","").trim();
        if (t.matches("\\d+,\\d+")) t=t.replace(",",".");
        t=t.replaceAll("[^0-9.]","");
        try { weightKg=Double.parseDouble(t); } catch(Exception e) { isBodyweight=true; weightKg=0; }
    }
    public String getName() { return name; }
    public double getWeightKg() { return weightKg; }
    public boolean isBodyweight() { return isBodyweight; }
    public String getReps() { return reps; }
    public int getRestSeconds() { return restSeconds; }
    public LocalDate getAddedDate() { return addedDate; }
    public DayOfWeek getDayOfWeek() { return addedDate.getDayOfWeek(); }
    public String getWeight() { return isBodyweight?"":weightKg==Math.floor(weightKg)?String.valueOf((int)weightKg):String.valueOf(weightKg); }
    public void setWeight(String w) { parseWeight(w); }
    public void setReps(String r) { this.reps=r; }
    public void setRestSeconds(int s) { this.restSeconds=s; }
    public void setWeightKg(double kg) { this.weightKg=kg; this.isBodyweight=false; }
}
