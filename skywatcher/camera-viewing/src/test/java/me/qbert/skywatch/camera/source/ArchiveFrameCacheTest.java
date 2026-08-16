package me.qbert.skywatch.camera.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;

// Rounds 2-3 of a real user report. Round 2: reads go through ArchiveFrameScanner.
// scanCachedOnly(...) (purely local), and only a BOUNDED real check (the archive root, plus
// whichever directory holds the frame nearest the current target time) happens on the throttled
// interval - never a full recursive re-walk of an already-cached tree, no matter how large it is.
// Round 3: scanCachedOnly(...)'s own (non-trivial, for a huge archive) local parse is now cached
// too, re-run only as part of an actual resync rather than on every single currentScanResult(...)
// call - fixing a real reported bug (a window resize taking 3-4 seconds, since resize fires many
// renders in quick succession, each redoing a full local reparse). Round 3 also adds the "latest"
// mtime liveness gate for Live+recorded sources - see the two dedicated tests near the bottom.
class ArchiveFrameCacheTest {

	private static final class FakeWallClock implements WallClock {
		private long millis;

		FakeWallClock(long startMillis) {
			this.millis = startMillis;
		}

		void advance(long deltaMillis) {
			millis += deltaMillis;
		}

		@Override
		public long currentTimeMillis() {
			return millis;
		}
	}

