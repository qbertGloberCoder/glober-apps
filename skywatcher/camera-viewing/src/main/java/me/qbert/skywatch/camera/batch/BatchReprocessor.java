package me.qbert.skywatch.camera.batch;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CalibrationEntry;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.render.ImagePlacement;
import me.qbert.skywatch.camera.source.ArchiveFrameScanner;

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

// Phase 6.2's batch/historical reprocessing mode - ties together ArchiveFrameScanner (6.1),
// CalibrationHistory (1.2), and render.FrameCompositor (Phase 4's full layer stack) into "for each
// archived frame, load the real captured image and burn the computed sky overlay onto it."
//
// Real cameras only - archived frames are a Real-camera-only concept (see CLAUDE.md's "Camera
// setup"). Real cameras always sit in Layer 1 (never a placement choice, unlike Virtual cameras -
// see the Layer model), and per LayerVisibility's auto-disable rules an opaque Layer-1 image forces
// 3A/ground off by default with no transparency exception (that exception is Virtual-only) - so a
// default-Options FrameCompositor call here only ever paints the objects sub-layer (sun/moon/
// planets/stars) on top of the loaded image, not sky/ground. That's also the actually-useful output
// here: burning computed object positions onto a real photo is spec §5's whole reason bounding
// circles exist - visually confirming calibration accuracy.
//
// Each frame is resolved against the calibration that was *actually in effect* at that frame's own
// timestamp (CalibrationHistory.latestAsOf(...)), not whatever the camera's current/latest
// calibration happens to be - the entire point of spec §7.2's time-versioned history.
//
// Routed through FrameCompositor.compose(...) rather than calling render.CelestialObjectsLayer's
// paintXxx methods directly, as it originally did - labels/graticule/OSD/watched-object
// crosshair-path/ecliptic-analemma all existed only as stubs or didn't exist at all when this class
// was first written; now that FrameCompositor covers the full layer stack, this class gets it for
// free by construction rather than needing its own copy of that wiring.
//
// Takes a caller-supplied FrameCompositor.Options directly (rather than unpacking stars/
// colorScheme/minSunMoonRadiusPixels as separate parameters, as it originally did) - the caller
// configures whatever labels/graticule/OSD/watched-object/path toggles they want, and
// compositeOverlay(...) only ever overrides cameraImage/placement on it (see that method's own
// comment: both are inherent to what a Real camera's frame IS, not a caller choice, since Real
// cameras always sit in Layer 1 - see the Layer model). A default-constructed Options (every new
// capability off) reproduces the exact same output this class always produced. Also deliberately
// does not write results to disk or decide on output file naming/directory structure - task 6.4
// ("save-cameras") is where that decision belongs; this class only produces composited images in
// memory.
public final class BatchReprocessor {
	private BatchReprocessor() {
	}

	public static final class Result {
		private final ArchiveFrameScanner.Frame frame;
		private final BufferedImage image;

		Result(ArchiveFrameScanner.Frame frame, BufferedImage image) {
			this.frame = frame;
			this.image = image;
		}

		public ArchiveFrameScanner.Frame getFrame() {
			return frame;
		}

		public BufferedImage getImage() {
			return image;
		}
	}

	public static List<Result> reprocessAll(List<ArchiveFrameScanner.Frame> frames, CameraConfig cameraConfig,
			FrameCompositor.Options options) throws Exception {
		List<Result> results = new ArrayList<Result>(frames.size());
		for (ArchiveFrameScanner.Frame frame : frames)
			results.add(new Result(frame, reprocessFrame(frame, cameraConfig, options)));
		return results;
	}

