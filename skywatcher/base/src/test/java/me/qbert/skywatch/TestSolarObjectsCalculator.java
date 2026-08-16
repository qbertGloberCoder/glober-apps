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
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.SolarObjects.SolarSystemCoordinate;
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

public class TestSolarObjectsCalculator {
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

	
	private String angleToDms(double angle) {
		int h = (int)(angle);
		angle -= h;
		angle *= 60;
		int m = (int)(angle);
		angle -= m;
		angle *= 60;
		return String.format("%02dd %02dm %02fs", h, m, angle);
	}
	
	private String angleToHms(double angle) {
		angle /= 15.0;
		
		int h = (int)(angle);
		angle -= h;
		angle *= 60;
		int m = (int)(angle);
		angle -= m;
		angle *= 60;
		return String.format("%02dh %02dm %02fs", h, m, angle);
	}
	
	private void testTransactional() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		CelestialObject solar = SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(solar);
		
//		expectedRa = (128.0 + (80+56.0/60.0+19/3600.0)+lon) % 360.0 - 128.0;	//  -9.061388888888885
//		expectedDec = 0.238;
		testLocation(transactionalListener, myLocation, time, solar, "Primary... where is the sun now? >> 'https://www.suncalc.org/#/45,-90,6/2024.03.20/12:31/1/3'",
				45, -90, 2024, 3, 20, 12, 31, 0, -5, -8.75671832284587, 0.10527969102980471);
		

		
		
//		expectedRa = (128.0 + (200+52.0/60.0+13/3600.0)+lon) % 360.0 - 128.0;	//  -9.129722222222199
//		expectedDec = -0.120;
		testLocation(transactionalListener, myLocation, time, solar, "Additional 1... where is the sun now? >> 'https://www.suncalc.org/#/-30,150,6/2025.03.20/12:31/1/3'",
				-30, 150, 2025, 3, 20, 12, 31, 0, 11, -8.812292912911573, -0.25954365263655343);
		
		
		
//		expectedRa = (128.0 + (19+52.52/60.0+24/3600.0)+lon) % 360.0 - 128.0;
//		expectedDec = 23.322;
		testLocation(transactionalListener, myLocation, time, solar, "Additional 2... where is the sun now? >> 'https://www.suncalc.org/#/0,25,6/2022.06.15/15:20/1/3'",
				0, 25, 2022, 6, 15, 15, 20, 0, 2, 45.208149443085716, 23.308939769438826);
		
	}
	
	private void testLocation(TransactionalStateChangeListener transactionalListener, ObserverLocation myLocation, ObservationTime time,
			CelestialObject celestialObject,
			String batchLabel,
			double lat, double lon, int year, int month, int day, int hour, int min, int sec, int timezoneOffset,
			double expectedRa, double expectedDec) throws UninitializedObject {
		System.out.println(batchLabel);

		transactionalListener.begin();

		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		time.addTime(-timezoneOffset, Calendar.HOUR);
		
		transactionalListener.commit();
		
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + "," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));

		ObjectDirectionRaDec direction = celestialObject.getCurrentDirection();
		System.out.println("	SOLAR = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("SOLAR RA is incorrect", "SOLR Dec is incorrect", direction, expectedRa, expectedDec);		
	}
	
	private void testMoon() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		CelestialObject solar = SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(solar);
		
		transactionalListener.begin();

		myLocation.setGeoLocation(0, 0);
		time.setLocalTime(2026, 4, 5, 21, 0, 43);
		time.addTime(5, Calendar.HOUR);
		
		transactionalListener.commit();
		
		Calendar cal = time.getTime();
		
		int year = cal.get(Calendar.YEAR);
		int month = cal.get(Calendar.MONTH) + 1;
		int day = cal.get(Calendar.DAY_OF_MONTH);
		
		SolarSystemCoordinate[] coords = ((SolarObjects)solar).getSolarSystemObjectCoordinates();
		
		System.out.print(year + "," + month + "," + day);
		for (SolarSystemCoordinate coord : coords) {
			System.out.print("," + coord.getX() + "," + coord.getY() + "," + coord.getZ());
		}
		System.out.print("\n");
		
	}
	
	private void computePlanetLocations() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		CelestialObject solar = SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(solar);
		
		transactionalListener.begin();

		myLocation.setGeoLocation(0, 0);
		time.setLocalTime(2001, 1, 1, 12, 0, 0);
		time.addTime(5, Calendar.HOUR);
		
		transactionalListener.commit();
		
		int loop = (int)(25.0*365.25);
		
		while (loop > 0) {
			Calendar cal = time.getTime();
			
			int year = cal.get(Calendar.YEAR);
			int month = cal.get(Calendar.MONTH) + 1;
			int day = cal.get(Calendar.DAY_OF_MONTH);
			
			SolarSystemCoordinate[] coords = ((SolarObjects)solar).getSolarSystemObjectCoordinates();
			
			System.out.print(year + "," + month + "," + day);
			for (SolarSystemCoordinate coord : coords) {
				System.out.print("," + coord.getX() + "," + coord.getY() + "," + coord.getZ());
			}
			System.out.print("\n");

			transactionalListener.begin();
			time.addTime(86400, Calendar.SECOND);
			transactionalListener.commit();
			
			loop --;
		}
	}
	
	public static void main(String[] args) throws UninitializedObject {
		Logger logger = LogManager.getLogger(TestSolarObjectsCalculator.class.getName());
		
		Configurator.setLevel("me.qbert.skywatch", Level.ERROR);
		
		logger.log(Level.ALL, "Test log here");
		
		TestSolarObjectsCalculator tester = new TestSolarObjectsCalculator();

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
