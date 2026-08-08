package me.qbert.globewrapping.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import me.qbert.globewrapping.calibration.SourceCalibration;
import me.qbert.globewrapping.geometry.DiscCalibration;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnwrapStageTest {

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

    @Test
    void writesACoveredDiscAndLeavesTheFarSideTransparent() throws IOException {
        Path sourcePath = tempDir.resolve("source.png");
        ImageFiles.save(solidColor(8, Color.RED), sourcePath, "png");

        SourceCalibration calibration = new SourceCalibration("test",
            new DiscCalibration(0.0, 0.0, 35786.0, 0.5, 0.5, 0.5, 0.5));
        UnwrapStage.SourceInput input = new UnwrapStage.SourceInput(calibration, sourcePath);

        Path outputPath = tempDir.resolve("canonical.png");
        new UnwrapStage().run(List.of(input), outputPath, 36, 18);

        BufferedImage result = ImageFiles.load(outputPath);
        assertEquals(36, result.getWidth());
        assertEquals(18, result.getHeight());

        int nearNadirArgb = result.getRGB(18, 9); // pixel bucket containing (lat~0, lon~0)
        assertEquals(0xFF, (nearNadirArgb >>> 24) & 0xFF, "near-nadir pixel should be fully covered/opaque");

        int farSideArgb = result.getRGB(0, 9); // near the antimeridian, well beyond the source's limb
        assertEquals(0, (farSideArgb >>> 24) & 0xFF, "far side of the globe should stay uncovered/transparent");
    }

    @Test
    void outputPathWithNoExtensionThrowsIllegalArgumentException() throws IOException {
        UnwrapStage.SourceInput input = validSourceInput();

        Path outputPath = tempDir.resolve("canonical-no-extension");
        assertThrows(IllegalArgumentException.class,
            () -> new UnwrapStage().run(List.of(input), outputPath, 36, 18));
    }

    @Test
    void outputPathWithUnrecognizedFormatThrowsIOException() throws IOException {
        UnwrapStage.SourceInput input = validSourceInput();

        Path outputPath = tempDir.resolve("canonical.notarealformat");
        assertThrows(IOException.class,
            () -> new UnwrapStage().run(List.of(input), outputPath, 36, 18));
    }

    private UnwrapStage.SourceInput validSourceInput() throws IOException {
        Path sourcePath = tempDir.resolve("source.png");
        ImageFiles.save(solidColor(8, Color.RED), sourcePath, "png");
        SourceCalibration calibration = new SourceCalibration("test",
            new DiscCalibration(0.0, 0.0, 35786.0, 0.5, 0.5, 0.5, 0.5));
        return new UnwrapStage.SourceInput(calibration, sourcePath);
    }
}
