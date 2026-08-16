package me.qbert.skywatch.camera.render;

import java.awt.image.BufferedImage;

import me.qbert.skywatch.camera.orientation.BoresightAngles;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

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

// Task 4.7's PTZ-Virtual-camera case: "a live-recomputed view from its 360-degree source" [spec §3's
// Hybrid mode background, CLAUDE.md's "Camera setup" - a VirtualImageSource.EQUIRECTANGULAR_360
// camera can pan/tilt/zoom anywhere on the sphere]. Reuses exactly the same inverse-projection math
// GroundFill already uses for its horizon test - "rendered with the same camera transformation as
// the camera itself" is a general technique, not ground-specific: for every output pixel, walk
// screen position -> theta/phi (CameraProjection's inverse) -> absolute direction
// (BoresightAngles.reconstructAltAz(...)) -> sample the source panorama at that direction's standard
// equirectangular (longitude=azimuth, latitude=altitude) coordinate.
//
// Deliberately no per-frame caching here (CLAUDE.md flags this as a possible future optimization,
// "task 0.5") - GroundFill/CelestialObjectsLayer's own per-pixel loops don't cache either, and this
// is the same O(width*height) cost as those, not a new performance category. Revisit only if a real
// interactive session turns out to need it.
public final class EquirectangularSceneRenderer {
	private EquirectangularSceneRenderer() {
	}

	// Pixels outside the projection's representable angle (CameraProjection.getMaxAngleRadians())
	// are left fully transparent (alpha 0) in the returned image - there is no source data for a
	// direction the lens can't represent at all, matching GroundFill/CelestialObjectsLayer's own
	// "silently skip, don't error" convention for out-of-lens targets.
	public static BufferedImage render(BufferedImage equirectangularSource, CameraProjection projection,
			Orientation cameraOrientation, int canvasWidth, int canvasHeight) {
		if (equirectangularSource == null)
			throw new IllegalArgumentException("equirectangularSource must not be null");
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		if (cameraOrientation == null)
			throw new IllegalArgumentException("cameraOrientation must not be null");
		if (canvasWidth <= 0 || canvasHeight <= 0)
			throw new IllegalArgumentException("canvas dimensions must be positive");

		BufferedImage output = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);

		int sourceWidth = equirectangularSource.getWidth();
		int sourceHeight = equirectangularSource.getHeight();
		double centerX = canvasWidth / 2.0;
		double centerY = canvasHeight / 2.0;
		double pixelsPerMillimeter = CameraProjector.pixelsPerMillimeter(canvasWidth);

		for (int y = 0; y < canvasHeight; y++) {
			for (int x = 0; x < canvasWidth; x++) {
				double dx = x - centerX;
				// Screen Y grows downward; BoresightAngles' "up" is positive in standard math
				// convention - same flip GroundFill applies.
				double dy = centerY - y;

				double radiusPixels = Math.sqrt(dx * dx + dy * dy);
				double radiusMillimeters = radiusPixels / pixelsPerMillimeter;
				double theta = projection.angleForSensorRadiusMillimeters(radiusMillimeters);

				if (theta > projection.getMaxAngleRadians())
					continue; // outside what this lens can represent - leave transparent

				double phi = Math.atan2(dy, dx);
				ObjectDirectionAltAz direction = BoresightAngles.reconstructAltAz(cameraOrientation, theta, phi);

				int[] sourcePixel = equirectangularPixel(direction.getAltitude(), direction.getAzimuth(),
						sourceWidth, sourceHeight);
				output.setRGB(x, y, equirectangularSource.getRGB(sourcePixel[0], sourcePixel[1]));
			}
		}

		return output;
	}

	// The standard equirectangular mapping: azimuth (0-360, matching this module's convention -
	// RotationVector.xyzToAltAz's own normalization) sweeps the full image width; altitude
	// (+90 top, -90 bottom) sweeps the full image height. Package-visible for direct unit testing
	// of the mapping alone, independent of a full projection/orientation setup.
	//
	// The +0.5 offset (wrapped back into [0,1) via floor rather than %, which is correct for
	// negative inputs too) is deliberate, not arbitrary: a camera's own reported orientation is
	// exactly where theta=0 (dead boresight) resolves to, and theta=0 always resolves to
	// azimuth=cameraOrientation.getAzimuth() regardless of phi - so whatever the camera reports as
	// its OWN azimuth must land at the image's own horizontal CENTER, not its left edge. A real user
	// report found the previous un-shifted u=azimuthDegrees/360.0 mapping put the camera's own
	// azimuth=0 at the source image's left edge instead - "the PTZ control says 0 but I seem to be
	// facing south," since whatever direction the source panorama's own true center represented was
	// landing 180 degrees away from where the reported orientation claimed.
	static int[] equirectangularPixel(double altitudeDegrees, double azimuthDegrees, int sourceWidth, int sourceHeight) {
		double uRaw = azimuthDegrees / 360.0 + 0.5;
		double u = uRaw - Math.floor(uRaw);
		double v = (90.0 - altitudeDegrees) / 180.0;

		int sourceX = clamp((int) (u * sourceWidth), 0, sourceWidth - 1);
		int sourceY = clamp((int) (v * sourceHeight), 0, sourceHeight - 1);
		return new int[] { sourceX, sourceY };
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}
}
