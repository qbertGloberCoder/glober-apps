package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.ObjectDirectionAltAz;

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

// Layer 2's watched-object crosshair [spec §6, ../CLAUDE.md's "Watched-object rendering" section] -
// a SEPARATE artifact from Layer 3's live sun/moon/planet/star glyph for the same object, not a
// second copy of it. Layer 3 always shows an object at its position for the frame's actual render
// time; this class marks where that same object's RA/Dec places it at some OTHER reference time the
// caller supplies - e.g. "where was the watched object 3 hours ago" while the rest of the frame
// renders live. Deliberately a plus-shaped reticle with a gap at the center (not a filled/bounding
// circle) so it reads as a distinct kind of marker from Layer 3's glyphs at a glance, per
// ../CLAUDE.md's "not the §5 glyph" framing.
//
// Resolves position via watch.WatchedObject.resolveAltAz(...) - no separate Sun/Moon/planet/star
// lookup here, this class only projects and draws.
public final class WatchedObjectCrosshair {
	private WatchedObjectCrosshair() {
	}

	private static final double ARM_LENGTH_PIXELS = 10.0;
	private static final double GAP_PIXELS = 3.0;

	public static void paint(Graphics2D g2d, WatchedObject watchedObject, ObservationTime referenceTime,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		paint(g2d, watchedObject, referenceTime, location, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels, false);
	}

	public static void paint(Graphics2D g2d, WatchedObject watchedObject, ObservationTime referenceTime,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels, boolean showLabel) throws Exception {
		if (watchedObject == null)
			throw new IllegalArgumentException("watchedObject must not be null");

		ObjectDirectionAltAz altAz = watchedObject.resolveAltAz(referenceTime, location);
		Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
				altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
		// A real user report (confirmed via the watched-object OSD detail tier's own "Local RA/Dec"
		// readout, plus a moving crosshair, for an object that was actually far outside the frame):
		// projectToPixels(...) returning non-null only means theta is within the lens's max
		// representable angle, not that the point actually lands inside the frame - see
		// CameraProjector.isOnCanvas(...)'s own comment. Unlike a connected path/line (Graphics2D
		// clips those correctly even with an off-canvas endpoint), this is a small, isolated,
		// point-centered marker - if its center is off-canvas, none of it is visible, so this check
		// is both correct and necessary here.
		if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
			return; // outside what this lens can represent at all, or off-canvas - nothing to mark

		g2d.setColor(color);
		double x = screenPosition.x;
		double y = screenPosition.y;
		g2d.draw(new Line2D.Double(x - ARM_LENGTH_PIXELS, y, x - GAP_PIXELS, y));
		g2d.draw(new Line2D.Double(x + GAP_PIXELS, y, x + ARM_LENGTH_PIXELS, y));
		g2d.draw(new Line2D.Double(x, y - ARM_LENGTH_PIXELS, x, y - GAP_PIXELS));
		g2d.draw(new Line2D.Double(x, y + GAP_PIXELS, x, y + ARM_LENGTH_PIXELS));

		if (showLabel)
			Labels.draw(g2d, watchedObject.getDisplayName(), screenPosition, color, canvasWidthPixels, canvasHeightPixels);
	}
}
