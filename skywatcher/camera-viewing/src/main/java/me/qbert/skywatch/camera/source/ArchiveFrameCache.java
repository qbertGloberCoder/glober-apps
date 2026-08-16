package me.qbert.skywatch.camera.source;

import java.io.File;
import java.util.Collections;
import java.util.List;

import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.RealImageSource;

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

// Round 3 of a real user report. Rounds 1-2 (time-based memoization, then a local-only read path via
// ArchiveFrameScanner.scanCachedOnly(...)) were still not enough: currentScanResult(...) was calling
// scanCachedOnly(...) - a full local read PLUS a TimestampPattern regex parse of EVERY cached frame
// - on EVERY SINGLE CALL, not just when a resync was actually due. For an archive with hundreds of
// thousands of frames, that parse alone is real work (the user's own report: a window RESIZE, which
// fires renderCurrentFrame() repeatedly, took 3-4 seconds - each resize event was re-parsing the
// entire archive from scratch). This class now caches the PARSED frame list itself
// (cachedFrames) and only re-parses it as part of an actual resync, never on an ordinary read.
//
// Also implements the user's own suggestion directly: "when it's 'live' it should not be checking
// the cache so often but the 'latest path'." A Live+recorded RealImageSource's "latest" file's mtime
// is ALREADY this module's established signal for "a new frame has arrived" (CLAUDE.md's "Image
// sources" section - the same mtime batch.LiveWatchDaemon already polls) - reused here as a cheap
// (a single File.lastModified() stat, not a directory listing) gate on whether the real, bounded
// resync (and the reparse that follows it) is even worth doing at all. When the mtime hasn't changed
// since the last check, NOTHING new has been captured, so the resync is skipped entirely - only a
// Pre-recorded-only source (no latest path, no liveness signal available) falls back to
// unconditionally resyncing on every refreshIntervalMillis elapsed.
public final class ArchiveFrameCache {
	public static final long DEFAULT_REFRESH_INTERVAL_MILLIS = 5_000L;

	private final RealImageSource source;
	private final DirectoryCache directoryCache;
	private final WallClock wallClock;
	private final long refreshIntervalMillis;
	private final File latestFile; // null for a Pre-recorded-only source - no liveness signal available

	private List<ArchiveFrameScanner.Frame> cachedFrames;
	private boolean lastTruncated;
	private long lastScanWallMillis = -1L;
	private long lastKnownLatestMtime = Long.MIN_VALUE; // sentinel no real File.lastModified() can equal

	public ArchiveFrameCache(RealImageSource source, DirectoryCache directoryCache, WallClock wallClock) {
		this(source, directoryCache, wallClock, DEFAULT_REFRESH_INTERVAL_MILLIS);
	}

	public ArchiveFrameCache(RealImageSource source, DirectoryCache directoryCache, WallClock wallClock,
			long refreshIntervalMillis) {
		if (source == null)
			throw new IllegalArgumentException("source must not be null");
		if (directoryCache == null)
			throw new IllegalArgumentException("directoryCache must not be null");
		if (wallClock == null)
			throw new IllegalArgumentException("wallClock must not be null");
		if (refreshIntervalMillis <= 0)
			throw new IllegalArgumentException("refreshIntervalMillis must be positive");
		this.source = source;
		this.directoryCache = directoryCache;
		this.wallClock = wallClock;
		this.refreshIntervalMillis = refreshIntervalMillis;
		this.latestFile = source.hasLatestSource() ? new File(source.getLatestPath()) : null;
	}

	// targetEpochMillis is the caller's current render/scrub time - used only to decide WHICH single
	// directory the bounded resync's second check should re-verify (see maybeResync(...)), never to
	// filter the returned frame list itself - the full cached list is always returned;
	// frameAtOrBefore(...) is the caller's own job, same as every round before this one.
	public ArchiveFrameScanner.ScanResult currentScanResult(long targetEpochMillis) {
		maybeResync(targetEpochMillis);
		List<ArchiveFrameScanner.Frame> frames = cachedFrames != null ? cachedFrames : Collections.emptyList();
		return new ArchiveFrameScanner.ScanResult(frames, lastTruncated);
	}

