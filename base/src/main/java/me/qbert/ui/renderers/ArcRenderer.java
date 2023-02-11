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
	private int width;
	private int height;
	private int startAngle;
	private int arcAngle;
	
	private boolean fill;

	public ArcRenderer(int coordinatesType) throws Exception {
		if (coordinatesType == ABSOLUTE_COORDINATES) {
			coordinate = new AbsoluteCoordinateTransformation();
		} else if (coordinatesType == FRACTIONAL_COORDINATES) {
			coordinate = new FractionCoordinateTransformation();
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
	}
	
	@Override
	public void renderComponent(Graphics2D g2d) {
		int x;
		int y;
		
		Point p = coordinate.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
		
		x = p.x - ((this.width - 1) / 2);
		y = p.y - ((this.height - 1) / 2);
		
		if (fill)
			g2d.fillArc(x, y, this.width, this.height, startAngle, arcAngle);
		else
			g2d.drawArc(x, y, this.width, this.height, startAngle, arcAngle);
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
	
	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
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
