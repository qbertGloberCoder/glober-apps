package me.qbert.skywatch.camera.astro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.astro.service.AbstractPrecession.PrecessionData;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

// Phase 2.1/2.2 [docs/tasks.md]: confirms sw-base's existing astro classes cover what
// camera-viewing needs (alt-az/RA-Dec for sun/moon, and the already-implemented
// ecliptic/analemma path sampling) rather than re-deriving/porting org.bluerock's calculators.
// Structural coverage only (valid ranges, non-null, non-empty) - astronomical accuracy
// verification against old_draw_project's output is Phase 9's job, not this one's.
class SwBaseAstroCoverageTest {

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

	@Test
	void sunObjectProvidesAltAzAndRaDecForACamera() throws Exception {
		ObservationTime time = observationTimeAt(1_723_161_600_000L); // 2024-08-09T00:00:00Z
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		// build() alone does not compute a position - it only registers as an ObservationTime/
		// ObserverLocation listener, so an explicit recompute() is required before the first read
		// (discovered while wiring me.qbert.skywatch.camera.render.CelestialObjectsLayer - see its
		// class comment). Without this, the object silently reports a degenerate default (altitude
		// = 90 - latitude, azimuth = 180) that happens to satisfy this test's loose range checks
		// anyway, which is exactly why this was never caught here.
		sun.recompute();

		ObjectDirectionAltAz altAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		assertValidAltAz(altAz);
		assertNotDegenerateDefault(altAz, location.getLatitude());

		ObjectDirectionRaDec raDec = sun.getCelestialSphereLocation();
		assertValidRaDec(raDec);
	}

	@Test
	void moonObjectProvidesAltAzAndRaDecForACamera() throws Exception {
		ObservationTime time = observationTimeAt(1_723_161_600_000L);
		ObserverLocation location = observerLocationAt(35.0, 139.0);

		CelestialObject moon = MoonObject.create().setObserverLocation(location).setObserverTime(time).build();
		moon.recompute(); // see sunObjectProvidesAltAzAndRaDecForACamera's comment

		ObjectDirectionAltAz altAz = moon.getCurrentDirectionAsAltitudeAzimuth();
		assertValidAltAz(altAz);
		assertNotDegenerateDefault(altAz, location.getLatitude());
		assertValidRaDec(moon.getCelestialSphereLocation());
	}

	@Test
	void sunAndMoonExposeEclipticLongitudeForPhaseComputation() throws Exception {
		// SunObject.getApparentEclipticLongitudeDegrees()/MoonObject.getEclipticLongitudeDegrees() -
		// added for camera-viewing's watch.MoonPhase (elongation = moon longitude - sun longitude).
		ObservationTime time = observationTimeAt(1_723_161_600_000L);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		SunObject sun = (SunObject) SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		MoonObject moon = (MoonObject) MoonObject.create().setObserverLocation(location).setObserverTime(time).build();
		moon.recompute();

		assertValidDegrees(sun.getApparentEclipticLongitudeDegrees());
		assertValidDegrees(moon.getEclipticLongitudeDegrees());
	}

	@Test
	void sunPrecessionAlreadyImplementsEclipticSampling() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		SunPrecession precession = new SunPrecession(location, false);

		ObservationTime endTime = observationTimeAt(1_723_161_600_000L);
		List<PrecessionData> points = precession.calculatePrecession(endTime);

		// ~365 daily samples across one orbital period, per SunPrecession's fixed 86400s advance.
		assertTrue(points.size() > 300 && points.size() < 370,
				"expected roughly a year of daily samples, got " + points.size());
		for (PrecessionData point : points) {
			assertValidRaDec(point.getRaDec());
		}
	}

	@Test
	void sunPrecessionAnalemmaModeProducesTheSameSampleCount() throws Exception {
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObservationTime endTime = observationTimeAt(1_723_161_600_000L);

		SunPrecession ecliptic = new SunPrecession(location, false);
		SunPrecession analemma = new SunPrecession(observerLocationAt(45.0, -75.0), true);

		int eclipticCount = ecliptic.calculatePrecession(endTime).size();
		int analemmaCount = analemma.calculatePrecession(observationTimeAt(1_723_161_600_000L)).size();

		assertEquals(eclipticCount, analemmaCount,
				"ecliptic and analemma modes sample the same time series, differing only in the RA collapse");
	}

	private void assertValidAltAz(ObjectDirectionAltAz altAz) {
		assertTrue(altAz.getAltitude() >= -90.0 && altAz.getAltitude() <= 90.0,
				"altitude out of range: " + altAz.getAltitude());
		double az = altAz.getAzimuth();
		assertTrue(az >= -0.001 && az <= 360.001, "azimuth out of range: " + az);
		assertFalse(Double.isNaN(altAz.getAltitude()) || Double.isNaN(az));
	}

	private void assertValidDegrees(double degrees) {
		assertFalse(Double.isNaN(degrees));
		assertTrue(degrees >= -0.001 && degrees <= 360.001, "ecliptic longitude out of range: " + degrees);
	}

	private void assertValidRaDec(ObjectDirectionRaDec raDec) {
		assertFalse(Double.isNaN(raDec.getRightAscension()) || Double.isNaN(raDec.getDeclination()));
		assertTrue(raDec.getDeclination() >= -90.0 && raDec.getDeclination() <= 90.0,
				"declination out of range: " + raDec.getDeclination());
	}

	// Regression guard for the recompute()-after-build() gotcha - a never-recomputed object
	// resolves to exactly altitude = 90 - latitude, azimuth = 180 (RA=Dec=0's degenerate result),
	// which is otherwise indistinguishable from a real position by assertValidAltAz's loose checks.
	private void assertNotDegenerateDefault(ObjectDirectionAltAz altAz, double observerLatitude) {
		boolean looksDegenerate = Math.abs(altAz.getAltitude() - (90.0 - observerLatitude)) < 1e-6
				&& Math.abs(altAz.getAzimuth() - 180.0) < 1e-6;
		assertFalse(looksDegenerate,
				"altitude/azimuth match the exact degenerate un-recomputed default - recompute() was likely not called");
	}
}
