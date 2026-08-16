package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.AbstractFractionRenderer;

// Proves CelestialObjectSizing's numbers actually drive ga-base's real RendererI/ArcRenderer
// pipeline correctly - not just that the math is right in isolation (see
// CelestialObjectSizingTest). Renders into a real headless BufferedImage and measures the actual
// filled pixel radius back out. Task 4.1 ("adopt ga-base's RendererI family") + 4.2 together.
class CelestialObjectSizingRenderingIntegrationTest {

	private static final int CANVAS_SIZE = 400;

	@Test
	void wideFieldOfViewRendersAtTheMinimumRadius() throws Exception {
		double expectedRadius = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, 120.0, CANVAS_SIZE, 5.0);

		int measuredRadius = renderFilledCircleAndMeasureRadius(expectedRadius);

		// A few pixels' tolerance for AWT's own fillArc rasterization + ArcRenderer's bounding-box
		// rounding - pixel-perfect exactness isn't a requirement anywhere in the spec ("a
		// reasonable minimum on-screen radius" is), only that the floor visibly applies.
		assertEquals(Math.round(expectedRadius), measuredRadius, 3,
				"the min-clamped radius should render as an actual circle of that size, not a sub-pixel dot");
	}

	@Test
	void narrowFieldOfViewRendersAtTheTrueProportionalRadius() throws Exception {
		double expectedRadius = CelestialObjectSizing.radiusPixels(
				CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES, 3.0, CANVAS_SIZE, 5.0);

		int measuredRadius = renderFilledCircleAndMeasureRadius(expectedRadius);

		assertEquals(Math.round(expectedRadius), measuredRadius, 3,
				"a large true-scaled radius should render at its full computed size, not be clamped");
	}

	// Renders a filled circle of the given radius (in pixels) centered on a CANVAS_SIZE square
	// canvas via ga-base's ArcRenderer, then measures the actual rendered radius by scanning
	// outward from the center along one row.
	private int renderFilledCircleAndMeasureRadius(double radiusPixels) throws Exception {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.setColor(Color.YELLOW);

		ArcRenderer sun = new ArcRenderer(AbstractFractionRenderer.FRACTIONAL_COORDINATES,
				AbstractFractionRenderer.ABSOLUTE_COORDINATES);
		sun.setFill(true);
		sun.setStartAngle(0);
		sun.setArcAngle(360);
		sun.setX(0.5);
		sun.setY(0.5);
		sun.setWidth(radiusPixels * 2.0);
		sun.setHeight(radiusPixels * 2.0);
		sun.setRenderDimensions(0, 0, CANVAS_SIZE, CANVAS_SIZE);

		sun.renderComponent(g2d);
		g2d.dispose();

		int centerX = CANVAS_SIZE / 2;
		int centerY = CANVAS_SIZE / 2;
		int measuredRadius = 0;
		for (int x = centerX; x < CANVAS_SIZE; x++) {
			int rgb = image.getRGB(x, centerY) & 0x00FFFFFF;
			if (rgb == (Color.YELLOW.getRGB() & 0x00FFFFFF))
				measuredRadius = x - centerX;
			else
				break;
		}

		return measuredRadius;
	}
}
