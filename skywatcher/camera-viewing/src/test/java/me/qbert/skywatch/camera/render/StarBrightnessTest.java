package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

class StarBrightnessTest {

	@Test
	void brightestStarInTheSetRendersFullWhite() {
		Color color = StarBrightness.grayscaleFor(-1.46, -1.46, 6.5);
		assertEquals(new Color(255, 255, 255), color);
	}

	@Test
	void dimmestStarInTheSetRendersAtTheFloorNotPureBlack() {
		Color color = StarBrightness.grayscaleFor(6.5, -1.46, 6.5);
		assertEquals(StarBrightness.DEFAULT_MIN_GRAYSCALE_LEVEL, color.getRed());
		assertTrue(color.getRed() > 0, "the dimmest star must still be visible, not pure black");
	}

	@Test
	void brightnessIsMonotonicAcrossTheMagnitudeRange() {
		double brightest = -1.46;
		double dimmest = 6.5;
		int previousLevel = 256; // above max, so the first comparison always passes

		for (double magnitude = brightest; magnitude <= dimmest; magnitude += 0.25) {
			int level = StarBrightness.grayscaleFor(magnitude, brightest, dimmest).getRed();
			assertTrue(level <= previousLevel, "dimmer stars (higher magnitude) must not render brighter, at mag=" + magnitude);
			previousLevel = level;
		}
	}

	@Test
	void magnitudesOutsideTheSetsRangeAreClamped() {
		Color brighterThanAnything = StarBrightness.grayscaleFor(-5.0, -1.46, 6.5);
		assertEquals(255, brighterThanAnything.getRed());

		Color dimmerThanAnything = StarBrightness.grayscaleFor(20.0, -1.46, 6.5);
		assertEquals(StarBrightness.DEFAULT_MIN_GRAYSCALE_LEVEL, dimmerThanAnything.getRed());
	}

	@Test
	void aDegenerateSingleStarSetRendersFullBrightness() {
		Color color = StarBrightness.grayscaleFor(3.0, 3.0, 3.0);
		assertEquals(new Color(255, 255, 255), color);
	}

	@Test
	void rejectsAnOutOfRangeFloor() {
		assertThrows(IllegalArgumentException.class, () -> StarBrightness.grayscaleFor(1.0, -1.0, 5.0, -1));
		assertThrows(IllegalArgumentException.class, () -> StarBrightness.grayscaleFor(1.0, -1.0, 5.0, 256));
	}
}
