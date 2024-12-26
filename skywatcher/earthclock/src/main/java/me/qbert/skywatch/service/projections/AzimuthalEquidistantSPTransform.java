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

public class AzimuthalEquidistantSPTransform implements ProjectionTransformI {
	protected Point2D.Double rotatePoint(double radius, double angle) {
		double fractionX = radius * Math.cos(angle) + 0.5;
		double fractionY = 0.5 - radius * Math.sin(angle);
		
		return new Point2D.Double(fractionX, fractionY);
	}
	
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, 1.0);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, overscan, false);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan, boolean positiveZOnly) {
		double angle = Math.toRadians(180.0 - longitude - observerLongitude - 90.0 - extraDstRotationDegrees);
		double radius = ((90.0 + latitude) / 180.0 * 0.5);
		
		return rotatePoint(radius, angle);

	}

}
