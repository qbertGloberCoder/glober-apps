package me.qbert.globewrapping.calibration;

import me.qbert.globewrapping.geometry.DiscCalibration;

/**
 * A named calibration profile: an alias (e.g. {@code goes8}) bundling the
 * {@link DiscCalibration} geometry so a CLI invocation only needs to
 * reference the alias plus an image path (globe-unwrapper-requirements.md
 * section 4).
 */
public record SourceCalibration(String alias, DiscCalibration disc) {

    public SourceCalibration {
        if (alias == null || alias.isBlank()) {
            throw new IllegalArgumentException("alias must not be blank");
        }
        if (disc == null) {
            throw new IllegalArgumentException("disc must not be null");
        }
    }
}
