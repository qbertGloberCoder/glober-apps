package me.qbert.globewrapping.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EquirectangularMappingTest {

    @Test
    void nullIslandMapsToImageCenter() {
        PixelPoint p = EquirectangularMapping.geoToPixel(new GeoPoint(0.0, 0.0), 360, 180);
        assertEquals(180.0, p.x(), 1e-9);
        assertEquals(90.0, p.y(), 1e-9);
    }

    @Test
    void topLeftCornerIsNorthWest() {
        PixelPoint p = EquirectangularMapping.geoToPixel(new GeoPoint(90.0, -180.0), 360, 180);
        assertEquals(0.0, p.x(), 1e-9);
        assertEquals(0.0, p.y(), 1e-9);
    }

    @Test
    void bottomRightCornerIsSouthEast() {
        PixelPoint p = EquirectangularMapping.geoToPixel(new GeoPoint(-90.0, 179.999), 360, 180);
        assertEquals(360.0, p.x(), 0.01);
        assertEquals(180.0, p.y(), 1e-9);
    }

    @Test
    void roundTripsThroughPixelAndBack() {
        int width = 3600;
        int height = 1800;
        GeoPoint[] points = {
            new GeoPoint(0.0, 0.0),
            new GeoPoint(45.0, 90.0),
            new GeoPoint(-33.7, -151.2),
            new GeoPoint(89.0, 179.0),
            new GeoPoint(-89.0, -179.0),
        };
        for (GeoPoint point : points) {
            PixelPoint pixel = EquirectangularMapping.geoToPixel(point, width, height);
            GeoPoint roundTripped = EquirectangularMapping.pixelToGeo(pixel.x(), pixel.y(), width, height);
            assertEquals(point.latitudeDeg(), roundTripped.latitudeDeg(), 1e-6);
            assertEquals(point.longitudeDeg(), roundTripped.longitudeDeg(), 1e-6);
        }
    }
}