	@Test
	void firstCallFindsFramesInAFreshArchive(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, new FakeWallClock(0L));

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());
	}

	@Test
	void aSecondCallWithinTheIntervalDoesNotSeeANewlyAddedFrame(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		writeBlankImage(new File(cameraRoot, "day0/20260809_120100_1.jpg"));
		wallClock.advance(1_000L); // still within the 5s interval

		assertEquals(1, cache.currentScanResult(t0).getFrames().size(),
				"a call within the refresh interval must not trigger a real resync");
	}

	@Test
	void aCallAfterTheIntervalElapsesPicksUpANewFrameInTheRootDirectory(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		// A brand-new top-level entry - must be caught by the resync's own root-level check.
		writeBlankImage(new File(cameraRoot, "day1/20260810_120000_1.jpg"));
		wallClock.advance(5_000L);

		assertEquals(2, cache.currentScanResult(t0).getFrames().size(),
				"a call after the refresh interval elapses must pick up new top-level content");
	}

	@Test
	void aCallAfterTheIntervalElapsesPicksUpANewFrameInTheDirectoryNearestTheTargetTime(@TempDir File tempDir)
			throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0 + 90_000L).getFrames().size());

		// A new file landing INSIDE an already-cached directory (e.g. a live camera's "today" folder
		// still growing) - must be caught by the resync's "nearest directory" check, not just the
		// root-level one, since the directory itself isn't new.
		writeBlankImage(new File(cameraRoot, "day0/20260809_120100_1.jpg"));
		wallClock.advance(5_000L);

		assertEquals(2, cache.currentScanResult(t0 + 90_000L).getFrames().size(),
				"a call after the refresh interval elapses must pick up new content in the directory nearest the target time");
	}

	@Test
	void boundedResyncNeverFullyReWalksAnAlreadyCachedTreeRegardlessOfSize(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		for (int i = 0; i < 20; i++)
			writeBlankImage(new File(cameraRoot, "day" + i + "/2026080" + (i % 9 + 1) + "_120000_1.jpg"));

		File cacheRoot = new File(tempDir, "cache");
		RealImageSource source = realImageSource(cameraRoot);

		// Fully cache the whole 20-directory tree first (simulating a completed "cache-update" run),
		// via an UNLIMITED cache.
		DirectoryCache unlimitedCache = new DirectoryCache(cacheRoot);
		ArchiveFrameScanner.scan(source, unlimitedCache);

		// Now build an ArchiveFrameCache against the SAME cache root, but with a circuit breaker far
		// too low to ever complete a full walk of 20 directories - if the bounded resync ever tried
		// to fully re-walk the tree, this would throw. It must not, since everything is already
		// cached and the resync only ever touches the root + one specific directory.
		DirectoryCache limitedCache = new DirectoryCache(cacheRoot, 2);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, limitedCache, new FakeWallClock(0L));

		ArchiveFrameScanner.ScanResult result = cache.currentScanResult(t0);

		assertFalse(result.isTruncated(), "an already-fully-cached tree must not trip the circuit breaker");
		assertEquals(20, result.getFrames().size());
	}

	// A real user report: an overnight-capture directory already cached from a PRIOR session (the
	// camera browsed earlier the same evening, or on a previous day) that gains new files SINCE
	// then (more frames landing while the app is closed, e.g. this user's own low-light "stack 8
	// frames instead of 1" workflow) went missing entirely after relaunch, even though the frames
	// were really on disk. Root cause: maybeResync(...)'s "which directory to bounded-recheck"
	// decision was computed from cachedFrames - the IN-MEMORY field, which starts null on every
	// freshly-constructed instance (every app launch/camera switch builds a new one) - so the very
	// first resync had nothing to compute "nearest" from, and the root-level check alone doesn't
	// help either (the day directory isn't NEW at the root level, just modified). Reproduced
	// directly here: pre-cache a day directory with 3 frames via a completed scan (simulating an
	// earlier session), add 8 more to that SAME already-cached directory, then build a BRAND NEW
	// ArchiveFrameCache against the same on-disk cache root (simulating the next app launch) and
	// confirm its very FIRST currentScanResult(...) call already sees all 11 frames.
	@Test
	void theFirstResyncOfAFreshInstanceSeesFilesAddedToAnAlreadyCachedDirectorySincePriorSessions(@TempDir File tempDir)
			throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File dayDir = new File(cameraRoot, "2026/08/14");
		writeBlankImage(new File(dayDir, "20260814_083000.jpg"));
		writeBlankImage(new File(dayDir, "20260814_183000.jpg"));
		writeBlankImage(new File(dayDir, "20260814_183100.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		File cacheRoot = new File(tempDir, "cache");

		// Simulates a completed earlier session: a full scan through an unlimited cache, caching the
		// day directory with its 3 pre-night frames.
		DirectoryCache warmupCache = new DirectoryCache(cacheRoot);
		assertEquals(3, ArchiveFrameScanner.scan(source, warmupCache).size());

		// Overnight captures land in the SAME already-cached day directory, while the app is closed.
		for (int i = 1; i <= 8; i++)
			writeBlankImage(new File(dayDir, "20260814_183200_" + i + ".jpg"));

		// The next app launch: a brand new ArchiveFrameCache instance against the SAME on-disk cache
		// root.
		DirectoryCache freshCache = new DirectoryCache(cacheRoot);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, freshCache, new FakeWallClock(0L));

		long target = Instant.parse("2026-08-14T18:32:30Z").toEpochMilli();
		assertEquals(11, cache.currentScanResult(target).getFrames().size(),
				"the very first resync of a fresh instance must see files added to an already-cached "
						+ "directory since a prior session, not just what was cached back then");
	}

	@Test
	void truncatedIsTrueWhenTheBoundedResyncsRootCheckHitsTheLimitOnAFreshArchive(@TempDir File tempDir)
			throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		for (int i = 0; i < 20; i++)
			new File(cameraRoot, "day" + i).mkdirs();

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache limitedCache = new DirectoryCache(new File(tempDir, "cache"), 2);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, limitedCache, new FakeWallClock(0L));

		assertTrue(cache.currentScanResult(t0).isTruncated());
	}

	@Test
	void invalidateForcesAnImmediateResyncRegardlessOfTheInterval(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		writeBlankImage(new File(cameraRoot, "day1/20260810_120000_1.jpg"));
		cache.invalidate();

		assertEquals(2, cache.currentScanResult(t0).getFrames().size(),
				"invalidate() must force a resync even though the interval hasn't elapsed");
	}

	@Test
	void constructorRejectsNonPositiveRefreshInterval(@TempDir File tempDir) throws IOException {
		RealImageSource source = realImageSource(new File(tempDir, "cameras/1"));
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));

		assertThrows(IllegalArgumentException.class,
				() -> new ArchiveFrameCache(source, directoryCache, WallClock.SYSTEM, 0L));
	}

	// Round 3's second fix - direct user suggestion: "when it's 'live' it should not be checking the
	// cache so often but the 'latest path'." A Live+recorded source's "latest" file mtime gates
	// whether a real resync happens at all once the interval elapses - if nothing new was captured,
	// the (comparatively expensive) bounded real checks + local reparse are skipped entirely.

	@Test
	void aLiveAndRecordedSourceSkipsTheResyncWhenTheLatestFileHasNotChanged(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File latestFile = new File(tempDir, "latest.jpg");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));
		writeBlankImage(latestFile);

		RealImageSource source = liveAndRecordedSource(cameraRoot, latestFile);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		// A new frame lands, but "latest.jpg" itself is never touched (unrealistic for a real
		// capture, which always rewrites "latest" too, but exactly what proves the gate is doing
		// its job rather than something else coincidentally finding this frame).
		writeBlankImage(new File(cameraRoot, "day0/20260809_120100_1.jpg"));
		wallClock.advance(5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size(),
				"an unchanged \"latest\" mtime must skip the resync entirely, even past the interval");
	}

	@Test
	void aLiveAndRecordedSourcePerformsTheResyncWhenTheLatestFileMtimeChanges(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File latestFile = new File(tempDir, "latest.jpg");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));
		writeBlankImage(latestFile);
		latestFile.setLastModified(1_000L);

		RealImageSource source = liveAndRecordedSource(cameraRoot, latestFile);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		writeBlankImage(new File(cameraRoot, "day0/20260809_120100_1.jpg"));
		// A deterministic, explicit mtime change - matching an actual capture rewriting "latest.jpg"
		// too, without depending on real filesystem mtime granularity/timing.
		latestFile.setLastModified(2_000L);
		wallClock.advance(5_000L);

		assertEquals(2, cache.currentScanResult(t0).getFrames().size(),
				"a changed \"latest\" mtime must trigger the resync and pick up the new frame");
	}

	@Test
	void aPreRecordedOnlySourceHasNoLivenessGateAndAlwaysResyncsOnTheInterval(@TempDir File tempDir) throws IOException {
		// No "latest" file at all - confirms the existing (pre-round-3) interval-only behavior is
		// unaffected for cameras with no liveness signal to gate on.
		File cameraRoot = new File(tempDir, "cameras/1");
		long t0 = Instant.parse("2026-08-09T12:00:00Z").toEpochMilli();
		writeBlankImage(new File(cameraRoot, "day0/20260809_120000_1.jpg"));

		RealImageSource source = realImageSource(cameraRoot);
		DirectoryCache directoryCache = new DirectoryCache(new File(tempDir, "cache"));
		FakeWallClock wallClock = new FakeWallClock(0L);
		ArchiveFrameCache cache = new ArchiveFrameCache(source, directoryCache, wallClock, 5_000L);

		assertEquals(1, cache.currentScanResult(t0).getFrames().size());

		writeBlankImage(new File(cameraRoot, "day0/20260809_120100_1.jpg"));
		wallClock.advance(5_000L);

		assertEquals(2, cache.currentScanResult(t0).getFrames().size());
	}

	private RealImageSource realImageSource(File cameraRoot) {
		return RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
	}

	private RealImageSource liveAndRecordedSource(File cameraRoot, File latestFile) {
		return RealImageSource.liveAndRecorded(latestFile.getPath(), cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
	}

	private void writeBlankImage(File file) throws IOException {
		file.getParentFile().mkdirs();
		javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB),
				"jpg", file);
	}
}
