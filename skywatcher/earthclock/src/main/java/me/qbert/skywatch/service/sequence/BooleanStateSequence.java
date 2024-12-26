package me.qbert.skywatch.service.sequence;

import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.BooleanState;
import me.qbert.skywatch.service.SequenceElementI;

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

public class BooleanStateSequence implements SequenceElementI {
	private BooleanState booleanStateObject;
	
	private boolean newState;
	
	public BooleanStateSequence(BooleanState booleanStateObject, boolean newState) {
		this.booleanStateObject = booleanStateObject;
		
		this.newState = newState;
	}
	
	@Override
	public int sequenceJumpPointer() {
		return 0;
	}

	@Override
	public boolean isIntermediateStep() {
		return false;
	}

	@Override
	public void update() throws UninitializedObject {
		booleanStateObject.setState(newState);
	}

}
