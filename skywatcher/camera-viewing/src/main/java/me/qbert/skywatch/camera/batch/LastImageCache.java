package me.qbert.skywatch.camera.batch;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.Callable;

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

// A tiny single-slot memoization cache - a direct user report: time-scrubbing needlessly rebuilds
// Layer 1 (re-decoding an archived photo from disk, or re-running EquirectangularSceneRenderer's
// per-pixel loop) on every render tick, even when nothing that actually determines Layer 1's content
// has changed since the previous call. The user's own suggested fix: "keeping a static in memory
// copy of the latest layer 1 image and just repaint that one (or don't rebuild it) for time
// scrubbing."
//
// Callers build the key from plain primitives/Strings/File/object-references that have well-defined
// equals() - never from domain value objects like orientation.Orientation or a CameraProjection
// directly, since neither overrides equals() (confirmed by reading Orientation.java), which would
// silently defeat the cache via identity comparison on every distinct-looking-but-equal instance.
// See batch.CameraImageCaches for the three concrete keys actually used.
//
// Only successful computations are cached - an exception from supplier.call() propagates normally,
// so existing graceful-degradation handling (e.g. a frame that fails to ImageIO.read degrades to
// "no image" rather than crashing) is unaffected.
final class LastImageCache {
	private Object[] lastKey;
	private BufferedImage lastImage;

	BufferedImage getOrCompute(Object[] key, Callable<BufferedImage> supplier) throws Exception {
		if (lastImage != null && Arrays.equals(key, lastKey))
			return lastImage;

		BufferedImage image = supplier.call();
		lastKey = key;
		lastImage = image;
		return image;
	}
}
