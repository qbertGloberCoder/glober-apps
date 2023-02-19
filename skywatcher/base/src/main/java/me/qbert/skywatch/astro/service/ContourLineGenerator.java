package me.qbert.skywatch.astro.service;

import java.awt.geom.Point2D;
import java.util.ArrayList;

import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.model.GeoLocation;

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

public class ContourLineGenerator {
	private static final double BEARING_DELTA_LIMIT = 0.0025;
	public static ArrayList<Point2D.Double> computeContourLines(ProjectionTransformerI transformer, ObserverLocation location, double distance) {
		GeoLocation computedCoord;
		Point2D.Double locatePoint;
		
		ArrayList<Double> bearingList = new ArrayList<Double>();
		ArrayList<Point2D.Double> contourLineList = new ArrayList<Point2D.Double>();

		double averageSegmentDistance = 0.0;
		int distanceCount = 0;
		
		Point2D.Double lastPoint = null;
		double lastBearing = 0;

		double bearing;
		
		boolean nullFound = false;
		
//		String dbg = "";
		
		for (bearing = 0.0;bearing >= -360.0;bearing -= 1.0) {
			computedCoord = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, bearing);
			
			double oLat = computedCoord.getLatitude();
			double oLon = computedCoord.getLongitude();
			
			locatePoint = transformer.updateLocation(oLat, oLon);
			
			if (locatePoint != null) {
				if (lastPoint != null) {
					averageSegmentDistance += Math.sqrt(Math.pow((locatePoint.x - lastPoint.x), 2.0) + Math.pow((locatePoint.y - lastPoint.y), 2.0));
					distanceCount ++;
					bearingList.add(0, new Double(lastBearing));
					contourLineList.add(0, lastPoint);
//					dbg = lastBearing + "," + dbg;
				}
				
				lastPoint = locatePoint;
				lastBearing = bearing;
			} else {
				if (lastPoint != null) {
					bearing = ((bearing + lastBearing) / 2.0) + 1.0;
				}
				if ((lastPoint == null) || (lastBearing - bearing < (1.0 + BEARING_DELTA_LIMIT))) {
					nullFound = true;
					break;
				}
			}
		}
		
		if (nullFound == true) {
			if (distanceCount > 0) {
				lastBearing = bearingList.get(distanceCount - 1).doubleValue();
				lastPoint = contourLineList.get(distanceCount - 1);
			}
	
			for (bearing = lastBearing + 1.0;bearing <= 360.0;bearing += 1.0) {
				computedCoord = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, bearing);
				
				double oLat = computedCoord.getLatitude();
				double oLon = computedCoord.getLongitude();
				
				locatePoint = transformer.updateLocation(oLat, oLon);
				
				if (locatePoint != null) {
					if (lastPoint != null) {
						averageSegmentDistance += Math.sqrt(Math.pow((locatePoint.x - lastPoint.x), 2.0) + Math.pow((locatePoint.y - lastPoint.y), 2.0));
						distanceCount ++;
						bearingList.add(new Double(lastBearing));
						contourLineList.add(lastPoint);
//						dbg = dbg + "," + lastBearing;
					}
				} else {
					if (lastPoint != null) {
						bearing = ((bearing + lastBearing) / 2.0) - 1.0;
					}
					if ((distanceCount > 0) && ((lastPoint == null) || (lastBearing - bearing < (BEARING_DELTA_LIMIT - 1.0)))) {
						break;
					}
				}
				
				lastPoint = locatePoint;
				lastBearing = bearing;
			}
		}
		
		if (distanceCount > 0) {
			if (lastPoint != null) {
				locatePoint = contourLineList.get(0);
				averageSegmentDistance += Math.sqrt(Math.pow((locatePoint.x - lastPoint.x), 2.0) + Math.pow((locatePoint.y - lastPoint.y), 2.0));
				distanceCount ++;
				if (nullFound) {
					bearingList.add(new Double(lastBearing));
					contourLineList.add(lastPoint);
//					dbg = dbg + "," + lastBearing;
				} else {
					bearingList.add(0, new Double(lastBearing));
					contourLineList.add(0, lastPoint);
//					dbg = lastBearing + "," + dbg;
				}
//				System.out.println("DBG: " + dbg);

			}
			
			averageSegmentDistance /= (double)(distanceCount);
			averageSegmentDistance *= 2.0;
			
//			System.out.println("??? " + distanceCount + ", " + averageSegmentDistance);

			int i = 0;
			while (i < distanceCount - 1) {
				lastPoint = contourLineList.get(i);
				locatePoint = contourLineList.get(i + 1);
				lastBearing = bearingList.get(i).doubleValue();
				bearing = bearingList.get(i + 1).doubleValue();
				
				if ((lastPoint == null) || (locatePoint == null)) {
					System.out.println("??? OOPS: bearing: " + ((locatePoint == null) ? "null" : "not null") + " + last bearing: " + ((lastPoint == null) ? "null" : "not null"));
					return contourLineList;
				}
				double thisDistance = Math.sqrt(Math.pow((locatePoint.x - lastPoint.x), 2.0) + Math.pow((locatePoint.y - lastPoint.y), 2.0));
				
				if ((thisDistance > averageSegmentDistance) && ((bearing - lastBearing) > BEARING_DELTA_LIMIT)) {
//					System.out.println("??? " + i + " > " + distanceCount + ", " + thisDistance + " > " + averageSegmentDistance + ", " + lastBearing + " > " + bearing);
					double newBearing = ((lastBearing + bearing) / 2.0);
					computedCoord = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, newBearing);
					
					double oLat = computedCoord.getLatitude();
					double oLon = computedCoord.getLongitude();
					
					locatePoint = transformer.updateLocation(oLat, oLon);
					if (locatePoint != null) {
						distanceCount ++;
						bearingList.add(i + 1, new Double(newBearing));
						contourLineList.add(i + 1, locatePoint);
					}
					else
						i ++;
				}
				else
					i ++;
			}
		}
		
		return contourLineList;
	}
}
