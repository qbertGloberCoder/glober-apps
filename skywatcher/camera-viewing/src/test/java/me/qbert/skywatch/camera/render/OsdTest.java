package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.watch.WatchedObject;

class OsdTest {

	private static final int CANVAS_SIZE = 300;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void summaryLinesMatchTheSpecsAlwaysOnFields() {
		Orientation orientation = new Orientation(10.5, 182.25, 1.5);

		List<String> lines = Osd.summaryLines(EPOCH_MILLIS, ZoneOffset.UTC, 45.0, -75.0, orientation);

		assertEquals(7, lines.size(), "date/time, timezone name, lat/lon, flat-earth day, roll, altitude, azimuth");
		// Lowercase "xxx" formats a zero UTC offset as "+00:00", not "Z" (that's uppercase "X").
		assertEquals("2024-08-09 00:00:00 +00:00", lines.get(0));
		assertEquals("Z", lines.get(1));
		assertEquals("Lat: 45.0000, Lon: -75.0000", lines.get(2));
		assertEquals("Flat-earth sun visibility: Day", lines.get(3));
		assertEquals("Roll: 1.5°", lines.get(4));
		assertEquals("Altitude: 10.5°", lines.get(5));
		assertEquals("Azimuth: 182.3°", lines.get(6));
	}

	@Test
	void flatEarthSunVisibilityIsAlwaysDayRegardlessOfActualTime() {
		// A real midnight timestamp - a globe-earth calculation would say "night" here, but the
		// flat-earth model has no mechanism to explain night at all [spec §8's own stated point].
		List<String> midnight = Osd.summaryLines(EPOCH_MILLIS, ZoneOffset.UTC, 45.0, -75.0, new Orientation(0, 0, 0));
		List<String> noon = Osd.summaryLines(EPOCH_MILLIS + 12L * 60 * 60 * 1000, ZoneOffset.UTC, 45.0, -75.0,
				new Orientation(0, 0, 0));

		assertEquals("Flat-earth sun visibility: Day", midnight.get(3));
		assertEquals("Flat-earth sun visibility: Day", noon.get(3));
	}

	@Test
	void rejectsNullTimezoneOrOrientation() {
		assertThrows(IllegalArgumentException.class,
				() -> Osd.summaryLines(EPOCH_MILLIS, null, 45.0, -75.0, new Orientation(0, 0, 0)));
		assertThrows(IllegalArgumentException.class,
				() -> Osd.summaryLines(EPOCH_MILLIS, ZoneOffset.UTC, 45.0, -75.0, null));
	}

	@Test
	void drawPaintsTextOntoTheCanvas() throws Exception {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

		Osd.draw(g2d, EPOCH_MILLIS, ZoneOffset.UTC, 45.0, -75.0, new Orientation(10.0, 90.0, 0.0), Color.WHITE,
				CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		boolean foundText = false;
		for (int y = 0; y < CANVAS_SIZE && !foundText; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) != (Color.BLACK.getRGB() & 0x00FFFFFF)) {
					foundText = true;
					break;
				}

		assertTrue(foundText, "expected the OSD to actually paint something onto the canvas");
	}

