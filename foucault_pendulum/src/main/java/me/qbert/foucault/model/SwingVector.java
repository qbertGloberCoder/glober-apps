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

public class SwingVector {
	private double radius;
	private double azimuth;
	private boolean lastUpdate;
	
	public double getRadius() {
		return radius;
	}
	public void setRadius(double radius) {
		this.radius = radius;
	}
	public double getAzimuth() {
		return azimuth;
	}
	public void setAzimuth(double azimuth) {
		this.azimuth = azimuth;
	}
	public boolean isLastUpdate() {
		return lastUpdate;
	}
	public void setLastUpdate(boolean lastUpdate) {
		this.lastUpdate = lastUpdate;
	}
	public SwingVector copy() {
		SwingVector newVector = new SwingVector();
		newVector.radius = radius;
		newVector.azimuth = azimuth;
		newVector.lastUpdate = lastUpdate;
		return newVector;
	}
}
