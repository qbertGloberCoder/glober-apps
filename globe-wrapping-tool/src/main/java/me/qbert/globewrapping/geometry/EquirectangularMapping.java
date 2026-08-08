package me.qbert.globewrapping.geometry;

/**
 * Pure lat/lon &lt;-&gt; equirectangular (plate carree) pixel mapping for a
 * canonical image of a given width/height. Longitude -180..180 maps to
 * x = 0..width; latitude +90..-90 (north to south) maps to y = 0..height.
 * Shared by every stage that touches the canonical equirect image.
 */
public final class EquirectangularMapping {

    private EquirectangularMapping() {
    }

    /** Absolute pixel coordinates (not clamped to the image bounds) for a geo point. */
    public static PixelPoint geoToPixel(GeoPoint point, int width, int height) {
        double x = (point.longitudeDeg() + 180.0) / 360.0 * width;
        double y = (90.0 - point.latitudeDeg()) / 180.0 * height;
        return new PixelPoint(x, y);
    }

    /** The geo point sampled at absolute pixel coordinates (x, y) of a width x height equirect image. */
    public static GeoPoint pixelToGeo(double x, double y, int width, int height) {
        double lon = (x / width) * 360.0 - 180.0;
        double lat = 90.0 - (y / height) * 180.0;
        lat = Math.max(-90.0, Math.min(90.0, lat));
        return new GeoPoint(lat, lon);
    }
}
