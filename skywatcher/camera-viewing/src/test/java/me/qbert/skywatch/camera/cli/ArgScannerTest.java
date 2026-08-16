package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ArgScannerTest {

	@Test
	void separatesPositionalsFromOptions() {
		ArgScanner scanner = new ArgScanner(new String[] { "one", "--flag", "value", "two" });

		assertEquals(Arrays.asList("one", "two"), scanner.positionals());
		assertEquals("value", scanner.requireOption("flag"));
	}

	@Test
	void supportsBothEqualsAndSpaceSeparatedForms() {
		ArgScanner equalsForm = new ArgScanner(new String[] { "--name=camera1" });
		ArgScanner spaceForm = new ArgScanner(new String[] { "--name", "camera1" });

		assertEquals("camera1", equalsForm.requireOption("name"));
		assertEquals("camera1", spaceForm.requireOption("name"));
	}

	@Test
	void missingValueForASpaceSeparatedFlagThrows() {
		assertThrows(CliUsageException.class, () -> new ArgScanner(new String[] { "--name" }));
	}

	@Test
	void requireOptionThrowsWhenMissing() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		assertThrows(CliUsageException.class, () -> scanner.requireOption("name"));
		assertFalse(scanner.option("name").isPresent());
	}

	@Test
	void repeatedFlagsAreAllCollectedInOrder() {
		ArgScanner scanner = new ArgScanner(new String[] { "--tag", "a", "--tag", "b", "--tag=c" });

		List<String> tags = scanner.options("tag");

		assertEquals(Arrays.asList("a", "b", "c"), tags);
		assertEquals("a", scanner.requireOption("tag"), "option(name) returns the first occurrence");
	}

	@Test
	void doubleOptionsParseOrFallBackToDefault() {
		ArgScanner scanner = new ArgScanner(new String[] { "--lat", "45.5" });

		assertEquals(45.5, scanner.requireDoubleOption("lat"), 1e-9);
		assertEquals(5.0, scanner.doubleOption("min-radius", 5.0), 1e-9);
	}

	@Test
	void intOptionsParseOrFallBackToDefault() {
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-scan-limit", "25" });

		assertEquals(25, scanner.intOption("cache-scan-limit", 10));
		assertEquals(10, scanner.intOption("some-other-flag", 10));
	}

	@Test
	void malformedIntThrowsAUsageException() {
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-scan-limit", "not-a-number" });

		assertThrows(CliUsageException.class, () -> scanner.intOption("cache-scan-limit", 10));
	}

	@Test
	void malformedNumberThrowsAUsageException() {
		ArgScanner scanner = new ArgScanner(new String[] { "--lat", "not-a-number" });

		assertThrows(CliUsageException.class, () -> scanner.requireDoubleOption("lat"));
	}

	@Test
	void booleanOptionsParseTrueAndFalseCaseInsensitivelyOrFallBackToDefault() {
		ArgScanner scanner = new ArgScanner(new String[] { "--labels", "TRUE", "--osd=False" });

		assertTrue(scanner.booleanOption("labels", false));
		assertFalse(scanner.booleanOption("osd", true));
		assertTrue(scanner.booleanOption("graticule", true), "an absent flag falls back to the given default");
	}

	@Test
	void malformedBooleanThrowsAUsageException() {
		ArgScanner scanner = new ArgScanner(new String[] { "--labels", "yes" });

		assertThrows(CliUsageException.class, () -> scanner.booleanOption("labels", false));
	}

	@Test
	void aFlagStartingWithDoubleDashIsNeverMistakenForAPositional() {
		ArgScanner scanner = new ArgScanner(new String[] { "--output=/tmp/out.png" });

		assertTrue(scanner.positionals().isEmpty());
	}
}
