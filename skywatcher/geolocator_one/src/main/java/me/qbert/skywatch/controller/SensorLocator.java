package me.qbert.skywatch.controller;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.exception.UninitializedObject;
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

public class SensorLocator {
	public class CurrentLocation {
		private ObjectDirectionAltAz altAz;
		private ObjectDirectionAltAz pointingAtAltAz;
		
		private double sunXPct;
		private double sunYPct;
		
		private double visibleXPct;
		private double visibleYPct;

		public ObjectDirectionAltAz getAltAz() {
			return altAz;
		}
		
		public ObjectDirectionAltAz getPointingAtAltAz() {
			return pointingAtAltAz;
		}

		public double getSunXPct() {
			return sunXPct;
		}
		
		public double getSunYPct() {
			return sunYPct;
		}
		
		public double getVisibleXPct() {
			return visibleXPct;
		}
		
		public double getVisibleYPct() {
			return visibleYPct;
		}
	}
	
	private LightSensor sensor;
	private CelestialObject sun;
	private ObservationTime time;
	
	private double sunOriginAltitude = 0.0;
	private double sunOriginAzimuth = 0.0;
	
	private boolean pointingToSun = false;
	
	public SensorLocator(CelestialObject sun, ObservationTime time) {
		sensor = new LightSensor();
		
		this.sun = sun;
		this.time = time;
	}
	
	public void reset() {
		pointingToSun = false;
		sunOriginAltitude = 0.0;
		sunOriginAzimuth = 0.0;
	}
	
	public boolean isPointingToSun() {
		return pointingToSun;
	}
	
/*	public void debugValues() {
		sensor.debugValues();
	} */
	
	// TODO: make this a bit more efficient
	private void updateSensorInfo(CurrentLocation location) {
		location.pointingAtAltAz.setAzimuth((sensor.getRelativeAzimuth() + 180.0) % 360.0);
		location.pointingAtAltAz.setAltitude((sensor.getRelativeAltitude()) % 360.0);

		double calcAz = (180.0 + sunOriginAzimuth - location.altAz.getAzimuth() + location.pointingAtAltAz.getAzimuth()) % 360.0;
		double calcAlt = sunOriginAltitude - location.altAz.getAltitude() + location.pointingAtAltAz.getAltitude();
		
		location.visibleXPct = (((calcAz + 360.0) % 360.0) / 360.0);
		location.visibleYPct = (((540.0 - calcAlt ) % 360.0) / 360.0);
		
		while (location.pointingAtAltAz.getAzimuth() > 180.0)
			location.pointingAtAltAz.setAzimuth(location.pointingAtAltAz.getAzimuth() - 360.0);
		while (location.pointingAtAltAz.getAzimuth() <- 180.0)
			location.pointingAtAltAz.setAzimuth(location.pointingAtAltAz.getAzimuth() + 360.0);
	}
	
	public void stepAzimuth(CurrentLocation location, int steps) {
		sensor.stepAzimuth(steps);
		updateSensorInfo(location);
	}
	
	public void stepAltitude(CurrentLocation location, int steps) {
		sensor.stepAltitude(steps);
		updateSensorInfo(location);
	}
	
	public CurrentLocation seekSun() throws UninitializedObject {
		time.setCurrentTime();
		
		CurrentLocation location = new CurrentLocation();
		
		ObjectDirectionRaDec direction = sun.getCurrentDirection();
		location.altAz = sun.getCurrentDirectionAsAltitudeAzimuth(direction);

		location.sunXPct = (((location.altAz.getAzimuth() + 360.0) % 360.0) / 360.0);
		location.sunYPct = (((540.0 - location.altAz.getAltitude()) % 360.0) / 360.0);
		
		location.pointingAtAltAz = new ObjectDirectionAltAz();
		updateSensorInfo(location);

		double sensorWindow = 1/(LightSensor.STEPS_PER_REVOLUTION * 1.6);
		boolean inWindow = true;
		if (location.visibleXPct < 0.5 - sensorWindow) {
			stepAzimuth(location, 1);
			inWindow = false;
		}
		else if (location.visibleXPct > 0.5 + sensorWindow) {
			stepAzimuth(location, -1);
			inWindow = false;
		}
		if (location.visibleYPct > 0.5 + sensorWindow) {
			stepAltitude(location, 1);
			inWindow = false;
		}
		else if (location.visibleYPct < 0.5 - sensorWindow) {
			stepAltitude(location, -1);
			inWindow = false;
		}
		
		if ((inWindow) && (! pointingToSun)) {
			pointingToSun = true;
			sunOriginAzimuth = location.altAz.getAzimuth();
			sunOriginAltitude = location.altAz.getAltitude();
			sensor.setOrigin();
			updateSensorInfo(location);
		}
		
		return location;
	}
}
