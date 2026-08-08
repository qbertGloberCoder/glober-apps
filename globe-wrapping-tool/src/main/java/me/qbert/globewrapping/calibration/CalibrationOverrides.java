package me.qbert.globewrapping.calibration;

import me.qbert.globewrapping.geometry.DiscCalibration;

/**
 * Ad hoc, per-run overrides of individual {@link DiscCalibration} fields
 * (globe-unwrapper-requirements.md section 4: "Ad hoc one-off overrides on
 * the CLI... should also be possible for calibration tuning"). Each field is
 * nullable/boxed; {@code null} means "leave the base value alone." This is
 * the last step of the resolution order: built-in defaults -&gt; named config
 * profile -&gt; CLI overrides.
 */
public record CalibrationOverrides(
    Double subLatitudeDeg,
    Double subLongitudeDeg,
    Double distanceKm,
    Double nadirX,
    Double nadirY,
    Double radiusX,
    Double radiusY
) {

    private static final CalibrationOverrides NONE =
        new CalibrationOverrides(null, null, null, null, null, null, null);

    public static CalibrationOverrides none() {
        return NONE;
    }

    public boolean isEmpty() {
        return this.equals(NONE);
    }

    /** Returns a new {@link SourceCalibration} with any non-null override fields applied on top of {@code base}. */
    public SourceCalibration applyTo(SourceCalibration base) {
        DiscCalibration d = base.disc();
        DiscCalibration merged = new DiscCalibration(
            pick(subLatitudeDeg, d.subLatitudeDeg()),
            pick(subLongitudeDeg, d.subLongitudeDeg()),
            pick(distanceKm, d.distanceKm()),
            pick(nadirX, d.nadirX()),
            pick(nadirY, d.nadirY()),
            pick(radiusX, d.radiusX()),
            pick(radiusY, d.radiusY()));
        return new SourceCalibration(base.alias(), merged);
    }

    private static double pick(Double override, double baseValue) {
        return override != null ? override : baseValue;
    }
}
