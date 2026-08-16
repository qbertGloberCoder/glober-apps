package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class Layer1DuskFadeTest {

	@Test
	void daytimeIsFullBrightness() {
		assertEquals(1.0, Layer1DuskFade.brightnessFraction(0.0), 1e-9);
		assertEquals(1.0, Layer1DuskFade.brightnessFraction(45.0), 1e-9);
		assertEquals(1.0, Layer1DuskFade.brightnessFraction(90.0), 1e-9);
	}

	@Test
	void belowNegativeEighteenDegreesIsFullyBlack() {
		assertEquals(0.0, Layer1DuskFade.brightnessFraction(-18.0), 1e-9);
		assertEquals(0.0, Layer1DuskFade.brightnessFraction(-45.0), 1e-9);
		assertEquals(0.0, Layer1DuskFade.brightnessFraction(-90.0), 1e-9);
	}

	// The approved, non-linear "banded" curve (confirmed with the user via a concrete numeric
	// preview) - mostly bright through civil twilight, most of the darkening happens in nautical
	// twilight, virtually black entering astronomical twilight. Deliberately NOT the same single
	// linear ramp SkyColor uses for the sky fill.
	@Test
	void matchesTheApprovedBandedCurveAtEachBreakpoint() {
		assertEquals(1.00, Layer1DuskFade.brightnessFraction(0.0), 1e-9);
		assertEquals(0.925, Layer1DuskFade.brightnessFraction(-3.0), 1e-9, "midway through civil twilight");
		assertEquals(0.85, Layer1DuskFade.brightnessFraction(-6.0), 1e-9, "end of civil twilight");
		assertEquals(0.45, Layer1DuskFade.brightnessFraction(-9.0), 1e-9, "midway through nautical twilight");
		assertEquals(0.05, Layer1DuskFade.brightnessFraction(-12.0), 1e-9, "end of nautical twilight");
		assertEquals(0.025, Layer1DuskFade.brightnessFraction(-15.0), 1e-9, "midway through astronomical twilight");
		assertEquals(0.00, Layer1DuskFade.brightnessFraction(-18.0), 1e-9, "end of astronomical twilight");
	}

	@Test
	void mostOfTheDarkeningHappensDuringNauticalTwilightNotEvenlyAcrossTheWholeSpan() {
		double civilDrop = Layer1DuskFade.brightnessFraction(0.0) - Layer1DuskFade.brightnessFraction(-6.0);
		double nauticalDrop = Layer1DuskFade.brightnessFraction(-6.0) - Layer1DuskFade.brightnessFraction(-12.0);
		double astronomicalDrop = Layer1DuskFade.brightnessFraction(-12.0) - Layer1DuskFade.brightnessFraction(-18.0);

		org.junit.jupiter.api.Assertions.assertTrue(nauticalDrop > civilDrop,
				"nautical twilight must darken more than civil twilight");
		org.junit.jupiter.api.Assertions.assertTrue(nauticalDrop > astronomicalDrop,
				"nautical twilight must darken more than astronomical twilight");
	}

	@Test
	void darkenIsANoOpAtFullBrightness() {
		BufferedImage image = solidImage(0xFF, 10, 20, 30);

		Layer1DuskFade.darken(image, 45.0);

		assertPixel(image, 0xFF, 10, 20, 30);
	}

	@Test
	void darkenScalesRgbButLeavesAlphaUntouched() {
		// Semi-transparent on purpose - the old prototype's own technique preserves alpha exactly,
		// only R/G/B are scaled.
		BufferedImage image = solidImage(0x80, 200, 100, 50);

		Layer1DuskFade.darken(image, -12.0); // brightness 0.05 at the end of nautical twilight

		double brightness = Layer1DuskFade.brightnessFraction(-12.0);
		assertPixel(image, 0x80, (int) Math.round(200 * brightness), (int) Math.round(100 * brightness),
				(int) Math.round(50 * brightness));
	}

	@Test
	void darkenLeavesFullyTransparentPixelsUntouched() {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, 0x00123456); // alpha=0, arbitrary RGB underneath

		Layer1DuskFade.darken(image, -18.0); // fully black, if it were touched

		assertEquals(0x00123456, image.getRGB(0, 0), "a fully-transparent pixel must never be turned opaque black");
	}

	@Test
	void darkenAtFullNightProducesOpaqueBlack() {
		BufferedImage image = solidImage(0xFF, 200, 150, 100);

		Layer1DuskFade.darken(image, -18.0);

		assertPixel(image, 0xFF, 0, 0, 0);
	}

	private BufferedImage solidImage(int alpha, int r, int g, int b) {
		BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, (alpha << 24) | (r << 16) | (g << 8) | b);
		return image;
	}

	private void assertPixel(BufferedImage image, int expectedAlpha, int expectedR, int expectedG, int expectedB) {
		int argb = image.getRGB(0, 0);
		assertEquals(expectedAlpha, (argb >>> 24) & 0xFF, "alpha");
		assertEquals(expectedR, (argb >> 16) & 0xFF, "red");
		assertEquals(expectedG, (argb >> 8) & 0xFF, "green");
		assertEquals(expectedB, argb & 0xFF, "blue");
	}
}
