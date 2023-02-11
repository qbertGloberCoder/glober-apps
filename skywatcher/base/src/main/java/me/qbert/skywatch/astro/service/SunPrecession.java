package me.qbert.skywatch.astro.service;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.SunObject;
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

public class SunPrecession extends AbstractPrecession {
	private ObserverLocation myLocation;
	
	private boolean showAsAnalemma;
	
	public SunPrecession(ObserverLocation myLocation, boolean showAsAnalemma) {
		this.myLocation = myLocation;
		this.showAsAnalemma = showAsAnalemma;
	}
	
	@Override
	protected double getLatitude() {
		return myLocation.getLatitude();
	}

	@Override
	protected double getLongitude() {
		return myLocation.getLongitude();
	}

	@Override
	protected AbstractCelestialObjectBuilder getCelestialObjectBuilder() {
		return SunObject.create().setStateChangeListener(getTransactionalListener());
	}

	@Override
	protected long getOrbitalSeconds() {
		return (int)(365.25 * (double)getAdvanceSeconds());
	}

	@Override
	protected long getAdvanceSeconds() {
		return 86400L;
	}

	@Override
	protected double getRaAdvanceEcliptic() {
		return (360.0 / 365.25);
	}

	@Override
	protected double getRaAdvanceAnalemma() {
		return 0;
	}

	@Override
	public boolean isShowAsAnalemma() {
		return showAsAnalemma;
	}

	public void setShowAsAnalemma(boolean showAsAnalemma) {
		this.showAsAnalemma = showAsAnalemma;
	}
}
