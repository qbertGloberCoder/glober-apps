package me.qbert.skywatch.camera.source;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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

// spec §10.2 + this round's design conversation: an on-disk, per-directory-partitioned cache of
// real-camera archive directory listings, so scrubbing through months/years of frames doesn't
// re-walk the filesystem tree every time. Grounded precisely in the user's own worked description
// (see CLAUDE.md's "Local file cache" section): each directory is checked with a plain File.list()
// call and compared against its own cache partition - deliberately NOT a file stat()/mtime check,
// since the user's files are write-once by construction ("there is no point looking for file
// changes, that should NOT be the case").
//
// On a mismatch, only the *subdirectory-level diff* is rebuilt: entries for subdirectories that
// disappeared are dropped (recursively, so no orphaned descendant partitions linger); subdirectories
// that are newly discovered are walked and cached in full - a directory that was never seen before
// cannot have any already-cached descendants, so a full recursive walk of a genuinely new subtree is
// exactly the right amount of work, no more or less. This is what explains the user's own described
// performance scenario: scrubbing into a brand-new month causes a brief pause while its day
// subdirectories get walked, then it's back to normal until it crosses into another stretch of
// genuinely new territory.
//
// Never triggered proactively - this class only ever does work in response to an explicit
// listChildren(...) call from whatever is asking for that directory's contents right now (e.g. a
// time scrubber navigating into it). There is no background scan.
//
// Circuit breaker (added after a real user report: pointing this at an external drive holding
// several years of images "choked quite hard" on the first, cold scan): maxNewDirectoriesPerScan
// caps how many NEWLY DISCOVERED subdirectories a single listChildren(...) call will walk/cache
// before giving up, throwing DirectoryScanLimitExceededException - whatever was already written to
// disk before the limit was hit stays cached ("caches what it found, stops"), and the NEXT call to
// listChildren(...) on the same top-level directory picks up wherever the previous one left off
// (its own partition is deliberately left un-written until the FULL walk of its "added" set
// succeeds - see the comment inside listChildren(...) - so a retry correctly re-treats any
// not-yet-walked subdirectories as still "added"). Unlimited (Integer.MAX_VALUE) by default and via
// the single-argument constructor, matching every batch CLI command's existing behavior
// (reprocess/watch/save-latest/cache-update scan everything deliberately, on purpose, and must
// never be silently throttled) - only the interactive preview/calibrate/app-mode paths opt into a
// real limit.
public final class DirectoryCache {

	// Invoked once per directory listChildren(...) actually visits (whether a cheap "unchanged, fast
	// path" match or a fresh walk-and-cache) - purely for progress feedback (the user's own ask: the
	// "cache-update" CLI command needs to print console progress "so we know it's not silently
	// crashed" against a very large, possibly slow external-drive archive). No-op by default.
	public interface ScanListener {
		void onDirectoryVisited(File directory, int totalDirectoriesVisitedSoFar);
	}

	private static final ScanListener NO_OP_LISTENER = (directory, totalDirectoriesVisitedSoFar) -> {
	};

	private final File cacheRoot;
	private final int maxNewDirectoriesPerScan;
	private final ScanListener scanListener;
	private int directoriesVisited;

	public DirectoryCache(File cacheRoot) {
		this(cacheRoot, Integer.MAX_VALUE);
	}

	public DirectoryCache(File cacheRoot, int maxNewDirectoriesPerScan) {
		this(cacheRoot, maxNewDirectoriesPerScan, NO_OP_LISTENER);
	}

	public DirectoryCache(File cacheRoot, int maxNewDirectoriesPerScan, ScanListener scanListener) {
		if (cacheRoot == null)
			throw new IllegalArgumentException("cacheRoot must not be null");
		if (scanListener == null)
			throw new IllegalArgumentException("scanListener must not be null");
		cacheRoot.mkdirs();
		this.cacheRoot = cacheRoot;
		this.maxNewDirectoriesPerScan = maxNewDirectoriesPerScan;
		this.scanListener = scanListener;
	}

	// The current child names (files and subdirectories) directly inside `directory`, refreshing
	// this directory's on-disk cache partition (and any newly-discovered descendants') as needed.
	public Set<String> listChildren(File directory) {
		directoriesVisited++;
		scanListener.onDirectoryVisited(directory, directoriesVisited);

		Set<String> actual = listActual(directory);
		Set<String> cached = readPartition(directory);

		if (cached != null && cached.equals(actual))
			return actual;

		if (cached != null) {
			Set<String> removed = new HashSet<String>(cached);
			removed.removeAll(actual);
			for (String removedChild : removed)
				deletePartitionRecursively(new File(directory, removedChild));
		}

		Set<String> added = new HashSet<String>(actual);
		if (cached != null)
			added.removeAll(cached);

		// This directory's OWN partition is only written after every newly-added subdirectory below
		// has been fully walked (writePartition(directory, actual) at the end of this method) -
		// deliberately, so a DirectoryScanLimitExceededException thrown partway through leaves this
		// directory's cache "not yet known to match," and a later retry re-diffs against the same
		// (stale/missing) partition rather than wrongly concluding there's nothing left to walk.
		//
		// The budget below counts the TOTAL number of newly-discovered directories walked across the
		// WHOLE recursive operation this call triggers, not just this loop's own top-level iteration
		// count - a single top-level "added" entry (e.g. a newly-discovered year directory) can itself
		// contain thousands of nested new subdirectories, so capping only the top-level loop would let
		// exactly the scenario this circuit breaker exists for slip through uncapped.
		int[] newDirectoriesWalkedThisCall = { 0 };
		for (String addedChild : added) {
			File addedPath = new File(directory, addedChild);
			if (addedPath.isDirectory())
				walkAndCache(addedPath, newDirectoriesWalkedThisCall);
		}

		writePartition(directory, actual);
		return actual;
	}

