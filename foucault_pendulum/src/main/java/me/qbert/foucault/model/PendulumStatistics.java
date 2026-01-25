package me.qbert.foucault.model;

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

public class PendulumStatistics {
	private WeightPosition lastApexPosition = new WeightPosition();
	private double precessionRate;
	private double siderealTime;
	private SwingVector forwardApex = new SwingVector();
	private SwingVector forwardNadir = new SwingVector();
	private SwingVector returnApex = new SwingVector();
	private SwingVector returnNadir = new SwingVector();
	private double swingCorrection;
	private double simulationSeconds;

	public WeightPosition getLastApexPosition() {
		return lastApexPosition;
	}

	public void setLastApexPosition(WeightPosition lastApexPosition) {
		this.lastApexPosition = lastApexPosition;
	}

	public double getPrecessionRate() {
		return precessionRate;
	}

	public void setPrecessionRate(double precessionRate) {
		this.precessionRate = precessionRate;
	}

	public double getSiderealTime() {
		return siderealTime;
	}

	public void setSiderealTime(double siderealTime) {
		this.siderealTime = siderealTime;
	}

	public SwingVector getForwardApex() {
		return forwardApex;
	}

	public void setForwardApex(double radius, double azimuth) {
		forwardApex.setRadius(radius);
		forwardApex.setAzimuth(azimuth);
	}

	public SwingVector getForwardNadir() {
		return forwardNadir;
	}

	public void setForwardNadir(double radius, double azimuth) {
		forwardNadir.setRadius(radius);
		forwardNadir.setAzimuth(azimuth);
	}

	public SwingVector getReturnApex() {
		return returnApex;
	}

	public void setReturnApex(double radius, double azimuth) {
		returnApex.setRadius(radius);
		returnApex.setAzimuth(azimuth);
	}

	public SwingVector getReturnNadir() {
		return returnNadir;
	}

	public void setReturnNadir(double radius, double azimuth) {
		returnNadir.setRadius(radius);
		returnNadir.setAzimuth(azimuth);
	}

	public double getSwingCorrection() {
		return swingCorrection;
	}

	public void setSwingCorrection(double swingCorrection) {
		this.swingCorrection = swingCorrection;
	}
	
	public double getSimulationSeconds() {
		return simulationSeconds;
	}

	public void setSimulationSeconds(double simulationSeconds) {
		this.simulationSeconds = simulationSeconds;
	}

	public PendulumStatistics copy() {
		PendulumStatistics newCopy = new PendulumStatistics();
		
		newCopy.lastApexPosition = lastApexPosition.copy();
		newCopy.precessionRate = precessionRate;
		newCopy.siderealTime = siderealTime;
		newCopy.forwardApex = forwardApex.copy();
		newCopy.forwardNadir = forwardNadir.copy();
		newCopy.returnApex = returnApex.copy();
		newCopy.returnNadir = returnNadir.copy();
		newCopy.swingCorrection = swingCorrection;
		newCopy.simulationSeconds = simulationSeconds;
		
		return newCopy;
	}
}