	@Test
	void watchedObjectDetailLinesCoverTheBuiltFields() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		List<String> lines = Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, ZoneOffset.UTC);

		// Watching, Local RA/Dec, Object RA/Dec, Altitude/Azimuth, Sub-object lat/lon, solar noon,
		// timezone noon meridian, moon orbit position, moon age, moon phase - see the class comment
		// for exactly why the remaining spec §8 fields (slope, average local RA, drift rates) aren't
		// included yet.
		assertEquals(10, lines.size());
		assertEquals("Watching: Sun", lines.get(0));
		assertTrue(lines.get(1).startsWith("Local RA:"));
		assertTrue(lines.get(2).startsWith("Object RA:"));
		assertTrue(lines.get(3).startsWith("Altitude:"));
		assertTrue(lines.get(4).startsWith("Sub-object:"));
		assertTrue(lines.get(5).startsWith("Nominal local solar noon:"));
		assertTrue(lines.get(6).startsWith("Timezone noon meridian:"));
		assertTrue(lines.get(7).startsWith("Moon orbit position:"));
		assertTrue(lines.get(8).startsWith("Moon age:"));
		assertTrue(lines.get(9).startsWith("Moon phase:"));
	}

	@Test
	void moonCycleLinesAreIndependentOfTheWatchedObject() throws Exception {
		// Same stance as "nominal local solar noon" - always about the Moon specifically, regardless
		// of what's currently being watched (see MoonPhase's own class comment).
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		List<String> sunLines = Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, ZoneOffset.UTC);
		List<String> jupiterLines = Osd.watchedObjectDetailLines(WatchedObject.planet(4), time, location, ZoneOffset.UTC);

		assertEquals(sunLines.get(7), jupiterLines.get(7));
		assertEquals(sunLines.get(8), jupiterLines.get(8));
		assertEquals(sunLines.get(9), jupiterLines.get(9));
	}

	@Test
	void moonPhaseLineNamesWaxingOrWaning() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		List<String> lines = Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, ZoneOffset.UTC);

		assertTrue(lines.get(9).endsWith("(waxing)") || lines.get(9).endsWith("(waning)"), lines.get(9));
	}

	@Test
	void watchingLineNamesTheActualWatchedObject() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		List<String> moonLines = Osd.watchedObjectDetailLines(WatchedObject.moon(), time, location, ZoneOffset.UTC);
		List<String> jupiterLines = Osd.watchedObjectDetailLines(WatchedObject.planet(4), time, location, ZoneOffset.UTC);

		assertEquals("Watching: Moon", moonLines.get(0));
		assertEquals("Watching: Jupiter", jupiterLines.get(0));
	}

	@Test
	void timezoneNoonMeridianMatchesTheZonesUtcOffset() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		// UTC-5 (US Eastern Standard Time's nominal offset) -> 15 degrees/hour * -5 = -75 degrees,
		// the classic "Eastern time zone is centered near 75W" fact.
		List<String> lines = Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, ZoneOffset.ofHours(-5));

		assertEquals("Timezone noon meridian: -75.0°", lines.get(6));
	}

	@Test
	void nominalLocalSolarNoonIsAPlausibleTimeOfDay() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		List<String> lines = Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, ZoneOffset.UTC);

		String solarNoonLine = lines.get(5);
		assertTrue(solarNoonLine.matches("Nominal local solar noon: \\d{2}:\\d{2}:\\d{2}"), solarNoonLine);
		// At UTC with this test's longitude (-75, i.e. well west of Greenwich), local solar noon
		// (uncorrected for any civil timezone) should land in the afternoon UTC clock, not near
		// UTC midnight - a coarse sanity bound, not pinning the exact NOAA-formula value.
		String clockPart = solarNoonLine.substring(solarNoonLine.length() - 8);
		int hour = Integer.parseInt(clockPart.substring(0, 2));
		assertTrue(hour >= 12 && hour <= 23, "expected solar noon at this longitude/UTC-timezone combination to fall in the afternoon UTC clock, got " + clockPart);
	}

	@Test
	void watchedObjectDetailLinesRejectsNullArguments() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		assertThrows(IllegalArgumentException.class,
				() -> Osd.watchedObjectDetailLines(null, time, location, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class,
				() -> Osd.watchedObjectDetailLines(WatchedObject.sun(), null, location, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class,
				() -> Osd.watchedObjectDetailLines(WatchedObject.sun(), time, null, ZoneOffset.UTC));
		assertThrows(IllegalArgumentException.class,
				() -> Osd.watchedObjectDetailLines(WatchedObject.sun(), time, location, null));
	}

	@Test
	void drawWatchedObjectDetailPaintsTextOntoTheCanvas() throws Exception {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);

		Osd.drawWatchedObjectDetail(g2d, WatchedObject.sun(), observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0),
				ZoneOffset.UTC, Color.WHITE, CANVAS_SIZE, CANVAS_SIZE, Osd.DEFAULT_DETAIL_TIER_TOP_Y_PIXELS);
		g2d.dispose();

		boolean foundText = false;
		for (int y = 0; y < CANVAS_SIZE && !foundText; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) != (Color.BLACK.getRGB() & 0x00FFFFFF)) {
					foundText = true;
					break;
				}

		assertTrue(foundText, "expected the watched-object detail tier to actually paint something onto the canvas");
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}
}
