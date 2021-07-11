package me.qbert.skywatch;


import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.StarObjectImpl;
import me.qbert.skywatch.astro.impl.SunObjectImpl;
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
	private void test() throws UninitializedObject {
		CelestialAddress starOne = new CelestialAddress();
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getDefault());
		CelestialObject star = new StarObjectImpl().setStarLocation(starOne).setObserverLocation(myLocation).setObserverTime(time);
		CelestialObject sun = new SunObjectImpl().setObserverLocation(myLocation).setObserverTime(time);

		
		
		//computePosition for: lat=10.0, lon=20.0,starRa=5.0,starDec=30.0,2021-07-11 12:31:05
		//		 Sun = 86.37315977385742, 22.007390065626414
		//		Star = 192.57903308280873, 30.0		
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
		starOne.setRightAscension(starRa);
		starOne.setDeclination(starDec);
		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		ObjectDirectionRaDec direction = sun.getCurrentDirection();
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + ",starRa=" + starRa + ",starDec=" + starDec +
				"," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));
		System.out.println("	 Sun = " + direction.getRightAscension() + ", " + direction.getDeclination());
		direction = star.getCurrentDirection();
		System.out.println("	Star = " + direction.getRightAscension() + ", " + direction.getDeclination());
		
		
		System.out.println("Direction for location: lat=10, lon=20 star: ra=5, dec=30, 2021-07-11 12:31:05 -4 = " + direction.getRightAscension() + ", " + direction.getDeclination());
		
		//computePosition for: lat=-50.0, lon=-40.0,starRa=25.0,starDec=45.0,2020-07-11 12:31:05
		//		 Sun = 26.366119731594154, 21.973453759061698
		//		Star = 112.81889224944868, 45.0
		lat=-50;
		lon=-40;
		starRa=25;
		starDec=45;
		year=2020;
		starOne.setRightAscension(starRa);
		starOne.setDeclination(starDec);
		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		direction = sun.getCurrentDirection();
		System.out.println("computePosition for: lat=" + lat + ", lon=" + lon + ",starRa=" + starRa + ",starDec=" + starDec +
				"," + String.format("%04d-%02d-%02d %02d:%02d:%02d", year, month, day, hour, min, sec));
		System.out.println("	 Sun = " + direction.getRightAscension() + ", " + direction.getDeclination());
		direction = star.getCurrentDirection();
		System.out.println("	Star = " + direction.getRightAscension() + ", " + direction.getDeclination());
	}
	
	public static void main(String[] args) throws UninitializedObject {
		TestAstroCalculators tester = new TestAstroCalculators();
		tester.test();
	}
}