	// starCatalog (Item 0's shared-instance architecture - see CameraAstronomy's own class comment):
	// when non-null, builds ONE CameraAstronomy for the WHOLE run (not per frame) - set to
	// StarCatalogTier.ALL so it renders exactly the caller-supplied list regardless of which
	// original tier selection produced it (FrameCompositor.Options is deliberately write-only, no
	// getStars() - see that class's own note - so the caller passes the same list it already gave
	// Options.setStars(...) explicitly, rather than this class reaching into Options for it), then
	// mutated via applyTimeAndLocation(...) per frame instead of a fresh ObservationTime/
	// ObserverLocation/Sun/Moon/Planets/Stars construction per frame. null (the overload above)
	// preserves this class's original always-fresh-construction behavior exactly - every existing
	// caller/test is unaffected. **Still open, flagged rather than silently wired end-to-end**: no
	// CLI command constructs and passes a real CameraAstronomy here yet - see docs/tasks.md's Item 0
	// entry, same status as ui.PreviewController's outer AppController/ControlPanel wiring.
	public static List<Result> reprocessAll(List<ArchiveFrameScanner.Frame> frames, CameraConfig cameraConfig,
			FrameCompositor.Options options, List<me.qbert.skywatch.camera.catalog.StarCoordinate> starCatalog)
			throws Exception {
		me.qbert.skywatch.camera.astro.CameraAstronomy astronomy = null;
		if (starCatalog != null) {
			astronomy = new me.qbert.skywatch.camera.astro.CameraAstronomy(TimeZone.getTimeZone("UTC"), starCatalog);
			astronomy.setStarMode(me.qbert.skywatch.camera.catalog.StarCatalogTier.ALL);
		}

		List<Result> results = new ArrayList<Result>(frames.size());
		for (ArchiveFrameScanner.Frame frame : frames)
			results.add(new Result(frame, reprocessFrame(frame, cameraConfig, options, astronomy)));
		return results;
	}

	public static BufferedImage reprocessFrame(ArchiveFrameScanner.Frame frame, CameraConfig cameraConfig,
			FrameCompositor.Options options) throws Exception {
		return reprocessFrame(frame, cameraConfig, options, null);
	}

	public static BufferedImage reprocessFrame(ArchiveFrameScanner.Frame frame, CameraConfig cameraConfig,
			FrameCompositor.Options options, me.qbert.skywatch.camera.astro.CameraAstronomy astronomy)
			throws Exception {
		if (frame == null)
			throw new IllegalArgumentException("frame must not be null");

		BufferedImage image = ImageIO.read(frame.getFile());
		if (image == null)
			throw new IOException("could not read image: " + frame.getFile());

		return compositeOverlay(image, frame.getEpochMillis(), cameraConfig, options, astronomy);
	}

	// The compositing core shared with batch.LiveCameraSaver (6.4) - the only difference between
	// "reprocess an archived frame" and "save the latest live frame" is which image gets loaded and
	// which epoch drives the overlay (a frame's own filename-derived timestamp vs. wall-clock "now");
	// resolving calibration and compositing the frame is identical either way.
	//
	// Mutates the caller's options (setCameraImage(...)/setPlacement(LAYER_1)) rather than treating
	// it as read-only - cameraImage necessarily changes per frame (reprocessAll(...) calls this once
	// per archived frame against the SAME options instance) and placement is never a Real camera's
	// choice to begin with, so there is nothing for the caller to meaningfully set on either field
	// beforehand; whatever was there is overwritten. Every other field (stars, colorScheme, labels,
	// graticule, OSD, watched-object crosshair/path, ...) is the caller's own configuration and is
	// read as-is, untouched. Callers that need the same Options object for something else afterward
	// should not assume cameraImage/placement still hold whatever they were before this call.
	static BufferedImage compositeOverlay(BufferedImage image, long epochMillis, CameraConfig cameraConfig,
			FrameCompositor.Options options) throws Exception {
		return compositeOverlay(image, epochMillis, cameraConfig, options, null);
	}

