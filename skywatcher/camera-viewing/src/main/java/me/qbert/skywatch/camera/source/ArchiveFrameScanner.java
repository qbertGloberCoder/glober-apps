package me.qbert.skywatch.camera.source;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

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

// Combines PathTemplate/TimestampPattern (parsing) with DirectoryCache (lazy, cached directory
// listing) into the one thing Phase 6's batch/historical reprocessing (task 6.2) actually needs: a
// timestamp-ordered list of every archived frame belonging to a Real camera's RealImageSource. Real
// cameras only - Virtual cameras have no archive to scan (see CLAUDE.md's "Camera setup").
public final class ArchiveFrameScanner {
	private ArchiveFrameScanner() {
	}

	public static final class Frame {
		private final File file;
		private final long epochMillis;

		Frame(File file, long epochMillis) {
			this.file = file;
			this.epochMillis = epochMillis;
		}

		public File getFile() {
			return file;
		}

		public long getEpochMillis() {
			return epochMillis;
		}
	}

	// A scanTolerant(...) result: whatever frames a (possibly truncated, see DirectoryCache's
	// circuit breaker) walk found, plus whether it was actually cut short.
	public static final class ScanResult {
		private final List<Frame> frames;
		private final boolean truncated;

		ScanResult(List<Frame> frames, boolean truncated) {
			this.frames = frames;
			this.truncated = truncated;
		}

		public List<Frame> getFrames() {
			return frames;
		}

		// True when the underlying DirectoryCache's circuit breaker cut the walk short
		// (DirectoryScanLimitExceededException) - frames still reflects everything found before that
		// happened, not an empty/discarded result.
		public boolean isTruncated() {
			return truncated;
		}
	}

	// Walks source.getArchiveTemplate()'s root directory (PathTemplate.rootDirectory(...)) via the
	// supplied DirectoryCache - so repeated scans (e.g. a scrubber re-listing the same month) reuse
	// its lazy, on-disk cache rather than re-walking the filesystem - and returns every file whose
	// path matches the archive pattern, oldest first. Files that don't match (a different camera's
	// files sharing the same parent tree, non-image files, etc.) are silently skipped, matching
	// TimestampPattern.parseEpochMillis(...)'s existing null-for-no-match contract.
	//
	// Lets DirectoryScanLimitExceededException propagate uncaught - every existing caller (batch
	// commands: reprocess/watch/save-latest/cache-update) always passes an unlimited DirectoryCache,
	// so this can never actually throw for them in practice; scanTolerant(...) below is the
	// interactive counterpart that specifically handles a limited cache gracefully instead.
	public static List<Frame> scan(RealImageSource source, DirectoryCache cache) {
		if (source == null)
			throw new IllegalArgumentException("source must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");

		File root = new File(PathTemplate.rootDirectory(source.getArchiveTemplate()));
		TimestampPattern pattern = source.compileArchivePattern();

		List<Frame> frames = new ArrayList<Frame>();
		walk(root, cache, pattern, frames);

		sortByTime(frames);
		return frames;
	}

	// The interactive counterpart to scan(...) above: when the supplied DirectoryCache is limited
	// (the control panel/preview/calibrate paths, see CLAUDE.md's "Local file cache" circuit-breaker
	// section) and its circuit breaker trips mid-walk, this catches DirectoryScanLimitExceededException
	// rather than letting it abort the whole operation - whatever frames were already found before
	// the limit was hit are still returned (sorted, exactly like scan(...)'s normal result), with
	// isTruncated() flagging that more exist but weren't reached yet. Callers that need to show the
	// user something (a one-time "too many archived images to synchronize" notice, pointing at the
	// "cache-update" command) branch on that flag; callers that don't care can just read getFrames().
	public static ScanResult scanTolerant(RealImageSource source, DirectoryCache cache) {
		if (source == null)
			throw new IllegalArgumentException("source must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");

		File root = new File(PathTemplate.rootDirectory(source.getArchiveTemplate()));
		TimestampPattern pattern = source.compileArchivePattern();

		List<Frame> frames = new ArrayList<Frame>();
		boolean truncated = false;
		try {
			walk(root, cache, pattern, frames);
		} catch (DirectoryScanLimitExceededException e) {
			truncated = true;
		}

		sortByTime(frames);
		return new ScanResult(frames, truncated);
	}

	// The purely-local counterpart to scan(...)/scanTolerant(...) above - built after a second real
	// user report that even a warm on-disk cache still stalled interactive rendering, because
	// scan(...)/scanTolerant(...) always call DirectoryCache.listChildren(...) (a REAL directory.
	// list() against the possibly slow/external archive drive) for EVERY directory in the tree, on
	// every single call. This method never touches the real archive directory at all - it builds the
	// frame list purely from DirectoryCache.readCachedTree(...)'s local, on-disk partition files
	// (see that method's own comment for why that's safe and fast regardless of archive size or
	// drive speed). A directory (or an entire camera) that has never been synchronized at all simply
	// contributes no frames - this is the READ path for ordinary interactive rendering; discovering
	// genuinely new content is a separate, deliberate concern (ArchiveFrameCache's own bounded
	// resync, or the "cache-update" CLI command's full walk), not this method's job.
	public static List<Frame> scanCachedOnly(RealImageSource source, DirectoryCache cache) {
		if (source == null)
			throw new IllegalArgumentException("source must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");

		File root = new File(PathTemplate.rootDirectory(source.getArchiveTemplate()));
		TimestampPattern pattern = source.compileArchivePattern();

		List<Frame> frames = new ArrayList<Frame>();
		cache.readCachedTree(root, file -> {
			Long epochMillis = pattern.parseEpochMillis(file.getPath());
			if (epochMillis != null)
				frames.add(new Frame(file, epochMillis));
		});

		sortByTime(frames);
		return frames;
	}

	private static void sortByTime(List<Frame> frames) {
		Collections.sort(frames, new Comparator<Frame>() {
			@Override
			public int compare(Frame a, Frame b) {
				return Long.compare(a.getEpochMillis(), b.getEpochMillis());
			}
		});
	}

	// Task 0.6/4.7's scrub algorithm: the archived frame whose filename-parsed timestamp is the
	// closest one at or before targetEpochMillis - no interpolation between frames, "next-oldest
	// real image" rather than a fixed granularity (CLAUDE.md's "Camera image display" section).
	// frames must already be sorted oldest-first, matching scan(...)'s own returned order. Returns
	// null if targetEpochMillis predates the archive's very first frame - there's nothing to show
	// for a scrub position before the archive begins.
	public static Frame frameAtOrBefore(List<Frame> frames, long targetEpochMillis) {
		if (frames == null)
			throw new IllegalArgumentException("frames must not be null");

		int lo = 0, hi = frames.size() - 1, result = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (frames.get(mid).getEpochMillis() <= targetEpochMillis) {
				result = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return result < 0 ? null : frames.get(result);
	}

	private static void walk(File directory, DirectoryCache cache, TimestampPattern pattern, List<Frame> frames) {
		Set<String> children = cache.listChildren(directory);

		for (String child : children) {
			File childFile = new File(directory, child);

			if (childFile.isDirectory()) {
				walk(childFile, cache, pattern, frames);
			} else {
				Long epochMillis = pattern.parseEpochMillis(childFile.getPath());
				if (epochMillis != null)
					frames.add(new Frame(childFile, epochMillis));
			}
		}
	}
}
