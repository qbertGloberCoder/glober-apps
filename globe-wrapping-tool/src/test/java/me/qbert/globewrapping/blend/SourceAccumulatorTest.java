package me.qbert.globewrapping.blend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import me.qbert.globewrapping.geometry.DiscCalibration;
import me.qbert.globewrapping.geometry.EquirectangularMapping;
import me.qbert.globewrapping.geometry.GeoPoint;
import me.qbert.globewrapping.geometry.PixelPoint;
import me.qbert.globewrapping.image.EquirectCanvas;
import org.junit.jupiter.api.Test;

class SourceAccumulatorTest {

    private static final int CANVAS_WIDTH = 360;
    private static final int CANVAS_HEIGHT = 180;

    private static BufferedImage solidColor(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    private static DiscCalibration calibrationAt(double subLat, double subLon) {
        return new DiscCalibration(subLat, subLon, 35786.0, 0.5, 0.5, 0.5, 0.5);
    }

    /** The (x, y) canvas index whose pixel bucket contains {@code target}. */
    private static int[] pixelIndexFor(GeoPoint target) {
        PixelPoint p = EquirectangularMapping.geoToPixel(target, CANVAS_WIDTH, CANVAS_HEIGHT);
        int x = Math.min(CANVAS_WIDTH - 1, Math.max(0, (int) Math.floor(p.x())));
        int y = Math.min(CANVAS_HEIGHT - 1, Math.max(0, (int) Math.floor(p.y())));
        return new int[] {x, y};
    }

    @Test
    void singleSourceCoversItsDiscAndLeavesTheFarSideUncovered() {
        EquirectCanvas canvas = new EquirectCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        SourceContribution source = new SourceContribution(calibrationAt(0.0, 0.0), solidColor(8, Color.RED));

        new SourceAccumulator().accumulate(canvas, List.of(source));

        int[] nearNadir = pixelIndexFor(new GeoPoint(0.0, 0.0));
        assertTrue(canvas.isCovered(nearNadir[0], nearNadir[1]));

        int[] farSide = pixelIndexFor(new GeoPoint(0.0, 179.0));
        assertFalse(canvas.isCovered(farSide[0], farSide[1]));
    }

    @Test
    void coincidentSourcesBlendFiftyFiftyAtSharedNadir() {
        EquirectCanvas canvas = new EquirectCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        SourceContribution red = new SourceContribution(calibrationAt(0.0, 0.0), solidColor(8, Color.RED));
        SourceContribution blue = new SourceContribution(calibrationAt(0.0, 0.0), solidColor(8, Color.BLUE));

        new SourceAccumulator().accumulate(canvas, List.of(red, blue));

        int[] idx = pixelIndexFor(new GeoPoint(0.0, 0.0));
        Optional<double[]> rgb = canvas.averageRgb(idx[0], idx[1]);
        assertTrue(rgb.isPresent());
        // Both sources share the exact same sub-point and distance, so at any point
        // their confidence weights are identical regardless of small pixel-center
        // offsets -- the blend must be an exact 50/50 average.
        assertEquals(0.5, rgb.get()[0], 1e-6, "red channel");
        assertEquals(0.0, rgb.get()[1], 1e-6, "green channel");
        assertEquals(0.5, rgb.get()[2], 1e-6, "blue channel");
    }

    @Test
    void closerSourceDominatesTheBlend() {
        EquirectCanvas canvas = new EquirectCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        SourceContribution near = new SourceContribution(calibrationAt(0.0, -10.0), solidColor(8, Color.RED));
        SourceContribution far = new SourceContribution(calibrationAt(0.0, 10.0), solidColor(8, Color.BLUE));

        new SourceAccumulator().accumulate(canvas, List.of(near, far));

        // A target 2 degrees from "near"'s nadir but 18 degrees from "far"'s nadir.
        int[] idx = pixelIndexFor(new GeoPoint(0.0, -8.0));
        Optional<double[]> rgb = canvas.averageRgb(idx[0], idx[1]);
        assertTrue(rgb.isPresent());
        assertTrue(rgb.get()[0] > rgb.get()[2],
            "closer source (red) should dominate over the farther one (blue): " + java.util.Arrays.toString(rgb.get()));
    }

    @Test
    void overlapIsNotAHardCutover() {
        EquirectCanvas canvas = new EquirectCanvas(CANVAS_WIDTH, CANVAS_HEIGHT);
        SourceContribution near = new SourceContribution(calibrationAt(0.0, -10.0), solidColor(8, Color.RED));
        SourceContribution far = new SourceContribution(calibrationAt(0.0, 10.0), solidColor(8, Color.BLUE));

        new SourceAccumulator().accumulate(canvas, List.of(near, far));

        int[] idx = pixelIndexFor(new GeoPoint(0.0, -8.0));
        Optional<double[]> rgb = canvas.averageRgb(idx[0], idx[1]);
        assertTrue(rgb.isPresent());
        // A hard priority cutover would give pure red (1,0,0) here; blending by
        // confidence weight should leave a nonzero contribution from the farther source too.
        assertTrue(rgb.get()[2] > 0.0, "blend should retain some of the farther source's contribution");
    }
}
