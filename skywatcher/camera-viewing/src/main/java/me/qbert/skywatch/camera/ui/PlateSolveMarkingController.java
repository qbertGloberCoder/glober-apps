package me.qbert.skywatch.camera.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.DistortionSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveMark;
import me.qbert.skywatch.camera.plate.PlateSolveMarkSet;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.source.ArchiveFrameCache;
import me.qbert.skywatch.camera.source.ArchiveFrameScanner;
import me.qbert.skywatch.camera.source.DirectoryCache;
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

// Sprint Item 2 (Phase 4)'s headless-testable core for technique 2 (semi-automated click-and-mark
// plate solving [CLAUDE.md's "Plate solving is Real archived-camera-only"]) - the marking-tab
// counterpart to CalibrationController (technique 1's own headless core; see that class's comment
// for why this split exists at all - JFrame/Window subclasses throw HeadlessException here).
//
// Gated to Real cameras with an archive only - technique 2 needs real photographed frames to click
// real objects in; a Virtual camera's image doesn't even have to be astronomically meaningful (see
// CLAUDE.md's "Plate-solving is Real archived-camera-only"), so it can never be a mark target.
//
// A later round moved the interactive marking-click UI from a small dedicated panel (which drove
// this class's own frames/currentIndex navigation below) into ui.PreviewWindow instead, for better
// click accuracy - see addMarkAtTime(...) below, the entry point that flow now uses. The frame-
// index navigation methods (refreshFrameList()/nextFrame()/previousFrame()/loadCurrentFrameImage()/
// etc.) are no longer driven by any interactive UI as a result, but are left in place: they remain a
// genuinely correct, independently-tested, non-Swing-dependent way to step through an archive's
// frames one at a time, which a future CLI-based technique-2 tool (still unbuilt - see CLAUDE.md's
// "Plate solving is Real archived-camera-only" section) would need in exactly this shape.
// PlateSolveSession itself already restricts to Fixed cameras (real or virtual) - this class narrows
// further to Real specifically, on top of that.
//
// Reuses the SAME plate.PlateSolveSession an open Camera Orientation tab is already editing (passed
// in by the caller, exactly as CalibrationController/orientationPanel's wiring already does) -
// accepting a Phase A result writes into the session's PENDING orientation/zoom, not a separate
// mechanism; the EXISTING Save/Revert buttons on that tab are what actually persists it. Distortion
// coefficients (Phase B) live directly ON the camera's live CameraProjection instance instead (no
// PlateSolveSession involvement, matching the Camera Orientation tab's own existing distortion
// wiring) - Save/Revert persists those too, since it round-trips the whole CameraConfig.
public final class PlateSolveMarkingController {
	private final PlateSolveSession session;
	private final CameraConfig cameraConfig;
	private final ArchiveFrameCache archiveFrameCache;
	private final PlateSolveMarkSet markSet = new PlateSolveMarkSet();

	private List<ArchiveFrameScanner.Frame> frames = Collections.emptyList();
	private int currentIndex = -1;
	private boolean lastRefreshTruncated;

	public PlateSolveMarkingController(PlateSolveSession session, DirectoryCache cache) {
		this(session, cache, ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS);
	}

	// archiveFrameCacheRefreshIntervalMillis - see PreviewController/CalibrationController's own
	// matching overload/comment; the overload above keeps the same default.
	public PlateSolveMarkingController(PlateSolveSession session, DirectoryCache cache,
			long archiveFrameCacheRefreshIntervalMillis) {
		if (session == null)
			throw new IllegalArgumentException("session must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");
		this.session = session;
		this.cameraConfig = session.getCameraConfig();
		requireRealCameraWithArchive(cameraConfig);
		this.archiveFrameCache = new ArchiveFrameCache(cameraConfig.getRealImageSource(), cache, WallClock.SYSTEM,
				archiveFrameCacheRefreshIntervalMillis);
		refreshFrameList();
	}

	private static void requireRealCameraWithArchive(CameraConfig cameraConfig) {
		if (cameraConfig.getType().getKind() != CameraType.Kind.REAL)
			throw new IllegalArgumentException(
					"plate-solve marking (technique 2) is Real-camera-only, was " + cameraConfig.getType().getKind());
		if (cameraConfig.getRealImageSource() == null || cameraConfig.getRealImageSource().getArchiveTemplate() == null)
			throw new IllegalArgumentException(
					"camera " + cameraConfig.getName() + " has no archive to mark frames from");
	}

	public PlateSolveSession getSession() {
		return session;
	}

	public PlateSolveMarkSet getMarkSet() {
		return markSet;
	}

