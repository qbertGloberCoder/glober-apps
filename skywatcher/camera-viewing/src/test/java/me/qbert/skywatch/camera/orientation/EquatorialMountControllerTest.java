package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;

// docs/tasks.md task 3.3's exact required cycle: enable -> let time pass -> disable -> confirm
// saved position matches last computed value -> re-enable -> confirm the new lock
// timestamp/reference is the just-saved position, not the original.
class EquatorialMountControllerTest {

	private static final long HOUR_MILLIS = 3_600_000L;

	private CameraConfig newPtzCamera() {
		CameraConfig camera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		return camera;
	}

	@Test
	void disableWritesTheComputedOrientationBackAsTheNewRestingPosition() {
		CameraConfig camera = newPtzCamera();
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		EquatorialMountTransform transform = new EquatorialMountTransform();
		EquatorialMountController controller = new EquatorialMountController(camera, transform);

		long enableEpoch = 1_700_000_000_000L;
		controller.enable(45.0, enableEpoch);
		assertTrue(controller.isEnabled());

		long disableEpoch = enableEpoch + 5L * HOUR_MILLIS;
		Orientation expectedAtDisable = transform.computeLockedOrientation(disableEpoch);

		controller.disable(disableEpoch);

		assertFalse(controller.isEnabled());
		assertFalse(transform.isLocked());
		Orientation saved = camera.getCurrentOrientation();
		assertEquals(expectedAtDisable.getAltitude(), saved.getAltitude(), 0.0001);
		assertEquals(expectedAtDisable.getAzimuth(), saved.getAzimuth(), 0.0001);
		assertEquals(expectedAtDisable.getBarrelRoll(), saved.getBarrelRoll(), 0.0001);

		// Must NOT have snapped back to the pre-enable orientation.
		assertNotEquals(0.0, saved.getAzimuth(), 0.0001);
	}

	@Test
	void reEnableLocksFreshFromTheJustSavedPositionNotTheOriginal() {
		CameraConfig camera = newPtzCamera();
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		EquatorialMountTransform transform = new EquatorialMountTransform();
		EquatorialMountController controller = new EquatorialMountController(camera, transform);

		long firstEnableEpoch = 1_700_000_000_000L;
		controller.enable(45.0, firstEnableEpoch);
		long disableEpoch = firstEnableEpoch + 5L * HOUR_MILLIS;
		controller.disable(disableEpoch);
		Orientation savedAfterFirstCycle = camera.getCurrentOrientation();

		long reEnableEpoch = disableEpoch + 2L * HOUR_MILLIS; // some time passes while parked
		controller.enable(45.0, reEnableEpoch);

		// Evaluating immediately at the re-enable instant must reproduce exactly the just-saved
		// position - i.e. the new lock reference is that saved position, not the original
		// (0, 90, 0) orientation from the very first activation.
		Orientation immediatelyAfterReEnable = transform.computeLockedOrientation(reEnableEpoch);
		assertEquals(savedAfterFirstCycle.getAltitude(), immediatelyAfterReEnable.getAltitude(), 0.0001);
		assertEquals(savedAfterFirstCycle.getAzimuth(), immediatelyAfterReEnable.getAzimuth(), 0.0001);
		assertEquals(savedAfterFirstCycle.getBarrelRoll(), immediatelyAfterReEnable.getBarrelRoll(), 0.0001);
	}

	@Test
	void disableIsANoOpWhenNotEnabled() {
		CameraConfig camera = newPtzCamera();
		Orientation initial = new Orientation(5.0, 5.0, 5.0);
		camera.setCurrentOrientation(initial);

		EquatorialMountTransform transform = new EquatorialMountTransform();
		EquatorialMountController controller = new EquatorialMountController(camera, transform);

		controller.disable(1_700_000_000_000L);

		assertEquals(initial.getAltitude(), camera.getCurrentOrientation().getAltitude(), 0.0001);
		assertFalse(controller.isEnabled());
	}

	@Test
	void enableRequiresModeToBeEquatorialMount() {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.NONE);

		EquatorialMountController controller = new EquatorialMountController(camera, new EquatorialMountTransform());

		try {
			controller.enable(45.0, 1_700_000_000_000L);
			throw new AssertionError("expected IllegalStateException");
		} catch (IllegalStateException expected) {
			// expected
		}
	}
}
