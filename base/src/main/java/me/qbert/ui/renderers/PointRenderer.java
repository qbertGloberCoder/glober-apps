package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;

import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;

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

public class PointRenderer extends AbstractFractionRenderer {
	
	private AbstractCoordinateTransformation coordinate;
	
	public PointRenderer(int coordinatesType) throws Exception {
		if (coordinatesType == ABSOLUTE_COORDINATES) {
			coordinate = new AbsoluteCoordinateTransformation();
		} else if (coordinatesType == FRACTIONAL_COORDINATES) {
			coordinate = new FractionCoordinateTransformation();
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
	}
	
	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
	@Override
	public void renderComponent(Graphics2D g2d) {
		if (! isRenderComponent())
			return;
		
		// Best alternative is to somehow get the canvas and setRGB on the canvas... This is easier
		Point p = coordinate.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
		g2d.drawLine(p.x, p.y, p.x, p.y);
	}

	public double getX() {
		return coordinate.getX();
	}

	public void setX(double fractionX) {
		coordinate.setX(fractionX);
	}

	public double getY() {
		return coordinate.getY();
	}

	public void setY(double fractionY) {
		coordinate.setY(fractionY);
	}
}
