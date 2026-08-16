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

// Which timezone to interpret a Real camera's archive filenames in [CLAUDE.md's "Image sources"].
// Defaults to the app's own local system zone ("my timezone"), not derived from the camera's own
// physical location or its CalibrationEntry history - the user's own reasoning: images are
// timestamped in the zone of whoever captured/labeled them, which by default is assumed to be you,
// even for your own equipment or an internet-scraped remote camera. Overridable per camera for the
// cases where that assumption doesn't hold - a shared timelapse someone else sent you, or EXIF
// capture-date metadata recorded in a different zone. Deliberately NOT versioned in
// CalibrationEntry (unlike orientation/location) - versioning it there would make resolving a
// filename's timestamp circular (you'd need the epoch to pick the entry, but need the entry's zone
// to compute the epoch).
public final class TimezoneSetting {
	private final boolean useSystemDefault;
	private final ZoneId explicitZone;

	private TimezoneSetting(boolean useSystemDefault, ZoneId explicitZone) {
		this.useSystemDefault = useSystemDefault;
		this.explicitZone = explicitZone;
	}

	public static TimezoneSetting useSystemDefault() {
		return new TimezoneSetting(true, null);
	}

	public static TimezoneSetting explicit(ZoneId zone) {
		if (zone == null)
			throw new IllegalArgumentException("zone must not be null");
		return new TimezoneSetting(false, zone);
	}

	public boolean isUseSystemDefault() {
		return useSystemDefault;
	}

	public ZoneId getExplicitZone() {
		if (useSystemDefault)
			throw new IllegalStateException("zone is system-default-derived, not explicit");
		return explicitZone;
	}

	// The zone to actually use for parsing - resolves the system default dynamically rather than
	// capturing it once, matching ObserverLocationSetting.useSystemLocale()'s equivalent "read it
	// from the running machine when needed" behavior.
	public ZoneId resolve() {
		return useSystemDefault ? ZoneId.systemDefault() : explicitZone;
	}
}
