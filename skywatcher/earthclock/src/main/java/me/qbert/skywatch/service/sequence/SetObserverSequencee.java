package me.qbert.skywatch.service.sequence;

import me.qbert.skywatch.service.SequenceElementI;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.exception.UninitializedObject;

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

public class SetObserverSequencee implements SequenceElementI {
	private ObserverLocation location;
	
	private double latitude;
	private double longitude;
	
	public SetObserverSequencee(ObserverLocation location, double latitude, double longitude) {
		this.location = location;
		
		this.latitude = latitude;
		this.longitude = longitude;
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
		location.setGeoLocation(latitude, longitude);
	}

}
