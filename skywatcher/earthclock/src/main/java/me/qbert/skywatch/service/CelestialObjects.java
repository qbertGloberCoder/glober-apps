package me.qbert.skywatch.service;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
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
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.ui.component.Canvas;
import me.qbert.ui.ImageTransformerI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.BoundaryContainerRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.ImageRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.PolyRenderer;
import me.qbert.ui.renderers.TextRenderer;
import me.qbert.ui.renderers.VirtualImageCanvasRenderer;

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

public class CelestialObjects implements ImageTransformerI {
	private static final int [] CONTOUR_ZENITH_ANGLES = {15,30,45,60,75,90,96,102,105,108,120,135,150,165};
	private static final int [] DAYNIGHT_CONTOUR_ZENITH_ANGLES = {90, 120};
	private static final double MARGIN_FRACTION = 0.005;
	private ObserverLocation myLocation = new ObserverLocation();
	private TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private ObservationTime time = new ObservationTime();
	private CelestialObject sun;
	private CelestialObject moon;
	
	private Canvas canvas;
	
	private ImageRenderer background;
//	private ImageRenderer clockArtwork;
	private ImageRenderer hourHand;
	private ImageRenderer minuteHand;
	
	private ArcRenderer observerLocation;
	private ArcRenderer sunLocation;
	private ArcRenderer moonLocation;
	
	private ArcRenderer secondLocation;
	private BoundaryContainerRenderer swissRailwaySecondRenderer;
	private ArcRenderer swissRailwayTipCircle;
	private PolyRenderer swissRailwayNeedle;
	
	private TextRenderer dateRenderer;
	private TextRenderer observerLocationRenderer;
	private TextRenderer subSolarRenderer;
	private TextRenderer sunAltitudeRenderer;
	private TextRenderer sunAzimuthRenderer;
	private TextRenderer subLunarRenderer;
	private TextRenderer moonAltitudeRenderer;
	private TextRenderer moonAzimuthRenderer;
	
