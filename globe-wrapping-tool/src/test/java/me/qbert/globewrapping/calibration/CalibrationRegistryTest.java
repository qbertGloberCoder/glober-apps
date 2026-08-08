package me.qbert.globewrapping.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import me.qbert.globewrapping.geometry.DiscCalibration;
import org.junit.jupiter.api.Test;

class CalibrationRegistryTest {

    @Test
    void builtInDefaultsIncludeGoes8AndHimawari() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();

        SourceCalibration goes8 = registry.get("goes8").orElseThrow();
        assertEquals(-75.2, goes8.disc().subLongitudeDeg(), 1e-9);
        assertEquals(35786.0, goes8.disc().distanceKm(), 1e-9);

        SourceCalibration himawari = registry.get("himawari").orElseThrow();
        assertEquals(140.7, himawari.disc().subLongitudeDeg(), 1e-9);
    }

    @Test
    void unknownAliasIsAbsent() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        assertTrue(registry.get("does-not-exist").isEmpty());
    }

    @Test
    void configProfileOverridesBuiltInOfSameAlias() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        SourceCalibration tunedGoes8 = new SourceCalibration("goes8",
            new DiscCalibration(0.0, -75.2, 35786.0, 0.51, 0.49, 0.5, 0.5));

        CalibrationRegistry merged = registry.withConfigProfiles(Map.of("goes8", tunedGoes8));

        assertEquals(0.51, merged.get("goes8").orElseThrow().disc().nadirX(), 1e-9);
        // original registry is unmodified
        assertEquals(0.5, registry.get("goes8").orElseThrow().disc().nadirX(), 1e-9);
    }

    @Test
    void configProfileAddsNewAlias() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        SourceCalibration custom = new SourceCalibration("mysat",
            new DiscCalibration(1.0, 10.0, 35786.0, 0.5, 0.5, 0.49, 0.49));

        CalibrationRegistry merged = registry.withConfigProfiles(Map.of("mysat", custom));

        assertTrue(merged.get("mysat").isPresent());
        assertTrue(merged.get("goes8").isPresent(), "existing built-in aliases should survive the merge");
    }

    @Test
    void resolveAppliesOverridesOnTopOfBase() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        CalibrationOverrides overrides = new CalibrationOverrides(null, null, null, 0.52, null, null, null);

        SourceCalibration resolved = registry.resolve("goes8", overrides);

        assertEquals(0.52, resolved.disc().nadirX(), 1e-9);
        assertEquals(0.485, resolved.disc().nadirY(), 1e-9, "unspecified fields keep the base value");
    }

    @Test
    void resolveWithNoOverridesReturnsBaseUnchanged() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        SourceCalibration resolved = registry.resolve("goes8", CalibrationOverrides.none());
        assertEquals(registry.get("goes8").orElseThrow().disc(), resolved.disc());
    }

    @Test
    void resolveOfUnknownAliasThrows() {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        assertThrows(IllegalArgumentException.class, () -> registry.resolve("nope", CalibrationOverrides.none()));
    }
}
