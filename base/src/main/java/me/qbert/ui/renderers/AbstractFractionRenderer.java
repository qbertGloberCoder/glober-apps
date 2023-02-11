package me.qbert.ui.renderers;

import java.awt.Point;

import me.qbert.ui.RendererI;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)

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
	
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
	private boolean renderComponent = true;
	
	private boolean maintainAspectRatio = true;
	private int shiftDirectionX = 0;
	private int shiftDirectionY = 0;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
	private boolean maintainAspectRatio = true;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
	private boolean maintainAspectRatio = true;
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
	private boolean maintainAspectRatio = true;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	
	private double boundaryLeft = 0;
	private double boundaryTop = 0;
	private double boundaryWidth = 0;
	private double boundaryHeight = 0;
	
	private Point topLeft;
	private Point bottomRight;
	
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
//	private boolean debug = false;
	
	private AbsoluteCoordinateTransformation absoluteCoordinate = new AbsoluteCoordinateTransformation();
	private FractionCoordinateTransformation fractionCoordinate = new FractionCoordinateTransformation();
	
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		boundaryLeft = (double)dimensionLeftX;
		boundaryTop = (double)dimensionTopY;
		boundaryWidth = (double)dimensionWidth;
		boundaryHeight = (double)dimensionHeight;
		
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
		double aspectRatio = 1.0;
		
		if (maintainAspectRatio) {
			aspectRatio = getAspectRatio();
			if (aspectRatio <= 0)
				aspectRatio = boundaryWidth / boundaryHeight;
			
			if ((boundaryWidth / boundaryHeight) > aspectRatio) {
				// width is greater than height
				boundaryWidth = boundaryHeight * aspectRatio;
				
				if (shiftDirectionX < 0)
					boundaryLeft = 0.0;
				else if (shiftDirectionX == 0)
					boundaryLeft = (((double)dimensionWidth - boundaryWidth) / 2.0);
				else
					boundaryLeft = ((double)dimensionWidth - boundaryWidth);
				
				boundaryLeft += dimensionLeftX;
			} else if ((boundaryWidth / boundaryHeight) <= aspectRatio) {
				// height is greater than width
				boundaryHeight = boundaryWidth / aspectRatio;
				if (shiftDirectionY < 0)
					boundaryTop = 0.0;
				else if (shiftDirectionY == 0)
					boundaryTop = (((double)dimensionHeight - boundaryHeight) / 2.0);
				else
					boundaryTop = ((double)dimensionHeight - boundaryHeight);
				
				boundaryTop += dimensionTopY;
			}
		}
		
//		if (debug)
//			System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
=======
=======
		double aspectRatio = 1.0;
		
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
		if (maintainAspectRatio) {
			aspectRatio = getAspectRatio();
			if (aspectRatio <= 0)
				aspectRatio = boundaryWidth / boundaryHeight;
			
			if ((boundaryWidth / boundaryHeight) > aspectRatio) {
				// width is greater than height
				boundaryWidth = boundaryHeight * aspectRatio;
				
				if (shiftDirectionX < 0)
					boundaryLeft = 0.0;
				else if (shiftDirectionX == 0)
					boundaryLeft = (((double)dimensionWidth - boundaryWidth) / 2.0);
				else
					boundaryLeft = ((double)dimensionWidth - boundaryWidth);
				
				boundaryLeft += dimensionLeftX;
			} else if ((boundaryWidth / boundaryHeight) <= aspectRatio) {
				// height is greater than width
				boundaryHeight = boundaryWidth / aspectRatio;
				if (shiftDirectionY < 0)
					boundaryTop = 0.0;
				else if (shiftDirectionY == 0)
					boundaryTop = (((double)dimensionHeight - boundaryHeight) / 2.0);
				else
					boundaryTop = ((double)dimensionHeight - boundaryHeight);
				
				boundaryTop += dimensionTopY;
			}
		}
		
<<<<<<< HEAD
//		System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
//		if (debug)
//			System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
=======
		double aspectRatio = 1.0;
		
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
		if (maintainAspectRatio) {
			aspectRatio = getAspectRatio();
			if (aspectRatio <= 0)
				aspectRatio = boundaryWidth / boundaryHeight;
			
			if ((boundaryWidth / boundaryHeight) > aspectRatio) {
				// width is greater than height
				boundaryWidth = boundaryHeight * aspectRatio;
				
				if (shiftDirectionX < 0)
					boundaryLeft = 0.0;
				else if (shiftDirectionX == 0)
					boundaryLeft = (((double)dimensionWidth - boundaryWidth) / 2.0);
				else
					boundaryLeft = ((double)dimensionWidth - boundaryWidth);
				
				boundaryLeft += dimensionLeftX;
			} else if ((boundaryWidth / boundaryHeight) <= aspectRatio) {
				// height is greater than width
				boundaryHeight = boundaryWidth / aspectRatio;
				if (shiftDirectionY < 0)
					boundaryTop = 0.0;
				else if (shiftDirectionY == 0)
					boundaryTop = (((double)dimensionHeight - boundaryHeight) / 2.0);
				else
					boundaryTop = ((double)dimensionHeight - boundaryHeight);
				
				boundaryTop += dimensionTopY;
			}
		}
		
