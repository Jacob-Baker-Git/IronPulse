package com.ironpulse.data;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnitsTest {

    @After public void resetUnits() { Units.setUseLbs(false); }

    @Test public void kgModePassesThrough() {
        Units.setUseLbs(false);
        assertEquals(80.0, Units.parseToKg("80"), 0.001);
        assertEquals("80 kg", Units.fmt(80));
        assertEquals(82.5, Units.parseToKg("82,5"), 0.001);
    }

    @Test public void lbsModeConvertsInputToKg() {
        Units.setUseLbs(true);
        assertEquals(100.0, Units.parseToKg("220.46"), 0.01);
        assertEquals("lbs", Units.unit());
    }

    @Test public void explicitSuffixOverridesCurrentUnit() {
        Units.setUseLbs(true);
        // "80kg" typed while in lbs mode still means 80 kg
        assertEquals(80.0, Units.parseToKg("80kg"), 0.001);
        Units.setUseLbs(false);
        // "220 lbs" typed while in kg mode still means ~100 kg
        assertEquals(99.79, Units.parseToKg("220 lbs"), 0.01);
    }

    @Test public void displayRoundTripIsStable() {
        Units.setUseLbs(true);
        double kg = 84.0;
        double display = Units.toDisplay(kg);
        assertEquals(kg, Units.toKg(display), 0.0001);
    }

    @Test public void bodyweightInputsPassThroughUnchanged() {
        Units.setUseLbs(true);
        assertEquals("BW", Units.inputToKgString("BW"));
        assertEquals("", Units.inputToKgString(""));
    }

    @Test public void inputToKgStringConvertsNumbers() {
        Units.setUseLbs(true);
        // 220 lb ≈ 99.79 kg
        assertEquals(99.79, Double.parseDouble(Units.inputToKgString("220")), 0.01);
        Units.setUseLbs(false);
        assertEquals("60", Units.inputToKgString("60"));
    }
}
