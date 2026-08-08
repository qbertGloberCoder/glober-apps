package me.qbert.globewrapping.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombineStageTest {

    @TempDir
    Path tempDir;

    /** A 10x10 canonical image: fully transparent except one opaque red pixel at (5, 5). */
    private static BufferedImage mostlyUncoveredCanonical() {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(5, 5, 0xFFFF0000);
        return image;
    }

    private static BufferedImage solidColor(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        return image;
    }

    @Test
    void basemapShowsThroughUncoveredRegions() throws IOException {
        Path canonicalPath = tempDir.resolve("canonical.png");
        ImageFiles.save(mostlyUncoveredCanonical(), canonicalPath, "png");

        Path basemapPath = tempDir.resolve("basemap.png");
        ImageFiles.save(solidColor(10, 10, Color.GREEN), basemapPath, "png");

        // .png (lossless) rather than .jpg here so the exact-value color assertions below
        // aren't at the mercy of JPEG quantization; CombineStage is format-agnostic either way.
        Path outputPath = tempDir.resolve("flattened.png");
        new CombineStage().run(canonicalPath, basemapPath, outputPath);

        BufferedImage result = ImageFiles.load(outputPath);
        assertEquals(0x00FF00, result.getRGB(0, 0) & 0xFFFFFF, "uncovered area should show the basemap");
        assertEquals(0xFF0000, result.getRGB(5, 5) & 0xFFFFFF, "covered pixel should keep the canonical's color");
    }

    @Test
    void withoutABasemapUncoveredRegionsFallBackToTheDefaultBackground() throws IOException {
        Path canonicalPath = tempDir.resolve("canonical.png");
        ImageFiles.save(mostlyUncoveredCanonical(), canonicalPath, "png");

        // .png (lossless) rather than .jpg here so the exact-value color assertions below
        // aren't at the mercy of JPEG quantization; CombineStage is format-agnostic either way.
        Path outputPath = tempDir.resolve("flattened.png");
        new CombineStage().run(canonicalPath, null, outputPath);

        BufferedImage result = ImageFiles.load(outputPath);
        assertEquals(CombineStage.DEFAULT_BACKGROUND.getRGB() & 0xFFFFFF, result.getRGB(0, 0) & 0xFFFFFF);
        assertEquals(0xFF0000, result.getRGB(5, 5) & 0xFFFFFF);
    }
}
