package me.qbert.globewrapping.geometry;

import java.util.Optional;

/**
 * Forward projection used by the {@code unwrap} stage: given one source's
 * {@link DiscCalibration} and a target point on the globe, determines whether
 * that point is visible from this source and, if so, the fractional pixel
 * position within the source image to sample.
 *
 * <p>Models a physically-grounded, finite-altitude, rectilinear
 * ("non-distorting", per globe-unwrapper-requirements.md section 2) lens: the
 * image-plane radius from nadir is proportional to {@code tan(alpha)}, where
 * {@code alpha} is the camera-side view angle (see {@link GeoLimb}),
 * normalized so the visible limb ({@code alpha = alpha_max}) lands exactly at
 * the calibrated {@code radiusX}/{@code radiusY}. This is the generalized,
 * per-source-parameterized replacement for {@code old_src}'s hardcoded
 * {@code App.mapData} sphere ray-trace — deliberately not an orthographic
 * projection (see {@link PerspectiveObserverProjection} for the matching
 * inverse used by {@code wrap}).
 */
public final class SatelliteDiscProjection {

    private SatelliteDiscProjection() {
    }

    /**
     * @return the fractional (0..1-ish, relative to the source image's own
     *     width/height) pixel to sample, or empty if {@code target} is beyond
     *     this source's visible limb.
     */
    public static Optional<PixelPoint> project(DiscCalibration calibration, GeoPoint target) {
        double theta = GreatCircle.angularDistanceRadians(calibration.subPoint(), target);
        double thetaMax = GeoLimb.visibleHalfAngleRadians(calibration.distanceKm());
        if (theta >= thetaMax) {
            return Optional.empty();
        }

        double alpha = GeoLimb.viewAngleFromGroundAngle(calibration.distanceKm(), theta);
        double alphaMax = GeoLimb.maxViewAngleRadians(calibration.distanceKm());
        double unitRadius = Math.tan(alpha) / Math.tan(alphaMax);

        double bearing = GreatCircle.initialBearingRadians(calibration.subPoint(), target);
        double unitDx = unitRadius * Math.sin(bearing);
        double unitDy = -unitRadius * Math.cos(bearing);

        double u = calibration.nadirX() + unitDx * calibration.radiusX();
        double v = calibration.nadirY() + unitDy * calibration.radiusY();
        return Optional.of(new PixelPoint(u, v));
    }
}
