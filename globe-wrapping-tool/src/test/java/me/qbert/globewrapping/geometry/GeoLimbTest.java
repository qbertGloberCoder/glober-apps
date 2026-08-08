package me.qbert.globewrapping.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GeoLimbTest {

    /** Real-world GOES-class geostationary altitude, km above the surface. */
    private static final double GEO_ALTITUDE_KM = 35786.0;

    @Test
    void geoAltitudeMatchesKnownFullDiskExtent() {
        // Real GEO full-disk imagery covers ~81.3 degrees from nadir (Earth-center angle)
        // and has a ~17.4 degree total field of view (~8.7 degree half-angle) at the satellite.
        double thetaMaxDeg = Math.toDegrees(GeoLimb.visibleHalfAngleRadians(GEO_ALTITUDE_KM));
        double alphaMaxDeg = Math.toDegrees(GeoLimb.maxViewAngleRadians(GEO_ALTITUDE_KM));

        assertEquals(81.3, thetaMaxDeg, 0.1);
        assertEquals(8.7, alphaMaxDeg, 0.1);
    }

    @Test
    void thetaAndAlphaComplementToNinetyDegreesAtTheLimb() {
        double thetaMax = GeoLimb.visibleHalfAngleRadians(GEO_ALTITUDE_KM);
        double alphaMax = GeoLimb.maxViewAngleRadians(GEO_ALTITUDE_KM);
        assertEquals(Math.PI / 2.0, thetaMax + alphaMax, 1e-9);
    }

    @Test
    void nadirMapsToNadir() {
        assertEquals(0.0, GeoLimb.viewAngleFromGroundAngle(GEO_ALTITUDE_KM, 0.0), 1e-12);
        assertEquals(0.0, GeoLimb.groundAngleFromViewAngle(GEO_ALTITUDE_KM, 0.0), 1e-12);
    }

    @Test
    void limbRoundTripsToMaxViewAngle() {
        double thetaMax = GeoLimb.visibleHalfAngleRadians(GEO_ALTITUDE_KM);
        double alphaMax = GeoLimb.maxViewAngleRadians(GEO_ALTITUDE_KM);
        assertEquals(alphaMax, GeoLimb.viewAngleFromGroundAngle(GEO_ALTITUDE_KM, thetaMax), 1e-9);
        assertEquals(thetaMax, GeoLimb.groundAngleFromViewAngle(GEO_ALTITUDE_KM, alphaMax), 1e-9);
    }

    @Test
    void thetaAlphaRoundTripsAtSeveralAltitudes() {
        double[] altitudes = {400.0 /* ISS-ish */, 850.0 /* polar LEO */, GEO_ALTITUDE_KM};
        for (double altitude : altitudes) {
            double thetaMax = GeoLimb.visibleHalfAngleRadians(altitude);
            for (double fraction : new double[] {0.0, 0.25, 0.5, 0.75, 0.99}) {
                double theta = thetaMax * fraction;
                double alpha = GeoLimb.viewAngleFromGroundAngle(altitude, theta);
                double roundTrippedTheta = GeoLimb.groundAngleFromViewAngle(altitude, alpha);
                assertEquals(theta, roundTrippedTheta, 1e-9,
                    "round trip failed at altitude=" + altitude + " fraction=" + fraction);
            }
        }
    }

    @Test
    void rejectsNonPositiveAltitude() {
        assertThrows(IllegalArgumentException.class, () -> GeoLimb.visibleHalfAngleRadians(0.0));
        assertThrows(IllegalArgumentException.class, () -> GeoLimb.visibleHalfAngleRadians(-100.0));
    }

    @Test
    void rejectsOutOfRangeAngles() {
        double thetaMax = GeoLimb.visibleHalfAngleRadians(GEO_ALTITUDE_KM);
        assertThrows(IllegalArgumentException.class,
            () -> GeoLimb.viewAngleFromGroundAngle(GEO_ALTITUDE_KM, thetaMax + 0.1));

        double alphaMax = GeoLimb.maxViewAngleRadians(GEO_ALTITUDE_KM);
        assertThrows(IllegalArgumentException.class,
            () -> GeoLimb.groundAngleFromViewAngle(GEO_ALTITUDE_KM, alphaMax + 0.1));
    }
}
