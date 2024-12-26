package me.qbert.skywatch.astro;

import java.util.ArrayList;

import me.qbert.skywatch.listeners.ObjectStateChangeListener;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.GeoLocation3D;

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

public class ObserverLocation3D extends GeoLocation3D {
	private ArrayList<ObjectStateChangeListener> listeners = new ArrayList<ObjectStateChangeListener>();
	
	@Override
	protected void settingsChanged() {
		for (ObjectStateChangeListener listener : listeners) {
			listener.stateChanged(this, listener);
		}
	}
	
	public void addListener(ObjectStateChangeListener stateChangeListener) {
		if (! listeners.contains(stateChangeListener))
			listeners.add(stateChangeListener);
	}
}
