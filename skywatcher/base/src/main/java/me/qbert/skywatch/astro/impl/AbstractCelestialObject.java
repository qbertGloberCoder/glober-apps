package me.qbert.skywatch.astro.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.CelestialObjectBuilder;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.listeners.ObjectStateChangeListener;
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

public abstract class AbstractCelestialObject extends GeoCalculator implements CelestialObject {
	private static final Logger logger = LogManager.getLogger(AbstractCelestialObject.class.getName());
	public abstract class AbstractCelestialObjectBuilder implements CelestialObjectBuilder {
		private ObjectStateChangeListener stateChangeListener = null;
		
		protected abstract AbstractCelestialObject getInstance();
		
		public AbstractCelestialObjectBuilder setObserverTime(ObservationTime observerTime) {
			observationTime = observerTime;
			return this;
		}
		
		public AbstractCelestialObjectBuilder setObserverLocation(ObserverLocation observerLocation) {
			location = observerLocation;
			return this;
		}

		public AbstractCelestialObjectBuilder setStateChangeListener(ObjectStateChangeListener objectStateListener) {
			stateChangeListener = objectStateListener;
			return this;
		}
		
		@Override
		public CelestialObject build() throws UninitializedObject {
			if ((location == null) || (observationTime == null))
				throw new UninitializedObject();
			
			AbstractCelestialObject instance = getInstance();
			instance.location = location;
			instance.observationTime = observationTime;
			if (stateChangeListener == null)
				stateChangeListener = instance;
			
			instance.observationTime.addListener(stateChangeListener);
			instance.location.addListener(stateChangeListener);
			return instance;
		}
		
	}
	protected ObserverLocation location = null;
	protected ObservationTime observationTime = null;

	public void setObjectIndex(int objectIndex) {
	}
	
	protected double modulus(double value, double max) {
	    logger.trace("modulus(value = " + value + ", max=" + max + ")");
		value = value % max;
	    logger.trace("value = value % max >> " + value);
		if (value < 0.0)
			value += max;
	    logger.trace("if (value < 0.0) value += max >> " + value);
		return value;
	}
	
	protected ObjectDirectionRaDec makeRaDec(double rightAscension, double declination) {
		ObjectDirectionRaDec direction = new ObjectDirectionRaDec();
		direction.setRightAscension(rightAscension);
		direction.setDeclination(declination);
		return direction;
	}
	
	protected GeoLocation makeGeoLocation(double latitude, double longitude) {
		GeoLocation location = new GeoLocation();
		location.setLatitude(latitude);
		location.setLongitude(longitude);
		return location;
	}

	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		recompute();
	}
	
	@Override
	public ObjectDirectionAltAz getCurrentDirectionAsAltitudeAzimuth(ObjectDirectionRaDec providedRaDec) {
		return raDeclinationToAltitudeAzimuth(providedRaDec, location);
	}
	
	@Override
	public ObjectDirectionAltAz getCurrentDirectionAsAltitudeAzimuth() {
		ObjectDirectionRaDec direction = getCurrentDirection();
		return raDeclinationToAltitudeAzimuth(direction, location);
	}
}
