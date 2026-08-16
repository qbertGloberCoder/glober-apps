package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.awt.Color;

import org.junit.jupiter.api.Test;

class ColorPresetsTest {

	@Test
	void defaultSchemeMatchesSpecSSStatedDefaults() {
		ColorScheme scheme = ColorPresets.defaultScheme();

		assertEquals(Color.YELLOW, scheme.getSunColor());
		assertEquals(new Color(0, 0, 139), scheme.getMoonColor());
		assertEquals(new Color(160, 32, 240), scheme.getPlanetColor());
	}

	@Test
	void eachCallReturnsAnIndependentMutableInstance() {
		ColorScheme first = ColorPresets.defaultScheme();
		ColorScheme second = ColorPresets.defaultScheme();

		assertNotSame(first, second);

		first.setSunColor(Color.BLACK);
		assertEquals(Color.YELLOW, second.getSunColor(), "mutating one preset instance must not affect another");
	}

	@Test
	void allThreePresetsExistAndAreConstructible() {
		ColorScheme defaultScheme = ColorPresets.defaultScheme();
		ColorScheme deuteranopia = ColorPresets.deuteranopiaFriendlyScheme();
		ColorScheme highContrast = ColorPresets.highContrastScheme();

		// Sun/moon should be visually distinguishable from each other within each preset - the
		// whole point of a "confirms calibration accuracy" overlay design.
		assertColorsDiffer(defaultScheme.getSunColor(), defaultScheme.getMoonColor());
		assertColorsDiffer(deuteranopia.getSunColor(), deuteranopia.getMoonColor());
		assertColorsDiffer(highContrast.getSunColor(), highContrast.getMoonColor());
	}

	private void assertColorsDiffer(Color a, Color b) {
		int distance = Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen()) + Math.abs(a.getBlue() - b.getBlue());
		org.junit.jupiter.api.Assertions.assertTrue(distance > 60, "colors " + a + " and " + b + " are too similar");
	}
}
