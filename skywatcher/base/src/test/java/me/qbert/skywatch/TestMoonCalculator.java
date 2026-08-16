package me.qbert.skywatch;


import java.util.Calendar;
import java.util.TimeZone;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.CelestialAddress;
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

public class TestMoonCalculator {
	private double myTestLatitude;
	private double myTestLongitude;
	
	private void assertForTest(String failMessage, double computed, double expected) throws AssertionFailedError {
		Assert.assertTrue(failMessage + "(" + computed + " != " + expected + ")", (computed == expected ? true : false));
	}
	
	private void assertForTest(String failMessageRa, String failMessageDec, ObjectDirectionRaDec computedDirection, double expectedRa, double expectedDec) throws AssertionFailedError {
		assertForTest(failMessageRa, computedDirection.getRightAscension(), expectedRa);
		assertForTest(failMessageDec, computedDirection.getDeclination(), expectedDec);
	}
	
	private ObjectDirectionAltAz raDeclinationToAltitudeAzimuth(double rightAscension, double declination,
			double latitude, double longitude) {
		double useLat = latitude;
		while ((useLat > 89.999) && (useLat < 90.001))
			useLat -= 0.0001;
		while ((useLat < -89.999) && (useLat > -90.001))
			useLat += 0.0001;
		double latRad = Math.toRadians(useLat);
		double raRad = Math.toRadians(rightAscension);
		double decRad = Math.toRadians(declination);

		double alt = Math.toDegrees(
				Math.asin(Math.sin(latRad) * Math.sin(decRad) + Math.cos(latRad) * Math.cos(decRad) * Math.cos(raRad)));
		double az = (Math.toDegrees(Math.atan2(-1 * Math.sin(raRad),
				Math.tan(Math.toRadians(declination)) * Math.cos(Math.toRadians(useLat))
						- Math.sin(Math.toRadians(useLat)) * Math.cos(raRad)))
				+ 360.0) % 360.0;

		ObjectDirectionAltAz coord = new ObjectDirectionAltAz();
		coord.setAltitude(alt);
		coord.setAzimuth(az);

		return coord;
	}

	
	private void testMoon() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		CelestialObject moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(moon);
		
		transactionalListener.begin();

		myLocation.setGeoLocation(0, 0);
		time.setLocalTime(2026, 4, 5, 21, 0, 43);
		time.addTime(5, Calendar.HOUR);
		
		transactionalListener.commit();
		
		Calendar cal = time.getTime();
		
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		
		System.out.print(year + "," + month + "," + day);
		System.out.print("," + ((MoonObject)moon).getX() + "," + ((MoonObject)moon).getY() + "," + ((MoonObject)moon).getZ() + ", R=" + ((MoonObject)moon).getR());
		System.out.print("\n");

		double distance = 406194.67;
		double ra = 225.54138888888888888888;//15h32m29s;
		double dec = -24.33499999999999999999;//-24d20m06s;
		
		transactionalListener.begin();
		
		myLocation.setGeoLocation(0, 0);
		time.setLocalTime(2026, 4, 5, 12, 57, 0);
		time.addTime(5, Calendar.HOUR);
		
		transactionalListener.commit();
		
		cal = time.getTime();
		
		year = cal.get(Calendar.YEAR);
		month = cal.get(Calendar.MONTH) + 1;
		day = cal.get(Calendar.DAY_OF_MONTH);
		
		ObjectDirectionRaDec location = ((MoonObject)moon).getCelestialSphereLocation();
		double x = ((MoonObject)moon).getX();
		double y = ((MoonObject)moon).getY();
		double z = ((MoonObject)moon).getZ();
		
		double cr = Math.sqrt(x*x+y*y+z*z);
		
		x *= (distance/cr);
		y *= (distance/cr);
		z *= (distance/cr);
		
		System.out.print(year + "," + month + "," + day);
		System.out.print("," + location.getRightAscension() + "," + location.getDeclination());
		System.out.print("," + x + "," + y + "," + z + ", R=" + ((MoonObject)moon).getR() + ", >> " + cr);
		System.out.print("\n");
		
	}
	
	public static void main(String[] args) throws UninitializedObject {
		Logger logger = LogManager.getLogger(TestMoonCalculator.class.getName());
		
		Configurator.setLevel("me.qbert.skywatch", Level.ERROR);
		
		logger.log(Level.ALL, "Test log here");
		
		TestMoonCalculator tester = new TestMoonCalculator();

		if (args.length == 2) {
			try {
				Double d = new Double(args[0]);
				tester.myTestLatitude = d.doubleValue();
				d = new Double(args[1]);
				tester.myTestLongitude = d.doubleValue();
			} catch (NumberFormatException e) {
				logger.error("Can't set the test geo-coordinates: " + e.getMessage());
				tester.myTestLatitude = 0.0;
				tester.myTestLongitude = 0.0;
			}
			
		}

//		tester.testTransactional();
//		tester.computePlanetLocations();
		tester.testMoon();
	}
}
