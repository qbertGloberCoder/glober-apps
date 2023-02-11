package me.qbert.ui.coordinates;


import java.awt.Point;

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
}
