package me.qbert.ui.coordinates;


import java.awt.Point;

<<<<<<< HEAD
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

public class AbsoluteCoordinateTransformation extends AbstractCoordinateTransformation {
	boolean floatTransformation = false;
	
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = (int)(getX());
		int y = (int)(getY());
		
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
public class AbsoluteCoordinateTransformation extends AbstractCoordinateTransformation {
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = dimensionLeftX + (int)(getX());
		int y = dimensionTopY + (int)(getY());
		
		return new Point(x, y);
	}
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
}
