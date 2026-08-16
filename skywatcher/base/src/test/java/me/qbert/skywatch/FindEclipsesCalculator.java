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
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
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

public class FindEclipsesCalculator {
	private void findTransits() throws UninitializedObject {
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getDefault());
		CelestialObject sun = SunObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		CelestialObject moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		CelestialObject solar = SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();

		transactionalListener.addListener(solar);
		transactionalListener.addListener(sun);
		transactionalListener.addListener(moon);
		
		//computePosition for: lat=10.0, lon=20.0,starRa=5.0,starDec=30.0,2021-07-11 12:31:05
		//		 Sun = 86.37315977385742, 22.007390065626414
		//		Moon = 65.4295572326447, 22.378638038111593
		//		Star(+0.0, +0.0) = 192.57903308280873, 30.0
		//		Star(+4.0, -6.0) = 188.57903308280873, 24.0
		double lat = 0;
		double lon = 0;
		int year = 2000;
		int month = 3;
		int day = 1;
		int hour = 12;
		int min = 0;
		int sec = 0;
		
		transactionalListener.begin();
		myLocation.setGeoLocation(lat, lon);
		time.setLocalTime(year, month, day, hour, min, sec);
		transactionalListener.commit();
		
		transactionalListener.begin();
		time.addTime(300);
		transactionalListener.commit();
		
		double lastMoonRaDelta = 0;
		double lastMoonDecDelta = 0;
		boolean initialized = false;
		
		while (year < 2030) {
			Calendar cal = time.getTime();
			year = cal.get(Calendar.YEAR);
			month = cal.get(Calendar.MONTH) + 1;
			day = cal.get(Calendar.DAY_OF_MONTH);
			hour = cal.get(Calendar.HOUR_OF_DAY);
			min = cal.get(Calendar.MINUTE);
			sec = cal.get(Calendar.SECOND);
			
			ObjectDirectionRaDec sunDirection;
			ObjectDirectionRaDec moonDirection;
			sunDirection = sun.getCurrentDirection();
			moonDirection = moon.getCurrentDirection();
			
			double newMoonRaDelta = sunDirection.getRightAscension() - moonDirection.getRightAscension();
			double newMoonDecDelta = sunDirection.getDeclination() - moonDirection.getDeclination();
			
			boolean useRa = true;
			
			if (initialized) {
				if (useRa) {
					if (((lastMoonRaDelta < 0.0) && (newMoonRaDelta > 0.0)) ||
							((lastMoonRaDelta > 0.0) && (newMoonRaDelta < 0.0))) {
						System.out.println(year + "," + month + "," + day + "," + hour + "," + min + "," + sec + "," + sunDirection.getRightAscension() +
								"," + sunDirection.getDeclination() + "," + moonDirection.getRightAscension() +
								"," + moonDirection.getDeclination());
					}
				} else {
					if (((lastMoonDecDelta < 0.0) && (newMoonDecDelta > 0.0)) ||
							((lastMoonDecDelta > 0.0) && (newMoonDecDelta < 0.0))) {
						System.out.println(year + "," + month + "," + day + "," + hour + "," + min + "," + sec + "," + sunDirection.getRightAscension() +
								"," + sunDirection.getDeclination() + "," + moonDirection.getRightAscension() +
								"," + moonDirection.getDeclination());
					}
				}
			} else {
				initialized = true;
			}

			lastMoonRaDelta = newMoonRaDelta;
			lastMoonDecDelta = newMoonDecDelta;
			
			while (newMoonRaDelta < 0.0)
				newMoonRaDelta += 360.0;
			
			int advanceSeconds;
			
			if ((newMoonRaDelta > 5.0) && (newMoonRaDelta < 340.0)) {
				advanceSeconds = (int)(86400.0 * (360.0 - newMoonRaDelta) / 12.5);
			} else if ((newMoonRaDelta < 1.0) || (newMoonRaDelta > 355.0)) {
				advanceSeconds = 30;
			} else {
				advanceSeconds = 300;
			}
			
			transactionalListener.begin();
			time.addTime(advanceSeconds);
			transactionalListener.commit();
		}		
	}
	
	public static void main(String[] args) throws UninitializedObject {
		Logger logger = LogManager.getLogger(FindEclipsesCalculator.class.getName());
		
		Configurator.setLevel("me.qbert.skywatch", Level.DEBUG);
		
		logger.log(Level.ALL, "Test log here");
		
		FindEclipsesCalculator tester = new FindEclipsesCalculator();

		tester.findTransits();
	}
}
