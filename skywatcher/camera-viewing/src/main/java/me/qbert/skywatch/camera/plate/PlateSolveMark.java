package me.qbert.skywatch.camera.plate;

import me.qbert.skywatch.camera.watch.WatchedObject;

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

// Task 7.2a's input tuple for technique 2 (semi-automated plate solving [CLAUDE.md's "Plate
// solving is Real archived-camera-only"]): the user picks a real object visible in an archived
// frame (sun/moon/a named catalog star) and clicks its on-screen position. Ported concept from
// org.bluerock.model.PlateSolveSetting - confirmed against the actual old code, not recalled from
// memory - as a (timestamp, objectName, imageX, imageY) tuple; objectName is watch.WatchedObject
// here rather than a raw string, reusing the same "which real object" identity spec §6/§8's
// crosshair/path/OSD detail tier already established, instead of inventing a second one.
//
// Screen position is stored normalized to [0,1] (not raw pixel coordinates) so a mark stays valid
// even if the same frame is later viewed/re-rendered at a different canvas size - resolution-
// independent, matching how the rest of this module (e.g. CameraProjector's 36mm-reference-sensor
// convention) already avoids baking a specific pixel size into stored data.
//
// This class is pure data entry - task 7.2b (the redesigned RandomPlateSolveFitter) is what
// consumes a set of these to search for the camera parameters minimizing predicted-vs-marked
// pixel distance. Nothing here does any fitting.
public final class PlateSolveMark {
	private final long epochMillis;
	private final WatchedObject object;
	private final double normalizedX;
	private final double normalizedY;

	public PlateSolveMark(long epochMillis, WatchedObject object, double normalizedX, double normalizedY) {
		if (object == null)
			throw new IllegalArgumentException("object must not be null");
		requireUnitRange(normalizedX, "normalizedX");
		requireUnitRange(normalizedY, "normalizedY");

		this.epochMillis = epochMillis;
		this.object = object;
		this.normalizedX = normalizedX;
		this.normalizedY = normalizedY;
	}

	// Convenience factory for the actual UI workflow this exists for - a click lands at a raw pixel
	// position on a canvas of a known size; this normalizes it the same way every mark is stored.
	public static PlateSolveMark fromPixelClick(long epochMillis, WatchedObject object, double pixelX,
			double pixelY, int canvasWidthPixels, int canvasHeightPixels) {
		if (canvasWidthPixels <= 0 || canvasHeightPixels <= 0)
			throw new IllegalArgumentException("canvas dimensions must be positive");
		return new PlateSolveMark(epochMillis, object, pixelX / canvasWidthPixels, pixelY / canvasHeightPixels);
	}

	public long getEpochMillis() {
		return epochMillis;
	}

	public WatchedObject getObject() {
		return object;
	}

	public double getNormalizedX() {
		return normalizedX;
	}

	public double getNormalizedY() {
		return normalizedY;
	}

	// The inverse of fromPixelClick(...) - needed by 7.2b's fitness function to compare a mark
	// against a predicted screen position computed for a specific candidate canvas size.
	public double pixelX(int canvasWidthPixels) {
		return normalizedX * canvasWidthPixels;
	}

	public double pixelY(int canvasHeightPixels) {
		return normalizedY * canvasHeightPixels;
	}

	private static void requireUnitRange(double value, String name) {
		if (value < 0.0 || value > 1.0)
			throw new IllegalArgumentException(name + " must be in [0, 1], was " + value);
	}
}
