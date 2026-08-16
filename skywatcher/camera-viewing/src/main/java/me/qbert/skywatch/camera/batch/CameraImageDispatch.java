package me.qbert.skywatch.camera.batch;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.GlobalSettings;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.config.VirtualImagePlacement;
import me.qbert.skywatch.camera.config.VirtualImageSource;
import me.qbert.skywatch.camera.orientation.MountTransformRuntime;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.render.EquirectangularSceneRenderer;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.render.ImagePlacement;
import me.qbert.skywatch.camera.source.ArchiveFrameCache;
import me.qbert.skywatch.camera.source.DirectoryCache;

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

// Task 5.1's camera dispatch: the single entry point Phase 8.1's future preview window (and
// anything else driving an interactive render) calls, regardless of camera kind. Dispatches off
// exactly the two independent axes CLAUDE.md's "Camera setup"/"Camera image display" sections
// describe - Real-vs-Virtual (which also implies Fixed-vs-PTZ) and the Layer-1 show/hide toggle -
// not spec §3's three fixed modes. No rendering logic lives here itself: Real cameras route to
// RealCameraScrubber (4.7's scrub-to-nearest-older-frame case, built last round); Virtual cameras
// load their single configured scene image directly (Fixed - CameraType.getVirtualImageSource() ==
// STATIC_DIRECTIONAL) or re-render it live from a 360-degree source via render.
// EquirectangularSceneRenderer (PTZ - EQUIRECTANGULAR_360) - both new this round, since dispatch had
// nothing to dispatch Virtual cameras to until an image-loading path existed for each case. This is
// exactly why 5.1 was blocked on 4.7/4.8 landing first, per docs/tasks.md.
public final class CameraImageDispatch {
	private CameraImageDispatch() {
	}

	public static final class Result {
		private final BufferedImage image;
		private final boolean cameraImageShown;
		private final long renderedEpochMillis;
		private final boolean archiveScanTruncated;

		Result(BufferedImage image, boolean cameraImageShown, long renderedEpochMillis, boolean archiveScanTruncated) {
			this.image = image;
			this.cameraImageShown = cameraImageShown;
			this.renderedEpochMillis = renderedEpochMillis;
			this.archiveScanTruncated = archiveScanTruncated;
		}

		// The final composited canvas - always non-null. Matches RealCameraScrubber.Result's
		// identical contract: a "no camera image" render is still a real render (sky/graticule/etc.
		// against the fallback canvas size), not an empty result - use isCameraImageShown() to tell
		// the two cases apart, not a null check.
		public BufferedImage getImage() {
			return image;
		}

		public boolean isCameraImageShown() {
			return cameraImageShown;
		}

		// For a Real camera with the image shown, the snapped archived frame's own timestamp (see
		// RealCameraScrubber). For every other case (hidden, or any Virtual camera - which has no
		// per-frame timestamp to snap to), exactly the raw targetEpochMillis passed in.
		public long getRenderedEpochMillis() {
			return renderedEpochMillis;
		}

		// See RealCameraScrubber.Result.isArchiveScanTruncated() - propagated straight through for
		// a Real camera; always false for a Virtual camera (no archive scan exists there at all).
		public boolean isArchiveScanTruncated() {
			return archiveScanTruncated;
		}
	}

