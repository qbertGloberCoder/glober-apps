package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnwrapCommandTest {

    @TempDir
    Path tempDir;

    private Path writeSolidColorSource(String name, Color color) throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        Path path = tempDir.resolve(name);
        ImageFiles.save(image, path, "png");
        return path;
    }

    @Test
    void validInvocationWritesACoveredCanonicalImage() throws IOException {
        Path source = writeSolidColorSource("goes8.png", Color.RED);
        Path output = tempDir.resolve("canonical.png");

        int exitCode = new UnwrapCommand().run(new String[] {output.toString(), "goes8", source.toString()});

        assertEquals(0, exitCode);
        assertTrue(ImageFiles.load(output).getWidth() > 0);
    }

    @Test
    void unknownAliasIsAUsageErrorNamingKnownAliases() throws IOException {
        Path source = writeSolidColorSource("mystery.png", Color.RED);
        Path output = tempDir.resolve("canonical.png");

        CliUsageException exception = assertThrows(CliUsageException.class,
            () -> new UnwrapCommand().run(new String[] {output.toString(), "not-a-real-alias", source.toString()}));
        assertTrue(exception.getMessage().contains("goes8"));
    }

    @Test
    void wrongArgumentCountIsAUsageError() {
        assertThrows(CliUsageException.class,
            () -> new UnwrapCommand().run(new String[] {"only-output.png"}));
        assertThrows(CliUsageException.class,
            () -> new UnwrapCommand().run(new String[] {"output.png", "goes8"})); // dangling alias, no path
    }

    @Test
    void perAliasOverrideFlagIsAcceptedEndToEnd() throws IOException {
        Path source = writeSolidColorSource("goes8.png", Color.RED);
        Path output = tempDir.resolve("canonical.png");

        int exitCode = new UnwrapCommand().run(new String[] {
            output.toString(), "goes8", source.toString(),
            "--override", "goes8.nadir_y=0.49",
        });

        assertEquals(0, exitCode);
    }
}
