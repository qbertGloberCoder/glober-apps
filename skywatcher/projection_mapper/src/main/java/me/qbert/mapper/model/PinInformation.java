package me.qbert.mapper.model;

import java.awt.Color;

public class PinInformation {
	private double latitude;
	private double longitude;
	private Color color;
	private int outerPinSize;
	private int innerPinSize;
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
	public int getOuterPinSize() {
		return outerPinSize;
	}
	public void setOuterPinSize(int outerPinSize) {
		this.outerPinSize = outerPinSize;
	}
	public int getInnerPinSize() {
		return innerPinSize;
	}
	public void setInnerPinSize(int innerPinSize) {
		this.innerPinSize = innerPinSize;
	}
	
}
