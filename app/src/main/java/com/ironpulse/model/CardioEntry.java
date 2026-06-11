package com.ironpulse.model;
import java.time.LocalDate;
public class CardioEntry {
    private final LocalDate date;
    private final String type;
    private final double distanceKm;
    private final int durationMinutes;
    private final String notes;
    public CardioEntry(LocalDate date, String type, double distanceKm, int durationMinutes, String notes) {
        this.date=date; this.type=type; this.distanceKm=distanceKm; this.durationMinutes=durationMinutes; this.notes=notes;
    }
    public LocalDate getDate() { return date; }
    public String getType() { return type; }
    public double getDistanceKm() { return distanceKm; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getNotes() { return notes; }
    public double getPaceMinPerKm() { return distanceKm>0&&durationMinutes>0?(double)durationMinutes/distanceKm:0; }
}
