package me.qbert.skywatch.camera.catalog;

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

public final class StarCoordinate {
	private final String name;
	private final String designation;
	private final double apparentMagnitude;
	private final double rightAscension;
	private final double declination;
	private final int groupLevel;
	private final boolean visible;

	public StarCoordinate(String name, String designation, double apparentMagnitude,
			double rightAscension, double declination, int groupLevel, boolean visible) {
		this.name = name;
		this.designation = designation;
		this.apparentMagnitude = apparentMagnitude;
		this.rightAscension = rightAscension;
		this.declination = declination;
		this.groupLevel = groupLevel;
		this.visible = visible;
	}

	public String getName() {
		return name;
	}

	public String getDesignation() {
		return designation;
	}

	public double getApparentMagnitude() {
		return apparentMagnitude;
	}

	public double getRightAscension() {
		return rightAscension;
	}

	public double getDeclination() {
		return declination;
	}

	public int getGroupLevel() {
		return groupLevel;
	}

	public boolean isVisible() {
		return visible;
	}
}
