package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;

class GroundFillTest {

	private static final int SIZE = 100;

	@Test
	void aLevelCameraPaintsOnlyTheBottomHalf() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		GroundFill.paint(image, projection, camera, GroundFill.DEFAULT_GROUND_COLOR);

		assertGround(image, 50, 80);
		assertSky(image, 50, 20);
	}

	@Test
	void theHorizonLineItselfIsNotPaintedGround() {
		// The exact canvas center row, for a level unrolled camera, is altitude=0 exactly - the
		// horizon belongs to the "sky" side (altitude < 0 is the ground test), not the ground.
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation camera = new Orientation(0.0, 0.0, 0.0);

		assertFalse(GroundFill.isBelowHorizon(projection, camera, 70, SIZE / 2, SIZE, SIZE));
	}

	@Test
	void tiltingTheCameraUpwardRevealsLessGround() {
		// Tilting the camera up (like tilting your head back) moves the horizon line toward the
		// bottom of the frame - less ground is visible, more sky.
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation level = new Orientation(0.0, 0.0, 0.0);
		Orientation tiltedUp = new Orientation(20.0, 0.0, 0.0);

		BufferedImage levelImage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		BufferedImage tiltedImage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		GroundFill.paint(levelImage, projection, level, GroundFill.DEFAULT_GROUND_COLOR);
		GroundFill.paint(tiltedImage, projection, tiltedUp, GroundFill.DEFAULT_GROUND_COLOR);

		assertTrue(countGroundPixels(tiltedImage) < countGroundPixels(levelImage),
				"tilting the camera up should bring less of the frame below the horizon");
	}

	@Test
	void rollingNinetyDegreesRotatesTheHorizonToVertical() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation rolled = new Orientation(0.0, 0.0, 90.0);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		GroundFill.paint(image, projection, rolled, GroundFill.DEFAULT_GROUND_COLOR);

		// What was a top/bottom split (unrolled) is now a left/right split - points chosen off both
		// center axes to avoid the exact-zero-altitude boundary case (a point exactly on either
		// centerline lands precisely on *some* horizon line, unrolled or rolled, and is therefore a
		// floating-point-fragile choice for a "which side" test).
		assertTrue(isGroundPixel(image, 80, 60) != isGroundPixel(image, 20, 60),
				"a 90-degree roll should split the frame left/right instead of top/bottom");

		// By the rectilinear projection's radial symmetry about its own center, rotating the split
		// by 90 degrees around a square canvas's center should still leave roughly half the frame
		// as ground - a robust way to confirm the split rotated rather than pinning exact pixels.
		double groundFraction = countGroundPixels(image) / (double) (SIZE * SIZE);
		assertEquals(0.5, groundFraction, 0.05);
	}

	@Test
	void cameraPointingStraightUpPaintsNoGroundAtAll() {
		RectilinearProjection projection = new RectilinearProjection(50.0);
		Orientation straightUp = new Orientation(90.0, 0.0, 0.0);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		GroundFill.paint(image, projection, straightUp, GroundFill.DEFAULT_GROUND_COLOR);

		assertEquals(0, countGroundPixels(image));
	}

	@Test
	void cameraPointingStraightDownPaintsGroundEverywhereWithinLensRange() {
		// A 360-degree fisheye with enough focal length that even the canvas corners (the largest
		// theta on-canvas) stay within the lens's PI max angle - see the hand-computed margin: at
		// SIZE=100 the corner radius is ~25.45mm, so focalLength must exceed ~8.1mm.
		FisheyeProjection projection = new FisheyeProjection(20.0, Math.PI);
		Orientation straightDown = new Orientation(-90.0, 0.0, 0.0);
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);

		GroundFill.paint(image, projection, straightDown, GroundFill.DEFAULT_GROUND_COLOR);

		assertEquals(SIZE * SIZE, countGroundPixels(image));
	}

	private void assertGround(BufferedImage image, int x, int y) {
		assertTrue(isGroundPixel(image, x, y), "expected (" + x + "," + y + ") to be ground-colored");
	}

	private void assertSky(BufferedImage image, int x, int y) {
		assertFalse(isGroundPixel(image, x, y), "expected (" + x + "," + y + ") to be untouched (not ground)");
	}

	private boolean isGroundPixel(BufferedImage image, int x, int y) {
		return image.getRGB(x, y) == GroundFill.DEFAULT_GROUND_COLOR.getRGB();
	}

	private int countGroundPixels(BufferedImage image) {
		int count = 0;
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if (isGroundPixel(image, x, y))
					count++;
		return count;
	}
}
