package me.qbert.skywatch.camera.render;

import java.awt.Rectangle;

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

// spec §9's viewfinder/zoom-out mode - "renders beyond the camera's actual field of view, to
// predict when an object will enter frame." See CLAUDE.md: this is a compositing/layout technique,
// not FOV-substitution at the math layer - "decoupling 'FOV to render' from 'camera's real FOV'"
// rather than a separate rendering path. This class computes exactly that decoupling's geometry:
// where the camera's own true-FOV image (layer 1/4) sits as an inset within a canvas rendered at a
// wider synthetic FOV, so the sky overlay (layer 2/3A-3C, via CelestialObjectSizing etc.) can use
// the wider renderFovDegrees/canvas-size pixels-per-degree while the inset shows only what the
// camera's true FOV actually covers.
public final class ViewfinderLayout {
	private ViewfinderLayout() {
	}

	// canvasWidth/canvasHeight are the full (wider) render canvas; trueFovDegrees is the camera's
	// actual field of view; renderFovDegrees is the (>= trueFovDegrees) FOV the overlay is
	// computed against. Returns the centered rectangle, within the full canvas, where the camera's
	// own true-FOV content should be drawn. When renderFovDegrees == trueFovDegrees this is the
	// full canvas - viewfinder mode is off, not a special case.
	public static Rectangle insetBounds(int canvasWidth, int canvasHeight, double trueFovDegrees,
			double renderFovDegrees) {
		if (canvasWidth <= 0 || canvasHeight <= 0)
			throw new IllegalArgumentException("canvas dimensions must be positive");
		if (trueFovDegrees <= 0.0 || renderFovDegrees <= 0.0)
			throw new IllegalArgumentException("FOV values must be positive");
		if (renderFovDegrees < trueFovDegrees)
			throw new IllegalArgumentException(
					"renderFovDegrees (" + renderFovDegrees + ") must be >= trueFovDegrees (" + trueFovDegrees + ") - viewfinder mode only widens, never narrows");

		double scale = trueFovDegrees / renderFovDegrees;

		int insetWidth = (int) Math.round(canvasWidth * scale);
		int insetHeight = (int) Math.round(canvasHeight * scale);
		int x = (canvasWidth - insetWidth) / 2;
		int y = (canvasHeight - insetHeight) / 2;

		return new Rectangle(x, y, insetWidth, insetHeight);
	}

	// The pixels-per-degree the sky overlay (layers 2/3A-3C) should use - based on the wider
	// render FOV, not the camera's true FOV. This is what "decoupled from the camera's real FOV"
	// means concretely: CelestialObjectSizing.radiusPixels(...) and similar should be called with
	// this value's implied canvasSpanPixels/fovDegrees, not the inset's.
	public static double overlayPixelsPerDegree(int canvasSpanPixels, double renderFovDegrees) {
		if (canvasSpanPixels <= 0)
			throw new IllegalArgumentException("canvasSpanPixels must be positive");
		if (renderFovDegrees <= 0.0)
			throw new IllegalArgumentException("renderFovDegrees must be positive");

		return canvasSpanPixels / renderFovDegrees;
	}
}
