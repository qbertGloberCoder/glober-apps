package me.qbert.skywatch.camera.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

// Pins the exact per-directory algorithm from this round's design conversation: list()-only
// staleness (no stat()), subdirectory-level add/remove diffing, and full recursive walk-and-cache
// only for genuinely new subdirectories.
class DirectoryCacheTest {

	@Test
	void freshDirectoryReturnsItsActualChildrenRepeatably(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		archive.mkdirs();
		new File(archive, "20260801_000000.jpg").createNewFile();
		new File(archive, "20260801_000100.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);

		Set<String> first = cache.listChildren(archive);
		Set<String> second = cache.listChildren(archive);

		assertEquals(namesOf("20260801_000000.jpg", "20260801_000100.jpg"), first);
		assertEquals(first, second, "an unchanged directory must return the same children on repeat calls");
	}

	@Test
	void detectsANewlyAddedFile(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		archive.mkdirs();
		new File(archive, "20260801_000000.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);

		new File(archive, "20260801_000100.jpg").createNewFile();
		Set<String> afterAdd = cache.listChildren(archive);

		assertEquals(namesOf("20260801_000000.jpg", "20260801_000100.jpg"), afterAdd);
	}

	@Test
	void detectsARemovedFile(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		archive.mkdirs();
		File removedFile = new File(archive, "20260801_000000.jpg");
		removedFile.createNewFile();
		new File(archive, "20260801_000100.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);

		removedFile.delete();
		Set<String> afterRemove = cache.listChildren(archive);

		assertEquals(namesOf("20260801_000100.jpg"), afterRemove);
	}

	@Test
	void newlyDiscoveredSubdirectoryIsEagerlyWalkedAndCached(@TempDir File tempDir) throws IOException {
		// The user's own scenario: scrubbing into a brand-new month causes a brief pause while its
		// day subdirectories get walked, in one pass, not lazily deferred day by day.
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File month = new File(archive, "08");
		File day1 = new File(month, "01");
		File day2 = new File(month, "02");
		day1.mkdirs();
		day2.mkdirs();
		new File(day1, "20260801_000000.jpg").createNewFile();
		new File(day2, "20260802_000000.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);

		assertTrue(cache.hasCachedPartition(month), "the newly-discovered month should be walked and cached");
		assertTrue(cache.hasCachedPartition(day1), "day subdirectories of a new month should be walked too");
		assertTrue(cache.hasCachedPartition(day2));
	}

	@Test
	void removedSubdirectoryDropsItsCachePartitionRecursively(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File month = new File(archive, "08");
		File day = new File(month, "01");
		day.mkdirs();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);
		assertTrue(cache.hasCachedPartition(month));
		assertTrue(cache.hasCachedPartition(day));

		day.delete();
		month.delete();
		cache.listChildren(archive);

		assertFalse(cache.hasCachedPartition(month), "a removed subdirectory's cache partition must be dropped");
		assertFalse(cache.hasCachedPartition(day), "descendants of a removed subdirectory must be dropped too");
	}

	@Test
	void unchangedSubdirectoryIsLeftAloneOnAParentRescan(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File month = new File(archive, "08");
		File day = new File(month, "01");
		day.mkdirs();
		new File(day, "20260801_000000.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);

		// A file appears deep inside an already-cached day - re-scanning the top of the tree must
		// not pick this up, since only the specific stale directory's own diff should react to it.
		new File(day, "20260801_000100.jpg").createNewFile();
		Set<String> dayChildrenViaParentRescan = cache.listChildren(archive);
		assertEquals(namesOf("08"), dayChildrenViaParentRescan);

		Set<String> dayChildrenDirectly = cache.listChildren(day);
		assertEquals(namesOf("20260801_000000.jpg", "20260801_000100.jpg"), dayChildrenDirectly);
	}

	@Test
	void unlimitedCacheWalksAnArbitrarilyDeepNewTreeWithoutThrowing(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		for (int i = 0; i < 20; i++)
			new File(archive, "day" + i).mkdirs();

		DirectoryCache cache = new DirectoryCache(cacheRoot); // default constructor - unlimited
		cache.listChildren(archive); // must not throw
	}

	@Test
	void limitedCacheThrowsOnceTooManyNewDirectoriesAreDiscoveredInOneCall(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		for (int i = 0; i < 20; i++)
			new File(archive, "day" + i).mkdirs();

		DirectoryCache cache = new DirectoryCache(cacheRoot, 10);

		DirectoryScanLimitExceededException thrown = assertThrows(DirectoryScanLimitExceededException.class,
				() -> cache.listChildren(archive));
		assertEquals(10, thrown.getLimit());
	}

	@Test
	void theLimitAppliesToTheTotalNestedCountNotJustTheTopLevelLoop(@TempDir File tempDir) throws IOException {
		// A single newly-discovered top-level entry ("year") containing many nested new
		// subdirectories must still trip the breaker - capping only the top-level loop's iteration
		// count would let exactly this shape slip through uncapped.
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File year = new File(archive, "2026");
		for (int i = 0; i < 20; i++)
			new File(year, "day" + i).mkdirs();

		DirectoryCache cache = new DirectoryCache(cacheRoot, 10);

		assertThrows(DirectoryScanLimitExceededException.class, () -> cache.listChildren(archive));
	}

	@Test
	void partialProgressBeforeTheLimitIsKeptOnDisk(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		List<File> days = new ArrayList<File>();
		for (int i = 0; i < 20; i++) {
			File day = new File(archive, "day" + i);
			day.mkdirs();
			days.add(day);
		}

		DirectoryCache cache = new DirectoryCache(cacheRoot, 10);
		assertThrows(DirectoryScanLimitExceededException.class, () -> cache.listChildren(archive));

		int cachedCount = 0;
		for (File day : days)
			if (cache.hasCachedPartition(day))
				cachedCount++;
		assertEquals(10, cachedCount, "exactly the directories walked before hitting the limit should be cached");
	}

	@Test
	void aRetryAfterTheLimitPicksUpWhereItLeftOff(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		List<File> days = new ArrayList<File>();
		for (int i = 0; i < 15; i++) {
			File day = new File(archive, "day" + i);
			day.mkdirs();
			days.add(day);
		}

		DirectoryCache limited = new DirectoryCache(cacheRoot, 10);
		assertThrows(DirectoryScanLimitExceededException.class, () -> limited.listChildren(archive));

		// A retry with an unlimited cache pointed at the SAME cache root finishes the job - already-
		// cached directories aren't wastefully re-walked from scratch (this is a correctness check on
		// resumability, not a performance timing assertion).
		DirectoryCache unlimited = new DirectoryCache(cacheRoot);
		unlimited.listChildren(archive);

		for (File day : days)
			assertTrue(unlimited.hasCachedPartition(day), "every day directory should be cached after the retry");
	}

	@Test
	void scanListenerFiresOncePerDirectoryVisited(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File day1 = new File(archive, "day1");
		File day2 = new File(archive, "day2");
		day1.mkdirs();
		day2.mkdirs();

		List<File> visited = new ArrayList<File>();
		DirectoryCache cache = new DirectoryCache(cacheRoot, Integer.MAX_VALUE,
				(directory, totalSoFar) -> visited.add(directory));

		cache.listChildren(archive);

		// archive itself, plus its two newly-discovered subdirectories.
		assertEquals(3, visited.size());
		assertTrue(visited.contains(archive));
		assertTrue(visited.contains(day1));
		assertTrue(visited.contains(day2));
	}

	@Test
	void readCachedTreeReconstructsAPreviouslyCachedTreeFromPartitionFilesOnly(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File day1 = new File(archive, "day1");
		File day2 = new File(archive, "day2");
		day1.mkdirs();
		day2.mkdirs();
		new File(day1, "a.jpg").createNewFile();
		new File(day2, "b.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive); // populates the on-disk cache for the whole tree

		List<File> visitedFiles = new ArrayList<File>();
		cache.readCachedTree(archive, visitedFiles::add);

		assertEquals(2, visitedFiles.size());
		assertTrue(visitedFiles.contains(new File(day1, "a.jpg")));
		assertTrue(visitedFiles.contains(new File(day2, "b.jpg")));
	}

	@Test
	void readCachedTreeNeverTouchesTheRealDirectoryOnceCached(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		File day1 = new File(archive, "day1");
		day1.mkdirs();
		new File(day1, "a.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot);
		cache.listChildren(archive);

		// A real filesystem change AFTER caching - readCachedTree(...) must not see it, since it
		// only ever reads the on-disk partition files, never the real directory.
		new File(day1, "b.jpg").createNewFile();

		List<File> visitedFiles = new ArrayList<File>();
		cache.readCachedTree(archive, visitedFiles::add);

		assertEquals(1, visitedFiles.size(), "readCachedTree(...) must reflect only what was cached, not live changes");
	}

	@Test
	void readCachedTreeOnANeverCachedDirectoryVisitsNothing(@TempDir File tempDir) throws IOException {
		File cacheRoot = new File(tempDir, "cache");
		File archive = new File(tempDir, "archive");
		archive.mkdirs();
		new File(archive, "a.jpg").createNewFile();

		DirectoryCache cache = new DirectoryCache(cacheRoot); // never populated via listChildren(...)

		List<File> visitedFiles = new ArrayList<File>();
		cache.readCachedTree(archive, visitedFiles::add);

		assertTrue(visitedFiles.isEmpty());
	}

	private static Set<String> namesOf(String... names) {
		return new HashSet<String>(java.util.Arrays.asList(names));
	}
}
