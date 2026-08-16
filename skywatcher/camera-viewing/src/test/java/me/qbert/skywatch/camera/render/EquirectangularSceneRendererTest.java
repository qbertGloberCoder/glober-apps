package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;

// Task 4.7's PTZ-Virtual-camera case: rendering a live perspective/fisheye crop out of a full
// 360-degree equirectangular source image.
class EquirectangularSceneRendererTest {

	@Test
	void equirectangularPixelCentersTheSourceImageOnTheCamerasOwnZeroOrientation() {
		int width = 360;
		int height = 180;

		// The user's own exact specification, and a real bug report: azimuth=0/altitude=0 - the
		// camera's own reported "zero" orientation, and exactly where theta=0 (dead boresight)
		// always resolves to - must land on the source image's own center, not its left edge.
		assertArrayEquals(new int[] { width / 2, height / 2 },
				EquirectangularSceneRenderer.equirectangularPixel(0.0, 0.0, width, height));
	}

	@Test
	void equirectangularPixelMapsTheFourCompassDirectionsRelativeToThatCenter() {
		int width = 360;
		int height = 180;

		assertArrayEquals(new int[] { width / 2, 0 },
				EquirectangularSceneRenderer.equirectangularPixel(90.0, 0.0, width, height), "straight up -> top-middle");
		assertArrayEquals(new int[] { width / 2, height - 1 },
				EquirectangularSceneRenderer.equirectangularPixel(-90.0, 0.0, width, height),
				"straight down -> bottom-middle");
		assertArrayEquals(new int[] { 3 * width / 4, height / 2 },
				EquirectangularSceneRenderer.equirectangularPixel(0.0, 90.0, width, height),
				"90 degrees off center -> three-quarters across");
		assertArrayEquals(new int[] { width / 4, height / 2 },
				EquirectangularSceneRenderer.equirectangularPixel(0.0, 270.0, width, height),
				"90 degrees the other way -> one-quarter across");
		assertArrayEquals(new int[] { 0, height / 2 },
				EquirectangularSceneRenderer.equirectangularPixel(0.0, 180.0, width, height),
				"directly opposite center -> the image's own seam/edge");
	}

	@Test
	void rendersTheSourcePixelTheBoresightIsPointingAt() {
		// A 2x1 source: left half blue, right half red - chosen so the camera's own azimuth=0
		// (dead ahead of its zero orientation) samples the SEAM between the two halves (the image's
		// center), not either one outright.
		BufferedImage source = new BufferedImage(360, 1, BufferedImage.TYPE_INT_ARGB);
		for (int x = 0; x < 360; x++)
			source.setRGB(x, 0, (x < 180 ? Color.BLUE : Color.RED).getRGB());

		RectilinearProjection projection = new RectilinearProjection(50.0);

		// Facing azimuth=90 (east of the camera's own zero) samples u=0.75 -> the right half (red).
		BufferedImage east = EquirectangularSceneRenderer.render(source, projection, new Orientation(0.0, 90.0, 0.0), 50, 50);
		assertEquals(Color.RED.getRGB(), east.getRGB(25, 25) | 0xFF000000);

		// Facing azimuth=270 (west of the camera's own zero) samples u=0.25 -> the left half (blue).
		BufferedImage west = EquirectangularSceneRenderer.render(source, projection, new Orientation(0.0, 270.0, 0.0), 50, 50);
		assertEquals(Color.BLUE.getRGB(), west.getRGB(25, 25) | 0xFF000000);
	}

	@Test
	void pixelsOutsideTheLenssMaxAngleAreLeftTransparent() {
		BufferedImage source = new BufferedImage(360, 180, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 180; y++)
			for (int x = 0; x < 360; x++)
				source.setRGB(x, y, Color.GREEN.getRGB());

		// A 180-degree fisheye (maxAngle = PI/2) with a short enough focal length that the image
		// circle fits well within a 100x100 canvas - the corners (outside that circle) fall beyond
		// the lens's representable angle and must stay transparent, while dead center (theta=0) is
		// always within any lens's max angle.
		FisheyeProjection fisheye = new FisheyeProjection(10.0, Math.PI / 2.0);
		BufferedImage rendered = EquirectangularSceneRenderer.render(source, fisheye, new Orientation(90.0, 0.0, 0.0), 100, 100);

		int cornerAlpha = (rendered.getRGB(0, 0) >>> 24) & 0xFF;
		assertEquals(0, cornerAlpha, "a corner pixel beyond the fisheye's max angle should be fully transparent");

		int centerAlpha = (rendered.getRGB(50, 50) >>> 24) & 0xFF;
		assertEquals(255, centerAlpha, "dead center (theta=0) is always within any lens's max angle");
	}
}
