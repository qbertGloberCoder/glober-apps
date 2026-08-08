package me.qbert.globewrapping.geometry;

import java.util.Optional;

/**
 * Inverse projection used by the {@code wrap} stage: for one output pixel of
 * a synthetic observer-view frame, determines which (if any) point on the
 * globe that pixel corresponds to.
 *
 * <p>One implementation per projection <em>strategy</em> — deliberately not a
 * single class with internal boolean mode flags. This is a direct reaction to
 * {@code old_draw_project}'s {@code ProjectorSpherical}, whose
 * {@code spaceView}/{@code zoomedOut}/{@code constrainToZoom}/{@code leaveUnwrapped}
 * flag combinatorics are named in globe-unwrapper-requirements.md section 6
 * as an anti-pattern to avoid.
 */
public interface ObserverProjection {

    /**
     * @param pixelX column (0..outputWidth), {@code pixelY} row (0..outputHeight) of the requested output pixel
     * @return the geo point visible at that pixel, or empty if that pixel is off-globe (beyond the horizon)
     */
    Optional<GeoPoint> unproject(
        ObserverParameters observer, int outputWidth, int outputHeight, double pixelX, double pixelY);
}
