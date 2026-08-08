package me.qbert.globewrapping.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.qbert.globewrapping.calibration.CalibrationConfigLoader;
import me.qbert.globewrapping.calibration.CalibrationOverrides;
import me.qbert.globewrapping.calibration.CalibrationRegistry;
import me.qbert.globewrapping.calibration.SourceCalibration;

/**
 * Resolves the two globe-unwrapper-requirements.md section 7 open items left
 * to CLI-design time:
 *
 * <ul>
 *   <li><b>{@code --config <path>} and its default search location</b>: if
 *       {@code --config} isn't given, a {@code globe-wrapping-tool.yaml} in the
 *       current working directory is used if present; if it isn't, the tool
 *       proceeds on built-in defaults alone (not an error) — config is
 *       optional. If {@code --config} <em>is</em> given but the file doesn't
 *       exist, that's a hard {@link CliUsageException}.</li>
 *   <li><b>Ad hoc per-run calibration overrides</b>: repeatable
 *       {@code --override <alias>.<field>=<value>} flags (field names match
 *       {@link CalibrationConfigLoader}'s YAML keys: {@code sub_lat},
 *       {@code sub_lon}, {@code distance}, {@code nadir_x}, {@code nadir_y},
 *       {@code radius_x}, {@code radius_y}), e.g.
 *       {@code --override goes8.nadir_y=0.49 --override goes8.radius_x=0.5}.</li>
 * </ul>
 */
final class CliConfig {

    static final String DEFAULT_CONFIG_FILENAME = "globe-wrapping-tool.yaml";

    private CliConfig() {
    }

    static CalibrationRegistry loadRegistry(String explicitConfigPath) {
        CalibrationRegistry registry = CalibrationRegistry.withBuiltInDefaults();

        Path configPath;
        if (explicitConfigPath != null) {
            configPath = Path.of(explicitConfigPath);
            if (!Files.exists(configPath)) {
                throw new CliUsageException("Config file not found: " + configPath);
            }
        } else {
            configPath = Path.of(DEFAULT_CONFIG_FILENAME);
            if (!Files.exists(configPath)) {
                return registry;
            }
        }

        Map<String, SourceCalibration> configProfiles = CalibrationConfigLoader.load(configPath);
        return registry.withConfigProfiles(configProfiles);
    }

    static Map<String, CalibrationOverrides> parseOverrides(List<ArgScanner.Option> overrideOptions) {
        Map<String, Map<String, Double>> fieldsByAlias = new LinkedHashMap<>();

        for (ArgScanner.Option option : overrideOptions) {
            String spec = option.value();
            int dot = spec.indexOf('.');
            int eq = spec.indexOf('=');
            if (dot < 0 || eq < 0 || eq < dot) {
                throw new CliUsageException(
                    "Invalid --override value '" + spec + "', expected <alias>.<field>=<value>");
            }
            String alias = spec.substring(0, dot);
            String field = spec.substring(dot + 1, eq);
            String valueText = spec.substring(eq + 1);

            double value;
            try {
                value = Double.parseDouble(valueText);
            } catch (NumberFormatException e) {
                throw new CliUsageException("Invalid --override value for '" + field + "': " + valueText);
            }

            fieldsByAlias.computeIfAbsent(alias, unused -> new LinkedHashMap<>()).put(field, value);
        }

        Map<String, CalibrationOverrides> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> entry : fieldsByAlias.entrySet()) {
            result.put(entry.getKey(), toOverrides(entry.getValue()));
        }
        return result;
    }

    private static CalibrationOverrides toOverrides(Map<String, Double> fields) {
        for (String field : fields.keySet()) {
            if (!KNOWN_FIELDS.contains(field)) {
                throw new CliUsageException(
                    "Unknown --override field '" + field + "', expected one of " + KNOWN_FIELDS);
            }
        }
        return new CalibrationOverrides(
            fields.get("sub_lat"), fields.get("sub_lon"), fields.get("distance"),
            fields.get("nadir_x"), fields.get("nadir_y"), fields.get("radius_x"), fields.get("radius_y"));
    }

    private static final List<String> KNOWN_FIELDS =
        List.of("sub_lat", "sub_lon", "distance", "nadir_x", "nadir_y", "radius_x", "radius_y");
}
