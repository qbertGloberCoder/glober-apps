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

	private double stereoVisionRotate = 0.0;
	
	private Double overscanLatLon;
	
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


	public double getStereoVisionRotate() {
		return stereoVisionRotate;
	}


	public void setStereoVisionRotate(double stereoVisionRotate) {
		this.stereoVisionRotate = stereoVisionRotate;
	}


	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, 1.0);
	}
	
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, boolean positiveZOnly) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, 1.0, positiveZOnly);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan) {
		return transform(latitude, longitude, observerLatitude, observerLongitude, extraDstRotationDegrees, overscan, false);
	}
	
	@Override
	public Double transform(double latitude, double longitude, double observerLatitude, double observerLongitude, double extraDstRotationDegrees, double overscan, boolean positiveZOnly) {
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
		
		if (stereoVisionRotate != 0.0) {
			tr = Math.sqrt(tx*tx+tz*tz);
			ta = Math.atan2(tz, tx);
			
			tx = tr*Math.cos(ta + Math.toRadians(stereoVisionRotate));
			tz = tr*Math.sin(ta + Math.toRadians(stereoVisionRotate));
		}

		
		boolean showPoint = true;
		
		if (Math.sqrt(tx*tx + ty*ty) > radius * 2)
			showPoint = false;
		
/*		if (positiveZOnly) {
			if (Math.sqrt(tx*tx + ty*ty) > radius*1.02)
				showPoint = false;
		} */
		if (tz < 0.0) {
			if (positiveZOnly) {
				showPoint = false;
			}
			else {
				double rad = Math.sqrt(tx*tx + ty*ty);
				double checkRadius = radius;
				if (! zoomedOut)
					checkRadius *= zoomLevel;
				if (rad < checkRadius)
					showPoint = false;
			}
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

		if ((positiveZOnly) && (Math.sqrt(tx*tx + ty*ty) <= radius)) {
//			System.out.println("XYZ: " + tx + "," + ty + "," + tz + " stereo? " + stereoVisionRotate + ", radius? " + radius);
			if (stereoVisionRotate != 0.0) {
				tr = Math.sqrt(tx*tx+tz*tz);
				ta = Math.atan2(tz, tx);
				
				tx = tr*Math.cos(ta - Math.toRadians(stereoVisionRotate));
				tz = tr*Math.sin(ta - Math.toRadians(stereoVisionRotate));
			}
			
			tz = Math.sqrt(radius*radius-tx*tx-ty*ty);

//			System.out.println("XYZ: " + tx + "," + ty + "," + tz);
			tr = Math.sqrt(ty*ty+tz*tz);
			ta = Math.atan2(tz, ty);
			
			ty = tr*Math.cos(ta - Math.toRadians(observerLatitude)); //getSequenceGenerator().getMyLocation().getLatitude()));
			tz = tr*Math.sin(ta - Math.toRadians(observerLatitude)); //getSequenceGenerator().getMyLocation().getLatitude())); 
			
//			System.out.println("XYZ: " + tx + "," + ty + "," + tz);

			double computedLat = Math.toDegrees(Math.asin(ty/radius));
			double computedLon = Math.toDegrees(Math.asin(tz/(radius * Math.cos(Math.toRadians(computedLat)))));
			
			computedLon = (90 - computedLon + extraDstRotationDegrees) + observerLongitude;
			
//			System.out.println("WE HAVE LAT/LON: " + computedLat + "," + computedLon);
			
			overscanLatLon = new Double(computedLat, computedLon);
		} else
			overscanLatLon = null;
		
		return p;
	}
	
	public Double getOverscanLatLon() {
		return overscanLatLon;
	}
}
