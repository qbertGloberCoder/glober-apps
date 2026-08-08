package me.qbert.globewrapping.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import me.qbert.globewrapping.geometry.DiscCalibration;
import org.junit.jupiter.api.Test;

class CalibrationOverridesTest {

    private final SourceCalibration base = new SourceCalibration("goes8",
        new DiscCalibration(0.0, -75.2, 35786.0, 0.5, 0.485, 0.4995, 0.4995));

    @Test
    void noneLeavesEveryFieldUnchanged() {
        SourceCalibration result = CalibrationOverrides.none().applyTo(base);
        assertEquals(base.disc(), result.disc());
        assertEquals(base.alias(), result.alias());
    }

    @Test
    void isEmptyIsTrueOnlyForNone() {
        assertTrue(CalibrationOverrides.none().isEmpty());
        CalibrationOverrides withOneField = new CalibrationOverrides(null, null, null, 0.5, null, null, null);
        assertTrue(!withOneField.isEmpty());
    }

    @Test
    void applyingOverridesOnlyReplacesNonNullFields() {
        CalibrationOverrides overrides =
            new CalibrationOverrides(null, null, 40000.0, null, null, 0.5, null);

        SourceCalibration result = overrides.applyTo(base);

        assertEquals(40000.0, result.disc().distanceKm(), 1e-9);
        assertEquals(0.5, result.disc().radiusX(), 1e-9);
        // untouched fields keep their base values
        assertEquals(base.disc().subLatitudeDeg(), result.disc().subLatitudeDeg(), 1e-9);
        assertEquals(base.disc().subLongitudeDeg(), result.disc().subLongitudeDeg(), 1e-9);
        assertEquals(base.disc().nadirX(), result.disc().nadirX(), 1e-9);
        assertEquals(base.disc().nadirY(), result.disc().nadirY(), 1e-9);
        assertEquals(base.disc().radiusY(), result.disc().radiusY(), 1e-9);
    }

    @Test
    void allFieldsCanBeOverriddenAtOnce() {
        CalibrationOverrides overrides = new CalibrationOverrides(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        DiscCalibration result = overrides.applyTo(base).disc();

        assertEquals(1.0, result.subLatitudeDeg(), 1e-9);
        assertEquals(2.0, result.subLongitudeDeg(), 1e-9);
        assertEquals(3.0, result.distanceKm(), 1e-9);
        assertEquals(4.0, result.nadirX(), 1e-9);
        assertEquals(5.0, result.nadirY(), 1e-9);
        assertEquals(6.0, result.radiusX(), 1e-9);
        assertEquals(7.0, result.radiusY(), 1e-9);
    }
}
