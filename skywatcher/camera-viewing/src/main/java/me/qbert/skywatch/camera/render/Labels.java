package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;

import me.qbert.ui.renderers.AbstractFractionRenderer;
import me.qbert.ui.renderers.TextRenderer;

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

// Task 4.4 / spec §5's "independent toggle, printed beside each rendered object" - combined with
// accurate plate solving, this is spec's own stated reason labels exist at all: identifying an
// unknown point of light in a real frame with no separate lookup/hit-test step, since the rendered
// object already tracks the real one. A thin wrapper over ga-base's TextRenderer, positioned
// relative to an already-computed screen point (from CameraProjector) rather than computing its
// own - CelestialObjectsLayer calls this right after drawing each object's own glyph, reusing the
// same position instead of re-projecting.
public final class Labels {
	private Labels() {
	}

	// Offset from the object's own center, in pixels - up and to the right, clear of the glyph
	// itself for any of the radii CelestialObjectsLayer/CelestialObjectSizing produce in practice.
	private static final double OFFSET_X_PIXELS = 6.0;
	private static final double OFFSET_Y_PIXELS = -6.0;

	public static void draw(Graphics2D g2d, String text, Point2D.Double objectScreenPosition, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		if (text == null || text.isEmpty())
			return;

		TextRenderer renderer = new TextRenderer(AbstractFractionRenderer.ABSOLUTE_COORDINATES);
		renderer.setText(text);
		renderer.setX(objectScreenPosition.x + OFFSET_X_PIXELS);
		renderer.setY(objectScreenPosition.y + OFFSET_Y_PIXELS);
		renderer.setRenderDimensions(0, 0, canvasWidthPixels, canvasHeightPixels);

		g2d.setColor(color);
		renderer.renderComponent(g2d);
	}
}
