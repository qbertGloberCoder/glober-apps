package me.qbert.globewrapping.geometry;

/**
 * Explicit, empirically-calibrated geometry for one real (or synthetic)
 * nadir-pointing, rectilinear-lens source image of the Earth disc. Every
 * field is a fraction of that source image's own dimensions, or a physical
 * distance — nothing here is inferred from image content. See
 * globe-unwrapper-requirements.md section 4 for the field definitions.
 */
public record DiscCalibration(
    double subLatitudeDeg,
    double subLongitudeDeg,
    double distanceKm,
    double nadirX,
    double nadirY,
    double radiusX,
    double radiusY
) {

    public DiscCalibration {
        if (distanceKm <= 0.0) {
            throw new IllegalArgumentException("distanceKm must be positive: " + distanceKm);
        }
        if (radiusX <= 0.0 || radiusY <= 0.0) {
            throw new IllegalArgumentException("radiusX/radiusY must be positive");
        }
    }

    public GeoPoint subPoint() {
        return new GeoPoint(subLatitudeDeg, subLongitudeDeg);
    }
}