<<<<<<< HEAD
//		System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
//		if (debug)
//			System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
=======
		double aspectRatio = 1.0;
		
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
		if (maintainAspectRatio) {
			aspectRatio = getAspectRatio();
			if (aspectRatio <= 0)
				aspectRatio = boundaryWidth / boundaryHeight;
			
			if ((boundaryWidth / boundaryHeight) > aspectRatio) {
				// width is greater than height
				boundaryWidth = boundaryHeight * aspectRatio;
				
				if (shiftDirectionX < 0)
					boundaryLeft = 0.0;
				else if (shiftDirectionX == 0)
					boundaryLeft = (((double)dimensionWidth - boundaryWidth) / 2.0);
				else
					boundaryLeft = ((double)dimensionWidth - boundaryWidth);
				
				boundaryLeft += dimensionLeftX;
			} else if ((boundaryWidth / boundaryHeight) <= aspectRatio) {
				// height is greater than width
				boundaryHeight = boundaryWidth / aspectRatio;
				if (shiftDirectionY < 0)
					boundaryTop = 0.0;
				else if (shiftDirectionY == 0)
					boundaryTop = (((double)dimensionHeight - boundaryHeight) / 2.0);
				else
					boundaryTop = ((double)dimensionHeight - boundaryHeight);
				
				boundaryTop += dimensionTopY;
			}
		}
		
<<<<<<< HEAD
//		System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
//		if (debug)
//			System.out.println(this.getClass().getName() + " SET: " + dimensionLeftX + ", + " + dimensionTopY + ", + " + dimensionWidth + ", + " + dimensionHeight + " --> " + boundaryLeft + ", + " + boundaryTop+ ", + " + boundaryWidth + ", + " + boundaryHeight);
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
		
		topLeft = convertFractionToPoint(0.0, 0.0);
		bottomRight = convertFractionToPoint(1.0, 1.0);
	}
	
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
/*	public void setDebug(boolean debug) {
		this.debug = debug;
	}
	
	public boolean isDebug() {
		return debug;
	} */
	
	public int getShiftDirectionX() {
		return shiftDirectionX;
	}

	public void setShiftDirectionX(int shiftDirectionX) {
		this.shiftDirectionX = shiftDirectionX;
	}

	public int getShiftDirectionY() {
		return shiftDirectionY;
	}

	public void setShiftDirectionY(int shiftDirectionY) {
		this.shiftDirectionY = shiftDirectionY;
	}

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	protected Point convertFractionToPoint(double fractionX, double fractionY) {
		int x = (int)(boundaryLeft + (boundaryWidth * fractionX));
		int y = (int)(boundaryTop + (boundaryHeight * fractionY));
		
		Point p = new Point(x, y);
		
		return p;
		
	}
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	
	public Point convertToCoordinates(double x, double y, int coordinatesType, boolean floatConversion) throws Exception {
		AbstractCoordinateTransformation coordinate = null;
		
		if (coordinatesType == ABSOLUTE_COORDINATES) {
			absoluteCoordinate.setFloatTransformation(floatConversion);
			coordinate = absoluteCoordinate;
		} else if (coordinatesType == FRACTIONAL_COORDINATES) {
			fractionCoordinate.setFloatTransformation(floatConversion);
			coordinate = fractionCoordinate;
		} else {
			throw new Exception("coordinates type " + coordinatesType + " is invalid");
		}
		
		coordinate.setX(x);
		coordinate.setY(y);

		Point p = coordinate.transform((int)getBoundaryLeft(), (int)getBoundaryTop(), (int)getBoundaryWidth(), (int)getBoundaryHeight());

//		if (debug) {
//			System.out.println(" CONVERT: " + x + ", " + y + " and " +  (int)getBoundaryLeft() + ", " + (int)getBoundaryTop() + ", " + (int)getBoundaryWidth() + ", " + (int)getBoundaryHeight() + " --> " + p.x + ", " + p.y);
//		}
		
		return p;
	}
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD

	public boolean isRenderComponent() {
		return renderComponent;
	}

	public void setRenderComponent(boolean renderComponent) {
		this.renderComponent = renderComponent;
	}
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)

	public boolean isRenderComponent() {
		return renderComponent;
	}

	public void setRenderComponent(boolean renderComponent) {
		this.renderComponent = renderComponent;
	}
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)

	public boolean isRenderComponent() {
		return renderComponent;
	}

	public void setRenderComponent(boolean renderComponent) {
		this.renderComponent = renderComponent;
	}
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)

	public boolean isRenderComponent() {
		return renderComponent;
	}

	public void setRenderComponent(boolean renderComponent) {
		this.renderComponent = renderComponent;
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
