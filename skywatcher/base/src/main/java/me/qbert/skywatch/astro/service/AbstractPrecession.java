package me.qbert.skywatch.astro.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject.AbstractCelestialObjectBuilder;
import me.qbert.skywatch.exception.UninitializedObject;
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

public abstract class AbstractPrecession {
	public class PrecessionData {
		private ObjectDirectionRaDec raDec;
		private ObjectDirectionAltAz altAz;
		private GeoLocation groundPosition;
		
		public ObjectDirectionRaDec getRaDec() {
			return raDec;
		}
		
		public ObjectDirectionAltAz getAltAz() {
			return altAz;
		}
		
		public GeoLocation getGroundPosition() {
			return groundPosition;
		}
	}
	private ObserverLocation myLocation = new ObserverLocation();
	private TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private ObservationTime time = new ObservationTime();
	private CelestialObject celestialObject = null;
	
	protected TransactionalStateChangeListener getTransactionalListener() {
		return transactionalListener;
	}
	
	protected abstract double getLatitude();
	protected abstract double getLongitude();
	protected abstract AbstractCelestialObjectBuilder getCelestialObjectBuilder(); 
	
	protected abstract long getOrbitalSeconds();
	protected abstract long getAdvanceSeconds();
	protected abstract double getRaAdvanceEcliptic();
	protected abstract double getRaAdvanceAnalemma();
	public abstract boolean isShowAsAnalemma();
	
	private void init() throws UninitializedObject {
		if (celestialObject != null)
			return;
		
		time.initTime(TimeZone.getDefault());
		celestialObject = getCelestialObjectBuilder().setObserverLocation(myLocation).setObserverTime(time).build();
		transactionalListener.addListener(celestialObject);
	}
	
	// TODO: find a better place to put this. It should be common code somewhere
	private double refixRa(double angle) {
		if (angle > -180.0)
			return angle - 360.0;
		if (angle < -180.0)
			return angle + 360.0;
		return angle;
	}
	
	public List<PrecessionData> calculatePrecession(ObservationTime time) throws UninitializedObject {
		return calculatePrecession(time.getTime().getTimeInMillis());
	}

	public List<PrecessionData> calculatePrecession(long endOfPrecessionTimeInMillis) throws UninitializedObject {
		init();
		
		List<PrecessionData> precessionPoints = new ArrayList<PrecessionData>();
		
		long precessionTime = endOfPrecessionTimeInMillis;

		long orbitalSeconds = getOrbitalSeconds();
		long advanceSeconds = getAdvanceSeconds();
		double raAdvanceEcliptic = getRaAdvanceEcliptic();
		double raAdvanceAnalemma = getRaAdvanceAnalemma();
		
		boolean showAsAnalemma = isShowAsAnalemma();
		
		double raAdjust = 0;
		
		long fullOrbitEarlier = endOfPrecessionTimeInMillis - (orbitalSeconds * 1000L);
		
		while (precessionTime > fullOrbitEarlier) {
			transactionalListener.begin();
			myLocation.setGeoLocation(getLatitude(), getLongitude());
			time.setUnixTime(precessionTime);
			transactionalListener.commit();
			
			PrecessionData data = new PrecessionData();
			
			data.raDec = celestialObject.getCelestialSphereLocation();
			if (raAdjust != 0.0)
				data.raDec.setRightAscension(refixRa(data.raDec.getRightAscension() + raAdjust));

			data.groundPosition = celestialObject.getEarthPositionOverhead();
			if (raAdjust != 0.0)
				data.groundPosition.setLongitude(data.groundPosition.getLongitude() - raAdjust);
			
			data.altAz = celestialObject.getCurrentDirectionAsAltitudeAzimuth(data.raDec);
			
			precessionPoints.add(data);
			
			if (showAsAnalemma)
				raAdjust += raAdvanceAnalemma;
			else
				raAdjust += raAdvanceEcliptic;
			
			precessionTime -= (advanceSeconds * 1000);
		}
		
		return precessionPoints;
	}
}
