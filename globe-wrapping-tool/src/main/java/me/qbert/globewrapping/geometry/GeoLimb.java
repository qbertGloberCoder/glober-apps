package me.qbert.globewrapping.geometry;

/**
 * Pure geometry relating a nadir-pointing camera at some altitude above Earth's
 * surface to the ground-angle / view-angle correspondence used by both the
 * forward ({@link SatelliteDiscProjection}, satellite disc -> globe) and
 * inverse ({@link PerspectiveObserverProjection}, globe -> synthetic observer)
 * projections. All angles are in radians unless the method name says "Deg".
 *
 * <p>Notation, consistent throughout this class: {@code Re} = Earth radius,
 * {@code h} = altitude above the surface (the "distance" field of
 * calibration/observer data per globe-unwrapper-requirements.md section 4),
 * {@code D = Re + h} = distance from Earth's center to the camera,
 * {@code theta} = angular distance (at Earth's center) between nadir and a
 * target point, {@code alpha} = the corresponding view angle measured at the
 * camera between its nadir direction and the direction to that same target.
 *
 * <p>The theta&lt;-&gt;alpha relationship is derived from the law of sines on
 * the triangle formed by Earth's center, the camera, and the target point:
 * <pre>
 *   sin(alpha) = Re * sin(theta) / slantRange(theta)
 *   theta      = arcsin(D * sin(alpha) / Re) - alpha
 * </pre>
 */
public final class GeoLimb {

    private GeoLimb() {
    }

    /** Earth-center angle theta_max at which a target point sits exactly on the visible limb. */
    public static double visibleHalfAngleRadians(double altitudeKm) {
        double d = distanceFromCenter(altitudeKm);
        return Math.acos(EarthModel.EARTH_RADIUS_KM / d);
    }

    /** Camera-side view angle alpha_max corresponding to the limb (the half field of view needed to see the whole disc). */
    public static double maxViewAngleRadians(double altitudeKm) {
        double d = distanceFromCenter(altitudeKm);
        return Math.asin(EarthModel.EARTH_RADIUS_KM / d);
    }

    /** Straight-line distance from the camera to a target at Earth-center angle theta. */
    public static double slantRangeKm(double altitudeKm, double thetaRadians) {
        double re = EarthModel.EARTH_RADIUS_KM;
        double d = distanceFromCenter(altitudeKm);
        double value = d * d + re * re - 2.0 * d * re * Math.cos(thetaRadians);
        return Math.sqrt(Math.max(0.0, value));
    }

    /** View angle alpha (from the camera's nadir direction) corresponding to ground angle theta. */
    public static double viewAngleFromGroundAngle(double altitudeKm, double thetaRadians) {
        double thetaMax = visibleHalfAngleRadians(altitudeKm);
        requireInRange(thetaRadians, thetaMax, "thetaRadians");

        double re = EarthModel.EARTH_RADIUS_KM;
        double slantRange = slantRangeKm(altitudeKm, thetaRadians);
        if (slantRange <= 0.0) {
            return 0.0;
        }
        double sinAlpha = clamp(re * Math.sin(thetaRadians) / slantRange);
        return Math.asin(sinAlpha);
    }

    /** Ground angle theta corresponding to view angle alpha (from the camera's nadir direction). */
    public static double groundAngleFromViewAngle(double altitudeKm, double alphaRadians) {
        double alphaMax = maxViewAngleRadians(altitudeKm);
        requireInRange(alphaRadians, alphaMax, "alphaRadians");

        double re = EarthModel.EARTH_RADIUS_KM;
        double d = distanceFromCenter(altitudeKm);
        double sinValue = clamp(d * Math.sin(alphaRadians) / re);
        return Math.asin(sinValue) - alphaRadians;
    }

    private static double distanceFromCenter(double altitudeKm) {
        if (altitudeKm <= 0.0) {
            throw new IllegalArgumentException("altitudeKm must be positive: " + altitudeKm);
        }
        return EarthModel.EARTH_RADIUS_KM + altitudeKm;
    }

    private static void requireInRange(double value, double max, String name) {
        double tolerance = 1e-9;
        if (value < -tolerance || value > max + tolerance) {
            throw new IllegalArgumentException(name + " " + value + " outside visible range [0, " + max + "]");
        }
    }

    private static double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }
}
