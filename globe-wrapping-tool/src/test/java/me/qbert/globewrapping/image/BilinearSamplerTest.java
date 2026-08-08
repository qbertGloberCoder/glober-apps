package me.qbert.globewrapping.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BilinearSamplerTest {

    @Test
    void samplingAtExactPixelReturnsThatPixelExactly() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF804020);

        Optional<double[]> result = BilinearSampler.sample(image, 0.0, 0.0);
        assertTrue(result.isPresent());
        assertEquals(0x80 / 255.0, result.get()[0], 1e-9);
        assertEquals(0x40 / 255.0, result.get()[1], 1e-9);
        assertEquals(0x20 / 255.0, result.get()[2], 1e-9);
    }

    @Test
    void samplingMidpointAveragesTwoOpaqueNeighbors() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF000000); // opaque black
        image.setRGB(1, 0, 0xFFFFFFFF); // opaque white

        Optional<double[]> result = BilinearSampler.sample(image, 0.5, 0.0);
        assertTrue(result.isPresent());
        assertEquals(0.5, result.get()[0], 1e-9);
        assertEquals(0.5, result.get()[1], 1e-9);
        assertEquals(0.5, result.get()[2], 1e-9);
    }

    @Test
    void samplingFullyTransparentRegionIsEmpty() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        // all pixels default to fully transparent black
        assertTrue(BilinearSampler.sample(image, 0.5, 0.5).isEmpty());
    }

    @Test
    void transparentNeighborsDoNotDilutePartiallyOpaqueRegion() {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFFFF0000); // opaque red
        image.setRGB(1, 0, 0x00000000); // fully transparent

        Optional<double[]> result = BilinearSampler.sample(image, 0.5, 0.0);
        assertTrue(result.isPresent());
        // only the opaque red texel contributes any weight, so the result should be pure red,
        // not a 50/50 blend with transparent black.
        assertEquals(1.0, result.get()[0], 1e-9);
        assertEquals(0.0, result.get()[1], 1e-9);
        assertEquals(0.0, result.get()[2], 1e-9);
    }

    @Test
    void outOfRangeCoordinatesClampToTheEdge() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF112233);

        Optional<double[]> result = BilinearSampler.sample(image, -50.0, -50.0);
        assertTrue(result.isPresent());
        assertEquals(0x11 / 255.0, result.get()[0], 1e-9);
        assertEquals(0x22 / 255.0, result.get()[1], 1e-9);
        assertEquals(0x33 / 255.0, result.get()[2], 1e-9);
    }
}
