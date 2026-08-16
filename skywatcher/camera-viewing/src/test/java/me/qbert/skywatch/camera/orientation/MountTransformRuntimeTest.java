package me.qbert.skywatch.camera.orientation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.VirtualImageSource;

// The missing wiring found by an earlier "full backlog audit" round - see this class's own class
// comment. These tests exercise resolve(...)'s edge-detection against a real CameraConfig, matching
// EquatorialMountControllerTest's own "enable -> let time pass -> disable -> confirm saved position"
// methodology, extended to cover the geolocation stabilizer (which has no controller wrapper of its
// own - this class owns that missing commit-then-disengage step directly) and mode-switch cleanup.
class MountTransformRuntimeTest {
	private static final long HOUR_MILLIS = 3_600_000L;

	private CameraConfig newPtzCamera() {
		return new CameraConfig("virtual-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
	}

	private ObservationTime timeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation locationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	@Test
	void modeNoneReturnsTheBaseOrientationUnchanged() throws Exception {
		CameraConfig camera = newPtzCamera();
		MountTransformRuntime runtime = new MountTransformRuntime(camera);
		Orientation base = new Orientation(5.0, 10.0, 1.0);

		Orientation result = runtime.resolve(camera, base, timeAt(0L), locationAt(45.0, -75.0));

		assertEquals(base.getAltitude(), result.getAltitude(), 0.0001);
		assertEquals(base.getAzimuth(), result.getAzimuth(), 0.0001);
		assertEquals(base.getBarrelRoll(), result.getBarrelRoll(), 0.0001);
	}

	@Test
	void equatorialMountEngagesOnRisingEdgeAndTracksOverTime() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		long enableEpoch = 1_700_000_000_000L;
		camera.getMountControl().setEnabled(true);
		Orientation atEnable = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(enableEpoch),
				locationAt(45.0, -75.0));
		assertTrue(runtime.isEquatorialMountEngaged());
		// At the exact lock instant, the computed orientation must match the base exactly.
		assertEquals(45.0, atEnable.getAzimuth(), 0.0001);

