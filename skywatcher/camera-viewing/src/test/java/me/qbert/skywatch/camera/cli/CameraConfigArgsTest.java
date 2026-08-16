package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.RealCaptureMode;

// The camera library resolution --camera adds on top of buildRealCamera(...)'s existing --config/
// raw-flags paths - "the primary way a user is meant to work with this app" (direct user
// instruction), checked before both of those.
class CameraConfigArgsTest {

	@Test
	void camerFlagResolvesFromTheLibrary(@TempDir File tempDir) throws Exception {
		File libraryDir = new File(tempDir, "library");
		CameraLibrary library = new CameraLibrary(libraryDir);
		library.save("polaris", flagBuiltCamera(tempDir, "polaris"));

		ArgScanner scanner = new ArgScanner(new String[] { "--camera", "polaris", "--library-dir", libraryDir.getPath() });

		CameraConfig loaded = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);

		assertEquals("polaris", loaded.getName());
	}

	@Test
	void camerFlagTakesPriorityOverConfigAndRawFlags(@TempDir File tempDir) throws Exception {
		File libraryDir = new File(tempDir, "library");
		CameraLibrary library = new CameraLibrary(libraryDir);
		library.save("polaris", flagBuiltCamera(tempDir, "polaris"));

		// --name here would build a DIFFERENT camera ("backyard") if --camera weren't checked first.
		ArgScanner scanner = new ArgScanner(new String[] { "--camera", "polaris", "--library-dir", libraryDir.getPath(),
				"--name", "backyard", "--lat", "0.0", "--lon", "0.0", "--alt", "0.0", "--az", "0.0",
				"--focal-length", "50.0", "--archive-template", "/x/YYYYmmdd_HHMMSS*.jpg" });

		CameraConfig loaded = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);

		assertEquals("polaris", loaded.getName());
	}

	@Test
	void camerFlagWithAnUnknownNameIsAUsageError(@TempDir File tempDir) {
		ArgScanner scanner = new ArgScanner(
				new String[] { "--camera", "nobody", "--library-dir", new File(tempDir, "library").getPath() });

		assertThrows(CliUsageException.class, () -> CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY));
	}

	@Test
	void libraryDirectoryDefaultsUnderTheUserHomeDirectory() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		File defaultDir = CameraConfigArgs.libraryDirectory(scanner);

		assertEquals(new File(System.getProperty("user.home"), ".camera-viewing/cameras"), defaultDir);
	}

	@Test
	void libraryDirFlagOverridesTheDefault(@TempDir File tempDir) {
		File customDir = new File(tempDir, "custom-library");
		ArgScanner scanner = new ArgScanner(new String[] { "--library-dir", customDir.getPath() });

		assertEquals(customDir, CameraConfigArgs.libraryDirectory(scanner));
	}

	@Test
	void cacheDirectoryDefaultsUnderTheUserHomeDirectory() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		File defaultDir = CameraConfigArgs.cacheDirectory(scanner);

		assertEquals(new File(System.getProperty("user.home"), ".camera-viewing/cache"), defaultDir);
	}

	@Test
	void cacheDirFlagOverridesTheDefault(@TempDir File tempDir) {
		File customDir = new File(tempDir, "custom-cache");
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-dir", customDir.getPath() });

		assertEquals(customDir, CameraConfigArgs.cacheDirectory(scanner));
	}

	@Test
	void cacheScanLimitDefaultsToTen() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		assertEquals(10, CameraConfigArgs.cacheScanLimit(scanner));
	}

	@Test
	void cacheScanLimitFlagOverridesTheDefault() {
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-scan-limit", "25" });

		assertEquals(25, CameraConfigArgs.cacheScanLimit(scanner));
	}

	@Test
	void cacheRefreshIntervalMillisDefaultsToFiveSeconds() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		assertEquals(5_000L, CameraConfigArgs.cacheRefreshIntervalMillis(scanner));
	}

	@Test
	void cacheRefreshIntervalSecondsFlagOverridesTheDefault() {
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-refresh-interval-seconds", "2.5" });

		assertEquals(2_500L, CameraConfigArgs.cacheRefreshIntervalMillis(scanner));
	}

	@Test
	void cacheRefreshIntervalSecondsRejectsNonPositiveValues() {
		ArgScanner scanner = new ArgScanner(new String[] { "--cache-refresh-interval-seconds", "0" });

		assertThrows(CliUsageException.class, () -> CameraConfigArgs.cacheRefreshIntervalMillis(scanner));
	}

	@Test
	void globalSettingsPathDefaultsUnderTheUserHomeDirectory() {
		ArgScanner scanner = new ArgScanner(new String[0]);

		File defaultFile = CameraConfigArgs.globalSettingsPath(scanner);

		assertEquals(new File(System.getProperty("user.home"), ".camera-viewing/settings.properties"), defaultFile);
	}

	@Test
	void settingsFileFlagOverridesTheDefault(@TempDir File tempDir) {
		File customFile = new File(tempDir, "custom-settings.properties");
		ArgScanner scanner = new ArgScanner(new String[] { "--settings-file", customFile.getPath() });

		assertEquals(customFile, CameraConfigArgs.globalSettingsPath(scanner));
	}

	@Test
	void withNoBarrelFlagsTheProjectionKeepsIdentityDistortion(@TempDir File tempDir) throws Exception {
		CameraConfig camera = flagBuiltCamera(tempDir, "no-distortion");

		me.qbert.skywatch.camera.projection.AbstractCameraProjection projection =
				(me.qbert.skywatch.camera.projection.AbstractCameraProjection) camera.getProjection();
		assertEquals(0.0, projection.getDistortionCoefficientA(), 1e-12);
		assertEquals(1.0, projection.getDistortionCoefficientD(), 1e-12);
	}

	@Test
	void barrelFlagsSetTheDistortionCoefficientsOnTheBuiltProjection(@TempDir File tempDir) throws Exception {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "distorted", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0", "--focal-length", "50.0",
				"--archive-template", new File(tempDir, "distorted").getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				"--barrel-a", "-0.0152", "--barrel-b", "-0.0262", "--barrel-c", "0.0009", "--barrel-d", "1.0579" });

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);

		me.qbert.skywatch.camera.projection.AbstractCameraProjection projection =
				(me.qbert.skywatch.camera.projection.AbstractCameraProjection) camera.getProjection();
		assertEquals(-0.0152, projection.getDistortionCoefficientA(), 1e-9);
		assertEquals(-0.0262, projection.getDistortionCoefficientB(), 1e-9);
		assertEquals(0.0009, projection.getDistortionCoefficientC(), 1e-9);
		assertEquals(1.0579, projection.getDistortionCoefficientD(), 1e-9);
	}

	@Test
	void barrelFlagsAreAllOrNothing(@TempDir File tempDir) {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "partial-distortion", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0", "--focal-length", "50.0",
				"--archive-template", new File(tempDir, "partial").getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				"--barrel-a", "-0.0152" });

		assertThrows(CliUsageException.class,
				() -> CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY));
	}

	private CameraConfig flagBuiltCamera(File tempDir, String name) throws Exception {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", name, "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0",
				"--az", "90.0", "--focal-length", "50.0",
				"--archive-template", new File(tempDir, name).getPath() + "/YYYYmmdd_HHMMSS*.jpg" });
		return CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);
	}

	// --- buildVirtualCamera(...) - new this round, mirrors buildRealCamera(...)'s own flag shape ---

	@Test
	void buildsAStaticDirectionalVirtualCameraFromFlags() throws Exception {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "backdrop", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0", "--focal-length", "50.0", "--virtual-image-source", "static",
				"--scene-image", "/scenes/southeast-view.png" });

		CameraConfig camera = CameraConfigArgs.buildVirtualCamera(scanner);

		assertEquals("backdrop", camera.getName());
		assertEquals(me.qbert.skywatch.camera.config.CameraType.Kind.VIRTUAL, camera.getType().getKind());
		assertEquals(me.qbert.skywatch.camera.config.VirtualImageSource.STATIC_DIRECTIONAL,
				camera.getType().getVirtualImageSource());
		assertEquals(me.qbert.skywatch.camera.config.VirtualImagePlacement.LAYER_1, camera.getVirtualImagePlacement());
		assertEquals("/scenes/southeast-view.png", camera.getVirtualScenePath());
		assertEquals(10.0, camera.getCalibrationHistory().latest().getOrientation().getAltitude(), 0.0001);
	}

	@Test
	void buildsAnEquirectangular360PtzVirtualCameraFromFlags() throws Exception {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "sky-dome", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "15.0", "--az", "200.0", "--focal-length", "24.0", "--virtual-image-source", "equirectangular360",
				"--virtual-placement", "layer4", "--scene-image", "/scenes/full-sky-equirect.png" });

		CameraConfig camera = CameraConfigArgs.buildVirtualCamera(scanner);

		assertEquals(me.qbert.skywatch.camera.config.VirtualImageSource.EQUIRECTANGULAR_360,
				camera.getType().getVirtualImageSource());
		assertEquals(me.qbert.skywatch.camera.config.VirtualImagePlacement.LAYER_4, camera.getVirtualImagePlacement());
		assertEquals(15.0, camera.getCurrentOrientation().getAltitude(), 0.0001);
		assertEquals(45.0, camera.getCurrentLocation().getLatitude(), 0.0001);
	}

	@Test
	void rejectsAnUnknownVirtualImageSource() {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "backdrop", "--lat", "45.0", "--lon", "-75.0",
				"--focal-length", "50.0", "--virtual-image-source", "not-a-real-source",
				"--scene-image", "/scenes/x.png" });

		assertThrows(CliUsageException.class, () -> CameraConfigArgs.buildVirtualCamera(scanner));
	}

	@Test
	void rejectsAnUnknownVirtualPlacement() {
		ArgScanner scanner = new ArgScanner(new String[] { "--name", "backdrop", "--lat", "45.0", "--lon", "-75.0",
				"--focal-length", "50.0", "--virtual-image-source", "static", "--virtual-placement", "not-a-layer",
				"--scene-image", "/scenes/x.png" });

		assertThrows(CliUsageException.class, () -> CameraConfigArgs.buildVirtualCamera(scanner));
	}

	@Test
	void virtualCameraFlagsRespectTheSameCameraFlagPriorityAsReal(@TempDir File tempDir) throws Exception {
		File libraryDir = new File(tempDir, "library");
		CameraLibrary library = new CameraLibrary(libraryDir);
		library.save("polaris", flagBuiltCamera(tempDir, "polaris"));

		ArgScanner scanner = new ArgScanner(new String[] { "--camera", "polaris", "--library-dir", libraryDir.getPath() });

		CameraConfig loaded = CameraConfigArgs.buildVirtualCamera(scanner);

		assertEquals("polaris", loaded.getName());
	}
}
