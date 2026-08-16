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

public class TestSolarAltitudeCalculator {
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
		
		double lat=45;
		double lon=-75;
		
		int year = 2024;
		int month = 10;
		int day = 15;
		int hour = 11;
		int min = 35;
		int sec = 0;
		
		hour += 5;
		
		transactionalListener.begin();

		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		
		transactionalListener.commit();

		ObjectDirectionRaDec direction = solar.getCurrentDirection();
		ObjectDirectionAltAz altAz = solar.getCurrentDirectionAsAltitudeAzimuth();

		while (altAz.getAzimuth() < 180.0) {
			transactionalListener.begin();

			time.addTime(1);
			
			transactionalListener.commit();
			
			direction = solar.getCurrentDirection();
			altAz = solar.getCurrentDirectionAsAltitudeAzimuth();
		}
		Calendar cal = time.getTime();
		
		year = cal.get(Calendar.YEAR);
		month = cal.get(Calendar.MONTH) + 1;
		day = cal.get(Calendar.DAY_OF_MONTH);
		hour = cal.get(Calendar.HOUR_OF_DAY);
		min = cal.get(Calendar.MINUTE);
		sec = cal.get(Calendar.SECOND);
		
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + "," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));
		System.out.println("    SOLAR = " + direction.getRightAscension() + ", " + direction.getDeclination());
		System.out.println("   ALT/AZ = " + altAz.getAltitude() + ", " + altAz.getAzimuth());

		for (lat = -5; lat < 70; lat += 1.0) {
			transactionalListener.begin();

			myLocation.setGeoLocation(lat, lon);
			
			transactionalListener.commit();
			
			direction = solar.getCurrentDirection();
			altAz = solar.getCurrentDirectionAsAltitudeAzimuth();

			System.out.println(String.format("%04d-%02d-%02d,%02d:%02d:%02d", year, month, day, hour, min, sec) +
					"," + lat +
					"," + lon +
					"," + direction.getRightAscension() +
					"," + direction.getDeclination() +
					"," + altAz.getAltitude() +
					"," + altAz.getAzimuth()
					);
		}
		
	}
	
	public static void main(String[] args) throws UninitializedObject {
		Logger logger = LogManager.getLogger(TestSolarAltitudeCalculator.class.getName());
		
		Configurator.setLevel("me.qbert.skywatch", Level.WARN);
		
		logger.log(Level.ALL, "Test log here");
		
		TestSolarAltitudeCalculator tester = new TestSolarAltitudeCalculator();

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

		tester.testTransactional();
	}
}
