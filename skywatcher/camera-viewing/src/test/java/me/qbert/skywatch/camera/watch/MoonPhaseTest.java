package me.qbert.skywatch.camera.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;

class MoonPhaseTest {

	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void resolvesValuesInValidRanges() throws Exception {
		MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0));

		assertTrue(phase.getOrbitPositionDegrees() >= 0.0 && phase.getOrbitPositionDegrees() < 360.0,
				"orbit position out of range: " + phase.getOrbitPositionDegrees());
		assertTrue(phase.getIlluminatedFractionPercent() >= 0.0 && phase.getIlluminatedFractionPercent() <= 100.0,
				"illuminated fraction out of range: " + phase.getIlluminatedFractionPercent());
		assertTrue(phase.getAgeDays() >= 0.0 && phase.getAgeDays() < MoonPhase.SYNODIC_MONTH_DAYS,
				"age out of range: " + phase.getAgeDays());
	}

	@Test
	void orbitPositionPercentIsDerivedFromDegrees() throws Exception {
		MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0));

		assertEquals(phase.getOrbitPositionDegrees() / 360.0 * 100.0, phase.getOrbitPositionPercent(), 1e-9);
	}

	@Test
	void ageDaysIsDerivedFromOrbitPosition() throws Exception {
		MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0));

		assertEquals(phase.getOrbitPositionDegrees() / 360.0 * MoonPhase.SYNODIC_MONTH_DAYS, phase.getAgeDays(), 1e-9);
	}

	@Test
	void illuminatedFractionMatchesTheElongationFormula() throws Exception {
		MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0));

		double expected = (1.0 - Math.cos(Math.toRadians(phase.getOrbitPositionDegrees()))) / 2.0 * 100.0;
		assertEquals(expected, phase.getIlluminatedFractionPercent(), 1e-9);
	}

	@Test
	void waxingIsExactlyTheFirstHalfOfTheOrbit() throws Exception {
		MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0));

		assertEquals(phase.getOrbitPositionDegrees() < 180.0, phase.isWaxing());
	}

	@Test
	void phaseCyclesThroughNearNewAndNearFullOverASynodicMonth() throws Exception {
		// Sampling daily across a bit more than one synodic month should encounter both a near-New
		// (low illumination) and a near-Full (high illumination) sample somewhere in the run - a
		// structural confirmation that the cycle actually progresses, without pinning it to any
		// specific real-world date (which this test deliberately avoids asserting).
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		double minIllumination = 100.0;
		double maxIllumination = 0.0;

		for (int day = 0; day <= 31; day++) {
			MoonPhase phase = MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS + day * 24L * 60 * 60 * 1000), location);
			minIllumination = Math.min(minIllumination, phase.getIlluminatedFractionPercent());
			maxIllumination = Math.max(maxIllumination, phase.getIlluminatedFractionPercent());
		}

		assertTrue(minIllumination < 20.0, "expected a near-New sample somewhere in a 31-day span, min was " + minIllumination);
		assertTrue(maxIllumination > 80.0, "expected a near-Full sample somewhere in a 31-day span, max was " + maxIllumination);
	}

	@Test
	void rejectsNullArguments() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> MoonPhase.resolve(null, observerLocationAt(45.0, -75.0)));
		assertThrows(IllegalArgumentException.class, () -> MoonPhase.resolve(observationTimeAt(EPOCH_MILLIS), null));
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
