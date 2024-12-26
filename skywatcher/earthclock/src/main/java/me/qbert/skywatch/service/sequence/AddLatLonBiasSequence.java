package me.qbert.skywatch.service.sequence;

import me.qbert.skywatch.service.SequenceElementI;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation3D;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.CoordinateBias;

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

public class AddLatLonBiasSequence implements SequenceElementI {
	private CoordinateBias coordinateBias;
	
	private double newMultiplier;
	private double newOffset;
	
	public AddLatLonBiasSequence(CoordinateBias coordinateBias, double newMultiplier, double newOffset) {
		this.coordinateBias = coordinateBias;
		
		this.newMultiplier = newMultiplier;
		this.newOffset = newOffset;
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
		coordinateBias.setMultiplier(coordinateBias.getMultiplier() * newMultiplier);
		coordinateBias.setOffset(coordinateBias.getOffset() + newOffset);
	}

}
