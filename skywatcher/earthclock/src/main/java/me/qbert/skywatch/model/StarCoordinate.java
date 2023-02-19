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

public class StarCoordinate {
	private String name;
	private String designation;
	private double magnification;
	private double rightAscension;
	private double declination;
	private int groupLevel;
	private boolean visible;
	
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDesignation() {
		return designation;
	}
	
	public void setDesignation(String designation) {
		this.designation = designation;
	}
	
	public double getMagnification() {
		return magnification;
	}
	
	public void setMagnification(double magnification) {
		this.magnification = magnification;
	}
	
	public double getRightAscension() {
		return rightAscension;
	}
	
	public void setRightAscension(double rightAscension) {
		this.rightAscension = rightAscension;
	}
	
	public double getDeclination() {
		return declination;
	}
	
	public void setDeclination(double declination) {
		this.declination = declination;
	}

	public int getGroupLevel() {
		return groupLevel;
	}

	public void setGroupLevel(int groupLevel) {
		this.groupLevel = groupLevel;
	}

	public boolean isVisible() {
		return visible;
	}

	public void setVisible(boolean visible) {
		this.visible = visible;
	}
}
