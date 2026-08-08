package me.qbert.globewrapping.calibration;

import java.util.LinkedHashMap;
import java.util.Map;
import me.qbert.globewrapping.geometry.DiscCalibration;

/**
 * Built-in default calibration profiles, seeded from values independently
 * confirmed by two prior codebases: the real GOES-8/Himawari-8 sub-satellite
 * points and example calibration in globe-unwrapper-requirements.md section 4
 * match {@code old_src/App.java}'s hardcoded constants (see
 * {@code old_project_topology.md}) and, for the sub-satellite points
 * specifically, {@code ../old_draw_project}'s
 * {@code dao/CameraConfigurationDao.java} presets (see
 * {@code ../old_draw_project/project_topology.md} section 10) — two
 * independent old codebases agree on the same real-world numbers.
 *
 * <p>These are the base of the resolution order (built-in defaults -&gt; named
 * config profile -&gt; CLI overrides, requirements section 4) — a config file
 * or CLI override for an alias listed here replaces it; new aliases can be
 * added purely via config, no code change required.
 */
public final class DefaultCalibrationProfiles {

    private DefaultCalibrationProfiles() {
    }

    public static Map<String, SourceCalibration> get() {
        Map<String, SourceCalibration> profiles = new LinkedHashMap<>();

        profiles.put("goes8", new SourceCalibration("goes8",
            new DiscCalibration(0.0, -75.2, 35786.0, 0.5, 0.485, 0.4995, 0.4995)));

        profiles.put("himawari", new SourceCalibration("himawari",
            new DiscCalibration(0.0, 140.7, 35786.0, 0.5, 0.5, 0.4992, 0.4992)));

        return profiles;
    }
}
