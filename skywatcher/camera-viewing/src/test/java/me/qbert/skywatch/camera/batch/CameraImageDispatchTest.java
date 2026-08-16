package me.qbert.skywatch.camera.batch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.MountMode;
import me.qbert.skywatch.camera.orientation.MountTransformRuntime;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DirectoryCache;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Task 5.1's camera dispatch - the single entry point covering all four camera shapes (Real/
// Virtual x Fixed/PTZ). Real-camera dispatch is a thin delegation to RealCameraScrubber (already
// covered by RealCameraScrubberTest), so these tests focus on what's new here: correctly routing
// Virtual cameras to a static scene-image load (Fixed) or a live equirectangular crop (PTZ), and
// the placement/error-path decisions dispatch itself is responsible for.
class CameraImageDispatchTest {

	private static final int CANVAS_SIZE = 200;
	// 2026-08-09T17:00:00Z - confirmed sun altitude +60.7deg at lat 45/lon -75 (this file's cameras'
	// own default location), comfortably above render.Layer1DuskFade's 0deg full-brightness
	// threshold - used wherever a test asserts an exact, undarkened Layer-1/4 pixel color, so the
	// assertion doesn't depend on whether the fixture's target time happens to fall at night.
	private static final long DAYTIME_EPOCH_MILLIS = Instant.parse("2026-08-09T17:00:00Z").toEpochMilli();

	@Test
	void dispatchesARealCameraToTheScrubber(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 80, Color.RED);

