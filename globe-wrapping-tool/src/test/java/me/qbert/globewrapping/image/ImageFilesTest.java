package me.qbert.globewrapping.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageFilesTest {

    @TempDir
    Path tempDir;

    @Test
    void savedImageLoadsBackWithSamePixels() throws IOException {
        BufferedImage original = new BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB);
        original.setRGB(1, 2, 0xFF336699);

        Path file = tempDir.resolve("roundtrip.png");
        ImageFiles.save(original, file, "png");

        BufferedImage loaded = ImageFiles.load(file);
        assertEquals(4, loaded.getWidth());
        assertEquals(3, loaded.getHeight());
        assertEquals(0xFF336699, loaded.getRGB(1, 2));
    }

    @Test
    void loadingMissingFileThrowsIOException() {
        Path missing = tempDir.resolve("does-not-exist.png");
        assertThrows(IOException.class, () -> ImageFiles.load(missing));
    }

    @Test
    void savingWithUnknownFormatThrowsIOException() {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Path file = tempDir.resolve("out.bogus");
        assertThrows(IOException.class, () -> ImageFiles.save(image, file, "not-a-real-format"));
    }
}
