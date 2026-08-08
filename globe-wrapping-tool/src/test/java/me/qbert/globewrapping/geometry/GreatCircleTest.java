package me.qbert.globewrapping.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GreatCircleTest {

    @Test
    void distanceFromAPointToItselfIsZero() {
        GeoPoint p = new GeoPoint(12.3, -45.6);
        assertEquals(0.0, GreatCircle.angularDistanceRadians(p, p), 1e-12);
    }

    @Test
    void antipodalPointsAreHalfACircleApart() {
        GeoPoint a = new GeoPoint(0.0, 0.0);
        GeoPoint b = new GeoPoint(0.0, 180.0);
        assertEquals(Math.PI, GreatCircle.angularDistanceRadians(a, b), 1e-9);
    }

    @Test
    void dueEastBearingIsNinetyDegrees() {
        GeoPoint from = new GeoPoint(0.0, 0.0);
        GeoPoint to = new GeoPoint(0.0, 10.0);
        assertEquals(Math.PI / 2.0, GreatCircle.initialBearingRadians(from, to), 1e-9);
    }

    @Test
    void dueNorthBearingIsZero() {
        GeoPoint from = new GeoPoint(0.0, 0.0);
        GeoPoint to = new GeoPoint(10.0, 0.0);
        assertEquals(0.0, GreatCircle.initialBearingRadians(from, to), 1e-9);
    }

    @Test
    void destinationPointTravelingEastAlongEquatorStaysOnEquator() {
        GeoPoint origin = new GeoPoint(0.0, 0.0);
        GeoPoint destination = GreatCircle.destinationPoint(origin, Math.PI / 2.0, Math.toRadians(30.0));
        assertEquals(0.0, destination.latitudeDeg(), 1e-9);
        assertEquals(30.0, destination.longitudeDeg(), 1e-9);
    }

    @Test
    void bearingAndDistanceRoundTripThroughDestinationPoint() {
        GeoPoint origin = new GeoPoint(37.5, -122.1);
        double bearing = Math.toRadians(63.0);
        double distance = Math.toRadians(15.0);

        GeoPoint destination = GreatCircle.destinationPoint(origin, bearing, distance);

        double recoveredDistance = GreatCircle.angularDistanceRadians(origin, destination);
        double recoveredBearing = GreatCircle.initialBearingRadians(origin, destination);

        assertEquals(distance, recoveredDistance, 1e-9);
        assertEquals(bearing, recoveredBearing, 1e-9);
    }
}
