package me.qbert.mapper.model;

import java.awt.Color;

public class ContourLine {
	private double latitude;
	private double longitude;
	private Color color;
	private double thickness;
	private boolean fixedWidthMode;
	public double getLatitude() {
		return latitude;
	}
	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}
	public double getLongitude() {
		return longitude;
	}
	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}
	public Color getColor() {
		return color;
	}
	public void setColor(Color color) {
		this.color = color;
	}
	public double getThickness() {
		return thickness;
	}
	public void setThickness(double thickness) {
		this.thickness = thickness;
	}
	public boolean isFixedWidthMode() {
		return fixedWidthMode;
	}
	public void setFixedWidthMode(boolean fixedWidthMode) {
		this.fixedWidthMode = fixedWidthMode;
	}
}
