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

public class RingClockImageRenderer extends AbstractImageRenderer {
	private BufferedImage numbersImage;
	
	private double zoomLevel = 1.0;
	
	private double lastLHA = -99999999.0;
	private boolean lastZoom = false;
	private double lastZoomLevel = 0.0;
	private BufferedImage lastBi;

	private boolean zoomedOut = false;
	private boolean leaveUnwrapped = false;

	private double circumferenceSizeFraction = 1.0;
	
	private int inWidth;
	private int inHeight;
	private int [] srcPixels;
	private int [][] secondsRing;
	
	private int [][] digits;
	private int hr;
	private int mn;
	private int sc;
	private double fracSec;
	
	public RingClockImageRenderer(File numbersImageFile) {
		if (numbersImageFile != null) {
			try {
				numbersImage = loadImageFromFile(numbersImageFile);
			} catch (Exception e) {
				e.printStackTrace();
				numbersImage = null;
			}
		} else {
			numbersImage = null;
		}
		
		digits = new int[3][2];
		
		if (numbersImage != null)
			createRingTexts();
	}
	
	private void createRingTexts() {
		inWidth = numbersImage.getWidth();
		inHeight = numbersImage.getHeight();
		
		srcPixels = numbersImage.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
		
		int sourceDigitWidth = (int)(inWidth / 10);
		int sourceDigitMiddle = sourceDigitWidth/2;
		
		int ringWidth = sourceDigitWidth * 60;
		secondsRing = new int[inHeight][ringWidth];
		
		for (int y = 0;y < inHeight;y ++) {
			for (int x = 0;x < ringWidth;x ++) {
				int numberValue = x / sourceDigitWidth;
				int numberOffset = x % sourceDigitWidth;
				
				int sOffset;
				
				if (numberOffset < sourceDigitMiddle) {
					sOffset = (int)(numberValue/10);
				} else {
					sOffset = numberValue % 10;
				}
				
				sOffset = sOffset * sourceDigitWidth;
				
				secondsRing[y][x] = srcPixels[y * inWidth + sOffset + numberOffset];
			}
		}
		
	}
	
	@Override
	public double getAspectRatio() {
		return getBoundaryWidth() / getBoundaryHeight();
	}
	
	public double getCircumferenceSizeFraction() {
		return circumferenceSizeFraction;
	}

	public void setCircumferenceSizeFraction(double circumferenceSizeFraction) {
		this.circumferenceSizeFraction = circumferenceSizeFraction;
	}
	
	private void getDigits(double hourAngle) {
	    fracSec = hourAngle / 360.0 * 24.0;
	    hr = (int)(fracSec);
	    fracSec -= (hr);
	    fracSec *= 60.0;
	    mn = (int)fracSec;
	    fracSec -= (mn);
	    fracSec *= 60;
	    sc = (int)fracSec;
	    fracSec -= sc;
	    
	    digits[0][0] = hr / 10;
	    digits[0][1] = hr % 10;
	    digits[1][0] = mn / 10;
	    digits[1][1] = mn % 10;
	    digits[2][0] = sc / 10;
	    digits[2][1] = sc % 10;
	}
	
