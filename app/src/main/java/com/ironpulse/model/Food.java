package com.ironpulse.model;

/**
 * A food item with its macro contribution. Used both as a saved/preset food
 * and as a logged entry (a snapshot — editing a saved food never rewrites history).
 */
public class Food {
    private String name;
    private double cals;
    private double protein;
    private double carbs;
    private double fats;

    public Food(String name, double cals, double protein, double carbs, double fats) {
        this.name = name; this.cals = cals; this.protein = protein; this.carbs = carbs; this.fats = fats;
    }

    public String getName()    { return name; }
    public double getCals()    { return cals; }
    public double getProtein() { return protein; }
    public double getCarbs()   { return carbs; }
    public double getFats()    { return fats; }

    public void setName(String n)    { this.name = n; }
    public void setCals(double v)    { this.cals = v; }
    public void setProtein(double v) { this.protein = v; }
    public void setCarbs(double v)   { this.carbs = v; }
    public void setFats(double v)    { this.fats = v; }

    /** macro index: 0=cals 1=protein 2=carbs 3=fats */
    public double macro(int i) {
        switch (i) { case 0: return cals; case 1: return protein; case 2: return carbs; default: return fats; }
    }
}
