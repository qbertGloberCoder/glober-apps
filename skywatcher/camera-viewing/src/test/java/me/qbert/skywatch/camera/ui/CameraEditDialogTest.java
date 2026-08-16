package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;

// CameraEditDialog itself is a JDialog (throws HeadlessException in this module's own display-less
// test environment, like every other Window subclass here) - but buildRealCameraConfig(...)/
// buildVirtualCameraConfig(...) are plain field-parsing/construction logic with no AWT dependency,
// so they're tested directly.
class CameraEditDialogTest {

	@Test
	void buildsAPreRecordedOnlyRectilinearCamera() {
		CameraConfig config = CameraEditDialog.buildRealCameraConfig("polaris", RealCaptureMode.PRE_RECORDED_ONLY, 45.0,
				-75.0, 10.0, 90.0, 2.0, 50.0, "rectilinear", 180.0, "/cameras/1/YYYYmmdd_HHMMSS*.jpg", "", "system");

		assertEquals("polaris", config.getName());
		assertEquals(CameraType.Kind.REAL, config.getType().getKind());
		assertEquals(RealCaptureMode.PRE_RECORDED_ONLY, config.getType().getRealCaptureMode());
		assertTrue(config.getProjection() instanceof RectilinearProjection);
		assertFalse(config.getRealImageSource().hasLatestSource());

		CalibrationEntry entry = config.getCalibrationHistory().latest();
		assertEquals(10.0, entry.getOrientation().getAltitude(), 0.0001);
		assertEquals(90.0, entry.getOrientation().getAzimuth(), 0.0001);
		assertEquals(2.0, entry.getOrientation().getBarrelRoll(), 0.0001);
		assertEquals(45.0, entry.getLatitude(), 0.0001);
		assertEquals(-75.0, entry.getLongitude(), 0.0001);
	}

	@Test
	void buildsALiveAndRecordedFisheyeCamera() {
		CameraConfig config = CameraEditDialog.buildRealCameraConfig("backyard", RealCaptureMode.LIVE_AND_RECORDED, 45.0,
				-75.0, 0.0, 0.0, 0.0, 8.0, "fisheye", 170.0, "/cameras/2/YYYYmmdd_HHMMSS*.jpg", "/cameras/2/latest.jpg",
				"system");

		assertEquals(RealCaptureMode.LIVE_AND_RECORDED, config.getType().getRealCaptureMode());
		assertTrue(config.getProjection() instanceof FisheyeProjection);
		assertTrue(config.getRealImageSource().hasLatestSource());
		assertEquals("/cameras/2/latest.jpg", config.getRealImageSource().getLatestPath());
	}

	@Test
	void acceptsAnExplicitTimezone() {
		CameraConfig config = CameraEditDialog.buildRealCameraConfig("cam", RealCaptureMode.PRE_RECORDED_ONLY, 45.0, -75.0,
				0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "/cameras/1/YYYYmmdd_HHMMSS*.jpg", "", "America/Toronto");

		assertFalse(config.getRealImageSource().getTimezone().isUseSystemDefault());
	}

	@Test
	void rejectsAnEmptyName() {
		assertThrows(IllegalArgumentException.class, () -> CameraEditDialog.buildRealCameraConfig("", RealCaptureMode.PRE_RECORDED_ONLY,
				45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "/cameras/1/YYYYmmdd_HHMMSS*.jpg", "", "system"));
	}

	@Test
	void rejectsAnEmptyArchiveTemplate() {
		assertThrows(IllegalArgumentException.class, () -> CameraEditDialog.buildRealCameraConfig("cam", RealCaptureMode.PRE_RECORDED_ONLY,
				45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "", "", "system"));
	}

	@Test
	void rejectsLiveAndRecordedWithNoLatestPath() {
		assertThrows(IllegalArgumentException.class, () -> CameraEditDialog.buildRealCameraConfig("cam", RealCaptureMode.LIVE_AND_RECORDED,
				45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "/cameras/1/YYYYmmdd_HHMMSS*.jpg", "", "system"));
	}

	@Test
	void rejectsAMalformedTimezone() {
		assertThrows(java.time.DateTimeException.class, () -> CameraEditDialog.buildRealCameraConfig("cam", RealCaptureMode.PRE_RECORDED_ONLY,
				45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "/cameras/1/YYYYmmdd_HHMMSS*.jpg", "", "Not/AZone"));
	}

	// --- Virtual camera construction (new this round) ---

