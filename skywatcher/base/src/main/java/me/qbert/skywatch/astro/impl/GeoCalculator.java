package me.qbert.skywatch.astro.impl;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
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

public class GeoCalculator {
	public class FlatEarthPosition {
		private ObjectDirectionRaDec gePosition;
		
		private double x;
		private double y;
		private double z;

		public ObjectDirectionRaDec getGePosition() {
			return gePosition;
		}

		public double getX() {
			return x;
		}

		public double getY() {
			return y;
		}

		public double getZ() {
			return z;
		}
	}

	
	public static final double FORTYFIVEDEGREES_DISTANCE = 5004.0;

	// This uses the haversine function... and is the proper globe earth formula that works
	public double getGreatCircleDistance(GeoLocation location1, GeoLocation location2) {
		double radiusMeters = 6371000; // metres
		double theta1 = location1.getLatitude() * Math.PI/180; // φ, λ in radians
		double theta2 = location2.getLatitude() * Math.PI/180;
		double deltaTheta = (location2.getLatitude()-location1.getLatitude()) * Math.PI/180;
		double deltaLamda = (location2.getLongitude()-location1.getLongitude()) * Math.PI/180;

		double a = Math.sin(deltaTheta/2) * Math.sin(deltaTheta/2) +
		          Math.cos(theta1) * Math.cos(theta2) *
		          Math.sin(deltaLamda/2) * Math.sin(deltaLamda/2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

		double d = radiusMeters * c; // in metres
		
		return d / 1000.0;
	}
	
	// This is broken and nothing can be properly or consistently modeled on a flat earth.....
	// The earth is a globe. This should be the flat earth equivalent of getGreatCircleDistance()
	public double getSillyFlatEarthDistance(GeoLocation observerLocation, GeoLocation objectLocation) {
		// First, each latitude is the radius of the circle. Compute latitude distance
		// in KM from north pole
		double observerRadius = (90.0 - observerLocation.getLatitude()) * FORTYFIVEDEGREES_DISTANCE / 45.0;
		double objectRadius = (90.0 - objectLocation.getLatitude()) * FORTYFIVEDEGREES_DISTANCE / 45.0;

		// ORIENT meridian on positive X axis and zero Y. East hemisphere as positive Y
		double xObs = observerRadius * Math.cos(Math.toRadians(observerLocation.getLongitude()));
		double yObs = observerRadius * Math.sin(Math.toRadians(observerLocation.getLongitude()));

		double xObj = objectRadius * Math.cos(Math.toRadians(objectLocation.getLongitude()));
		double yObj = objectRadius * Math.sin(Math.toRadians(objectLocation.getLongitude()));

		double xDelta = xObj - xObs;
		double yDelta = yObj - yObs;

		return Math.sqrt(xDelta*xDelta + yDelta*yDelta);
	}
	
	public FlatEarthPosition getSillyFlatEarthDirection(ObjectDirectionAltAz object, ObserverLocation observerLocation) {
		// First, each latitude is the radius of the circle. Compute latitude distance
		// in KM from north pole
		double observerRadius = (90.0 - observerLocation.getLatitude()) * FORTYFIVEDEGREES_DISTANCE / 45.0;

		// ORIENT meridian on positive X axis and zero Y. East hemisphere as positive Y
		double xObs = observerRadius * Math.cos(Math.toRadians(observerLocation.getLongitude()));
		double yObs = observerRadius * Math.sin(Math.toRadians(observerLocation.getLongitude()));
		double zObs = 0.0;
		
		double flerfHorizontal = 0.0;
		if (object.getAltitude() != 0.0)
			flerfHorizontal = FORTYFIVEDEGREES_DISTANCE / Math.tan(Math.toRadians(object.getAltitude()));

		double xObj = (flerfHorizontal * Math.cos(Math.toRadians(object.getAltitude()))) * Math.cos(Math.toRadians(1260 - object.getAzimuth() + observerLocation.getLongitude()));
		double yObj = (flerfHorizontal * Math.cos(Math.toRadians(object.getAltitude()))) * Math.sin(Math.toRadians(1260 - object.getAzimuth() + observerLocation.getLongitude()));
		double zObj = (FORTYFIVEDEGREES_DISTANCE * Math.sin(Math.toRadians(object.getAltitude())));
		
		FlatEarthPosition fePosition = new FlatEarthPosition();
		fePosition.gePosition = altAzToRaDec(object, observerLocation);
		fePosition.x = xObs + xObj;
		fePosition.y = 0.0 - yObs - yObj;
		fePosition.z = zObj;
		
		return fePosition;
	}

	
	public ObjectDirectionRaDec altAzToRaDec(ObjectDirectionAltAz object, ObserverLocation location) {
		// not so easy to convert local back to LRA and dec given formulas for the other
		// have multiple variables to compute one of each
		// taking a slightly different approach for this one...
		// It does seem to work... and it might be slow and inefficient

		double objUseAz = fixDegrees(180.0 + object.getAzimuth());
		double objUseAlt = object.getAltitude();

		if ((objUseAz < -90.0) || (objUseAz > 90.0)) {
			objUseAz = fixDegrees(object.getAzimuth());
			objUseAlt = 180.0 - object.getAltitude();
		}

		// north-south is X
		// east-west is Y
		// up-down is Z
		// rotating on Z south to west to north to east to south is 0 to 360
		double x1 = Math.cos(Math.toRadians(objUseAz)) * Math.cos(Math.toRadians(objUseAlt));
		double y1 = Math.sin(Math.toRadians(objUseAz)) * Math.cos(Math.toRadians(objUseAlt));
		double z1 = Math.sin(Math.toRadians(objUseAlt));

		double obsUseAz = 0;
		double obsUseAlt = location.getLatitude();

		obsUseAlt = obsUseAlt * Math.cos(Math.toRadians(obsUseAz));

		// We want observer zero to be due south NOT WORKING: (observerAzimuth - 180)
		double x2 = Math.cos(Math.toRadians(obsUseAz)) * Math.cos(Math.toRadians(obsUseAlt));
		double y2 = Math.sin(Math.toRadians(obsUseAz)) * Math.cos(Math.toRadians(obsUseAlt));
		double z2 = Math.sin(Math.toRadians(obsUseAlt));

		double objAxisAngle = Math.toDegrees(Math.atan2(z1, x1));
		double obsAxisAngle = Math.toDegrees(Math.atan2(z2, x2));

		double tx = Math.sqrt(x1 * x1 + z1 * z1) * Math.cos(Math.toRadians(objAxisAngle + obsAxisAngle));
		double tz = Math.sqrt(x1 * x1 + z1 * z1) * Math.sin(Math.toRadians(objAxisAngle + obsAxisAngle));
		x1 = tx;
		z1 = tz;

		double declination = Math.toDegrees(Math.atan2(Math.sqrt(y1 * y1 + z1 * z1), x1)) - 90.0;
		double lra = Math.toDegrees(Math.atan2(y1, z1));

		ObjectDirectionRaDec direction = new ObjectDirectionRaDec();
		direction.setRightAscension(lra);
		direction.setDeclination(declination);

		return direction;
	}

	
	public ObjectDirectionAltAz raDeclinationToAltitudeAzimuth(ObjectDirectionRaDec objectRaDec, ObserverLocation location) {
		double useLat = location.getLatitude();
		while ((useLat > 89.999) && (useLat < 90.001))
			useLat -= 0.0001;
		while ((useLat < -89.999) && (useLat > -90.001))
			useLat += 0.0001;
		double latRad = Math.toRadians(useLat);
		double raRad = Math.toRadians(objectRaDec.getRightAscension());
		double decRad = Math.toRadians(objectRaDec.getDeclination());

		double alt = Math.toDegrees(
				Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(raRad)));
		double az = (Math.toDegrees(Math.atan2(-1 * Math.sin(raRad),
				Math.tan(Math.toRadians(objectRaDec.getDeclination())) * Math.cos(Math.toRadians(useLat))
						- Math.sin(Math.toRadians(useLat)) * Math.cos(raRad)))
				+ 360.0) % 360.0;

		ObjectDirectionAltAz coord = new ObjectDirectionAltAz();
		coord.setAltitude(alt);
		coord.setAzimuth(az);

		return coord;
	}
	
	private double fixDegrees(double degrees) {
		while (degrees >= 360.0)
			degrees -= 360.0;
		while (degrees <= -360.0)
			degrees += 360.0;
		return degrees;
	}
}
