package me.qbert.skywatch.astro.service;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject.AbstractCelestialObjectBuilder;
import me.qbert.skywatch.astro.impl.MoonObject;

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

public class MoonPrecession extends AbstractPrecession {
	private ObserverLocation myLocation;
	
	private boolean showAsAnalemma;
	
	public MoonPrecession(ObserverLocation myLocation, boolean showAsAnalemma) {
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
		return MoonObject.create().setStateChangeListener(getTransactionalListener());
	}

	@Override
	protected long getOrbitalSeconds() {
		return (int)(29.5305890 * (double)getAdvanceSeconds());
	}

	@Override
	protected long getAdvanceSeconds() {
		return 89460L;
	}

	@Override
	protected double getRaAdvanceEcliptic() {
		return 12.23;
	}

	@Override
	protected double getRaAdvanceAnalemma() {
		return 0.0;
	}

	@Override
	public boolean isShowAsAnalemma() {
		return showAsAnalemma;
	}

	public void setShowAsAnalemma(boolean showAsAnalemma) {
		this.showAsAnalemma = showAsAnalemma;
	}
}
