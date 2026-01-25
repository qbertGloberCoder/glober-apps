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

public class CelestialObjectProfile {
	private String profileName;
	private double siderealRotateSeconds;
	private double acceleration;
	private boolean withLocation = false;
	private double pendulumLength;
	private double swingRadius;
	private double latitude;

	public CelestialObjectProfile(String profileName, double siderealRotateSeconds, double acceleration) {
		this(profileName, siderealRotateSeconds, acceleration, false, 1.0, 0.8, 0.0);
	}
	
	public CelestialObjectProfile(String profileName, double siderealRotateSeconds, double acceleration,
			double pendulumLength, double swingRadius, double latitude) {
		this(profileName, siderealRotateSeconds, acceleration, true, pendulumLength, swingRadius, latitude);
	}
	
	private CelestialObjectProfile(String profileName, double siderealRotateSeconds, double acceleration,
			boolean withLocation, double pendulumLength, double swingRadius, double latitude
			) {
		this.profileName = profileName;
		this.siderealRotateSeconds = siderealRotateSeconds;
		this.acceleration = acceleration;
		this.withLocation = withLocation;
		this.pendulumLength = pendulumLength;
		this.swingRadius = swingRadius;
		this.latitude = latitude;
	}
	public String getProfileName() {
		return profileName;
	}
	public double getSiderealRotateSeconds() {
		return siderealRotateSeconds;
	}
	public double getAcceleration() {
		return acceleration;
	}

	public boolean isWithLocation() {
		return withLocation;
	}

	public double getPendulumLength() {
		return pendulumLength;
	}

	public double getSwingRadius() {
		return swingRadius;
	}

	public double getLatitude() {
		return latitude;
	}
}
