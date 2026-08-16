package me.qbert.skywatch.camera.config;

import me.qbert.skywatch.camera.orientation.Orientation;

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

// One time-versioned calibration entry [spec §7.2] for a Fixed camera (real or virtual). Zoom is
// tracked here too (spec §7.1); barrel-distortion coefficients are deliberately NOT included yet -
// spec §12 leaves open whether distortion drifts over time the same way pointing does, or stays a
// separate non-versioned field (see docs/tasks.md's open items) - add it here only once that's
// decided, rather than guessing.
//
// Carries latitude/longitude alongside orientation, versioned together under one effectiveFrom -
// the user's own reasoning (someone traveling and recording sunrises/sunsets from different
// locations across sessions should get a location correction versioned together with each
// orientation correction, not through a second desynchronized history). Always explicit - unlike
// ObserverLocationSetting's config-time "use my locale" option, a recorded calibration entry always
// has definite coordinates.
public final class CalibrationEntry {
	private final long effectiveFromEpochMillis;
	private final Orientation orientation;
	private final double zoom;
	private final double latitude;
	private final double longitude;

	public CalibrationEntry(long effectiveFromEpochMillis, Orientation orientation, double zoom,
			double latitude, double longitude) {
		if (orientation == null)
			throw new IllegalArgumentException("orientation must not be null");
		this.effectiveFromEpochMillis = effectiveFromEpochMillis;
		this.orientation = orientation;
		this.zoom = zoom;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public long getEffectiveFromEpochMillis() {
		return effectiveFromEpochMillis;
	}

	public Orientation getOrientation() {
		return orientation;
	}

	public double getZoom() {
		return zoom;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}
}
