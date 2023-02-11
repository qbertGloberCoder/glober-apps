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
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
	private AbstractCoordinateTransformation arcSize;
=======
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
	private int width;
	private int height;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
	private AbstractCoordinateTransformation arcSize;
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
	private AbstractCoordinateTransformation arcSize;
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	private int startAngle;
	private int arcAngle;
	
	private boolean fill;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
	public ArcRenderer(int coordinatesType, int sizeCoordinatesType) throws Exception {
=======
	public ArcRenderer(int coordinatesType) throws Exception {
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
	public ArcRenderer(int coordinatesType, int sizeCoordinatesType) throws Exception {
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
	public ArcRenderer(int coordinatesType) throws Exception {
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
	public ArcRenderer(int coordinatesType, int sizeCoordinatesType) throws Exception {
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		if (coordinatesType == ABSOLUTE_COORDINATES) {
			coordinate = new AbsoluteCoordinateTransformation();
		} else if (coordinatesType == FRACTIONAL_COORDINATES) {
			coordinate = new FractionCoordinateTransformation();
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		
		if (sizeCoordinatesType == ABSOLUTE_COORDINATES) {
			arcSize = new AbsoluteCoordinateTransformation();
			((AbsoluteCoordinateTransformation)arcSize).setFloatTransformation(true);
		} else if (sizeCoordinatesType == FRACTIONAL_COORDINATES) {
			arcSize = new FractionCoordinateTransformation();
			((FractionCoordinateTransformation)arcSize).setFloatTransformation(true);
		} else {
			throw new Exception("coordinates type " + sizeCoordinatesType + " is invalid");
		}
<<<<<<< HEAD
<<<<<<< HEAD
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	}

	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
	@Override
	public void renderComponent(Graphics2D g2d) {
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
		if (! isRenderComponent())
			return;
		
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
		if (! isRenderComponent())
			return;
		
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
		if (! isRenderComponent())
			return;
		
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		int x;
		int y;
		
		Point p = coordinate.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
		Point s = arcSize.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
		
		x = p.x - ((s.x - 1) / 2) - 1;
		y = p.y - ((s.y - 1) / 2) - 1;
		
		if (fill)
			g2d.fillArc(x, y, s.x, s.y, startAngle, arcAngle);
		else
			g2d.drawArc(x, y, s.x, s.y, startAngle, arcAngle);
=======
=======
		Point s = arcSize.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
		
		x = p.x - ((s.x - 1) / 2) - 1;
		y = p.y - ((s.y - 1) / 2) - 1;
		
		if (fill)
			g2d.fillArc(x, y, s.x, s.y, startAngle, arcAngle);
		else
<<<<<<< HEAD
			g2d.drawArc(x, y, this.width, this.height, startAngle, arcAngle);
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
			g2d.drawArc(x, y, s.x, s.y, startAngle, arcAngle);
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
=======
		Point s = arcSize.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
		
		x = p.x - ((s.x - 1) / 2) - 1;
		y = p.y - ((s.y - 1) / 2) - 1;
		
		if (fill)
			g2d.fillArc(x, y, s.x, s.y, startAngle, arcAngle);
		else
<<<<<<< HEAD
			g2d.drawArc(x, y, this.width, this.height, startAngle, arcAngle);
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
			g2d.drawArc(x, y, s.x, s.y, startAngle, arcAngle);
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
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
<<<<<<< HEAD
<<<<<<< HEAD
	}
	
	public double getWidth() {
		return arcSize.getX();
<<<<<<< HEAD
	}

	public void setWidth(double width) {
		arcSize.setX(width);
=======
	}
	
<<<<<<< HEAD
=======
	}
	
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
	public int getWidth() {
		return width;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	}

	public double getHeight() {
		return arcSize.getY();
	}

=======
	public double getWidth() {
		return arcSize.getX();
	}

	public void setWidth(double width) {
		arcSize.setX(width);
	}

	public double getHeight() {
		return arcSize.getY();
	}

>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
	}

	public void setWidth(double width) {
		arcSize.setX(width);
	}

	public double getHeight() {
		return arcSize.getY();
	}

>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
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
