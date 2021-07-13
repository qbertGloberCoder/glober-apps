package me.qbert.skywatch.astro.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import me.qbert.skywatch.astro.CelestialObject;
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
	private static final Logger logger = LogManager.getLogger(StarObjectImpl.class.getName());
	
	public class StarObjectBuilder extends AbstractCelestialObjectBuilder {
		@Override
		protected StarObjectImpl getInstance() {
			return new StarObjectImpl();
		}
		
		public StarObjectBuilder setStarLocation(CelestialAddress starLocationObject) {
			starLocation = starLocationObject;
			return this;
		}

		@Override
		public CelestialObject build() throws UninitializedObject {
			if (starLocation == null)
				throw new UninitializedObject();
			
			StarObjectImpl instance = (StarObjectImpl) super.build();
			
			instance.starLocation = starLocation;
			
			return instance;
		}
	}
	
	private CelestialAddress starLocation = null;
	
	private static double julianDate;
	private static double gast;
	private double hourAngle;

	// Not entirely happy with this design
	private StarObjectImpl() {
	}

	private StarObjectBuilder createBuilder() {
		return new StarObjectBuilder();
	}
	
	public static StarObjectBuilder create() {
		return new StarObjectImpl().createBuilder();
	}
	
	/*
	 Taken from something I can't remember where I got it from
	 */
	@Override
	public void recompute() {
		// B13
		double newJulianDate = observationTime.getJulianDate();
		
		logger.trace("Recompute StarObject: " + starLocation.getRightAscension() + " ra, " + starLocation.getDeclination() + " dec");
		
		if (newJulianDate != julianDate) {
			logger.trace("          for julian date: " + newJulianDate);

			julianDate = newJulianDate;
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
			gast = gmst+eqEq;

			logger.trace("          and gast: " + gast);
		}
		
		hourAngle = ((gast*15)-starLocation.getRightAscension())+location.getLongitude();
		
		// Lat B3
		// Long B4
		// TZ = B5
		// Date = b10
		// time = b11
	}

	@Override
	public ObjectDirectionRaDec getCurrentDirection() {
		return makeRaDec(hourAngle, starLocation.getDeclination());
	}

	@Override
	public GeoLocation getEarthPositionOverhead() {
		return makeGeoLocation(starLocation.getDeclination(), location.getLongitude() - hourAngle);
	}
}