	// FIXED - a real, reported bug: the original implementation called
	// ArchiveFrameScanner.scanTolerant(...) directly, a FULL real recursive walk of the whole
	// archive tree, synchronously, on every controller construction (i.e. every time the user opens
	// a Real-with-archive camera, whether or not they ever visit the Plate Solve tab). Reasoned at
	// the time as "a one-shot operation, marking is occasional, paying for a real walk is fine" - but
	// that reasoning only accounts for HOW OFTEN the scan runs, not how long a single full walk
	// itself takes: for a real multi-year archive (this module's own documented case: 975
	// directories / 676,345 frames took 377 SECONDS for a cold full walk - see CLAUDE.md's "cache
	// was still stalling" rounds), that's minutes of the Swing event thread being blocked on open -
	// indistinguishable from a hang/infinite loop to the user (confirmed directly: "I launched the
	// app, selected the camera, clicked open. The open stays pressed and it is unresponsive").
	//
	// Fixed by routing through source.ArchiveFrameCache instead - the exact same bounded-resync
	// mechanism PreviewController/CalibrationController already use for this exact reason (see that
	// class's own comment): at most TWO real directory listings per resync (the archive root, plus
	// whichever directory holds the frame nearest the target time), never a full tree walk: a
	// resync only runs once per refreshIntervalMillis of REAL elapsed time (or immediately on the
	// first call), and reads between resyncs are a fast, local, in-memory list read. Starts near "now"
	// (the natural place to begin browsing an archive backward, matching the previous "most recent
	// frame" starting point).
	public void refreshFrameList() {
		ArchiveFrameScanner.ScanResult scanResult = archiveFrameCache.currentScanResult(WallClock.SYSTEM.currentTimeMillis());
		frames = scanResult.getFrames();
		lastRefreshTruncated = scanResult.isTruncated();
		currentIndex = frames.isEmpty() ? -1 : frames.size() - 1;
	}

	// A real user report: the tab always opened on the newest ("now") frame regardless of where the
	// Preview window was actually scrubbed to (e.g. scrubbed back to April, but the marking tab still
	// showed today) - confusing, since marking is meant to work against whatever archived moment the
	// user is already looking at. Seeks to the archived frame at-or-before targetEpochMillis within
	// the ALREADY-loaded frame list (the same nearest-frame snap RealCameraScrubber/
	// CalibrationController use elsewhere) - a no-op (stays on the newest frame) if targetEpochMillis
	// is null/predates every archived frame, matching frameAtOrBefore(...)'s own established "no
	// interpolation, no frame before the first one" contract.
	public void seekToTime(long targetEpochMillis) {
		ArchiveFrameScanner.Frame nearest = ArchiveFrameScanner.frameAtOrBefore(frames, targetEpochMillis);
		if (nearest != null)
			currentIndex = frames.indexOf(nearest);
	}

	// See RealCameraScrubber.Result.isArchiveScanTruncated()/CalibrationController.
	// isArchiveScanTruncated() - the same circuit-breaker signal, surfaced here for the same reason.
	public boolean isLastRefreshTruncated() {
		return lastRefreshTruncated;
	}

	public boolean hasFrames() {
		return !frames.isEmpty();
	}

	public int getFrameCount() {
		return frames.size();
	}

	public int getCurrentFrameIndex() {
		return currentIndex;
	}

	public boolean hasNextFrame() {
		return currentIndex >= 0 && currentIndex < frames.size() - 1;
	}

	public boolean hasPreviousFrame() {
		return currentIndex > 0;
	}

	// Index-based stepping (sprint Item 2's own explicit ask) - deliberately NOT the nearest-
	// timestamp scrub ArchiveFrameScanner.frameAtOrBefore(...) already provides elsewhere in this
	// module (RealCameraScrubber/CalibrationController); marking wants "the next/previous frame that
	// actually exists," one at a time, not "the frame nearest an arbitrary target time."
	public void nextFrame() {
		if (hasNextFrame())
			currentIndex++;
	}

	public void previousFrame() {
		if (hasPreviousFrame())
			currentIndex--;
	}

	public ArchiveFrameScanner.Frame getCurrentFrame() {
		if (currentIndex < 0)
			throw new IllegalStateException("no archived frames available to mark - call refreshFrameList() first");
		return frames.get(currentIndex);
	}

	public BufferedImage loadCurrentFrameImage() throws IOException {
		ArchiveFrameScanner.Frame frame = getCurrentFrame();
		BufferedImage image = ImageIO.read(frame.getFile());
		if (image == null)
			throw new IOException("could not read image: " + frame.getFile());
		return image;
	}

	// Registers a mark at the CURRENT frame's own timestamp - a click always marks where an object
	// appeared in whichever frame is presently displayed, never a caller-supplied time. Only ever
	// meaningful when this class's own frame-index navigation is in use (see this class's own comment
	// on why that's no longer the interactive UI's entry point) - addMarkAtTime(...) below is what the
	// Preview-window-driven marking flow actually calls now.
	public void addMark(WatchedObject object, double imagePixelX, double imagePixelY, int imageWidthPixels,
			int imageHeightPixels) {
		addMarkAtTime(object, getCurrentFrame().getEpochMillis(), imagePixelX, imagePixelY, imageWidthPixels,
				imageHeightPixels);
	}

