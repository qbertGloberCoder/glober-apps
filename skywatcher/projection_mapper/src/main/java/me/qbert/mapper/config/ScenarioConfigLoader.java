package me.qbert.mapper.config;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import me.qbert.mapper.model.AnimationConfiguration;
import me.qbert.mapper.model.ContourLine;
import me.qbert.mapper.model.ContourLineSweepTiming;
import me.qbert.mapper.model.ContourSweepLine;
import me.qbert.mapper.model.PinInformation;
import me.qbert.mapper.model.PinPing;
import me.qbert.mapper.model.SweepConfiguration;
import me.qbert.skywatch.astro.ObserverLocation;

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

// Loads an AnimationConfiguration from an external, hand-editable scenario.properties file plus,
// per wavefront ("sweep"), 3 referenced CSV files: a pin registry (pinid,latitude,longitude), that
// wave's arrival-time events joined to the registry by pinid (pinid,arrival_time, in seconds), and
// its propagation time->distance curve (ideal_time,surface_distance - seconds, kilometers). See
// CLAUDE.md's "External scenario configuration file" section for the full format and a worked
// example. This is the external-file counterpart to Main.makePinSweep(...)'s hardcoded-array
// construction - same AnimationConfiguration/SweepConfiguration/PinInformation/PinPing/
// ContourSweepLine/ContourLine shape, just read from files instead of Java arrays. All relative
// paths (the properties file itself, and the CSV paths inside it) resolve against the current
// working directory, matching how projections/<name>/map.png already resolves.
public final class ScenarioConfigLoader {
	private static final String EVENT_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS";

	private static final Comparator<ContourLineSweepTiming> TIMING_COMPARATOR = new Comparator<ContourLineSweepTiming>() {
		@Override
		public int compare(ContourLineSweepTiming a, ContourLineSweepTiming b) {
			return Double.compare(a.getTime(), b.getTime());
		}
	};

	private ScenarioConfigLoader() {
	}

	public static AnimationConfiguration load(File propertiesFile) throws IOException, ScenarioConfigException {
		if (!propertiesFile.isFile())
			throw new ScenarioConfigException("scenario config file not found: " + propertiesFile);

		Properties properties = new Properties();
		try (FileInputStream in = new FileInputStream(propertiesFile)) {
			properties.load(in);
		}

		AnimationConfiguration configuration = new AnimationConfiguration();

		Calendar eventTime = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		eventTime.setTime(parseEventTime(requireString(properties, "eventTimeUtc", propertiesFile), propertiesFile));
		configuration.setEventTime(eventTime);

		configuration.setEventLocation(requireLocation(properties, "eventLocation", propertiesFile));
		configuration.setObserver(requireLocation(properties, "observer", propertiesFile));
		configuration.setEventSecondsPerFrame(requireLong(properties, "eventSecondsPerFrame", propertiesFile));

		int sweepCount = requireInt(properties, "sweep.count", propertiesFile);
		if (sweepCount < 1)
			throw new ScenarioConfigException("sweep.count must be at least 1 in " + propertiesFile);

		ArrayList<SweepConfiguration> sweeps = new ArrayList<SweepConfiguration>();
		for (int i = 0; i < sweepCount; i++)
			sweeps.add(loadSweep(properties, "sweep." + i, propertiesFile));
		configuration.setSweepConfigurations(sweeps);

		return configuration;
	}

