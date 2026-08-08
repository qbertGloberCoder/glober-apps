package me.qbert.globewrapping.geometry;

/** A point on the globe. Longitude is normalized into [-180, 180) on construction. */
public record GeoPoint(double latitudeDeg, double longitudeDeg) {

    public GeoPoint {
        if (Double.isNaN(latitudeDeg) || latitudeDeg < -90.0 || latitudeDeg > 90.0) {
            throw new IllegalArgumentException("latitudeDeg out of range [-90,90]: " + latitudeDeg);
        }
        longitudeDeg = normalizeLongitude(longitudeDeg);
    }

    public static double normalizeLongitude(double lonDeg) {
        double normalized = lonDeg % 360.0;
        if (normalized < -180.0) {
            normalized += 360.0;
        } else if (normalized >= 180.0) {
            normalized -= 360.0;
        }
        return normalized;
    }
}
