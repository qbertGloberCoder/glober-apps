package me.qbert.skywatch.astro;

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

public interface CelestialObject extends ObjectStateChangeListener {
	public void recompute();
	public ObjectDirectionRaDec getCelestialSphereLocation();
	public ObjectDirectionRaDec getCurrentDirection();
	public ObjectDirectionAltAz getCurrentDirectionAsAltitudeAzimuth(ObjectDirectionRaDec providedRaDec);
	public ObjectDirectionAltAz getCurrentDirectionAsAltitudeAzimuth();
	public GeoLocation getEarthPositionOverhead();
}
