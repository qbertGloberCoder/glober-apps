package me.qbert.skywatch.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;
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

public class DigitalClockImageRenderer extends AbstractImageRenderer {
	private BufferedImage backgroundImage;
	private double inAspect;
	
	private BufferedImage sourceImageBi;
	
	private BufferedImage lastBi;
	
	private Double aspectRatioOverride;

	private int inWidth;
	private int inHeight;
	
	private BufferedImage [] digitPixels;
//	private int [][] digitPixels;
	private int [] digitMaskPixels;
	private BufferedImage decorations;
//	private int [] decorationPixels;
	private int [] decorationMaskPixels;

	private Integer lastHour;
	private Integer lastMinute;
	private Integer lastSecond;
	private Integer lastFraction;
	private boolean last24HourMode;
	
	private Point [] amBounds;
	private Point [] pmBounds;
	private Point [] hrMnBounds;
	private Point [] mnScBounds;
	private Point [] scFracBounds;
	private Point [][] digitsBounds;
	
	private boolean initialized = false;
	
	public DigitalClockImageRenderer(File backgroundImageFile, File [] digitsImageFiles, File digitMaskFile, File decorations, File decorationsMask) {
		initialized = false;
		if (backgroundImageFile != null) {
			try {
				backgroundImage = loadImageFromFile(backgroundImageFile);
			} catch (Exception e) {
				e.printStackTrace();
				backgroundImage = null;
			}
		} else {
			backgroundImage = null;
		}
		
		if (backgroundImage != null) {
			inWidth = backgroundImage.getWidth();
			inHeight = backgroundImage.getHeight();
			inAspect = (double)(inWidth) / (double)inHeight;
			
			digitPixels = new BufferedImage[digitsImageFiles.length]; //[inWidth * inHeight];
			
			for (int i = 0;i < digitsImageFiles.length;i ++) {
				try {
					digitPixels[i] = loadImageFromFile(digitsImageFiles[i]);
//					bi.getRGB(0, 0, inWidth, inHeight, digitPixels[i], 0, inWidth);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}

			try {
				BufferedImage bi = loadImageFromFile(digitMaskFile);
				digitMaskPixels = bi.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				this.decorations = loadImageFromFile(decorations);
//				decorationPixels = bi.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
			} catch (Exception e) {
				e.printStackTrace();
			}

			try {
				BufferedImage bi = loadImageFromFile(decorationsMask);
				decorationMaskPixels = bi.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		setBounds();
		
		sourceImageBi = new BufferedImage(inWidth, inHeight, BufferedImage.TYPE_INT_ARGB);
		
		initialized = true;
	}
	
	private void setBounds() {
		setDigitsBounds();
		setDecorationsBounds();
	}
	
	private void setDigitsBounds() {
		int topEnd = 0;
		int bottomStart = inHeight;
		
		int leftX = 0;
		int division = 0;
		
		digitsBounds = new Point[9][2];
		
		for (int x = 0;x < inWidth;x ++) {
			int lastMask = 0x12345678;
			boolean rowWithMask = false;
			for (int y = 0;y < inHeight;y ++) {
				int thisMaskPixel = digitMaskPixels[y * inWidth + x];
				
				if ((thisMaskPixel & 0xFF000000) != 0x0)
					rowWithMask = true;
				
				if ((y > 0) && (lastMask != thisMaskPixel)) {
					if ((thisMaskPixel & 0xFF000000) == 0x0) {
						// transition TO deadspace
						if (topEnd < y)
							topEnd = y;
					} else {
						// transition FROM deadspace
						if (bottomStart > y)
							bottomStart = y;
					}
				}
				
				lastMask = thisMaskPixel;
			}
			
			if (x == inWidth - 1)
				rowWithMask = false;

			if (! rowWithMask) {
				if (leftX < x - 1) {
					Point [] bound = makeBounds(leftX, 0, x, inHeight);
					digitsBounds[division][0] = bound[0];
					digitsBounds[division][1] = bound[1];
					
					topEnd = 0;
					bottomStart = inHeight;
					division ++;
				}
				leftX = x;
			}
		}
	}
	
	
	private void setDecorationsBounds() {
		int topEnd = 0;
		int bottomStart = inHeight;
		
		int leftX = 0;
		int division = 0;
		
		for (int x = 0;x < inWidth;x ++) {
			int lastMask = 0x12345678;
			boolean rowWithMask = false;
			for (int y = 0;y < inHeight;y ++) {
				int thisMaskPixel = decorationMaskPixels[y * inWidth + x];
				
				if ((thisMaskPixel & 0xFF000000) != 0x0)
					rowWithMask = true;
				
				if ((y > 0) && (lastMask != thisMaskPixel)) {
					if ((thisMaskPixel & 0xFF000000) == 0x0) {
						// transition TO deadspace
						if (topEnd < y)
							topEnd = y;
					} else {
						// transition FROM deadspace
						if (bottomStart > y)
							bottomStart = y;
					}
				}
				
				lastMask = thisMaskPixel;
			}

			if (x == inWidth - 1)
				rowWithMask = false;

			if (! rowWithMask) {
				if (leftX < x - 1) {
					if (division == 0) {
						amBounds = makeBounds(leftX, 0, x, topEnd);
						pmBounds = makeBounds(leftX, bottomStart, x, inHeight);
						topEnd = 0;
						bottomStart = inHeight;
						division ++;
					}
					else if (division == 1) {
						hrMnBounds = makeBounds(leftX, 0, x, inHeight);
						topEnd = 0;
						bottomStart = inHeight;
						division ++;
					}
					else if (division == 2) {
						mnScBounds = makeBounds(leftX, 0, x, inHeight);
						topEnd = 0;
						bottomStart = inHeight;
						division ++;
					}
					else if (division == 3) {
						scFracBounds = makeBounds(leftX, 0, x, inHeight);
						topEnd = 0;
						bottomStart = inHeight;
						division ++;
					}
				}
				leftX = x;
			}
		}
	}
	
	private Point [] makeBounds(int leftX, int topY, int rightX, int bottomY) {
		Point [] p = new Point[2];
		
		p[0] = new Point(leftX, topY);
		p[1] = new Point(rightX, bottomY);
		
		return p;
	}
	
	public Double getAspectRatioOverride() {
		return aspectRatioOverride;
	}

	public void setAspectRatioOverride(Double aspectRatioOverride) {
		this.aspectRatioOverride = aspectRatioOverride;
	}

	@Override
	public double getAspectRatio() {
		if (aspectRatioOverride != null)
			return aspectRatioOverride.doubleValue();
		
		return getBoundaryWidth() / getBoundaryHeight();
	}
	
	public void setTime(Integer hour, Integer minute, Integer second, Integer fraction, boolean twentyFourHourMode) {
		if ((! isRenderComponent()) || (hour == null) || (! initialized))
			return;

		if ((lastBi != null) && (lastHour != null) && (lastHour.compareTo(hour) == 0) &&
				(lastMinute != null) && (lastMinute.compareTo(minute) == 0) &&
				(lastSecond != null) && (lastSecond.compareTo(second) == 0) &&
				((lastFraction == null) || (lastFraction.compareTo(fraction)) == 0) &&
				(last24HourMode == twentyFourHourMode))
			return;
		
		
		Graphics2D g2d = sourceImageBi.createGraphics();
		g2d.drawImage(backgroundImage, 0, 0, inWidth, inHeight, 0, 0, inWidth, inHeight, null);
		
//		int [] destPixels = sourceImageBi.getRGB(0, 0, inWidth, inHeight, null, 0, inWidth);
		
		if (! twentyFourHourMode) {
			if (hour < 12) {
				copySegment(g2d, decorations, amBounds);
//				copySegment(inWidth, inHeight, destPixels, decorationPixels, amBounds);
				if (hour == 0)
					hour = 12;
			} else {
				copySegment(g2d, decorations, pmBounds);
//				copySegment(inWidth, inHeight, destPixels, decorationPixels, pmBounds);
				if (hour > 12)
					hour -= 12;
			}
		}
		
		int segment = hour;
		int digit;
		if (segment > 9) {
			digit = segment / 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[0]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[0]);
		}
		digit = segment % 10;
		copySegment(g2d, digitPixels[digit], digitsBounds[1]);
//		copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[1]);
	    
		if (minute != null) {
			copySegment(g2d, decorations, hrMnBounds);
//			copySegment(inWidth, inHeight, destPixels, decorationPixels, hrMnBounds);
			
			digit = minute / 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[2]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[2]);
			digit = minute % 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[3]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[3]);
		}
	    
		if (second != null) {
			copySegment(g2d, decorations, mnScBounds);
//			copySegment(inWidth, inHeight, destPixels, decorationPixels, mnScBounds);
			
			digit = second / 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[4]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[4]);
			digit = second % 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[5]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[5]);
		}
	    
