package me.qbert.globewrapping.calibration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalibrationConfigLoaderTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesTheRequirementsDocExampleConfig() {
        String config = """
            goes8:
              sub_lat: 0.0
              sub_lon: -75.2
              distance: 35786
              nadir_x: 0.5
              nadir_y: 0.485
              radius_x: 0.4995
              radius_y: 0.4995

            himawari:
              sub_lat: 0.0
              sub_lon: 140.7
              distance: 35786
              nadir_x: 0.5
              nadir_y: 0.5
              radius_x: 0.4992
              radius_y: 0.4992
            """;

        Map<String, SourceCalibration> profiles = CalibrationConfigLoader.load(yaml(config));

        assertEquals(2, profiles.size());
        SourceCalibration goes8 = profiles.get("goes8");
        assertEquals(-75.2, goes8.disc().subLongitudeDeg(), 1e-9);
        assertEquals(35786.0, goes8.disc().distanceKm(), 1e-9, "integer YAML values must coerce to double");
        assertEquals(0.4995, goes8.disc().radiusX(), 1e-9);

        SourceCalibration himawari = profiles.get("himawari");
        assertEquals(140.7, himawari.disc().subLongitudeDeg(), 1e-9);
    }

    @Test
    void emptyDocumentYieldsNoProfiles() {
        assertTrue(CalibrationConfigLoader.load(yaml("")).isEmpty());
    }

    @Test
    void missingFieldThrowsWithAliasAndFieldNamed() {
        String config = """
            goes8:
              sub_lat: 0.0
              sub_lon: -75.2
              distance: 35786
              nadir_x: 0.5
              nadir_y: 0.485
              radius_x: 0.4995
            """;
        CalibrationConfigException exception = assertThrows(
            CalibrationConfigException.class, () -> CalibrationConfigLoader.load(yaml(config)));
        assertTrue(exception.getMessage().contains("goes8"));
        assertTrue(exception.getMessage().contains("radius_y"));
    }

    @Test
    void nonNumericFieldThrows() {
        String config = """
            goes8:
              sub_lat: "north-ish"
              sub_lon: -75.2
              distance: 35786
              nadir_x: 0.5
              nadir_y: 0.485
              radius_x: 0.4995
              radius_y: 0.4995
            """;
        assertThrows(CalibrationConfigException.class, () -> CalibrationConfigLoader.load(yaml(config)));
    }

    @Test
    void nonMappingTopLevelThrows() {
        assertThrows(CalibrationConfigException.class, () -> CalibrationConfigLoader.load(yaml("- just\n- a\n- list\n")));
    }
}
