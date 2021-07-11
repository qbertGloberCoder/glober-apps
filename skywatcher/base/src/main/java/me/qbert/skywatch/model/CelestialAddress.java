package me.qbert.skywatch.model;

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

public class CelestialAddress {
	private double rightAscension;
	private double declination;
	
	public double getRightAscension() {
		return rightAscension;
	}
	
	public void setRightAscension(double rightAscension) {
		if (this.rightAscension != rightAscension) {
			this.rightAscension = rightAscension;
			settingsChanged();
		}
	}
	
	public double getDeclination() {
		return declination;
	}
	
	public void setDeclination(double declination) {
		if (this.declination != declination) {
			this.declination = declination;
			settingsChanged();
		}
	}
	
	public void setAddress(double rightAscension, double declination) {
		if ((this.rightAscension != rightAscension) || (this.declination != declination)) {
			this.rightAscension = rightAscension;
			this.declination = declination;
			settingsChanged();
		}
	}
	
	protected void settingsChanged() {}
}
