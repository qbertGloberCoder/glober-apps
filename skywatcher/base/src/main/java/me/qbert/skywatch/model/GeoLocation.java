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

public class GeoLocation {
	private double latitude = 0.0;
	private double longitude = 0.0;
	
	public double getLatitude() {
		return latitude;
	}
	
	public void setLatitude(double latitude) {
		if (this.latitude != latitude) {
			this.latitude = latitude;
			settingsChanged();
		}
	}
	
	public double getLongitude() {
		return longitude;
	}
	
	public void setLongitude(double longitude) {
		if (this.longitude != longitude) {
			this.longitude = longitude;
			settingsChanged();
		}
	}
	
	public void setGeoLocation(double latitude, double longitude) {
		if ((this.latitude != latitude) || (this.longitude != longitude)) {
			this.latitude = latitude;
			this.longitude = longitude;
			settingsChanged();
		}
		
	}
	
	protected void settingsChanged() {}
}
