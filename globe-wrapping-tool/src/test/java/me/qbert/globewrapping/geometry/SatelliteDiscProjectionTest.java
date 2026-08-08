package me.qbert.globewrapping.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SatelliteDiscProjectionTest {

    /** The GOES-8-like example calibration from globe-unwrapper-requirements.md section 4. */
    private static final DiscCalibration GOES8 =
        new DiscCalibration(0.0, -75.2, 35786.0, 0.5, 0.485, 0.4995, 0.4995);

    @Test
    void subPointProjectsExactlyToNadir() {
        Optional<PixelPoint> result = SatelliteDiscProjection.project(GOES8, GOES8.subPoint());
        assertTrue(result.isPresent());
        assertEquals(GOES8.nadirX(), result.get().x(), 1e-9);
        assertEquals(GOES8.nadirY(), result.get().y(), 1e-9);
    }

    @Test
    void pointBeyondTheLimbIsNotVisible() {
        // Antipodal point: definitely on the far side of the globe from a GEO satellite.
        GeoPoint antipode = new GeoPoint(0.0, GeoPoint.normalizeLongitude(GOES8.subLongitudeDeg() + 180.0));
        assertFalse(SatelliteDiscProjection.project(GOES8, antipode).isPresent());
    }

    @Test
    void nearTheLimbDueEastLandsNearNadirPlusRadiusX() {
        double thetaMax = GeoLimb.visibleHalfAngleRadians(GOES8.distanceKm());
        double theta = thetaMax * 0.999;
        GeoPoint target = GreatCircle.destinationPoint(GOES8.subPoint(), Math.PI / 2.0, theta);

        Optional<PixelPoint> result = SatelliteDiscProjection.project(GOES8, target);
        assertTrue(result.isPresent());
        assertEquals(GOES8.nadirX() + GOES8.radiusX(), result.get().x(), 0.02);
        assertEquals(GOES8.nadirY(), result.get().y(), 0.02);
    }

    @Test
    void dueNorthMovesTowardDecreasingV() {
        double thetaMax = GeoLimb.visibleHalfAngleRadians(GOES8.distanceKm());
        double theta = thetaMax * 0.5;
        GeoPoint target = GreatCircle.destinationPoint(GOES8.subPoint(), 0.0, theta);

        Optional<PixelPoint> result = SatelliteDiscProjection.project(GOES8, target);
        assertTrue(result.isPresent());
        assertEquals(GOES8.nadirX(), result.get().x(), 1e-6);
        assertTrue(result.get().y() < GOES8.nadirY(), "north of nadir should have a smaller v (image y grows downward)");
    }
}
