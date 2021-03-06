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

public class ObjectDirectionAltAz {
	private double altitude;
	private double azimuth;
	
	public double getAltitude() {
		return altitude;
	}

	public void setAltitude(double altitude) {
		this.altitude = altitude;
	}

	public double getAzimuth() {
		return azimuth;
	}

	public void setAzimuth(double azimuth) {
		this.azimuth = azimuth;
	}

	@Override
	public boolean equals(Object object) {
	    // self check
	    if (this == object)
	        return true;
	    // null check
	    if (object == null)
	        return false;
	    if (getClass() != object.getClass())
	        return false;
	    ObjectDirectionAltAz castObj = (ObjectDirectionAltAz)object;
	    
		if ((altitude != castObj.altitude) || (azimuth != castObj.azimuth))
			return false;
		
		return true;
	}
}