	// Registers a mark at a CALLER-SUPPLIED timestamp instead of this class's own frame-index
	// position - the entry point ui.ControlPanel's Preview-window-driven marking flow uses: a click
	// in the Preview window marks wherever THAT window currently is (PreviewController.
	// getLastRenderedEpochMillis()), entirely independent of whatever this controller's own
	// currentIndex happens to be (which nothing drives interactively anymore - see this class's own
	// comment).
	public void addMarkAtTime(WatchedObject object, long epochMillis, double imagePixelX, double imagePixelY,
			int imageWidthPixels, int imageHeightPixels) {
		markSet.add(PlateSolveMark.fromPixelClick(epochMillis, object, imagePixelX, imagePixelY, imageWidthPixels,
				imageHeightPixels));
	}

	private ObserverLocation currentLocation() {
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(session.getPendingLatitude(), session.getPendingLongitude());
		return location;
	}

	// Phase A - fits altitude/azimuth/barrel-roll/focal-length against the accumulated mark set,
	// starting from the session's own pending values. Distortion is FORCED OFF for the whole search
	// (a real, previously-live gap this UI closes - PlateSolveFitter.residual(...) itself has no
	// distortion-disable of its own, see that class's comment; the fix has to live in the caller's
	// projectionFactory, which is exactly this method) - wide random-search perturbations evaluated
	// with real distortion coefficients active risk the same extrapolation failure class CLAUDE.md's
	// "Item 5, round 5" already found and fixed elsewhere for ordinary rendering.
	public PlateSolveFitter.Result solveOrientation(int canvasWidthPixels, int canvasHeightPixels, long randomSeed,
			int iterations) throws Exception {
		if (markSet.isEmpty())
			throw new IllegalStateException("no marks to solve from - click at least one object first");

		ObserverLocation location = currentLocation();
		PlateSolveFitter.Candidate initialGuess = new PlateSolveFitter.Candidate(session.getPendingOrientation(),
				session.getPendingZoom());

		return PlateSolveFitter.fit(markSet, location, this::distortionDisabledProjectionAt, initialGuess,
				canvasWidthPixels, canvasHeightPixels, randomSeed, iterations);
	}

	private CameraProjection distortionDisabledProjectionAt(double focalLengthMillimeters) {
		CameraProjection projection = cameraConfig.getProjection().withFocalLength(focalLengthMillimeters);
		((AbstractCameraProjection) projection).setDistortionEnabled(false);
		return projection;
	}

	// Commits a Phase A result into the session's PENDING orientation/zoom - not auto-saved, the
	// existing Camera Orientation tab's Save/Revert (unchanged) is what actually persists it, per
	// this class's own comment.
	public void acceptOrientationResult(PlateSolveFitter.Result result) {
		session.adjustOrientation(result.getCandidate().getOrientation());
		session.adjustZoom(result.getCandidate().getFocalLengthMillimeters());
	}

	// Phase B - fits the four barrel-distortion coefficients against the SAME mark set, holding
	// orientation/zoom fixed at the session's CURRENT pending values (normally just-accepted from
	// Phase A, but not required to be - a caller could re-run Phase B alone against a manually-tuned
	// orientation too). Unlike Phase A, distortion is deliberately EVALUATED here (the whole point of
	// this search) - DistortionSolveFitter.fit(...) itself handles enabling it for the search and
	// restoring the projection's original state afterward regardless of outcome.
	public DistortionSolveFitter.Result solveDistortion(DistortionSolveFitter.Coefficients initialGuess,
			int canvasWidthPixels, int canvasHeightPixels, long randomSeed, int iterations) throws Exception {
		if (markSet.isEmpty())
			throw new IllegalStateException("no marks to solve from - click at least one object first");

		ObserverLocation location = currentLocation();
		AbstractCameraProjection projection = (AbstractCameraProjection) cameraConfig.getProjection()
				.withFocalLength(session.getPendingZoom());

		return DistortionSolveFitter.fit(markSet, location, session.getPendingOrientation(), projection, initialGuess,
				canvasWidthPixels, canvasHeightPixels, randomSeed, iterations);
	}

	// Commits a Phase B result directly onto the camera's own live CameraProjection instance (NOT
	// through the session - matches the Camera Orientation tab's existing distortion wiring exactly,
	// see this class's own comment). The existing Save/Revert (unchanged) persists this too, since it
	// round-trips the whole CameraConfig including its projection.
	public void acceptDistortionResult(DistortionSolveFitter.Result result) {
		AbstractCameraProjection projection = (AbstractCameraProjection) cameraConfig.getProjection();
		DistortionSolveFitter.Coefficients coefficients = result.getCoefficients();
		projection.setDistortionCoefficients(coefficients.getA(), coefficients.getB(), coefficients.getC(),
				coefficients.getD());
	}

	// The initial guess Phase B should start from - the projection's OWN currently-live coefficients
	// (whatever was last calibrated or the identity default), not a hardcoded zero - mirrors
	// solveOrientation(...)'s own "start from the session's current pending values" convention.
	public DistortionSolveFitter.Coefficients currentDistortionCoefficients() {
		AbstractCameraProjection projection = (AbstractCameraProjection) cameraConfig.getProjection();
		return new DistortionSolveFitter.Coefficients(projection.getDistortionCoefficientA(),
				projection.getDistortionCoefficientB(), projection.getDistortionCoefficientC(),
				projection.getDistortionCoefficientD());
	}
}
