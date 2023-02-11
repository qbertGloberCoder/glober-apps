package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;

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

public class LineRenderer extends AbstractFractionRenderer {
	ArrayList<AbstractCoordinateTransformation[]> lineSegments;
	
	private int coordinatesType;
	
	public LineRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		initializeList(1);
	}
	
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation[]>();
		for (int i = 0;i < count;i ++) {
			AbstractCoordinateTransformation [] linePair = new AbstractCoordinateTransformation[2];
			
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				linePair[0] = new AbsoluteCoordinateTransformation();
				linePair[1] = new AbsoluteCoordinateTransformation();
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				linePair[0] = new FractionCoordinateTransformation();
				linePair[1] = new FractionCoordinateTransformation();
			}
			
			lineSegments.add(linePair);
		}
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
		if (lineSegments == null)
			return;
		
		int left = (int)getBoundaryLeft();
		int top = (int)getBoundaryTop();
		int width = (int)getBoundaryWidth();
		int height = (int)getBoundaryHeight();

		for (int i = 0;i < lineSegments.size();i ++) {
			AbstractCoordinateTransformation [] linePair = lineSegments.get(i);
			
			Point p1 = linePair[0].transform(left, top, width, height);
			Point p2 = linePair[1].transform(left, top, width, height);
			g2d.drawLine(p1.x, p1.y, p2.x, p2.y);
		}
	}

	public double getX1() {
		return lineSegments.get(0)[0].getX();
	}

	public void setX1(double x) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[0].setX(x);
	}

	public double getY1() {
		return lineSegments.get(0)[0].getY();
	}

	public void setY1(double y) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[0].setY(y);
	}

	public double getX2() {
		return lineSegments.get(0)[1].getX();
	}

	public void setX2(double x) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[1].setX(x);
	}

	public double getY2() {
		return lineSegments.get(0)[1].getY();
	}

	public void setY2(double y) {
		if (lineSegments.size() != 1)
			initializeList(1);
		lineSegments.get(0)[1].setY(y);
	}

	
	public void setX1Array(double [] xArray) {
		if (xArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != xArray.length)
			initializeList(xArray.length);
		
		for (int i = 0;i < xArray.length;i ++) {
			lineSegments.get(i)[0].setX(xArray[i]);
		}
	}
	
	public void setY1Array(double [] yArray) {
		if (yArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != yArray.length)
			initializeList(yArray.length);
		
		for (int i = 0;i < yArray.length;i ++) {
			lineSegments.get(i)[0].setY(yArray[i]);
		}
	}
	
	public void setX2Array(double [] xArray) {
		if (xArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != xArray.length)
			initializeList(xArray.length);
		
		for (int i = 0;i < xArray.length;i ++) {
			lineSegments.get(i)[1].setX(xArray[i]);
		}
	}
	
	public void setY2Array(double [] yArray) {
		if (yArray == null) {
			lineSegments = null;
			return;
		}
		
		if (lineSegments.size() != yArray.length)
			initializeList(yArray.length);
		
		for (int i = 0;i < yArray.length;i ++) {
			lineSegments.get(i)[1].setY(yArray[i]);
		}
	}
}
