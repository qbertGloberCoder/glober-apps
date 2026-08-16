package me.qbert.skywatch.camera.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.config.TimezoneSetting;

// Exercises the whole chain end to end against a real small directory tree: PathTemplate.
// rootDirectory(...) -> DirectoryCache -> TimestampPattern parsing, matching the user's own
// worked archive layout.
class ArchiveFrameScannerTest {

	@Test
	void scansAndSortsFramesAcrossMultipleDayDirectories(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File day1 = new File(cameraRoot, "2026/08/01");
		File day2 = new File(cameraRoot, "2026/08/02");
		day1.mkdirs();
		day2.mkdirs();

		// Deliberately created out of chronological order, to confirm the scanner sorts rather than
		// just preserving directory-listing order.
		new File(day2, "20260802_060000_1.jpg").createNewFile();
		new File(day1, "20260801_235900_1.jpg").createNewFile();
		new File(day1, "20260801_000000_1.jpg").createNewFile();
		// A file that doesn't match the pattern at all - must be silently skipped.
		new File(day1, "readme.txt").createNewFile();

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(source, cache);

		assertEquals(3, frames.size());
		assertEquals(expectedEpoch(2026, 8, 1, 0, 0, 0), frames.get(0).getEpochMillis());
		assertEquals(expectedEpoch(2026, 8, 1, 23, 59, 0), frames.get(1).getEpochMillis());
		assertEquals(expectedEpoch(2026, 8, 2, 6, 0, 0), frames.get(2).getEpochMillis());
	}

	@Test
	void scanCachedOnlyReturnsPreviouslyCachedFramesWithoutTouchingTheRealDirectory(@TempDir File tempDir)
			throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File day1 = new File(cameraRoot, "2026/08/01");
		day1.mkdirs();
		new File(day1, "20260801_000000_1.jpg").createNewFile();

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));
		ArchiveFrameScanner.scan(source, cache); // populates the on-disk cache

		// A real change AFTER caching - scanCachedOnly(...) must not see it (it never touches the
		// real archive directory at all, only the on-disk cache partitions).
		new File(day1, "20260801_000100_1.jpg").createNewFile();

		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scanCachedOnly(source, cache);

		assertEquals(1, frames.size());
	}

	@Test
	void scanCachedOnlyOnAnUncachedArchiveReturnsAnEmptyList(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File day1 = new File(cameraRoot, "2026/08/01");
		day1.mkdirs();
		new File(day1, "20260801_000000_1.jpg").createNewFile();

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache")); // never synchronized

		assertTrue(ArchiveFrameScanner.scanCachedOnly(source, cache).isEmpty());
	}

	@Test
	void scanTolerantReturnsWhateverWasFoundBeforeAnUnlimitedCacheNeverThrows(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		File day1 = new File(cameraRoot, "2026/08/01");
		day1.mkdirs();
		new File(day1, "20260801_000000_1.jpg").createNewFile();

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		ArchiveFrameScanner.ScanResult result = ArchiveFrameScanner.scanTolerant(source, cache);

		assertEquals(1, result.getFrames().size());
		assertTrue(!result.isTruncated());
	}

	@Test
	void scanTolerantFlagsTruncationAndKeepsWhateverWasFoundBeforeTheLimit(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		for (int i = 0; i < 20; i++) {
			File day = new File(cameraRoot, "2026/08/" + (10 + i));
			day.mkdirs();
			new File(day, "2026081" + (i % 10) + "_000000_1.jpg").createNewFile();
		}

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"), 5);

		ArchiveFrameScanner.ScanResult result = ArchiveFrameScanner.scanTolerant(source, cache);

		assertTrue(result.isTruncated());
		assertTrue(result.getFrames().size() < 20, "a truncated scan must not have found every frame");
	}

	@Test
	void scanLetsTheLimitExceptionPropagateUnlikeScanTolerant(@TempDir File tempDir) throws IOException {
		File cameraRoot = new File(tempDir, "cameras/1");
		for (int i = 0; i < 20; i++)
			new File(cameraRoot, "2026/08/" + (10 + i)).mkdirs();

		RealImageSource source = RealImageSource.preRecordedOnly(cameraRoot.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"), 5);

		org.junit.jupiter.api.Assertions.assertThrows(DirectoryScanLimitExceededException.class,
				() -> ArchiveFrameScanner.scan(source, cache));
	}

	@Test
	void ignoresFilesBelongingToADifferentCamera(@TempDir File tempDir) throws IOException {
		File camerasRoot = new File(tempDir, "cameras");
		File camera1 = new File(camerasRoot, "1");
		File camera2 = new File(camerasRoot, "2");
		camera1.mkdirs();
		camera2.mkdirs();
		new File(camera1, "20260801_000000_1.jpg").createNewFile();
		new File(camera2, "20260801_000000_1.jpg").createNewFile();

		RealImageSource source = RealImageSource.preRecordedOnly(camera1.getPath() + "/**/YYYYmmdd_HHMMSS*.jpg",
				TimezoneSetting.explicit(ZoneOffset.UTC), DstAmbiguousPolicy.ASSUME_STANDARD);
		DirectoryCache cache = new DirectoryCache(new File(tempDir, "cache"));

		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(source, cache);

		assertEquals(1, frames.size());
		assertTrue(frames.get(0).getFile().getPath().contains(camera1.getName() + File.separator)
				|| frames.get(0).getFile().getPath().startsWith(camera1.getPath()));
	}

	@Test
	void frameAtOrBeforeFindsTheClosestOlderFrame() {
		List<ArchiveFrameScanner.Frame> frames = frames(100L, 200L, 300L);

		assertEquals(200L, ArchiveFrameScanner.frameAtOrBefore(frames, 250L).getEpochMillis(),
				"should snap to the closest OLDER frame, not the next one");
	}

	@Test
	void frameAtOrBeforeIsInclusiveOfAnExactMatch() {
		List<ArchiveFrameScanner.Frame> frames = frames(100L, 200L, 300L);

		assertEquals(200L, ArchiveFrameScanner.frameAtOrBefore(frames, 200L).getEpochMillis());
	}

	@Test
	void frameAtOrBeforeReturnsNullWhenTargetPredatesTheWholeArchive() {
		List<ArchiveFrameScanner.Frame> frames = frames(100L, 200L, 300L);

		assertNull(ArchiveFrameScanner.frameAtOrBefore(frames, 50L));
	}

	@Test
	void frameAtOrBeforeReturnsTheLastFrameWhenTargetIsAfterEverything() {
		List<ArchiveFrameScanner.Frame> frames = frames(100L, 200L, 300L);

		assertEquals(300L, ArchiveFrameScanner.frameAtOrBefore(frames, 999L).getEpochMillis());
	}

	@Test
	void frameAtOrBeforeReturnsNullForAnEmptyList() {
		assertNull(ArchiveFrameScanner.frameAtOrBefore(Collections.<ArchiveFrameScanner.Frame>emptyList(), 100L));
	}

	private List<ArchiveFrameScanner.Frame> frames(long... epochMillis) {
		List<ArchiveFrameScanner.Frame> frames = new ArrayList<ArchiveFrameScanner.Frame>();
		for (long epoch : epochMillis)
			frames.add(new ArchiveFrameScanner.Frame(new File("frame-" + epoch + ".jpg"), epoch));
		return frames;
	}

	private long expectedEpoch(int year, int month, int day, int hour, int minute, int second) {
		return OffsetDateTime.of(year, month, day, hour, minute, second, 0, ZoneOffset.UTC).toInstant().toEpochMilli();
	}
}
