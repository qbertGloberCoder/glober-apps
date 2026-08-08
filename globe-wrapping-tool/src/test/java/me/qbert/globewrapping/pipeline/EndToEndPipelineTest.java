package me.qbert.globewrapping.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.qbert.globewrapping.calibration.SourceCalibration;
import me.qbert.globewrapping.geometry.DiscCalibration;
import me.qbert.globewrapping.geometry.ObserverParameters;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Chains all three stages together (unlike {@code UnwrapStageTest}/
 * {@code CombineStageTest}/{@code WrapStageTest}, which each exercise one
 * stage in isolation), using small synthetic fixture images generated in the
 * test itself rather than real satellite captures (none exist in this
 * checkout — see {@code CLAUDE.md}'s "Stale paths and missing source images"
 * note). This automates the same shape of check performed manually during
 * the Step 7 smoke test (real jar, real basemap, real timing).
 */
class EndToEndPipelineTest {

    @TempDir
    Path tempDir;

    private static BufferedImage solidColor(int size, Color color) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, size, size);
        g.dispose();
        return image;
    }

    private static BufferedImage solidColorOpaque(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    @Test
    void unwrapThenCombineThenWrapProducesTheExpectedColorsAtEachStage() throws IOException {
        // Two sources, on opposite sides of the globe, each a solid color.
        Path goes8Path = tempDir.resolve("goes8.png");
        ImageFiles.save(solidColor(8, Color.RED), goes8Path, "png");
        Path himawariPath = tempDir.resolve("himawari.png");
        ImageFiles.save(solidColor(8, Color.BLUE), himawariPath, "png");

        SourceCalibration goes8 = new SourceCalibration("goes8",
            new DiscCalibration(0.0, -75.2, 35786.0, 0.5, 0.5, 0.5, 0.5));
        SourceCalibration himawari = new SourceCalibration("himawari",
            new DiscCalibration(0.0, 140.7, 35786.0, 0.5, 0.5, 0.5, 0.5));

        Path canonicalPath = tempDir.resolve("canonical.png");
        new UnwrapStage().run(
            List.of(
                new UnwrapStage.SourceInput(goes8, goes8Path),
                new UnwrapStage.SourceInput(himawari, himawariPath)),
            canonicalPath, 360, 180);

        // Exact coverage-boundary shape is already covered by SatelliteDiscProjectionTest/
        // SourceAccumulatorTest; this test just carries the canonical through combine/wrap.

        // combine: a green basemap should show through wherever the canonical is uncovered.
        Path basemapPath = tempDir.resolve("basemap.png");
        ImageFiles.save(solidColorOpaque(360, 180, 0x00FF00), basemapPath, "png");
        Path flattenedPath = tempDir.resolve("flattened.png");
        new CombineStage().run(canonicalPath, basemapPath, flattenedPath);

        BufferedImage flattened = ImageFiles.load(flattenedPath);
        // Near goes8's nadir, the flattened result should be red (not green/basemap).
        assertEquals(0xFF0000, flattened.getRGB(180, 90) & 0xFFFFFF, "near goes8 nadir should be red, not basemap");
        // Far from both discs should fall back to the basemap. With thetaMax ~= 81.3 degrees,
        // goes8 (sub-lon -75.2) covers roughly [-156.5, 6.1] and himawari (sub-lon 140.7) covers
        // roughly [59.4, 180] union [-180, -138.0] along the equator -- the only gap between them
        // is (6.1, 59.4), so lon=30 (pixel x=210 in a 360-wide canvas) is genuinely uncovered by
        // both.
        assertEquals(0x00FF00, flattened.getRGB(210, 90) & 0xFFFFFF, "far from both discs should show the basemap");

        // wrap: viewing the canonical from directly above goes8's own nadir should show red at
        // the center of the output frame.
        Path wrappedPath = tempDir.resolve("wrapped.png");
        ObserverParameters overGoes8 = new ObserverParameters(0.0, -75.2, 35786.0);
        new WrapStage().run(canonicalPath, overGoes8, 100, 100, wrappedPath);
        BufferedImage wrapped = ImageFiles.load(wrappedPath);
        assertEquals(0xFF0000, wrapped.getRGB(50, 50) & 0xFFFFFF, "wrap centered on goes8 should show red at center");

        // ... and viewing from himawari's nadir should show blue instead -- confirming wrap
        // actually samples the canonical rather than coincidentally always returning the same color.
        Path wrappedHimawariPath = tempDir.resolve("wrapped-himawari.png");
        ObserverParameters overHimawari = new ObserverParameters(0.0, 140.7, 35786.0);
        new WrapStage().run(canonicalPath, overHimawari, 100, 100, wrappedHimawariPath);
        BufferedImage wrappedHimawari = ImageFiles.load(wrappedHimawariPath);
        assertEquals(0x0000FF, wrappedHimawari.getRGB(50, 50) & 0xFFFFFF, "wrap centered on himawari should show blue");
        assertNotEquals(
            wrapped.getRGB(50, 50), wrappedHimawari.getRGB(50, 50), "the two wrap views should genuinely differ");
    }
}
