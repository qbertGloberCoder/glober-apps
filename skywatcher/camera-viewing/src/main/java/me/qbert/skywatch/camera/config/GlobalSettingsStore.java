package me.qbert.skywatch.camera.config;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.ColorScheme;

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

// Mirrors config.CameraConfigStore's exact shape (plain java.util.Properties, no JSON/YAML
// dependency - see CLAUDE.md's "Config storage" policy; OrderedProperties for hand-editable key
// order; CameraConfigFormatException naming the offending key) for the ONE global settings file,
// deliberately separate from any per-camera profile - see GlobalSettings' own class comment for why
// "My Location"/worldModel don't belong inside a camera's own .properties file.
public final class GlobalSettingsStore {
	private GlobalSettingsStore() {
	}

	public static void save(GlobalSettings settings, File file) throws IOException {
		OutputStream out = new FileOutputStream(file);
		try {
			save(settings, new OutputStreamWriter(out, StandardCharsets.UTF_8));
		} finally {
			out.close();
		}
	}

	public static void save(GlobalSettings settings, Writer writer) throws IOException {
		if (settings == null)
			throw new IllegalArgumentException("settings must not be null");

		OrderedProperties p = new OrderedProperties();
		p.setProperty("worldModel", settings.getWorldModel().name());
		if (settings.hasMyLocation()) {
			p.setProperty("myLocation.latitude", Double.toString(settings.getMyLatitude()));
			p.setProperty("myLocation.longitude", Double.toString(settings.getMyLongitude()));
		}
		p.setProperty("fontSizePixels", Integer.toString(settings.getFontSizePixels()));
		writeColorScheme(p, settings.getColorScheme());

		p.store(writer, "camera-viewing global settings - see CLAUDE.md's \"Config storage\"");
	}

	// A missing file is NOT an error - a fresh install has no settings file yet, exactly matching
	// GlobalSettings' own no-arg-constructor defaults (worldModel=GLOBE, my location unset).
	public static GlobalSettings loadOrDefault(File file) throws IOException {
		if (!file.isFile())
			return new GlobalSettings();
		InputStream in = new FileInputStream(file);
		try {
			return load(new InputStreamReader(in, StandardCharsets.UTF_8));
		} finally {
			in.close();
		}
	}

	public static GlobalSettings load(Reader reader) throws IOException {
		Properties p = new Properties();
		p.load(reader);

		GlobalSettings settings = new GlobalSettings();
		if (p.getProperty("worldModel") != null)
			settings.setWorldModel(readEnum(p, "worldModel", WorldModel.class));
		if (p.getProperty("myLocation.latitude") != null)
			settings.setMyLocation(readDouble(p, "myLocation.latitude"), readDouble(p, "myLocation.longitude"));
		if (p.getProperty("fontSizePixels") != null)
			settings.setFontSizePixels(readInt(p, "fontSizePixels"));
		if (p.getProperty("colorScheme.sunColor") != null)
			settings.setColorScheme(readColorScheme(p));

		return settings;
	}

	// Item 5 - full ColorScheme persistence, one #RRGGBB hex key per field (matching this module's
	// existing --osd-color CLI convention - see cli.CameraConfigArgs.parseHexColor(...), a separate
	// package so not reused directly, but the exact same tolerant #RRGGBB format). Written/read only
	// as a complete set - sunColor's presence on load is the "was a color scheme ever saved" signal,
	// matching myLocation.latitude's own pair-presence convention above.
	private static void writeColorScheme(OrderedProperties p, ColorScheme scheme) {
		p.setProperty("colorScheme.sunColor", toHex(scheme.getSunColor()));
		p.setProperty("colorScheme.moonColor", toHex(scheme.getMoonColor()));
		p.setProperty("colorScheme.planetColor", toHex(scheme.getPlanetColor()));
		p.setProperty("colorScheme.graticuleColor", toHex(scheme.getGraticuleColor()));
		p.setProperty("colorScheme.celestialOriginColor", toHex(scheme.getCelestialOriginColor()));
		p.setProperty("colorScheme.watchedObjectPathColor", toHex(scheme.getWatchedObjectPathColor()));
		p.setProperty("colorScheme.watchedObjectMarkerColor", toHex(scheme.getWatchedObjectMarkerColor()));
		p.setProperty("colorScheme.labelColor", toHex(scheme.getLabelColor()));
		p.setProperty("colorScheme.observerCardinalCrossColor", toHex(scheme.getObserverCardinalCrossColor()));
		p.setProperty("colorScheme.watchedObjectReferenceLineColor", toHex(scheme.getWatchedObjectReferenceLineColor()));
		p.setProperty("colorScheme.boresightReferenceColor", toHex(scheme.getBoresightReferenceColor()));
	}

