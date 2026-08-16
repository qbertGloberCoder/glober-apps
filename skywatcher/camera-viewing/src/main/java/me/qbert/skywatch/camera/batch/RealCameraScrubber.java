package me.qbert.skywatch.camera.batch;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.render.ImagePlacement;
import me.qbert.skywatch.camera.source.ArchiveFrameCache;
import me.qbert.skywatch.camera.source.ArchiveFrameScanner;
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

// Task 0.6/4.7's interactive scrubbing case for a Real, Fixed camera: given an arbitrary target
// time from a UI time control (not a specific archived Frame like BatchReprocessor's 6.2 use, and
// not wall-clock "now" like LiveCameraSaver's 6.4 use - those two are the batch/live cases, this
// is the third, interactive one), decide what Layer 1 shows and what time everything else renders
// against.
//
// Two outcomes, matching CLAUDE.md's "Camera image display" show/hide split exactly:
//  - imageShown: find the archived frame whose filename timestamp is the closest one at-or-before
//    targetEpochMillis (ArchiveFrameScanner.frameAtOrBefore(...) - no interpolation), show it in
//    Layer 1, and render everything else against THAT frame's own timestamp, not the raw scrub
//    target - this is what makes scrubbing "snap" to real image timestamps rather than a fixed
//    granularity. If targetEpochMillis predates the whole archive, there is nothing to snap to yet
//    - falls back to the same "no image" behavior as !imageShown, rendered at the raw scrub target.
//  - !imageShown: no image at all (Layer 1 stays empty), rendered against targetEpochMillis
//    exactly - continuous/arbitrary scrubbing, since there's no image to snap to.
// Either way the calibration entry actually in effect at the render time (CalibrationHistory.
// latestAsOf(...), via BatchReprocessor.resolveCalibration(...)) governs orientation/location -
// not a separate calibration-resolution rule, just a third caller of the same one BatchReprocessor/
// LiveCameraSaver already use.
public final class RealCameraScrubber {
	private RealCameraScrubber() {
	}

	public static final class Result {
		private final BufferedImage image;
		private final boolean realImageShown;
		private final long renderedEpochMillis;
		private final boolean archiveScanTruncated;

		Result(BufferedImage image, boolean realImageShown, long renderedEpochMillis, boolean archiveScanTruncated) {
			this.image = image;
			this.realImageShown = realImageShown;
			this.renderedEpochMillis = renderedEpochMillis;
			this.archiveScanTruncated = archiveScanTruncated;
		}

		// The final composited canvas - always non-null, the same FrameCompositor.compose(...)
		// output a caller would get from BatchReprocessor/LiveCameraSaver. When no real photo ends
		// up in Layer 1 (hidden, or a scrub target before the archive begins), this is still a real
		// render - just of the sky/graticule/etc. against the fallback canvas size, with nothing in
		// Layer 1. Use isRealImageShown() to tell the two cases apart, not a null check here.
		public BufferedImage getImage() {
			return image;
		}

		// Whether an actual captured photo ended up in Layer 1 for this render - false for hidden
		// mode, and false when imageShown was requested but the scrub target predates the whole
		// archive (nothing to show yet).
		public boolean isRealImageShown() {
			return realImageShown;
		}

		// The timestamp actually rendered against - the snapped frame's own timestamp when an
		// image was found and shown, or the raw scrub target otherwise (hidden, or shown but no
		// archived frame exists yet at or before it).
		public long getRenderedEpochMillis() {
			return renderedEpochMillis;
		}

		// True when the archive scan behind this render was cut short by DirectoryCache's circuit
		// breaker (source.DirectoryScanLimitExceededException) - the render still completed normally
		// against whatever frames were found before that happened; this only flags that a
		// "cache-update" run would find more. Always false when imageShown was false (no scan
		// happens at all in that case) or the cache has no limit configured.
		public boolean isArchiveScanTruncated() {
			return archiveScanTruncated;
		}
	}

