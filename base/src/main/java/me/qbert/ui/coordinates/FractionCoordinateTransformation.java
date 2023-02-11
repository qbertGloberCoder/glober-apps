package me.qbert.ui.coordinates;


import java.awt.Point;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
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

public class FractionCoordinateTransformation extends AbstractCoordinateTransformation {
	boolean floatTransformation = false;
	
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = (int)(getX() * dimensionWidth);
		int y = (int)(getY() * dimensionHeight);
		
		if (! floatTransformation) {
			x += dimensionLeftX;
			y += dimensionTopY;
		}
		
		return new Point(x, y);
	}

	public boolean isFloatTransformation() {
		return floatTransformation;
	}

	public void setFloatTransformation(boolean floatTransformation) {
		this.floatTransformation = floatTransformation;
	}
=======
public class FractionCoordinateTransformation extends AbstractCoordinateTransformation {
	boolean floatTransformation = false;
	
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = (int)(getX() * dimensionWidth);
		int y = (int)(getY() * dimensionHeight);
		
		if (! floatTransformation) {
			x += dimensionLeftX;
			y += dimensionTopY;
		}
		
		return new Point(x, y);
	}
<<<<<<< HEAD
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======

	public boolean isFloatTransformation() {
		return floatTransformation;
	}

	public void setFloatTransformation(boolean floatTransformation) {
		this.floatTransformation = floatTransformation;
	}
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
public class FractionCoordinateTransformation extends AbstractCoordinateTransformation {
	boolean floatTransformation = false;
	
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = (int)(getX() * dimensionWidth);
		int y = (int)(getY() * dimensionHeight);
		
		if (! floatTransformation) {
			x += dimensionLeftX;
			y += dimensionTopY;
		}
		
		return new Point(x, y);
	}
<<<<<<< HEAD
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======

	public boolean isFloatTransformation() {
		return floatTransformation;
	}

	public void setFloatTransformation(boolean floatTransformation) {
		this.floatTransformation = floatTransformation;
	}
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
}