	private static SweepConfiguration loadSweep(Properties properties, String prefix, File propertiesFile)
			throws IOException, ScenarioConfigException {
		File pinsFile = new File(requireString(properties, prefix + ".pins", propertiesFile));
		File pingsFile = new File(requireString(properties, prefix + ".pings", propertiesFile));
		File propagationFile = new File(requireString(properties, prefix + ".propagation", propertiesFile));

		Color color = parseHexColor(requireString(properties, prefix + ".color", propertiesFile), prefix + ".color",
				propertiesFile);
		double thickness = requireDouble(properties, prefix + ".thickness", propertiesFile);
		boolean fixedWidthMode = requireBoolean(properties, prefix + ".fixedWidthMode", propertiesFile);
		double epicenterLatitude = requireDouble(properties, prefix + ".epicenter.latitude", propertiesFile);
		double epicenterLongitude = requireDouble(properties, prefix + ".epicenter.longitude", propertiesFile);
		int outerPinSize = requireInt(properties, prefix + ".outerPinSize", propertiesFile);
		int innerPinSize = requireInt(properties, prefix + ".innerPinSize", propertiesFile);
		int pingOversize = requireInt(properties, prefix + ".pingOversize", propertiesFile);

		Map<String, PinInformation> pinsById = loadPins(pinsFile, color, outerPinSize, innerPinSize);
		List<PinPing> pings = loadPings(pingsFile, pinsById, pingOversize);
		ArrayList<ContourLineSweepTiming> propagation = loadPropagation(propagationFile);
		Collections.sort(propagation, TIMING_COMPARATOR);

		ContourLine contourLine = new ContourLine();
		contourLine.setColor(color);
		contourLine.setThickness(thickness);
		contourLine.setFixedWidthMode(fixedWidthMode);
		contourLine.setLatitude(epicenterLatitude);
		contourLine.setLongitude(epicenterLongitude);

		ContourSweepLine sweepLine = new ContourSweepLine();
		sweepLine.setContourLine(contourLine);
		sweepLine.setContourSweeps(propagation);

		SweepConfiguration sweepConfiguration = new SweepConfiguration();
		sweepConfiguration.setPins(pinsById.values().toArray(new PinInformation[0]));
		sweepConfiguration.setPinPingTimes(pings.toArray(new PinPing[0]));
		sweepConfiguration.setContourSweepLine(sweepLine);
		return sweepConfiguration;
	}

	private static Map<String, PinInformation> loadPins(File file, Color color, int outerPinSize, int innerPinSize)
			throws IOException, ScenarioConfigException {
		List<String[]> rows = readCsvRows(file);
		Map<String, PinInformation> pinsById = new LinkedHashMap<String, PinInformation>();
		for (int i = 1; i < rows.size(); i++) {
			String[] row = rows.get(i);
			if (row.length < 3)
				throw new ScenarioConfigException("expected pinid,latitude,longitude at row " + (i + 1) + " of " + file);
			String pinId = row[0].trim();
			if (pinsById.containsKey(pinId))
				throw new ScenarioConfigException("duplicate pinid \"" + pinId + "\" in " + file);
			PinInformation pin = new PinInformation();
			pin.setColor(color);
			pin.setOuterPinSize(outerPinSize);
			pin.setInnerPinSize(innerPinSize);
			pin.setLatitude(parseDouble(row[1], "latitude", file, i + 1));
			pin.setLongitude(parseDouble(row[2], "longitude", file, i + 1));
			pinsById.put(pinId, pin);
		}
		return pinsById;
	}

	private static List<PinPing> loadPings(File file, Map<String, PinInformation> pinsById, int pingOversize)
			throws IOException, ScenarioConfigException {
		List<String[]> rows = readCsvRows(file);
		List<PinPing> pings = new ArrayList<PinPing>();
		for (int i = 1; i < rows.size(); i++) {
			String[] row = rows.get(i);
			if (row.length < 2)
				throw new ScenarioConfigException("expected pinid,arrival_time at row " + (i + 1) + " of " + file);
			String pinId = row[0].trim();
			PinInformation pin = pinsById.get(pinId);
			if (pin == null)
				throw new ScenarioConfigException(
						"pinid \"" + pinId + "\" at row " + (i + 1) + " of " + file + " has no matching entry in the pin registry");
			PinPing ping = new PinPing();
			ping.setPin(pin);
			ping.setTime(parseDouble(row[1], "arrival_time", file, i + 1));
			ping.setPingOversize(pingOversize);
			pings.add(ping);
		}
		return pings;
	}

	private static ArrayList<ContourLineSweepTiming> loadPropagation(File file) throws IOException, ScenarioConfigException {
		List<String[]> rows = readCsvRows(file);
		ArrayList<ContourLineSweepTiming> timings = new ArrayList<ContourLineSweepTiming>();
		for (int i = 1; i < rows.size(); i++) {
			String[] row = rows.get(i);
			if (row.length < 2)
				throw new ScenarioConfigException("expected ideal_time,surface_distance at row " + (i + 1) + " of " + file);
			ContourLineSweepTiming timing = new ContourLineSweepTiming();
			timing.setTime(parseDouble(row[0], "ideal_time", file, i + 1));
			timing.setDistanceKilometers(parseDouble(row[1], "surface_distance", file, i + 1));
			timings.add(timing);
		}
		return timings;
	}