	// fallbackCanvasWidth/fallbackCanvasHeight size the canvas for the no-image case (hidden, or a
	// scrub target before the archive begins) - ignored by FrameCompositor whenever an image ends
	// up in Layer 1, since the image's own dimensions govern the canvas then (Options.
	// setCanvasSize(...)'s own documented contract), so it's simplest to just always set it rather
	// than branching.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options)
			throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, null);
	}

	// activeEditSession (task "app mode"'s live location/orientation editing) is the interactive-
	// preview counterpart to CalibrationController.renderPreview(...): when non-null, its PENDING
	// orientation/location are used instead of resolving the persisted CalibrationHistory entry,
	// so a live edit made from the control panel's Location tab (or an open CalibrationWindow
	// sharing the same session) is immediately visible in the next scrub/render - not just in a
	// separately-opened Calibrate window. Null (the default, via the overload above) preserves this
	// class's original behavior exactly - every existing caller is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, null);
	}

	// archiveFrameCache (a real user report: the interactive render loop calls composite(...) up to
	// 4 times a second while playing, and re-scanning the WHOLE archive tree on every single one of
	// those calls stalls the UI for a large archive - see source.ArchiveFrameCache's own class
	// comment for the full story): when non-null, its memoized ArchiveFrameScanner.ScanResult is
	// used instead of a fresh scanTolerant(...) call every time - cache is then only consulted for
	// whatever archiveFrameCache itself does internally, not scanned directly here. Null (the
	// default, via the overload above) preserves this class's original always-fresh-scan behavior
	// exactly - every existing caller (batch commands, which only ever scan once per invocation
	// anyway, and every existing test) is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache) throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, null);
	}

	// imageCaches (a direct user report: time-scrubbing needlessly re-decodes the same archived photo
	// from disk on every render tick, even when the scrub resolves to the identical frame as last
	// time - see batch.LastImageCache's own class comment): when non-null, its realFrameImage() slot
	// is consulted before ImageIO.read(...), keyed on the resolved frame's own File. Null (the
	// default, via the overload above) preserves this class's original always-fresh-read behavior
	// exactly - every existing caller/test is unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, CameraImageCaches imageCaches)
			throws Exception {
		return composite(cameraConfig, cache, targetEpochMillis, imageShown, fallbackCanvasWidth, fallbackCanvasHeight,
				options, activeEditSession, archiveFrameCache, imageCaches, null);
	}

	// astronomy (Item 0's shared-instance architecture): when non-null, mutates its shared
	// ObservationTime/ObserverLocation via applyTimeAndLocation(...) instead of constructing fresh
	// ones on every single call - the fix for the confirmed per-render-tick object churn this class
	// (and everything CelestialObjectsLayer/Graticule build underneath it) used to incur, up to 4
	// times a second while playing. Null (the default, via the overload above) preserves this
	// class's original always-fresh-construction behavior exactly - every existing caller/test is
	// unaffected.
	public static Result composite(CameraConfig cameraConfig, DirectoryCache cache, long targetEpochMillis,
			boolean imageShown, int fallbackCanvasWidth, int fallbackCanvasHeight, FrameCompositor.Options options,
			PlateSolveSession activeEditSession, ArchiveFrameCache archiveFrameCache, CameraImageCaches imageCaches,
			me.qbert.skywatch.camera.astro.CameraAstronomy astronomy) throws Exception {
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");

		BufferedImage image = null;
		long renderedEpochMillis = targetEpochMillis;
		boolean archiveScanTruncated = false;

		if (imageShown) {
			ArchiveFrameScanner.ScanResult scanResult = archiveFrameCache != null
					? archiveFrameCache.currentScanResult(targetEpochMillis)
					: ArchiveFrameScanner.scanTolerant(cameraConfig.getRealImageSource(), cache);
			archiveScanTruncated = scanResult.isTruncated();
			ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.frameAtOrBefore(scanResult.getFrames(), targetEpochMillis);
			if (frame != null) {
				// A real user report: a file that WAS in the scanned list can still fail to read (moved/
				// renamed by the user's own archiving script between scan and read, especially plausible
				// against a large, actively-managed archive) - ImageIO.read(...) throwing here used to
				// propagate uncaught, crashing PreviewWindow's entire render Timer loop on the EDT for
				// one bad frame. Treated the same as "no frame found" (image stays null, renderedEpochMillis
				// stays at the raw target) rather than failing the whole render - a single unreadable
				// frame should degrade gracefully, not take down interactive preview/calibrate entirely.
				File file = frame.getFile();
				try {
					image = imageCaches != null
							? imageCaches.realFrameImage().getOrCompute(new Object[] { file }, () -> ImageIO.read(file))
							: ImageIO.read(file);
				} catch (Exception e) {
					image = null;
				}
				if (image != null)
					renderedEpochMillis = frame.getEpochMillis();
			}
		}

		CameraProjection storedProjection = BatchReprocessor.requireProjection(cameraConfig);

		// withFocalLength(...) is what makes "zoom" actually affect the render - storedProjection
		// alone is the camera's fixed, persisted lens definition, not a per-frame current zoom (see
		// CameraProjection.withFocalLength(...)'s own comment). Resolved per-branch, matching
		// orientation/location: a live edit session's PENDING zoom when active, otherwise whatever
		// the resolved calibration entry says.
		CameraProjection projection;
		Orientation orientation;
		double latitude;
		double longitude;
		if (activeEditSession != null) {
			orientation = activeEditSession.getPendingOrientation();
			latitude = activeEditSession.getPendingLatitude();
			longitude = activeEditSession.getPendingLongitude();
			projection = storedProjection.withFocalLength(activeEditSession.getPendingZoom());
		} else {
			CalibrationEntry calibration = BatchReprocessor.resolveCalibration(cameraConfig, renderedEpochMillis);
			orientation = calibration.getOrientation();
			ObserverLocation resolvedLocation = BatchReprocessor.locationFor(calibration);
			latitude = resolvedLocation.getLatitude();
			longitude = resolvedLocation.getLongitude();
			projection = storedProjection.withFocalLength(calibration.getZoom());
		}

		// Distortion correction only makes sense while an actual real photo is on screen to align
		// against - direct user instruction (see AbstractCameraProjection's own class comment). image
		// == null exactly matches ImagePlacement.NONE below - the same "is a real photo currently
		// shown" signal, not a separate concept.
		((AbstractCameraProjection) projection).setDistortionEnabled(image != null);

		// This is always a Real camera - the dusk/night fade is Virtual-camera-only (direct user
		// instruction - see FrameCompositor.Options' applyLayer1DuskFade field comment). Already the
		// default, set explicitly here for the same defense-in-depth clarity as distortion above.
		options.setApplyLayer1DuskFade(false);

		options.setCanvasSize(fallbackCanvasWidth, fallbackCanvasHeight);
		if (image != null)
			options.setCameraImage(image).setPlacement(ImagePlacement.LAYER_1);
		else
			options.setCameraImage(null).setPlacement(ImagePlacement.NONE);

		BufferedImage composited;
		if (astronomy != null) {
			astronomy.applyTimeAndLocation(renderedEpochMillis, latitude, longitude);
			composited = FrameCompositor.compose(projection, orientation, astronomy.getObservationTime(),
					astronomy.getObserverLocation(), options, astronomy);
		} else {
			ObservationTime time = BatchReprocessor.observationTimeAt(renderedEpochMillis);
			ObserverLocation location = new ObserverLocation();
			location.setGeoLocation(latitude, longitude);
			composited = FrameCompositor.compose(projection, orientation, time, location, options);
		}
		BufferedImage result = image != null ? toOpaqueRgb(composited) : composited;
		return new Result(result, image != null, renderedEpochMillis, archiveScanTruncated);
	}

	// Matches BatchReprocessor.toOpaqueRgb(...)'s own reasoning exactly when an image is present -
	// a Real camera's Layer-1 photo is always fully opaque, so the alpha channel FrameCompositor.
	// compose(...) always allocates is never meaningful there. Not applied in the no-image case:
	// with nothing in Layer 1, the canvas can legitimately have transparent regions (e.g. sky/
	// ground manually toggled off), so flattening to opaque RGB would silently discard real
	// information instead of a no-op - this class's two outcomes genuinely differ here, unlike
	// BatchReprocessor/LiveCameraSaver which only ever have the image-present case.
	private static BufferedImage toOpaqueRgb(BufferedImage argbImage) {
		BufferedImage rgbImage = new BufferedImage(argbImage.getWidth(), argbImage.getHeight(), BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = rgbImage.createGraphics();
		try {
			g2d.drawImage(argbImage, 0, 0, null);
		} finally {
			g2d.dispose();
		}
		return rgbImage;
	}
}
