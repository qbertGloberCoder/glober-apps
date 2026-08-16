package me.qbert.skywatch.camera.cli;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.camera.catalog.StarCatalogLoader;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.catalog.StarVisibilityOverrides;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraConfigFormatException;
import me.qbert.skywatch.camera.config.CameraConfigStore;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.ColorScheme;
import me.qbert.skywatch.camera.render.EclipticAnalemmaMode;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.DstAmbiguousPolicy;
import me.qbert.skywatch.camera.watch.WatchedObject;

/*
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

// Shared flag vocabulary for every subcommand that needs a real camera. Task 0.4's
// config.CameraConfigStore now exists, so --config <path> loads a full saved profile
// (config.CameraConfigStore.load(...)) instead of building one from the --lat/--lon/--alt/--az/
// --roll/--focal-length/--lens/--fisheye-max-angle/--barrel-a/b/c/d/--archive-template/--latest/
// --timezone flags
// below - those flags remain as the fallback for a camera with no saved profile yet, matching the
// "first draft" CLI [task 6.5]'s original stance before task 0.4 landed. --name/--stars/
// --min-radius/--output(-dir)/--interval-seconds are unaffected either way - they're invocation
// parameters (which star tier to render, where to write output), not part of the camera itself, so
// they still apply on top of a loaded profile exactly as they do with flag-built cameras.
final class CameraConfigArgs {
	private CameraConfigArgs() {
	}

	// Shared USAGE fragment for buildOptions(...)'s rendering toggles - every subcommand that calls
	// buildOptions(...) splices this into its own USAGE string rather than repeating it, so the two
	// can't drift out of sync. Deliberately does NOT include a manual sky/ground toggle or a ground
	// color/swap-order flag (Options.setManualSkyToggle(...)/setManualHideGroundToggle(...)/
	// setGroundColor(...)/setGroundPaintsOverObjects(...)) - every camera this CLI ever builds is
	// Real, and a Real camera always sits in Layer 1, where LayerVisibility force-disables sky AND
	// ground unconditionally (no exception for Real cameras - that exception is Virtual-only). Those
	// four Options fields are therefore structurally inert for every invocation this CLI can ever
	// produce; exposing flags for them would silently do nothing rather than error, a real footgun,
	// so they're left unexposed rather than wired to dead code.
	static final String RENDER_FLAGS_USAGE = "[--color-scheme default|deuteranopia|high-contrast] "
			+ "[--labels true|false] [--graticule true|false] "
			+ "[--graticule-ra-step <deg>] [--graticule-dec-step <deg>] [--celestial-origin true|false] "
			+ "[--observer-cardinal-cross true|false] [--boresight-reference-lines true|false] "
			+ "[--watched-object-reference-lines true|false] "
			+ "[--osd true|false] [--osd-color #RRGGBB] [--osd-detail true|false] "
			+ "[--sun-path none|ecliptic|analemma] [--moon-path none|ecliptic|analemma] "
			+ "[--watched-object sun|moon|mercury|venus|mars|jupiter|saturn|uranus|neptune|pluto|star:<name>] "
			+ "[--crosshair true|false] [--crosshair-offset-hours <n>] "
			+ "[--watched-path true|false] [--watched-path-hours <n>] [--watched-path-interval-minutes <n>]";

	static CameraConfig buildRealCamera(ArgScanner scanner, RealCaptureMode mode) throws IOException {
		if (scanner.option("camera").isPresent())
			return loadFromLibrary(scanner);
		if (scanner.option("config").isPresent())
			return loadFromConfigFile(scanner.requireOption("config"));

		String name = scanner.requireOption("name");
		double latitude = scanner.requireDoubleOption("lat");
		double longitude = scanner.requireDoubleOption("lon");
		double altitude = scanner.requireDoubleOption("alt");
		double azimuth = scanner.requireDoubleOption("az");
		double roll = scanner.doubleOption("roll", 0.0);
		double focalLength = scanner.requireDoubleOption("focal-length");

		CameraConfig camera = new CameraConfig(name, CameraType.real(mode),
				ObserverLocationSetting.explicit(latitude, longitude));
		CameraProjection projection = buildProjection(scanner, focalLength);
		applyDistortion(scanner, projection);
		camera.setProjection(projection);
		camera.getCalibrationHistory().append(new CalibrationEntry(0L,
				new Orientation(altitude, azimuth, roll), focalLength, latitude, longitude));
		camera.setRealImageSource(buildRealImageSource(scanner, mode));

		return camera;
	}

	// Virtual counterpart to buildRealCamera(...) above - no CLI subcommand consumes this yet (every
	// existing subcommand is Real-camera-only by design, matching a Real camera's own always-Layer-1/
	// always-archived nature), but it exists so a Virtual camera's own config file can be built and
	// round-tripped from the CLI the same way a Real one can, keeping the two paths in sync -
	// mirrors ui.CameraEditDialog.buildVirtualCameraConfig(...)'s exact same construction, branching
	// on CameraType.getOrientationMode() to decide between CalibrationHistory (Fixed) and a single
	// mutable currentOrientation/currentLocation (PTZ) exactly the way CameraConfig itself expects.
	static CameraConfig buildVirtualCamera(ArgScanner scanner) throws IOException {
		if (scanner.option("camera").isPresent())
			return loadFromLibrary(scanner);
		if (scanner.option("config").isPresent())
			return loadFromConfigFile(scanner.requireOption("config"));

		String name = scanner.requireOption("name");
		double latitude = scanner.requireDoubleOption("lat");
		double longitude = scanner.requireDoubleOption("lon");
		double altitude = scanner.doubleOption("alt", 0.0);
		double azimuth = scanner.doubleOption("az", 0.0);
		double roll = scanner.doubleOption("roll", 0.0);
		double focalLength = scanner.requireDoubleOption("focal-length");
		VirtualImageSource imageSource = buildVirtualImageSource(scanner);
		VirtualImagePlacement placement = buildVirtualImagePlacement(scanner);
		String scenePath = scanner.requireOption("scene-image");

		CameraType type = CameraType.virtual(imageSource);
		CameraConfig camera = new CameraConfig(name, type, ObserverLocationSetting.explicit(latitude, longitude));
		CameraProjection projection = buildProjection(scanner, focalLength);
		applyDistortion(scanner, projection);
		camera.setProjection(projection);
		camera.setVirtualScenePath(scenePath);
		camera.setVirtualImagePlacement(placement);

		if (type.getOrientationMode() == OrientationMode.FIXED) {
			camera.getCalibrationHistory().append(new CalibrationEntry(0L,
					new Orientation(altitude, azimuth, roll), focalLength, latitude, longitude));
		} else {
			camera.setCurrentOrientation(new Orientation(altitude, azimuth, roll));
			camera.setCurrentLocation(ObserverLocationSetting.explicit(latitude, longitude));
		}

		return camera;
	}

	private static VirtualImageSource buildVirtualImageSource(ArgScanner scanner) {
		String source = scanner.requireOption("virtual-image-source");
		if ("equirectangular360".equalsIgnoreCase(source) || "equirectangular_360".equalsIgnoreCase(source))
			return VirtualImageSource.EQUIRECTANGULAR_360;
		if ("static".equalsIgnoreCase(source) || "static_directional".equalsIgnoreCase(source))
			return VirtualImageSource.STATIC_DIRECTIONAL;
		throw new CliUsageException(
				"unknown --virtual-image-source \"" + source + "\" (expected equirectangular360 or static)");
	}

	private static VirtualImagePlacement buildVirtualImagePlacement(ArgScanner scanner) {
		String placement = scanner.option("virtual-placement").orElse("layer1");
		if ("layer1".equalsIgnoreCase(placement))
			return VirtualImagePlacement.LAYER_1;
		if ("layer4".equalsIgnoreCase(placement))
			return VirtualImagePlacement.LAYER_4;
		throw new CliUsageException("unknown --virtual-placement \"" + placement + "\" (expected layer1 or layer4)");
	}

	// The primary way a user is meant to work with this app (direct user instruction): a named
	// multi-camera "library" (config.CameraLibrary) instead of encoding a whole camera as CLI flags
	// on every launch - "java -jar app.jar preview --camera polaris" looks "polaris" up by name.
	// Checked before --config/raw-flags, same reasoning as those: an explicit, more specific choice
	// wins. Same RealCaptureMode/type-governs-everything stance as loadFromConfigFile(...) below -
	// no mode check duplicated here either.
	private static CameraConfig loadFromLibrary(ArgScanner scanner) throws IOException {
		String name = scanner.requireOption("camera");
		CameraLibrary library = new CameraLibrary(libraryDirectory(scanner));
		if (!library.contains(name))
			throw new CliUsageException("no camera named \"" + name + "\" in library " + library.getDirectory());
		return library.load(name);
	}

	// ~/.camera-viewing/cameras by default - a persistent per-user directory, deliberately distinct
	// from --cache-dir's tmp-based default elsewhere in this file (that's a cache, safe to lose;
	// this is data). Package-visible so cli.AppCommand can point its own CameraLibrary at the same
	// directory without duplicating the default.
	static File libraryDirectory(ArgScanner scanner) {
		return new File(scanner.option("library-dir")
				.orElse(new File(System.getProperty("user.home"), ".camera-viewing/cameras").getPath()));
	}

	// ~/.camera-viewing/cache by default - corrected this round after a real user report: every
	// interactive/scripted command previously defaulted --cache-dir to a DIFFERENT
	// java.io.tmpdir-based subdirectory of its own (e.g. "camera-viewing-preview-cache" vs.
	// "camera-viewing-app-cache"), which meant (a) the cache the user expected to find under
	// ~/.camera-viewing/ (matching libraryDirectory(...)'s own already-established convention right
	// above) was actually sitting in a temp directory instead - often RAM-backed (tmpfs) on Linux,
	// which is a plausible real contributor to "choking hard" on a large external-drive archive, not
	// just disk I/O - and (b) a camera scanned once via "preview" didn't share its cache with the
	// SAME camera opened later via app mode, wasting a full re-walk for no reason. Package-visible,
	// shared by AppCommand/PreviewCommand/CalibrateCommand/CacheUpdateCommand - deliberately NOT
	// used by ReprocessCommand, which keeps its own `<output-dir>/.cache` default (a self-contained,
	// portable-per-export-run convention that predates this fix and is still the right choice for a
	// one-shot batch job with its own output directory).
	static File cacheDirectory(ArgScanner scanner) {
		return new File(
				scanner.option("cache-dir").orElse(new File(System.getProperty("user.home"), ".camera-viewing/cache").getPath()));
	}

	// The interactive circuit breaker's limit (source.DirectoryCache's maxNewDirectoriesPerScan) -
	// see CLAUDE.md's "Local file cache" section for the full reasoning and the user's own request.
	// Default of 10 is the user's own explicit starting number ("value to be fine tuned") - easy to
	// raise via --cache-scan-limit once they've seen it trip in practice and decided on a better one
	// for their own archive's actual directory shape, not something to second-guess here.
	static int cacheScanLimit(ArgScanner scanner) {
		return scanner.intOption("cache-scan-limit", 10);
	}

	// source.ArchiveFrameCache's memoization interval - a second real user report (after the circuit
	// breaker above): re-walking the whole archive tree on every single interactive render (up to 4
	// times a second while playing, or on every calibration spinner drag) stalls the UI for a large
	// archive even once the on-disk cache is fully warm, since DirectoryCache still does a real
	// File.list() per directory on every visit. Default 5 seconds - reused unless the operator has a
	// specific reason to trade "how promptly does a newly-arrived live frame show up" against "how
	// often do we touch a slow/external drive."
	static long cacheRefreshIntervalMillis(ArgScanner scanner) {
		double seconds = scanner.doubleOption("cache-refresh-interval-seconds", 5.0);
		if (seconds <= 0.0)
			throw new CliUsageException("--cache-refresh-interval-seconds must be positive, got " + seconds);
		return (long) (seconds * 1000.0);
	}

	// ~/.camera-viewing/settings.properties by default - the ONE global settings file (config.
	// GlobalSettings/GlobalSettingsStore), deliberately separate from libraryDirectory(...)'s
	// per-camera profiles above. Package-visible, same "--xxx overrides a ~/.camera-viewing/...
	// default" shape as cacheDirectory(...)/libraryDirectory(...).
	static File globalSettingsPath(ArgScanner scanner) {
		return new File(scanner.option("settings-file")
				.orElse(new File(System.getProperty("user.home"), ".camera-viewing/settings.properties").getPath()));
	}

	// The loaded profile's own RealCaptureMode/type governs everything - the "mode" a caller passed
	// to buildRealCamera(...) (what a flag-built camera for *this* subcommand would need) is not
	// forced onto it. A mismatch (e.g. --config points at a Pre-recorded-only profile but the
	// subcommand needed a "latest" source) surfaces naturally and clearly downstream - e.g.
	// LiveCameraSaver.saveLatest(...) already throws IllegalStateException for a camera with no
	// live source - rather than needing a redundant check duplicated here.
	private static CameraConfig loadFromConfigFile(String path) throws IOException {
		File file = new File(path);
		try {
			return CameraConfigStore.load(file);
		} catch (CameraConfigFormatException e) {
			throw new CliUsageException("invalid --config file \"" + path + "\": " + e.getMessage());
		}
	}

	private static CameraProjection buildProjection(ArgScanner scanner, double focalLength) {
		String lens = scanner.option("lens").orElse("rectilinear");
		if ("rectilinear".equals(lens))
			return new RectilinearProjection(focalLength);
		if ("fisheye".equals(lens)) {
			double maxAngleDegrees = scanner.requireDoubleOption("fisheye-max-angle");
			return new FisheyeProjection(focalLength, Math.toRadians(maxAngleDegrees));
		}
		throw new CliUsageException("unknown --lens \"" + lens + "\" (expected rectilinear or fisheye)");
	}

	// --barrel-a/b/c/d [CLAUDE.md's "Barrel distortion built into the lens hierarchy"] - all-or-
	// nothing, matching the persisted-file shape (config.CameraConfigStore writes/reads all four
	// together or none at all). Absent entirely (the common case) leaves buildProjection(...)'s
	// result at its identity default. Every CameraProjection buildProjection(...) can return already
	// extends AbstractCameraProjection.
	private static void applyDistortion(ArgScanner scanner, CameraProjection projection) {
		boolean anyPresent = scanner.option("barrel-a").isPresent() || scanner.option("barrel-b").isPresent()
				|| scanner.option("barrel-c").isPresent() || scanner.option("barrel-d").isPresent();
		if (!anyPresent)
			return;
		double a = scanner.requireDoubleOption("barrel-a");
		double b = scanner.requireDoubleOption("barrel-b");
		double c = scanner.requireDoubleOption("barrel-c");
		double d = scanner.requireDoubleOption("barrel-d");
		((AbstractCameraProjection) projection).setDistortionCoefficients(a, b, c, d);
	}

	private static RealImageSource buildRealImageSource(ArgScanner scanner, RealCaptureMode mode) {
		String archiveTemplate = scanner.requireOption("archive-template");
		TimezoneSetting timezone = buildTimezone(scanner);

		if (mode == RealCaptureMode.LIVE_AND_RECORDED) {
			String latestPath = scanner.requireOption("latest");
			return RealImageSource.liveAndRecorded(latestPath, archiveTemplate, timezone, DstAmbiguousPolicy.ASSUME_STANDARD);
		}
		return RealImageSource.preRecordedOnly(archiveTemplate, timezone, DstAmbiguousPolicy.ASSUME_STANDARD);
	}

	private static TimezoneSetting buildTimezone(ArgScanner scanner) {
		String timezone = scanner.option("timezone").orElse("system");
		if ("system".equals(timezone))
			return TimezoneSetting.useSystemDefault();
		return TimezoneSetting.explicit(ZoneId.of(timezone));
	}

	// ~/.camera-viewing/star-visibility-overrides.csv by default - see catalog.
	// StarVisibilityOverrides' own class comment for the file's shape/purpose.
	static File starVisibilityOverridesPath(ArgScanner scanner) {
		return new File(scanner.option("star-visibility-file")
				.orElse(new File(System.getProperty("user.home"), ".camera-viewing/star-visibility-overrides.csv")
						.getPath()));
	}

	static List<StarCoordinate> loadStars(ArgScanner scanner) throws IOException {
		String tierName = scanner.option("stars").orElse("main");
		StarCatalogTier tier = parseTier(tierName);

		StarCatalogLoader loader = new StarCatalogLoader();
		InputStream stream = CameraConfigArgs.class.getResourceAsStream("/stars.db");
		if (stream == null)
			throw new IOException("stars.db not found on classpath");
		List<StarCoordinate> base;
		try {
			base = loader.load(stream);
		} finally {
			stream.close();
		}

		List<StarCoordinate> overrides = StarVisibilityOverrides.load(starVisibilityOverridesPath(scanner));
		List<StarCoordinate> merged = StarVisibilityOverrides.merge(base, overrides);
		return loader.filterByTier(merged, tier);
	}

	private static StarCatalogTier parseTier(String tierName) {
		if ("main".equals(tierName))
			return StarCatalogTier.MAIN;
		if ("named".equals(tierName))
			return StarCatalogTier.NAMED;
		if ("all".equals(tierName))
			return StarCatalogTier.ALL;
		if ("visible".equals(tierName))
			return StarCatalogTier.VISIBLE_ONLY;
		throw new CliUsageException("unknown --stars \"" + tierName + "\" (expected main, named, all, or visible)");
	}

	static ColorScheme colorScheme(ArgScanner scanner) {
		String preset = scanner.option("color-scheme").orElse("default");
		if ("default".equalsIgnoreCase(preset))
			return ColorPresets.defaultScheme();
		if ("deuteranopia".equalsIgnoreCase(preset))
			return ColorPresets.deuteranopiaFriendlyScheme();
		if ("high-contrast".equalsIgnoreCase(preset))
			return ColorPresets.highContrastScheme();
		throw new CliUsageException(
				"unknown --color-scheme \"" + preset + "\" (expected default, deuteranopia, or high-contrast)");
	}

	static double minRadius(ArgScanner scanner) {
		return scanner.doubleOption("min-radius", 5.0);
	}

	// Composes loadStars/colorScheme/minRadius plus every remaining rendering toggle into the
	// FrameCompositor.Options that BatchReprocessor.reprocessFrame(...)/LiveCameraSaver.
	// saveLatest(...)/LiveWatchDaemon take directly. cameraImage/placement are deliberately left
	// unset - compositeOverlay(...) always overrides both per frame, so there is nothing meaningful
	// for a CLI flag to set on either. setManualSkyToggle(...)/setManualHideGroundToggle(...)/
	// setGroundColor(...)/setGroundPaintsOverObjects(...) are deliberately NOT wired to any flag
	// either, for a different reason - see RENDER_FLAGS_USAGE's own comment (they're structurally
	// inert for every camera this CLI can ever build).
	//
	// wallClock drives --crosshair-offset-hours' reference-time computation (see below) - injected
	// rather than calling System.currentTimeMillis() directly, matching this module's established
	// clock.WallClock/SimulatedClock testability seam.
	//
	// Every toggle defaults to false/off (or the spec-default color scheme), matching every affected
	// FrameCompositor.Options field's own default - a caller passing none of these flags gets
	// byte-identical output to before this method (or any of its flags) existed.
	static FrameCompositor.Options buildOptions(ArgScanner scanner, WallClock wallClock) throws Exception {
		List<StarCoordinate> stars = loadStars(scanner);

		FrameCompositor.Options options = new FrameCompositor.Options()
				.setStars(stars)
				.setColorScheme(colorScheme(scanner))
				.setMinSunMoonRadiusPixels(minRadius(scanner))
				.setShowLabels(scanner.booleanOption("labels", false))
				.setShowGraticule(scanner.booleanOption("graticule", false))
				.setGraticuleStepDegrees(scanner.doubleOption("graticule-ra-step", 30.0),
						scanner.doubleOption("graticule-dec-step", 15.0))
				.setShowCelestialOrigin(scanner.booleanOption("celestial-origin", false))
				.setShowObserverCardinalCross(scanner.booleanOption("observer-cardinal-cross", false))
				.setShowBoresightReferenceLines(scanner.booleanOption("boresight-reference-lines", false))
				.setSunPathMode(parseEclipticAnalemmaMode("sun-path", scanner))
				.setMoonPathMode(parseEclipticAnalemmaMode("moon-path", scanner));

		boolean showOsd = scanner.booleanOption("osd", false);
		boolean showOsdDetail = scanner.booleanOption("osd-detail", false);
		// Both showOsd and showOsdDetail need setOsdTimezone(...) set (validated at
		// FrameCompositor.compose(...) time) - computed once here rather than duplicated per tier.
		// Reuses the same --timezone flag/"system" default already used for archive-filename parsing
		// (buildTimezone(...)) rather than a redundant second timezone flag. The two concepts are
		// distinct in principle (CLAUDE.md's Osd class comment ties display timezone to "the observer
		// location setting's own resolved zone", not the archive-parsing one specifically), but this
		// CLI has no other timezone concept to offer, and TimezoneSetting's own default reasoning
		// ("images are timestamped in the zone of whoever captured/labeled them, which by default is
		// assumed to be you") applies just as well to "what zone should the OSD display in" - both
		// boil down to "the app operator's own timezone" absent a reason to say otherwise.
		if (showOsd || showOsdDetail) {
			options.setOsdTimezone(buildTimezone(scanner).resolve());
			Optional<String> osdColor = scanner.option("osd-color");
			if (osdColor.isPresent())
				options.setOsdTextColor(parseHexColor("osd-color", osdColor.get()));
		}
		if (showOsd)
			options.setShowOsd(true);
		if (showOsdDetail)
			options.setShowWatchedObjectDetail(parseWatchedObject(scanner, stars));

		// The crosshair, the path, and the OSD detail tier all share one --watched-object identity,
		// per direct instruction - a user wanting them pointed at genuinely different objects needs
		// the programmatic FrameCompositor.Options API directly, not this CLI (each of
		// setWatchedObjectCrosshair(...)/setShowWatchedObjectPath(...)/
		// setShowWatchedObjectDetail(...) already supports that independently; the CLI just doesn't
		// expose three separate object-selection flags for it).
		//
		// The crosshair's reference time is computed ONCE here, from wallClock's "now" plus
		// --crosshair-offset-hours (default -24.0, a day ago) - not re-derived per render. For
		// save-latest (a single live render per invocation) this is exactly right. For reprocess
		// (potentially many archived frames) and watch (potentially many ticks over a long-running
		// process) - both sharing one Options instance across every render, see BatchReprocessor.
		// compositeOverlay(...)'s and LiveWatchDaemon's own comments on why that sharing is safe -
		// this means the SAME fixed reference point (computed once, at startup) is marked on every
		// frame/tick, not one that tracks "N hours before THIS render" as reprocessing/watching
		// continues. Deliberate, not an oversight: making it per-render would need compositeOverlay
		// (...) to also mutate watchedObjectReferenceTime the way it already mutates cameraImage/
		// placement, and nothing asked for that; "mark where the object was relative to when this
		// command started" is itself a coherent, useful reading of the flag for all three commands.
		if (scanner.booleanOption("crosshair", false)) {
			double offsetHours = scanner.doubleOption("crosshair-offset-hours", -24.0);
			long referenceEpochMillis = wallClock.currentTimeMillis() + (long) (offsetHours * 3_600_000.0);
			options.setWatchedObjectCrosshair(parseWatchedObject(scanner, stars), observationTimeAt(referenceEpochMillis));
		}

		if (scanner.booleanOption("watched-path", false)) {
			options.setShowWatchedObjectPath(parseWatchedObject(scanner, stars));
			double windowHours = scanner.doubleOption("watched-path-hours", 24.0);
			double intervalMinutes = scanner.doubleOption("watched-path-interval-minutes", 10.0);
			options.setWatchedObjectPathWindow((long) (windowHours * 3_600_000.0), (long) (intervalMinutes * 60_000.0));
		}

		if (scanner.booleanOption("watched-object-reference-lines", false))
			options.setShowWatchedObjectReferenceLines(parseWatchedObject(scanner, stars));

		return options;
	}

	private static EclipticAnalemmaMode parseEclipticAnalemmaMode(String flagName, ArgScanner scanner) {
		String value = scanner.option(flagName).orElse("none");
		if ("none".equalsIgnoreCase(value))
			return EclipticAnalemmaMode.NONE;
		if ("ecliptic".equalsIgnoreCase(value))
			return EclipticAnalemmaMode.ECLIPTIC;
		if ("analemma".equalsIgnoreCase(value))
			return EclipticAnalemmaMode.ANALEMMA;
		throw new CliUsageException("unknown --" + flagName + " \"" + value + "\" (expected none, ecliptic, or analemma)");
	}

	private static Color parseHexColor(String flagName, String value) {
		String hex = value.startsWith("#") ? value.substring(1) : value;
		if (hex.length() == 6) {
			try {
				return new Color(Integer.parseInt(hex, 16));
			} catch (NumberFormatException e) {
				// falls through to the usage error below
			}
		}
		throw new CliUsageException("--" + flagName + " must be a #RRGGBB hex color, got \"" + value + "\"");
	}

	// sun/moon/a planet by SolarObjects.OBJECT_LIST name (case-insensitive), or "star:<name>" looked
	// up (case-insensitive) in whichever --stars tier was actually loaded - a name outside that
	// loaded tier is reported as not found even if it exists in the full catalog, the same way an
	// unrecognized planet/lens name is: matching what's actually configured to render, not silently
	// reaching into data this invocation otherwise ignores.
	private static WatchedObject parseWatchedObject(ArgScanner scanner, List<StarCoordinate> stars) {
		String value = scanner.requireOption("watched-object");

		if ("sun".equalsIgnoreCase(value))
			return WatchedObject.sun();
		if ("moon".equalsIgnoreCase(value))
			return WatchedObject.moon();
		for (int i = 1; i < SolarObjects.OBJECT_LIST.length; i++)
			if (SolarObjects.OBJECT_LIST[i].trim().equalsIgnoreCase(value))
				return WatchedObject.planet(i);
		if (value.toLowerCase(Locale.ROOT).startsWith("star:")) {
			String starName = value.substring("star:".length());
			for (StarCoordinate star : stars)
				if (star.getName().equalsIgnoreCase(starName))
					return WatchedObject.star(star);
			throw new CliUsageException(
					"no star named \"" + starName + "\" found in the loaded --stars catalog (check --stars tier)");
		}

		throw new CliUsageException("unknown --watched-object \"" + value
				+ "\" (expected sun, moon, a planet name, or star:<name>)");
	}

	private static ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}
}
