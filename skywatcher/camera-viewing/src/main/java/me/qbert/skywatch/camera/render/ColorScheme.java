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

// spec §5: "All colors are user-configurable (not hardcoded)... Ship a small set of presets
// (default, deuteranopia-friendly, high-contrast) in addition to full customization." This is the
// mutable per-camera (or per-user) color configuration; ColorPresets below holds the three ship-
// with-the-app starting points. Stars are deliberately not included here - they're rendered
// grayscale by magnitude (StarBrightness), not a single fixed color.
public final class ColorScheme {
	private Color sunColor;
	private Color moonColor;
	private Color planetColor;
	private Color graticuleColor;
	// Renamed from celestialEquatorColor (Item 5, "Graticule redesign") - this field now colors BOTH
	// the celestial-origin reference lines together (Dec=0, the original celestial equator, AND the
	// new RA=0 celestial prime meridian - see render.Graticule.paintPrimeMeridian), not just the
	// equator alone. Default changed from CYAN to RED accordingly (see ColorPresets) - the OLD cyan
	// default was handed down to the new boresightReferenceColor field below instead, per the design
	// note's own "picks up the color already in the palette" reasoning.
	private Color celestialOriginColor;
	private Color watchedObjectPathColor;
	private Color watchedObjectMarkerColor;
	private Color labelColor;
	// New reference-line color fields (Item 5) - see render.Graticule.paintObserverCardinalCross/
	// paintWatchedObjectReferenceLines/paintBoresightReferenceLines for what each one colors.
	private Color observerCardinalCrossColor;
	private Color watchedObjectReferenceLineColor;
	private Color boresightReferenceColor;

	public ColorScheme(Color sunColor, Color moonColor, Color planetColor, Color graticuleColor,
			Color celestialOriginColor, Color watchedObjectPathColor, Color watchedObjectMarkerColor,
			Color labelColor, Color observerCardinalCrossColor, Color watchedObjectReferenceLineColor,
			Color boresightReferenceColor) {
		this.sunColor = requireNonNull(sunColor, "sunColor");
		this.moonColor = requireNonNull(moonColor, "moonColor");
		this.planetColor = requireNonNull(planetColor, "planetColor");
		this.graticuleColor = requireNonNull(graticuleColor, "graticuleColor");
		this.celestialOriginColor = requireNonNull(celestialOriginColor, "celestialOriginColor");
		this.watchedObjectPathColor = requireNonNull(watchedObjectPathColor, "watchedObjectPathColor");
		this.watchedObjectMarkerColor = requireNonNull(watchedObjectMarkerColor, "watchedObjectMarkerColor");
		this.labelColor = requireNonNull(labelColor, "labelColor");
		this.observerCardinalCrossColor = requireNonNull(observerCardinalCrossColor, "observerCardinalCrossColor");
		this.watchedObjectReferenceLineColor = requireNonNull(watchedObjectReferenceLineColor,
				"watchedObjectReferenceLineColor");
		this.boresightReferenceColor = requireNonNull(boresightReferenceColor, "boresightReferenceColor");
	}

	private static Color requireNonNull(Color color, String name) {
		if (color == null)
			throw new IllegalArgumentException(name + " must not be null");
		return color;
	}

	public Color getSunColor() {
		return sunColor;
	}

	public void setSunColor(Color sunColor) {
		this.sunColor = requireNonNull(sunColor, "sunColor");
	}

	public Color getMoonColor() {
		return moonColor;
	}

	public void setMoonColor(Color moonColor) {
		this.moonColor = requireNonNull(moonColor, "moonColor");
	}

	public Color getPlanetColor() {
		return planetColor;
	}

	public void setPlanetColor(Color planetColor) {
		this.planetColor = requireNonNull(planetColor, "planetColor");
	}

	public Color getGraticuleColor() {
		return graticuleColor;
	}

	public void setGraticuleColor(Color graticuleColor) {
		this.graticuleColor = requireNonNull(graticuleColor, "graticuleColor");
	}

	public Color getCelestialOriginColor() {
		return celestialOriginColor;
	}

	public void setCelestialOriginColor(Color celestialOriginColor) {
		this.celestialOriginColor = requireNonNull(celestialOriginColor, "celestialOriginColor");
	}

	public Color getWatchedObjectPathColor() {
		return watchedObjectPathColor;
	}

	public void setWatchedObjectPathColor(Color watchedObjectPathColor) {
		this.watchedObjectPathColor = requireNonNull(watchedObjectPathColor, "watchedObjectPathColor");
	}

	public Color getWatchedObjectMarkerColor() {
		return watchedObjectMarkerColor;
	}

	public void setWatchedObjectMarkerColor(Color watchedObjectMarkerColor) {
		this.watchedObjectMarkerColor = requireNonNull(watchedObjectMarkerColor, "watchedObjectMarkerColor");
	}

	public Color getLabelColor() {
		return labelColor;
	}

	public void setLabelColor(Color labelColor) {
		this.labelColor = requireNonNull(labelColor, "labelColor");
	}

	public Color getObserverCardinalCrossColor() {
		return observerCardinalCrossColor;
	}

	public void setObserverCardinalCrossColor(Color observerCardinalCrossColor) {
		this.observerCardinalCrossColor = requireNonNull(observerCardinalCrossColor, "observerCardinalCrossColor");
	}

	public Color getWatchedObjectReferenceLineColor() {
		return watchedObjectReferenceLineColor;
	}

	public void setWatchedObjectReferenceLineColor(Color watchedObjectReferenceLineColor) {
		this.watchedObjectReferenceLineColor = requireNonNull(watchedObjectReferenceLineColor,
				"watchedObjectReferenceLineColor");
	}

	public Color getBoresightReferenceColor() {
		return boresightReferenceColor;
	}

	public void setBoresightReferenceColor(Color boresightReferenceColor) {
		this.boresightReferenceColor = requireNonNull(boresightReferenceColor, "boresightReferenceColor");
	}
}