	// astronomy - see the reprocessAll(...) overload's own comment. null preserves this method's
	// original always-fresh-construction behavior exactly.
	static BufferedImage compositeOverlay(BufferedImage image, long epochMillis, CameraConfig cameraConfig,
			FrameCompositor.Options options, me.qbert.skywatch.camera.astro.CameraAstronomy astronomy)
			throws Exception {
		if (image == null)
			throw new IllegalArgumentException("image must not be null");
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		CalibrationEntry calibration = resolveCalibration(cameraConfig, epochMillis);
		// withFocalLength(calibration.getZoom()) is what makes a versioned zoom change in the
		// calibration history actually affect the rendered FOV - requireProjection(...) alone is the
		// camera's fixed, persisted lens definition, not a per-frame current zoom (see
		// CameraProjection.withFocalLength(...)'s own comment for the full story - a real user
		// report found "zoom" had no effect anywhere in this pipeline before this fix).
		CameraProjection projection = requireProjection(cameraConfig).withFocalLength(calibration.getZoom());
		// This class always shows the real photo in Layer 1 (see the class comment - reprocess/
		// save-latest never have a hide toggle) - distortion correction is always meaningful here.
		((AbstractCameraProjection) projection).setDistortionEnabled(true);
		Orientation orientation = calibration.getOrientation();

		// This class is always a Real camera - the dusk/night fade is Virtual-camera-only (direct
		// user instruction - see FrameCompositor.Options' applyLayer1DuskFade field comment). Already
		// the default, set explicitly here for the same defense-in-depth clarity as distortion above.
		options.setApplyLayer1DuskFade(false);

		options.setCameraImage(image).setPlacement(ImagePlacement.LAYER_1);

		BufferedImage composited;
		if (astronomy != null) {
			astronomy.applyTimeAndLocation(epochMillis, calibration.getLatitude(), calibration.getLongitude());
			composited = FrameCompositor.compose(projection, orientation, astronomy.getObservationTime(),
					astronomy.getObserverLocation(), options, astronomy);
		} else {
			ObservationTime time = observationTimeAt(epochMillis);
			ObserverLocation location = locationFor(calibration);
			composited = FrameCompositor.compose(projection, orientation, time, location, options);
		}
		return toOpaqueRgb(composited);
	}

	// Shared with batch.RealCameraScrubber (4.7's interactive scrub case) - both resolve "what
	// calibration governs this Real camera at this timestamp" identically; the only difference
	// between the two classes is whether an image ends up in Layer 1 at all.
	static CalibrationEntry resolveCalibration(CameraConfig cameraConfig, long epochMillis) {
		if (cameraConfig.getType().getKind() != CameraType.Kind.REAL)
			throw new IllegalArgumentException("batch/live reprocessing is Real-camera-only");

		CalibrationEntry calibration = cameraConfig.getCalibrationHistory().latestAsOf(epochMillis);
		if (calibration == null)
			throw new IllegalStateException("no calibration entry covers time " + epochMillis
					+ " for camera " + cameraConfig.getName());
		return calibration;
	}

	static CameraProjection requireProjection(CameraConfig cameraConfig) {
		CameraProjection projection = cameraConfig.getProjection();
		if (projection == null)
			throw new IllegalStateException("camera " + cameraConfig.getName() + " has no projection configured");
		return projection;
	}

	static ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	static ObserverLocation locationFor(CalibrationEntry calibration) {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(calibration.getLatitude(), calibration.getLongitude());
		return location;
	}

	// FrameCompositor.compose(...) always returns a TYPE_INT_ARGB canvas (it has to, generically -
	// some placements/toggles genuinely need alpha, e.g. a transparent Virtual scene). A Real
	// camera's Layer-1 photo is always fully opaque already (LayerVisibility forces sky/ground off
	// for it, so nothing here ever paints a transparent pixel either), so an alpha channel on the
	// result is never meaningful - only a liability, since several ImageIO writers (notably JPEG,
	// the typical real-archive format) silently refuse to write an ARGB image at all rather than
	// dropping the channel themselves. Converting back to a plain RGB image here, once, at the
	// shared compositing core, is simpler than pushing that concern onto every caller that writes
	// this result to disk.
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
