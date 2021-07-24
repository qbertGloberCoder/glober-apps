package me.qbert.skywatch.controller;

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

public class LightSensor {
	public static final int STEPS_PER_REVOLUTION = 2000;
	
	private double originAzimuth;
	private double originAltitude;
	
	private int azimuthSteps;
	private int altitudeSteps;
	
	public LightSensor() {
		originAzimuth = 360.0 * Math.random();
		originAltitude = 360.0 * Math.random();
	}
	
	public void stepAzimuth(int steps) {
		azimuthSteps += steps;
	}
	
	public void stepAltitude(int steps) {
		altitudeSteps += steps;
	}
	
	public void setOrigin() {
		originAzimuth = 180.0;
		originAltitude = 0.0;
		
		azimuthSteps = 0;
		altitudeSteps = 0;
	}
	
/*	public void debugValues() {
		System.out.println("Values: " + originAltitude + " alt, " + originAzimuth + " az, " +
					altitudeSteps + " alt steps, " + azimuthSteps + "az steps");
	} */
	
	public double getRelativeAzimuth() {
		return originAzimuth + (360.0 * (double)azimuthSteps / (double)STEPS_PER_REVOLUTION);
	}
	
	public double getRelativeAltitude() {
		return originAltitude + (360.0 * (double)altitudeSteps / (double)STEPS_PER_REVOLUTION);
	}
}
