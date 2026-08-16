package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;

import org.junit.jupiter.api.Test;

class ViewfinderLayoutTest {

	@Test
	void equalFovsProduceTheFullCanvasAsTheInset() {
		Rectangle inset = ViewfinderLayout.insetBounds(800, 600, 40.0, 40.0);

		assertEquals(new Rectangle(0, 0, 800, 600), inset);
	}

	@Test
	void widerRenderFovShrinksAndCentersTheInset() {
		// Rendering at double the camera's true FOV should halve the inset's linear size.
		Rectangle inset = ViewfinderLayout.insetBounds(800, 600, 40.0, 80.0);

		assertEquals(400, inset.width);
		assertEquals(300, inset.height);
		assertEquals(200, inset.x, "inset should be horizontally centered");
		assertEquals(150, inset.y, "inset should be vertically centered");
	}

	@Test
	void insetShrinksMonotonicallyAsRenderFovWidensFurther() {
		int previousWidth = Integer.MAX_VALUE;

		for (double renderFov = 40.0; renderFov <= 160.0; renderFov += 10.0) {
			Rectangle inset = ViewfinderLayout.insetBounds(1000, 1000, 40.0, renderFov);
			assertTrue(inset.width <= previousWidth, "inset must not grow as render FOV widens further");
			previousWidth = inset.width;
		}
	}

	@Test
	void rejectsARenderFovNarrowerThanTheTrueFov() {
		assertThrows(IllegalArgumentException.class, () -> ViewfinderLayout.insetBounds(800, 600, 40.0, 20.0));
	}

	@Test
	void overlayPixelsPerDegreeUsesTheWiderRenderFovNotTheTrueFov() {
		double atTrueFov = ViewfinderLayout.overlayPixelsPerDegree(800, 40.0);
		double atRenderFov = ViewfinderLayout.overlayPixelsPerDegree(800, 80.0);

		assertEquals(20.0, atTrueFov, 0.0001);
		assertEquals(10.0, atRenderFov, 0.0001);
		assertTrue(atRenderFov < atTrueFov, "a wider render FOV means fewer pixels per degree, by design");
	}
}