	@Test
	void buildsAStaticDirectionalVirtualCamera() {
		CameraConfig config = CameraEditDialog.buildVirtualCameraConfig("backdrop", VirtualImageSource.STATIC_DIRECTIONAL,
				VirtualImagePlacement.LAYER_1, 45.0, -75.0, 10.0, 90.0, 2.0, 50.0, "rectilinear", 180.0,
				"/scenes/southeast-view.png");

		assertEquals(CameraType.Kind.VIRTUAL, config.getType().getKind());
		assertEquals(VirtualImageSource.STATIC_DIRECTIONAL, config.getType().getVirtualImageSource());
		assertEquals(OrientationMode.FIXED, config.getType().getOrientationMode(),
				"a static-directional virtual camera must be Fixed, not PTZ");
		assertEquals("/scenes/southeast-view.png", config.getVirtualScenePath());
		assertEquals(VirtualImagePlacement.LAYER_1, config.getVirtualImagePlacement());
		assertTrue(config.getProjection() instanceof RectilinearProjection);

		// Fixed virtual cameras version orientation/location through CalibrationHistory, exactly like
		// Real cameras - not a single mutable currentOrientation/currentLocation.
		CalibrationEntry entry = config.getCalibrationHistory().latest();
		assertEquals(10.0, entry.getOrientation().getAltitude(), 0.0001);
		assertEquals(90.0, entry.getOrientation().getAzimuth(), 0.0001);
		assertEquals(45.0, entry.getLatitude(), 0.0001);
		assertEquals(-75.0, entry.getLongitude(), 0.0001);
	}

	@Test
	void buildsAnEquirectangular360PtzVirtualCamera() {
		// maxAngleRadians for a fisheye lens is a HALF-angle (PI/2 = 180-degree fisheye, PI =
		// 360-degree) - 180.0 degrees here means PI radians, a full 360-degree fisheye.
		CameraConfig config = CameraEditDialog.buildVirtualCameraConfig("sky-dome", VirtualImageSource.EQUIRECTANGULAR_360,
				VirtualImagePlacement.LAYER_4, 45.0, -75.0, 15.0, 200.0, 0.0, 24.0, "fisheye", 180.0,
				"/scenes/full-sky-equirect.png");

		assertEquals(CameraType.Kind.VIRTUAL, config.getType().getKind());
		assertEquals(VirtualImageSource.EQUIRECTANGULAR_360, config.getType().getVirtualImageSource());
		assertEquals(OrientationMode.PTZ, config.getType().getOrientationMode(),
				"an equirectangular-360 virtual camera must be PTZ, not Fixed");
		assertEquals(VirtualImagePlacement.LAYER_4, config.getVirtualImagePlacement());
		assertTrue(config.getProjection() instanceof FisheyeProjection);

		// PTZ cameras have no calibration history - a single mutable currentOrientation/currentLocation
		// instead.
		assertThrows(IllegalStateException.class, config::getCalibrationHistory);
		assertEquals(15.0, config.getCurrentOrientation().getAltitude(), 0.0001);
		assertEquals(200.0, config.getCurrentOrientation().getAzimuth(), 0.0001);
		assertEquals(45.0, config.getCurrentLocation().getLatitude(), 0.0001);
		assertEquals(-75.0, config.getCurrentLocation().getLongitude(), 0.0001);
	}

	@Test
	void rejectsAVirtualCameraWithNoSceneImagePath() {
		assertThrows(IllegalArgumentException.class,
				() -> CameraEditDialog.buildVirtualCameraConfig("backdrop", VirtualImageSource.STATIC_DIRECTIONAL,
						VirtualImagePlacement.LAYER_1, 45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, ""));
	}

	@Test
	void rejectsAVirtualCameraWithNoEmptyName() {
		assertThrows(IllegalArgumentException.class,
				() -> CameraEditDialog.buildVirtualCameraConfig("", VirtualImageSource.STATIC_DIRECTIONAL,
						VirtualImagePlacement.LAYER_1, 45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0,
						"/scenes/x.png"));
	}

	// The confirmed crash this round fixed: the OLD prefillFrom(...) unconditionally called
	// CameraType.getRealCaptureMode(), which throws IllegalStateException for any Virtual camera -
	// so opening "Edit" on a Virtual camera would crash outright instead of showing the form. The
	// fixed prefillFrom(...) branches on getType().getKind() first (see CameraEditDialog.
	// prefillFrom/prefillVirtualFrom/prefillRealFrom). CameraEditDialog itself can't be instantiated
	// in this sandbox (JDialog throws HeadlessException, like every other Window subclass here - see
	// this test class's own comment), so this test instead directly pins the underlying invariant the
	// fix relies on: a Virtual camera's getKind() correctly identifies it as VIRTUAL, and
	// getRealCaptureMode() is confirmed to throw for it - exactly the call the old code made
	// unconditionally, and exactly the call the new branch-on-getKind() code now avoids.
	@Test
	void aVirtualCameraCorrectlyReportsItsKindAndCannotBeMistakenForReal() {
		CameraConfig config = CameraEditDialog.buildVirtualCameraConfig("backdrop", VirtualImageSource.STATIC_DIRECTIONAL,
				VirtualImagePlacement.LAYER_1, 45.0, -75.0, 0.0, 0.0, 0.0, 50.0, "rectilinear", 180.0, "/scenes/x.png");

		assertEquals(CameraType.Kind.VIRTUAL, config.getType().getKind());
		assertThrows(IllegalStateException.class, config.getType()::getRealCaptureMode,
				"calling getRealCaptureMode() unconditionally (the pre-fix behavior) must throw for a Virtual camera");
	}
}
