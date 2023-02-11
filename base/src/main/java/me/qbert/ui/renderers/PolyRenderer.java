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

public class PolyRenderer extends AbstractFractionRenderer {
	ArrayList<AbstractCoordinateTransformation> lineSegments;
	
	private int coordinatesType;
	
	private boolean fill;

	public PolyRenderer(int coordinatesType) throws Exception {
		if ((coordinatesType == ABSOLUTE_COORDINATES) || (coordinatesType == FRACTIONAL_COORDINATES)) {
			this.coordinatesType = coordinatesType;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
	}
	
<<<<<<< HEAD
	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	private void initializeList(int count) {
		lineSegments = new ArrayList<AbstractCoordinateTransformation>();
		for (int i = 0;i < count;i ++) {
			if (coordinatesType == ABSOLUTE_COORDINATES) {
				lineSegments.add(new AbsoluteCoordinateTransformation());
			} else if (coordinatesType == FRACTIONAL_COORDINATES) {
				lineSegments.add(new FractionCoordinateTransformation());
			}
		}
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
<<<<<<< HEAD
		if (! isRenderComponent())
			return;
		
		if (lineSegments == null)
			return;
		
=======
		if (lineSegments == null)
			return;
		
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		int left = (int)getBoundaryLeft();
		int top = (int)getBoundaryTop();
		int width = (int)getBoundaryWidth();
		int height = (int)getBoundaryHeight();

		int [] x = new int[lineSegments.size()];
		int [] y = new int[lineSegments.size()];
		
		for (int i = 0;i < lineSegments.size();i ++) {
			Point p = lineSegments.get(i).transform(left, top, width, height);
			x[i] = p.x;
			y[i] = p.y;
		}

		
		if (fill)
			g2d.fillPolygon(x, y, x.length);
		else
			g2d.drawPolygon(x, y, x.length);
	}
	
	public double[] getX() {
		double [] x = new double[lineSegments.size()];
		
		for (int i = 0;i < lineSegments.size();i ++) {
			x[i] = lineSegments.get(i).getX();
		}
		
		return x;
	}

	public void setX(double [] xArray) {
		if (xArray == null) {
			lineSegments = null;
			return;
		}
		
		if ((lineSegments == null) || (lineSegments.size() != xArray.length))
			initializeList(xArray.length);
		
		for (int i = 0;i < xArray.length;i ++) {
			lineSegments.get(i).setX(xArray[i]);
		}
	}

	public double[] getY() {
		double [] y = new double[lineSegments.size()];
		
		for (int i = 0;i < lineSegments.size();i ++) {
			y[i] = lineSegments.get(i).getY();
		}
		
		return y;
	}

	public void setY(double [] yArray) {
		if (yArray == null) {
			lineSegments = null;
			return;
		}
		
		if ((lineSegments == null) || (lineSegments.size() != yArray.length))
			initializeList(yArray.length);
		
		for (int i = 0;i < yArray.length;i ++) {
			lineSegments.get(i).setY(yArray[i]);
		}
	}

	public boolean isFill() {
		return fill;
	}

	public void setFill(boolean fill) {
		this.fill = fill;
	}
}
