package me.qbert.skywatch.camera.batch;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.render.FrameCompositor;

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

// Phase 6.3's live-watch daemon mode [spec §9] ("update-live-cameras", the old prototype's own
// name) - the piece that decides *when* to call 6.4's LiveCameraSaver.saveLatest(...): watches each
// configured camera's "latest" file for a change, exactly per CLAUDE.md's "Camera image display" -
// "latest.jpg's mtime is used only to detect 'now' - watch it for changes to know a new live frame
// has arrived." Do not read this class as re-deriving the overlay's *render* time from mtime - that
// is still wall-clock "now" via LiveCameraSaver, per that section's explicit warning against relying
// on mtime for anything else. mtime here is purely a change-detection signal, nothing more.
//
// Split into a pure, testable per-tick step (tick()) and a thin run() loop wrapper, mirroring this
// module's established convention of injectable/testable time seams (clock.WallClock/SimulatedClock)
// rather than baking real sleeps into the tested surface. Scheduling policy beyond "poll everything
// on one fixed interval" (per-camera independent intervals, a thread pool, etc.) is left to whatever
// wires this into a real deployment (task 6.5's CLI) - not anticipated here.
//
// One FrameCompositor.Options is shared across every watched camera and every tick - fine, since
// BatchReprocessor.compositeOverlay(...) (which LiveCameraSaver.saveLatest(...) routes through)
// only ever mutates its cameraImage/placement fields, synchronously, once per saveLatest(...) call;
// nothing here reads those two fields back afterward, so overwriting them per-camera each tick has
// no cross-camera effect. Every other Options field (stars, colorScheme, labels, graticule, OSD,
// ...) is genuinely shared configuration, applied identically to every camera this daemon watches.
public final class LiveWatchDaemon {

	// One camera's watch state: the camera + its dedicated output file (task 6.4's own stance -
	// output naming is caller-supplied, not decided by this class either) + the last-observed
	// "latest" file mtime, so repeated ticks can tell whether a new frame has actually arrived.
	public static final class Watch {
		private final CameraConfig cameraConfig;
		private final File outputFile;
		private long lastSeenMtime = -1L;

		public Watch(CameraConfig cameraConfig, File outputFile) {
			if (cameraConfig == null)
				throw new IllegalArgumentException("cameraConfig must not be null");
			if (outputFile == null)
				throw new IllegalArgumentException("outputFile must not be null");
			this.cameraConfig = cameraConfig;
			this.outputFile = outputFile;
		}

		public CameraConfig getCameraConfig() {
			return cameraConfig;
		}

		public File getOutputFile() {
			return outputFile;
		}
	}

	// Per-tick outcome: which cameras had a new frame detected and were successfully saved, and
	// which failed (with their exception) - one camera's failure never prevents the others in the
	// same tick from being checked, matching a real daemon's expected resilience (a transient I/O
	// error on one camera, e.g. reading a file mid-write, shouldn't stall every other camera).
	public static final class TickResult {
		private final List<CameraConfig> saved = new ArrayList<CameraConfig>();
		private final Map<CameraConfig, Exception> failed = new HashMap<CameraConfig, Exception>();

		public List<CameraConfig> getSaved() {
			return saved;
		}

		public Map<CameraConfig, Exception> getFailed() {
			return failed;
		}
	}

	private final List<Watch> watches;
	private final FrameCompositor.Options options;
	private final WallClock wallClock;

	public LiveWatchDaemon(List<Watch> watches, FrameCompositor.Options options, WallClock wallClock) {
		if (watches == null)
			throw new IllegalArgumentException("watches must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		if (wallClock == null)
			throw new IllegalArgumentException("wallClock must not be null");
		this.watches = watches;
		this.options = options;
		this.wallClock = wallClock;
	}

	// Checks every watched camera's "latest" file mtime once. A camera with no live source
	// configured yet (or whose "latest" file doesn't currently exist) is silently skipped, not
	// treated as a failure - a daemon watching several cameras shouldn't error out just because one
	// hasn't been set up yet. The very first tick for a given Watch always saves (lastSeenMtime
	// starts at the sentinel -1L, which never equals a real mtime) - a freshly-started daemon
	// renders the current latest frame immediately rather than waiting for the next change.
	public TickResult tick() {
		TickResult result = new TickResult();

		for (Watch watch : watches) {
			RealImageSource source = safeGetRealImageSource(watch.cameraConfig);
			if (source == null || !source.hasLatestSource())
				continue;

			File latestFile = new File(source.getLatestPath());
			if (!latestFile.isFile())
				continue;

			long mtime = latestFile.lastModified();
			if (mtime == watch.lastSeenMtime)
				continue;

			try {
				LiveCameraSaver.saveLatest(watch.cameraConfig, options, wallClock, watch.outputFile);
				watch.lastSeenMtime = mtime;
				result.saved.add(watch.cameraConfig);
			} catch (Exception e) {
				result.failed.put(watch.cameraConfig, e);
			}
		}

		return result;
	}

	// getRealImageSource() throws for a Virtual camera (type-gated, see CameraConfig) rather than
	// returning null - normalize that into "nothing to watch" here too, so a misconfigured Watch
	// entry doesn't need special-casing by the caller.
	private RealImageSource safeGetRealImageSource(CameraConfig cameraConfig) {
		try {
			return cameraConfig.getRealImageSource();
		} catch (IllegalStateException e) {
			return null;
		}
	}

	// Blocking run loop: tick(), sleep intervalMillis, repeat until stopRequested returns true.
	// Deliberately minimal - see the class comment on why real scheduling policy belongs elsewhere.
	public void run(long intervalMillis, BooleanSupplier stopRequested) {
		if (stopRequested == null)
			throw new IllegalArgumentException("stopRequested must not be null");

		while (!stopRequested.getAsBoolean()) {
			tick();
			try {
				Thread.sleep(intervalMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}
}
