package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

// Pixel-level integration tests - LayerVisibility's boolean auto-disable logic is already
// thoroughly unit-tested (LayerVisibilityTest); these confirm FrameCompositor actually *respects*
// those decisions when painting, not just that it computes the right booleans.
class FrameCompositorTest {

	private static final int CANVAS_SIZE = 200;
	private static final long EPOCH_MILLIS = 1_723_161_600_000L; // 2024-08-09T00:00:00Z

	@Test
	void anOpaqueLayer1ImageForcesSkyAndGroundOffLeavingBackgroundUntouched() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0); // level, well away from any sun position

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, observationTimeAt(EPOCH_MILLIS),
				observerLocationAt(45.0, -75.0), options);

		// A corner far from any rendered object must stay exactly the original background color -
		// if sky/ground had (incorrectly) rendered, this pixel would be overwritten regardless of
		// the real sun altitude at EPOCH_MILLIS (day = blue, night = black, neither is white).
		assertEquals(Color.WHITE.getRGB(), result.getRGB(2, 2));
	}

	@Test
	void aVirtualCameraWithATransparentSkyRegionMakesSkySelectable() throws Exception {
		BufferedImage source = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = source.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		// A transparent hole in a corner, away from where any object will render.
		g2d.setComposite(java.awt.AlphaComposite.Clear);
		g2d.fillRect(0, 0, 10, 10);
		g2d.dispose();

		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		Color expectedSkyColor = SkyColor.forSunAltitude(sunAltitudeAt(time, location));

		FrameCompositor.Options selectable = new FrameCompositor.Options()
				.setCameraImage(copy(source))
				.setPlacement(ImagePlacement.LAYER_1)
				.setManualSkyToggle(true)
				.setStars(Collections.emptyList());
		BufferedImage withSky = FrameCompositor.compose(projection, camera, time, location, selectable);
		assertEquals(0xFF000000 | expectedSkyColor.getRGB(), withSky.getRGB(2, 2),
				"the transparent hole should be filled with the sky color when the toggle is on");

		FrameCompositor.Options notSelected = new FrameCompositor.Options()
				.setCameraImage(copy(source))
				.setPlacement(ImagePlacement.LAYER_1)
				.setManualSkyToggle(false)
				.setStars(Collections.emptyList());
		BufferedImage withoutSky = FrameCompositor.compose(projection, camera, time, location, notSelected);
		int alpha = (withoutSky.getRGB(2, 2) >>> 24) & 0xFF;
		assertEquals(0, alpha, "sky must stay off when the now-selectable toggle is left off");

		// A real user report: enabling sky for a Virtual camera with a transparent region (a real
		// uploaded scene image's own edge, or an equirectangular render's out-of-lens margin) painted
		// over the WHOLE canvas, hiding the image entirely - not just filling the transparent gap. The
		// test above only ever checked a pixel INSIDE the transparent hole, which would pass under
		// either the old (unconditional full-canvas fillRect) or the fixed (only-fill-non-opaque)
		// behavior - it never proved the opaque part of the image survives. This does.
		assertEquals(Color.WHITE.getRGB(), withSky.getRGB(CANVAS_SIZE - 2, CANVAS_SIZE - 2),
				"a pixel of the image far from the transparent hole must stay exactly as drawn - sky "
						+ "must fill only the actually-transparent gap, not paint over the whole canvas");
	}

	// A direct user request: fade Layer 1 toward black through dusk/night, replicating an old
	// prototype technique - see render.Layer1DuskFade's own class comment. EPOCH_MILLIS/45,-75 is
	// confirmed daytime (sun altitude +1.8deg); epoch 0L at the same location is confirmed well past
	// full astronomical twilight (sun altitude about -25.9deg), so this proves both ends of the curve
	// through the real FrameCompositor.compose(...) pipeline, not just Layer1DuskFade in isolation.
	// Corrected in a later round: the user actually saw this applied to a REAL camera's own photo
	// (streetlights/IR illumination overridden by a computed sun-altitude guess) and asked for the
	// original, narrower Virtual-camera-only scope back - Options.applyLayer1DuskFade defaults to
	// false now, so this test opts in explicitly via setApplyLayer1DuskFade(true) to exercise the
	// feature itself; the companion test below proves the new off-by-default behavior.
	@Test
	void layerOneImageStaysUnfadedInDaytimeButDarkensTowardBlackAtNightWhenDuskFadeIsEnabled() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options daytimeOptions = new FrameCompositor.Options()
				.setCameraImage(copy(source))
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList())
				.setApplyLayer1DuskFade(true);
		BufferedImage daytimeResult = FrameCompositor.compose(projection, camera, observationTimeAt(EPOCH_MILLIS), location,
				daytimeOptions);
		assertEquals(Color.WHITE.getRGB(), daytimeResult.getRGB(2, 2), "daytime must not darken Layer 1 at all");

		FrameCompositor.Options nightOptions = new FrameCompositor.Options()
				.setCameraImage(copy(source))
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList())
				.setApplyLayer1DuskFade(true);
		BufferedImage nightResult = FrameCompositor.compose(projection, camera, observationTimeAt(0L), location, nightOptions);
		assertEquals(0xFF000000, nightResult.getRGB(2, 2),
				"full night (well past astronomical twilight) must fade Layer 1 to opaque black when enabled");
	}

	// The reverted default - proves a Real-camera-shaped call (no setApplyLayer1DuskFade(...) at
	// all, matching RealCameraScrubber/BatchReprocessor's explicit false and every other caller that
	// never sets it) leaves a real photo at full brightness even at full night.
	@Test
	void layer1DuskFadeStaysOffByDefaultEvenAtFullNight() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options nightOptions = new FrameCompositor.Options()
				.setCameraImage(copy(source))
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());
		BufferedImage nightResult = FrameCompositor.compose(projection, camera, observationTimeAt(0L), location, nightOptions);
		assertEquals(Color.WHITE.getRGB(), nightResult.getRGB(2, 2),
				"without opting in, a real photo must stay at full brightness even at full night");
	}

	// A real bug the user caught directly: the original implementation darkened the SHARED canvas in
	// place after drawing the Layer-4 image - since Layer 4 paints LAST (after sky/graticule/ground/
	// objects are already opaque on the canvas - see LayerVisibility.compositeOrder(...)), that
	// canvas-wide darken() call crushed those ALREADY-PAINTED layers to black too, wherever the
	// foreground image itself was transparent. At full night this would black out almost the entire
	// frame, not just the foreground image - defeating the whole point of a transparent-background
	// foreground overlay (the "unicorn on a see-through background" case: the unicorn should fade,
	// but the moon/stars visible through the transparent parts of the image must not). This proves
	// both halves of the fix: the image's own opaque half darkens, the transparent half leaves
	// whatever is underneath (the sky fill) completely untouched.
	@Test
	void layer4DuskFadeOnlyDarkensTheImagesOwnOpaquePixelsNotWhateverIsPaintedUnderneath() throws Exception {
		BufferedImage halfOpaque = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = halfOpaque.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, CANVAS_SIZE / 2, CANVAS_SIZE); // left half opaque white, right half transparent
		g2d.dispose();

		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		// Nautical twilight (sun altitude confirmed -9.19deg at 45,-75) rather than full night -
		// deliberately NOT full night, where the sky fill and the fully-darkened image both converge
		// to identical black and the test can no longer tell "correctly untouched" apart from
		// "incorrectly darkened along with everything else" (caught by reverting the fix and finding
		// an earlier, full-night version of this test passed under the buggy code too).
		ObservationTime time = observationTimeAt(1_723_165_800_000L);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		Color expectedSkyColor = SkyColor.forSunAltitude(sunAltitudeAt(time, location));
		assertNotEquals(Color.BLACK.getRGB(), expectedSkyColor.getRGB(),
				"test setup sanity check: the sky color at this time must not already be black, or this "
						+ "test can't distinguish 'left alone' from 'darkened along with the image'");

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(halfOpaque)
				.setPlacement(ImagePlacement.LAYER_4)
				.setManualSkyToggle(true)
				.setStars(Collections.emptyList())
				.setApplyLayer1DuskFade(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		int opaqueHalfRgb = result.getRGB(2, 2) & 0x00FFFFFF;
		assertTrue(opaqueHalfRgb != 0 && opaqueHalfRgb != (Color.WHITE.getRGB() & 0x00FFFFFF),
				"the image's own opaque half must partially darken during nautical twilight - neither "
						+ "still full white (fade didn't apply) nor already crushed to black");
		assertEquals(0xFF000000 | expectedSkyColor.getRGB(), result.getRGB(CANVAS_SIZE - 2, 2),
				"the image's own transparent half must leave the sky underneath completely untouched, "
						+ "not darkened along with the opaque half");
	}

	@Test
	void layerFourPlacementDoesNotAutoDisableSkyOrGround() throws Exception {
		// A fully transparent Layer-4 image - nothing to occlude, so sky/ground underneath must
		// remain visible (unlike the Layer-1 case, which forces them off).
		BufferedImage transparentOverlay = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);

		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		Color expectedSkyColor = SkyColor.forSunAltitude(sunAltitudeAt(time, location));

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(transparentOverlay)
				.setPlacement(ImagePlacement.LAYER_4)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(0xFF000000 | expectedSkyColor.getRGB(), result.getRGB(2, 2));
	}

	@Test
	void noImagePlacementStillRendersSkyAndGroundNormally() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		Color expectedSkyColor = SkyColor.forSunAltitude(sunAltitudeAt(time, location));

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(0xFF000000 | expectedSkyColor.getRGB(), result.getRGB(2, 2));
	}

	@Test
	void hideGroundToggleStillHidesGroundEvenWithNoImage() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		// Pointing straight down - the entire canvas is below the horizon, so it would be entirely
		// ground-colored if ground were rendered (see GroundFillTest's equivalent "straight down"
		// case) - a strong, unambiguous signal for whether the hide-ground toggle actually worked.
		Orientation camera = new Orientation(-90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setManualHideGroundToggle(true)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertNotEquals(GroundFill.DEFAULT_GROUND_COLOR.getRGB(), result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2) & 0x00FFFFFF);
	}

	@Test
	void aTargetAimedCameraRendersTheSunRegardlessOfSkyGroundState() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(0xFF000000 | (ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF),
				result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2));
	}

	// A real user report ("start adding the additional layer controls: show/hide the camera images,
	// show/hide stars, show/hide planets, etc") - setShowSun/Moon/Planets/Stars(false) below.

	@Test
	void showSunDefaultsToTrue() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		// setShowSun(...) deliberately never called - must still render, matching every existing
		// caller's current behavior.
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(0xFF000000 | (ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF),
				result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2));
	}

	@Test
	void showSunFalseHidesTheSun() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList())
				.setShowSun(false);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(Color.WHITE.getRGB(), result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"the sun must not render at all when showSun is false - the untouched WHITE background should show through");
	}

	@Test
	void showMoonFalseHidesTheMoon() throws Exception {
		BufferedImage source = opaqueImage(Color.WHITE);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(35.0, 139.0);
		CelestialObject moon = me.qbert.skywatch.astro.impl.MoonObject.create()
				.setObserverLocation(location).setObserverTime(time).build();
		moon.recompute();
		ObjectDirectionAltAz moonAltAz = moon.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(moonAltAz.getAltitude(), moonAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options shownOptions = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());
		BufferedImage shown = FrameCompositor.compose(projection, camera, time, location, shownOptions);
		assertEquals(0xFF000000 | (ColorPresets.defaultScheme().getMoonColor().getRGB() & 0x00FFFFFF),
				shown.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2), "sanity check: the moon renders by default");

		FrameCompositor.Options hiddenOptions = new FrameCompositor.Options()
				.setCameraImage(source)
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList())
				.setShowMoon(false);
		BufferedImage hidden = FrameCompositor.compose(projection, camera, time, location, hiddenOptions);
		assertEquals(Color.WHITE.getRGB(), hidden.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"the moon must not render at all when showMoon is false");
	}

	@Test
	void showPlanetsFalseHidesPlanets() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		me.qbert.skywatch.astro.impl.SolarObjects solarObjects = (me.qbert.skywatch.astro.impl.SolarObjects)
				me.qbert.skywatch.astro.impl.SolarObjects.create().setObserverLocation(location).setObserverTime(time).build();
		solarObjects.recompute();
		solarObjects.setObjectIndex(4); // Jupiter
		ObjectDirectionAltAz jupiterAltAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options shownOptions = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());
		BufferedImage shown = FrameCompositor.compose(projection, camera, time, location, shownOptions);
		assertTrue(containsColor(shown, ColorPresets.defaultScheme().getPlanetColor()),
				"sanity check: Jupiter's outline renders by default");

		FrameCompositor.Options hiddenOptions = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowPlanets(false);
		BufferedImage hidden = FrameCompositor.compose(projection, camera, time, location, hiddenOptions);
		assertTrue(!containsColor(hidden, ColorPresets.defaultScheme().getPlanetColor()),
				"no planet outline should render anywhere when showPlanets is false");
	}

	@Test
	void showStarsFalseHidesStars() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		me.qbert.skywatch.camera.catalog.StarCoordinate star =
				new me.qbert.skywatch.camera.catalog.StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true);
		me.qbert.skywatch.model.CelestialAddress starAddress = new me.qbert.skywatch.model.CelestialAddress();
		starAddress.setAddress(star.getRightAscension(), star.getDeclination());
		CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(starAddress).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Color starColor = me.qbert.skywatch.camera.render.StarBrightness.grayscaleFor(star.getApparentMagnitude(),
				star.getApparentMagnitude(), star.getApparentMagnitude());

		FrameCompositor.Options shownOptions = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.singletonList(star));
		BufferedImage shown = FrameCompositor.compose(projection, camera, time, location, shownOptions);
		assertTrue(containsColor(shown, starColor), "sanity check: the star renders by default");

		FrameCompositor.Options hiddenOptions = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.singletonList(star))
				.setShowStars(false);
		BufferedImage hidden = FrameCompositor.compose(projection, camera, time, location, hiddenOptions);
		assertTrue(!containsColor(hidden, starColor), "no star should render anywhere when showStars is false");
	}

	// A light regression guard: setStars(...) (catalog CONTENT) and setShowStars(false) (visibility)
	// must stay independent - toggling showStars off then back on must not lose/alter the configured
	// star list.
	@Test
	void showStarsFalseDoesNotAffectStarTierSelection() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		me.qbert.skywatch.camera.catalog.StarCoordinate star =
				new me.qbert.skywatch.camera.catalog.StarCoordinate("Test Star", "TS1", 1.0, 83.822, -5.391, 1, true);
		me.qbert.skywatch.model.CelestialAddress starAddress = new me.qbert.skywatch.model.CelestialAddress();
		starAddress.setAddress(star.getRightAscension(), star.getDeclination());
		CelestialObject starObject = me.qbert.skywatch.astro.impl.StarObject.create()
				.setStarLocation(starAddress).setObserverLocation(location).setObserverTime(time).build();
		starObject.recompute();
		ObjectDirectionAltAz starAltAz = starObject.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(starAltAz.getAltitude(), starAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Color starColor = me.qbert.skywatch.camera.render.StarBrightness.grayscaleFor(star.getApparentMagnitude(),
				star.getApparentMagnitude(), star.getApparentMagnitude());

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.singletonList(star))
				.setShowStars(false);
		FrameCompositor.compose(projection, camera, time, location, options);

		options.setShowStars(true);
		BufferedImage afterToggleBackOn = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(containsColor(afterToggleBackOn, starColor),
				"the same configured star list must still render after toggling showStars off then back on");
	}

	private boolean containsColor(BufferedImage image, Color color) {
		int target = color.getRGB() & 0x00FFFFFF;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) == target)
					return true;
		return false;
	}

	@Test
	void showOsdPaintsTextOverTheCorner() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowOsd(true)
				.setOsdTimezone(java.time.ZoneOffset.UTC)
				.setOsdTextColor(Color.MAGENTA);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		boolean foundOsdText = false;
		for (int y = 0; y < 100 && !foundOsdText; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (result.getRGB(x, y) == Color.MAGENTA.getRGB()) {
					foundOsdText = true;
					break;
				}

		assertTrue(foundOsdText, "expected the OSD's own text color to appear near the top of the canvas");
	}

	@Test
	void osdStaysOffByDefault() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		for (int y = 0; y < 100; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(Color.MAGENTA.getRGB(), result.getRGB(x, y));
	}

	@Test
	void showOsdWithoutTimezoneThrows() {
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setShowOsd(true);

		assertThrows(IllegalStateException.class, () -> FrameCompositor.compose(new RectilinearProjection(50.0),
				new Orientation(0.0, 0.0, 0.0), observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), options));
	}

	@Test
	void showWatchedObjectDetailPaintsTextBelowTheSummaryPosition() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowWatchedObjectDetail(WatchedObject.sun())
				.setOsdTimezone(java.time.ZoneOffset.UTC)
				.setOsdTextColor(Color.MAGENTA);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		boolean foundDetailText = false;
		int startY = (int) Osd.DEFAULT_DETAIL_TIER_TOP_Y_PIXELS;
		for (int y = startY; y < CANVAS_SIZE && !foundDetailText; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (result.getRGB(x, y) == Color.MAGENTA.getRGB()) {
					foundDetailText = true;
					break;
				}

		assertTrue(foundDetailText, "expected the watched-object detail tier's own text color at/below its default start position");
	}

	@Test
	void watchedObjectDetailStaysOffByDefault() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(Color.MAGENTA.getRGB(), result.getRGB(x, y));
	}

	@Test
	void showWatchedObjectDetailWithoutTimezoneThrows() {
		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setShowWatchedObjectDetail(WatchedObject.sun());

		assertThrows(IllegalStateException.class, () -> FrameCompositor.compose(new RectilinearProjection(50.0),
				new Orientation(0.0, 0.0, 0.0), observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), options));
	}

	@Test
	void showGraticulePaintsGridLinesOverTheSky() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowGraticule(true)
				.setGraticuleStepDegrees(30.0, 15.0);
		// Sky is left at its default (rendering) deliberately - Layer 2 now sits ABOVE Layer 3A in
		// the composite order (corrected this round: the graticule must survive under a rendered
		// sky, only ground/objects are meant to occlude it - see LayerVisibility.compositeOrder(...)'s
		// class comment).

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		boolean foundGraticuleColor = false;
		Color expected = ColorPresets.defaultScheme().getGraticuleColor();
		for (int y = 0; y < CANVAS_SIZE && !foundGraticuleColor; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (result.getRGB(x, y) == expected.getRGB()) {
					foundGraticuleColor = true;
					break;
				}

		assertTrue(foundGraticuleColor, "expected at least one graticule-colored pixel, surviving under a rendered sky");
	}

	@Test
	void groundStillOccludesTheGraticuleByDesign() throws Exception {
		// The whole reason Layer 2 sits below 3B/3C in the first place (unlike sky, which it now sits
		// above) - ground/objects must still be able to hide the graticule/crosshair, so a real photo
		// or an opaque ground fill doesn't get cluttered by reference lines drawn on top of it.
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation straightDown = new Orientation(-90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowGraticule(true)
				.setGraticuleStepDegrees(30.0, 15.0);

		BufferedImage result = FrameCompositor.compose(projection, straightDown, time, location, options);

		assertEquals(GroundFill.DEFAULT_GROUND_COLOR.getRGB(), result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"pointing straight down should be all ground, occluding any graticule line at canvas center");
	}

	@Test
	void graticuleStaysOffByDefault() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		Color graticuleColor = ColorPresets.defaultScheme().getGraticuleColor();
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(graticuleColor.getRGB(), result.getRGB(x, y));
	}

	// Item 5 ("Graticule redesign") - the four new reference-line groups' FrameCompositor wiring.
	// Graticule's own pixel-geometry is already thoroughly tested (GraticuleTest); these confirm
	// FrameCompositor.Options actually routes each toggle through to the right Graticule call with
	// the right ColorScheme field.

	@Test
	void showCelestialOriginPaintsALineOverTheSky() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowCelestialOrigin(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(hasColorAnywhere(result, ColorPresets.defaultScheme().getCelestialOriginColor()),
				"expected at least one celestial-origin-colored pixel");
	}

	@Test
	void celestialOriginStaysOffByDefault() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(!hasColorAnywhere(result, ColorPresets.defaultScheme().getCelestialOriginColor()));
	}

	@Test
	void showObserverCardinalCrossPaintsThroughTheZenith() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(90.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowObserverCardinalCross(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(hasColorAnywhere(result, ColorPresets.defaultScheme().getObserverCardinalCrossColor()));
	}

	@Test
	void showBoresightReferenceLinesPaintsThroughScreenCenter() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		Orientation camera = new Orientation(37.0, 214.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowBoresightReferenceLines(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(hasColorAnywhere(result, ColorPresets.defaultScheme().getBoresightReferenceColor()));
	}

	@Test
	void showWatchedObjectReferenceLinesPaintsThroughTheWatchedObject() throws Exception {
		me.qbert.skywatch.camera.projection.FisheyeProjection projection =
				new me.qbert.skywatch.camera.projection.FisheyeProjection(20.0, Math.PI / 2.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowWatchedObjectReferenceLines(WatchedObject.sun());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertTrue(hasColorAnywhere(result, ColorPresets.defaultScheme().getWatchedObjectReferenceLineColor()));
	}

	// The real regression this pins down (found and fixed this round): an early version auto-showed
	// the horizon line whenever ground simply wasn't rendering, which ALSO covered the much more
	// common "Layer 1 already holds an opaque image" case (a real photo, or a PTZ Virtual camera's
	// default placement) - breaking two existing PTZ-panorama pixel-position tests. The correct
	// trigger is specifically the manual "hide ground / as seen from space" scenario.
	@Test
	void horizonReferenceLineShowsWhenGroundIsManuallyHiddenButNotWhenForcedOffByALayer1Image() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation level = new Orientation(0.0, 90.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options manuallyHidden = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setManualHideGroundToggle(true);
		BufferedImage withManualHide = FrameCompositor.compose(projection, level, time, location, manuallyHidden);
		assertTrue(hasColorAnywhere(withManualHide, GroundFill.DEFAULT_GROUND_COLOR),
				"expected the horizon reference line (ground's own color) when ground is manually hidden");

		FrameCompositor.Options layer1Image = new FrameCompositor.Options()
				.setCameraImage(opaqueImage(Color.WHITE))
				.setPlacement(ImagePlacement.LAYER_1)
				.setStars(Collections.emptyList());
		BufferedImage withImage = FrameCompositor.compose(projection, level, time, location, layer1Image);
		assertTrue(!hasColorAnywhere(withImage, GroundFill.DEFAULT_GROUND_COLOR),
				"a Layer-1 image's own force-disabled ground must NOT auto-show the horizon line");
	}

	private boolean hasColorAnywhere(BufferedImage image, Color color) {
		int target = 0xFF000000 | (color.getRGB() & 0x00FFFFFF);
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if (image.getRGB(x, y) == target)
					return true;
		return false;
	}

	@Test
	void showWatchedObjectCrosshairPaintsAMarkerOverTheSky() throws Exception {
		// Watches a planet rather than the sun/moon deliberately - the live OBJECTS layer paints
		// every planet too (as a small unfilled 2.5px ring, CelestialObjectsLayer.PLANET_RADIUS_PIXELS),
		// which stays well clear of the crosshair arm pixel checked below, unlike the sun's filled
		// disc (radius driven by minSunMoonRadiusPixels) which could otherwise bleed into and mask it.
		ObservationTime referenceTime = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject jupiter = WatchedObject.planet(4);
		ObjectDirectionAltAz jupiterAltAz = jupiter.resolveAltAz(referenceTime, location);
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setWatchedObjectCrosshair(jupiter, referenceTime)
				// Sky is left at its default (rendering) - Layer 2 now sits above Layer 3A, so sky no
				// longer needs disabling to see the crosshair. Ground still needs disabling: this
				// planet happens to sit below the horizon at this test's chosen time/location, and
				// ground genuinely still occludes Layer 2 by design (see groundStillOccludesTheGraticuleByDesign
				// above) - that part of the original workaround was correct, not a stale artifact.
				.setManualHideGroundToggle(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, referenceTime, location, options);

		Color expected = ColorPresets.defaultScheme().getWatchedObjectMarkerColor();
		assertEquals(expected.getRGB(), result.getRGB(CANVAS_SIZE / 2 + 6, CANVAS_SIZE / 2),
				"expected the crosshair's own marker color along one of its arms, surviving under a rendered sky");
	}

	@Test
	void watchedObjectCrosshairStaysOffByDefault() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setManualSkyToggle(false);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		Color markerColor = ColorPresets.defaultScheme().getWatchedObjectMarkerColor();
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(markerColor.getRGB(), result.getRGB(x, y));
	}

	@Test
	void ecliplicSunPathPaintsBeforeTheLiveGlyphSoTheGlyphStaysOnTop() throws Exception {
		// The user's own instruction: the ecliptic/analemma path paints first (as part of the
		// OBJECTS sub-layer, not Layer 2), so the live glyph composites on top of it. Aiming the
		// camera at the sun's own live position for this exact render time is a strong test of that
		// ordering: the path's own first sample (index 0) IS this same live position (both use the
		// same `time`), so the path necessarily draws through this exact pixel too - if the ordering
		// were reversed, the path's thin line could show through instead of the glyph's solid disc.
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		ObjectDirectionAltAz sunAltAz = sun.getCurrentDirectionAsAltitudeAzimuth();
		Orientation camera = new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setSunPathMode(EclipticAnalemmaMode.ECLIPTIC);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		assertEquals(0xFF000000 | (ColorPresets.defaultScheme().getSunColor().getRGB() & 0x00FFFFFF),
				result.getRGB(CANVAS_SIZE / 2, CANVAS_SIZE / 2),
				"the live sun glyph should paint on top of the ecliptic path at the same screen position");
	}

	@Test
	void pathModeSettersRejectNull() {
		FrameCompositor.Options options = new FrameCompositor.Options();
		assertThrows(IllegalArgumentException.class, () -> options.setSunPathMode(null));
		assertThrows(IllegalArgumentException.class, () -> options.setMoonPathMode(null));
	}

	@Test
	void showWatchedObjectPathPaintsAMarkerOverTheSky() throws Exception {
		// A planet again, same reasoning as showWatchedObjectCrosshairPaintsAMarkerOverTheSky - the
		// live OBJECTS glyph for this planet is a small unfilled ring, well clear of the path's own
		// arm/segment pixels near this test's chosen screen position.
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject jupiter = WatchedObject.planet(4);
		ObjectDirectionAltAz jupiterAltAz = jupiter.resolveAltAz(time, location);
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowWatchedObjectPath(jupiter)
				// Layer 2 still sits below ground/objects (unchanged by the ecliptic/analemma move) -
				// same treatment as the graticule/crosshair tests above.
				.setManualHideGroundToggle(true);

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		boolean foundPathColor = false;
		Color expected = ColorPresets.defaultScheme().getWatchedObjectPathColor();
		for (int y = 0; y < CANVAS_SIZE && !foundPathColor; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (result.getRGB(x, y) == expected.getRGB()) {
					foundPathColor = true;
					break;
				}

		assertTrue(foundPathColor, "expected at least one watched-object-path pixel, surviving under a rendered sky");
	}

	@Test
	void watchedObjectPathStaysOffByDefault() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList());

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		Color pathColor = ColorPresets.defaultScheme().getWatchedObjectPathColor();
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(pathColor.getRGB(), result.getRGB(x, y));
	}

	@Test
	void largerFontSizeProducesMoreTextPixelsThanTheDefault() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		int defaultSizeTextPixels = countMagentaPixels(FrameCompositor.compose(projection, camera, time, location,
				osdOptions()));
		int largeSizeTextPixels = countMagentaPixels(FrameCompositor.compose(projection, camera, time, location,
				osdOptions().setFontSizePixels(48)));

		assertTrue(largeSizeTextPixels > defaultSizeTextPixels,
				"a larger configured font size should paint more text-colored pixels than the default (16px)");
	}

	@Test
	void setFontSizePixelsRejectsNonPositiveValues() {
		FrameCompositor.Options options = new FrameCompositor.Options();
		assertThrows(IllegalArgumentException.class, () -> options.setFontSizePixels(0));
		assertThrows(IllegalArgumentException.class, () -> options.setFontSizePixels(-5));
	}

	private FrameCompositor.Options osdOptions() {
		return new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowOsd(true)
				.setOsdTimezone(java.time.ZoneOffset.UTC)
				.setOsdTextColor(Color.MAGENTA);
	}

	private int countMagentaPixels(BufferedImage image) {
		int count = 0;
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				if (image.getRGB(x, y) == Color.MAGENTA.getRGB())
					count++;
		return count;
	}

	@Test
	void clearWatchedObjectCrosshairTurnsAPreviouslyEnabledCrosshairBackOff() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject jupiter = WatchedObject.planet(4);
		ObjectDirectionAltAz jupiterAltAz = jupiter.resolveAltAz(time, location);
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setWatchedObjectCrosshair(jupiter, time)
				.setManualHideGroundToggle(true)
				.clearWatchedObjectCrosshair();

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		Color crosshairColor = ColorPresets.defaultScheme().getWatchedObjectMarkerColor();
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(crosshairColor.getRGB(), result.getRGB(x, y));
	}

	@Test
	void clearWatchedObjectPathTurnsAPreviouslyEnabledPathBackOff() throws Exception {
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);
		WatchedObject jupiter = WatchedObject.planet(4);
		ObjectDirectionAltAz jupiterAltAz = jupiter.resolveAltAz(time, location);
		Orientation camera = new Orientation(jupiterAltAz.getAltitude(), jupiterAltAz.getAzimuth(), 0.0);
		RectilinearProjection projection = new RectilinearProjection(50.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowWatchedObjectPath(jupiter)
				.setManualHideGroundToggle(true)
				.clearWatchedObjectPath();

		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		Color pathColor = ColorPresets.defaultScheme().getWatchedObjectPathColor();
		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(pathColor.getRGB(), result.getRGB(x, y));
	}

	@Test
	void clearWatchedObjectDetailTurnsAPreviouslyEnabledDetailTierBackOff() throws Exception {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		ObservationTime time = observationTimeAt(EPOCH_MILLIS);
		ObserverLocation location = observerLocationAt(45.0, -75.0);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setPlacement(ImagePlacement.NONE)
				.setCanvasSize(CANVAS_SIZE, CANVAS_SIZE)
				.setStars(Collections.emptyList())
				.setShowWatchedObjectDetail(WatchedObject.sun())
				.setOsdTimezone(java.time.ZoneOffset.UTC)
				.setOsdTextColor(Color.MAGENTA)
				.clearWatchedObjectDetail();

		// Must not throw despite osdTimezone having been set only for the (now-cleared) detail tier -
		// compose(...)'s own validation only requires a timezone when a detail target is ACTUALLY set.
		BufferedImage result = FrameCompositor.compose(projection, camera, time, location, options);

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertNotEquals(Color.MAGENTA.getRGB(), result.getRGB(x, y));
	}

	@Test
	void placementRequiresAnImageOrThrows() {
		FrameCompositor.Options options = new FrameCompositor.Options().setPlacement(ImagePlacement.LAYER_1);

		assertThrows(IllegalStateException.class, () -> FrameCompositor.compose(new RectilinearProjection(50.0),
				new Orientation(0.0, 0.0, 0.0), observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), options));
	}

	@Test
	void noImageAndNoCanvasSizeThrows() {
		FrameCompositor.Options options = new FrameCompositor.Options().setPlacement(ImagePlacement.NONE);

		assertThrows(IllegalStateException.class, () -> FrameCompositor.compose(new RectilinearProjection(50.0),
				new Orientation(0.0, 0.0, 0.0), observationTimeAt(EPOCH_MILLIS), observerLocationAt(45.0, -75.0), options));
	}

	private BufferedImage opaqueImage(Color color) {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();
		return image;
	}

	private BufferedImage copy(BufferedImage source) {
		BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
		Graphics2D g2d = copy.createGraphics();
		g2d.drawImage(source, 0, 0, null);
		g2d.dispose();
		return copy;
	}

	private double sunAltitudeAt(ObservationTime time, ObserverLocation location) throws Exception {
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth().getAltitude();
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private ObserverLocation observerLocationAt(double latitude, double longitude) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}
}