		if (fraction != null) {
			copySegment(g2d, decorations, scFracBounds);
//			copySegment(inWidth, inHeight, destPixels, decorationPixels, scFracBounds);
			
			segment = fraction;
			digit = segment / 100;
			segment = segment % 100;
			copySegment(g2d, digitPixels[digit], digitsBounds[6]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[6]);
			digit = segment / 10;
			segment = segment % 10;
			copySegment(g2d, digitPixels[digit], digitsBounds[7]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[7]);
			digit = segment;
			copySegment(g2d, digitPixels[digit], digitsBounds[8]);
//			copySegment(inWidth, inHeight, destPixels, digitPixels[digit], digitsBounds[8]);
		}
		
//		sourceImageBi.setRGB(0, 0, inWidth, inHeight, destPixels, 0, inWidth);

	    g2d.dispose();

	    lastHour = hour;
	    lastMinute = minute;
	    lastSecond = second;
	    lastFraction = fraction;
	    last24HourMode = twentyFourHourMode;
	    
		int outWidth = Math.max((int)getBoundaryWidth(), 32);
		int outHeight = Math.max((int)getBoundaryHeight(), 32);
		
		lastBi = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
		
		int lx = (int)getBoundaryLeft();
		int ty = (int)getBoundaryTop();
		int rx = (int)getBoundaryWidth() + lx;
		int by = (int)getBoundaryHeight() + ty;
		