	// Forces the NEXT currentScanResult(...) call to perform a real resync immediately, regardless
	// of the refresh interval OR the latest-file mtime check - an explicit "check for new files now"
	// escape hatch (e.g. a future UI button, or right after the user runs "cache-update"). Not wired
	// to any UI control yet.
	public void invalidate() {
		lastScanWallMillis = -1L;
		lastKnownLatestMtime = Long.MIN_VALUE;
	}

	private void maybeResync(long targetEpochMillis) {
		long now = wallClock.currentTimeMillis();
		if (lastScanWallMillis >= 0 && now - lastScanWallMillis < refreshIntervalMillis)
			return;
		lastScanWallMillis = now;

		// The cheap liveness gate: a Live+recorded camera's "latest" file mtime is watched ONLY to
		// detect "a new frame has arrived" (CLAUDE.md's own established convention) - if it hasn't
		// changed, nothing was captured since the last check, so there is nothing for a real resync
		// to find. A single stat() call, not a directory listing - dramatically cheaper than what
		// follows, and skips that work entirely on every check that finds nothing new.
		if (latestFile != null) {
			long currentMtime = latestFile.lastModified();
			if (currentMtime == lastKnownLatestMtime)
				return;
			lastKnownLatestMtime = currentMtime;
		}

		boolean truncated = false;
		// A real, reported bug: on the VERY FIRST resync of a freshly-constructed instance (every
		// app launch or camera switch builds a new one), cachedFrames is still null - falling back
		// to Collections.emptyList() here meant frameAtOrBefore(...) below always returned null on
		// that first call, so the "nearest frame's own directory" bounded check never fired at all.
		// Concretely: a day directory already known from a PRIOR session (its own name unchanged at
		// the root level - nothing for the root-level check below to notice either) that gained new
		// files SINCE that session (e.g. a night's worth of captures landing while the app was
		// closed) was never re-verified on the next launch - confirmed directly by reproducing a
		// user's real report ("overnight-capture folder gains new files while the app is closed,
		// they're missing after relaunch"). Seeding from the ON-DISK cache (scanCachedOnly - a cheap,
		// local-only read, no real filesystem I/O beyond reading partition files) instead of an empty
		// list gives the very first resync something real to compute "nearest" from, so it can find
		// and re-check the right directory immediately rather than only ever discovering brand-new
		// top-level directories until a SECOND resync has a chance to populate cachedFrames itself.
		// Subsequent resyncs are unaffected (cachedFrames is already populated by then, from the
		// previous resync's own final scanCachedOnly call below) - this only changes the first call.
		List<ArchiveFrameScanner.Frame> knownSoFar = cachedFrames != null ? cachedFrames
				: ArchiveFrameScanner.scanCachedOnly(source, directoryCache);

		File root = new File(PathTemplate.rootDirectory(source.getArchiveTemplate()));
		try {
			directoryCache.listChildren(root);
		} catch (DirectoryScanLimitExceededException e) {
			truncated = true;
		}

		ArchiveFrameScanner.Frame nearest = ArchiveFrameScanner.frameAtOrBefore(knownSoFar, targetEpochMillis);
		if (nearest != null) {
			try {
				directoryCache.listChildren(nearest.getFile().getParentFile());
			} catch (DirectoryScanLimitExceededException e) {
				truncated = true;
			}
		}

		// Exactly one local reparse per actual resync - never per ordinary read (see the class
		// comment's "3-4 second resize" bug this fixes).
		cachedFrames = ArchiveFrameScanner.scanCachedOnly(source, directoryCache);
		lastTruncated = truncated;
	}
}
