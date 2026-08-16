package me.qbert.skywatch.camera.cli;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.imageio.ImageIO;

import me.qbert.skywatch.camera.batch.BatchReprocessor;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealCaptureMode;
import me.qbert.skywatch.camera.render.FrameCompositor;
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

// Task 6.2's batch/historical reprocessing mode as a CLI invocation: scan a Pre-recorded-only Real
// camera's whole archive and write a composited copy of every matching frame into --output-dir,
// keeping each frame's original filename. Output naming is deliberately this simple (reuse the
// source filename) rather than inventing a new convention - task 6.4's stance on not guessing at
// naming schemes applies here too.
final class ReprocessCommand {
	static final String USAGE = "Usage: reprocess --config <path> | (--name <camera> --lat <deg> "
			+ "--lon <deg> --alt <deg> --az <deg> [--roll <deg>] --focal-length <mm> "
			+ "[--lens rectilinear|fisheye] [--fisheye-max-angle <deg>] "
			+ "[--barrel-a <n>] [--barrel-b <n>] [--barrel-c <n>] [--barrel-d <n>] --archive-template <template> "
			+ "[--timezone <zoneId>|system]) "
			+ "[--stars main|named|all|visible] [--min-radius <px>] " + CameraConfigArgs.RENDER_FLAGS_USAGE
			+ " --output-dir <dir> [--cache-dir <dir>]";

	int run(String[] args) throws Exception {
		ArgScanner scanner = new ArgScanner(args);
		if (!scanner.positionals().isEmpty())
			throw new CliUsageException(USAGE);

		CameraConfig camera = CameraConfigArgs.buildRealCamera(scanner, RealCaptureMode.PRE_RECORDED_ONLY);
		FrameCompositor.Options options = CameraConfigArgs.buildOptions(scanner, WallClock.SYSTEM);

		File outputDir = new File(scanner.requireOption("output-dir"));
		outputDir.mkdirs();
		File cacheDir = new File(scanner.option("cache-dir").orElse(new File(outputDir, ".cache").getPath()));
		DirectoryCache cache = new DirectoryCache(cacheDir);

		List<ArchiveFrameScanner.Frame> frames = ArchiveFrameScanner.scan(camera.getRealImageSource(), cache);
		if (frames.isEmpty()) {
			System.out.println("No archived frames matched the archive template.");
			return 0;
		}

		int count = 0;
		for (ArchiveFrameScanner.Frame frame : frames) {
			BufferedImage composited = BatchReprocessor.reprocessFrame(frame, camera, options);
			File outputFile = new File(outputDir, frame.getFile().getName());
			String format = formatFromFileName(outputFile);
			if (!ImageIO.write(composited, format, outputFile))
				throw new IOException("no image writer available for format \"" + format + "\" (from " + outputFile + ")");
			count++;
		}

		System.out.println("Wrote " + count + " frame(s) to " + outputDir);
		return 0;
	}

	private String formatFromFileName(File file) {
		String name = file.getName();
		int lastDot = name.lastIndexOf('.');
		return lastDot >= 0 ? name.substring(lastDot + 1) : "png";
	}
}
