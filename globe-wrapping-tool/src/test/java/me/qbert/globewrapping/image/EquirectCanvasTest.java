package me.qbert.globewrapping.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EquirectCanvasTest {

    @Test
    void uncoveredPixelIsUncoveredAndTransparent() {
        EquirectCanvas canvas = new EquirectCanvas(4, 4);
        assertFalse(canvas.isCovered(1, 1));
        assertTrue(canvas.averageRgb(1, 1).isEmpty());
        assertEquals(0, canvas.toBufferedImage().getRGB(1, 1) >>> 24, "alpha should be 0 when uncovered");
    }

    @Test
    void singleContributionIsReturnedExactly() {
        EquirectCanvas canvas = new EquirectCanvas(4, 4);
        canvas.accumulate(2, 3, 1.0, 0.5, 0.0, 1.0);

        Optional<double[]> result = canvas.averageRgb(2, 3);
        assertTrue(result.isPresent());
        assertEquals(1.0, result.get()[0], 1e-9);
        assertEquals(0.5, result.get()[1], 1e-9);
        assertEquals(0.0, result.get()[2], 1e-9);
        assertTrue(canvas.isCovered(2, 3));
    }

    @Test
    void multipleContributionsAreWeightedAverage() {
        EquirectCanvas canvas = new EquirectCanvas(2, 2);
        canvas.accumulate(0, 0, 1.0, 1.0, 1.0, 3.0); // weight 3, white
        canvas.accumulate(0, 0, 0.0, 0.0, 0.0, 1.0); // weight 1, black

        Optional<double[]> result = canvas.averageRgb(0, 0);
        assertTrue(result.isPresent());
        // (1.0*3 + 0.0*1) / 4 = 0.75
        assertEquals(0.75, result.get()[0], 1e-9);
    }

    @Test
    void zeroOrNegativeWeightContributionIsIgnored() {
        EquirectCanvas canvas = new EquirectCanvas(2, 2);
        canvas.accumulate(0, 0, 1.0, 1.0, 1.0, 0.0);
        canvas.accumulate(0, 0, 1.0, 1.0, 1.0, -5.0);
        assertFalse(canvas.isCovered(0, 0));
    }

    @Test
    void coveredPixelRendersFullyOpaque() {
        EquirectCanvas canvas = new EquirectCanvas(2, 2);
        canvas.accumulate(0, 0, 0.2, 0.4, 0.6, 1.0);
        BufferedImage image = canvas.toBufferedImage();
        int argb = image.getRGB(0, 0);
        assertEquals(0xFF, argb >>> 24 & 0xFF);
    }

    @Test
    void outOfBoundsAccessThrows() {
        EquirectCanvas canvas = new EquirectCanvas(2, 2);
        assertThrows(IndexOutOfBoundsException.class, () -> canvas.accumulate(2, 0, 1, 1, 1, 1));
        assertThrows(IndexOutOfBoundsException.class, () -> canvas.isCovered(-1, 0));
    }
}
