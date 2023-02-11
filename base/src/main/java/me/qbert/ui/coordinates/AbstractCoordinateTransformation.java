package me.qbert.ui.coordinates;

import me.qbert.ui.CoordinatesTransformationI;

public abstract class AbstractCoordinateTransformation implements CoordinatesTransformationI {
	private double x;
	private double y;
	
	public double getX() {
		return x;
	}
	
	public void setX(double x) {
		this.x = x;
	}
	
	public double getY() {
		return y;
	}
	
	public void setY(double y) {
		this.y = y;
	}
}
