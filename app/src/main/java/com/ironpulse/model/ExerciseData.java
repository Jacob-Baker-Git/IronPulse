package com.ironpulse.model;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.*;

public class ExerciseData {
    /** Stable identity — survives renames; assigned on creation or migrated on load. */
    private String id;
    private String name;
    private final LocalDate addedDate;
    private double weightKg;
    private boolean isBodyweight;
    /** Canonical schedule: sets × repsPerSet. */
    private int sets;
    private int repsPerSet;
    /** Legacy "3x10" form — kept in sync so old backups stay importable. */
    private String reps;
    private int restSeconds;
    /** Weekdays this exercise recurs on (DayOfWeek names). Defaults to addedDate's day. */
    private List<String> days;

    public ExerciseData(String name, String weightStr, String reps, int restSeconds, LocalDate addedDate) {
        this.id=UUID.randomUUID().toString();
        this.name=name; this.restSeconds=restSeconds; this.addedDate=addedDate;
        this.days=new ArrayList<>(Collections.singletonList(addedDate.getDayOfWeek().name()));
        setReps(reps);
        parseWeight(weightStr);
    }
    public ExerciseData(String name, String weightStr, String reps, int restSeconds) { this(name,weightStr,reps,restSeconds,LocalDate.now()); }

    /**
     * Repairs instances deserialized from older JSON: assigns a missing id and
     * derives the int sets/reps from the legacy string form.
     */
    public void normalize() {
        if (id == null || id.isEmpty()) id = UUID.randomUUID().toString();
        if (sets <= 0 || repsPerSet <= 0) parseRepsString(reps);
        reps = sets + "x" + repsPerSet;
        if (days == null || days.isEmpty())
            days = new ArrayList<>(Collections.singletonList(addedDate.getDayOfWeek().name()));
    }

    private void parseRepsString(String r) {
        sets = 3; repsPerSet = 10;
        if (r == null) return;
        String[] p = r.toLowerCase().split("x");
        try { if (p.length > 0 && !p[0].trim().isEmpty()) sets = Math.max(1, (int) Double.parseDouble(p[0].trim().replaceAll("[^0-9.]",""))); } catch(Exception ignored) {}
        try {
            if (p.length > 1) repsPerSet = Math.max(1, (int) Double.parseDouble(p[1].trim().replaceAll("[^0-9.]","")));
            else if (p.length == 1) { repsPerSet = Math.max(1, (int) Double.parseDouble(p[0].trim().replaceAll("[^0-9.]",""))); sets = 1; }
        } catch(Exception ignored) {}
    }

    private void parseWeight(String w) {
        if (w==null||w.trim().isEmpty()||w.equalsIgnoreCase("bw")||w.equalsIgnoreCase("bodyweight")||w.trim().equals("0")) { isBodyweight=true; weightKg=0; return; }
        isBodyweight=false;
        String t=w.trim().replaceAll("(?i)\\s*(kgs?|lbs?)\\s*$","").trim();
        if (t.matches("\\d+,\\d+")) t=t.replace(",",".");
        t=t.replaceAll("[^0-9.]","");
        try { weightKg=Double.parseDouble(t); } catch(Exception e) { isBodyweight=true; weightKg=0; }
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String n) { this.name=n; }
    public double getWeightKg() { return weightKg; }
    public boolean isBodyweight() { return isBodyweight; }
    public int getSets() { return Math.max(1, sets); }
    public int getRepsPerSet() { return Math.max(1, repsPerSet); }
    public String getReps() { return getSets() + "x" + getRepsPerSet(); }
    public int getRestSeconds() { return restSeconds; }
    public LocalDate getAddedDate() { return addedDate; }
    public DayOfWeek getDayOfWeek() { return addedDate.getDayOfWeek(); }

    /** All weekdays this exercise recurs on (always at least one). */
    public Set<DayOfWeek> getDays() {
        Set<DayOfWeek> out = EnumSet.noneOf(DayOfWeek.class);
        if (days != null)
            for (String d : days) { try { out.add(DayOfWeek.valueOf(d)); } catch (Exception ignored) {} }
        if (out.isEmpty()) out.add(addedDate.getDayOfWeek());
        return out;
    }

    public void setDays(Collection<DayOfWeek> newDays) {
        if (newDays == null || newDays.isEmpty()) return;
        Set<DayOfWeek> ordered = EnumSet.copyOf(newDays);
        days = new ArrayList<>();
        for (DayOfWeek d : ordered) days.add(d.name());
    }

    /** "Mon, Thu" — for delete prompts and card labels. */
    public String daysLabel() {
        StringBuilder sb = new StringBuilder();
        for (DayOfWeek d : getDays()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
        }
        return sb.toString();
    }
    public String getWeight() { return isBodyweight?"":weightKg==Math.floor(weightKg)?String.valueOf((int)weightKg):String.valueOf(weightKg); }
    public void setWeight(String w) { parseWeight(w); }
    public void setReps(String r) { parseRepsString(r); reps = sets + "x" + repsPerSet; }
    public void setRestSeconds(int s) { this.restSeconds=s; }
    public void setWeightKg(double kg) { this.weightKg=kg; this.isBodyweight=false; }
}