	private BoundaryContainerRenderer contourLinesContainerRenderer;
	private LineRenderer [] sunlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];
	private LineRenderer [] moonlightContourLines = new LineRenderer[CONTOUR_ZENITH_ANGLES.length];

	private double dayNightLatitude = -99999.0;
	private double dayNightLongitude = -99999.0;
	private VirtualImageCanvasRenderer dayNightRenderer;
	private ColorRenderer [] dayNightFillColors;
	private LineRenderer [] dayNightContourLines;
	private PolyRenderer nightTimeFill;
	
	
	private int calendarField;
	
	private boolean railwayStyleClock = true;
	private boolean twentyFourHourClock = true;
	private int fillLevel;
	
	public CelestialObjects(Canvas canvas) throws Exception {
		calendarField = -1;
		
		observerLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		sunLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		moonLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		
		secondLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		makeSwissRailwaySecondNeedle();
		
		
		
        dateRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        dateRenderer.setMaintainAspectRatio(false);
        observerLocationRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        observerLocationRenderer.setMaintainAspectRatio(false);
        subSolarRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        subSolarRenderer.setMaintainAspectRatio(false);
        sunAltitudeRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        sunAltitudeRenderer.setMaintainAspectRatio(false);
        sunAzimuthRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        sunAzimuthRenderer.setMaintainAspectRatio(false);
        subLunarRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        subLunarRenderer.setMaintainAspectRatio(false);
        moonAltitudeRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        moonAltitudeRenderer.setMaintainAspectRatio(false);
        moonAzimuthRenderer = new TextRenderer(ArcRenderer.ABSOLUTE_COORDINATES);
        moonAzimuthRenderer.setMaintainAspectRatio(false);
		
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
		
		background = new ImageRenderer(new File("Azimuthal_equidistant_projection_SW.png"), new File("clock-artwork.png"));
		background.setRotateAngle(getRotationAngle(myLocation.getLongitude()));
		
		hourHand = new ImageRenderer(new File("hour-hand.png"));
		hourHand.setRotateAngle(0.0);
		
		minuteHand = new ImageRenderer(new File("minute-hand.png"));
		minuteHand.setRotateAngle(0.0);
		
        List<RendererI> renderers = new ArrayList<RendererI>();
        List<RendererI> clockRenderer = new ArrayList<RendererI>();
        
        clockRenderer.add(background);
        
        ColorRenderer colorRenderer;
        Color c;
        
        ArrayList<RendererI> dayNightRenderers = new ArrayList<RendererI>();
        dayNightRenderer = new VirtualImageCanvasRenderer(this);
        dayNightRenderer.setMaintainAspectRatio(true);
        dayNightContourLines = new LineRenderer[DAYNIGHT_CONTOUR_ZENITH_ANGLES[1] - DAYNIGHT_CONTOUR_ZENITH_ANGLES[0]];
        dayNightFillColors = new ColorRenderer[4];
        for (int idx = 0;idx < dayNightContourLines.length;idx ++) {
			dayNightContourLines[idx] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
        	dayNightContourLines[idx].setMaintainAspectRatio(true);
        	dayNightContourLines[idx].setLineWidth(2.0, LineRenderer.ABSOLUTE_COORDINATES);
        	
        	int fillColour = (idx / 6);
        	if (fillColour >= dayNightFillColors.length)
        		fillColour = dayNightFillColors.length - 1;

        	if (dayNightFillColors[fillColour] == null) {
        		dayNightFillColors[fillColour] = new ColorRenderer();
        		dayNightFillColors[fillColour].setBackgroundColor(Color.black);
        		dayNightFillColors[fillColour].setForegroundColor(Color.black);
        		dayNightRenderers.add(dayNightFillColors[fillColour]);
        	}
        	dayNightRenderers.add(dayNightContourLines[idx]);
        }
        nightTimeFill = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
        nightTimeFill.setFill(true);
        nightTimeFill.setMaintainAspectRatio(true);
        dayNightRenderers.add(nightTimeFill);
        
        setDayNightFillLevel(224);
        
        dayNightRenderer.setRenderers(dayNightRenderers);
        
        clockRenderer.add(dayNightRenderer);

        
        clockRenderer.add(hourHand);
        clockRenderer.add(minuteHand);

        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.magenta);
        colorRenderer.setForegroundColor(Color.magenta);
        clockRenderer.add(colorRenderer);
        clockRenderer.add(secondLocation);
        clockRenderer.add(swissRailwaySecondRenderer);
        
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
        
        contourLinesContainerRenderer = new BoundaryContainerRenderer();
        contourLinesContainerRenderer.setMaintainAspectRatio(true);
        
        clockRenderer.add(contourLinesContainerRenderer);
        List<RendererI> contourLinesRenderers = new ArrayList<RendererI>();
        

		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			if (CONTOUR_ZENITH_ANGLES[i] == 15) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.white);
		        colorRenderer.setForegroundColor(Color.white);
		        contourLinesRenderers.add(colorRenderer);
			}
			else if (CONTOUR_ZENITH_ANGLES[i] == 90) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.yellow);
		        colorRenderer.setForegroundColor(Color.yellow);
		        contourLinesRenderers.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 96) || (CONTOUR_ZENITH_ANGLES[i] == 102) || (CONTOUR_ZENITH_ANGLES[i] == 108)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.red);
		        colorRenderer.setForegroundColor(Color.red);
		        contourLinesRenderers.add(colorRenderer);
			}
			else if ((CONTOUR_ZENITH_ANGLES[i] == 105) || (CONTOUR_ZENITH_ANGLES[i] == 120)) {
		        colorRenderer = new ColorRenderer();
		        colorRenderer.setBackgroundColor(Color.black);
		        colorRenderer.setForegroundColor(Color.black);
		        contourLinesRenderers.add(colorRenderer);
			}			
			
			sunlightContourLines[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
			contourLinesRenderers.add(sunlightContourLines[i]);
		}
        
        contourLinesContainerRenderer.setRenderers(contourLinesRenderers);

		BoundaryContainerRenderer clockBoundaryRenderer = new BoundaryContainerRenderer();
		clockBoundaryRenderer.setBoundMinimumXFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumXFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMinimumYFraction(MARGIN_FRACTION);
		clockBoundaryRenderer.setBoundMaximumYFraction(1.0 - MARGIN_FRACTION);
		clockBoundaryRenderer.setMaintainAspectRatio(true);
		clockBoundaryRenderer.setRenderers(clockRenderer);
		
        colorRenderer = new ColorRenderer();
        c = new Color(224,224,224);
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
        renderers.add(observerLocationRenderer);
        renderers.add(subSolarRenderer);
        renderers.add(sunAltitudeRenderer);
        renderers.add(sunAzimuthRenderer);
        renderers.add(subLunarRenderer);
        renderers.add(moonAltitudeRenderer);
        renderers.add(moonAzimuthRenderer);
        
        int textY = 20;
        
        dateRenderer.setX(10);
        dateRenderer.setY(textY);
        dateRenderer.setText("---");
        textY += 15;
        
        observerLocationRenderer.setX(10);
        observerLocationRenderer.setY(textY);
        observerLocationRenderer.setText("---");
        textY += 15;
        
        subSolarRenderer.setX(10);
        subSolarRenderer.setY(textY);
        subSolarRenderer.setText("---");
        textY += 15;
        
        sunAltitudeRenderer.setX(35);
        sunAltitudeRenderer.setY(textY);
        sunAltitudeRenderer.setText("---");
        textY += 15;
        
        sunAzimuthRenderer.setX(35);
        sunAzimuthRenderer.setY(textY);
        sunAzimuthRenderer.setText("---");
        textY += 15;
        
        subLunarRenderer.setX(10);
        subLunarRenderer.setY(textY);
        subLunarRenderer.setText("---");
        textY += 15;
        
        moonAltitudeRenderer.setX(35);
        moonAltitudeRenderer.setY(textY);
        moonAltitudeRenderer.setText("---");
        textY += 15;
        
        moonAzimuthRenderer.setX(35);
        moonAzimuthRenderer.setY(textY);
        moonAzimuthRenderer.setText("---");
        textY += 15;
        
        updateLocation(sunLocation, 0, 0);
        updateLocation(moonLocation, 0, 0);
        updateLocation(observerLocation, 0, 0);
        
        updateLocation(secondLocation, -90, 90 + myLocation.getLongitude(), 16);
        
        setRailwayStyleClock(railwayStyleClock);
        
        
        canvas.setRenderers(renderers);
	}
	
	public void setClockFace(boolean mickeyFace) {
		if (mickeyFace) {
			background.reinitOverlay(new File("clock-artwork.png"));
			hourHand.reinitImage(new File("hour-hand.png"));
			minuteHand.reinitImage(new File("minute-hand.png"));
		} else {
			background.reinitOverlay(null);
			hourHand.reinitImage(new File("needle-hour.png"));
			minuteHand.reinitImage(new File("needle-minute.png"));
		}
	}
	
	public void setRenderContourLines(boolean contourLines) {
		contourLinesContainerRenderer.setRenderComponent(contourLines);
	}
	
	public boolean isRenderContourLines() {
		return contourLinesContainerRenderer.isRenderComponent();
	}
	
	public void setDayNightRendered(boolean rendered) {
		dayNightRenderer.setRenderComponent(rendered);
	}
	
	public boolean isDayNightRendered() {
		return dayNightRenderer.isRenderComponent();
	}
	
	public void setDayNightFillLevel(int fillLevel) {
		this.fillLevel = fillLevel;
		
		int maxValue = fillLevel;
		if (maxValue > 255)
			maxValue = 255;
		int variableLevels = maxValue - 96 + 1;
		

        for (int fillColour = 0;fillColour < dayNightFillColors.length;fillColour ++) {
            int colorIdx = 96 + variableLevels * (fillColour * 6) / ((dayNightFillColors.length - 1) * 6);
            if (colorIdx > maxValue)
            	colorIdx = maxValue;
            Color c = new Color(colorIdx, colorIdx, colorIdx);
            dayNightFillColors[fillColour].setBackgroundColor(c);
            dayNightFillColors[fillColour].setForegroundColor(c);
        }
        
        invalidateDayNight();
	}
	
	public int getDayNightFillLevel() {
		return fillLevel;
	}
	
	private void updateSwissRailwaySecondNeedle(double angle) {
		if (! swissRailwaySecondRenderer.isRenderComponent())
			return;
		
		double angleRad = Math.toRadians(angle);
		
		double yLength = 0.1875;
		Point2D.Double p = rotatePoint(0.5, yLength, angleRad);
		swissRailwayTipCircle.setX(p.x);
		swissRailwayTipCircle.setY(p.y);
		
		double width = 0.00625;
		double [] x = {0.5 - width, 0.5 + width, 0.5 + width, 0.5 - width};
		double [] y = {yLength, yLength, 0.55, 0.55};
		
		for (int i = 0;i < x.length;i ++) {
			p = rotatePoint(x[i], y[i], angleRad);
			x[i] = p.x;
			y[i] = p.y;
		}
		swissRailwayNeedle.setX(x);
		swissRailwayNeedle.setY(y);
	}
	
	private void makeSwissRailwaySecondNeedle() throws Exception {
		swissRailwayTipCircle = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
		Point2D.Double p2d = updateLocation(-45.0, -180.0, 0);
		swissRailwayTipCircle.setArcAngle(360);
		swissRailwayTipCircle.setMaintainAspectRatio(true);
		swissRailwayTipCircle.setFill(true);
		swissRailwayTipCircle.setWidth(0.0375);
		swissRailwayTipCircle.setHeight(0.0375);
		
		swissRailwayNeedle = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
		swissRailwayNeedle.setFill(true);
		swissRailwayNeedle.setMaintainAspectRatio(true);
		
		List<RendererI> renderers = new ArrayList<RendererI>();
		
		ColorRenderer colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.red);
        colorRenderer.setForegroundColor(Color.red);
		renderers.add(colorRenderer);
		renderers.add(swissRailwayTipCircle);
		renderers.add(swissRailwayNeedle);
		
		swissRailwaySecondRenderer = new BoundaryContainerRenderer();
		swissRailwaySecondRenderer.setMaintainAspectRatio(true);
		swissRailwaySecondRenderer.setRenderers(renderers);
		
		updateSwissRailwaySecondNeedle(0.0);
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
			
			double hoursPerRevolution;
			
			if (twentyFourHourClock)
				hoursPerRevolution = 24.0;
			else
				hoursPerRevolution = 12.0;
			
			hourHand.setRotateAngle(360.0 * hoursMinutesSeconds / hoursPerRevolution);
			minuteHand.setRotateAngle(360.0 * (minutesSeconds / 60.0));
			double secondsAngle = myLocation.getLongitude() - (360.0 * (seconds / 60.0));
			updateLocation(secondLocation, -90, 540 + secondsAngle, 16);
			updateSwissRailwaySecondNeedle(secondsAngle);
			
			String subSecond = "";
			if (calendarField == 0)
				subSecond = String.format(".%03d", milliSecond);
			
    		dateRenderer.setText(String.format("%04d-%02d-%02d %02d:%02d:%02d%s %4.1f", year, month, day, hour, minute, second, subSecond, timezoneAdjust));

    		observerLocationRenderer.setText("Observer lat: " + myLocation.getLatitude() + ", lon: " + myLocation.getLongitude());
    		
    		GeoLocation subObjectPoint;
    		subObjectPoint = sun.getEarthPositionOverhead();
    		updateLocation(sunLocation, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
    		
    		subSolarRenderer.setText("Sub-Solar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		updateContourLines(subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
    		
    		ObjectDirectionAltAz altitudeAzimuth = sun.getCurrentDirectionAsAltitudeAzimuth();
    		sunAltitudeRenderer.setText("Altitude: " + altitudeAzimuth.getAltitude());
    		sunAzimuthRenderer.setText("Azimuth: " + altitudeAzimuth.getAzimuth());
            
    		subObjectPoint = moon.getEarthPositionOverhead();
            updateLocation(moonLocation, subObjectPoint.getLatitude(), subObjectPoint.getLongitude());
            
    		subLunarRenderer.setText("Sub-Lunar coordinate: " + subObjectPoint.getLatitude() + " " + subObjectPoint.getLongitude());
    		altitudeAzimuth = moon.getCurrentDirectionAsAltitudeAzimuth();
    		moonAltitudeRenderer.setText("Altitude: " + altitudeAzimuth.getAltitude());
    		moonAzimuthRenderer.setText("Azimuth: " + altitudeAzimuth.getAzimuth());
    		
        }
        
        updateLocation(observerLocation, myLocation.getLatitude(), myLocation.getLongitude());
        
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
	
	public void setSpeed(int speedIndex) {
		if (speedIndex == 0)
			calendarField = -1;
		else if (speedIndex == 1)
			calendarField = 0;
		else if (speedIndex == 2)
			calendarField = Calendar.MINUTE;
		else if (speedIndex == 3)
			calendarField = Calendar.HOUR_OF_DAY;
		else if (speedIndex == 4)
			calendarField = Calendar.DAY_OF_MONTH;
		else if (speedIndex == 5)
			calendarField = Calendar.MONTH;
		else if (speedIndex == 6)
			calendarField = Calendar.YEAR;
	}
	
	public int getCurrentSpeed() {
		if (calendarField == Calendar.YEAR)
			return 6;
		
		if (calendarField == Calendar.MONTH)
			return 5;
		
		if (calendarField == Calendar.DAY_OF_MONTH)
			return 4;
		
		if (calendarField == Calendar.HOUR_OF_DAY)
			return 3;
		
		if (calendarField == Calendar.MINUTE)
			return 2;
		
		if (calendarField == 0)
			return 1;
		
		return 0;
		
	}
	
	public static String [] getSpeedLabels() {
		String [] s = new String[7];
		
		s[0] = "Step 1 fps";
		s[1] = "Step 60 fps";
		s[2] = "Advance by minutes";
		s[3] = "Advance by hours";
		s[4] = "Advance by days";
		s[5] = "Advance by months";
		s[6] = "Advance by years";
		return s;
	}
	
	private Point2D.Double rotatePoint(double x, double y, double angle) {
		double xFrac = x - 0.5;
		double yFrac = 0.5 - y;
		double radius = Math.sqrt(xFrac * xFrac + yFrac * yFrac);
		
		double originalAngle = Math.atan2(yFrac, xFrac);
		
		return rotatePoint(radius, originalAngle + angle - Math.toRadians(myLocation.getLongitude()));
	}
	
	private Point2D.Double rotatePoint(double radius, double angle) {
		double fractionX = radius * Math.cos(angle) + 0.5;
		double fractionY = 0.5 - radius * Math.sin(angle);
		
		return new Point2D.Double(fractionX, fractionY);
	}
	
	private Point2D.Double updateLocation(double latitude, double longitude) {
		return updateLocation(latitude, longitude, myLocation.getLongitude());
	}
	
	private Point2D.Double updateLocation(double latitude, double longitude, double observerLongitude) {
		double angle = Math.toRadians(longitude - observerLongitude - 90.0);
		double radius = (90.0 - latitude) / 180.0 * 0.5;
		
		return rotatePoint(radius, angle);
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
		
		boolean debug = false;
		double debugRadius = 0.0;
		
		for (int i = 0;i < CONTOUR_ZENITH_ANGLES.length;i ++) {
			int distanceMarker = CONTOUR_ZENITH_ANGLES[i];
			
			double distance = circumference * distanceMarker / 360;
			Double lastX = null;
			Double lastY = null;
			
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();
			
			double lastRadius = 0.0;
			double lastBearing = 0.0;
			
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
				
				double px = locatePoint.x - 0.5;
				double py = 0.5 - locatePoint.y;
				
				double thisAngle = Math.atan2(py, px);
				double thisRadius = Math.sqrt(px*px + py*py);
				
				if (thisRadius > debugRadius)
					debugRadius = thisRadius;
				
				if ((lastRadius > 0.475) && (thisRadius > 0.475)) {
					int steps = 30;
					double bearingSteps = (bearing - lastBearing) / (double)steps;
					
					for (double fillSubBearings = lastBearing + bearingSteps;fillSubBearings < bearing;fillSubBearings += bearingSteps) {
						computedCoord2 = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, fillSubBearings);
						double oLat2 = computedCoord2.getLatitude();
						double oLon2 = computedCoord2.getLongitude();
						
						Point2D.Double locatePoint2 = updateLocation(oLat2, oLon2);

						if ((lastX != null) && (lastY != null)) {
							fractionX1.add(lastX);
							fractionY1.add(lastY);
							fractionX2.add(locatePoint2.x);
							fractionY2.add(locatePoint2.y);
						}

						lastX = locatePoint2.x;
						lastY = locatePoint2.y;
					}
					
				}
				
				lastRadius = thisRadius;
				lastBearing = bearing;
				
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

		if (debug == true) {
			System.out.println("??? RADIUS??? " + debugRadius);
		}
		
		updateDayNight(latitude, longitude);
	}
	
	private void invalidateDayNight() {
		dayNightLatitude = -999999.99;
	}
	
	private void updateDayNight(double latitude, double longitude) {
		dayNightRenderer.setRotateAngle(-longitude);
		
		if ((Math.abs(latitude - dayNightLatitude) < 0.5) && (myLocation.getLongitude() == dayNightLongitude))
			return;
		
		dayNightRenderer.invalidate();
		
		dayNightLongitude = myLocation.getLongitude();
		
		dayNightLatitude = latitude;
		
		double circumference = 40030.17359191636;
		
		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, 0);
		
		boolean debug = false;
		double debugRadius = 0.0;
		
		int maxAngles = DAYNIGHT_CONTOUR_ZENITH_ANGLES[1] - DAYNIGHT_CONTOUR_ZENITH_ANGLES[0];
		
		ArrayList<Double> fillFractionsX = null;
		ArrayList<Double> fillFractionsY = null;
		
		for (int i = 0;i < maxAngles;i ++) {
			int distanceMarker = DAYNIGHT_CONTOUR_ZENITH_ANGLES[0] + i;
			
			ArrayList<Double> fractionX1 = new ArrayList<Double>();
			ArrayList<Double> fractionY1 = new ArrayList<Double>();
			ArrayList<Double> fractionX2 = new ArrayList<Double>();
			ArrayList<Double> fractionY2 = new ArrayList<Double>();
			
			double lastRadius = 0.0;
			double lastBearing = 0.0;
			
			for (double fraction = 0.0;fraction < 1.0;fraction += 0.05) {
				double distance = circumference * ((double)distanceMarker + fraction) / 360.0;
				Double lastX = null;
				Double lastY = null;
				
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
					
					double px = locatePoint.x - 0.5;
					double py = 0.5 - locatePoint.y;
					
					double thisAngle = Math.atan2(py, px);
					double thisRadius = Math.sqrt(px*px + py*py);
					
					if (thisRadius > debugRadius)
						debugRadius = thisRadius;
					
					if ((lastRadius > 0.47) && (thisRadius > 0.47)) {
						int steps = 45;
						double bearingSteps = (bearing - lastBearing) / (double)steps;
						
						for (double fillSubBearings = lastBearing + bearingSteps;fillSubBearings < bearing;fillSubBearings += bearingSteps) {
							computedCoord2 = GeoCalculator.getCoordinateFromLocationgAndBearing(location, distance, fillSubBearings);
							double oLat2 = computedCoord2.getLatitude();
							double oLon2 = computedCoord2.getLongitude();
							
							Point2D.Double locatePoint2 = updateLocation(oLat2, oLon2);
	
							if ((lastX != null) && (lastY != null)) {
								fractionX1.add(lastX);
								fractionY1.add(lastY);
								fractionX2.add(locatePoint2.x);
								fractionY2.add(locatePoint2.y);
							}
	
							lastX = locatePoint2.x;
							lastY = locatePoint2.y;
						}
						
					}
					
					lastRadius = thisRadius;
					lastBearing = bearing;
					
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
				
				dayNightContourLines[i].setX1Array(arrayListToArray(fractionX1));
				dayNightContourLines[i].setY1Array(arrayListToArray(fractionY1));
				dayNightContourLines[i].setX2Array(arrayListToArray(fractionX2));
				dayNightContourLines[i].setY2Array(arrayListToArray(fractionY2));
				
				if (fraction == 0.0) {
					fillFractionsX = (ArrayList<Double>) fractionX1.clone();
					fillFractionsY = (ArrayList<Double>) fractionY1.clone();
				}
			}
		}

		if (fillFractionsX != null) {
			nightTimeFill.setX(arrayListToArray(fillFractionsX));
			nightTimeFill.setY(arrayListToArray(fillFractionsY));
		}
		
		if (debug == true) {
			System.out.println("??? RADIUS??? " + debugRadius);
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
		
		secondLocation.setRenderComponent(! railwayStyleClock);
		swissRailwaySecondRenderer.setRenderComponent(railwayStyleClock);		
	}
	
	public boolean isTwentyFourHourClock() {
		return twentyFourHourClock;
	}

	public void setTwentyFourHourClock(boolean twentyFourHourClock) {
		this.twentyFourHourClock = twentyFourHourClock;
	}

	@Override
	public void transformImage(RendererI renderer, BufferedImage image) {
		if (image == null)
			return;
		
		int width = image.getWidth();
		int height = image.getHeight();
		
		double averageRadius = ((width + height) / 4.0);
		
		if ((width < 1) || (height < 1))
			return;
		
		int [] rgbArray = new int[width * height];
		image.getRGB(0, 0, width, height, rgbArray, 0, width);
		
		for (int y = 0;y < height;y ++) {
			int cartesianY = y - (height / 2);
			for (int x = 0;x < width;x ++) {
				int cartesianX = x - (width / 2);
				double radius = Math.sqrt(cartesianX*cartesianX+cartesianY*cartesianY);
				int alpha = 0x00FFFFFF;
				int i = y * width + x;
				if (radius <= averageRadius)
					alpha = ((((rgbArray[i] >> 16) & 0xFF) + ((rgbArray[i] >> 8) & 0xFF) + (rgbArray[i] & 0xFF)) / 3) << 24;
				rgbArray[i] = alpha;
			}
		}
		
		image.setRGB(0, 0, width, height, rgbArray, 0, width);
	}
	
}
