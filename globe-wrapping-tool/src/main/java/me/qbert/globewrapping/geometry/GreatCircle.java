package me.qbert.globewrapping.geometry;

/**
 * Pure spherical-trigonometry helpers, independent of any camera/altitude
 * concept. Standard formulas (haversine distance, forward azimuth, and
 * "destination point given start/bearing/distance").
 */
public final class GreatCircle {

    private GreatCircle() {
    }

    /** Angular distance (radians) between two points, via the haversine formula. */
    public static double angularDistanceRadians(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitudeDeg());
        double lat2 = Math.toRadians(b.latitudeDeg());
        double dLon = Math.toRadians(b.longitudeDeg() - a.longitudeDeg());

        double sinHalfDLat = Math.sin((lat2 - lat1) / 2.0);
        double sinHalfDLon = Math.sin(dLon / 2.0);
        double h = sinHalfDLat * sinHalfDLat + Math.cos(lat1) * Math.cos(lat2) * sinHalfDLon * sinHalfDLon;
        h = Math.max(0.0, Math.min(1.0, h));
        return 2.0 * Math.asin(Math.sqrt(h));
    }

    /** Initial bearing in radians (0 = north, positive = clockwise/eastward) from {@code from} to {@code to}. */
    public static double initialBearingRadians(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitudeDeg());
        double lat2 = Math.toRadians(to.latitudeDeg());
        double dLon = Math.toRadians(to.longitudeDeg() - from.longitudeDeg());

        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return Math.atan2(y, x);
    }

    /**
     * The point reached by travelling {@code angularDistanceRadians} from
     * {@code origin} along {@code bearingRadians} (0 = north, positive =
     * clockwise/eastward). Inverse of the (angularDistance, bearing) pair
     * produced by {@link #angularDistanceRadians} / {@link #initialBearingRadians}.
     */
    public static GeoPoint destinationPoint(GeoPoint origin, double bearingRadians, double angularDistanceRadians) {
        double lat1 = Math.toRadians(origin.latitudeDeg());
        double lon1 = Math.toRadians(origin.longitudeDeg());

        double sinLat2 = Math.sin(lat1) * Math.cos(angularDistanceRadians)
            + Math.cos(lat1) * Math.sin(angularDistanceRadians) * Math.cos(bearingRadians);
        sinLat2 = Math.max(-1.0, Math.min(1.0, sinLat2));
        double lat2 = Math.asin(sinLat2);

        double y = Math.sin(bearingRadians) * Math.sin(angularDistanceRadians) * Math.cos(lat1);
        double x = Math.cos(angularDistanceRadians) - Math.sin(lat1) * sinLat2;
        double lon2 = lon1 + Math.atan2(y, x);

        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lon2));
    }
}
