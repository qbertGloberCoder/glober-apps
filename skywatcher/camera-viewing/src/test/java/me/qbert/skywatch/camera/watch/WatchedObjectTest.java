package me.qbert.skywatch.camera.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

class WatchedObjectTest {

	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void sunHasExpectedKindAndDisplayName() {
		WatchedObject sun = WatchedObject.sun();
		assertEquals(WatchedObject.Kind.SUN, sun.getKind());
		assertEquals("Sun", sun.getDisplayName());
	}

	@Test
	void moonHasExpectedKindAndDisplayName() {
		WatchedObject moon = WatchedObject.moon();
		assertEquals(WatchedObject.Kind.MOON, moon.getKind());
		assertEquals("Moon", moon.getDisplayName());
	}

	@Test
	void planetHasExpectedKindIndexAndDisplayName() {
		WatchedObject jupiter = WatchedObject.planet(4);
		assertEquals(WatchedObject.Kind.PLANET, jupiter.getKind());
		assertEquals(4, jupiter.getPlanetIndex());
		assertEquals("Jupiter", jupiter.getDisplayName());
	}

	@Test
	void planetRejectsIndexZeroAndOutOfRange() {
		assertThrows(IllegalArgumentException.class, () -> WatchedObject.planet(0));
		assertThrows(IllegalArgumentException.class, () -> WatchedObject.planet(SolarObjects.OBJECT_LIST.length));
	}

	@Test
	void starHasExpectedKindAndDisplayName() {
		StarCoordinate vega = new StarCoordinate("Vega", "Alpha Lyrae", 0.03, 279.23, 38.78, 1, true);
		WatchedObject star = WatchedObject.star(vega);
		assertEquals(WatchedObject.Kind.STAR, star.getKind());
		assertEquals(vega, star.getStar());
		assertEquals("Vega", star.getDisplayName());
	}

	@Test
	void starRejectsNull() {
		assertThrows(IllegalArgumentException.class, () -> WatchedObject.star(null));
	}

	@Test
	void accessorsAreGatedByKind() {
		WatchedObject sun = WatchedObject.sun();
		assertThrows(IllegalStateException.class, sun::getPlanetIndex);
		assertThrows(IllegalStateException.class, sun::getStar);

		WatchedObject jupiter = WatchedObject.planet(4);
		assertThrows(IllegalStateException.class, jupiter::getStar);

		WatchedObject vega = WatchedObject.star(new StarCoordinate("Vega", "Alpha Lyrae", 0.03, 279.23, 38.78, 1, true));
		assertThrows(IllegalStateException.class, vega::getPlanetIndex);
	}

	@Test
	void resolvesSunPositionForACamera() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		ObjectDirectionAltAz altAz = WatchedObject.sun().resolveAltAz(time, location);
		assertNotDegenerateDefault(altAz, location.getLatitude());

