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

public class ArcRenderer extends AbstractFractionRenderer {
	private AbstractCoordinateTransformation coordinate;
	private AbstractCoordinateTransformation arcSize;
	private int startAngle;
	private int arcAngle;
	
	private boolean fill;

	public ArcRenderer(int coordinatesType, int sizeCoordinatesType) throws Exception {
		if (coordinatesType == ABSOLUTE_COORDINATES) {
			coordinate = new AbsoluteCoordinateTransformation();
		} else if (coordinatesType == FRACTIONAL_COORDINATES) {
			coordinate = new FractionCoordinateTransformation();
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		if (sizeCoordinatesType == ABSOLUTE_COORDINATES) {
			arcSize = new AbsoluteCoordinateTransformation();
			((AbsoluteCoordinateTransformation)arcSize).setFloatTransformation(true);
		} else if (sizeCoordinatesType == FRACTIONAL_COORDINATES) {
			arcSize = new FractionCoordinateTransformation();
			((FractionCoordinateTransformation)arcSize).setFloatTransformation(true);
		} else {
			throw new Exception("coordinates type " + sizeCoordinatesType + " is invalid");
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
		
		int x;
		int y;
		
		Point p = coordinate.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
		Point s = arcSize.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
		
		x = p.x - ((s.x - 1) / 2) - 1;
		y = p.y - ((s.y - 1) / 2) - 1;
		
		if (fill)
			g2d.fillArc(x, y, s.x, s.y, startAngle, arcAngle);
		else
			g2d.drawArc(x, y, s.x, s.y, startAngle, arcAngle);
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
	
	public double getWidth() {
		return arcSize.getX();
	}

	public void setWidth(double width) {
		arcSize.setX(width);
	}

	public double getHeight() {
		return arcSize.getY();
	}

	public void setHeight(double height) {
		arcSize.setY(height);
	}

	public int getStartAngle() {
		return startAngle;
	}

	public void setStartAngle(int startAngle) {
		this.startAngle = startAngle;
	}

	public int getArcAngle() {
		return arcAngle;
	}

	public void setArcAngle(int arcAngle) {
		this.arcAngle = arcAngle;
	}

	public boolean isFill() {
		return fill;
	}

	public void setFill(boolean fill) {
		this.fill = fill;
	}
}
