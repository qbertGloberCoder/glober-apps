package me.qbert.skywatch.astro.impl;

import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

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

public class StarObjectImpl extends AbstractCelestialObjectImpl {
	private CelestialAddress starLocation = null;
	
	private double hourAngle;
	
	public CelestialAddress getStarLocation() {
		return starLocation;
	}

	public StarObjectImpl setStarLocation(CelestialAddress starLocation) {
		this.starLocation = starLocation;
		return this;
	}

	@Override
	protected boolean isInitialized() {
		if (!super.isInitialized())
			return false;
		
		if (starLocation == null)
			return false;
		
		return true;
	}
	/*
	 Taken from something I can't remember where I got it from
	 */
	@Override
	public void recompute() {
		if (! isInitialized())
			return;
		
		// B13
		double julianDate = observationTime.getJulianDate();
		double julianCentury = observationTime.getJulianCentury();
		// B14
		double days = julianDate-2451545;
		// B15
		double gmst = modulus(18.697374558+24.065709824419*days,24);
		// B16
		double omega = 125.04-0.052954*julianDate;
		// B17
		double l = 280.47+0.98565*julianDate;
		// B18
		double e = 23.4393-0.0000004*julianDate;
		// B19
		double deltaW = -0.000319*Math.sin(Math.toRadians(omega))-0.000024*Math.sin(Math.toRadians(2*l));
		// B20
		double eqEq = deltaW*Math.cos(Math.toRadians(e));
		// B21
		double gast = gmst+eqEq;
		
		hourAngle = ((gast*15)-starLocation.getRightAscension())+location.getLongitude();
		
		// Lat B3
		// Long B4
		// TZ = B5
		// Date = b10
		// time = b11
	}

	@Override
	public ObjectDirectionRaDec getCurrentDirection() throws UninitializedObject {
		if (! isInitialized())
			throw new UninitializedObject();
		
		return makeRaDec(hourAngle, starLocation.getDeclination());
	}

	@Override
	public GeoLocation getEarthPositionOverhead() throws UninitializedObject {
		if (! isInitialized())
			throw new UninitializedObject();

		return makeGeoLocation(starLocation.getDeclination(), location.getLongitude() - hourAngle);
	}
}