		ObjectDirectionRaDec raDec = WatchedObject.sun().resolveRaDec(time, location);
		assertValidRaDec(raDec);
	}

	@Test
	void resolvesMoonPositionForACamera() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		ObjectDirectionAltAz altAz = WatchedObject.moon().resolveAltAz(time, location);
		assertNotDegenerateDefault(altAz, location.getLatitude());
	}

	@Test
	void resolvesPlanetPositionForACamera() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		ObjectDirectionAltAz altAz = WatchedObject.planet(4).resolveAltAz(time, location);
		assertNotDegenerateDefault(altAz, location.getLatitude());
	}

	@Test
	void differentPlanetIndicesResolveToDifferentPositions() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		ObjectDirectionAltAz venus = WatchedObject.planet(2).resolveAltAz(time, location);
		ObjectDirectionAltAz jupiter = WatchedObject.planet(4).resolveAltAz(time, location);

		assertFalse(Math.abs(venus.getAltitude() - jupiter.getAltitude()) < 1e-9
				&& Math.abs(venus.getAzimuth() - jupiter.getAzimuth()) < 1e-9,
				"different planets should not resolve to the exact same position");
	}

	@Test
	void resolvesStarPositionForACamera() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		StarCoordinate vega = new StarCoordinate("Vega", "Alpha Lyrae", 0.03, 279.23, 38.78, 1, true);

		ObjectDirectionAltAz altAz = WatchedObject.star(vega).resolveAltAz(time, location);
		assertNotDegenerateDefault(altAz, location.getLatitude());
	}

	@Test
	void resolvesCelestialSphereLocationDistinctFromLocalRaDec() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject sun = WatchedObject.sun();

		ObjectDirectionRaDec celestial = sun.resolveCelestialSphereLocation(time, location);
		ObjectDirectionRaDec local = sun.resolveRaDec(time, location);

		assertValidRaDec(celestial);
		// The two are computed via genuinely different CelestialObject methods (getCelestialSphereLocation()
		// vs getCurrentDirection()) - not asserting a specific numeric relationship between them, just
		// that resolveCelestialSphereLocation actually calls the distinct method rather than aliasing
		// resolveRaDec's own hour-angle-adjusted value.
		assertFalse(Math.abs(celestial.getRightAscension() - local.getRightAscension()) < 1e-9,
				"celestial-sphere RA and local/hour-angle RA should not be numerically identical here");
	}

	@Test
	void resolvesSubObjectLocationForACamera() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		GeoLocation subObject = WatchedObject.sun().resolveSubObjectLocation(time, location);

		assertFalse(Double.isNaN(subObject.getLatitude()) || Double.isNaN(subObject.getLongitude()));
		assertTrue(subObject.getLatitude() >= -90.0 && subObject.getLatitude() <= 90.0,
				"sub-object latitude out of range: " + subObject.getLatitude());
	}

	@Test
	void resolveAtTwoDifferentTimesGivesDifferentPositions() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject sun = WatchedObject.sun();

		ObjectDirectionAltAz first = sun.resolveAltAz(observationTimeAt(EPOCH_MILLIS), location);
		ObjectDirectionAltAz sixHoursLater = sun.resolveAltAz(observationTimeAt(EPOCH_MILLIS + 6L * 60 * 60 * 1000),
				location);

		assertFalse(Math.abs(first.getAltitude() - sixHoursLater.getAltitude()) < 1e-6
				&& Math.abs(first.getAzimuth() - sixHoursLater.getAzimuth()) < 1e-6,
				"the sun's position 6 hours later should differ - this is the whole point of a reusable identity");
	}

	@Test
	void rejectsNullTimeOrLocation() {
		assertThrows(IllegalArgumentException.class,
				() -> WatchedObject.sun().resolveAltAz(null, observerLocationAt(45.0, -75.0)));
		assertThrows(IllegalArgumentException.class,
				() -> WatchedObject.sun().resolveAltAz(observationTimeAt(EPOCH_MILLIS), null));
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

	private void assertValidRaDec(ObjectDirectionRaDec raDec) {
		assertFalse(Double.isNaN(raDec.getRightAscension()) || Double.isNaN(raDec.getDeclination()));
		assertTrue(raDec.getDeclination() >= -90.0 && raDec.getDeclination() <= 90.0,
				"declination out of range: " + raDec.getDeclination());
	}

	// Same regression guard as SwBaseAstroCoverageTest - a never-recomputed object resolves to
	// exactly altitude = 90 - latitude, azimuth = 180 (RA=Dec=0's degenerate result).
	private void assertNotDegenerateDefault(ObjectDirectionAltAz altAz, double observerLatitude) {
		boolean looksDegenerate = Math.abs(altAz.getAltitude() - (90.0 - observerLatitude)) < 1e-6
				&& Math.abs(altAz.getAzimuth() - 180.0) < 1e-6;
		assertFalse(looksDegenerate,
				"altitude/azimuth match the exact degenerate un-recomputed default - recompute() was likely not called");
	}
}
