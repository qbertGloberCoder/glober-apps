package me.qbert.skywatch.ui.renderers;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.SolarObjects.SolarSystemCoordinate;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.EncapsulatingRenderer;
import me.qbert.ui.renderers.LineRenderer;
import me.qbert.ui.renderers.PolyRenderer;

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

public class SolarSystemDateRenderer {
	private ArcRenderer [] planetsCalendar;
	private ArcRenderer [] planetsOrbits;
	private LineRenderer calendarPointer;
	
	private ArcRenderer circumference;
	
	private LineRenderer [] months;
	
	private boolean reinitPlanetOrbits = true;
	private boolean planetsToScale = false;
	private EncapsulatingRenderer planetRenderer;
	
	public SolarSystemDateRenderer() throws Exception {
		ArrayList<RendererI> renderers = new ArrayList<RendererI>();
		ColorRenderer colorRenderer; /*= new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.darkGray);
        colorRenderer.setForegroundColor(Color.darkGray);
        renderers.add(colorRenderer);
        
        PolyRenderer background = new PolyRenderer(PolyRenderer.FRACTIONAL_COORDINATES);
        double [] xArray = {0.0, 1.0, 1.0, 0.0};
        double [] yArray = {0.0, 0.0, 1.0, 1.0};
        background.setX(xArray);
        background.setY(yArray);
        background.setFill(true);
        background.setMaintainAspectRatio(false);
        renderers.add(background); */
        
        
    	planetsCalendar = new ArcRenderer[SolarObjects.OBJECT_LIST.length + 1];
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.yellow);
        colorRenderer.setForegroundColor(Color.yellow);
        renderers.add(colorRenderer);
		planetsCalendar[0] = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		planetsCalendar[0].setX(0.5);
		planetsCalendar[0].setY(0.5);
		planetsCalendar[0].setWidth(8);
		planetsCalendar[0].setHeight(8);
		planetsCalendar[0].setMaintainAspectRatio(true);
		planetsCalendar[0].setFill(true);
		planetsCalendar[0].setArcAngle(360);
    	renderers.add(planetsCalendar[0]);
		
    	for (int i = 0;i < SolarObjects.OBJECT_LIST.length;i ++) {
    		if (i == 0) {
    	        colorRenderer = new ColorRenderer();
    	        colorRenderer.setBackgroundColor(Color.green);
    	        colorRenderer.setForegroundColor(Color.green);
    	        renderers.add(colorRenderer);
    		} else if (i == 1) {
    	        colorRenderer = new ColorRenderer();
    	        colorRenderer.setBackgroundColor(Color.magenta);
    	        colorRenderer.setForegroundColor(Color.magenta);
    	        renderers.add(colorRenderer);
    		}
    		planetsCalendar[i+1] = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
    		planetsCalendar[i+1].setX(0.5);
    		planetsCalendar[i+1].setY(0.5 * (i + 1) / (SolarObjects.OBJECT_LIST.length + 1));
    		planetsCalendar[i+1].setMaintainAspectRatio(true);
    		planetsCalendar[i+1].setFill(true);
    		planetsCalendar[i+1].setWidth(8);
    		planetsCalendar[i+1].setHeight(8);
    		planetsCalendar[i+1].setArcAngle(360);
    		planetsCalendar[i+1].setMaintainAspectRatio(true);
        	renderers.add(planetsCalendar[i+1]);
    	}
    	