	private static List<String[]> readCsvRows(File file) throws IOException, ScenarioConfigException {
		if (!file.isFile())
			throw new ScenarioConfigException("CSV file not found: " + file);
		try (CSVReader reader = new CSVReader(new FileReader(file))) {
			List<String[]> rows = reader.readAll();
			if (rows.isEmpty())
				throw new ScenarioConfigException("expected at least a header row in " + file);
			return rows;
		} catch (CsvException e) {
			throw new ScenarioConfigException("malformed CSV: " + file, e);
		}
	}

	private static String requireString(Properties properties, String key, File propertiesFile) throws ScenarioConfigException {
		String value = properties.getProperty(key);
		if (value == null || value.trim().isEmpty())
			throw new ScenarioConfigException("missing required key \"" + key + "\" in " + propertiesFile);
		return value.trim();
	}

	private static double requireDouble(Properties properties, String key, File propertiesFile) throws ScenarioConfigException {
		String value = requireString(properties, key, propertiesFile);
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			throw new ScenarioConfigException("key \"" + key + "\" must be a number in " + propertiesFile + ", got \"" + value + "\"");
		}
	}

	private static long requireLong(Properties properties, String key, File propertiesFile) throws ScenarioConfigException {
		String value = requireString(properties, key, propertiesFile);
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw new ScenarioConfigException("key \"" + key + "\" must be an integer in " + propertiesFile + ", got \"" + value + "\"");
		}
	}

	private static int requireInt(Properties properties, String key, File propertiesFile) throws ScenarioConfigException {
		return (int) requireLong(properties, key, propertiesFile);
	}

	private static boolean requireBoolean(Properties properties, String key, File propertiesFile) throws ScenarioConfigException {
		String value = requireString(properties, key, propertiesFile);
		if ("true".equalsIgnoreCase(value))
			return true;
		if ("false".equalsIgnoreCase(value))
			return false;
		throw new ScenarioConfigException("key \"" + key + "\" must be true or false in " + propertiesFile + ", got \"" + value + "\"");
	}

	private static ObserverLocation requireLocation(Properties properties, String prefix, File propertiesFile)
			throws ScenarioConfigException {
		double latitude = requireDouble(properties, prefix + ".latitude", propertiesFile);
		double longitude = requireDouble(properties, prefix + ".longitude", propertiesFile);
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		return location;
	}

	// Matches camera-viewing's own established --osd-color parseHexColor(...) convention
	// (cli/CameraConfigArgs.java) - strict #RRGGBB, a clear error otherwise.
	private static Color parseHexColor(String value, String key, File propertiesFile) throws ScenarioConfigException {
		String hex = value.startsWith("#") ? value.substring(1) : value;
		if (hex.length() == 6) {
			try {
				return new Color(Integer.parseInt(hex, 16));
			} catch (NumberFormatException e) {
				// falls through to the error below
			}
		}
		throw new ScenarioConfigException(
				"key \"" + key + "\" must be a #RRGGBB hex color in " + propertiesFile + ", got \"" + value + "\"");
	}

	private static double parseDouble(String value, String columnName, File file, int rowNumber) throws ScenarioConfigException {
		try {
			return Double.parseDouble(value.trim());
		} catch (NumberFormatException e) {
			throw new ScenarioConfigException(
					"expected a number for " + columnName + " at row " + rowNumber + " of " + file + ", got \"" + value + "\"");
		}
	}

	private static Date parseEventTime(String value, File propertiesFile) throws ScenarioConfigException {
		SimpleDateFormat format = new SimpleDateFormat(EVENT_TIME_FORMAT);
		format.setTimeZone(TimeZone.getTimeZone("UTC"));
		format.setLenient(false);
		try {
			return format.parse(value);
		} catch (ParseException e) {
			throw new ScenarioConfigException(
					"key \"eventTimeUtc\" must match " + EVENT_TIME_FORMAT + " in " + propertiesFile + ", got \"" + value + "\"", e);
		}
	}
}
