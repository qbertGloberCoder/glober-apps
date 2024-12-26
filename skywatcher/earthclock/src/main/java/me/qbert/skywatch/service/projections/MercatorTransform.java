package me.qbert.skywatch.service.projections;

import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;

import me.qbert.skywatch.service.ProjectionTransformI;

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

public class MercatorTransform implements ProjectionTransformI {
	private static final double NORTH_MOST_LATITUDE = 85.12;
	private static final double SOUTH_MOST_LATITUDE = -85.12;
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, 1.0);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, overscan, false);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan, boolean positiveZOnly) {
		longitude -= observerLongitude;
		while (longitude > 180.0)
			longitude -= 360.0;
		while (longitude < -180.0)
			longitude += 360;
		
		if (latitude > NORTH_MOST_LATITUDE)
			latitude = NORTH_MOST_LATITUDE;
		if (latitude < SOUTH_MOST_LATITUDE)
			latitude = SOUTH_MOST_LATITUDE;

		
		double fractionX = 0.5 + (longitude/360.0);
		double fractionY = 0.5-((326.0028845 * (Math.log(Math.tan(Math.toRadians(45.0+(latitude/2))))))/2058);
		
		return new Point2D.Double(fractionX, fractionY);
	}

}
