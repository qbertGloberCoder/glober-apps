package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MountControlTest {

	@Test
	void newControlStartsDisengagedWithNoModeSelected() {
		MountControl control = new MountControl();

		assertFalse(control.isEnabled());
		assertFalse(control.isActive());
	}

	@Test
	void enablingWithModeNoneStaysInactive() {
		MountControl control = new MountControl();
		control.setEnabled(true);

		assertTrue(control.isEnabled());
		assertFalse(control.isActive(), "mode = NONE makes the enable toggle moot");
	}

	@Test
	void activeRequiresBothAModeAndEnabled() {
		MountControl control = new MountControl();
		control.setMode(MountMode.EQUATORIAL_MOUNT);
		assertFalse(control.isActive());

		control.setEnabled(true);
		assertTrue(control.isActive());
	}

	@Test
	void resetDisengagesButKeepsTheSelectedMode() {
		MountControl control = new MountControl();
		control.setMode(MountMode.LOCATION_STABILIZER);
		control.setEnabled(true);

		control.reset();

		assertFalse(control.isEnabled(), "the enable toggle never persists - reset() simulates a camera switch");
		assertEquals(MountMode.LOCATION_STABILIZER, control.getMode(), "mode itself is a persisted setting, unaffected by reset()");
	}

	// docs/tasks.md task 3.9: "confirm mutual exclusivity/composability with the equatorial
	// mount... verify nothing in the UI/config model implies they could ever run simultaneously."
	// MountMode is a single-valued enum field (not a flag set), which structurally rules out
	// selecting both the equatorial mount and the geolocation stabilizer at once - this test pins
	// that structural guarantee rather than re-deriving it by inspection each time.
	@Test
	void modeIsExactlyOneOfThreeMutuallyExclusiveValuesNeverAComposableSet() {
		assertEquals(3, MountMode.values().length,
				"exactly NONE / EQUATORIAL_MOUNT / LOCATION_STABILIZER - if this grows, re-check the mutual-exclusivity assumption");

		MountControl control = new MountControl();
		control.setMode(MountMode.EQUATORIAL_MOUNT);
		assertEquals(MountMode.EQUATORIAL_MOUNT, control.getMode());

		// Selecting the stabilizer REPLACES the mode, it does not add to it - there is no API
		// surface on this class through which both could ever be simultaneously selected.
		control.setMode(MountMode.LOCATION_STABILIZER);
		assertEquals(MountMode.LOCATION_STABILIZER, control.getMode());
	}
}
