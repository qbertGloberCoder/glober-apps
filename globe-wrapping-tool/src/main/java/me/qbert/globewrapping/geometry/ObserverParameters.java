package me.qbert.globewrapping.geometry;

/** A synthetic observer/camera for the {@code wrap} stage: where it's looking straight down from, and from how high. */
public record ObserverParameters(double centerLatitudeDeg, double centerLongitudeDeg, double heightKm) {

    public ObserverParameters {
        if (heightKm <= 0.0) {
            throw new IllegalArgumentException("heightKm must be positive: " + heightKm);
        }
    }

    public GeoPoint center() {
        return new GeoPoint(centerLatitudeDeg, centerLongitudeDeg);
    }
}
