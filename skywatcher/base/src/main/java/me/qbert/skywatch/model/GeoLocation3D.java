package me.qbert.skywatch.model;

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

public class GeoLocation3D extends GeoLocation {
	private double altitude = 0.0;
	private double diameter = 0.0;
	
	public double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
	}

	public double getDiameter() {
		return diameter;
	}

	public void setDiameter(double diameter) {
		this.diameter = diameter;
	}

	public void setGeoLocation(double latitude, double longitude, double altitude, double diameter) {
		if ((this.altitude != altitude) || (this.diameter != diameter)){
			this.altitude = altitude;
			this.diameter = diameter;
			super.setGeoLocation(latitude, longitude, true);
			settingsChanged();
		}
		else
			super.setGeoLocation(latitude, longitude);
	}
	
	protected void settingsChanged() {}
}
