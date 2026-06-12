package com.ironpulse.model;
import java.time.LocalDate;

/** A body measurement (waist, chest, …) — centimetres stored, like kg for weight. */
public class MeasurementEntry {
    public static final String[] TYPES = {"Waist","Chest","Arms","Hips","Thighs","Neck"};

    private final LocalDate date;
    private final String type;
    private final double valueCm;

    public MeasurementEntry(LocalDate date, String type, double valueCm) {
        this.date = date; this.type = type; this.valueCm = valueCm;
    }
    public LocalDate getDate() { return date; }
    public String getType() { return type; }
    public double getValueCm() { return valueCm; }
}