	public void wrapToLHA(double hourAngle) {
		if (leaveUnwrapped)
			return;
		
		if (! isRenderComponent())
			return;

		if ((lastBi != null) && (hourAngle == lastLHA) && (zoomedOut == lastZoom) && (zoomLevel == lastZoomLevel))
			return;
		
		
		int outWidth = Math.max((int)getBoundaryWidth(), 32);
		int outHeight = Math.max((int)getBoundaryHeight(), 32);
		
		lastBi = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
		
	    int radius;
	    
	    if (outWidth > outHeight)
	    	radius = outHeight / 2;
	    else
	    	radius = outWidth / 2;
	    
	    if (zoomedOut)
	    	radius /= 3.0;
	    
	    getDigits(hourAngle);
//	    System.out.println("?? DISPLAY TIME: " + String.format("%02d:%02d:%02d AND %8.3g", hr, mn, sc, fracSec));
	    
		int [] destPixels = lastBi.getRGB(0, 0, outWidth, outHeight, null, 0, outWidth);
		
		int middleX = outWidth / 2;
		int middleY = outHeight / 2;
		
		int squareDistance = middleY;
		
		if (middleX < middleY)
			squareDistance = middleX;
		
		double drawRatio = (squareDistance / 5.0 * circumferenceSizeFraction);
		
		int yTop = (int)(middleY - drawRatio);
		int yThird = (int)(2.0 * drawRatio / 3.0);
		
		int sourceDigitWidth = (int)(inWidth / 10);
		int sourceDigitMiddle = sourceDigitWidth/2;
		
		int rightMarker = (int)(squareDistance * Math.sin(Math.toRadians(360.0 / 48)));
		
		double multiplyRatioX = (sourceDigitWidth - 4) / (rightMarker * 2);
		double multiplyRatioY = (double)(inHeight) / (double)yThird; //((outHeight - (yTop*2)) / 3);
		
		int leftMarker = middleX - rightMarker;
		rightMarker += middleX;
		
		for (int y = yTop;y < outHeight - yTop;y ++) {
			int band = (y - yTop) / yThird;
			
			int ringRadius = 0;
			
			int srcDigitsWidth = secondsRing[0].length;
			
			double divideFactor = 1.0;
			int scXBias = 0;
			
			double lha = 90.0 - hourAngle;
			
			if (band == 0) {
				ringRadius = squareDistance;
				srcDigitsWidth = sourceDigitWidth * 24;
			} else if (band == 1) {
				divideFactor = 0.4;
				ringRadius = squareDistance;
//				ringRadius = (squareDistance * 60);
				
//				scXBias = 7 * sourceDigitWidth;
//				lha = lha - (int)lha;
//				lha *= 360;
//				lha = lha - 90.0;
				
				lha = 90.0 - 360.0*((mn / 60.0) + ((double)sc + fracSec)/3600)/divideFactor;
			} else {
				divideFactor = 0.4;
				ringRadius = squareDistance;

				//				divideFactor = 1800;
//				ringRadius = (squareDistance * 3600);
				
//				lha *= 24;
//				lha *= 60;
				lha = 90.0 - 360.0*((double)sc + fracSec) / (60.0 * divideFactor);
//				scXBias = -8 * sourceDigitWidth;
//				lha = lha - (int)lha;
//				lha *= 360.0;
//				lha = lha - (int)lha;
//				lha *= 360.0;
			}
			
			boolean overflowed = false;
			
			int setColor = 0x404FFFFF;
			if ((y - yTop) % yThird == 0)
				setColor = 0xFF000000;
			
			if (((y - yTop) <= (yThird / 8)) || (((outHeight - yTop - y) <= (yThird / 8)))) {
				if (((y - yTop) == (yThird / 8)) || (((outHeight - yTop - y) == (yThird / 8))))
					setColor = 0xFF000000;
				else
					setColor = 0x00000000;
			}
			
//			double latAngleRad = Math.atan2((middleY - y), ringRadius);
			double circumference = ringRadius;
			
			int drawRadius = (int)circumference;
			if (drawRadius > middleX) {
				drawRadius = middleX;
				overflowed = true;
			}
			
			int leftX =  middleX - drawRadius;
			int rightX = outWidth - middleX + drawRadius - 1;
			
			for (int x = leftX;x <= rightX; x ++) {
				int useColor = setColor;
				
				double pixelAngleRad = Math.acos(((double)x - (double)middleX) / (double)ringRadius);
//				getDigits(hourAngle + Math.toDegrees(pixelAngleRad));
				
//				if (y == yTop + yThird + (yThird/2))
//					System.out.println("?? GOT x: " + (x - middleX) + " and " + ringRadius + " to " + Math.toDegrees(pixelAngleRad));
				
				if ((x > leftMarker) && (x < rightMarker)) {
					if  ((y - yTop) % yThird == 0)
						useColor = 0xFF000000;
					else
						useColor = 0x40FF7F7F;
				}
				
				if ((x == leftMarker) || (x == rightMarker))
					destPixels[(int)y * outWidth + x] = 0xFF000000;
				else if (band < 3) {
					int spos = (int)(srcDigitsWidth / 2.0 * (double)divideFactor * (Math.PI - (Math.toRadians(lha) + pixelAngleRad))/ (Math.PI)) - scXBias + sourceDigitMiddle; //Math.sin(pixelAngleRad)); //(int)((double)(x - middleX) * Math.cos(pixelAngleRad) * multiplyRatioX) + offset;
					
					while (spos < 0)
						spos += srcDigitsWidth;
					while (spos >= srcDigitsWidth)
						spos -= srcDigitsWidth;
					
/*					while (spos > inWidth)
						spos -= inWidth;
					while (spos < 0)
						spos += inWidth; */
					
					int srcOffsetX = spos; //digits[band][digitIdx] * sourceDigitWidth + spos;
					int srcOffsetY = (int)(((y - yTop) % yThird) * multiplyRatioY);
					
//					srcOffsetX = x;
					
					if ((srcOffsetY >= 0) && (srcOffsetY < inHeight) && (srcOffsetX >= 0) && (srcOffsetX < srcDigitsWidth)) {
						if (secondsRing[srcOffsetY][srcOffsetX] != 0x0000000)
							destPixels[(int)y * outWidth + x] = secondsRing[srcOffsetY][srcOffsetX];
						else
							destPixels[(int)y * outWidth + x] = useColor;
					} else {
						destPixels[(int)y * outWidth + x] = useColor;
					}
//					destPixels[(int)y * outWidth + x] = 0xFFA070F0;
				} else if ((((x == leftX) || (x == rightX)) && (! overflowed)))
					destPixels[(int)y * outWidth + x] = 0xFF000000;
				else
					destPixels[(int)y * outWidth + x] = useColor;
			}
		}
	    
	    lastBi.setRGB(0, 0, outWidth, outHeight, destPixels, 0, outWidth);
	    
	    lastZoom = zoomedOut;
	    lastZoomLevel = zoomLevel;
	    
	    setOriginalImage(lastBi);
	}
}
