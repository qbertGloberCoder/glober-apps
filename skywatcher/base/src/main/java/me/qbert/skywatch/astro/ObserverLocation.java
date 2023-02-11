package me.qbert.skywatch.astro;

import java.util.ArrayList;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.model.GeoLocation;

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

public class ObserverLocation extends GeoLocation {
	private ArrayList<CelestialObject> listeners = new ArrayList<CelestialObject>();
	
	@Override
	protected void settingsChanged() {
		for (CelestialObject listener : listeners) {
			listener.recompute();
		}
	}
	
	public void addListener(CelestialObject celestialObject) {
		if (! listeners.contains(celestialObject))
			listeners.add(celestialObject);
	}
}