	// Added after a second real user report: even with the on-disk cache fully warm, the interactive
	// render loop was still calling listChildren(...) (a REAL directory.list() call) for EVERY
	// directory in the whole archive on every scan, because ArchiveFrameScanner.walk(...) always
	// recurses through listChildren(...) - the circuit breaker/memoization from the two rounds above
	// only throttled HOW OFTEN that full real walk happened, not the fact that a full real walk
	// happened at all. For a many-thousand-directory archive on a slow/external drive, even one such
	// walk every few seconds is still a continuous stall.
	//
	// This method never calls directory.list() or File.isDirectory() against the real archive
	// directory AT ALL - it reconstructs the previously-cached tree structure purely by reading this
	// class's own on-disk partition files (which live under cacheRoot, on the LOCAL machine,
	// regardless of how slow or remote the real archive directory is - see the class comment above).
	// A child name is treated as a subdirectory if and only if it has its own cached partition file -
	// reliable because partitionFile(...)/writePartition(...) are only ever written for directories
	// (see walkAndCache(...)/listChildren(...) above), never for plain files. A directory with no
	// cached partition at all (never synchronized) simply contributes nothing - callers that need to
	// discover genuinely new content must explicitly resynchronize via listChildren(...) (bounded,
	// e.g. just the root or one specific directory - see ArchiveFrameCache) or the "cache-update" CLI
	// command's full walk, not this method.
	public void readCachedTree(File directory, Consumer<File> fileVisitor) {
		Set<String> cached = readPartition(directory);
		if (cached == null)
			return;

		for (String child : cached) {
			File childPath = new File(directory, child);
			if (hasCachedPartition(childPath))
				readCachedTree(childPath, fileVisitor);
			else
				fileVisitor.accept(childPath);
		}
	}

	private void walkAndCache(File directory, int[] newDirectoriesWalkedThisCall) {
		if (newDirectoriesWalkedThisCall[0] >= maxNewDirectoriesPerScan)
			throw new DirectoryScanLimitExceededException(directory, maxNewDirectoriesPerScan);
		newDirectoriesWalkedThisCall[0]++;

		directoriesVisited++;
		scanListener.onDirectoryVisited(directory, directoriesVisited);

		Set<String> children = listActual(directory);
		writePartition(directory, children);
		for (String child : children) {
			File childPath = new File(directory, child);
			if (childPath.isDirectory())
				walkAndCache(childPath, newDirectoriesWalkedThisCall);
		}
	}

	private void deletePartitionRecursively(File directory) {
		Set<String> cachedChildren = readPartition(directory);
		if (cachedChildren != null)
			for (String child : cachedChildren)
				deletePartitionRecursively(new File(directory, child));

		partitionFile(directory).delete();
	}

	private Set<String> listActual(File directory) {
		String[] names = directory.list();
		Set<String> result = new HashSet<String>();
		if (names != null)
			for (String name : names)
				result.add(name);
		return result;
	}

	private Set<String> readPartition(File directory) {
		File partitionFile = partitionFile(directory);
		if (!partitionFile.isFile())
			return null;

		try {
			List<String> lines = Files.readAllLines(partitionFile.toPath(), StandardCharsets.UTF_8);
			return new HashSet<String>(lines);
		} catch (IOException e) {
			throw new RuntimeException("failed to read cache partition for " + directory, e);
		}
	}

	private void writePartition(File directory, Set<String> children) {
		try {
			Files.write(partitionFile(directory).toPath(), children, StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("failed to write cache partition for " + directory, e);
		}
	}

	private File partitionFile(File directory) {
		return new File(cacheRoot, hash(directory.getAbsolutePath()) + ".cache");
	}

	// Package-visible for tests to confirm the eager-walk-on-discovery behavior without re-invoking
	// listChildren (which would just re-sync against the live filesystem rather than reveal whether
	// a partition was already populated as a side effect of an earlier call).
	boolean hasCachedPartition(File directory) {
		return partitionFile(directory).isFile();
	}

	// Not a staleness check (see class comment) - purely a stable, filesystem-safe way to name each
	// directory's own cache partition file.
	private static String hash(String absolutePath) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] bytes = digest.digest(absolutePath.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(bytes.length * 2);
			for (byte b : bytes) {
				String byteHex = Integer.toHexString(b & 0xFF);
				if (byteHex.length() == 1)
					hex.append('0');
				hex.append(byteHex);
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 must always be available on any JVM", e);
		}
	}
}
