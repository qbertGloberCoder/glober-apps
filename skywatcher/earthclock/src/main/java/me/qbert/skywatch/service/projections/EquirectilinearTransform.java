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

public class EquirectilinearTransform implements ProjectionTransformI {
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees) {
		// 85 pixels per 15 degree markers; image width is 2058
		// 360/15*85
		// 2040.00000000000000000000
		// 2040/2058
		// .99125364431486880466
		
		while (longitude > 180.0)
			longitude -= 360.0;
		while (longitude < -180.0)
			longitude += 360;

		double fractionX = 0.5 + (longitude * 85.0 / 30.0)/1029.0;
		double fractionY = 0.5 - (latitude * 85.0 / 15.0)/1029.0;
		
		return new Point2D.Double(fractionX, fractionY);
	}

}