		long laterEpoch = enableEpoch + 5L * HOUR_MILLIS;
		Orientation atLater = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(laterEpoch),
				locationAt(45.0, -75.0));
		assertNotEquals(atEnable.getAzimuth(), atLater.getAzimuth(), 0.0001,
				"the tracked orientation should have moved after 5 hours");
		// camera.getCurrentOrientation() itself must NOT have been overwritten while merely tracking -
		// only disable()/disengageForCameraSwitch(...) commit a value back.
		assertEquals(45.0, camera.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	@Test
	void equatorialMountDisengagesOnFallingEdgeAndCommitsTheLiveOrientation() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		long enableEpoch = 1_700_000_000_000L;
		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(enableEpoch), locationAt(45.0, -75.0));

		long disableEpoch = enableEpoch + 3L * HOUR_MILLIS;
		Orientation liveAtDisable = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(disableEpoch),
				locationAt(45.0, -75.0));

		camera.getMountControl().setEnabled(false);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(disableEpoch), locationAt(45.0, -75.0));

		assertFalse(runtime.isEquatorialMountEngaged());
		assertEquals(liveAtDisable.getAzimuth(), camera.getCurrentOrientation().getAzimuth(), 0.0001,
				"disabling must commit the live computed orientation, not snap back to the pre-enable value");
	}

	@Test
	void stabilizerEngagesOnRisingEdgeAndLocksTheSelectedAxes() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.LOCATION_STABILIZER);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);
		// Dec-lock responds to latitude (a real user report swapped this mapping - see
		// GeolocationStabilizerTransform's own class comment).
		runtime.setDecLocked(true);

		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(0.0, 0.0));
		assertTrue(runtime.isStabilizerEngaged());

		// Off-equatorial azimuth so a latitude change actually moves altitude (matches
		// GeolocationStabilizerTransformTest's own az=45 note on az=90's degenerate case).
		Orientation moved = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(30.0, 0.0));
		assertNotEquals(0.0, moved.getAltitude(), 1.0, "Dec-locked altitude should respond to a latitude change");
	}

	@Test
	void stabilizerDisengagesOnFallingEdgeAndCommitsTheLiveOrientation() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.LOCATION_STABILIZER);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);
		runtime.setRaLocked(true);

		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(0.0, 0.0));

		Orientation liveAtDisengage = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L),
				locationAt(30.0, 0.0));

		camera.getMountControl().setEnabled(false);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(30.0, 0.0));

		assertFalse(runtime.isStabilizerEngaged());
		assertEquals(liveAtDisengage.getAltitude(), camera.getCurrentOrientation().getAltitude(), 0.0001,
				"disengaging must commit the live computed orientation");
	}

	@Test
	void switchingModeAwayFromAnEngagedEquatorialMountDisengagesAndCommitsIt() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		long enableEpoch = 1_700_000_000_000L;
		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(enableEpoch), locationAt(45.0, -75.0));

		long laterEpoch = enableEpoch + 2L * HOUR_MILLIS;
		Orientation liveJustBeforeSwitch = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(laterEpoch),
				locationAt(45.0, -75.0));

		// The mode combo's own handler resets enabled=false on every mode change (ui.ControlPanel) -
		// simulated here directly, matching what that handler actually does before the next render.
		camera.getMountControl().setMode(MountMode.NONE);
		camera.getMountControl().setEnabled(false);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(laterEpoch), locationAt(45.0, -75.0));

		assertFalse(runtime.isEquatorialMountEngaged());
		assertEquals(liveJustBeforeSwitch.getAzimuth(), camera.getCurrentOrientation().getAzimuth(), 0.0001,
				"switching mode away from an engaged mount must commit its live position, not abandon it");
	}

	@Test
	void switchingModeAwayFromAnEngagedStabilizerDisengagesAndCommitsIt() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.LOCATION_STABILIZER);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);
		runtime.setRaLocked(true);

		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(0.0, 0.0));

		Orientation liveJustBeforeSwitch = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L),
				locationAt(30.0, 0.0));

		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		camera.getMountControl().setEnabled(false);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(0L), locationAt(30.0, 0.0));

		assertFalse(runtime.isStabilizerEngaged());
		assertEquals(liveJustBeforeSwitch.getAltitude(), camera.getCurrentOrientation().getAltitude(), 0.0001,
				"switching mode away from an engaged stabilizer must commit its live position");
	}

	@Test
	void disengageForCameraSwitchCommitsAndResetsAnEngagedEquatorialMount() throws Exception {
		CameraConfig camera = newPtzCamera();
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		long enableEpoch = 1_700_000_000_000L;
		camera.getMountControl().setEnabled(true);
		runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(enableEpoch), locationAt(45.0, -75.0));

		long switchEpoch = enableEpoch + 4L * HOUR_MILLIS;
		Orientation liveAtSwitch = runtime.resolve(camera, camera.getCurrentOrientation(), timeAt(switchEpoch),
				locationAt(45.0, -75.0));

		runtime.disengageForCameraSwitch(camera, timeAt(switchEpoch), locationAt(45.0, -75.0));

		assertFalse(runtime.isEquatorialMountEngaged());
		assertFalse(camera.getMountControl().isEnabled(), "\"even switching cameras should turn it off\" (CLAUDE.md)");
		assertEquals(liveAtSwitch.getAzimuth(), camera.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	@Test
	void disengageForCameraSwitchIsANoOpWhenNothingIsEngaged() throws Exception {
		CameraConfig camera = newPtzCamera();
		Orientation initial = new Orientation(1.0, 2.0, 3.0);
		camera.setCurrentOrientation(initial);
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		runtime.disengageForCameraSwitch(camera, timeAt(0L), locationAt(0.0, 0.0));

		assertEquals(initial.getAltitude(), camera.getCurrentOrientation().getAltitude(), 0.0001);
		assertEquals(initial.getAzimuth(), camera.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	@Test
	void trackingRateIsAPassThroughToTheOwnedTransform() {
		CameraConfig camera = newPtzCamera();
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		runtime.setTrackingRateDegreesPerHour(TrackingRate.LUNAR.getDegreesPerHour());

		assertEquals(TrackingRate.LUNAR.getDegreesPerHour(), runtime.getTrackingRateDegreesPerHour(), 0.0001);
	}

	@Test
	void axisLocksArePassThroughsToTheOwnedStabilizer() {
		CameraConfig camera = newPtzCamera();
		MountTransformRuntime runtime = new MountTransformRuntime(camera);

		runtime.setRaLocked(true);
		runtime.setDecLocked(true);
		runtime.setRollLocked(true);

		assertTrue(runtime.isRaLocked());
		assertTrue(runtime.isDecLocked());
		assertTrue(runtime.isRollLocked());
	}
}
