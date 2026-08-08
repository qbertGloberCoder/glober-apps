package me.qbert.globewrapping.calibration;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import me.qbert.globewrapping.geometry.DiscCalibration;
import org.yaml.snakeyaml.Yaml;

/**
 * Parses a calibration config file into named {@link SourceCalibration}
 * profiles. Format decision (globe-unwrapper-requirements.md section 7, open
 * item): <b>YAML</b>, via SnakeYAML — chosen over TOML during Step 0 because
 * it's a single, actively-maintained, ubiquitous dependency and its
 * nested-mapping syntax maps directly onto the alias -&gt; fields structure
 * (see {@code tasks.md}'s Step 0 execution log for the full rationale).
 *
 * <p>Expected shape (matching requirements section 4's config sketch, with
 * YAML's {@code :} instead of TOML's {@code =}):
 * <pre>
 * goes8:
 *   sub_lat: 0.0
 *   sub_lon: -75.2
 *   distance: 35786
 *   nadir_x: 0.5
 *   nadir_y: 0.485
 *   radius_x: 0.4995
 *   radius_y: 0.4995
 * </pre>
 */
public final class CalibrationConfigLoader {

    private CalibrationConfigLoader() {
    }

    public static Map<String, SourceCalibration> load(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read calibration config: " + path, e);
        }
    }

    public static Map<String, SourceCalibration> load(InputStream in) {
        Yaml yaml = new Yaml();
        Object root = yaml.load(in);
        if (root == null) {
            return Map.of();
        }
        if (!(root instanceof Map<?, ?> aliasMap)) {
            throw new CalibrationConfigException(
                "Top-level calibration config must be a mapping of alias -> fields, got: " + root.getClass());
        }

        Map<String, SourceCalibration> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : aliasMap.entrySet()) {
            String alias = String.valueOf(entry.getKey());
            result.put(alias, parseProfile(alias, entry.getValue()));
        }
        return result;
    }

    private static SourceCalibration parseProfile(String alias, Object fieldsObj) {
        if (!(fieldsObj instanceof Map<?, ?> fields)) {
            throw new CalibrationConfigException(
                "Calibration profile '" + alias + "' must be a mapping of fields, got: "
                    + (fieldsObj == null ? "null" : fieldsObj.getClass()));
        }

        DiscCalibration disc = new DiscCalibration(
            requireDouble(fields, alias, "sub_lat"),
            requireDouble(fields, alias, "sub_lon"),
            requireDouble(fields, alias, "distance"),
            requireDouble(fields, alias, "nadir_x"),
            requireDouble(fields, alias, "nadir_y"),
            requireDouble(fields, alias, "radius_x"),
            requireDouble(fields, alias, "radius_y"));
        return new SourceCalibration(alias, disc);
    }

    private static double requireDouble(Map<?, ?> fields, String alias, String key) {
        Object value = fields.get(key);
        if (value == null) {
            throw new CalibrationConfigException(
                "Calibration profile '" + alias + "' is missing required field '" + key + "'");
        }
        if (!(value instanceof Number number)) {
            throw new CalibrationConfigException(
                "Calibration profile '" + alias + "' field '" + key + "' must be numeric, got: " + value);
        }
        return number.doubleValue();
    }
}
