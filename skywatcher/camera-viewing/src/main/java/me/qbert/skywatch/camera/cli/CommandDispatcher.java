package me.qbert.skywatch.camera.cli;

import java.util.Arrays;

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

// Task 6.5's CLI command shape - "first draft only" per spec §12, which explicitly leaves this
// unspecified pending core schema completion (see docs/tasks.md). Subcommand grammar and dispatch
// style match globe-wrapping-tool's cli.CommandDispatcher (a hardcoded name->subcommand dispatch,
// no registry/annotation mechanism), per this repo's own convention for CLI tools - not invented
// fresh here.
//
// No-arguments is now its OWN meaningful case (direct user instruction), not a usage error: it
// launches full interactive "app mode" (cli.AppCommand) - the primary way this app is meant to be
// used, with the CLI-flags-per-subcommand paths below kept as an explicit "quick custom settings"
// shortcut, not replaced by it.
final class CommandDispatcher {
	private static final String USAGE =
			"Usage:\n"
			+ "  (no arguments)  Full interactive app mode - opens the control panel, no camera\n"
			+ "                  selected yet. [--library-dir <dir>] [camera flags...] (requires a display)\n"
			+ "  save-latest --name <camera> --latest <path> --output <path> [camera flags...]\n"
			+ "  reprocess --name <camera> --archive-template <template> --output-dir <dir> [camera flags...]\n"
			+ "  watch --name <camera> --latest <path> --output <path> [--interval-seconds <n>] [camera flags...]\n"
			+ "  preview --name <camera> --archive-template <template> [--width <px>] [--height <px>]\n"
			+ "          [camera flags...]  (requires a display)\n"
			+ "  calibrate --config <path> | (--name <camera> ... --archive-template <template>)\n"
			+ "            [--calibration-file <path>] [--target-time <ISO-8601 instant>]\n"
			+ "            [camera flags...]  (requires a display)\n"
			+ "  cache-update --camera <name> [--library-dir <dir>] [--cache-dir <dir>]\n"
			+ "            Explicitly synchronizes a library camera's whole archive into the on-disk\n"
			+ "            cache, with console progress - no interactive-mode scan limit. Use this\n"
			+ "            after \"there are too many archived images to synchronize\" from the\n"
			+ "            interactive preview/calibrate/app-mode paths.\n"
			+ "  star-visibility --designation <d> --visible true|false [--star-visibility-file <path>]\n"
			+ "            [--name <n> --magnitude <n> --ra <deg> --dec <deg> --group-level <1-3>]\n"
			+ "            Marks/unmarks a star locally visible (VISIBLE_ONLY tier), or introduces a\n"
			+ "            brand-new star not already in stars.db. CLI-only - no UI star-picker.\n"
			+ "\n"
			+ "Camera flags (shared by every subcommand - see CameraConfigArgs):\n"
			+ "  --camera <name>  Load a camera by name from the library (--library-dir, default\n"
			+ "                   ~/.camera-viewing/cameras) - the primary way to select a camera,\n"
			+ "                   instead of all the flags below.\n"
			+ "  --config <path>  Load a full camera profile saved via config.CameraConfigStore,\n"
			+ "                   instead of all the flags below.\n"
			+ "  --lat <deg> --lon <deg> --alt <deg> --az <deg> [--roll <deg>]\n"
			+ "  --focal-length <mm> [--lens rectilinear|fisheye] [--fisheye-max-angle <deg>]\n"
			+ "  [--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>]\n"
			+ "  --archive-template <template> [--timezone <zoneId>|system]\n"
			+ "  [--stars main|named|all|visible] [--min-radius <px>] [--star-visibility-file <path>]\n"
			+ "\n"
			+ "Interactive commands (preview/calibrate/app mode) share --cache-dir (default\n"
			+ "~/.camera-viewing/cache), --cache-scan-limit (default 10 - the most new archive\n"
			+ "directories a single interactive scan will walk before pausing and warning, rather\n"
			+ "than blocking the UI on a huge first-time scan), and --cache-refresh-interval-seconds\n"
			+ "(default 5 - how often a render re-walks the archive tree at all, rather than reusing\n"
			+ "the last scan; re-walking on every render stalls the UI for a large archive even once\n"
			+ "the on-disk cache is warm).";

	int run(String[] args) throws Exception {
		if (args.length == 0)
			return new AppCommand().run(args);

		String subcommand = args[0];
		String[] rest = Arrays.copyOfRange(args, 1, args.length);

		if ("save-latest".equals(subcommand))
			return new SaveLatestCommand().run(rest);
		if ("reprocess".equals(subcommand))
			return new ReprocessCommand().run(rest);
		if ("watch".equals(subcommand))
			return new WatchCommand().run(rest);
		if ("preview".equals(subcommand))
			return new PreviewCommand().run(rest);
		if ("calibrate".equals(subcommand))
			return new CalibrateCommand().run(rest);
		if ("cache-update".equals(subcommand))
			return new CacheUpdateCommand().run(rest);
		if ("star-visibility".equals(subcommand))
			return new StarVisibilityCommand().run(rest);

		throw new CliUsageException("Unknown subcommand '" + subcommand + "'.\n" + USAGE);
	}
}
