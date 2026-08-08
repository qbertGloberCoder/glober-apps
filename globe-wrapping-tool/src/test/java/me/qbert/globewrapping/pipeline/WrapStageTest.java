package me.qbert.globewrapping.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.geometry.ObserverParameters;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapStageTest {

    @TempDir
    Path tempDir;

    private static BufferedImage solidColor(int width, int height, int rgb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    @Test
    void centerPixelSamplesTheCanonicalAndCornerFallsBackToBackground() throws IOException {
        Path canonicalPath = tempDir.resolve("canonical.png");
        ImageFiles.save(solidColor(360, 180, 0x0000FF), canonicalPath, "png");

        ObserverParameters observer = new ObserverParameters(10.0, 20.0, 35786.0);
        Path outputPath = tempDir.resolve("wrapped.png");
        new WrapStage().run(canonicalPath, observer, 2000, 2000, outputPath);

        BufferedImage result = ImageFiles.load(outputPath);
        assertEquals(0x0000FF, result.getRGB(1000, 1000) & 0xFFFFFF, "center pixel should sample the canonical");
        assertEquals(WrapStage.DEFAULT_BACKGROUND.getRGB() & 0xFFFFFF, result.getRGB(0, 0) & 0xFFFFFF,
            "far corner (well beyond the horizon at GEO altitude) should fall back to the background");
    }

    @Test
    void wrapCentersOnTheRequestedLongitude() throws IOException {
        // Left half of the canonical (western longitudes) is red, right half (eastern) is blue.
        BufferedImage canonical = new BufferedImage(360, 180, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 180; y++) {
            for (int x = 0; x < 360; x++) {
                canonical.setRGB(x, y, x < 180 ? 0xFF0000 : 0x0000FF);
            }
        }
        Path canonicalPath = tempDir.resolve("canonical.png");
        ImageFiles.save(canonical, canonicalPath, "png");

        ObserverParameters overWesternHemisphere = new ObserverParameters(0.0, -90.0, 35786.0);
        Path westOutput = tempDir.resolve("west.png");
        new WrapStage().run(canonicalPath, overWesternHemisphere, 200, 200, westOutput);
        int westCenter = ImageFiles.load(westOutput).getRGB(100, 100) & 0xFFFFFF;
        assertEquals(0xFF0000, westCenter);

        ObserverParameters overEasternHemisphere = new ObserverParameters(0.0, 90.0, 35786.0);
        Path eastOutput = tempDir.resolve("east.png");
        new WrapStage().run(canonicalPath, overEasternHemisphere, 200, 200, eastOutput);
        int eastCenter = ImageFiles.load(eastOutput).getRGB(100, 100) & 0xFFFFFF;
        assertEquals(0x0000FF, eastCenter);
    }
}
