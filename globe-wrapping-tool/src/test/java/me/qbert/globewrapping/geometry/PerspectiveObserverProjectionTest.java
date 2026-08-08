package me.qbert.globewrapping.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class PerspectiveObserverProjectionTest {

    private final PerspectiveObserverProjection projection = new PerspectiveObserverProjection();

    @Test
    void centerPixelMapsToObserverCenter() {
        ObserverParameters observer = new ObserverParameters(12.0, 34.0, 35786.0);
        Optional<GeoPoint> result = projection.unproject(observer, 1000, 800, 500.0, 400.0);
        assertTrue(result.isPresent());
        assertEquals(12.0, result.get().latitudeDeg(), 1e-9);
        assertEquals(34.0, result.get().longitudeDeg(), 1e-9);
    }

    @Test
    void pixelAboveCenterDecodesToTheNorth() {
        ObserverParameters observer = new ObserverParameters(0.0, 0.0, 35786.0);
        Optional<GeoPoint> result = projection.unproject(observer, 1000, 800, 500.0, 300.0);
        assertTrue(result.isPresent());
        assertTrue(result.get().latitudeDeg() > 0.0, "a pixel above center (smaller row) should be north");
        assertEquals(0.0, result.get().longitudeDeg(), 1e-6);
    }

    @Test
    void atReferenceAltitudeTheDiscInscribesTheShorterFrameDimensionRegardlessOfSize() {
        ObserverParameters atReference =
            new ObserverParameters(0.0, 0.0, PerspectiveObserverProjection.REFERENCE_ALTITUDE_KM);

        for (int[] size : new int[][] {{1024, 1024}, {2000, 2000}, {640, 480}}) {
            int width = size[0];
            int height = size[1];
            double halfMin = Math.min(width, height) / 2.0;

            double justInsideX = width / 2.0 + (halfMin - 1.0);
            Optional<GeoPoint> justInside = projection.unproject(atReference, width, height, justInsideX, height / 2.0);
            assertTrue(justInside.isPresent(), width + "x" + height + ": just inside the inscribed radius should be visible");

            double justOutsideX = width / 2.0 + (halfMin + 5.0);
            Optional<GeoPoint> justOutside = projection.unproject(atReference, width, height, justOutsideX, height / 2.0);
            assertFalse(justOutside.isPresent(), width + "x" + height + ": just beyond the inscribed radius should not be visible");
        }
    }

    @Test
    void farBeyondTheHorizonIsNotVisible() {
        // A very low altitude with a huge pixel offset easily exceeds the visible half-angle.
        ObserverParameters lowObserver = new ObserverParameters(0.0, 0.0, 400.0);
        PerspectiveObserverProjection wideLens = new PerspectiveObserverProjection(0.01);
        assertFalse(wideLens.unproject(lowObserver, 2000, 2000, 2000.0, 1000.0).isPresent());
    }

    /**
     * Cross-consistency check: build a {@link DiscCalibration} that represents the exact
     * same physical camera as an {@link ObserverParameters}/lens-constant pair, and verify
     * that projecting the point {@link PerspectiveObserverProjection} unprojected for a given
     * pixel lands back on that same pixel via {@link SatelliteDiscProjection}. This exercises
     * nearly the entire geometry package and confirms the forward and inverse projections are
     * true mathematical inverses of each other, not just independently plausible formulas.
     */
    @Test
    void forwardAndInverseProjectionsAreTrueInverses() {
        double heightKm = 35786.0;
        int width = 1000;
        int height = 800;
        double lensConstant = 2.0e-4; // arbitrary fixed lens; the value itself isn't load-bearing here
        ObserverParameters observer = new ObserverParameters(15.0, -40.0, heightKm);
        PerspectiveObserverProjection customLens = new PerspectiveObserverProjection(lensConstant);

        double pixelX = 700.0;
        double pixelY = 300.0;

        Optional<GeoPoint> target = customLens.unproject(observer, width, height, pixelX, pixelY);
        assertTrue(target.isPresent());

        double alphaMax = GeoLimb.maxViewAngleRadians(heightKm);
        double equivalentRadiusX = Math.tan(alphaMax) / (lensConstant * width);
        double equivalentRadiusY = Math.tan(alphaMax) / (lensConstant * height);
        DiscCalibration equivalentCalibration = new DiscCalibration(
            observer.centerLatitudeDeg(), observer.centerLongitudeDeg(), heightKm,
            0.5, 0.5, equivalentRadiusX, equivalentRadiusY);

        Optional<PixelPoint> roundTripped = SatelliteDiscProjection.project(equivalentCalibration, target.get());
        assertTrue(roundTripped.isPresent());

        assertEquals(pixelX, roundTripped.get().x() * width, 1e-4);
        assertEquals(pixelY, roundTripped.get().y() * height, 1e-4);
    }
}