	private static ColorScheme readColorScheme(Properties p) throws CameraConfigFormatException {
		ColorScheme defaults = ColorPresets.defaultScheme();
		return new ColorScheme(
				readHexColor(p, "colorScheme.sunColor", defaults.getSunColor()),
				readHexColor(p, "colorScheme.moonColor", defaults.getMoonColor()),
				readHexColor(p, "colorScheme.planetColor", defaults.getPlanetColor()),
				readHexColor(p, "colorScheme.graticuleColor", defaults.getGraticuleColor()),
				readHexColor(p, "colorScheme.celestialOriginColor", defaults.getCelestialOriginColor()),
				readHexColor(p, "colorScheme.watchedObjectPathColor", defaults.getWatchedObjectPathColor()),
				readHexColor(p, "colorScheme.watchedObjectMarkerColor", defaults.getWatchedObjectMarkerColor()),
				readHexColor(p, "colorScheme.labelColor", defaults.getLabelColor()),
				readHexColor(p, "colorScheme.observerCardinalCrossColor", defaults.getObserverCardinalCrossColor()),
				readHexColor(p, "colorScheme.watchedObjectReferenceLineColor",
						defaults.getWatchedObjectReferenceLineColor()),
				readHexColor(p, "colorScheme.boresightReferenceColor", defaults.getBoresightReferenceColor()));
	}

	private static String toHex(Color color) {
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	// Falls back to the given default when the specific key is absent - lets an old settings file
	// hand-edited to drop (or predating) one particular color field still load cleanly, matching this
	// module's established "an old profile with a stray/missing key still loads" tolerance elsewhere
	// (e.g. CameraConfigStore's mountControl.mode).
	private static Color readHexColor(Properties p, String key, Color fallback) throws CameraConfigFormatException {
		String value = p.getProperty(key);
		if (value == null)
			return fallback;
		String hex = value.startsWith("#") ? value.substring(1) : value;
		if (hex.length() == 6) {
			try {
				return new Color(Integer.parseInt(hex, 16));
			} catch (NumberFormatException e) {
				// falls through to the format exception below
			}
		}
		throw new CameraConfigFormatException("invalid #RRGGBB hex color for " + key + ": \"" + value + "\"");
	}

	private static int readInt(Properties p, String key) throws CameraConfigFormatException {
		String value = p.getProperty(key);
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			throw new CameraConfigFormatException("invalid number for " + key + ": \"" + value + "\"", e);
		}
	}

	private static <T extends Enum<T>> T readEnum(Properties p, String key, Class<T> type)
			throws CameraConfigFormatException {
		String value = p.getProperty(key);
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw new CameraConfigFormatException("invalid value for " + key + ": \"" + value + "\"", e);
		}
	}

	private static double readDouble(Properties p, String key) throws CameraConfigFormatException {
		String value = p.getProperty(key);
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new CameraConfigFormatException("invalid number for " + key + ": \"" + value + "\"", e);
		}
	}

	// Same technique as CameraConfigStore's own private OrderedProperties - Properties.store(...)
	// otherwise writes keys in an unspecified hash-table order, working against this file's
	// hand-editable intent.
	private static final class OrderedProperties extends Properties {
		private static final long serialVersionUID = 1L;
		private final LinkedHashSet<Object> keyOrder = new LinkedHashSet<Object>();

		@Override
		public synchronized Object put(Object key, Object value) {
			keyOrder.add(key);
			return super.put(key, value);
		}

		@Override
		public Set<Object> keySet() {
			return keyOrder;
		}

		@Override
		public synchronized Enumeration<Object> keys() {
			return Collections.enumeration(keyOrder);
		}

		@Override
		public Set<Map.Entry<Object, Object>> entrySet() {
			LinkedHashSet<Map.Entry<Object, Object>> ordered = new LinkedHashSet<Map.Entry<Object, Object>>();
			for (Object key : keyOrder)
				ordered.add(new AbstractMap.SimpleEntry<Object, Object>(key, get(key)));
			return ordered;
		}
	}
}
