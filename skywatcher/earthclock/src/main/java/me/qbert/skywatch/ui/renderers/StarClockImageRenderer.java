package me.qbert.skywatch.ui.renderers;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D.Double;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.ArrayList;

import me.qbert.skywatch.astro.service.ProjectionTransformerI;
import me.qbert.skywatch.service.ArcRendererLocationSetterI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.AbstractImageRenderer;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.BoundaryContainerRenderer;
import me.qbert.ui.renderers.ColorRenderer;

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

public class StarClockImageRenderer extends BoundaryContainerRenderer {
	private static final byte [][][] digits = {
		{ // 0
		  	  { 0, 1, 1, 1, 1, 0 }
			, { 0, 1, 0, 0, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 0, 0, 1, 0 }
			, { 0, 1, 1, 1, 1, 0 }
		}, { // 1
			  { 0, 0, 1, 0, 0, 0 }
			, { 0, 1, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 1, 1, 1, 1, 0 }
		} , { // 2
			  { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 0, 1, 1, 1, 1, 1 }
		} , { // 3
			  { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 1, 1, 1, 0 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
		}, { // 4
			  { 0, 0, 0, 1, 0, 0 }
			, { 0, 0, 1, 1, 0, 0 }
			, { 0, 1, 0, 1, 0, 0 }
			, { 1, 0, 0, 1, 0, 0 }
			, { 1, 1, 1, 1, 1, 1 }
			, { 0, 0, 0, 1, 0, 0 }
			, { 0, 0, 0, 1, 0, 0 }
			, { 0, 0, 0, 1, 0, 0 }
		}, { // 5
			  { 0, 1, 1, 1, 1, 1 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 0, 1, 1, 1, 1, 0 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 1, 1, 1, 1, 1, 0 }
		}, { // 6
			  { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 0 }
			, { 1, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
		}, { // 7
			  { 0, 1, 1, 1, 1, 0 }
			, { 0, 0, 0, 0, 1, 0 }
			, { 0, 0, 0, 1, 0, 0 }
			, { 0, 0, 0, 1, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 0, 1, 0, 0, 0 }
			, { 0, 1, 0, 0, 0, 0 }
			, { 0, 1, 0, 0, 0, 0 }
		}, { // 8
			  { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
			, { 0, 1, 0, 0, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
		} , { // 9
			  { 0, 1, 1, 1, 1, 0 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 1, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 0, 0, 0, 0, 1 }
			, { 0, 1, 1, 1, 1, 0 }
		}
	};
	private double zoomLevel = 1.0;
	
	private double lastLHA = -99999999.0;
	private boolean lastZoom = false;
	private double lastZoomLevel = 0.0;

	private boolean zoomedOut = false;
	private boolean leaveUnwrapped = false;

	private double circumferenceSizeFraction = 1.0;
	
	private double hourStars;
	private double minuteStars;
	private double secondStars;
	
	private double [][][] digitSpacing;
	
	private ProjectionTransformerI projectionTransformer;
	private ColorRenderer colorRenderer;
	
	public StarClockImageRenderer(ProjectionTransformerI projectionTransformer) {
		this.projectionTransformer = projectionTransformer;
		
      	colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(Color.white);
        colorRenderer.setForegroundColor(Color.white);
        
        digitSpacing = new double[digits.length][digits[0].length][digits[0][0].length];
        
        for (int d = 0;d < digitSpacing.length;d ++) {
        	for (int r = 0;r < digitSpacing[0].length;r ++) {
        		for (int c = 0;c < digitSpacing[0][0].length;c ++) {
        			digitSpacing[d][r][c] = 1.0 + Math.random() * 0.0625;
        		}
        	}
        }
	}
	
	@Override
	public double getAspectRatio() {
		return 1.0;
	}

	public double getCircumferenceSizeFraction() {
		return circumferenceSizeFraction;
	}

	public void setCircumferenceSizeFraction(double circumferenceSizeFraction) {
		this.circumferenceSizeFraction = circumferenceSizeFraction;
	}
	
	private void getDigits(double hourAngle) {
	    hourStars = hourAngle;
	    minuteStars = (hourAngle % 15.0);
	    secondStars = (hourAngle % 900.0);
	}
	
	public void wrapToLHA(double hourAngle) {
		if (leaveUnwrapped)
			return;
		
		if (! isRenderComponent())
			return;

		if ((hourAngle == lastLHA) && (zoomedOut == lastZoom) && (zoomLevel == lastZoomLevel))
			return;
		
		getDigits(hourAngle);
		
		ArrayList<RendererI> renderers = new ArrayList<RendererI>();
		
		renderers.add(colorRenderer);
		
		double digitLongitudeWidth = (360.0/24.0)/4.0;
		double digitLatitudeHeight = digitLongitudeWidth * 10 / 6;
		
		double starLongitudeSpace = digitLongitudeWidth / 6.0;
		double starLatitudeSpace =  digitLatitudeHeight / 10.0;
		
		try {
			setRing(renderers, hourStars, digitLatitudeHeight + digitLatitudeHeight / 2.0, 1, 24, digitLongitudeWidth, digitLatitudeHeight, starLongitudeSpace, starLatitudeSpace);
			setRing(renderers, minuteStars, digitLatitudeHeight / 2.0, 60, 1440, digitLongitudeWidth, digitLatitudeHeight, starLongitudeSpace, starLatitudeSpace);
			setRing(renderers, secondStars, -digitLatitudeHeight + digitLatitudeHeight / 2.0, 3600, 86400, digitLongitudeWidth, digitLatitudeHeight, starLongitudeSpace, starLatitudeSpace);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		
		setRenderers(renderers);
	    
	    lastZoom = zoomedOut;
	    lastZoomLevel = zoomLevel;
	}
	
	private void setRing(ArrayList<RendererI> renderers, double angle, double ringLatitude, int ringMultiplier, int unitMax, double digitLongitudeWidth, double digitLatitudeHeight, double starLongitudeSpace, double starLatitudeSpace) throws Exception {
		int size = 8;
		
		int maxUnit = 24;
		if (ringMultiplier > 1)
			maxUnit = 60;
		
		double minuteMax = 10; // 1
		double secondMax = 2; // 0.016
		
		for (int unit = 0;unit < unitMax;unit ++) {
			double starAngle = (double)unit * 360.0 / (double)unitMax;
			
			starAngle -= angle;
			
			while (starAngle < 0)
				starAngle += 360.0;
			while (starAngle >= 360.0)
				starAngle -= 360.0;
			
			if (ringMultiplier == 60) {
				if ((starAngle > minuteMax) && (starAngle < (180 - minuteMax)))
					continue;
				if ((starAngle > 180 + minuteMax) && (starAngle < (360 - minuteMax)))
					continue;
			}
			if (ringMultiplier == 3600) {
				if ((starAngle > secondMax) && (starAngle < (180 - secondMax)))
					continue;
				if ((starAngle > 180 + secondMax) && (starAngle < (360 - secondMax)))
					continue;
			}
			
			for (int d = 0;d < 2;d ++) {
				int digit;
				
				if (d == 0)
					digit = ((unit % maxUnit) / 10);
				else
					digit = (unit % maxUnit) % 10;
				
				for (int latCount = 0;latCount < 8;latCount ++) {
					double origLat = ringLatitude + (starLatitudeSpace * latCount);
					double y = Math.sin(Math.toRadians(origLat));
					for (int lonCount = 0;lonCount < 6;lonCount ++) {
						if (digits[digit][7 - latCount][lonCount] == 1) {
							double tempLon = starAngle + (digitLongitudeWidth / (-2.0 * (double)ringMultiplier)) + (starLongitudeSpace * (lonCount - 3) / (double)ringMultiplier);
							while (tempLon < -180.0)
								tempLon += 360.0;
							while (tempLon > 180.0)
								tempLon -= 360.0;
							if (d == 1)
								tempLon += (1.5 * digitLongitudeWidth / (double)ringMultiplier);
							ArcRenderer arc = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
							arc.setMaintainAspectRatio(true);
							
							double spacing = digitSpacing[digit][7 - latCount][lonCount] * (double)ringMultiplier;
							
							double tempLat = Math.toDegrees(Math.atan2(y, Math.sqrt(spacing * spacing - y * y)));
							
							Double p2d = projectionTransformer.updateLocation(tempLat, tempLon, true, spacing);
							if (p2d != null) {
								arc.setArcAngle(360);
								arc.setFill(true);
								arc.setWidth(size);
								arc.setHeight(size);
								arc.setX(p2d.x);
								arc.setY(p2d.y);

								renderers.add(arc);
							}
						}
					}
				}
			}
		}
	}
}