		CameraConfig camera = realCamera(cameraRoot);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		long target = Instant.parse("2026-08-09T12:00:30Z").toEpochMilli();

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, cache, target, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions());

		assertTrue(result.isCameraImageShown());
		assertEquals(80, result.getImage().getWidth(), "should be the real frame's own resolution, not the fallback");
	}

	@Test
	void archiveScanTruncatedPropagatesFromTheScrubberForARealCamera(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		for (int i = 0; i < 20; i++) {
			File day = new File(cameraRoot, "day" + i);
			day.mkdirs();
			writeOpaqueImage(new File(day, "2026080" + (i % 9 + 1) + "_120000_1.jpg"), 80, 80, Color.RED);
		}

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("cam1", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		DirectoryCache limitedCache = new DirectoryCache(new File(tempDir, "cache"), 5);
		long target = Instant.parse("2026-08-09T12:00:30Z").toEpochMilli();

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, limitedCache, target, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions());

		assertTrue(result.isArchiveScanTruncated());
	}

	@Test
	void activeEditSessionOverridesThePersistedCalibrationForARealCamera(@TempDir File tempDir) throws Exception {
		// Task "app mode"'s live-editing bridge, threaded through to RealCameraScrubber - confirms
		// CameraImageDispatch's own overload actually passes the session along rather than dropping
		// it, mirroring RealCameraScrubberTest's own equivalent check one layer down.
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeOpaqueImage(new File(cameraRoot, "20260809_120000_1.jpg"), 80, 80, Color.BLACK);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(45.0, -75.0);
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		time.setUnixTime(t0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();

		CameraConfig camera = realCamera(cameraRoot); // saved calibration points nowhere near the sun
		PlateSolveSession session = new PlateSolveSession(camera, new File(tempDir, "camera.properties"));
		session.adjustOrientation(new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0));

		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, cache, t0 + 5_000L, true, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions(), session);

		int sunColor = ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF;
		int centerX = result.getImage().getWidth() / 2;
		int centerY = result.getImage().getHeight() / 2;
		assertEquals(sunColor, result.getImage().getRGB(centerX, centerY) & 0x00FFFFFF,
				"the sun should render centered now that the PENDING (not saved) orientation points at it");
	}

	@Test
	void rejectsARealCameraWithNoCache(@TempDir File tempDir) throws Exception {
		File cameraRoot = new File(tempDir, "cameras/1");
		cameraRoot.mkdirs();
		CameraConfig camera = realCamera(cameraRoot);

		assertThrows(IllegalArgumentException.class, () -> CameraImageDispatch.composite(camera, null, 0L, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions()));
	}

	@Test
	void loadsAFixedVirtualCamerasStaticSceneImageDirectly(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "scene.png");
		writeOpaqueImage(sceneFile, 120, 90, Color.GREEN);

		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, 0L, true, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions());

		assertTrue(result.isCameraImageShown());
		assertEquals(120, result.getImage().getWidth(), "a Fixed virtual scene image keeps its own resolution");
		assertEquals(90, result.getImage().getHeight());
	}

	@Test
	void fixedVirtualCameraHiddenRendersAtTheFallbackCanvasSize(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "scene.png");
		writeOpaqueImage(sceneFile, 120, 90, Color.GREEN);

		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, 42L, false, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions());

		assertFalse(result.isCameraImageShown());
		assertEquals(42L, result.getRenderedEpochMillis(), "hidden mode renders at the exact requested time");
		assertEquals(CANVAS_SIZE, result.getImage().getWidth());
	}

	@Test
	void fixedVirtualCameraWithNoCalibrationCoveringTheTargetTimeThrows() {
		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));

		assertThrows(IllegalStateException.class, () -> CameraImageDispatch.composite(camera, null, 0L, false,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions()));
	}

	@Test
	void fixedVirtualCameraWithNoSceneImageConfiguredThrowsWhenShown() {
		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		assertThrows(IllegalStateException.class, () -> CameraImageDispatch.composite(camera, null, 0L, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions()));
	}

	@Test
	void rendersAPtzVirtualCamerasLiveViewFromA360Source(@TempDir File tempDir) throws Exception {
		// Left half (azimuth 0-180) red, right half (azimuth 180-360) blue - matches
		// EquirectangularSceneRendererTest's own setup.
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(Color.RED);
		g2d.fillRect(0, 0, 180, 1);
		g2d.setColor(Color.BLUE);
		g2d.fillRect(180, 0, 180, 1);
		g2d.dispose();
		File sceneFile = new File(tempDir, "panorama.png");
		ImageIO.write(source, "png", sceneFile);

		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0)); // facing east, into the red half

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, 777L, true, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions());

		assertTrue(result.isCameraImageShown());
		assertEquals(CANVAS_SIZE, result.getImage().getWidth(), "a PTZ camera has no native resolution - always the requested canvas size");
		assertEquals(777L, result.getRenderedEpochMillis(), "Virtual cameras have no per-frame timestamp to snap to");
	}

	@Test
	void ptzVirtualCameraHiddenNeverTouchesTheSceneSource() throws Exception {
		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		// Deliberately no virtualScenePath set - if hidden mode tried to load it, this would throw.
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, 123L, false, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions());

		assertFalse(result.isCameraImageShown());
		assertEquals(123L, result.getRenderedEpochMillis());
	}

	@Test
	void ptzVirtualCameraWithSystemLocaleLocationAndNoGlobalSettingsThrows() {
		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		assertThrows(IllegalStateException.class, () -> CameraImageDispatch.composite(camera, null, 0L, false,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions()));
	}

	// config.ObserverLocationSetting.resolve(...) - once a real GlobalSettings with "my location"
	// set is supplied, a PTZ Virtual camera's "use my locale" mode resolves instead of throwing.
	@Test
	void ptzVirtualCameraWithSystemLocaleLocationResolvesFromGlobalSettings() throws Exception {
		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.useSystemLocale());
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		me.qbert.skywatch.camera.config.GlobalSettings globalSettings = new me.qbert.skywatch.camera.config.GlobalSettings();
		globalSettings.setMyLocation(45.0, -75.0);

		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, 0L, false, CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions(), null, null, globalSettings);

		assertFalse(result.isCameraImageShown());
	}

	// Direct user instruction: distortion is meaningless for a Virtual camera's synthetic image
	// (Fixed or PTZ) - AbstractCameraProjection.setDistortionEnabled(false) is applied unconditionally
	// for every Virtual camera, regardless of placement/imageShown. Proven the same way the earlier
	// zoom-fix tests proved their own wiring: point the camera 5 degrees off the sun (distortion has
	// no visible effect exactly at boresight) and confirm the sun's rendered pixel position is
	// IDENTICAL with vs without real distortion coefficients configured on the camera's projection.
	@Test
	void distortionNeverAppliesToAFixedVirtualCamera(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "scene.png");
		writeOpaqueImage(sceneFile, 120, 90, Color.GREEN);

		ObserverLocation location = observerLocationAt(45.0, -75.0);
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(0L, location);

		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		RectilinearProjection projection = new RectilinearProjection(50.0);
		camera.setProjection(projection);
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth() + 5.0, 0.0), 50.0, 45.0, -75.0));

		FrameCompositor.Options options = defaultOptions().setManualSkyToggle(true).setManualHideGroundToggle(false);
		BufferedImage ideal = CameraImageDispatch.composite(camera, null, 0L, false, CANVAS_SIZE, CANVAS_SIZE, options)
				.getImage();
		int idealDistance = distanceFromCenterToSunPixel(ideal);

		projection.setDistortionCoefficients(-0.015173144276557696, -0.026200973539670214, 9.254249203305798E-4,
				1.0578540561260015);
		BufferedImage distorted = CameraImageDispatch.composite(camera, null, 0L, false, CANVAS_SIZE, CANVAS_SIZE, options)
				.getImage();
		int distortedDistance = distanceFromCenterToSunPixel(distorted);

		assertEquals(idealDistance, distortedDistance,
				"a Virtual camera's projection must never apply distortion, even with real coefficients configured");
	}

	// A direct user report: time-scrubbing needlessly re-decodes a Virtual camera's scene file on
	// every render tick, even though its path essentially never changes between calls - see
	// batch.LastImageCache's own class comment. Proven by overwriting the SAME scene file with
	// different content between two calls sharing one CameraImageCaches - a cache hit must keep
	// showing the FIRST call's content, not pick up the filesystem change.
	@Test
	void virtualSceneSourceCacheReusesTheDecodedImageWhenThePathIsUnchanged(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "scene.png");
		writeOpaqueImage(sceneFile, 120, 90, Color.GREEN);

		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		// A daytime target time - render.Layer1DuskFade would otherwise crush GREEN and MAGENTA to
		// identical black regardless of caching, making the reuse assertion below vacuous.
		CameraImageCaches imageCaches = new CameraImageCaches();
		CameraImageDispatch.Result first = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int firstPixel = first.getImage().getRGB(2, 2) & 0x00FFFFFF;

		writeOpaqueImage(sceneFile, 120, 90, Color.MAGENTA); // same path, different content

		CameraImageDispatch.Result second = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int secondPixel = second.getImage().getRGB(2, 2) & 0x00FFFFFF;

		assertEquals(firstPixel, secondPixel,
				"a cache hit must reuse the previously-decoded scene image, not re-read the changed file");
	}

	// The performance fix that actually matters for PTZ: with orientation/zoom/canvas size all
	// unchanged, EquirectangularSceneRenderer's O(width*height) per-pixel render must be skipped
	// entirely - proven the same way as the scene-source cache above, by swapping the underlying
	// panorama file between two calls sharing one CameraImageCaches and confirming the second call's
	// output is unaffected.
	@Test
	void ptzRenderedOutputCacheReusesTheRenderWhenOrientationIsUnchanged(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "panorama.png");
		ImageIO.write(solidPanorama(Color.RED), "png", sceneFile);

		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0));

		// Daytime target times (still within the same second, so the render's TIME genuinely differs
		// between calls while orientation/zoom/canvas stay fixed) - render.Layer1DuskFade would
		// otherwise crush RED and BLUE to identical black regardless of caching, making the reuse
		// assertion below vacuous.
		CameraImageCaches imageCaches = new CameraImageCaches();
		CameraImageDispatch.Result first = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int firstPixel = first.getImage().getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) & 0x00FFFFFF;

		ImageIO.write(solidPanorama(Color.BLUE), "png", sceneFile); // same path, different content

		CameraImageDispatch.Result second = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS + 999L, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int secondPixel = second.getImage().getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) & 0x00FFFFFF;

		assertEquals(firstPixel, secondPixel,
				"scrubbing time alone (orientation unchanged) must reuse the cached render, not re-run the "
						+ "per-pixel equirectangular projection against the changed source file");
	}

	// The correctness counterpart: an actual orientation change with the SAME CameraImageCaches must
	// still produce a genuinely different render, proving the cache is correctly keyed rather than
	// simply always returning the first thing it ever computed.
	@Test
	void ptzRenderedOutputCacheBustsWhenOrientationChanges(@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "panorama.png");
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(Color.RED);
		g2d.fillRect(0, 0, 180, 1);
		g2d.setColor(Color.BLUE);
		g2d.fillRect(180, 0, 180, 1);
		g2d.dispose();
		ImageIO.write(source, "png", sceneFile);

		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());

		// A daytime target time - render.Layer1DuskFade would otherwise darken both renders toward
		// black, crushing the red-vs-blue distinction this test relies on to prove a re-render happened.
		CameraImageCaches imageCaches = new CameraImageCaches();
		camera.setCurrentOrientation(new Orientation(0.0, 90.0, 0.0)); // facing east, into the red half
		CameraImageDispatch.Result facingRed = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE, CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int redFacingPixel = facingRed.getImage().getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) & 0x00FFFFFF;

		camera.setCurrentOrientation(new Orientation(0.0, 270.0, 0.0)); // now facing west, into the blue half
		CameraImageDispatch.Result facingBlue = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE,
				CANVAS_SIZE, defaultOptions(), null, null, null, imageCaches);
		int blueFacingPixel = facingBlue.getImage().getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) & 0x00FFFFFF;

		assertNotEquals(redFacingPixel, blueFacingPixel,
				"an actual orientation change must bust the cache and re-render, not keep serving the first orientation's output");
	}

	// The missing wiring found by an earlier "full backlog audit" round - see orientation.
	// MountTransformRuntime's own class comment. Confirms a real render through the FULL
	// compositeVirtual(...) path (not just MountTransformRuntimeTest's isolated resolve(...) calls)
	// actually reflects the equatorial mount's tracking over elapsed time - i.e. the wiring genuinely
	// reaches the render path, not just the orchestrator class in isolation.
	@Test
	void engagedEquatorialMountRotatesTheRenderedOrientationOverElapsedTimeThroughTheRealRenderPath(
			@TempDir File tempDir) throws Exception {
		File sceneFile = new File(tempDir, "panorama.png");
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(Color.RED);
		g2d.fillRect(0, 0, 180, 1);
		g2d.setColor(Color.BLUE);
		g2d.fillRect(180, 0, 180, 1);
		g2d.dispose();
		ImageIO.write(source, "png", sceneFile);

		CameraConfig camera = new CameraConfig("ptz-pano", CameraType.virtual(VirtualImageSource.EQUIRECTANGULAR_360),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		// Non-degenerate azimuth - see orientation.MountTransformRuntimeTest's own note: az=90 at
		// alt=0 sits exactly on RotationVector's X rotation axis and is invariant under the transform.
		camera.setCurrentOrientation(new Orientation(0.0, 45.0, 0.0));
		camera.getMountControl().setMode(MountMode.EQUATORIAL_MOUNT);

		MountTransformRuntime mountRuntime = new MountTransformRuntime(camera);

		// Sun/moon/planets/stars are NOT auto-disabled by an opaque Layer-1 image (only the ground
		// sub-layer is - see CLAUDE.md's Layer model) and genuinely move over a 6-hour gap regardless
		// of this test's own mount wiring - disabled here so the ONLY thing that can make the two
		// renders differ is the panorama sampling driven by the mount-transformed orientation, not an
		// unrelated, always-true fact about celestial objects moving over time.
		FrameCompositor.Options options = defaultOptions().setShowSun(false).setShowMoon(false).setShowPlanets(false)
				.setShowStars(false);

		// 2026-06-21T13:00Z - summer solstice, local mid-morning at lon=-75 - confirmed empirically
		// (a standalone SunObject check) that the sun stays comfortably above the horizon (27-68deg)
		// across the whole 9-hour span used below, so render.Layer1DuskFade never engages and can't
		// confound this test the way an arbitrary epoch did (crushing every render to identical black
		// for several hours straight).
		long enableEpoch = 1_782_046_800_000L;
		camera.getMountControl().setEnabled(true);
		CameraImageDispatch.Result atLock = CameraImageDispatch.composite(camera, null, enableEpoch, true, CANVAS_SIZE,
				CANVAS_SIZE, options, null, null, null, null, mountRuntime);

		// The EQ mount rotates around the pole, not a simple additive azimuth shift - altitude moves a
		// lot too (confirmed empirically: at this lock lat/alt/az, azimuth doesn't cross from the red
		// half (0-180) into the blue half (180-360) until roughly 9 hours in). 9 hours was chosen by
		// checking real computeLockedOrientation(...) output, not assumed from the tracking rate alone.
		long laterEpoch = enableEpoch + 9L * 3_600_000L;
		CameraImageDispatch.Result atLater = CameraImageDispatch.composite(camera, null, laterEpoch, true, CANVAS_SIZE,
				CANVAS_SIZE, options, null, null, null, null, mountRuntime);

		// Compares which channel DOMINATES at the center pixel (red-leaning vs blue-leaning), not exact
		// equality or exact color - render.Layer1DuskFade darkens Layer 1 based on the sun's real
		// altitude, which also differs across this same 6-hour gap regardless of the mount wiring, but
		// uniform per-pixel darkening never flips which of two channels (one originally 0, one
		// originally 255) is larger, so this stays a clean, darkening-invariant proof that the sampled
		// azimuth actually moved.
		boolean redDominantAtLock = isRedDominant(atLock.getImage());
		boolean redDominantAtLater = isRedDominant(atLater.getImage());
		assertNotEquals(redDominantAtLock, redDominantAtLater,
				"the render must change over elapsed time while the equatorial mount is engaged - proving "
						+ "MountTransformRuntime is really wired into the real render path");
	}

	private boolean isRedDominant(BufferedImage image) {
		int argb = image.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2);
		int red = (argb >> 16) & 0xFF;
		int blue = argb & 0xFF;
		return red > blue;
	}

	private BufferedImage solidPanorama(Color color) {
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, 360, 1);
		g2d.dispose();
		return source;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	private ObjectDirectionAltAz sunAltAzAt(long epochMillis, ObserverLocation location) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth();
	}

	private int distanceFromCenterToSunPixel(BufferedImage image) {
		int sunColor = ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF;
		int centerX = image.getWidth() / 2;
		int centerY = image.getHeight() / 2;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) == sunColor) {
					int dx = x - centerX;
					int dy = y - centerY;
					return (int) Math.round(Math.sqrt(dx * dx + dy * dy));
				}
		return Integer.MAX_VALUE;
	}

	@Test
	void layerOneAndLayerFourPlacementProduceDifferentRenders(@TempDir File tempDir) throws Exception {
		// A scene image with a fully transparent right half - lets sky/ground show through
		// underneath in Layer 1, but not in Layer 4 (which always paints last, occluding
		// everything underneath regardless of the scene image's own alpha).
		BufferedImage scene = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = scene.createGraphics();
		g2d.setColor(Color.GREEN);
		g2d.fillRect(0, 0, 50, 100);
		g2d.dispose(); // right half stays fully transparent (alpha 0)
		File sceneFile = new File(tempDir, "scene.png");
		ImageIO.write(scene, "png", sceneFile);

		BufferedImage layer1 = compositeFixedVirtualCamera(sceneFile, VirtualImagePlacement.LAYER_1);
		BufferedImage layer4 = compositeFixedVirtualCamera(sceneFile, VirtualImagePlacement.LAYER_4);

		assertFalse(imagesEqual(layer1, layer4), "Layer 1 vs Layer 4 placement must change what actually renders");
	}

	private BufferedImage compositeFixedVirtualCamera(File sceneFile, VirtualImagePlacement placement) throws Exception {
		CameraConfig camera = new CameraConfig("static-view", CameraType.virtual(VirtualImageSource.STATIC_DIRECTIONAL),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setProjection(new RectilinearProjection(50.0));
		camera.setVirtualScenePath(sceneFile.getPath());
		camera.setVirtualImagePlacement(placement);
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));

		FrameCompositor.Options options = defaultOptions().setManualSkyToggle(true).setManualHideGroundToggle(false);
		// A daytime target time - render.Layer1DuskFade darkens Layer 1/4 toward black at night, which
		// would otherwise crush this test's GREEN-vs-transparent distinction to indistinguishable black
		// in both placements and mask the very difference this test exists to prove.
		CameraImageDispatch.Result result = CameraImageDispatch.composite(camera, null, DAYTIME_EPOCH_MILLIS, true,
				CANVAS_SIZE, CANVAS_SIZE, options);
		return result.getImage();
	}

	private boolean imagesEqual(BufferedImage a, BufferedImage b) {
		if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
			return false;
		for (int y = 0; y < a.getHeight(); y++)
			for (int x = 0; x < a.getWidth(); x++)
				if (a.getRGB(x, y) != b.getRGB(x, y))
					return false;
		return true;
	}

	private CameraConfig realCamera(File cameraRoot) {
		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		CameraConfig camera = new CameraConfig("cam1", CameraType.real(RealCaptureMode.PRE_RECORDED_ONLY),
				ObserverLocationSetting.explicit(45.0, -75.0));
		camera.setRealImageSource(source);
		camera.setProjection(new RectilinearProjection(50.0));
		camera.getCalibrationHistory().append(new CalibrationEntry(0L, new Orientation(0.0, 0.0, 0.0), 50.0, 45.0, -75.0));
		return camera;
	}

	private FrameCompositor.Options defaultOptions() {
		return new FrameCompositor.Options()
				.setStars(Collections.<StarCoordinate>emptyList())
				.setColorScheme(ColorPresets.defaultScheme())
				.setMinSunMoonRadiusPixels(5.0);
	}

	private void writeOpaqueImage(File file, int width, int height, Color color) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, width, height);
		g2d.dispose();
		String format = file.getName().endsWith(".png") ? "png" : "jpg";
		ImageIO.write(image, format, file);
	}
}
