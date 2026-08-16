package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.MountMode;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// The headless-testable coordinator behind "app mode." These tests focus on the WIRING this class
// adds on top of already-tested pieces (CameraLibrary, PreviewController) - switching cameras,
// no-camera-selected as a real starting state, and shared clock/options surviving a switch.
class AppControllerTest {

	@Test
	void defaultConstructorGetsAFreshUnpersistedGlobalSettings(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);

		assertNotNull(app.getSettings());
		assertFalse(app.getSettings().hasMyLocation());

		// No settingsFile was supplied - saveSettings() is a documented no-op, not an error.
		app.saveSettings();
	}

	@Test
	void settingsFileConstructorPersistsAcrossANewAppControllerInstance(@TempDir File tempDir) throws Exception {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
		File settingsFile = new File(tempDir, "settings.properties");

		me.qbert.skywatch.camera.config.GlobalSettings initial = new me.qbert.skywatch.camera.config.GlobalSettings();
		AppController first = new AppController(library, cache, options, new SimulatedClock(),
				me.qbert.skywatch.camera.source.ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS, initial, settingsFile);
		first.getSettings().setMyLocation(45.5, -75.25);
		first.saveSettings();

		me.qbert.skywatch.camera.config.GlobalSettings reloaded = me.qbert.skywatch.camera.config.GlobalSettingsStore
				.loadOrDefault(settingsFile);
		AppController second = new AppController(library, cache, options, new SimulatedClock(),
				me.qbert.skywatch.camera.source.ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS, reloaded, settingsFile);

		assertTrue(second.getSettings().hasMyLocation());
		assertEquals(45.5, second.getSettings().getMyLatitude(), 0.0001);
		assertEquals(-75.25, second.getSettings().getMyLongitude(), 0.0001);
	}

	@Test
	void startsWithNoActiveCamera(@TempDir File tempDir) {
		AppController app = newAppController(tempDir);

		assertFalse(app.hasActiveCamera());
		assertNull(app.getActiveCameraName());
		assertNull(app.getActivePreviewController());
	}

	@Test
	void switchToCameraLoadsFromTheLibraryAndBecomesActive(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));

		PreviewController controller = app.switchToCamera("polaris");

		assertTrue(app.hasActiveCamera());
		assertEquals("polaris", app.getActiveCameraName());
		assertEquals("polaris", controller.getCameraConfig().getName());
		assertEquals(controller, app.getActivePreviewController());
	}

	@Test
	void switchingCamerasReplacesTheActiveControllerButKeepsTheSharedClockAndOptions(@TempDir File tempDir)
			throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));
		app.addCamera("backyard", camera("backyard", tempDir));

		PreviewController first = app.switchToCamera("polaris");
		PreviewController second = app.switchToCamera("backyard");

		assertNotSame(first, second, "switching cameras must build a fresh controller, not mutate the old one");
		assertEquals("backyard", app.getActiveCameraName());
		assertSame(app.getClock(), first.getClock(), "the clock is shared, not per-camera");
		assertSame(app.getClock(), second.getClock());
	}

	@Test
	void switchingCamerasReusesTheActiveControllersCanvasSize(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));
		app.addCamera("backyard", camera("backyard", tempDir));

		PreviewController first = app.switchToCamera("polaris");
		first.setCanvasSize(555, 444);

		PreviewController second = app.switchToCamera("backyard");

		assertEquals(555, second.getCanvasWidthPixels(), "a resized window shouldn't snap back to the default on switch");
		assertEquals(444, second.getCanvasHeightPixels());
	}

	@Test
	void switchToCameraRejectsAnUnknownName(@TempDir File tempDir) {
		AppController app = newAppController(tempDir);

		assertThrows(java.io.IOException.class, () -> app.switchToCamera("nobody"));
	}

	@Test
	void listCameraNamesReflectsTheLibrary(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("zeta", camera("zeta", tempDir));
		app.addCamera("alpha", camera("alpha", tempDir));

		List<String> names = app.listCameraNames();

		assertEquals(Arrays.asList("alpha", "zeta"), names);
	}

	@Test
	void removeCameraClearsTheActiveSelectionIfItWasTheOneRemoved(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));
		app.switchToCamera("polaris");
		assertTrue(app.hasActiveCamera());

		app.removeCamera("polaris");

		assertFalse(app.hasActiveCamera());
		assertNull(app.getActiveCameraName());
		assertFalse(app.getLibrary().contains("polaris"));
	}

	@Test
	void removeCameraLeavesTheActiveSelectionAloneIfADifferentCameraWasRemoved(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));
		app.addCamera("backyard", camera("backyard", tempDir));
		app.switchToCamera("polaris");

		app.removeCamera("backyard");

		assertTrue(app.hasActiveCamera());
		assertEquals("polaris", app.getActiveCameraName());
	}

	@Test
	void switchToCameraAutoCreatesAnActiveEditSessionForTheNewCamera(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));

		PreviewController controller = app.switchToCamera("polaris");

		assertNotNull(controller.getActiveEditSession(), "task \"app mode\": a Fixed camera should get a live-editing session automatically");
		assertEquals("polaris", controller.getActiveEditSession().getCameraConfig().getName());
	}

	@Test
	void aLocationEditThroughTheAutoCreatedSessionIsVisibleInTheNextRenderWithoutSaving(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("polaris", camera("polaris", tempDir));
		PreviewController controller = app.switchToCamera("polaris");
		controller.setImageShown(false); // no archive needed - a synthetic hidden-mode render is enough
		app.getClock().pause();
		long now = app.getClock().getCurrentTimeMillis();

		// Aim the pending orientation at the sun's position AS SEEN FROM THE NEW LOCATION (50, -80) -
		// if the location edit below didn't actually take effect (still rendering from the camera's
		// original 45, -75), the sun would NOT be centered, since its apparent position differs by
		// observer location.
		ObserverLocation newLocation = observerLocationAt(50.0, -80.0);
		ObjectDirectionAltAz sunAtNewLocation = sunAltAzAt(now, newLocation);
		controller.getActiveEditSession()
				.adjustOrientation(new Orientation(sunAtNewLocation.getAltitude(), sunAtNewLocation.getAzimuth(), 0.0));

		controller.getActiveEditSession().adjustLocation(50.0, -80.0);

		BufferedImage rendered = controller.renderCurrentFrame();

		int sunColor = ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF;
		int centerX = rendered.getWidth() / 2;
		int centerY = rendered.getHeight() / 2;
		assertEquals(sunColor, rendered.getRGB(centerX, centerY) & 0x00FFFFFF,
				"the sun should render centered - proving the session's location edit (never saved) was actually used");

		// The camera's own PERSISTED calibration must be untouched - this was a live edit, not a save.
		assertEquals(45.0, controller.getCameraConfig().getCalibrationHistory().latest().getLatitude(), 0.0001);
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	private ObjectDirectionAltAz sunAltAzAt(long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = observationTimeAt(epochMillis);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth();
	}

	private AppController newAppController(File tempDir) {
		CameraLibrary library = new CameraLibrary(new File(tempDir, "library"));
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
		return new AppController(library, cache, options, new SimulatedClock());
	}

	private CameraConfig camera(String name, File tempDir) {
		RealImageSource source = RealImageSource.preRecordedOnly(new File(tempDir, name).getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig(name, CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 1.0, 45.0, -75.0));
		return camera;
	}

	private CameraConfig virtualPtzCamera(String name) {
		CameraConfig camera = new CameraConfig(name, CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(24.0));
		camera.setVirtualScenePath("/scenes/sky-dome.png");
		camera.setVirtualImagePlacement(VirtualImagePlacement.LAYER_1);
		return camera;
	}

	// PTZ cameras have no explicit Save - CLAUDE.md's "Orientation editing" documents the intended
	// persistence model directly: current orientation/location auto-persist on app exit and on
	// camera switch. This is the switch half of that contract.
	@Test
	void switchingAwayFromAPtzCameraPersistsItsCurrentOrientationToTheLibrary(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("dome", virtualPtzCamera("dome"));
		app.addCamera("other", camera("other", tempDir));

		PreviewController controller = app.switchToCamera("dome");
		controller.getCameraConfig().setCurrentOrientation(new Orientation(12.0, 200.0, 0.0));

		app.switchToCamera("other");

		CameraConfig reloaded = app.getLibrary().load("dome");
		assertEquals(12.0, reloaded.getCurrentOrientation().getAltitude(), 0.0001);
		assertEquals(200.0, reloaded.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	// The exit half of the same contract - persistActiveCameraOnExit() is what ControlPanel's own
	// windowClosed(...) handler calls; tested directly here since PTZ cameras have no explicit Save
	// button of their own to reach this any other way.
	@Test
	void persistActiveCameraOnExitPersistsAPtzCamerasCurrentOrientation(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("dome", virtualPtzCamera("dome"));

		PreviewController controller = app.switchToCamera("dome");
		controller.getCameraConfig().setCurrentOrientation(new Orientation(-5.0, 90.0, 0.0));

		app.persistActiveCameraOnExit();

		CameraConfig reloaded = app.getLibrary().load("dome");
		assertEquals(-5.0, reloaded.getCurrentOrientation().getAltitude(), 0.0001);
		assertEquals(90.0, reloaded.getCurrentOrientation().getAzimuth(), 0.0001);
	}

	// A Fixed camera already persists explicitly via PlateSolveSession.save(...) - switching away
	// from one must not ALSO silently overwrite its file with whatever happens to be in memory
	// (which could include an unsaved, since-reverted pending edit elsewhere in the session).
	@Test
	void switchingAwayFromAFixedCameraDoesNotAutoPersistIt(@TempDir File tempDir) throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("fixed", camera("fixed", tempDir));
		app.addCamera("other", camera("other", tempDir));
		long savedAtBefore = app.getLibrary().fileFor("fixed").lastModified();

		app.switchToCamera("fixed");
		app.switchToCamera("other");

		assertEquals(savedAtBefore, app.getLibrary().fileFor("fixed").lastModified(),
				"a Fixed camera's file must be untouched by switching away from it - it has its own explicit Save");
	}

	// The missing wiring found by an earlier "full backlog audit" round - see orientation.
	// MountTransformRuntime's own class comment. "Even switching cameras should turn it off"
	// (CLAUDE.md) - this proves persistActiveCameraIfPtz() commits the mount's LIVE computed
	// orientation (not the stale lock-time snapshot) before the camera is persisted/replaced, through
	// the real switchToCamera(...) path, not just MountTransformRuntimeTest's isolated calls.
	@Test
	void switchingAwayFromAPtzCameraWithAnEngagedEquatorialMountCommitsTheLiveOrientation(@TempDir File tempDir)
			throws Exception {
		AppController app = newAppController(tempDir);
		app.addCamera("dome", virtualPtzCamera("dome"));
		app.addCamera("other", camera("other", tempDir));

		app.getClock().pause();
		long lockEpoch = 1_700_000_000_000L;
		app.getClock().setTime(lockEpoch);

		PreviewController controller = app.switchToCamera("dome");
		// virtualPtzCamera(...)'s scene path is a fake, non-existent file (fine for tests that never
		// render with the image shown) - mount resolution runs unconditionally either way (see
		// CameraImageDispatch.compositeVirtual(...)'s restructuring), so hiding the image avoids an
		// irrelevant ImageIO failure without weakening what this test actually checks.
		controller.setImageShown(false);
		// Non-degenerate azimuth - see orientation.MountTransformRuntimeTest's own note: az=90 at
		// alt=0 sits exactly on RotationVector's X rotation axis and is invariant under the transform.
		controller.getCameraConfig().setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		controller.getCameraConfig().getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);
		controller.getCameraConfig().getMountControl().setEnabled(true);
		controller.renderCurrentFrame(); // locks the mount at lockEpoch, orientation (0, 45, 0)

		app.getClock().setTime(lockEpoch + 5L * 3_600_000L); // 5h later - the mount keeps tracking
		controller.renderCurrentFrame(); // live-tracks, does NOT write back to currentOrientation yet

		app.switchToCamera("other"); // persistActiveCameraIfPtz() must commit "dome"'s LIVE position

		CameraConfig reloaded = app.getLibrary().load("dome");
		assertFalse(reloaded.getMountControl().isEnabled(),
				"\"even switching cameras should turn it off\" (CLAUDE.md) - enabled must never persist as true");
		assertNotEquals(45.0, reloaded.getCurrentOrientation().getAzimuth(), 0.01,
				"the persisted orientation must be the mount's live tracked position after 5 hours, not the "
						+ "stale value captured at lock time");
	}
}
