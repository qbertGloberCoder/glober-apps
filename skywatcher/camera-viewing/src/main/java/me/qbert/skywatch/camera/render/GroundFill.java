package me.qbert.skywatch.camera.render;

import java.awt.Color;
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

// The ground sub-layer (3B or 3C) - a flat, horizon-based fill ("light brown... like an airplane
// [artificial-horizon] gyroscope instrument", CLAUDE.md's Layer model table). Direct user
// instruction: this is NOT a separately-computed artificial-horizon instrument - it's rendered
// "with the same camera transformation as the camera itself", so every pixel is tested against the
// camera's own CameraProjection + Orientation, the exact same pair CameraProjector uses for
// everything else. 3A (SkyColor) is already a uniform full-canvas flood fill (CLAUDE.md: "simpler
// than computing a horizon-aware fill"); this class is what "the ground sub-layer painting over the
// below-horizon portion handles the rest" refers to - only touches pixels below the horizon
// (altitude < 0), leaving everything else untouched so the sky/objects layers underneath show
// through.
public final class GroundFill {
	private GroundFill() {
	}

	// "Light brown", matching the artificial-horizon-instrument look CLAUDE.md describes.
	public static final Color DEFAULT_GROUND_COLOR = new Color(139, 90, 43);

	public static void paint(BufferedImage image, CameraProjection projection, Orientation cameraOrientation,
			Color groundColor) {
		if (image == null)
			throw new IllegalArgumentException("image must not be null");
		if (groundColor == null)
			throw new IllegalArgumentException("groundColor must not be null");

		int width = image.getWidth();
		int height = image.getHeight();
		int rgb = groundColor.getRGB();

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				if (isBelowHorizon(projection, cameraOrientation, x, y, width, height))
					image.setRGB(x, y, rgb);
			}
		}
	}

	// Exposed independently of paint(...) - reusable wherever "is this screen pixel above or below
	// the horizon" is needed (e.g. a future UI affordance), not just by this class's own loop.
	public static boolean isBelowHorizon(CameraProjection projection, Orientation cameraOrientation, int pixelX,
			int pixelY, int canvasWidthPixels, int canvasHeightPixels) {
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		if (cameraOrientation == null)
			throw new IllegalArgumentException("cameraOrientation must not be null");

		double centerX = canvasWidthPixels / 2.0;
		double centerY = canvasHeightPixels / 2.0;
		double dx = pixelX - centerX;
		// Screen Y grows downward; BoresightAngles' "up" is positive in standard math convention.
		double dy = centerY - pixelY;

		double radiusPixels = Math.sqrt(dx * dx + dy * dy);
		double radiusMillimeters = radiusPixels / CameraProjector.pixelsPerMillimeter(canvasWidthPixels);
		double theta = projection.angleForSensorRadiusMillimeters(radiusMillimeters);

		if (theta > projection.getMaxAngleRadians())
			return false; // outside what this lens can represent at all - nothing to paint here

		double phi = Math.atan2(dy, dx);
		ObjectDirectionAltAz altAz = BoresightAngles.reconstructAltAz(cameraOrientation, theta, phi);

		return altAz.getAltitude() < 0.0;
	}
}
