package com.ironpulse.model;
import java.time.LocalDate;
public class BodyWeightEntry {
    private final LocalDate date;
    private final double weightKg;
    public BodyWeightEntry(LocalDate date, double weightKg) { this.date=date; this.weightKg=weightKg; }
    public LocalDate getDate() { return date; }
    public double getWeightKg() { return weightKg; }
}
