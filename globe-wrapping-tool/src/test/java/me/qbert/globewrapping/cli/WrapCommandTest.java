package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WrapCommandTest {

    @TempDir
    Path tempDir;

    private Path solidCanonical() throws IOException {
        BufferedImage image = new BufferedImage(360, 180, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 180; y++) {
            for (int x = 0; x < 360; x++) {
                image.setRGB(x, y, 0x336699);
            }
        }
        Path path = tempDir.resolve("canonical.png");
        ImageFiles.save(image, path, "png");
        return path;
    }

    @Test
    void validInvocationProducesOutputOfTheRequestedSize() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");

        int exitCode = new WrapCommand().run(new String[] {
            canonical.toString(), "center", "10,20", "height", "35786", "size", "100x80", output.toString(),
        });

        assertEquals(0, exitCode);
        BufferedImage result = ImageFiles.load(output);
        assertEquals(100, result.getWidth());
        assertEquals(80, result.getHeight());
    }

    @Test
    void malformedCenterThrows() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");
        assertThrows(CliUsageException.class, () -> new WrapCommand().run(new String[] {
            canonical.toString(), "center", "not-a-latlon", "height", "35786", "size", "100x80", output.toString(),
        }));
    }

    @Test
    void malformedSizeThrows() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");
        assertThrows(CliUsageException.class, () -> new WrapCommand().run(new String[] {
            canonical.toString(), "center", "10,20", "height", "35786", "size", "not-a-size", output.toString(),
        }));
    }

    @Test
    void missingKeywordTokensAreAUsageError() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");
        assertThrows(CliUsageException.class, () -> new WrapCommand().run(new String[] {
            canonical.toString(), "WRONGWORD", "10,20", "height", "35786", "size", "100x80", output.toString(),
        }));
    }

    @Test
    void omittingHeightDefaultsToTheSameResultAsExplicitReferenceAltitude() throws IOException {
        Path canonical = solidCanonical();
        Path withoutHeight = tempDir.resolve("without-height.png");
        Path withHeight = tempDir.resolve("with-height.png");

        new WrapCommand().run(new String[] {
            canonical.toString(), "center", "10,20", "size", "100x80", withoutHeight.toString(),
        });
        new WrapCommand().run(new String[] {
            canonical.toString(), "center", "10,20", "height", "35786", "size", "100x80", withHeight.toString(),
        });

        BufferedImage without = ImageFiles.load(withoutHeight);
        BufferedImage with = ImageFiles.load(withHeight);
        for (int y = 0; y < without.getHeight(); y++) {
            for (int x = 0; x < without.getWidth(); x++) {
                assertEquals(with.getRGB(x, y), without.getRGB(x, y), "pixel (" + x + "," + y + ") should match");
            }
        }
    }

    @Test
    void keywordOrderDoesNotMatter() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");

        int exitCode = new WrapCommand().run(new String[] {
            canonical.toString(), "size", "100x80", "center", "10,20", output.toString(),
        });

        assertEquals(0, exitCode);
        BufferedImage result = ImageFiles.load(output);
        assertEquals(100, result.getWidth());
        assertEquals(80, result.getHeight());
    }

    @Test
    void missingRequiredCenterIsAUsageError() throws IOException {
        Path canonical = solidCanonical();
        Path output = tempDir.resolve("wrapped.png");
        assertThrows(CliUsageException.class, () -> new WrapCommand().run(new String[] {
            canonical.toString(), "size", "100x80", output.toString(),
        }));
    }
}
