package me.qbert.skywatch.ui.renderers;

import java.awt.image.BufferedImage;
import java.io.File;

import me.qbert.ui.renderers.AbstractImageRenderer;

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

public class GlobeImageRenderer extends AbstractImageRenderer {
	private BufferedImage equirectinearImage;
	
	private double zoomLevel = 1.0;
	
	private double lastLat;
	private double lastLon;
	private boolean lastZoom = false;
	private double lastZoomLevel = 0.0;
	private BufferedImage lastBi;

	private boolean zoomedOut = true;
	private boolean leaveUnwrapped = false;
	
	private double circumferenceSizeFraction = 1.0;

	public GlobeImageRenderer(File equirectinearImageFile) {
		if (equirectinearImageFile != null) {
			try {
				equirectinearImage = loadImageFromFile(equirectinearImageFile);
			} catch (Exception e) {
				e.printStackTrace();
				equirectinearImage = null;
			}
		} else {
			equirectinearImage = null;
		}
	}

	
	public double getCircumferenceSizeFraction() {
		return circumferenceSizeFraction;
	}


	public void setCircumferenceSizeFraction(double circumferenceSizeFraction) {
		boolean rewrap = false;
		if (this.circumferenceSizeFraction != circumferenceSizeFraction) {
			lastZoomLevel = -9999;
			rewrap = true;
		}
		this.circumferenceSizeFraction = circumferenceSizeFraction;
		if (rewrap)
			wrapToCoordinates(lastLat, lastLon);
	}


	public void wrapToCoordinates(double latitude, double longitude) {
		if (leaveUnwrapped)
			return;

		if ((lastBi != null) && (latitude == lastLat) && (longitude == lastLon) && (zoomedOut == lastZoom) && (zoomLevel == lastZoomLevel))
			return;
		
		
		int outWidth = Math.max((int)getBoundaryWidth(), 1024);
		int outHeight = Math.max((int)getBoundaryHeight(), 1024);
		
		int inWidth = equirectinearImage.getWidth();
		int inHeight = equirectinearImage.getHeight();
		
		lastBi = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
		
	    int radius;
	    
	    int commonBorder = 0;
	    int borderLeft = commonBorder;
	    int borderTop = 128;
	    int borderRight = commonBorder;
	    int borderBottom = commonBorder;
	    
	    
	    double latRad = Math.toRadians(latitude);
	    double lonRad = Math.toRadians(longitude);

		double pixelsPerDegreeX = (double)((inWidth - borderLeft - borderRight) - 2) / 360.0;
		double pixelsPerDegreeY = (double)((inHeight - borderTop - borderBottom) - 2) / 180.0;
	    
	    if (outWidth > outHeight)
	    	radius = outHeight / 2;
	    else
	    	radius = outWidth / 2;
	    
	    if (zoomedOut)
	    	radius = (int)(radius * circumferenceSizeFraction);
	    
		int [] srcPixels = equirectinearImage.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
		int [] destPixels = lastBi.getRGB(0, 0, outWidth, outHeight, null, 0, outWidth);

		int destCenterX = outWidth / 2;
	    int destCenterY = outHeight / 2;
	    for (double yd = -radius;yd < radius;yd ++) {
	    	int y = (int) yd;
	    	double xm = Math.sqrt(radius*radius-yd*yd);
	    	for (double xd = -xm;xd < xm; xd ++) {
	    		int x = (int)xd;
	    		
	    		double tx = xd;
	    		double ty = yd;
	    		
	    		if (zoomLevel < 1.0) {
	    			tx *= zoomLevel;
	    			ty *= zoomLevel;
	    		}
	    		
	    		double tz = Math.sqrt(radius*radius-tx*tx-ty*ty);
	    		
	    		double tr;
	    		double ta;

	    		tr = Math.sqrt(ty*ty+tz*tz);
	    		ta = Math.atan2(tz, ty);
	    		ty = tr*Math.cos(ta - latRad);
	    		tz = tr*Math.sin(ta - latRad);
	    		
	    		tr = Math.sqrt(tx*tx+tz*tz);
	    		ta = Math.atan2(tz, tx);
	    		tx = tr*Math.cos(ta - lonRad);
	    		tz = tr*Math.sin(ta - lonRad);

	    		double mLat = Math.toDegrees(Math.atan2(ty,tr));
	    		double mLon = Math.toDegrees(Math.atan2(tx,tz));
	    		
	    		int sx = (int)((inWidth/2.0) + borderLeft - 4 + (mLon * pixelsPerDegreeX));
	    		int sy = (int)(((inHeight - borderTop - borderBottom) / 2.0) + borderTop - 4 - (mLat * pixelsPerDegreeY));
	    		
	    		try {
	    			int dstX = destCenterX + x;
	    			int dstY = destCenterY - y;
	    			if ((dstX >= 0) && (dstX < outWidth) && (dstY >= 0) && (dstY < outHeight) &&
	    				(sx >= 0) && (sx < inWidth) && (sy >= 0) && (sy < inHeight)) {
	    				destPixels[dstY * outWidth + dstX] = srcPixels[sy * inWidth + sx];
	    			}
	    		}
	    		catch (Exception e) {
	    		}
	    	}
	    }
	    
	    lastBi.setRGB(0, 0, outWidth, outHeight, destPixels, 0, outWidth);
	    
	    lastLat = latitude;
	    lastLon = longitude;
	    lastZoom = zoomedOut;
	    lastZoomLevel = zoomLevel;
	    
	    setOriginalImage(lastBi);
	}
}
