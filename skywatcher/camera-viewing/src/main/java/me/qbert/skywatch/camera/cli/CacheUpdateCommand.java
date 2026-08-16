package me.qbert.skywatch.camera.cli;

import java.io.File;
import java.io.PrintStream;
import java.util.List;

import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.CameraType;
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

// New subcommand, direct user request after pointing the app at an external drive holding several
// years of images and finding the interactive preview "choking quite hard" on the first, cold scan:
// a deliberate, explicit "go synchronize this camera's whole archive now" action, separate from
// ordinary interactive browsing/preview/calibrate (which all use a LIMITED DirectoryCache - see
// CameraConfigArgs.cacheScanLimit(...) - specifically so they never block the UI on a first-time
// scan of a huge archive). This command uses an UNLIMITED cache - the whole point is to finish the
// job, not stop partway - and prints console progress as it goes ("so we know it's not silently
// crashed," the user's own words) rather than running silently for however long a slow/external
// drive takes.
final class CacheUpdateCommand {
	static final String USAGE = "Usage: cache-update --camera <name> [--library-dir <dir>] [--cache-dir <dir>]";

	// visitedEvery controls how often a progress line prints - every directory would be extremely
	// noisy for a real multi-year archive; not configurable, this is purely a console-output cadence
	// choice, not a behavior a caller would ever need to tune.
	private static final int PROGRESS_EVERY_N_DIRECTORIES = 25;

	int run(String[] args) throws Exception {
		return run(args, System.out);
	}

	// Package-visible with an injected PrintStream so the console-output behavior itself is directly
	// testable (capturing stdout would be a much clumsier way to verify this) - matching this
	// module's established testability-seam convention elsewhere (clock.WallClock, etc.).
	int run(String[] args, PrintStream out) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		String cameraName = scanner.requireOption("camera");
		CameraLibrary library = new CameraLibrary(CameraConfigArgs.libraryDirectory(scanner));
		if (!library.contains(cameraName))
			throw new CliUsageException("no camera named \"" + cameraName + "\" in library " + library.getDirectory());

		CameraConfig camera = library.load(cameraName);
		if (camera.getType().getKind() != CameraType.Kind.REAL)
			throw new CliUsageException(
					"cache-update is Real-camera-only - \"" + cameraName + "\" is Virtual, which has no archive to scan");
		if (camera.getRealImageSource() == null)
			throw new CliUsageException("camera \"" + cameraName + "\" has no archive source configured");

		File cacheDir = CameraConfigArgs.cacheDirectory(scanner);
		out.println("Scanning archive for \"" + cameraName + "\" into cache " + cacheDir + " ...");

		long startMillis = System.currentTimeMillis();
		DirectoryCache cache = new DirectoryCache(cacheDir, Integer.MAX_VALUE, (directory, totalSoFar) -> {
			if (totalSoFar % PROGRESS_EVERY_N_DIRECTORIES == 0)
				out.println("  ...scanned " + totalSoFar + " directories so far (" + directory + ")");
		});

		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(camera.getRealImageSource(), cache);

		double elapsedSeconds = (System.currentTimeMillis() - startMillis) / 1000.0;
		out.println(String.format(java.util.Locale.ROOT, "Done. Found %d archived frames in %.1fs.", frames.size(),
				elapsedSeconds));
		return 0;
	}
}
