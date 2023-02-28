package me.qbert.skywatch.service.projections;

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

public class GlobeTransform implements ProjectionTransformI {
	private double zoomLevel = 1.0;
	private boolean zoomedOut = true;

	
	public double getZoomLevel() {
		return zoomLevel;
	}


	public void setZoomLevel(double zoomLevel) {
		this.zoomLevel = zoomLevel;
	}


	public boolean isZoomedOut() {
		return zoomedOut;
	}


	public void setZoomedOut(boolean zoomedOut) {
		this.zoomedOut = zoomedOut;
	}


	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, 1.0);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan) {
		double useLat = latitude;
		double useLon = longitude - observerLongitude;
		
		while (useLat > 90.0)
			useLat -= 180.0;
		while (useLat < -90.0)
			useLat += 180.0;
		while (useLon > 180.0)
			useLon -= 360.0;
		while (useLon < -180.0)
			useLon += 360.0;

		double radius = 0.5;
		
		if (zoomedOut)
			radius *= zoomLevel;
		
		double tx = radius * overscan * Math.cos(Math.toRadians(useLat)) * Math.cos(Math.toRadians(90 - useLon + extraDstRotationDegrees));
		double tz = radius * overscan * Math.cos(Math.toRadians(useLat)) * Math.sin(Math.toRadians(90 - useLon + extraDstRotationDegrees));
		double ty = radius * overscan * Math.sin(Math.toRadians(useLat));
		
		double tr;
		double ta;

		tr = Math.sqrt(ty*ty+tz*tz);
		ta = Math.atan2(tz, ty);
		
		ty = tr*Math.cos(ta + Math.toRadians(observerLatitude)); //getSequenceGenerator().getMyLocation().getLatitude()));
		tz = tr*Math.sin(ta + Math.toRadians(observerLatitude)); //getSequenceGenerator().getMyLocation().getLatitude())); 
		
		boolean showPoint = true;
		
		if (Math.sqrt(tx*tx + ty*ty) > radius * 2)
			showPoint = false;
		
		if (tz < 0.0) {
			double rad = Math.sqrt(tx*tx + ty*ty);
			double checkRadius = radius;
			if (! zoomedOut)
				checkRadius *= zoomLevel;
			if (rad < checkRadius)
				showPoint = false;
		}
		
//		if ((zoomedOut == true) && (tz > 0.0)) {
//			double rad = Math.sqrt(tx*tx + ty*ty);
//			if (rad < radius * 4)
//				showPoint = false;
//		}
		
		if (! showPoint) {
			return null;
		}

		Double p = new Double(0.5 + tx, 0.5 - ty);
		
		return p;
	}

}
