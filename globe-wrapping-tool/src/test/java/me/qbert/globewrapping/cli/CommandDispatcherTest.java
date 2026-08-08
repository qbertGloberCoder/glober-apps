package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import me.qbert.globewrapping.image.ImageFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandDispatcherTest {

    @TempDir
    Path tempDir;

    @Test
    void emptyArgsIsAUsageError() {
        assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(new String[0]));
    }

    @Test
    void unknownSubcommandIsAUsageError() {
        assertThrows(CliUsageException.class, () -> new CommandDispatcher().run(new String[] {"not-a-subcommand"}));
    }

    @Test
    void dispatchesToUnwrap() throws IOException {
        BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 8, 8);
        g.dispose();
        Path source = tempDir.resolve("goes8.png");
        ImageFiles.save(image, source, "png");

        Path output = tempDir.resolve("canonical.png");
        int exitCode = new CommandDispatcher().run(new String[] {"unwrap", output.toString(), "goes8", source.toString()});

        assertEquals(0, exitCode);
    }
}
