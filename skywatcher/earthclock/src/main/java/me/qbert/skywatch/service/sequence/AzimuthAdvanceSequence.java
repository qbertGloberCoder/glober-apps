package me.qbert.skywatch.service.sequence;

import me.qbert.skywatch.service.SequenceElementI;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;

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

public class AzimuthAdvanceSequence implements SequenceElementI {
	private CelestialObject celestialObject;
	private ObservationTime observationTime;
	private double azimuth;
	
	public AzimuthAdvanceSequence(CelestialObject celestialObject, ObservationTime observationTime, double azimuth) {
		this.celestialObject = celestialObject;
		this.observationTime = observationTime;
		this.azimuth = azimuth;
	}

	@Override
	public int sequenceJumpPointer() {
		return 0;
	}

	@Override
	public boolean isIntermediateStep() {
		return true;
	}
	
	@Override
	public void update() throws UninitializedObject {
		long currentTime = observationTime.getTime().getTimeInMillis();
		long abortTime = currentTime;
		long closestTime = abortTime;
		double altitudeDelta = 9999.999;
		
		abortTime += 1000000000L;
		
		long advanceTime = 60000L;
		while (advanceTime > 1000L) {
			for (long ts = currentTime;ts < abortTime;ts += advanceTime) {
				observationTime.setUnixTime(ts);
				celestialObject.recompute();
				double newAzimuth = (celestialObject.getCurrentDirectionAsAltitudeAzimuth().getAzimuth() + 360.0) % 360.0;
				double newAltDelta = Math.abs(azimuth - newAzimuth);
				if (newAltDelta > 180)
					newAltDelta = Math.abs(newAltDelta - 360.0);
				
				if (newAltDelta < altitudeDelta) {
					altitudeDelta = newAltDelta;
					closestTime = ts;
				} else if (newAltDelta > altitudeDelta + 2.0)
					break;
			}
			currentTime = closestTime - advanceTime;
			abortTime = closestTime + (2 * advanceTime);
			
			advanceTime /= 2;
		}
	}
}
