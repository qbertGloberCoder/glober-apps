package me.qbert.ui.coordinates;


import java.awt.Point;

public class FractionCoordinateTransformation extends AbstractCoordinateTransformation {
	@Override
	public Point transform(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		int x = dimensionLeftX + (int)(getX() * dimensionWidth);
		int y = dimensionTopY + (int)(getY() * dimensionHeight);
		
		return new Point(x, y);
	}
}
