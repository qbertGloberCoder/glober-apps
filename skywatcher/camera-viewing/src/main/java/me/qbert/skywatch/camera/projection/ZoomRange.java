package me.qbert.skywatch.camera.projection;

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

// A Real camera's declared lens capability, in 35mm-equivalent focal-length millimeters (the user's
// own examples: "18mm - 70mm, or 35-200") - the range a camera's actual current zoom setting
// (whatever supplies focalLengthMillimeters to a RectilinearProjection/FisheyeProjection instance
// for a given frame) is expected to fall within. A single-focal-length (non-zoom) lens is
// represented with min == max.
public final class ZoomRange {
	private final double minFocalLengthMillimeters;
	private final double maxFocalLengthMillimeters;

	public ZoomRange(double minFocalLengthMillimeters, double maxFocalLengthMillimeters) {
		if (minFocalLengthMillimeters <= 0.0 || maxFocalLengthMillimeters <= 0.0)
			throw new IllegalArgumentException("focal lengths must be positive");
		if (minFocalLengthMillimeters > maxFocalLengthMillimeters)
			throw new IllegalArgumentException("minFocalLengthMillimeters must not exceed maxFocalLengthMillimeters");
		this.minFocalLengthMillimeters = minFocalLengthMillimeters;
		this.maxFocalLengthMillimeters = maxFocalLengthMillimeters;
	}

	public double getMinFocalLengthMillimeters() {
		return minFocalLengthMillimeters;
	}

	public double getMaxFocalLengthMillimeters() {
		return maxFocalLengthMillimeters;
	}

	public boolean contains(double focalLengthMillimeters) {
		return focalLengthMillimeters >= minFocalLengthMillimeters && focalLengthMillimeters <= maxFocalLengthMillimeters;
	}

	public double clamp(double focalLengthMillimeters) {
		return Math.max(minFocalLengthMillimeters, Math.min(maxFocalLengthMillimeters, focalLengthMillimeters));
	}
}
