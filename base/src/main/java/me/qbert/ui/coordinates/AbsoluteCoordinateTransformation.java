package me.qbert.ui.coordinates;


import java.awt.Point;

public class AbsoluteCoordinateTransformation extends AbstractCoordinateTransformation {
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = dimensionLeftX + (int)(getX());
		int y = dimensionTopY + (int)(getY());
		
		return new Point(x, y);
	}
}
