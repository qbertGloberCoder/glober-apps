package me.qbert.globewrapping.blend;

/**
 * Default {@link ConfidenceWeightFunction}: confidence falls off linearly
 * from 1.0 at a source's own nadir to 0.0 at its visibility limb
 * (globe-unwrapper-requirements.md section 5: "closer to this source's own
 * nadir -&gt; more trusted... confirmed from an earlier working version: this
 * produced seamless composites in practice"). This is one implementation of
 * the swappable {@link ConfidenceWeightFunction} interface, not a hardcoded
 * formula — non-linear alternatives can be added later without touching
 * {@link SourceAccumulator}.
 */
public final class LinearFalloffConfidenceWeight implements ConfidenceWeightFunction {

    @Override
    public double weight(double angularDistanceFromNadirRadians, double maxVisibleAngleRadians) {
        if (maxVisibleAngleRadians <= 0.0) {
            throw new IllegalArgumentException("maxVisibleAngleRadians must be positive: " + maxVisibleAngleRadians);
        }
        double fraction = angularDistanceFromNadirRadians / maxVisibleAngleRadians;
        return Math.max(0.0, 1.0 - fraction);
    }
}
