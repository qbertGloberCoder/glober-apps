package me.qbert.foucault.physics;

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

public class PrecessionRate {
	private Location location;
	private double omega;
	
	private double omegaX;
	private double omegaY;
	private double omegaZ;
	
	private boolean precessionActive = true;

	public PrecessionRate(Location location) {
		this(location, 0.0);
	}
	
	public PrecessionRate(Location location, double omega) {
		this.location = location;
		this.omega = omega;
		reinit();
	}
	
	public void setOmega(double omega) {
		this.omega = omega;
		reinit();
	}
	
	public double getOmega() {
		return omega;
	}
	
	public void reinit() {
		omegaX = 0.0;
		omegaY = omega * Math.cos(Math.toRadians(location.getLatitude()));
		omegaZ = omega * Math.sin(Math.toRadians(location.getLatitude()));
	}

	public boolean isPrecessionActive() {
		return precessionActive;
	}

	public void setPrecessionActive(boolean precessionActive) {
		this.precessionActive = precessionActive;
	}

	public double getOmegaX() {
		if (! precessionActive) return 0.0;
		
		return omegaX;
	}

	public double getOmegaY() {
		if (! precessionActive) return 0.0;
		
		return omegaY;
	}

	public double getOmegaZ() {
		if (! precessionActive) return 0.0;
		
		return omegaZ;
	}

}
