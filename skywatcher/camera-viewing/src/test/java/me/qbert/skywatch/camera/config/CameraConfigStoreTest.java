package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.orientation.MountMode;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.projection.ZoomRange;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;

// Round-trips every optional/type-gated field CameraConfig exposes, across the full Real/Virtual x
// Fixed/PTZ matrix - the whole point of task 0.4's persistence layer is that nothing gets silently
// dropped or defaulted wrong on the way through a save/load cycle.
class CameraConfigStoreTest {

	@Test
	void roundTripsARealLiveAndRecordedCameraWithProjectionAndZoomRange() throws IOException {
		CameraConfig original = new CameraConfig("driveway", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.setTimeBiasMillis(1234L);
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 1.5), 50.0, 45.0, -75.0));
		original.getCalibrationHistory().append(
				new CalibrationEntry(86_400_000L, new Orientation(12.0, 95.0, 0.0), 55.0, 46.0, -76.0));
		original.setRealImageSource(RealImageSource.liveAndRecorded("/cameras/1/latest.jpg",
				"/cameras/1/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(ZoneId.of("America/Toronto")),
				DstAmbiguousPolicy.ASSUME_DAYLIGHT));
		original.setProjection(new RectilinearProjection(50.0));
		original.setZoomRange(new ZoomRange(18.0, 70.0));

		CameraConfig loaded = roundTrip(original);

		assertEquals("driveway", loaded.getName());
		assertEquals(CameraType.Kind.REAL, loaded.getType().getKind());
		assertEquals(RealCaptureMode.LIVE_AND_RECORDED, loaded.getType().getRealCaptureMode());
		assertEquals(1234L, loaded.getTimeBiasMillis());
		assertEquals(45.0, loaded.getObserverLocation().getLatitude());
		assertEquals(-75.0, loaded.getObserverLocation().getLongitude());

		assertEquals(2, loaded.getCalibrationHistory().getEntries().size());
		CalibrationEntry firstEntry = loaded.getCalibrationHistory().getEntries().get(0);
		assertEquals(0L, firstEntry.getEffectiveFromEpochMillis());
		assertEquals(10.0, firstEntry.getOrientation().getAltitude());
		assertEquals(90.0, firstEntry.getOrientation().getAzimuth());
		assertEquals(1.5, firstEntry.getOrientation().getBarrelRoll());
		assertEquals(50.0, firstEntry.getZoom());
		assertEquals(45.0, firstEntry.getLatitude());
		assertEquals(-75.0, firstEntry.getLongitude());
		CalibrationEntry secondEntry = loaded.getCalibrationHistory().getEntries().get(1);
		assertEquals(86_400_000L, secondEntry.getEffectiveFromEpochMillis());
		assertEquals(55.0, secondEntry.getZoom());

		// Live+recorded is never equatorial-mount-eligible - no mountControl.* keys, no crash.
		assertThrows(IllegalStateException.class, loaded::getMountControl);

		RealImageSource loadedSource = loaded.getRealImageSource();
		assertNotNull(loadedSource);
		assertTrue(loadedSource.hasLatestSource());
		assertEquals("/cameras/1/latest.jpg", loadedSource.getLatestPath());
		assertEquals("/cameras/1/**/YYYYmmdd_HHMMSS*.jpg", loadedSource.getArchiveTemplate());
		assertFalse(loadedSource.getTimezone().isUseSystemDefault());
		assertEquals(ZoneId.of("America/Toronto"), loadedSource.getTimezone().getExplicitZone());
		assertEquals(DstAmbiguousPolicy.ASSUME_DAYLIGHT, loadedSource.getDstAmbiguousPolicy());

		assertTrue(loaded.getProjection() instanceof RectilinearProjection);
		assertEquals(50.0, ((RectilinearProjection) loaded.getProjection()).getFocalLengthMillimeters());

		assertEquals(18.0, loaded.getZoomRange().getMinFocalLengthMillimeters());
		assertEquals(70.0, loaded.getZoomRange().getMaxFocalLengthMillimeters());
	}

	@Test
	void roundTripsARealPreRecordedOnlyCameraWithMountControl() throws IOException {
		CameraConfig original = new CameraConfig("timelapse-eq", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.useSystemLocale());
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(60.0, 0.0, 0.0), 200.0, 51.0, -114.0));
		original.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		original.setRealImageSource(RealImageSource.preRecordedOnly("/archive/**/YYYYmmdd_HHMMSS.jpg",
				TimezoneSetting.useSystemDefault(), DstAmbiguousPolicy.ASSUME_STANDARD));

		CameraConfig loaded = roundTrip(original);

		assertTrue(loaded.getObserverLocation().isUseSystemLocale());
		assertEquals(MountMode.EQUATORIAL_MOUNT, loaded.getMountControl().getMode());
		assertFalse(loaded.getMountControl().isEnabled(), "the enable toggle must never be persisted");
		assertFalse(loaded.getRealImageSource().hasLatestSource());
		assertThrows(IllegalStateException.class, loaded.getRealImageSource()::getLatestPath);
		assertTrue(loaded.getRealImageSource().getTimezone().isUseSystemDefault());
	}

	@Test
	void roundTripsAVirtualPtzCameraWithFisheyeProjection() throws IOException {
		CameraConfig original = new CameraConfig("virtual-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(0.0, 0.0));
		original.setCurrentOrientation(new Orientation(20.0, 200.0, 5.0));
		original.setCurrentLocation(ObserverLocationSetting.explicit(35.0, 139.0));
		original.setProjection(new FisheyeProjection(8.0, Math.PI));
		original.setVirtualImagePlacement(VirtualImagePlacement.LAYER_4);

		CameraConfig loaded = roundTrip(original);

		assertEquals(VirtualImagePlacement.LAYER_4, loaded.getVirtualImagePlacement());

		assertEquals(OrientationMode.PTZ, loaded.getType().getOrientationMode());
		assertEquals(20.0, loaded.getCurrentOrientation().getAltitude());
		assertEquals(200.0, loaded.getCurrentOrientation().getAzimuth());
		assertEquals(5.0, loaded.getCurrentOrientation().getBarrelRoll());
		assertEquals(35.0, loaded.getCurrentLocation().getLatitude());
		assertEquals(139.0, loaded.getCurrentLocation().getLongitude());
		assertThrows(IllegalStateException.class, loaded::getCalibrationHistory);

		assertTrue(loaded.getProjection() instanceof FisheyeProjection);
		FisheyeProjection loadedFisheye = (FisheyeProjection) loaded.getProjection();
		assertEquals(8.0, loadedFisheye.getFocalLengthMillimeters());
		assertEquals(Math.PI, loadedFisheye.getMaxAngleRadians(), 1e-9);
	}

	@Test
	void roundTripsAVirtualFixedCameraWithAScenePath() throws IOException {
		CameraConfig original = new CameraConfig("unicorns", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(5.0, 270.0, 0.0), 35.0, 45.0, -75.0));
		original.setVirtualScenePath("/scenes/unicorns.png");

		CameraConfig loaded = roundTrip(original);

		assertEquals(VirtualImageSource.STATIC_DIRECTIONAL, loaded.getType().getVirtualImageSource());
		assertEquals("/scenes/unicorns.png", loaded.getVirtualScenePath());
		assertEquals(1, loaded.getCalibrationHistory().getEntries().size());
		// Fixed virtual cameras ARE equatorial-mount eligible (corrected earlier this project).
		assertEquals(MountMode.NONE, loaded.getMountControl().getMode());
		assertEquals(VirtualImagePlacement.LAYER_1, loaded.getVirtualImagePlacement(),
				"never explicitly set on 'original' - should round-trip as the default");
	}

	@Test
	void aHandWrittenFileOmittingVirtualImagePlacementKeepsTheDefault() throws IOException {
		String text = "name=old-profile\ntype.kind=VIRTUAL\ntype.virtualImageSource=STATIC_DIRECTIONAL\n"
				+ "worldModel=GLOBE\ntimeBiasMillis=0\nobserverLocation.useSystemLocale=true\n"
				+ "calibration.count=0\nmountControl.mode=NONE\n";

		CameraConfig loaded = CameraConfigStore.load(new StringReader(text));

		assertEquals(VirtualImagePlacement.LAYER_1, loaded.getVirtualImagePlacement());
	}

	@Test
	void unconfiguredOptionalFieldsRoundTripAsStillNull() throws IOException {
		CameraConfig original = new CameraConfig("bare", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		// Deliberately: no setRealImageSource(...), no setProjection(...), no setZoomRange(...).

		CameraConfig loaded = roundTrip(original);

		assertNull(loaded.getRealImageSource());
		assertNull(loaded.getProjection());
		assertNull(loaded.getZoomRange());
	}

	@Test
	void fileBasedSaveAndLoadRoundTrips(@TempDir File tempDir) throws IOException {
		CameraConfig original = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));

		File file = new File(tempDir, "backyard-east.properties");
		CameraConfigStore.save(original, file);
		assertTrue(file.isFile());

		CameraConfig loaded = CameraConfigStore.load(file);
		assertEquals("backyard-east", loaded.getName());
	}

	@Test
	void theWrittenFileKeepsAHumanReadableKeyOrder() throws IOException {
		CameraConfig config = new CameraConfig("backyard-east", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		config.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(10.0, 90.0, 0.0), 50.0, 45.0, -75.0));

		StringWriter writer = new StringWriter();
		CameraConfigStore.save(config, writer);
		String text = writer.toString();

		int nameIndex = text.indexOf("name=");
		int typeIndex = text.indexOf("type.kind=");
		int timeBiasIndex = text.indexOf("timeBiasMillis=");
		assertTrue(nameIndex >= 0 && typeIndex > nameIndex && timeBiasIndex > typeIndex,
				"expected name, then type.kind, then timeBiasMillis in that order, got:\n" + text);
	}

	@Test
	void aHandWrittenFileWithAStrayWorldModelKeyFromAnOlderVersionStillLoadsFine() throws IOException {
		// worldModel used to be a required per-camera key (see CameraConfigStore's own comment on
		// this) - now that it lives on GlobalSettings instead, an OLD camera profile that still has
		// this line must keep loading without error, not fail as a "malformed file."
		String text = "name=old-profile\ntype.kind=REAL\ntype.realCaptureMode=LIVE_AND_RECORDED\n"
				+ "worldModel=FLAT\ntimeBiasMillis=0\nobserverLocation.useSystemLocale=true\n"
				+ "calibration.count=1\ncalibration.0.effectiveFromEpochMillis=0\n"
				+ "calibration.0.altitude=0.0\ncalibration.0.azimuth=0.0\ncalibration.0.barrelRoll=0.0\n"
				+ "calibration.0.zoom=50.0\ncalibration.0.latitude=45.0\ncalibration.0.longitude=-75.0\n";

		CameraConfig loaded = CameraConfigStore.load(new StringReader(text));

		assertEquals("old-profile", loaded.getName());
	}

	// A real user report: a camera opened via the interactive control panel had its Camera Location/
	// Orientation controls stay permanently disabled - traced, among other things, to this gap.
	// mountControl.mode was added to the persisted format after some EQ-mount-eligible cameras (a
	// Pre-recorded-only Real camera, here) had already been saved without it - an old file missing
	// this REQUIRED key would throw CameraConfigFormatException on load, which AppController.
	// switchToCamera(...) propagates uncaught - silently leaving the control panel's edit-session-
	// dependent controls stuck in their construction-time disabled state, with only an easy-to-miss
	// dialog as a symptom. Must load cleanly, keeping MountControl's own NONE default.
	@Test
	void aHandWrittenFileWithNoMountControlModeKeyFromAnOlderVersionStillLoadsFine() throws IOException {
		String text = "name=old-eq-eligible\ntype.kind=REAL\ntype.realCaptureMode=PRE_RECORDED_ONLY\n"
				+ "timeBiasMillis=0\nobserverLocation.useSystemLocale=true\n"
				+ "calibration.count=1\ncalibration.0.effectiveFromEpochMillis=0\n"
				+ "calibration.0.altitude=0.0\ncalibration.0.azimuth=0.0\ncalibration.0.barrelRoll=0.0\n"
				+ "calibration.0.zoom=50.0\ncalibration.0.latitude=45.0\ncalibration.0.longitude=-75.0\n";

		CameraConfig loaded = CameraConfigStore.load(new StringReader(text));

		assertEquals("old-eq-eligible", loaded.getName());
		assertEquals(me.qbert.skywatch.camera.orientation.MountMode.NONE, loaded.getMountControl().getMode());
	}

	@Test
	void nonIdentityDistortionCoefficientsRoundTripAndAreWrittenToTheFile() throws IOException {
		CameraConfig original = new CameraConfig("distorted-cam", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		RectilinearProjection projection = new RectilinearProjection(50.0);
		projection.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
				1.0578540561260015);
		original.setProjection(projection);
		original.setRealImageSource(RealImageSource.preRecordedOnly("/archive/**/YYYYmmdd_HHMMSS.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD));

		StringWriter writer = new StringWriter();
		CameraConfigStore.save(original, writer);
		String text = writer.toString();
		assertTrue(text.contains("projection.distortionA="), "non-identity coefficients must actually be written:\n" + text);

		CameraConfig loaded = CameraConfigStore.load(new StringReader(text));
		RectilinearProjection loadedProjection = (RectilinearProjection) loaded.getProjection();
		assertEquals(-0.015173144276557696, loadedProjection.getDistortionCoefficientA(), 1e-15);
		assertEquals(-0.026200973539670214, loadedProjection.getDistortionCoefficientB(), 1e-15);
		assertEquals(9.254249203305798E-4, loadedProjection.getDistortionCoefficientC(), 1e-15);
		assertEquals(1.0578540561260015, loadedProjection.getDistortionCoefficientD(), 1e-15);
	}

	@Test
	void identityDistortionCoefficientsAreNotWrittenToTheFile() throws IOException {
		// Keeps the common no-distortion camera's file exactly as short as before this feature -
		// matching ZoomRange's own "optional, all-or-nothing" convention.
		CameraConfig original = new CameraConfig("no-distortion", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		original.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		original.setProjection(new RectilinearProjection(50.0));
		original.setRealImageSource(RealImageSource.preRecordedOnly("/archive/**/YYYYmmdd_HHMMSS.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD));

		StringWriter writer = new StringWriter();
		CameraConfigStore.save(original, writer);
		String text = writer.toString();

		assertFalse(text.contains("distortionA"), "identity coefficients should not appear in the file at all:\n" + text);

		CameraConfig loaded = CameraConfigStore.load(new StringReader(text));
		RectilinearProjection loadedProjection = (RectilinearProjection) loaded.getProjection();
		assertEquals(0.0, loadedProjection.getDistortionCoefficientA(), 1e-15);
		assertEquals(1.0, loadedProjection.getDistortionCoefficientD(), 1e-15);
	}

	@Test
	void missingRequiredKeyProducesAClearFormatException() {
		String text = "name=incomplete\ntype.kind=REAL\ntype.realCaptureMode=LIVE_AND_RECORDED\n";

		CameraConfigFormatException exception = assertThrows(CameraConfigFormatException.class,
				() -> CameraConfigStore.load(new StringReader(text)));
		assertTrue(exception.getMessage().contains("observerLocation.useSystemLocale"),
				"expected the missing-key complaint to name the key, got: " + exception.getMessage());
	}

	@Test
	void invalidEnumValueProducesAClearFormatException() {
		String text = "name=bad-enum\ntype.kind=NOT_A_REAL_KIND\n";

		CameraConfigFormatException exception = assertThrows(CameraConfigFormatException.class,
				() -> CameraConfigStore.load(new StringReader(text)));
		assertTrue(exception.getMessage().contains("type.kind"));
	}

	@Test
	void invalidZoneIdProducesAClearFormatException() throws IOException {
		CameraConfig config = new CameraConfig("bad-zone", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		config.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		config.setRealImageSource(RealImageSource.preRecordedOnly("/archive/**/YYYYmmdd_HHMMSS.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD));

		StringWriter writer = new StringWriter();
		CameraConfigStore.save(config, writer);
		String corrupted = writer.toString().replace("realImageSource.timezone.zoneId=Z",
				"realImageSource.timezone.zoneId=Not/AZone");

		assertThrows(CameraConfigFormatException.class, () -> CameraConfigStore.load(new StringReader(corrupted)));
	}

	private CameraConfig roundTrip(CameraConfig config) throws IOException {
		StringWriter writer = new StringWriter();
		CameraConfigStore.save(config, writer);
		return CameraConfigStore.load(new StringReader(writer.toString()));
	}
}
