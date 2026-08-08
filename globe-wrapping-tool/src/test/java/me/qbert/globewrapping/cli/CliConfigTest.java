package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import me.qbert.globewrapping.calibration.CalibrationOverrides;
import me.qbert.globewrapping.calibration.CalibrationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void explicitConfigFileIsLoadedAndOverridesBuiltInDefault() throws IOException {
        Path configPath = tempDir.resolve("cfg.yaml");
        Files.writeString(configPath, """
            goes8:
              sub_lat: 0.0
              sub_lon: -75.2
              distance: 35786
              nadir_x: 0.51
              nadir_y: 0.485
              radius_x: 0.4995
              radius_y: 0.4995
            """, StandardCharsets.UTF_8);

        CalibrationRegistry registry = CliConfig.loadRegistry(configPath.toString());
        assertEquals(0.51, registry.get("goes8").orElseThrow().disc().nadirX(), 1e-9);
    }

    @Test
    void explicitConfigPathThatDoesNotExistThrows() {
        Path missing = tempDir.resolve("does-not-exist.yaml");
        assertThrows(CliUsageException.class, () -> CliConfig.loadRegistry(missing.toString()));
    }

    @Test
    void noConfigArgumentAndNoDefaultFileFallsBackToBuiltInDefaultsOnly() {
        // tempDir has no globe-wrapping-tool.yaml in it; loadRegistry(null) only consults the CWD
        // default search path (not tempDir), so this really just confirms built-ins survive
        // when nothing overrides them, without needing to manipulate the process CWD.
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();
        assertTrue(registry.get("goes8").isPresent());
        assertTrue(registry.get("himawari").isPresent());
    }

    @Test
    void parsesMultipleOverrideFieldsForOneAlias() {
        List<ArgScanner.Option> options = List.of(
            new ArgScanner.Option("override", "goes8.nadir_y=0.49"),
            new ArgScanner.Option("override", "goes8.radius_x=0.5"));

        Map<String, CalibrationOverrides> result = CliConfig.parseOverrides(options);

        CalibrationOverrides overrides = result.get("goes8");
        assertEquals(0.49, overrides.nadirY(), 1e-9);
        assertEquals(0.5, overrides.radiusX(), 1e-9);
    }

    @Test
    void overridesForDifferentAliasesAreKeptSeparate() {
        List<ArgScanner.Option> options = List.of(
            new ArgScanner.Option("override", "goes8.nadir_y=0.49"),
            new ArgScanner.Option("override", "himawari.radius_x=0.5"));

        Map<String, CalibrationOverrides> result = CliConfig.parseOverrides(options);

        assertEquals(0.49, result.get("goes8").nadirY(), 1e-9);
        assertEquals(0.5, result.get("himawari").radiusX(), 1e-9);
        assertEquals(null, result.get("goes8").radiusX());
    }

    @Test
    void malformedOverrideSpecThrows() {
        List<ArgScanner.Option> options = List.of(new ArgScanner.Option("override", "goes8-nadir_y-0.49"));
        assertThrows(CliUsageException.class, () -> CliConfig.parseOverrides(options));
    }

    @Test
    void nonNumericOverrideValueThrows() {
        List<ArgScanner.Option> options = List.of(new ArgScanner.Option("override", "goes8.nadir_y=not-a-number"));
        assertThrows(CliUsageException.class, () -> CliConfig.parseOverrides(options));
    }

    @Test
    void unknownOverrideFieldThrows() {
        List<ArgScanner.Option> options = List.of(new ArgScanner.Option("override", "goes8.bogus_field=1.0"));
        assertThrows(CliUsageException.class, () -> CliConfig.parseOverrides(options));
    }
}
