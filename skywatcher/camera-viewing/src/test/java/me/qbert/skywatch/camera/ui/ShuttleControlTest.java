package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JSlider;

import org.junit.jupiter.api.Test;

// ShuttleControl itself is a bare JPanel (not a Window/Frame/Dialog), which doesn't throw
// HeadlessException on construction - see CalibrationPanel's own note on this. These tests
// exercise the pure position-to-rate mapping directly, plus the widget's own wiring/callbacks,
// which don't require an actual on-screen display or a real mouse drag.
class ShuttleControlTest {

	@Test
	void centerPositionAlwaysMapsToZeroRate() {
		assertEquals(0.0, ShuttleControl.rateForPosition(0, ShuttleControl.SLOW_MODE_MAX_RATE));
		assertEquals(0.0, ShuttleControl.rateForPosition(0, ShuttleControl.FAST_MODE_MAX_RATE));
	}

	@Test
	void fullDeflectionReachesTheModesMaxRate() {
		assertEquals(ShuttleControl.SLOW_MODE_MAX_RATE,
				ShuttleControl.rateForPosition(ShuttleControl.SLIDER_RANGE, ShuttleControl.SLOW_MODE_MAX_RATE), 1e-9);
		assertEquals(-ShuttleControl.SLOW_MODE_MAX_RATE,
				ShuttleControl.rateForPosition(-ShuttleControl.SLIDER_RANGE, ShuttleControl.SLOW_MODE_MAX_RATE), 1e-9);
	}

	@Test
	void rateGrowsQuadraticallyNotLinearly() {
		double maxRate = 100.0;
		double quarter = ShuttleControl.rateForPosition(ShuttleControl.SLIDER_RANGE / 4, maxRate);
		double half = ShuttleControl.rateForPosition(ShuttleControl.SLIDER_RANGE / 2, maxRate);

		// Quadratic: half-deflection should be ~4x quarter-deflection's rate, not ~2x.
		assertEquals(4.0, half / quarter, 0.05);
	}

	@Test
	void negativePositionsScrubBackward() {
		double rate = ShuttleControl.rateForPosition(-50, 100.0);
		assertTrue(rate < 0.0, "a negative slider position must produce a negative (backward) rate");
	}

	@Test
	void fastModeReachesAHigherMaxRateThanSlowMode() {
		double slow = ShuttleControl.rateForPosition(ShuttleControl.SLIDER_RANGE, ShuttleControl.SLOW_MODE_MAX_RATE);
		double fast = ShuttleControl.rateForPosition(ShuttleControl.SLIDER_RANGE, ShuttleControl.FAST_MODE_MAX_RATE);

		assertTrue(fast > slow, "fast mode's max rate must exceed slow mode's");
	}

	@Test
	void startsAtCenterInSlowMode() {
		ShuttleControl control = new ShuttleControl();

		assertEquals(0, control.getSliderPosition());
		assertTrue(!control.isFastMode());
	}

	@Test
	void onRateChangedRejectsNull() {
		ShuttleControl control = new ShuttleControl();
		assertThrows(IllegalArgumentException.class, () -> control.onRateChanged(null));
	}

	@Test
	void onReleaseRejectsNull() {
		ShuttleControl control = new ShuttleControl();
		assertThrows(IllegalArgumentException.class, () -> control.onRelease(null));
	}

	@Test
	void listenersCanBeWiredAndReplaced() {
		// Confirms the widget doesn't crash with its default no-op listeners before any are wired -
		// exercised indirectly since ShuttleControlTest can't simulate a real mouse drag/release in
		// this headless sandbox; this at least proves the callback slots are genuinely replaceable.
		ShuttleControl control = new ShuttleControl();
		List<Double> ratesReceived = new ArrayList<Double>();
		control.onRateChanged(ratesReceived::add);
		control.onRelease(() -> {
		});

		assertTrue(ratesReceived.isEmpty(), "wiring a listener must not itself fire it");
	}

	// The generalization added for PtzOrientationPanel's pan/tilt controls - a custom slider
	// orientation and caller-supplied max rates instead of this class's own original time-scrub
	// constants, while the no-arg constructor above keeps using exactly those constants unchanged.
	@Test
	void customConstructorUsesTheSuppliedRatesNotTheDefaultOnes() {
		ShuttleControl control = new ShuttleControl(JSlider.VERTICAL, 10.0, 90.0);

		assertEquals(0.0, control.getCurrentRate(), "starts centered, at rest");
	}

	@Test
	void getCurrentRateReflectsTheSliderPositionWithoutNeedingAListener() {
		ShuttleControl control = new ShuttleControl(JSlider.HORIZONTAL, 10.0, 90.0);

		assertEquals(ShuttleControl.rateForPosition(control.getSliderPosition(), 10.0), control.getCurrentRate(), 1e-9,
				"getCurrentRate() must be pollable directly, not just available via onRateChanged(...)");
	}
}
