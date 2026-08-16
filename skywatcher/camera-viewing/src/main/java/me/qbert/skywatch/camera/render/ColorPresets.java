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

// The three ship-with-the-app presets [spec §5]. Each call returns a fresh, independently mutable
// ColorScheme instance (not a shared constant) since these are meant to be a user's starting
// point, not read-only. Not a claim of clinical validation for DEUTERANOPIA_FRIENDLY - it follows
// the standard blue/orange (rather than red/green) contrast approach commonly recommended for
// red-green color-vision deficiency, the most common form, avoiding spec's default purple (which
// can read too close to blue for deuteranopes).
public final class ColorPresets {
	private ColorPresets() {
	}

	// Matches spec §5's stated defaults exactly: Sun=Yellow, Moon=Dark blue, Planets=Purple.
	// Item 5 ("Graticule redesign"): celestialOriginColor moves from the old CYAN default to RED
	// (the "celestial origin" reference-line group), and boresightReferenceColor picks up the OLD
	// cyan default instead - the observer-cardinal-cross/watched-object-reference-line groups get
	// GREEN/YELLOW respectively, matching the design's own red/green/yellow/cyan mnemonic exactly.
	public static ColorScheme defaultScheme() {
		return new ColorScheme(
				Color.YELLOW,
				new Color(0, 0, 139), // dark blue
				new Color(160, 32, 240), // purple
				Color.LIGHT_GRAY,
				Color.RED,
				Color.GREEN,
				Color.RED,
				Color.WHITE,
				Color.GREEN,
				Color.YELLOW,
				Color.CYAN);
	}

	public static ColorScheme deuteranopiaFriendlyScheme() {
		return new ColorScheme(
				new Color(230, 159, 0), // orange
				new Color(0, 114, 178), // blue
				new Color(86, 180, 233), // sky blue - distinct from moon's darker blue
				new Color(150, 150, 150),
				new Color(213, 94, 0), // vermillion - celestial origin
				new Color(0, 158, 115), // bluish green
				new Color(213, 94, 0), // vermillion
				Color.WHITE,
				new Color(0, 114, 178), // blue - observer cardinal cross
				new Color(240, 228, 66), // yellow - watched-object reference lines
				new Color(86, 180, 233)); // sky blue - boresight reference lines
	}

	public static ColorScheme highContrastScheme() {
		return new ColorScheme(
				Color.YELLOW,
				Color.WHITE,
				Color.CYAN,
				Color.GRAY,
				Color.RED,
				Color.GREEN,
				Color.RED,
				Color.WHITE,
				Color.GREEN,
				Color.YELLOW,
				Color.MAGENTA);
	}
}
