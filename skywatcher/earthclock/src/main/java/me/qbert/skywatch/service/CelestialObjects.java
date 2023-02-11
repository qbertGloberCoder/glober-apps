package me.qbert.skywatch.service;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.BoundaryContainerRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.ImageRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.PolyRenderer;
import me.qbert.ui.renderers.TextRenderer;

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

public class CelestialObjects {
	private static final int [] CONTOUR_ZENITH_ANGLES = {15,30,45,60,75,90,96,102,105,108,120,135,150,165};
	private static final double MARGIN_FRACTION = 0.005;
	private ObserverLocation myLocation = new ObserverLocation();
	private TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private ObservationTime time = new ObservationTime();
	private CelestialObject sun;
	private CelestialObject moon;
	
	private Canvas canvas;
	
	private ImageRenderer background;
	private ImageRenderer clockArtwork;
	private ImageRenderer hourHand;
	private ImageRenderer minuteHand;
	
	private ArcRenderer observerLocation;
	private ArcRenderer sunLocation;
	private ArcRenderer moonLocation;
	private ArcRenderer secondLocation;
	private TextRenderer dateRenderer;
	private TextRenderer subSolarRenderer;
	private TextRenderer subLunarRenderer;
	
	private LineRenderer [] sunlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];
	private LineRenderer [] moonlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];
	
	private int calendarField;
	
	private boolean railwayStyleClock = true;
	
	public CelestialObjects(Canvas canvas) throws Exception {
		calendarField = -1;
		
		observerLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES);
		sunLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES);
		moonLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES);
		secondLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES);
        dateRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        dateRenderer.setMaintainAspectRatio(false);
        subSolarRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        subSolarRenderer.setMaintainAspectRatio(false);
        subLunarRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        subLunarRenderer.setMaintainAspectRatio(false);
		
		try {
		time.initTime(TimeZone.getDefault());
		sun = SunObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
		} catch (Exception e) {
			e.printStackTrace();
			time = null;
			sun = null;
			moon = null;
		}
		
		transactionalListener.addListener(sun);
		transactionalListener.addListener(moon);
		
		myLocation.setGeoLocation(0.0, 0.0);

		this.canvas = canvas;
		
		background = new ImageRenderer(new File("Azimuthal_equidistant_projection_SW.png"));
		background.setRotateAngle(getRotationAngle(myLocation.getLongitude()));
		
		clockArtwork = new ImageRenderer(new File("clock-artwork.png"));
		
		hourHand = new ImageRenderer(new File("hour-hand.png"));
		hourHand.setRotateAngle(0.0);
		
		minuteHand = new ImageRenderer(new File("minute-hand.png"));
		minuteHand.setRotateAngle(0.0);
		
        List<RendererI> renderers = new ArrayList<RendererI>();
        List<RendererI> clockRenderer = new ArrayList<RendererI>();
        
        clockRenderer.add(background);
        clockRenderer.add(clockArtwork);
        clockRenderer.add(hourHand);
        clockRenderer.add(minuteHand);
        
        ColorRenderer colorRenderer;

        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.yellow);
        colorRenderer.setForegroundColor(Color.yellow);
        clockRenderer.add(colorRenderer);
        clockRenderer.add(sunLocation);
		
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.blue);
        colorRenderer.setForegroundColor(Color.blue);
        clockRenderer.add(colorRenderer);
        clockRenderer.add(moonLocation);
        
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.green);
        colorRenderer.setForegroundColor(Color.green);
        clockRenderer.add(colorRenderer);
        clockRenderer.add(observerLocation);

        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.magenta);
        colorRenderer.setForegroundColor(Color.magenta);
        clockRenderer.add(colorRenderer);
        clockRenderer.add(secondLocation);
        
		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			if (CONTOUR_ZENITH_ANGLES[i] == 15) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.white);
		        colorRenderer.setForegroundColor(Color.white);
		        clockRenderer.add(colorRenderer);
			}
			else if (CONTOUR_ZENITH_ANGLES[i] == 90) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.yellow);
		        colorRenderer.setForegroundColor(Color.yellow);
		        clockRenderer.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 96) || (CONTOUR_ZENITH_ANGLES[i] == 102) || (CONTOUR_ZENITH_ANGLES[i] == 108)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.red);
		        colorRenderer.setForegroundColor(Color.red);
		        clockRenderer.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 105) || (CONTOUR_ZENITH_ANGLES[i] == 120)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.black);
		        colorRenderer.setForegroundColor(Color.black);
		        clockRenderer.add(colorRenderer);
			}			
			
			sunlightContourLines[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
			clockRenderer.add(sunlightContourLines[i]);
		}

		BoundaryContainerRenderer clockBoundaryRenderer = new BoundaryContainerRenderer();
		clockBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setRenderers(clockRenderer);
		
        colorRenderer = new ColorRenderer();
        Color c = new Color(224,224,224);
        colorRenderer.setBackgroundColor(c);
        colorRenderer.setForegroundColor(c);
        renderers.add(colorRenderer);
        
        PolyRenderer polyRenderer = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
        polyRenderer.setFill(true);
        polyRenderer.setMaintainAspectRatio(false);
        double [] x = {0.0, 1.0, 1.0, 0.0};
        double [] y = {0.0, 0.0, 1.0, 1.0};
        polyRenderer.setX(x);
        polyRenderer.setY(y);
        renderers.add(polyRenderer);

        renderers.add(clockBoundaryRenderer);
        
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.black);
        colorRenderer.setForegroundColor(Color.black);
        renderers.add(colorRenderer);
        renderers.add(dateRenderer);
        renderers.add(subSolarRenderer);
        renderers.add(subLunarRenderer);
        
        int textY = 20;
        
        dateRenderer.setX(10);
        dateRenderer.setY(textY);
        dateRenderer.setText("---");
        textY += 15;
        
        subSolarRenderer.setX(10);
        subSolarRenderer.setY(textY);
        subSolarRenderer.setText("---");
        textY += 15;
        
        subLunarRenderer.setX(10);
        subLunarRenderer.setY(textY);
        subLunarRenderer.setText("---");
        textY += 15;
        
        updateLocation(sunLocation, 0, 0);
        updateLocation(moonLocation, 0, 0);
        updateLocation(observerLocation, 0, 0);
        
        updateLocation(secondLocation, -90, 90 + myLocation.getLongitude(), 16);
        
        canvas.setRenderers(renderers);
	}
	
	public void updateObjects() throws UninitializedObject {
        
        if (moon != null) {
    		transactionalListener.begin();
    		
    		if (calendarField <= 0)
    			time.setCurrentTime();
    		else
    			time.addTime(1, calendarField);
    		
    		transactionalListener.commit();
    		
    		Calendar cal = time.getTime();
    		
			int year = cal.get(Calendar.YEAR);
			int month = cal.get(Calendar.MONTH) + 1;
			int day = cal.get(Calendar.DAY_OF_MONTH);
			int hour = cal.get(Calendar.HOUR_OF_DAY);
			int minute = cal.get(Calendar.MINUTE);
			int second = cal.get(Calendar.SECOND);
			int milliSecond = cal.get(Calendar.MILLISECOND);
			double tdstOffset = (double)(cal.get(Calendar.DST_OFFSET) / 3600000.0);
			double tzOffset = (double)(cal.get(Calendar.ZONE_OFFSET) / 3600000.0);
			double timezoneAdjust = tzOffset + tdstOffset;
			
			if (calendarField == -1)
				milliSecond = 0;
			
			double seconds = second + (milliSecond / 1000.0);
			double minutesSeconds = minute + (seconds / 60.0);
			double hoursMinutesSeconds = hour + (minutesSeconds / 60.0);
			
			if (railwayStyleClock) {
				seconds *= 1.0256410256410256410;
				
				if (seconds > 59.999)
					seconds = 0.0;
			}
		
			if (railwayStyleClock) {
				minutesSeconds = minute;
				hoursMinutesSeconds = hour + (minutesSeconds / 60.0);
			}
			
			hourHand.setRotateAngle(360.0 * hoursMinutesSeconds / 24.0);
			minuteHand.setRotateAngle(360.0 * (minutesSeconds / 60.0));
			updateLocation(secondLocation, -90, 540 + myLocation.getLongitude() - (360.0 * (seconds / 60.0)), 16);
			
			String subSecond = "";
			if (calendarField == 0)
				subSecond = String.format(".%03d", milliSecond);
			
    		dateRenderer.setText(String.format("%04d-%02d-%02d %02d:%02d:%02d%s %4.1f", year, month, day, hour, minute, second, subSecond, timezoneAdjust));

    		GeoLocation subObjectPoint;
    		subObjectPoint = sun.getEarthPositionOverhead();
    		updateLocation(sunLocation, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
    		
    		subSolarRenderer.setText("Sub-Solar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		updateContourLines(subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
            
    		subObjectPoint = moon.getEarthPositionOverhead();
            updateLocation(moonLocation, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
            
    		subLunarRenderer.setText("Sub-Lunar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		
        }
        
        updateLocation(observerLocation, myLocation.getLatitude(), myLocation.getLongitude());
        
//        canvas.invalidate();
        canvas.repaint();
	}
	
	public void speedDown() {
		if (calendarField == Calendar.YEAR)
			calendarField = Calendar.MONTH;
		else if (calendarField == Calendar.MONTH)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (calendarField == Calendar.DAY_OF_MONTH)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (calendarField == Calendar.HOUR_OF_DAY)
			calendarField = Calendar.MINUTE;
		else if (calendarField == Calendar.MINUTE)
			calendarField = 0;
		else if (calendarField == 0)
			calendarField = -1;
	}
	
	public void speedUp() {
		if (calendarField < 0)
			calendarField = 0;
		else if (calendarField == 0)
			calendarField = Calendar.MINUTE;
		else if (calendarField == Calendar.MINUTE)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (calendarField == Calendar.HOUR_OF_DAY)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (calendarField == Calendar.DAY_OF_MONTH)
			calendarField = Calendar.MONTH;
		else if (calendarField == Calendar.MONTH)
			calendarField = Calendar.YEAR;
	}
	
	private Point2D.Double updateLocation(double latitude, double longitude) {
		double angle = Math.toRadians(longitude - myLocation.getLongitude() - 90.0);
		double radius = (90.0 - latitude) / 180.0 * 0.5;
		
		double fractionX = radius * Math.cos(angle) + 0.5;
		double fractionY = 0.5 - radius * Math.sin(angle);
		
		return new Point2D.Double(fractionX, fractionY);
	}
	
	private void updateLocation(ArcRenderer arc, double latitude, double longitude) {
		updateLocation(arc, latitude, longitude, 8);
	}
	
	private void updateLocation(ArcRenderer arc, double latitude, double longitude, int size) {
		Point2D.Double p2d = updateLocation(latitude, longitude);
		
//		System.out.println("??? fraction " + fractionX + "," + fractionY + " FOR: " + latitude + "," + longitude + " USING: " + radius + "," + Math.toDegrees(angle));
		
		arc.setArcAngle(360);
		arc.setFill(true);
		arc.setWidth(size);
		arc.setHeight(size);
		arc.setX(p2d.x);
		arc.setY(p2d.y);
	}
	
	private double getRotationAngle(double longitude) {
		double bias = -90.0;
		
//		System.out.println("??? ROTATION ANGLE??? " + (bias - (-90 - longitude)) + " for " + longitude);
		
		return bias - (-90 - longitude);
	}
	
	private void updateContourLines(double latitude, double longitude) {
		double circumference = 40030.17359191636;
		
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);
		
		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			int distanceMarker = CONTOUR_ZENITH_ANGLES[i];
			
			double distance = circumference * distanceMarker / 360;
			Double lastX = null;
			Double lastY = null;
			
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();
			
			for (double bearing = 0.0;bearing < 360.0;bearing ++) {
				GeoLocation computedCoord2;
				
//				if (globeEarthMode) { 
					computedCoord2 = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, bearing);
//				}
//				else {
//					computedCoord2 = GeoCalculator.getSillyFlatEarthCoordinateFromLocationgAndBearing(location, distance, bearing);
//				}
				
				double oLat = computedCoord2.getLatitude();
				double oLon = computedCoord2.getLongitude();
				
				Point2D.Double locatePoint = updateLocation(oLat, oLon);
				
				if ((lastX != null) && (lastY != null)) {
					fractionX1.add(lastX);
					fractionY1.add(lastY);
					fractionX2.add(locatePoint.x);
					fractionY2.add(locatePoint.y);
				}

				lastX = locatePoint.x;
				lastY = locatePoint.y;
			}
			
			if ((lastX != null) && (lastY != null)) {
				fractionX1.add(lastX);
				fractionY1.add(lastY);
				fractionX2.add(fractionX2.get(0));
				fractionY2.add(fractionY2.get(0));
			}
			
			sunlightContourLines[i].setX1Array(arrayListToArray(fractionX1));
			sunlightContourLines[i].setY1Array(arrayListToArray(fractionY1));
			sunlightContourLines[i].setX2Array(arrayListToArray(fractionX2));
			sunlightContourLines[i].setY2Array(arrayListToArray(fractionY2));
		}
	}
	
	private double [] arrayListToArray(ArrayList<Double> arrayList) {
		double [] d = new double[arrayList.size()];
		
		for (int i = 0;i < d.length;i ++) {
			d[i] = arrayList.get(i);
		}
		
		return d;
	}
	
	public int animateSpeedHint() {
		if (calendarField < 0)
			return 0;
		
		if (calendarField == 0)
			return 1;
		
		return 2;
	}
	
	public void setLatitude(double newLatitude) {
		myLocation.setLatitude(newLatitude);
	}
	
	public void setLongitude(double newLongitude) {
		myLocation.setLongitude(newLongitude);
		background.setRotateAngle(getRotationAngle(myLocation.getLongitude()));
	}

	public boolean isRailwayStyleClock() {
		return railwayStyleClock;
	}

	public void setRailwayStyleClock(boolean railwayStyleClock) {
		this.railwayStyleClock = railwayStyleClock;
	}
}
