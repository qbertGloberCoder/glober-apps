package me.qbert.skywatch.camera.config;

import java.time.ZoneId;

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

// One row of timezones.txt - a zone's own reference point, used only for TimezoneCatalog's
// nearest-neighbor lookup (see its class comment). Not a general-purpose "where is this timezone"
// API - a large zone (e.g. a whole country) has exactly one representative point here, not a
// boundary.
public final class TimezoneEntry {
	private final ZoneId zoneId;
	private final double latitude;
	private final double longitude;

	public TimezoneEntry(ZoneId zoneId, double latitude, double longitude) {
		if (zoneId == null)
			throw new IllegalArgumentException("zoneId must not be null");
		this.zoneId = zoneId;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public ZoneId getZoneId() {
		return zoneId;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}
}
