package me.qbert.globewrapping.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArgScannerTest {

    @Test
    void separatesPositionalsFromFlagValuePairs() {
        ArgScanner scanner = new ArgScanner(new String[] {"out.png", "goes8", "g8.jpg", "--config", "cfg.yaml"});
        assertEquals(List.of("out.png", "goes8", "g8.jpg"), scanner.positionals());
        assertEquals("cfg.yaml", scanner.option("config").orElseThrow());
    }

    @Test
    void supportsEqualsSyntax() {
        ArgScanner scanner = new ArgScanner(new String[] {"--config=cfg.yaml", "out.png"});
        assertEquals("cfg.yaml", scanner.option("config").orElseThrow());
        assertEquals(List.of("out.png"), scanner.positionals());
    }

    @Test
    void repeatableFlagsAreAllCaptured() {
        ArgScanner scanner = new ArgScanner(new String[] {
            "--override", "goes8.nadir_y=0.49", "--override", "goes8.radius_x=0.5",
        });
        List<ArgScanner.Option> overrides = scanner.options("override");
        assertEquals(2, overrides.size());
        assertEquals("goes8.nadir_y=0.49", overrides.get(0).value());
        assertEquals("goes8.radius_x=0.5", overrides.get(1).value());
    }

    @Test
    void unknownFlagIsAbsentNotAnError() {
        ArgScanner scanner = new ArgScanner(new String[] {"out.png"});
        assertTrue(scanner.option("config").isEmpty());
    }

    @Test
    void missingValueForFlagThrows() {
        assertThrows(CliUsageException.class, () -> new ArgScanner(new String[] {"out.png", "--config"}));
    }
}
