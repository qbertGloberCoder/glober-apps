package me.qbert.ui.renderers;

import java.awt.Point;

import me.qbert.ui.RendererI;

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

public abstract class AbstractFractionRenderer implements RendererI {
	public static final int ABSOLUTE_COORDINATES = 1;
	public static final int FRACTIONAL_COORDINATES = 2;
	
	private boolean maintainAspectRatio = true;
	
	private double boundaryLeft = 0;
	private double boundaryTop = 0;
	private double boundaryWidth = 0;
	private double boundaryHeight = 0;
	
	private Point topLeft;
	private Point bottomRight;
	
	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		boundaryLeft = (double)dimensionLeftX;
		boundaryTop = (double)dimensionTopY;
		boundaryWidth = (double)dimensionWidth;
		boundaryHeight = (double)dimensionHeight;
		
		if (maintainAspectRatio) {
			if (boundaryWidth > boundaryHeight) {
				// width is greater than height
				boundaryWidth = boundaryHeight;
				boundaryLeft = (((double)dimensionWidth - boundaryHeight) / 2.0);
			} else if (boundaryWidth < boundaryHeight) {
				// height is greater than width
				boundaryHeight = boundaryWidth;
				boundaryTop = (((double)dimensionHeight - boundaryWidth) / 2.0);
			}
		}
		
//		System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
		
		topLeft = convertFractionToPoint(0.0, 0.0);
		bottomRight = convertFractionToPoint(1.0, 1.0);
	}
	
	protected Point convertFractionToPoint(double fractionX, double fractionY) {
		int x = (int)(boundaryLeft + (boundaryWidth * fractionX));
		int y = (int)(boundaryTop + (boundaryHeight * fractionY));
		
		Point p = new Point(x, y);
		
		return p;
		
	}

	public boolean isMaintainAspectRatio() {
		return maintainAspectRatio;
	}

	public void setMaintainAspectRatio(boolean maintainAspectRatio) {
		this.maintainAspectRatio = maintainAspectRatio;
	}

	public double getBoundaryLeft() {
		return boundaryLeft;
	}

	public double getBoundaryTop() {
		return boundaryTop;
	}

	public double getBoundaryWidth() {
		return boundaryWidth;
	}

	public double getBoundaryHeight() {
		return boundaryHeight;
	}

	public Point getTopLeft() {
		return topLeft;
	}

	public Point getBottomRight() {
		return bottomRight;
	}
}
