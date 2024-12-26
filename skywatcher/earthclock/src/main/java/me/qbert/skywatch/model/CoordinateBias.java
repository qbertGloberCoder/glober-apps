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

public class CoordinateBias {
	public static enum CoordinateMode {
		LATITUDE,
		LONGITUDE
	}
	
	private CoordinateMode mode;
	private double multiplier;
	private double offset;
	
	public CoordinateBias(CoordinateMode mode) {
		this.mode = mode;
		reset();
	}
	
	public void reset() {
		multiplier = 1.0;
		offset = 0.0;
	}
	
	public double getMultiplier() {
		return multiplier;
	}
	
	public void setMultiplier(double multiplier) {
		if (multiplier < 0.0)
			multiplier = 0.0;
		else if (multiplier > 1.0)
			multiplier = 1.0;
		else
			this.multiplier = multiplier;
	}
	
	public double getOffset() {
		return offset;
	}
	
	public void setOffset(double offset) {
		double limit = 360.0;
		if (mode == CoordinateMode.LATITUDE)
			limit = 90;
		if (offset < -limit)
			offset = -limit;
		else if (offset > limit)
			offset = limit;
		else
			this.offset = offset;
	}
}
