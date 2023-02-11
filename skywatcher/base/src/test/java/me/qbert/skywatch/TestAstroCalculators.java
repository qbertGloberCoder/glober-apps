package me.qbert.skywatch;


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
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.CelestialAddress;
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

public class TestAstroCalculators {
	private void assertForTest(String failMessage, double computed, double expected) throws AssertionFailedError {
		Assert.assertTrue(failMessage, (computed == expected ? true : false));
	}
	
	private void assertForTest(String failMessageRa, String failMessageDec, ObjectDirectionRaDec computedDirection, double expectedRa, double expectedDec) throws AssertionFailedError {
		assertForTest(failMessageRa, computedDirection.getRightAscension(), expectedRa);
		assertForTest(failMessageDec, computedDirection.getDeclination(), expectedDec);
	}
	
	private void testTransactional() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		CelestialAddress starOne = new CelestialAddress();
		CelestialAddress starTwo = new CelestialAddress();
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getDefault());
		CelestialObject starObjOne = StarObject.create().setStarLocation(starOne).setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		CelestialObject starObjTwo = StarObject.create().setStarLocation(starTwo).setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		CelestialObject sun = SunObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(sun);
		transactionalListener.addListener(starObjOne);
		transactionalListener.addListener(starObjTwo);
		
		//computePosition for: lat=10.0, lon=20.0,starRa=5.0,starDec=30.0,2021-07-11 12:31:05
		//		 Sun = 86.37315977385742, 22.007390065626414
		//		Star(+0.0, +0.0) = 192.57903308280873, 30.0
		//		Star(+4.0, -6.0) = 188.57903308280873, 24.0
		double lat = 10;
		double lon = 20;
		double starRa = 5;
		double starDec = 30;
		int year = 2021;
		int month = 7;
		int day = 11;
		int hour = 12;
		int min = 31;
		int sec = 5;
		transactionalListener.begin();
		
		starOne.setRightAscension(starRa);
		starOne.setDeclination(starDec);
		starTwo.setRightAscension(starRa + 4.0);
		starTwo.setDeclination(starDec - 6.0);
		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		
		transactionalListener.commit();
		
		ObjectDirectionRaDec direction = sun.getCurrentDirection();
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + ",starRa=" + starRa + ",starDec=" + starDec +
				"," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));
		System.out.println("	 Sun = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("SUN RA is incorrect", "SUN Dec is incorrect", direction, 86.37315977385742, 22.007390065626414);
		direction = starObjOne.getCurrentDirection();
		System.out.println("	Star(+0.0, +0.0) = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("Star 1 RA is incorrect", "Star 1 Dec is incorrect", direction, 192.57903308280873, 30.0);
		direction = starObjTwo.getCurrentDirection();
		System.out.println("	Star(+4.0, -6.0) = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("Star 2 RA is incorrect", "Star 2 Dec is incorrect", direction, 188.57903308280873, 24.0);
		
		
		System.out.println("Direction for location: lat=10, lon=20 star: ra=5, dec=30, 2021-07-11 12:31:05 -4 = " + direction.getRightAscension() + ", " + direction.getDeclination());
		
		//computePosition for: lat=-50.0, lon=-40.0,starRa=25.0,starDec=45.0,2020-07-11 12:31:05
		//		 Sun = 26.366119731594154, 21.973453759061698
		//		Star(+0.0, +0.0) = 112.81889224944868, 45.0
		//		Star(+4.0, -6.0) = 108.81889224944868, 39.0
		lat=-50;
		lon=-40;
		starRa=25;
		starDec=45;
		year=2020;

		transactionalListener.begin();

		starOne.setRightAscension(starRa);
		starOne.setDeclination(starDec);
		starTwo.setRightAscension(starRa + 4.0);
		starTwo.setDeclination(starDec - 6.0);
		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		direction = sun.getCurrentDirection();
		
		transactionalListener.commit();
		
		direction = sun.getCurrentDirection();
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + ",starRa=" + starRa + ",starDec=" + starDec +
				"," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));
		System.out.println("	 Sun = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("SUN RA is incorrect", "SUN Dec is incorrect", direction, 26.366119731594154, 21.973453759061698);
		direction = starObjOne.getCurrentDirection();
		System.out.println("	Star(+0.0, +0.0) = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("Star 1 RA is incorrect", "Star 1 Dec is incorrect", direction, 112.81889224944868, 45.0);
		direction = starObjTwo.getCurrentDirection();
		System.out.println("	Star(+4.0, -6.0) = " + direction.getRightAscension() + ", " + direction.getDeclination());
		assertForTest("Star 2 RA is incorrect", "Star 2 Dec is incorrect", direction, 108.81889224944868, 39.0);
	}
	
	public static void main(String[] args) throws UninitializedObject {
		Logger logger = LogManager.getLogger(TestAstroCalculators.class.getName());
		
		Configurator.setLevel("me.qbert.skywatch", Level.TRACE);
		
		logger.log(Level.ALL, "Test log here");

		TestAstroCalculators tester = new TestAstroCalculators();
		tester.testTransactional();
	}
}
