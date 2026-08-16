package me.qbert.skywatch.camera.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraConfigStore;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

class SaveLatestCommandTest {

	private static final int IMAGE_SIZE = 100;

	@Test
	void savesTheLatestFrameEndToEndFromCliArguments(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		// SaveLatestCommand renders against wall-clock "now" (clock.WallClock.SYSTEM) - matching
		// that here, rather than a fixed historical epoch, since the command doesn't expose an
		// injectable clock (real determinism isn't needed: the gap between this call and the
		// command's own read of "now" a few lines later is milliseconds, imperceptible for the
		// sun's position).
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(System.currentTimeMillis(), 45.0, -75.0);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard",
				"--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--stars", "main",
				"--output", outputFile.getPath()
		};

		int exitCode = new SaveLatestCommand().run(args);

		assertEquals(0, exitCode);
		assertTrue(outputFile.isFile());

		BufferedImage written = ImageIO.read(outputFile);
		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()),
				rgbNoAlpha(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2));
	}

	@Test
	void labelsFlagAddsLabelTextNearTheSun(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		// Aim exactly at the sun's own computed position, same as the base test - the label renders
		// offset from that known point (render.Labels' own fixed pixel offset), and the default label
		// color (White) doesn't otherwise appear anywhere in a render with no other white-colored
		// element active.
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(System.currentTimeMillis(), 45.0, -75.0);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--labels", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorNear(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2, 20, Color.WHITE),
				"expected the sun's label text somewhere near its own screen position");
	}

	@Test
	void graticuleFlagAddsGridLines(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--graticule", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColor(written, Color.LIGHT_GRAY), "expected at least one graticule-colored pixel");
	}

	// Item 5 ("Graticule redesign") - renamed from celestialEquatorFlagAddsALine: the flag/toggle now
	// draws BOTH the RA=0 celestial prime meridian and the Dec=0 celestial equator, and the default
	// color moved from CYAN to RED (celestialOriginColor) - see ColorPresets/FrameCompositor.Options.
	@Test
	void celestialOriginFlagAddsALine(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--celestial-origin", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColor(written, Color.RED), "expected at least one celestial-origin-colored pixel");
	}

	@Test
	void observerCardinalCrossFlagAddsALine(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--observer-cardinal-cross", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColor(written, Color.GREEN), "expected at least one observer-cardinal-cross-colored pixel");
	}

	@Test
	void boresightReferenceLinesFlagAddsALine(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--boresight-reference-lines", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		// Aimed dead-center by construction (see Graticule.paintBoresightReferenceLines) - no need to
		// scan the whole canvas.
		assertTrue(hasColorNear(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2, 3, Color.CYAN),
				"expected the boresight reference lines to pass through screen center");
	}

	@Test
	void watchedObjectReferenceLinesFlagAddsALine(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--watched-object-reference-lines", "true", "--watched-object", "sun",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColor(written, Color.YELLOW), "expected at least one watched-object-reference-line-colored pixel");
	}

	@Test
	void osdFlagAddsSummaryTextInTheCorner(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--osd", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorInRegion(written, 0, 0, IMAGE_SIZE, 20, Color.WHITE),
				"expected the OSD's default White text near the top of the frame");
	}

	@Test
	void renderFlagsStayOffByDefault(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(!hasColor(written, Color.LIGHT_GRAY), "graticule must stay off without --graticule");
		// CYAN is now boresightReferenceColor's default (Item 5) - still an off-by-default check, just
		// against a different reference-line group than before this round's color reassignment.
		assertTrue(!hasColor(written, Color.CYAN), "boresight reference lines must stay off without --boresight-reference-lines");
		assertTrue(!hasColor(written, Color.RED), "watched-object crosshair must stay off without --crosshair");
		assertTrue(!hasColor(written, Color.GREEN), "watched-object path must stay off without --watched-path");
	}

	@Test
	void colorSchemeFlagSwitchesToADifferentPreset(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		ObjectDirectionAltAz sunAltAz = sunAltAzAt(System.currentTimeMillis(), 45.0, -75.0);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--color-scheme", "deuteranopia",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertEquals(rgbNoAlpha(ColorPresets.deuteranopiaFriendlyScheme().getSunColor()),
				rgbNoAlpha(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"expected the deuteranopia preset's own sun color, not the default scheme's yellow");
	}

	@Test
	void unknownColorSchemeProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--color-scheme", "rainbow",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void osdColorFlagChangesTheOsdTextColor(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--osd", "true",
				"--osd-color", "#FF00FF",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorInRegion(written, 0, 0, IMAGE_SIZE, 20, Color.MAGENTA),
				"expected the OSD text in the custom --osd-color instead of the default White");
		assertTrue(!hasColorInRegion(written, 0, 0, IMAGE_SIZE, 20, Color.WHITE),
				"the default OSD color should no longer appear once --osd-color overrides it");
	}

	@Test
	void malformedOsdColorProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--osd", "true", "--osd-color", "notacolor",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void sunPathFlagChangesTheRenderedOutput(@TempDir File tempDir) throws Exception {
		// The ecliptic/analemma path uses the exact same sun color as the live glyph by design (see
		// FrameCompositor's OBJECTS case), so a single-render color check can't distinguish "the path
		// rendered" from "just the live glyph rendered" - comparing a with-flag render against an
		// otherwise-identical without-flag render is unambiguous regardless of exact pixel colors.
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 400, 400);

		String[] baseArgs = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "90.0", "--az", "0.0",
				"--focal-length", "8.0", "--lens", "fisheye", "--fisheye-max-angle", "180",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC"
		};

		File withoutPath = new File(tempDir, "without.png");
		assertEquals(0, new SaveLatestCommand().run(withOutput(baseArgs, withoutPath)));

		File withPath = new File(tempDir, "with.png");
		String[] argsWithPath = withOutput(concat(baseArgs, "--sun-path", "ecliptic"), withPath);
		assertEquals(0, new SaveLatestCommand().run(argsWithPath));

		assertTrue(!imagesEqual(ImageIO.read(withoutPath), ImageIO.read(withPath)),
				"expected --sun-path ecliptic to change the rendered output");
	}

	@Test
	void moonPathFlagChangesTheRenderedOutput(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile, 400, 400);

		String[] baseArgs = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "90.0", "--az", "0.0",
				"--focal-length", "8.0", "--lens", "fisheye", "--fisheye-max-angle", "180",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC"
		};

		File withoutPath = new File(tempDir, "without.png");
		assertEquals(0, new SaveLatestCommand().run(withOutput(baseArgs, withoutPath)));

		File withPath = new File(tempDir, "with.png");
		String[] argsWithPath = withOutput(concat(baseArgs, "--moon-path", "analemma"), withPath);
		assertEquals(0, new SaveLatestCommand().run(argsWithPath));

		assertTrue(!imagesEqual(ImageIO.read(withoutPath), ImageIO.read(withPath)),
				"expected --moon-path analemma to change the rendered output");
	}

	@Test
	void unknownSunPathModeProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--sun-path", "spiral",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void osdDetailFlagAddsTextBelowTheSummaryTier(@TempDir File tempDir) throws Exception {
		// Tall enough canvas for the detail tier's own lines to actually have room below the
		// summary's fixed height (render.Osd.DEFAULT_DETAIL_TIER_TOP_Y_PIXELS).
		int tallSize = 320;
		File latestFile = new File(tempDir, "latest.jpg");
		BufferedImage image = new BufferedImage(tallSize, tallSize, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, tallSize, tallSize);
		g2d.dispose();
		ImageIO.write(image, "jpg", latestFile);

		File outputFile = new File(tempDir, "out.png");
		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--watched-object", "sun",
				"--osd-detail", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorInRegion(written, 0, 115, tallSize, tallSize - 115, Color.WHITE),
				"expected detail-tier text below the always-on summary's own fixed height, even "
						+ "without --osd (the detail tier is independently toggleable)");
	}

	@Test
	void crosshairFlagAddsAMarkerAtTheOffsetReferenceTime(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		// Default --crosshair-offset-hours is -24.0 (a day before "now") - aim the camera exactly at
		// where the sun was at that reference time, the same robust-test approach used throughout
		// this module. SaveLatestCommand always uses WallClock.SYSTEM (no injectable clock exposed
		// at the CLI layer), so this computes its own "now" a few milliseconds before the command's -
		// utterly negligible drift for the sun's position, same reasoning already applied to
		// savesTheLatestFrameEndToEndFromCliArguments above.
		long referenceEpochMillis = System.currentTimeMillis() - 24L * 60 * 60 * 1000;
		ObjectDirectionAltAz sunAltAz = sunAltAzAt(referenceEpochMillis, 45.0, -75.0);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--watched-object", "sun",
				"--crosshair", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorNear(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2, 15, Color.RED),
				"expected the crosshair's own marker color near the sun's 24-hours-ago position");
	}

	@Test
	void watchedPathFlagAddsATrailEndingAtTheLivePosition(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		ObjectDirectionAltAz sunAltAz = sunAltAzAt(System.currentTimeMillis(), 45.0, -75.0);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", String.valueOf(sunAltAz.getAltitude()), "--az", String.valueOf(sunAltAz.getAzimuth()),
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--watched-object", "sun",
				"--watched-path", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args));

		BufferedImage written = ImageIO.read(outputFile);
		assertTrue(hasColorNear(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2, 15, Color.GREEN),
				"expected the watched-object path's own color near the sun's live position - the path's "
						+ "own last sample lands exactly there");
	}

	@Test
	void watchedObjectAcceptsAPlanetName(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--watched-object", "Jupiter",
				"--watched-path", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args), "a planet name should be a valid --watched-object");
	}

	@Test
	void watchedObjectAcceptsAStarByName(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);
		File outputFile = new File(tempDir, "out.png");

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0",
				"--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(),
				"--archive-template", tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				"--timezone", "UTC",
				"--stars", "main",
				"--watched-object", "star:Vega",
				"--watched-path", "true",
				"--output", outputFile.getPath()
		};

		assertEquals(0, new SaveLatestCommand().run(args), "a known star name should be a valid --watched-object");
	}

	@Test
	void unknownWatchedObjectProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--watched-object", "pluto-the-dog",
				"--crosshair", "true",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void unknownStarNameProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--watched-object", "star:NoSuchStarXYZ",
				"--crosshair", "true",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void crosshairWithoutWatchedObjectProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--crosshair", "true",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void savesUsingASavedConfigFileInsteadOfIndividualCameraFlags(@TempDir File tempDir) throws Exception {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		ObjectDirectionAltAz sunAltAz = sunAltAzAt(System.currentTimeMillis(), 45.0, -75.0);

		CameraConfig profile = new CameraConfig("backyard", CameraType.real(RealCaptureMode.LIVE_AND_RECORDED),
				ObserverLocationSetting.explicit(45.0, -75.0));
		profile.setProjection(new RectilinearProjection(50.0));
		profile.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(sunAltAz.getAltitude(), sunAltAz.getAzimuth(), 0.0), 50.0, 45.0, -75.0));
		profile.setRealImageSource(RealImageSource.liveAndRecorded(latestFile.getPath(),
				tempDir.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg", TimezoneSetting.explicit(java.time.ZoneOffset.UTC),
				DstAmbiguousPolicy.ASSUME_STANDARD));

		File configFile = new File(tempDir, "backyard.properties");
		CameraConfigStore.save(profile, configFile);

		File outputFile = new File(tempDir, "out.png");
		String[] args = { "--config", configFile.getPath(), "--output", outputFile.getPath() };

		int exitCode = new SaveLatestCommand().run(args);

		assertEquals(0, exitCode);
		BufferedImage written = ImageIO.read(outputFile);
		assertEquals(rgbNoAlpha(ColorPresets.defaultScheme().getSunColor()),
				rgbNoAlpha(written, IMAGE_SIZE / 2, IMAGE_SIZE / 2),
				"the camera loaded from --config should still render the sun at its own computed position");
	}

	@Test
	void aMalformedConfigFileProducesAUsageErrorNamingTheProblem(@TempDir File tempDir) throws IOException {
		File configFile = new File(tempDir, "broken.properties");
		java.nio.file.Files.write(configFile.toPath(), "name=broken\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

		String[] args = { "--config", configFile.getPath(), "--output", new File(tempDir, "out.png").getPath() };

		CliUsageException exception = assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
		assertTrue(exception.getMessage().contains(configFile.getPath()),
				"expected the error to name the offending --config file, got: " + exception.getMessage());
	}

	@Test
	void missingRequiredFlagProducesAUsageError() {
		String[] args = { "--lat", "45.0" };

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	@Test
	void unknownLensValueProducesAUsageError(@TempDir File tempDir) throws IOException {
		File latestFile = new File(tempDir, "latest.jpg");
		writeBlankImage(latestFile);

		String[] args = {
				"--name", "backyard", "--lat", "45.0", "--lon", "-75.0", "--alt", "10.0", "--az", "90.0",
				"--focal-length", "50.0", "--lens", "wideangle",
				"--latest", latestFile.getPath(), "--archive-template", tempDir.getPath() + "/YYYYmmdd_HHMMSS.jpg",
				"--output", new File(tempDir, "out.png").getPath()
		};

		assertThrows(CliUsageException.class, () -> new SaveLatestCommand().run(args));
	}

	private void writeBlankImage(File file) throws IOException {
		writeBlankImage(file, IMAGE_SIZE, IMAGE_SIZE);
	}

	private void writeBlankImage(File file, int width, int height) throws IOException {
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, width, height);
		g2d.dispose();
		ImageIO.write(image, "jpg", file);
	}

	// Appends --output <outputFile> to a base argument array - used by the sun-path/moon-path
	// with-flag-vs-without-flag comparison tests, which run the same base arguments twice with only
	// one flag and the output path differing.
	private String[] withOutput(String[] args, File outputFile) {
		return concat(args, "--output", outputFile.getPath());
	}

	private String[] concat(String[] base, String... extra) {
		String[] result = java.util.Arrays.copyOf(base, base.length + extra.length);
		System.arraycopy(extra, 0, result, base.length, extra.length);
		return result;
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

	private ObjectDirectionAltAz sunAltAzAt(long epochMillis, double latitude, double longitude) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);

		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute();
		return sun.getCurrentDirectionAsAltitudeAzimuth();
	}

	private int rgbNoAlpha(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) & 0x00FFFFFF;
	}

	private int rgbNoAlpha(Color color) {
		return color.getRGB() & 0x00FFFFFF;
	}

	private boolean hasColor(BufferedImage image, Color color) {
		return hasColorInRegion(image, 0, 0, image.getWidth(), image.getHeight(), color);
	}

	private boolean hasColorNear(BufferedImage image, int centerX, int centerY, int radius, Color color) {
		int left = Math.max(0, centerX - radius);
		int top = Math.max(0, centerY - radius);
		return hasColorInRegion(image, left, top, Math.min(image.getWidth(), centerX + radius) - left,
				Math.min(image.getHeight(), centerY + radius) - top, color);
	}

	private boolean hasColorInRegion(BufferedImage image, int left, int top, int width, int height, Color color) {
		for (int y = top; y < top + height && y < image.getHeight(); y++)
			for (int x = left; x < left + width && x < image.getWidth(); x++)
				if (image.getRGB(x, y) == color.getRGB())
					return true;
		return false;
	}
}
