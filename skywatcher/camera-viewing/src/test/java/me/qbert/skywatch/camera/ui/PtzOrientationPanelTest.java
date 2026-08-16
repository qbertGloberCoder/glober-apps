package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;

// PtzOrientationPanel itself is a bare JPanel (not a Window/Frame/Dialog), which doesn't throw
// HeadlessException on construction - see CalibrationPanel's own note. These tests exercise
// integrate(...)'s wrap/clamp math directly, per the user's own exact specification: "azimuth
// should just spin in either direction forever, wrapping at 0/360, tilt should stop at + and - 90
// degrees."
class PtzOrientationPanelTest {

	@Test
	void panningForwardAdvancesAzimuthProportionallyToRateAndElapsedTime() {
		Orientation current = new Orientation(0.0, 100.0, 5.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 10.0, 0.0, 2.0);

		assertEquals(120.0, updated.getAzimuth(), 1e-9);
		assertEquals(0.0, updated.getAltitude(), 1e-9, "tilt rate was zero - altitude must not change");
		assertEquals(5.0, updated.getBarrelRoll(), 1e-9, "barrel roll is not controlled here - must carry over unchanged");
	}

	@Test
	void azimuthWrapsPastThreeSixtyInsteadOfExceedingIt() {
		Orientation current = new Orientation(0.0, 350.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 20.0, 0.0, 1.0);

		assertEquals(10.0, updated.getAzimuth(), 1e-9, "350 + 20 = 370, which should wrap to 10, not sit at 370");
	}

	@Test
	void azimuthWrapsBelowZeroBackToNearThreeSixty() {
		Orientation current = new Orientation(0.0, 10.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, -20.0, 0.0, 1.0);

		assertEquals(350.0, updated.getAzimuth(), 1e-9, "10 - 20 = -10, which should wrap to 350, not go negative");
	}

	@Test
	void panningContinuesIndefinitelyAcrossManyWraps() {
		// A pan control must "just spin in either direction forever" - confirm several consecutive
		// wraps all land correctly, not just a single crossing.
		Orientation current = new Orientation(0.0, 0.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 100.0, 0.0, 10.0); // 1000 degrees of travel

		assertEquals(1000.0 % 360.0, updated.getAzimuth(), 1e-9);
	}

	@Test
	void tiltStopsExactlyAtPositiveNinetyInsteadOfContinuingPast() {
		Orientation current = new Orientation(85.0, 0.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 0.0, 20.0, 1.0); // would reach 105 unclamped

		assertEquals(90.0, updated.getAltitude(), 1e-9);
	}

	@Test
	void tiltStopsExactlyAtNegativeNinetyInsteadOfContinuingPast() {
		Orientation current = new Orientation(-85.0, 0.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 0.0, -20.0, 1.0); // would reach -105 unclamped

		assertEquals(-90.0, updated.getAltitude(), 1e-9);
	}

	@Test
	void tiltWithinRangeIsUnaffectedByTheClamp() {
		Orientation current = new Orientation(0.0, 0.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 0.0, 15.0, 2.0);

		assertEquals(30.0, updated.getAltitude(), 1e-9);
	}

	@Test
	void bothAxesIntegrateSimultaneouslyWithoutInterference() {
		Orientation current = new Orientation(0.0, 0.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 30.0, 10.0, 1.0);

		assertEquals(30.0, updated.getAzimuth(), 1e-9);
		assertEquals(10.0, updated.getAltitude(), 1e-9);
	}

	@Test
	void zeroElapsedTimeMeansNoMovement() {
		Orientation current = new Orientation(12.0, 34.0, 0.0);

		Orientation updated = PtzOrientationPanel.integrate(current, 100.0, 100.0, 0.0);

		assertEquals(34.0, updated.getAzimuth(), 1e-9);
		assertEquals(12.0, updated.getAltitude(), 1e-9);
	}

	@Test
	void newPanelStartsWithZeroRatesAndIsConstructibleHeadlessly() {
		PtzOrientationPanel panel = new PtzOrientationPanel();

		assertEquals(0.0, panel.getPanRateDegreesPerSecond());
		assertEquals(0.0, panel.getTiltRateDegreesPerSecond());
		assertEquals(0.0, panel.getZoomRateMillimetersPerSecond());
	}

	// --- integrateZoom(...) - new this round, the user's own follow-up ask ---

	@Test
	void zoomingInIncreasesFocalLengthProportionallyToRateAndElapsedTime() {
		double updated = PtzOrientationPanel.integrateZoom(50.0, 20.0, 2.0);

		assertEquals(90.0, updated, 1e-9);
	}

	@Test
	void zoomingOutDecreasesFocalLength() {
		double updated = PtzOrientationPanel.integrateZoom(50.0, -20.0, 1.0);

		assertEquals(30.0, updated, 1e-9);
	}

	@Test
	void zoomClampsAtTheMaximumInsteadOfContinuingPast() {
		double updated = PtzOrientationPanel.integrateZoom(1990.0, 100.0, 1.0); // would reach 2090 unclamped

		assertEquals(PtzOrientationPanel.MAX_FOCAL_LENGTH_MILLIMETERS, updated, 1e-9);
	}

	@Test
	void zoomClampsAtTheMinimumInsteadOfGoingBelowIt() {
		double updated = PtzOrientationPanel.integrateZoom(5.0, -100.0, 1.0); // would reach -95 unclamped

		assertEquals(PtzOrientationPanel.MIN_FOCAL_LENGTH_MILLIMETERS, updated, 1e-9);
	}

	@Test
	void zoomDoesNotWrapUnlikeAzimuth() {
		// A real behavioral distinction from integrate(...)'s azimuth handling - zoom is a physical
		// lens limit, not a rotation, so it must clamp (stop dead) rather than wrap back around.
		double updated = PtzOrientationPanel.integrateZoom(PtzOrientationPanel.MAX_FOCAL_LENGTH_MILLIMETERS, 50.0, 1.0);

		assertEquals(PtzOrientationPanel.MAX_FOCAL_LENGTH_MILLIMETERS, updated, 1e-9,
				"zoom must stay pinned at the max, not wrap back toward the minimum");
	}
}