	// cache is only consulted for Real cameras (RealCameraScrubber's archive scan) - pass null at
	// an all-Virtual call site that never needs one.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options)
			throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, null);
	}

	// activeEditSession (task "app mode"'s live location/orientation editing): when non-null and
	// the camera is Real, threads straight through to RealCameraScrubber's own override - see that
	// class's matching overload for the full reasoning. Real-camera-only for now, matching where
	// this round's camera library actually lives (Real, Pre-recorded-only cameras) - a Virtual
	// camera's own current orientation/location are already directly mutable/live via
	// compositeVirtual(...) below with no persisted-vs-pending distinction to bridge at all, so
	// there is nothing for this parameter to override there yet.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, null);
	}

	// archiveFrameCache: threads straight through to RealCameraScrubber's own matching overload -
	// see its class comment and source.ArchiveFrameCache's for the full reasoning (a real user
	// report: re-scanning the whole archive tree on every render stalls the UI for a large archive).
	// Irrelevant for Virtual cameras (no archive scan exists there at all) - null (the default, via
	// the overload above) preserves this class's original always-fresh-scan behavior exactly.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, null);
	}

	// globalSettings resolves a PTZ Virtual camera's "use my locale" location setting (config.
	// ObserverLocationSetting.resolve(...)) - null (the default, via the overload above) preserves
	// this class's original behavior exactly: a "use my locale" PTZ camera throws a clear error
	// rather than silently guessing at a location, matching config.ObserverLocationSetting.
	// resolve(...)'s own contract for a null/unconfigured GlobalSettings.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, GlobalSettings globalSettings)
			throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, globalSettings, null);
	}

	// imageCaches (a direct user report: time-scrubbing needlessly rebuilds Layer 1 - re-decoding an
	// archived/scene photo from disk, or re-running EquirectangularSceneRenderer's per-pixel loop -
	// on every render tick, even when nothing that actually determines Layer 1's content has changed
	// since the last call - see batch.LastImageCache's own class comment): threads straight through
	// to RealCameraScrubber's matching overload for a Real camera, and is consulted directly in
	// compositeVirtual(...) below for a Virtual camera's scene-source load and (for PTZ) its
	// rendered-output cache. Null (the default, via the overload above) preserves this class's
	// original always-fresh behavior exactly - every existing caller/test is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, GlobalSettings globalSettings,
			CameraImageCaches imageCaches) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, globalSettings, imageCaches, null);
	}

	// mountRuntime (the missing wiring found by an earlier "full backlog audit" round - see
	// orientation.MountTransformRuntime's own class comment): consulted only for a PTZ Virtual camera
	// (compositeVirtual(...)'s !isFixed branch) to actually apply the equatorial-mount/geolocation-
	// stabilizer transform MountControl.mode/isEnabled() describe - every other branch is unaffected.
	// Null (the default, via the overload above) preserves this class's original behavior exactly -
	// every existing caller/test is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, GlobalSettings globalSettings,
			CameraImageCaches imageCaches, MountTransformRuntime mountRuntime) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, globalSettings, imageCaches, mountRuntime, null);
	}

	// astronomy (Item 0's shared-instance architecture - see CameraAstronomy's own class comment):
	// threads straight through to RealCameraScrubber's/compositeVirtual's own matching overloads.
	// Null (the default, via the overload above) preserves this class's original always-fresh-
	// construction behavior exactly - every existing caller/test is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, GlobalSettings globalSettings,
			CameraImageCaches imageCaches, MountTransformRuntime mountRuntime, CameraAstronomy astronomy)
			throws Exception {
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");

		if (cameraConfig.getType().getKind() == CameraType.Kind.REAL) {
			if (cache == null)
				throw new IllegalArgumentException("cache must not be null for a Real camera");
			RealCameraScrubber.Result result = RealCameraScrubber.composite(cameraConfig, cache, targetEpochMillis,
					imageShown, fallbackCanvasWidth, fallbackCanvasHeight, options, activeEditSession, archiveFrameCache,
					imageCaches, astronomy);
			return new Result(result.getImage(), result.isRealImageShown(), result.getRenderedEpochMillis(),
					result.isArchiveScanTruncated());
		}

		return compositeVirtual(cameraConfig, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, globalSettings, imageCaches, mountRuntime, astronomy);
	}

	// Virtual cameras are never opaque-RGB-flattened the way RealCameraScrubber flattens a shown
	// Real photo - transparency is meaningful here (CLAUDE.md's "Camera setup": the "unicorns on a
	// see-through background" case, plus EquirectangularSceneRenderer's own synthetic alpha outside
	// the lens's representable angle) and feeds LayerVisibility's sky-transparency exception, which
	// is Virtual-camera-only.
	private static Result compositeVirtual(CameraConfig cameraConfig, long targetEpochMillis, boolean imageShown,
			int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			GlobalSettings globalSettings, CameraImageCaches imageCaches, MountTransformRuntime mountRuntime,
			CameraAstronomy astronomy) throws Exception {
		CameraProjection storedProjection = BatchReprocessor.requireProjection(cameraConfig);
		boolean isFixed = cameraConfig.getType().getOrientationMode() == OrientationMode.FIXED;

		CameraProjection projection;
		Orientation orientation;
		double latitude;
		double longitude;
		if (isFixed) {
			// A Fixed Virtual camera uses the exact same time-versioned CalibrationHistory as a
			// Fixed Real camera (CLAUDE.md: "have the same save/revert rules as a real camera") -
			// not a separate mechanism.
			CalibrationEntry calibration = cameraConfig.getCalibrationHistory().latestAsOf(targetEpochMillis);
			if (calibration == null)
				throw new IllegalStateException(
						"no calibration entry covers time " + targetEpochMillis + " for camera " + cameraConfig.getName());
			orientation = calibration.getOrientation();
			latitude = calibration.getLatitude();
			longitude = calibration.getLongitude();
			// See CameraProjection.withFocalLength(...)'s own comment - same fix as the Real-camera
			// path, applied here since a Fixed Virtual camera shares the identical calibration-zoom
			// mechanism.
			projection = storedProjection.withFocalLength(calibration.getZoom());
		} else {
			orientation = cameraConfig.getCurrentOrientation();
			// resolve(...) is the real resolver "use my locale" always implied but never had - see
			// config.ObserverLocationSetting's own comment. A null globalSettings (every pre-existing
			// caller) preserves the exact original behavior: a clear IllegalStateException, just
			// raised by resolve(...) itself now rather than duplicated here.
			ObserverLocationSetting locationSetting = cameraConfig.getCurrentLocation().resolve(globalSettings);
			latitude = locationSetting.getLatitude();
			longitude = locationSetting.getLongitude();
			// A PTZ Virtual camera has no per-current-zoom field yet (no calibration history at all -
			// "may use any projection freely" per CLAUDE.md, but nothing wires a live zoom change for
			// it yet) - unaffected by this fix, matches its existing behavior exactly.
			projection = storedProjection;
		}

		// Item 0's shared-instance architecture (see CameraAstronomy's own class comment): when
		// astronomy is non-null, mutate its shared ObservationTime/ObserverLocation instead of
		// constructing fresh ones every call - null preserves the original always-fresh-construction
		// behavior exactly.
		ObservationTime time;
		ObserverLocation location;
		if (astronomy != null) {
			astronomy.applyTimeAndLocation(targetEpochMillis, latitude, longitude);
			time = astronomy.getObservationTime();
			location = astronomy.getObserverLocation();
		} else {
			time = new ObservationTime();
			time.initTime(TimeZone.getTimeZone("UTC"));
			time.setUnixTime(targetEpochMillis);

			location = new ObserverLocation();
			location.setGeoLocation(latitude, longitude);
		}

		// The missing wiring found by an earlier "full backlog audit" round - see orientation.
		// MountTransformRuntime's own class comment. PTZ Virtual cameras only (!isFixed) - a Fixed
		// Virtual camera has no single mutable currentOrientation field to lock/engage against, a
		// deliberately separate, not-yet-built follow-up. Must run before the image-loading block
		// below, since a PTZ camera's rendered-output cache key (and the equirectangular render
		// itself) needs the FINAL, mount-transformed orientation, not the raw stored one.
		if (!isFixed && mountRuntime != null)
			orientation = mountRuntime.resolve(cameraConfig, orientation, time, location);

		// Distortion correction is meaningless for a Virtual camera's synthetic image, Fixed or PTZ -
		// direct user instruction (see AbstractCameraProjection's own class comment). Unconditional,
		// not gated on imageShown/placement - unlike the Real-camera case, there is no scenario where
		// a Virtual camera's distortion should ever apply. Mutating whichever instance "projection"
		// currently refers to is safe either way: the Fixed branch above already produced a fresh,
		// render-local copy via withFocalLength(...), and the PTZ branch's storedProjection reference
		// is never serialized (this flag is deliberately not persisted), so setting it here every
		// render is a harmless no-op repeat, not a leak into saved state.
		((AbstractCameraProjection) projection).setDistortionEnabled(false);

		// This dispatch path is always a Virtual camera (Fixed or PTZ) - the dusk/night fade is
		// scoped to Virtual cameras only (direct user instruction - see FrameCompositor.Options'
		// applyLayer1DuskFade field comment), so it's unconditionally on here.
		options.setApplyLayer1DuskFade(true);

		// A final copy for the lambda below - "orientation" itself is reassigned above (the mount
		// wiring), so it's no longer effectively final.
		final Orientation resolvedOrientation = orientation;

		BufferedImage image = null;
		if (imageShown) {
			String scenePath = cameraConfig.getVirtualScenePath();
			if (scenePath == null)
				throw new IllegalStateException("camera " + cameraConfig.getName() + " has no scene image configured");

			// virtualSceneSource() cache: scenePath is a single fixed configured file that essentially
			// never changes between calls - skip a redundant ImageIO.read when it hasn't.
			BufferedImage sceneSource = imageCaches != null
					? imageCaches.virtualSceneSource().getOrCompute(new Object[] { scenePath },
							() -> loadSceneSource(scenePath))
					: loadSceneSource(scenePath);

			if (cameraConfig.getType().getVirtualImageSource() == VirtualImageSource.EQUIRECTANGULAR_360) {
				// ptzRenderedOutput() cache: skips EquirectangularSceneRenderer's full O(width*height)
				// per-pixel render entirely whenever scene/orientation/zoom/canvas size are all
				// unchanged since the last call - the fix that actually matters for PTZ performance
				// during pure time-scrubbing. projection is compared by reference identity (it has no
				// equals() override) - correct here since a zoom edit REPLACES the CameraProjection
				// instance (withFocalLength(...)) rather than mutating it in place, and PTZ cameras
				// never have their distortion coefficients live-edited (Real-camera-only UI gate), so
				// identity is a safe, sufficient proxy for "did the effective projection change".
				image = imageCaches != null
						? imageCaches.ptzRenderedOutput().getOrCompute(
								new Object[] { scenePath, resolvedOrientation.getAltitude(), resolvedOrientation.getAzimuth(),
										resolvedOrientation.getBarrelRoll(), projection, fallbackCanvasWidth,
										fallbackCanvasHeight },
								() -> EquirectangularSceneRenderer.render(sceneSource, projection, resolvedOrientation,
										fallbackCanvasWidth, fallbackCanvasHeight))
						: EquirectangularSceneRenderer.render(sceneSource, projection, resolvedOrientation, fallbackCanvasWidth,
								fallbackCanvasHeight);
			} else {
				image = sceneSource;
			}
		}

		options.setCanvasSize(fallbackCanvasWidth, fallbackCanvasHeight);
		if (image != null) {
			ImagePlacement placement = cameraConfig.getVirtualImagePlacement() == VirtualImagePlacement.LAYER_1
					? ImagePlacement.LAYER_1
					: ImagePlacement.LAYER_4;
			options.setCameraImage(image).setPlacement(placement);
		} else {
			options.setCameraImage(null).setPlacement(ImagePlacement.NONE);
		}

		BufferedImage composited = FrameCompositor.compose(projection, orientation, time, location, options, astronomy);
		return new Result(composited, image != null, targetEpochMillis, false);
	}

	private static BufferedImage loadSceneSource(String scenePath) throws IOException {
		BufferedImage sceneSource = ImageIO.read(new File(scenePath));
		if (sceneSource == null)
			throw new IOException("could not read image: " + scenePath);
		return sceneSource;
	}
}
