package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombineCommandTest {

    @TempDir
    Path tempDir;

    @Test
    void basemapPathIsUsedWhenGiven() throws IOException {
        Path canonical = tempDir.resolve("canonical.png");
        BufferedImage transparent = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        ImageFiles.save(transparent, canonical, "png");

        Path basemap = tempDir.resolve("basemap.png");
        BufferedImage green = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                green.setRGB(x, y, 0x00FF00);
            }
        }
        ImageFiles.save(green, basemap, "png");

        Path output = tempDir.resolve("flattened.png");
        int exitCode = new CombineCommand().run(new String[] {basemap.toString(), canonical.toString(), output.toString()});

        assertEquals(0, exitCode);
        assertEquals(0x00FF00, ImageFiles.load(output).getRGB(0, 0) & 0xFFFFFF);
    }

    @Test
    void noneKeywordSkipsTheBasemap() throws IOException {
        Path canonical = tempDir.resolve("canonical.png");
        ImageFiles.save(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB), canonical, "png");

        Path output = tempDir.resolve("flattened.png");
        int exitCode = new CombineCommand().run(new String[] {"none", canonical.toString(), output.toString()});

        assertEquals(0, exitCode);
    }

    @Test
    void wrongArgumentCountIsAUsageError() {
        assertThrows(CliUsageException.class, () -> new CombineCommand().run(new String[] {"only-one.png"}));
    }
}