    	planetsOrbits = new ArcRenderer[SolarObjects.OBJECT_LIST.length];
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.gray);
        colorRenderer.setForegroundColor(Color.gray);
        renderers.add(colorRenderer);
    	for (int i = 0;i < SolarObjects.OBJECT_LIST.length;i ++) {
    		planetsOrbits[i] = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
    		planetsOrbits[i].setX(0.5);
    		planetsOrbits[i].setY(0.5);
    		planetsOrbits[i].setMaintainAspectRatio(true);
    		planetsOrbits[i].setFill(false);
    		planetsOrbits[i].setArcAngle(360);
        	renderers.add(planetsOrbits[i]);
    	}
    	
    	calendarPointer = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
    	calendarPointer.setMaintainAspectRatio(true);
    	calendarPointer.setX1(0.5);
    	calendarPointer.setY1(0.5);
    	calendarPointer.setX2(0.5);
    	calendarPointer.setY2(0.0);
    	renderers.add(calendarPointer);
    	
    	initMonths();
    	
        colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.white);
        colorRenderer.setForegroundColor(Color.white);
        renderers.add(colorRenderer);

        for (int i = 0;i < months.length;i ++)
    		renderers.add(months[i]);
    	
    	circumference = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.FRACTIONAL_COORDINATES);
    	circumference.setX(0.5);
    	circumference.setY(0.5);
    	circumference.setWidth(1.0);
    	circumference.setHeight(1.0);
    	circumference.setMaintainAspectRatio(true);
    	circumference.setFill(false);
    	circumference.setArcAngle(360);
    	renderers.add(circumference);
    	
    	planetRenderer = new EncapsulatingRenderer();
    	planetRenderer.setLockApsectRatio(1.0);
    	planetRenderer.setRenderers(renderers);
	}
	
	private void initMonths() {
		ObserverLocation myLocation = new ObserverLocation();
		ObservationTime time = new ObservationTime();
		TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
		SolarObjects solarObjects;
		
		try {
			time.initTime(TimeZone.getDefault());
			time.setCurrentTime();
			solarObjects = (SolarObjects) SolarObjects.create().setStateChangeListener(transactionalListener).setObserverLocation(myLocation).setObserverTime(time).build();
			
			transactionalListener.addListener(solarObjects);
		} catch (Exception e) {
			solarObjects = null;
		}
		
		months = new LineRenderer[12];
		if (solarObjects == null) {
			for (int i = 0;i < 12;i ++) {
				try {
					months[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
					months[i].setMaintainAspectRatio(true);
					setTickMarker(months[i], 90.0 + ((double)i * 30.0));

				} catch (Exception e) {
				}
			}
		} else {
			int year = time.getTime().get(Calendar.YEAR);
			
			double initialAngle = 0.0;
			
			for (int i = 0;i < 12;i ++) {
				try {
					months[i] = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
					months[i].setMaintainAspectRatio(true);
					transactionalListener.begin();
					time.setLocalTime(year, i + 1, 1, 0, 0, 0);
					transactionalListener.commit();
					
					SolarSystemCoordinate[] planetLocations = solarObjects.getSolarSystemObjectCoordinates();
					
					double x = planetLocations[0].getX();
					double y = planetLocations[0].getY();
					
		    		double ang = Math.toDegrees(Math.atan2(y, x));
		    		
		    		if (i == 0) {
		    			setTickMarker(months[i], 90.0);
		    			initialAngle = ang;
		    		} else {
		    			setTickMarker(months[i], 90.0 + ang - initialAngle);
		    		}
					
				} catch (Exception e) {
				}
			}
		}
	}
	
	private void setTickMarker(LineRenderer lineRenderer, double angle) {
		double angleRad = Math.toRadians(angle);
		
		double x = 0.5 + 0.5 * Math.cos(angleRad);
		double y = 0.5 - 0.5 * Math.sin(angleRad);

		lineRenderer.setX1(x);
		lineRenderer.setY1(y);

		x = 0.5 + 0.45 * Math.cos(angleRad);
		y = 0.5 - 0.45 * Math.sin(angleRad);
		lineRenderer.setX2(x);
		lineRenderer.setY2(y);
	}
	
	public EncapsulatingRenderer getRenderer() {
		return planetRenderer;
	}

	public void setPlanetsToScale(boolean planetsToScale) {
		this.planetsToScale = planetsToScale;
		reinitPlanetOrbits = true;
	}
	
	public boolean isPlanetsToScale() {
		return planetsToScale;
	}

	public void updateSolarObjects(int dayOfYear, SolarSystemCoordinate[] planetLocations) {
        
        double desiredAngle = Math.toRadians(90.0 + 360.0 * dayOfYear / 365.25);
        double addedAngle = 0.18246472132354;
		double zoomFactor = 0.5 / SolarObjects.SOLAR_SYSTEM_IN_AUS;
		double x;
		double y;
    	for (int i = 0;i < SolarObjects.OBJECT_LIST.length;i ++) {
    		x = planetLocations[i].getX() * zoomFactor;
    		y = planetLocations[i].getY() * zoomFactor;
    		
    		double ang = Math.atan2(y, x);
    		double radius = Math.sqrt(x*x+y*y);
    		
//    		if (dayOfYear == 1)
  //  			System.out.println("??? doy " + dayOfYear + ", desired angle: " + desiredAngle + ", this angle: " + ang);
//    		if (i == 0) {
  //  			System.out.println("??? doy " + doy + ", desired angle: " + desiredAngle + ", this angle: " + ang);
    //			addedAngle = ang - desiredAngle;
    //		}
    		
    		if (! planetsToScale) {
    			
    			radius = (double)(i + 1) * 0.5 / (double)SolarObjects.OBJECT_LIST.length;
    			
    			if (i == 0)
    				radius = (double)3.0 * 0.5 / (double)SolarObjects.OBJECT_LIST.length;
    			if ((i == 1) || (i == 2))
    				radius = (double)i * 0.5 / (double)SolarObjects.OBJECT_LIST.length;
    			
    			// similar to the orbit of pluto at scale and gives a bit of room for the months marker
    			radius *= 0.8;
    			
    			x = radius * Math.cos(ang - addedAngle);
    			y = radius * Math.sin(ang - addedAngle);
    		} else if (addedAngle != 0) {
    			x = radius * Math.cos(ang - addedAngle);
    			y = radius * Math.sin(ang- addedAngle);
    		}
    		
    		
    		x += 0.5;
    		y = 0.5 - y;
    		
    		planetsCalendar[i + 1].setX(x);
    		planetsCalendar[i + 1].setY(y);
    		
    		if ((planetsToScale != planetsToScale) || (reinitPlanetOrbits)) {
	    		planetsOrbits[i].setWidth(radius * 2);
	    		planetsOrbits[i].setHeight(radius * 2);
    		}

    		if (i == 0) {
	    		x = 0.5 * Math.cos(-ang + addedAngle) + 0.5;
	    		y = 0.5 * Math.sin(-ang + addedAngle) + 0.5;
	    		
	        	calendarPointer.setX2(x);
	        	calendarPointer.setY2(y);
    		}
    	}
    	
    	reinitPlanetOrbits = false;
	}
}
