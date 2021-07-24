package me.qbert.skywatch.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.controller.SensorLocator.CurrentLocation;
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

public class SensorTracker {
	public class ChangeEntry {
		private long seconds;
		private ObjectDirectionAltAz altAz;

		public long getSeconds() {
			return seconds;
		}
		
		public ObjectDirectionAltAz getAltAz() {
			return altAz;
		}
	}
	private SensorLocator sensorLocator;
	
	private CelestialObject sun;
	private ObservationTime time;
	private ObserverLocation myLocation;
	
	private long initTime;
	
	private long lastSeekTime;
	private boolean initialized = false;
	private CurrentLocation lastLocation;
	
	private List<ChangeEntry> trackChanges = new ArrayList<ChangeEntry>();

	public SensorTracker() throws UninitializedObject {
        time = new ObservationTime();
		time.initTime(TimeZone.getDefault());
		myLocation = new ObserverLocation();
		myLocation.setGeoLocation(45, -75);
        sun = SunObject.create().setObserverLocation(myLocation).setObserverTime(time).build();

        sensorLocator = new SensorLocator(sun, time);
	}
	
	public void reset() {
		sensorLocator.reset();
		trackChanges.clear();
		initialized = false;
	}
	
	public void setLocation(double latitude, double longitude) {
		myLocation.setGeoLocation(latitude, longitude);
	}
	
	public double getLatitude() {
		return myLocation.getLatitude();
	}
	
	public double getLongitude() {
		return myLocation.getLongitude();
	}
	
	public boolean isPointingToSun() {
		return sensorLocator.isPointingToSun();
	}
	
	
	public List<ChangeEntry> getTrackChanges() {
		return trackChanges;
	}
	
	public CurrentLocation seekSun() throws UninitializedObject {
		long thisSeekTime = time.getTime().getTimeInMillis() / 1000;

		CurrentLocation preMoveLocation = lastLocation;
		CurrentLocation location = sensorLocator.seekSun();
		
		if ((initialized) && (thisSeekTime - lastSeekTime < 5))
			return null;
		
		if ((isPointingToSun()) && (!initialized)) {
			initTime = thisSeekTime;
			initialized = true;

			ChangeEntry ce = new ChangeEntry();
			ce.seconds = 0;
			ce.altAz = location.getPointingAtAltAz();
			trackChanges.add(ce);
		}
		else if ( initialized) {
			int maxSteps = LightSensor.STEPS_PER_REVOLUTION * 3 / 2;
			while ((maxSteps > 0) && (! location.getPointingAtAltAz().equals(preMoveLocation.getPointingAtAltAz()))) {
				maxSteps --;
				preMoveLocation = location;
				location = sensorLocator.seekSun();
			}
			
			ChangeEntry ce = new ChangeEntry();
			ce.seconds = thisSeekTime - initTime;
			ce.altAz = location.getPointingAtAltAz();
			
			ChangeEntry previousCe = null;
			if (trackChanges.size() > 0)
				previousCe = trackChanges.get(trackChanges.size() - 1);
			// If this is the first entry or if this entry differs from the previously tracked
			// entry, we want to record this one too
			if ((previousCe == null) || (! previousCe.altAz.equals(location.getPointingAtAltAz())))
				trackChanges.add(ce);
			
			// we don't want more than the 1000 most recent.
			while (trackChanges.size() > 1000) {
				trackChanges.remove(0);
			}
			
//			sensorLocator.debugValues();
		}
		
		lastLocation = location;
		lastSeekTime = thisSeekTime;
		
		return location;
	}
}
