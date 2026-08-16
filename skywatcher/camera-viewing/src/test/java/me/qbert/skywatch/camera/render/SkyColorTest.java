package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;

import org.junit.jupiter.api.Test;

class SkyColorTest {

	@Test
	void daytimeIsSolidBlueAtOrAboveTheHorizon() {
		assertEquals(new Color(87, 141, 203), SkyColor.forSunAltitude(0.0));
		assertEquals(new Color(87, 141, 203), SkyColor.forSunAltitude(45.0));
		assertEquals(new Color(87, 141, 203), SkyColor.forSunAltitude(90.0));
	}

	@Test
	void fullNightIsSolidBlackAtOrBelowNegativeEighteenDegrees() {
		assertEquals(Color.BLACK, SkyColor.forSunAltitude(-18.0));
		assertEquals(Color.BLACK, SkyColor.forSunAltitude(-45.0));
		assertEquals(Color.BLACK, SkyColor.forSunAltitude(-90.0));
	}

	@Test
	void twilightFadesMonotonicallyFromBlueToBlack() {
		Color previous = SkyColor.forSunAltitude(0.0);

		for (double altitude = -0.5; altitude >= -18.0; altitude -= 0.5) {
			Color current = SkyColor.forSunAltitude(altitude);

			assertTrue(current.getRed() <= previous.getRed(), "red must not increase as the sun sets further, at alt=" + altitude);
			assertTrue(current.getGreen() <= previous.getGreen(), "green must not increase, at alt=" + altitude);
			assertTrue(current.getBlue() <= previous.getBlue(), "blue must not increase, at alt=" + altitude);

			previous = current;
		}

		assertEquals(Color.BLACK, previous);
	}

	@Test
	void midTwilightIsRoughlyHalfway() {
		Color midTwilight = SkyColor.forSunAltitude(-9.0);

		assertEquals(44, midTwilight.getRed(), 2);
		assertEquals(71, midTwilight.getGreen(), 2);
		assertEquals(102, midTwilight.getBlue(), 2);
	}
}
