package me.qbert.globewrapping.calibration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a named alias to its {@link SourceCalibration}, implementing the
 * resolution order from globe-unwrapper-requirements.md section 4: built-in
 * defaults -&gt; named config profile -&gt; CLI overrides. This class covers the
 * first two steps (defaults + config); {@link CalibrationOverrides} covers
 * the third and is applied afterward by the caller (typically the {@code cli}
 * package, once override flag syntax is decided).
 */
public final class CalibrationRegistry {

    private final Map<String, SourceCalibration> profiles;

    private CalibrationRegistry(Map<String, SourceCalibration> profiles) {
        this.profiles = profiles;
    }

    public static CalibrationRegistry withBuiltInDefaults() {
        return new CalibrationRegistry(new LinkedHashMap<>(DefaultCalibrationProfiles.get()));
    }

    /**
     * Returns a new registry with {@code configProfiles} layered on top of this one's —
     * an alias present in both keeps the config version; an alias only in {@code configProfiles}
     * is added; this registry is left unmodified.
     */
    public CalibrationRegistry withConfigProfiles(Map<String, SourceCalibration> configProfiles) {
        Map<String, SourceCalibration> merged = new LinkedHashMap<>(this.profiles);
        merged.putAll(configProfiles);
        return new CalibrationRegistry(merged);
    }

    public Optional<SourceCalibration> get(String alias) {
        return Optional.ofNullable(profiles.get(alias));
    }

    public Map<String, SourceCalibration> all() {
        return Map.copyOf(profiles);
    }

    /** Resolves {@code alias} and applies {@code overrides} on top of it (the third resolution-order step). */
    public SourceCalibration resolve(String alias, CalibrationOverrides overrides) {
        SourceCalibration base = get(alias)
            .orElseThrow(() -> new IllegalArgumentException("Unknown calibration alias: " + alias));
        return overrides.applyTo(base);
    }
}
