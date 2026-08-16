package me.qbert.skywatch.camera.orientation;

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

// altitude/azimuth/barrelRoll, all degrees. See spec §7.4's orientation-transformer signature.
public final class Orientation {
	private final double altitude;
	private final double azimuth;
	private final double barrelRoll;

	public Orientation(double altitude, double azimuth, double barrelRoll) {
		this.altitude = altitude;
		this.azimuth = azimuth;
		this.barrelRoll = barrelRoll;
	}

	public double getAltitude() {
		return altitude;
	}

	public double getAzimuth() {
		return azimuth;
	}

	public double getBarrelRoll() {
		return barrelRoll;
	}

	@Override
	public String toString() {
		return "Orientation[altitude=" + altitude + ", azimuth=" + azimuth + ", barrelRoll=" + barrelRoll + "]";
	}
}
