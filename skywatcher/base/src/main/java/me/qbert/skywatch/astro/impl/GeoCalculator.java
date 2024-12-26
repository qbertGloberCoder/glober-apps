package me.qbert.skywatch.astro.impl;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
	private static final Logger logger = LogManager.getLogger(GeoCalculator.class.getName());
	
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

	/*************************************************************************************
	 * GLOBE GOES HERE
	 *************************************************************************************/
	// This uses the haversine function... and is the proper globe earth formula that works
	public static double getGreatCircleDistance(GeoLocation location1, GeoLocation location2) {
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
	
	public static double getGlobeBearing(GeoLocation location1, GeoLocation location2) {
		// =degrees(ATAN2(COS(radians(B2))*SIN(radians(E2))-SIN(radians(B2))*COS(radians(E2))*COS(radians(F2-C2)),SIN(radians(F2-C2))*COS(radians(E2))))
		// B lat 1
		// C lon 1
		// E lat 2
		// F lon 2
		
		double lat1 = location1.getLatitude();
		double lat2 = location2.getLatitude();
		double lon1 = location1.getLongitude();
		double lon2 = location2.getLongitude();
		
		return Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(lon2-lon1))*Math.cos(Math.toRadians(lat2)),
				Math.cos(Math.toRadians(lat1))*Math.sin(Math.toRadians(lat2))-Math.sin(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.cos(Math.toRadians(lon2-lon1))));	
	}
	
	public static GeoLocation getCoordinateFromLocationgAndBearing(ObserverLocation observerLocation, double distance, double bearing) {
		// =degrees(ASIN(SIN(radians(B2))*COS(Q2/6371) + COS(radians(B2))*SIN(Q2/6371)*COS(radians(S2))))
		// =C2+degrees(ATAN2(COS(Q2/6371)-SIN(radians(B2))*SIN(radians(E6)),SIN(radians(S2))*SIN(Q2/6371)*COS(radians(B2))))
		// Q distance
		// S bearing
		
		double lat = observerLocation.getLatitude();
		double lon = observerLocation.getLongitude();
		
		double lat2 = Math.toDegrees(Math.asin(Math.sin(Math.toRadians(lat))*Math.cos(distance/6371) + Math.cos(Math.toRadians(lat))*Math.sin(distance/6371)*Math.cos(Math.toRadians(bearing))));
		double lon2 = lon+Math.toDegrees(Math.atan2(Math.sin(Math.toRadians(bearing))*Math.sin(distance/6371)*Math.cos(Math.toRadians(lat)),
				Math.cos(distance/6371)-Math.sin(Math.toRadians(lat))*Math.sin(Math.toRadians(lat2))));
		
		// =-180+mod((I42+degrees(ATAN2(COS(J42/6371)-SIN(radians(H42))*SIN(radians(K42)),SIN(radians(G42))*SIN(J42/6371)*COS(radians(H42)))))+540,360)
		lon2 = (-180.0 + ((lon2 + 540.0) % 360.0));
		
		GeoLocation geoLocation = new GeoLocation();
		geoLocation.setGeoLocation(lat2, lon2);
		
		return geoLocation;
	}
	
	public static GeoLocation getGreatCircleIntermediatePosition(GeoLocation location1, GeoLocation location2, double percent) {
		double distance = getGreatCircleDistance(location1, location2);
		return getGreatCircleIntermediatePosition(location1, location2, distance, percent);
	}
	
	public static ArrayList<GeoLocation> divideGreatCircle(GeoLocation location1, GeoLocation location2, int segments) {
		ArrayList<GeoLocation> positions = new ArrayList<GeoLocation>();
		double distance = getGreatCircleDistance(location1, location2);
		for (int i = 0;i < (segments + 1);i ++) {
			GeoLocation latLon = getGreatCircleIntermediatePosition(location1, location2, distance, ((double)i / (double)(segments + 1)));
			positions.add(latLon);
		}
		return positions;
	}
	
	private static GeoLocation getGreatCircleIntermediatePosition(GeoLocation location1, GeoLocation location2, double distance, double percent) {
		/* Intermediate point

		An intermediate point at any fraction along the great circle path between two points can also be calculated.1
		Formula: 	a = sin((1−f)⋅δ) / sin δ
			b = sin(f⋅δ) / sin δ
			x = a ⋅ cos φ1 ⋅ cos λ1 + b ⋅ cos φ2 ⋅ cos λ2
			y = a ⋅ cos φ1 ⋅ sin λ1 + b ⋅ cos φ2 ⋅ sin λ2
			z = a ⋅ sin φ1 + b ⋅ sin φ2
			φi = atan2(z, √x² + y²)
			λi = atan2(y, x)
		where 	f is fraction along great circle route (f=0 is point 1, f=1 is point 2), δ is the angular distance d/R between the two points.
		...φ should be lat...
		*/

		double lat1 = location1.getLatitude();
		double lat2 = location2.getLatitude();
		double lon1 = location1.getLongitude();
		double lon2 = location2.getLongitude();

		double radius = 6371.0; // metres
		double sigma = distance/radius;
		double a = Math.sin((1-percent) * sigma) / Math.sin(sigma);
		double b = Math.sin(percent * sigma) / Math.sin(sigma);
		double x = a * Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lon1)) + b*Math.cos(Math.toRadians(lat2))*Math.cos(Math.toRadians(lon2));
		double y = a * Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lon1)) + b*Math.cos(Math.toRadians(lat2))*Math.sin(Math.toRadians(lon2));
		double z = a * Math.sin(Math.toRadians(lat1)) + b*Math.sin(Math.toRadians(lat2));
		double lati = Math.toDegrees(Math.atan2(z, Math.sqrt(x*x+y*y)));
		double loni = Math.toDegrees(Math.atan2(y, x));
		
		GeoLocation latLon = new GeoLocation();
		latLon.setLatitude(lati);
		latLon.setLongitude(loni);
	
		return latLon;
	}	
	
	/*************************************************************************************
	 * FLATOPIA GOES HERE
	 *************************************************************************************/
	// This is broken and nothing can be properly or consistently modeled on a flat earth.....
	// The earth is a globe. This should be the flat earth equivalent of getGreatCircleDistance()
	public static double getSillyFlatEarthDistance(GeoLocation observerLocation, GeoLocation objectLocation) {
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
	
	public static GeoLocation getSillyFlatEarthCoordinateFromLocationgAndBearing(ObserverLocation observerLocation, double distance, double bearing) {
		// First, each latitude is the radius of the circle. Compute latitude distance
		// in KM from north pole
		double objectRadius = (90.0 - observerLocation.getLatitude()) * FORTYFIVEDEGREES_DISTANCE / 45.0;

		
		double xObj = objectRadius * Math.cos(Math.toRadians(observerLocation.getLongitude()));
		double yObj = objectRadius * Math.sin(Math.toRadians(observerLocation.getLongitude()));

		xObj += (distance * Math.cos(Math.toRadians(bearing)));
		yObj += (distance * Math.sin(Math.toRadians(bearing)));

		
		double lat2 = 90-(Math.sqrt(xObj*xObj+yObj*yObj)*45.0/FORTYFIVEDEGREES_DISTANCE);
		double lon2 = Math.toDegrees(Math.atan2(yObj, xObj));
		
		lon2 = (-180.0 + ((lon2 + 540.0) % 360.0));
		
		GeoLocation geoLocation = new GeoLocation();
		geoLocation.setGeoLocation(lat2, lon2);
		
		return geoLocation;
	}

	public static ArrayList<GeoLocation> divideSillyFlatEarthRoute(GeoLocation location1, GeoLocation location2, int segments) {
		ArrayList<GeoLocation> positions = new ArrayList<GeoLocation>();

		double lat1 = location1.getLatitude();
		double lat2 = location2.getLatitude();
		double lon1 = location1.getLongitude();
		double lon2 = location2.getLongitude();

		double observerRadius = (90.0 - lat1) * FORTYFIVEDEGREES_DISTANCE / 45.0;
		double objectRadius = (90.0 - lat2) * FORTYFIVEDEGREES_DISTANCE / 45.0;

		// ORIENT meridian on positive X axis and zero Y. East hemisphere as positive Y
		double xObs = observerRadius * Math.cos(Math.toRadians(lon1));
		double yObs = observerRadius * Math.sin(Math.toRadians(lon1));

		double xObj = objectRadius * Math.cos(Math.toRadians(lon2));
		double yObj = objectRadius * Math.sin(Math.toRadians(lon2));
		
		double xDelta = (xObj - xObs) / (double)segments;
		double yDelta = (yObj - yObs) / (double)segments;
		
		for (int i = 0;i < (segments + 1);i ++) {
			GeoLocation latLon = new GeoLocation();
			
			double xpos = (xDelta*(double)i) + xObs;
			double ypos = (yDelta*(double)i) + yObs;
			
			latLon.setLongitude(Math.toDegrees(Math.atan2(ypos, xpos)));
			latLon.setLatitude((Math.sqrt(xpos * xpos + ypos*ypos)/FORTYFIVEDEGREES_DISTANCE*-45.0) + 90.0);
			
			positions.add(latLon);
		}
		return positions;
	} 
	
	
	/*************************************************************************************
	 * MORE GLOBE GOES HERE... COORDINATE TRANFORMATIONS
	 *************************************************************************************/
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
		logger.trace("RA/DEC (" + objectRaDec.getRightAscension() + "," + objectRaDec.getDeclination() + ") AND location (" +
				location.getLatitude() + "," + location.getLongitude() + ") TO ALT/AZ");
		double useLat = location.getLatitude();
		logger.trace("useLat >> " + useLat);
		while ((useLat > 89.999) && (useLat < 90.001))
			useLat -= 0.0001;
		logger.trace("(WHILE > 89.999 or < 90.001) >> " + useLat);
		while ((useLat < -89.999) && (useLat > -90.001))
			useLat += 0.0001;
		logger.trace("(WHILE < -89.999 or > -90.001) >> " + useLat);
		double latRad = Math.toRadians(useLat);
		logger.trace("radians(useLat) >> " + latRad);
		double raRad = Math.toRadians(objectRaDec.getRightAscension());
		logger.trace("radians(raRad) >> " + raRad);
		double decRad = Math.toRadians(objectRaDec.getDeclination());
		logger.trace("(radians(decRad)) >> " + decRad);

		double alt = Math.toDegrees(
				Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(raRad)));
		logger.trace("alt = (Math.toDegrees( Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(raRad)))) >> " + alt);
		
		double az = (Math.toDegrees(Math.atan2(-1 * Math.sin(raRad),
				Math.tan(Math.toRadians(objectRaDec.getDeclination())) * Math.cos(Math.toRadians(useLat))
						- Math.sin(Math.toRadians(useLat)) * Math.cos(raRad)))
				+ 360.0) % 360.0;
		logger.trace("az = (Math.toDegrees(Math.atan2(-1 * Math.sin(raRad), Math.tan(Math.toRadians(objectRaDec.getDeclination())) * Math.cos(Math.toRadians(useLat))- Math.sin(Math.toRadians(useLat)) * Math.cos(raRad))) + 360.0) % 360.0 >> " + az);

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
