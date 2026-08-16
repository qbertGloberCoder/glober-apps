package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;

class CameraConfigTest {

	@Test
	void fixedCameraGetsACalibrationHistoryNotACurrentOrientation() {
		CameraConfig camera = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertNotNull(camera.getCalibrationHistory());
		assertTrue(camera.getCalibrationHistory().isEmpty());
		assertThrows(IllegalStateException.class, camera::getCurrentOrientation);
	}

	@Test
	void ptzCameraGetsACurrentOrientationNotACalibrationHistory() {
		CameraConfig camera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		assertNotNull(camera.getCurrentOrientation());
		assertThrows(IllegalStateException.class, camera::getCalibrationHistory);

		camera.setCurrentOrientation(new Orientation(10.0, 90.0, 0.0));
		assertEquals(90.0, camera.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	@Test
	void ptzCameraGetsASecondCurrentLocationAlongsideCurrentOrientation() {
		// Task 1.3b: "roaming" is just this control, no separate roaming-mode UI.
		CameraConfig camera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertNotNull(camera.getCurrentLocation());
		assertEquals(45.0, camera.getCurrentLocation().getLatitude(), 0.0001);

		camera.setCurrentLocation(ObserverLocationSetting.explicit(35.0, 139.0));
		assertEquals(35.0, camera.getCurrentLocation().getLatitude(), 0.0001);
		assertEquals(139.0, camera.getCurrentLocation().getLongitude(), 0.0001);
	}

	@Test
	void fixedCameraHasNoSingleCurrentLocation() {
		CameraConfig camera = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertThrows(IllegalStateException.class, camera::getCurrentLocation);
		assertThrows(IllegalStateException.class,
				() -> camera.setCurrentLocation(ObserverLocationSetting.explicit(1.0, 1.0)));
	}

	@Test
	void realCameraGetsARealImageSourceNotAVirtualScenePath() {
		CameraConfig camera = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));

		assertNull(camera.getRealImageSource(), "unconfigured until explicitly set - not an error");
		assertThrows(IllegalStateException.class, camera::getVirtualScenePath);
		assertThrows(IllegalStateException.class, () -> camera.setVirtualScenePath("scene.png"));

		RealImageSource source = RealImageSource.liveAndRecorded("/cameras/1/latest.jpg",
				"/cameras/1/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.useSystemDefault(),
				me.qbert.skywatch.camera.source.DstAmbiguousPolicy.ASSUME_STANDARD);
		camera.setRealImageSource(source);
		assertEquals(source, camera.getRealImageSource());
	}

	@Test
	void virtualCameraGetsAScenePathNotARealImageSource() {
		CameraConfig camera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		assertNull(camera.getVirtualScenePath(), "unconfigured until explicitly set - not an error");
		assertThrows(IllegalStateException.class, camera::getRealImageSource);
		assertThrows(IllegalStateException.class,
				() -> camera.setRealImageSource(RealImageSource.preRecordedOnly("**",
						TimezoneSetting.useSystemDefault(), me.qbert.skywatch.camera.source.DstAmbiguousPolicy.ASSUME_STANDARD)));

		camera.setVirtualScenePath("unicorns.png");
		assertEquals("unicorns.png", camera.getVirtualScenePath());
	}

	@Test
	void virtualImagePlacementDefaultsToLayer1AndIsRealCameraGated() {
		CameraConfig virtualCamera = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		assertEquals(VirtualImagePlacement.LAYER_1, virtualCamera.getVirtualImagePlacement(),
				"the ordinary/default placement per CLAUDE.md's Layer model table");

		virtualCamera.setVirtualImagePlacement(VirtualImagePlacement.LAYER_4);
		assertEquals(VirtualImagePlacement.LAYER_4, virtualCamera.getVirtualImagePlacement());

		CameraConfig realCamera = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		assertThrows(IllegalStateException.class, realCamera::getVirtualImagePlacement);
		assertThrows(IllegalStateException.class, () -> realCamera.setVirtualImagePlacement(VirtualImagePlacement.LAYER_4));
	}

	@Test
	void projectionAndZoomRangeAreAvailableRegardlessOfCameraKind() {
		// CLAUDE.md's "Camera projection model": unlike realImageSource/virtualScenePath, this is
		// NOT type-gated - a Virtual PTZ camera may use any projection freely.
		CameraConfig real = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		CameraConfig virtualPtz = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());

		assertNull(real.getProjection(), "unconfigured until explicitly set - not an error");
		assertNull(virtualPtz.getProjection());

		me.qbert.skywatch.camera.projection.CameraProjection realLens = new me.qbert.skywatch.camera.projection.RectilinearProjection(60.0);
		me.qbert.skywatch.camera.projection.CameraProjection fisheye = new me.qbert.skywatch.camera.projection.FisheyeProjection(8.0, Math.PI / 2.0);

		real.setProjection(realLens);
		virtualPtz.setProjection(fisheye);

		assertEquals(realLens, real.getProjection());
		assertEquals(fisheye, virtualPtz.getProjection());

		me.qbert.skywatch.camera.projection.ZoomRange range = new me.qbert.skywatch.camera.projection.ZoomRange(18.0, 70.0);
		real.setZoomRange(range);
		assertEquals(range, real.getZoomRange());
		assertNull(virtualPtz.getZoomRange(), "zoomRange is independent per camera and optional");
	}

	@Test
	void mountControlOnlyExistsWhenEligible() {
		CameraConfig eligible = new CameraConfig("virtual-pano",
				CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());
		assertNotNull(eligible.getMountControl());

		CameraConfig ineligible = new CameraConfig("backyard-east",
				CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		assertThrows(IllegalStateException.class, ineligible::getMountControl);
	}
}
