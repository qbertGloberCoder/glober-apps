package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import me.qbert.ui.renderers.AbstractFractionRenderer;
import me.qbert.ui.renderers.ArcRenderer;

// spec §5: planets/stars render as unfilled bounding circles, not filled dots - "in a properly
// plate-solved fixed-camera frame, the real point of light should fall inside the circle, visually
// confirming calibration accuracy." Proves fill=false on the same ArcRenderer/CelestialObjectSizing
// pipeline used for the sun/moon (CelestialObjectSizingRenderingIntegrationTest) actually produces
// an outline, not a solid disc - the center must stay untouched.
class BoundingCircleRenderingTest {

	private static final int CANVAS_SIZE = 200;

	@Test
	void unfilledArcRendererLeavesTheCenterUntouchedButDrawsAnOutline() throws Exception {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.setColor(Color.WHITE);

		double radius = 30.0;

		ArcRenderer boundingCircle = new ArcRenderer(AbstractFractionRenderer.FRACTIONAL_COORDINATES,
				AbstractFractionRenderer.ABSOLUTE_COORDINATES);
		boundingCircle.setFill(false); // the whole point of a bounding circle, per spec §5
		boundingCircle.setStartAngle(0);
		boundingCircle.setArcAngle(360);
		boundingCircle.setX(0.5);
		boundingCircle.setY(0.5);
		boundingCircle.setWidth(radius * 2.0);
		boundingCircle.setHeight(radius * 2.0);
		boundingCircle.setRenderDimensions(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		boundingCircle.renderComponent(g2d);
		g2d.dispose();

		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;

		// The center - where a real point of light would fall if calibration is correct - must
		// remain untouched (still background-colored), unlike a filled dot.
		assertEquals(Color.BLACK.getRGB(), image.getRGB(centerX, centerY),
				"a bounding circle must not fill its center - that's the whole point vs. a filled dot");

		// The outline itself must actually have been drawn, roughly radius pixels out.
		boolean foundOutline = false;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			if ((image.getRGB(x, centerY) & 0x00FFFFFF) == (Color.WHITE.getRGB() & 0x00FFFFFF)) {
				assertTrue(Math.abs((x - centerX) - radius) <= 2,
						"outline pixel at distance " + (x - centerX) + " should be near radius " + radius);
				foundOutline = true;
				break;
			}
		}
		assertTrue(foundOutline, "expected to find the drawn outline along the scanned row");
	}
}
