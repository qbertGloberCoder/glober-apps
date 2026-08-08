package me.qbert.globewrapping.geometry;

import java.util.Optional;

/**
 * Finite-altitude, rectilinear ("non-distorting") pinhole-camera model: a
 * tan(view angle)-per-pixel constant (the virtual "lens") combined with the
 * observer's height fully determines ground scale, matching
 * globe-unwrapper-requirements.md section 6 ("height + output pixel
 * dimensions together already fully determine scale... no separate scale or
 * zoom parameter is needed"). With a fixed lens constant, a higher altitude
 * naturally covers more ground per pixel and a lower altitude is a tighter
 * crop — both regimes fall out of the same per-pixel inverse test with no
 * special-casing (mirrors {@link SatelliteDiscProjection}'s forward math).
 *
 * <p><b>Default lens is output-size-aware, not a single hardcoded constant.</b>
 * An earlier version used one fixed {@code tanAlphaPerPixel} tuned for
 * ~2000px-wide frames — at real GEO altitude (35786 km) with a smaller,
 * video-friendly frame like 1024x1024, that produced a disc radius of
 * ~1000px, nearly double the frame's own half-width, forcing users to a
 * physically meaningless height (~78000 km) just to make the disc fit. The
 * default constructor now derives the lens <i>per call</i> from the actual
 * requested output size, anchored to {@link #REFERENCE_ALTITUDE_KM} (real
 * GEO altitude, matching the {@code distance} field already used by every
 * satellite profile): at that reference height, the visible disc's radius is
 * exactly half the frame's shorter dimension, for any output size. Distance
 * scaling away from that reference follows an inverse-square-ish
 * relationship, not linear — e.g. viewing from 2x the reference altitude
 * shows a disc at ~55% the linear radius but only ~30% the area; viewing
 * from 1/10th the reference altitude would (if the disc still fit the frame)
 * be ~5.5x the linear size / ~30x the area, i.e. a fixed-size frame shows
 * roughly a 30th of the ground area it would at the reference altitude.
 * Callers wanting a fixed lens independent of output size (e.g. rendering
 * one physical camera at multiple export resolutions) can still construct
 * this class with an explicit {@code tanAlphaPerPixel}.
 */
public final class PerspectiveObserverProjection implements ObserverProjection {

    /**
     * Reference altitude (km) the output-size-aware default lens is
     * calibrated against: real geostationary altitude, matching the
     * {@code distance} field of every satellite profile in
     * {@code globe-wrapping-tool.yaml}. At this height, the visible disc's radius
     * exactly equals half of {@code min(outputWidth, outputHeight)} — the
     * disc inscribes the frame's shorter dimension, for any requested size.
     */
    public static final double REFERENCE_ALTITUDE_KM = 35786.0;

    private final Double fixedTanAlphaPerPixel;

    /** Output-size-aware default: see the class-level javadoc. */
    public PerspectiveObserverProjection() {
        this.fixedTanAlphaPerPixel = null;
    }

    /** Fixed lens, independent of whatever output size is requested. */
    public PerspectiveObserverProjection(double tanAlphaPerPixel) {
        if (tanAlphaPerPixel <= 0.0) {
            throw new IllegalArgumentException("tanAlphaPerPixel must be positive: " + tanAlphaPerPixel);
        }
        this.fixedTanAlphaPerPixel = tanAlphaPerPixel;
    }

    @Override
    public Optional<GeoPoint> unproject(
        ObserverParameters observer, int outputWidth, int outputHeight, double pixelX, double pixelY) {

        double tanAlphaPerPixel = fixedTanAlphaPerPixel != null
            ? fixedTanAlphaPerPixel
            : referenceTanAlphaPerPixel(outputWidth, outputHeight);

        double dxPix = pixelX - outputWidth / 2.0;
        double dyPix = pixelY - outputHeight / 2.0;

        double tanAlphaX = dxPix * tanAlphaPerPixel;
        double tanAlphaY = dyPix * tanAlphaPerPixel;
        double tanAlpha = Math.hypot(tanAlphaX, tanAlphaY);
        double alpha = Math.atan(tanAlpha);

        double alphaMax = GeoLimb.maxViewAngleRadians(observer.heightKm());
        if (alpha >= alphaMax) {
            return Optional.empty();
        }

        double theta = GeoLimb.groundAngleFromViewAngle(observer.heightKm(), alpha);
        double bearing = Math.atan2(tanAlphaX, -tanAlphaY);

        return Optional.of(GreatCircle.destinationPoint(observer.center(), bearing, theta));
    }

    /** The lens such that, at {@link #REFERENCE_ALTITUDE_KM}, the disc radius is exactly half the shorter output dimension. */
    private static double referenceTanAlphaPerPixel(int outputWidth, int outputHeight) {
        double halfMinDimension = Math.min(outputWidth, outputHeight) / 2.0;
        double referenceAlphaMax = GeoLimb.maxViewAngleRadians(REFERENCE_ALTITUDE_KM);
        return Math.tan(referenceAlphaMax) / halfMinDimension;
    }
}
