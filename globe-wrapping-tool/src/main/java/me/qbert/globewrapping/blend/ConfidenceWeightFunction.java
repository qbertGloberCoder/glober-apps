package me.qbert.globewrapping.blend;

/**
 * A swappable falloff curve for how much a source's contribution to a
 * canonical-equirect pixel should be trusted, based on how far that pixel is
 * (angularly) from the source's own sub-satellite point — per
 * globe-unwrapper-requirements.md section 5: "the exact falloff curve
 * (linear vs. non-linear) should be a swappable function, not hardcoded."
 */
@FunctionalInterface
public interface ConfidenceWeightFunction {

    /**
     * @param angularDistanceFromNadirRadians how far (great-circle angle) the target point is from this source's sub-satellite point
     * @param maxVisibleAngleRadians this source's own visibility half-angle (theta_max) — confidence should reach zero at or beyond this
     * @return a non-negative confidence weight
     */
    double weight(double angularDistanceFromNadirRadians, double maxVisibleAngleRadians);
}
