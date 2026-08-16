package me.qbert.skywatch.camera.render;

import java.awt.Color;

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

// spec §5: stars are rendered "grayscale by apparent magnitude" (bounding circles, not filled
// dots - see CLAUDE.md's Reused core table on why filled dots were rejected). Apparent magnitude
// is an inverted scale - brighter stars have LOWER (even negative) numbers - so this maps the
// brightest magnitude in whatever set is currently selected (catalog.StarCatalogTier) to full
// white and the dimmest to a configurable floor, not to pure black, since a fully black outline on
// a dark sky would be invisible - defeating the "confirms calibration accuracy" purpose spec §5
// gives bounding circles in the first place.
public final class StarBrightness {
	private StarBrightness() {
	}

	public static final int DEFAULT_MIN_GRAYSCALE_LEVEL = 40;

	public static Color grayscaleFor(double apparentMagnitude, double brightestMagnitudeInSet,
			double dimmestMagnitudeInSet) {
		return grayscaleFor(apparentMagnitude, brightestMagnitudeInSet, dimmestMagnitudeInSet, DEFAULT_MIN_GRAYSCALE_LEVEL);
	}

	public static Color grayscaleFor(double apparentMagnitude, double brightestMagnitudeInSet,
			double dimmestMagnitudeInSet, int minGrayscaleLevel) {
		if (minGrayscaleLevel < 0 || minGrayscaleLevel > 255)
			throw new IllegalArgumentException("minGrayscaleLevel must be in [0,255], was " + minGrayscaleLevel);

		int level;
		if (dimmestMagnitudeInSet <= brightestMagnitudeInSet) {
			// A degenerate/single-star set - nothing to normalize against, render at full brightness.
			level = 255;
		} else {
			double magnitudeRange = dimmestMagnitudeInSet - brightestMagnitudeInSet;
			double brightnessFraction = (dimmestMagnitudeInSet - apparentMagnitude) / magnitudeRange;
			brightnessFraction = clamp01(brightnessFraction);

			level = minGrayscaleLevel + (int) Math.round(brightnessFraction * (255 - minGrayscaleLevel));
		}

		return new Color(level, level, level);
	}

	private static double clamp01(double value) {
		if (value < 0.0)
			return 0.0;
		if (value > 1.0)
			return 1.0;
		return value;
	}
}