		double lxFromPct = getBoundMinimumXFraction() * outWidth;
		double wFromPct = (getBoundMaximumXFraction() * outWidth) - lxFromPct;
		double tyFromPct = getBoundMinimumYFraction() * outHeight;
		double hFromPct = (getBoundMaximumYFraction() * outHeight) - tyFromPct;
		
		double outAspect = (wFromPct / hFromPct);
		
//		System.out.println("??? " + inAspect + " --> " + outAspect);
		if (outAspect > inAspect) {
			// output is wider than the clock
			int w = (int)(hFromPct * inAspect);
			lx = (int)(lxFromPct + (wFromPct - w) / 2);
			rx = lx + w;
		} else {
			// output is higher than wider for the clock
			int h = (int)(wFromPct / inAspect);
			ty = (int)(tyFromPct + ((hFromPct - h) / 2));
			by = ty + h;
		}
		
 		g2d = lastBi.createGraphics();
		g2d.drawImage(sourceImageBi, lx, ty, rx, by, 0, 0, inWidth, inHeight, null);
	    g2d.dispose();

	    setOriginalImage(lastBi);
	}
	
	private void copySegment(Graphics2D g2d, BufferedImage sourceImage, Point [] segmentBounds) {
		int leftX = (int)(segmentBounds[0].x);
		int rightX = (int)(segmentBounds[1].x);
		int topY = (int)(segmentBounds[0].y);
		int bottomY = (int)(segmentBounds[1].y);

		g2d.drawImage(sourceImage, leftX, topY, rightX, bottomY, leftX, topY, rightX, bottomY, null);
	}
	
/*	private void copySegment(int outWidth, int outHeight, int [] destinationPixels, int [] sourcePixels, Point [] segmentBounds) {
		double xRatio = (double)inWidth / (double)outWidth;
		double yRatio = (double)inHeight / (double)outHeight;
		
//		System.out.println("??? " + outWidth + ", " + outHeight + " and " + destinationPixels.length + " compared to: " + sourcePixels.length);
		
		int leftX = (int)(segmentBounds[0].x / xRatio);
		int rightX = (int)(segmentBounds[1].x / xRatio);
		int topY = (int)(segmentBounds[0].y / yRatio);
		int bottomY = (int)(segmentBounds[1].y / yRatio);
		for (int y = topY;y < bottomY;y ++) {
			for (int x = leftX;x < rightX;x ++) {
				int oOffset = y * outWidth + x;
				int iOffset = (int)((y * yRatio) * inWidth + (x * xRatio));
				if ((iOffset >= 0) && (iOffset < sourcePixels.length) && (oOffset >= 0) && (oOffset < destinationPixels.length) && 
						((sourcePixels[iOffset] &0xFF000000) != 0))
					destinationPixels[oOffset] = sourcePixels[iOffset];
			}
		}
	} */
}
