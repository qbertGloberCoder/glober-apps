package me.qbert.skywatch.camera.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class GlobalSettingsStoreTest {

	@Test
	void roundTripsAWorldModelAndMyLocation() throws IOException {
		GlobalSettings original = new GlobalSettings();
		original.setWorldModel(WorldModel.FLAT);
		original.setMyLocation(45.5, -75.25);

		StringWriter writer = new StringWriter();
		GlobalSettingsStore.save(original, writer);

		GlobalSettings loaded = GlobalSettingsStore.load(new StringReader(writer.toString()));
		assertEquals(WorldModel.FLAT, loaded.getWorldModel());
		assertTrue(loaded.hasMyLocation());
		assertEquals(45.5, loaded.getMyLatitude(), 0.0001);
		assertEquals(-75.25, loaded.getMyLongitude(), 0.0001);
	}

	@Test
	void unconfiguredMyLocationRoundTripsAsStillUnset() throws IOException {
		GlobalSettings original = new GlobalSettings();

		StringWriter writer = new StringWriter();
		GlobalSettingsStore.save(original, writer);

		GlobalSettings loaded = GlobalSettingsStore.load(new StringReader(writer.toString()));
		assertFalse(loaded.hasMyLocation());
		assertEquals(WorldModel.GLOBE, loaded.getWorldModel());
	}

	@Test
	void fileBasedSaveAndLoadRoundTrips(@TempDir File tempDir) throws IOException {
		GlobalSettings original = new GlobalSettings();
		original.setMyLocation(1.0, 2.0);
		File file = new File(tempDir, "settings.properties");

		GlobalSettingsStore.save(original, file);
		GlobalSettings loaded = GlobalSettingsStore.loadOrDefault(file);

		assertEquals(1.0, loaded.getMyLatitude(), 0.0001);
		assertEquals(2.0, loaded.getMyLongitude(), 0.0001);
	}

	// A missing file is a real, expected first-launch state, not an error.
	@Test
	void loadOrDefaultReturnsFreshDefaultsWhenTheFileDoesNotExist(@TempDir File tempDir) throws IOException {
		File missing = new File(tempDir, "does-not-exist.properties");

		GlobalSettings settings = GlobalSettingsStore.loadOrDefault(missing);

		assertFalse(settings.hasMyLocation());
		assertEquals(WorldModel.GLOBE, settings.getWorldModel());
	}

	@Test
	void invalidWorldModelValueProducesAClearFormatException() {
		String contents = "worldModel=NOT_A_REAL_WORLD_MODEL\n";
		assertThrows(CameraConfigFormatException.class, () -> GlobalSettingsStore.load(new StringReader(contents)));
	}

	@Test
	void invalidLatitudeNumberProducesAClearFormatException() {
		String contents = "myLocation.latitude=not-a-number\nmyLocation.longitude=1.0\n";
		assertThrows(CameraConfigFormatException.class, () -> GlobalSettingsStore.load(new StringReader(contents)));
	}

	// Item 5 ("Graticule redesign") - fontSizePixels and the full ColorScheme (including the new
	// reference-line color fields) previously reset to hardcoded defaults on every launch.

	@Test
	void roundTripsFontSizeAndAFullCustomColorScheme() throws IOException {
		GlobalSettings original = new GlobalSettings();
		original.setFontSizePixels(24);
		me.qbert.skywatch.camera.render.ColorScheme customScheme = new me.qbert.skywatch.camera.render.ColorScheme(
				new java.awt.Color(0x010203),
				new java.awt.Color(0x040506),
				new java.awt.Color(0x070809),
				new java.awt.Color(0x0A0B0C),
				new java.awt.Color(0x0D0E0F),
				new java.awt.Color(0x101112),
				new java.awt.Color(0x131415),
				new java.awt.Color(0x161718),
				new java.awt.Color(0x191A1B),
				new java.awt.Color(0x1C1D1E),
				new java.awt.Color(0x1F2021));
		original.setColorScheme(customScheme);

		StringWriter writer = new StringWriter();
		GlobalSettingsStore.save(original, writer);
		GlobalSettings loaded = GlobalSettingsStore.load(new StringReader(writer.toString()));

		assertEquals(24, loaded.getFontSizePixels());
		me.qbert.skywatch.camera.render.ColorScheme loadedScheme = loaded.getColorScheme();
		assertEquals(customScheme.getSunColor(), loadedScheme.getSunColor());
		assertEquals(customScheme.getMoonColor(), loadedScheme.getMoonColor());
		assertEquals(customScheme.getPlanetColor(), loadedScheme.getPlanetColor());
		assertEquals(customScheme.getGraticuleColor(), loadedScheme.getGraticuleColor());
		assertEquals(customScheme.getCelestialOriginColor(), loadedScheme.getCelestialOriginColor());
		assertEquals(customScheme.getWatchedObjectPathColor(), loadedScheme.getWatchedObjectPathColor());
		assertEquals(customScheme.getWatchedObjectMarkerColor(), loadedScheme.getWatchedObjectMarkerColor());
		assertEquals(customScheme.getLabelColor(), loadedScheme.getLabelColor());
		assertEquals(customScheme.getObserverCardinalCrossColor(), loadedScheme.getObserverCardinalCrossColor());
		assertEquals(customScheme.getWatchedObjectReferenceLineColor(), loadedScheme.getWatchedObjectReferenceLineColor());
		assertEquals(customScheme.getBoresightReferenceColor(), loadedScheme.getBoresightReferenceColor());
	}

	@Test
	void unconfiguredFontSizeAndColorSchemeRoundTripAsTheirDefaults() throws IOException {
		GlobalSettings original = new GlobalSettings();

		StringWriter writer = new StringWriter();
		GlobalSettingsStore.save(original, writer);
		GlobalSettings loaded = GlobalSettingsStore.load(new StringReader(writer.toString()));

		assertEquals(16, loaded.getFontSizePixels());
		assertEquals(java.awt.Color.RED, loaded.getColorScheme().getCelestialOriginColor());
	}

	@Test
	void invalidFontSizeNumberProducesAClearFormatException() {
		String contents = "fontSizePixels=not-a-number\n";
		assertThrows(CameraConfigFormatException.class, () -> GlobalSettingsStore.load(new StringReader(contents)));
	}

	@Test
	void invalidHexColorProducesAClearFormatException() {
		String contents = "colorScheme.sunColor=not-a-color\n";
		assertThrows(CameraConfigFormatException.class, () -> GlobalSettingsStore.load(new StringReader(contents)));
	}

	// Confirms the hand-editable format really is hand-editable, matching this module's established
	// discipline (CameraConfigStore's own equivalent test) - a file with only SOME of the 11
	// colorScheme.* keys present must still load cleanly, falling back to the default preset's value
	// for whichever keys are missing.
	@Test
	void aPartialColorSchemeFillsInMissingFieldsFromTheDefaultPreset() throws IOException {
		String contents = "colorScheme.sunColor=#010203\n";

		GlobalSettings loaded = GlobalSettingsStore.load(new StringReader(contents));

		assertEquals(new java.awt.Color(0x010203), loaded.getColorScheme().getSunColor());
		assertEquals(me.qbert.skywatch.camera.render.ColorPresets.defaultScheme().getMoonColor(),
				loaded.getColorScheme().getMoonColor());
	}
}
