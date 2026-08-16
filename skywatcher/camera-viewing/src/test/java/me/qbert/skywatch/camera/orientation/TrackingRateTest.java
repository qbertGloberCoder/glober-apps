package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

// Task 9.2's golden-value check: the three tracking-rate constants, verified directly against the
// user's own supplied "standard rates" reference table (arcsec/hour):
//   Sidereal 54,000.0 | Solar 53,997.7 (99.996% of sidereal) | Lunar 52,145.0 (96.56% of sidereal)
// degrees/hour = arcsec/hour / 3600.
class TrackingRateTest {

	private static final double ARCSEC_PER_HOUR_TO_DEGREES_PER_HOUR = 1.0 / 3600.0;

	@Test
	void siderealRateMatchesTheStandardRatesTable() {
		assertEquals(54000.0 * ARCSEC_PER_HOUR_TO_DEGREES_PER_HOUR, TrackingRate.SIDEREAL.getDegreesPerHour(), 0.00001);
		assertEquals(15.0, TrackingRate.SIDEREAL.getDegreesPerHour(), 0.00001, "54,000 arcsec/hour is exactly 15 deg/hour");
	}

	@Test
	void solarRateMatchesTheStandardRatesTable() {
		assertEquals(53997.7 * ARCSEC_PER_HOUR_TO_DEGREES_PER_HOUR, TrackingRate.SOLAR.getDegreesPerHour(), 0.00001);
	}

	@Test
	void lunarRateMatchesTheStandardRatesTable() {
		assertEquals(52145.0 * ARCSEC_PER_HOUR_TO_DEGREES_PER_HOUR, TrackingRate.LUNAR.getDegreesPerHour(), 0.00001);
	}

	@Test
	void solarRateIsTheTablesStatedPercentageOfSidereal() {
		double percentOfSidereal = TrackingRate.SOLAR.getDegreesPerHour() / TrackingRate.SIDEREAL.getDegreesPerHour() * 100.0;
		assertEquals(99.996, percentOfSidereal, 0.001);
	}

	@Test
	void lunarRateIsTheTablesStatedPercentageOfSidereal() {
		double percentOfSidereal = TrackingRate.LUNAR.getDegreesPerHour() / TrackingRate.SIDEREAL.getDegreesPerHour() * 100.0;
		assertEquals(96.56, percentOfSidereal, 0.01);
	}

	@Test
	void siderealRateCompletesAFullRotationInExactlyTwentyFourHours() {
		// A direct consequence of the standard-rates table's sidereal value being exactly 15.0
		// deg/hour (not the more precise 15.0410686 an earlier round used) - see
		// EquatorialMountTransformTest's own equivalent test for the same fact verified through the
		// actual mount rotation, not just this arithmetic.
		assertEquals(360.0, TrackingRate.SIDEREAL.getDegreesPerHour() * 24.0, 0.00001);
	}
}
