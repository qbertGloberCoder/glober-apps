package me.qbert.skywatch.camera.source;

import java.io.File;

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

// DirectoryCache's circuit breaker (see its own class comment) - thrown mid-walk once a single
// listChildren(...) call has discovered more new subdirectories than its configured limit allows.
// Unchecked: every existing caller of listChildren(...)/ArchiveFrameScanner.scan(...) is unaffected
// unless it opts in to a limited DirectoryCache (the default constructor is unlimited), so forcing
// a checked-exception ripple through every one of those call sites would be pure churn for callers
// that can never actually hit this. Whatever was already cached before the limit was hit stays on
// disk (DirectoryCache.walkAndCache(...) writes each subdirectory's partition as it finishes, before
// this is thrown) - "caches what it found, stops," per the user's own description - so this is a
// resumable pause, not a failure to discard and retry from scratch.
public final class DirectoryScanLimitExceededException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final File directory;
	private final int limit;

	public DirectoryScanLimitExceededException(File directory, int limit) {
		super("too many new archive directories to synchronize (limit: " + limit + ") while scanning " + directory
				+ " - already-scanned progress was kept; run \"cache-update\" to synchronize the rest with visible "
				+ "progress, or raise the interactive scan limit (--cache-scan-limit)");
		this.directory = directory;
		this.limit = limit;
	}

	public File getDirectory() {
		return directory;
	}

	public int getLimit() {
		return limit;
	}
}
