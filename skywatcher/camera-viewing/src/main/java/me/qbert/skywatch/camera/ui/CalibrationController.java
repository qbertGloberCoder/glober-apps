package me.qbert.skywatch.camera.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.TimeZone;

import javax.imageio.ImageIO;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.render.FrameCompositor;
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

// Task 8.2's headless-testable core - the calibration-UI counterpart to PreviewController (see its
// class comment for why this split exists: JFrame/Window subclasses throw HeadlessException in a
// display-less environment, so every real decision has to live somewhere that isn't one). Wraps
// task 7.1's plate.PlateSolveSession (adjust pending orientation/location/zoom, Save/Revert) with
// the one thing a manual plate-solve UI (technique 1 - visual comparison, see CLAUDE.md's "Plate
// solving") actually needs on top of it: finding and loading the real archived still frame closest
// to a target time to preview against, since PlateSolveSession.render(...) takes an already-loaded
// still image, not a time.
//
// Reuses ArchiveFrameScanner.frameAtOrBefore(...) directly (the same task 0.6/4.7 scrub algorithm
// RealCameraScrubber uses) rather than routing through RealCameraScrubber/CameraImageDispatch -
// those two always resolve orientation from the camera's PERSISTED CalibrationHistory, which is
// exactly the value calibration mode needs to override with the session's PENDING one instead, so
// reusing them here would mean immediately overriding half of what they compute.
public final class CalibrationController {
	private final PlateSolveSession session;
	private final DirectoryCache cache;
	private final FrameCompositor.Options options;
	private boolean lastRenderArchiveScanTruncated;
	// Memoizes the archive scan (see ArchiveFrameCache's own class comment) - a real user report:
	// renderPreview(...) is called on every single orientation/zoom/location spinner drag while
	// calibrating, and re-walking the whole archive tree on every one of those events stalls the UI
	// for a large archive even more readily than PreviewController's own 250ms render timer does.
	private final ArchiveFrameCache archiveFrameCache;

	public CalibrationController(PlateSolveSession session, DirectoryCache cache, FrameCompositor.Options options) {
		this(session, cache, options, ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS);
	}

	// archiveFrameCacheRefreshIntervalMillis - see PreviewController's matching overload/comment;
	// the overload above keeps the previous default for every existing caller/test.
	public CalibrationController(PlateSolveSession session, DirectoryCache cache, FrameCompositor.Options options,
			long archiveFrameCacheRefreshIntervalMillis) {
		if (session == null)
			throw new IllegalArgumentException("session must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		this.session = session;
		this.cache = cache;
		this.options = options;
		this.archiveFrameCache = new ArchiveFrameCache(session.getCameraConfig().getRealImageSource(), cache,
				WallClock.SYSTEM, archiveFrameCacheRefreshIntervalMillis);
	}

	public PlateSolveSession getSession() {
		return session;
	}

	// Finds the archived frame at or before targetEpochMillis (task 0.6's scrub algorithm - no
	// interpolation), loads it, and composites the overlay on top of it using the session's PENDING
	// orientation/location/zoom - "apply override, recompute, redraw" against a real photo, exactly
	// task 7.1's manual technique. Throws IllegalStateException if no archived frame exists at or
	// before that time - there is nothing to visually compare against yet.
	public BufferedImage renderPreview(long targetEpochMillis) throws Exception {
		ArchiveFrameScanner.ScanResult scanResult = archiveFrameCache.currentScanResult(targetEpochMillis);
		lastRenderArchiveScanTruncated = scanResult.isTruncated();

		ArchiveFrameScanner.Frame frame = ArchiveFrameScanner.frameAtOrBefore(scanResult.getFrames(), targetEpochMillis);
		if (frame == null)
			throw new IllegalStateException("no archived frame at or before " + targetEpochMillis + " to calibrate against");

		BufferedImage stillFrame = ImageIO.read(frame.getFile());
		if (stillFrame == null)
			throw new IOException("could not read image: " + frame.getFile());

		ObservationTime time = observationTimeAt(frame.getEpochMillis());
		return session.render(stillFrame, time, options);
	}

	// See RealCameraScrubber.Result.isArchiveScanTruncated() - the same circuit-breaker signal,
	// surfaced here since this class bypasses RealCameraScrubber entirely (see the class comment).
	public boolean isArchiveScanTruncated() {
		return lastRenderArchiveScanTruncated;
	}

	public void adjustOrientation(Orientation orientation) {
		session.adjustOrientation(orientation);
	}

	public void adjustLocation(double latitude, double longitude) {
		session.adjustLocation(latitude, longitude);
	}

	public void adjustZoom(double zoom) {
		session.adjustZoom(zoom);
	}

	public boolean hasPendingEdit() {
		return session.hasPendingEdit();
	}

	// Commits the pending values as a new calibration entry effective as of the still frame most
	// recently rendered by renderPreview(...) - callers that want a different effective time should
	// call PlateSolveSession.save(...) on getSession() directly instead.
	public void save(long effectiveFromEpochMillis) throws IOException {
		session.save(effectiveFromEpochMillis);
	}

	public void revert() throws IOException {
		session.revert();
	}

	private static ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}
}
