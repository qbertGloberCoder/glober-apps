package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class TimezoneCatalogTest {

	@Test
	void loadParsesEachRowIntoAZoneIdAndCoordinates() throws Exception {
		String data = "Africa/Abidjan;5.31666;-4.03334\nAmerica/Toronto;43.7;-79.4\n";
		TimezoneCatalog catalog = TimezoneCatalog.load(stream(data));

		assertEquals(2, catalog.size());
	}

	@Test
	void blankLinesAreSkipped() throws Exception {
		String data = "Africa/Abidjan;5.31666;-4.03334\n\n\nAmerica/Toronto;43.7;-79.4\n";
		TimezoneCatalog catalog = TimezoneCatalog.load(stream(data));

		assertEquals(2, catalog.size());
	}

	@Test
	void malformedRowsAreSkippedRatherThanFailingTheWholeLoad() throws Exception {
		String data = "Africa/Abidjan;5.31666;-4.03334\n"
				+ "Not/A/Real/Zone;12.0;34.0\n" // invalid ZoneId
				+ "missing-fields;1.0\n" // wrong field count
				+ "America/Toronto;43.7;-79.4\n";
		TimezoneCatalog catalog = TimezoneCatalog.load(stream(data));

		assertEquals(2, catalog.size());
	}

	@Test
	void nearestZoneToReturnsTheClosestEntryByPlainCoordinateDistance() throws Exception {
		String data = "America/Toronto;43.7;-79.4\nAustralia/Sydney;-33.87;151.21\nEurope/London;51.5;-0.13\n";
		TimezoneCatalog catalog = TimezoneCatalog.load(stream(data));

		// Close to Toronto's own coordinates - should win over London/Sydney by a wide margin.
		assertEquals(ZoneId.of("America/Toronto"), catalog.nearestZoneTo(43.6, -79.5));
	}

	@Test
	void nearestZoneToOnAnEmptyCatalogThrows() throws Exception {
		TimezoneCatalog catalog = TimezoneCatalog.load(stream(""));

		assertThrows(IllegalStateException.class, () -> catalog.nearestZoneTo(0.0, 0.0));
	}

	@Test
	void loadFromClasspathFindsTheBundledResourceAndParsesRealRows() throws Exception {
		TimezoneCatalog catalog = TimezoneCatalog.loadFromClasspath();

		assertTrue(catalog.size() > 400, "expected the real bundled timezones.txt (418 rows) to load");
		// A sanity check against a real, known row rather than just a bare count.
		assertEquals(ZoneId.of("UTC"), catalog.nearestZoneTo(0.0, 0.0));
	}

	private InputStream stream(String data) throws IOException {
		return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
	}
}
