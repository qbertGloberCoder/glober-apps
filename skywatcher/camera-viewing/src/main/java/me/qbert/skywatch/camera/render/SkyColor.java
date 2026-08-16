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

// Layer 3A's flood-fill color, driven by the sun's current altitude - ported from
// old_draw_project's actual formula (org.bluerock.ui.controllers.StarFieldController, ~line
// 6380), confirmed to have no equivalent already in sw-base/ga-base before porting. Daytime blue
// at sun altitude >= 0, fading proportionally across the civil/nautical/astronomical twilight band
// (0 to -18 degrees) to solid black at -18 and below. See CLAUDE.md's Layer model table.
public final class SkyColor {
	private SkyColor() {
	}

	private static final Color DAY_COLOR = new Color(87, 141, 203);
	private static final Color NIGHT_COLOR = Color.BLACK;
	private static final double TWILIGHT_END_DEGREES = -18.0;

	public static Color forSunAltitude(double sunAltitudeDegrees) {
		if (sunAltitudeDegrees >= 0.0)
			return DAY_COLOR;

		if (sunAltitudeDegrees <= TWILIGHT_END_DEGREES)
			return NIGHT_COLOR;

		double nightFraction = sunAltitudeDegrees / TWILIGHT_END_DEGREES; // 0 at alt=0, 1 at alt=-18
		double dayWeight = 1.0 - nightFraction;

		int r = (int) Math.round(DAY_COLOR.getRed() * dayWeight);
		int g = (int) Math.round(DAY_COLOR.getGreen() * dayWeight);
		int b = (int) Math.round(DAY_COLOR.getBlue() * dayWeight);

		return new Color(r, g, b);
	}
}
