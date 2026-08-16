package me.qbert.skywatch.camera.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

// Backs the "My Location" tab's timezone suggestion (CLAUDE.md's control panel redesign round) -
// given the operator's own lat/lon, suggest a plausible timezone. Bundled as a classpath resource
// (src/main/resources/timezones.txt, 418 rows, "zoneId;lat;lon") exactly the way
// catalog.StarCatalogLoader loads stars.db - same established pattern, not a new one. No global
// state - callers decide when/whether to load, same stance as StarCatalogLoader.
public final class TimezoneCatalog {
	private final List<TimezoneEntry> entries;

	private TimezoneCatalog(List<TimezoneEntry> entries) {
		this.entries = entries;
	}

	public static TimezoneCatalog load(InputStream timezonesTxtStream) throws IOException {
		List<TimezoneEntry> entries = new ArrayList<TimezoneEntry>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(timezonesTxtStream, StandardCharsets.UTF_8));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.trim().isEmpty())
					continue;

				TimezoneEntry entry = parseLine(line);
				if (entry != null)
					entries.add(entry);
			}
		} finally {
			reader.close();
		}

		return new TimezoneCatalog(Collections.unmodifiableList(entries));
	}

	// Convenience for the ordinary case - the bundled catalog on the classpath, no caller-supplied
	// file. CameraConfigArgs.loadStars(...) is the equivalent precedent for stars.db.
	public static TimezoneCatalog loadFromClasspath() throws IOException {
		InputStream stream = TimezoneCatalog.class.getResourceAsStream("/timezones.txt");
		if (stream == null)
			throw new IOException("timezones.txt not found on classpath");
		try {
			return load(stream);
		} finally {
			stream.close();
		}
	}

	public int size() {
		return entries.size();
	}

	// Simple squared-distance nearest-neighbor over the (small, 418-row) catalog - no need for real
	// great-circle math at this scale/precision, matching the plan's own reasoning: this is a
	// suggestion, not a boundary lookup.
	public ZoneId nearestZoneTo(double latitude, double longitude) {
		if (entries.isEmpty())
			throw new IllegalStateException("timezone catalog is empty");

		TimezoneEntry nearest = null;
		double nearestDistanceSquared = Double.POSITIVE_INFINITY;
		for (TimezoneEntry entry : entries) {
			double deltaLatitude = entry.getLatitude() - latitude;
			double deltaLongitude = entry.getLongitude() - longitude;
			double distanceSquared = deltaLatitude * deltaLatitude + deltaLongitude * deltaLongitude;
			if (distanceSquared < nearestDistanceSquared) {
				nearestDistanceSquared = distanceSquared;
				nearest = entry;
			}
		}

		return nearest.getZoneId();
	}

	private static TimezoneEntry parseLine(String line) {
		String[] fields = line.split(";", -1);
		if (fields.length != 3)
			return null;

		try {
			ZoneId zoneId = ZoneId.of(fields[0]);
			double latitude = Double.parseDouble(fields[1]);
			double longitude = Double.parseDouble(fields[2]);
			return new TimezoneEntry(zoneId, latitude, longitude);
		} catch (RuntimeException e) {
			// Malformed row (bad zone id or number) - skip rather than fail the whole catalog load,
			// matching StarCatalogLoader.parseLine(...)'s own stance on a single bad row.
			return null;
		}
	}
}
