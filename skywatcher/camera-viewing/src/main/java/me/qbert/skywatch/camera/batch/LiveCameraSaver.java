package me.qbert.skywatch.camera.batch;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.RealImageSource;
import me.qbert.skywatch.camera.render.FrameCompositor;

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

// Phase 6.4 ("save-cameras" [spec §9], the old prototype's own name for this operation): render the
// latest overlaid frame for a Live+recorded camera and write it to an externally accessible
// location - e.g. a fixed, repeatedly-overwritten path a dashboard/webpage can poll. Shares its
// compositing core with 6.2's BatchReprocessor (see BatchReprocessor.compositeOverlay) - the only
// real difference is which image gets loaded and which epoch drives the overlay.
//
// The "latest" source (RealImageSource.getLatestPath()) is, by design, a plain non-templated path
// with no filename-derived timestamp (see CLAUDE.md's "Image sources") - unlike BatchReprocessor's
// archived frames, there is no real capture timestamp to parse. Its mtime is documented as being
// for detecting "a new frame arrived" only (task 6.3's job, not this one's), never for driving the
// overlay itself. The only sensible choice left is wall-clock "now" - the latest capture is recent
// enough (the user's own prototype: roughly one per minute) that a few seconds/up to a capture
// interval of drift is imperceptible for celestial-object positions. Injected via WallClock, the
// same seam clock.SimulatedClock/WallClock already established for testability elsewhere in this
// module, rather than calling System.currentTimeMillis() directly.
//
// Does not decide on an output *naming convention* (e.g. "camera name -> filename") - outputFile is
// caller-supplied, matching CLAUDE.md's stance that this is a policy decision for whatever wires up
// 6.3's daemon loop, not something to guess at here.
public final class LiveCameraSaver {
	private LiveCameraSaver() {
	}

	public static void saveLatest(CameraConfig cameraConfig, FrameCompositor.Options options, WallClock wallClock,
			File outputFile) throws Exception {
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		if (wallClock == null)
			throw new IllegalArgumentException("wallClock must not be null");
		if (outputFile == null)
			throw new IllegalArgumentException("outputFile must not be null");

		RealImageSource source = cameraConfig.getRealImageSource();
		if (source == null || !source.hasLatestSource())
			throw new IllegalStateException(
					"camera " + cameraConfig.getName() + " has no live \"latest\" source configured");

		File latestFile = new File(source.getLatestPath());
		BufferedImage image = ImageIO.read(latestFile);
		if (image == null)
			throw new IOException("could not read image: " + latestFile);

		BufferedImage composited = BatchReprocessor.compositeOverlay(image, wallClock.currentTimeMillis(),
				cameraConfig, options);

		String format = formatFromFileName(outputFile);
		if (!ImageIO.write(composited, format, outputFile))
			throw new IOException("no image writer available for format \"" + format + "\" (from " + outputFile + ")");
	}

	private static String formatFromFileName(File file) {
		String name = file.getName();
		int lastDot = name.lastIndexOf('.');
		if (lastDot < 0 || lastDot == name.length() - 1)
			throw new IllegalArgumentException("outputFile must have a recognizable extension to infer an image format: " + file);
		return name.substring(lastDot + 1);
	}
}
