package me.qbert.skywatch.ui.renderers;

import java.awt.Graphics2D;
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

public class EquirectilinearScrollImageRenderer extends AbstractImageRenderer {
	private BufferedImage equirectinearImage;
	private BufferedImage lastBi;
	
	private double lastLon;

	public EquirectilinearScrollImageRenderer(File equirectinearImageFile) {
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

	
	public void wrapToCoordinates(double longitude) {
		if ((lastBi != null) && (longitude == lastLon))
			return;
		
		
		int inWidth = equirectinearImage.getWidth();
		int inHeight = equirectinearImage.getHeight();
		
		int outWidth = inWidth;
		int outHeight = inHeight;
		
		lastBi = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_ARGB);
		
		Graphics2D g2d = lastBi.createGraphics();
		
		while (longitude < -180.0)
			longitude += 360.0;
		while (longitude > 180.0)
			longitude -= 360.0;
		
		if (longitude < 0.0) {
			int sourceSplit = inWidth - (int)((double)inWidth * (0.0 - longitude) / 360);
			if (sourceSplit == 0)
				g2d.drawImage(equirectinearImage, 0, 0, outWidth, outHeight, 0, 0, inWidth, inHeight, null);
			else {
				g2d.drawImage(equirectinearImage, 0, 0, (outWidth - sourceSplit), outHeight, sourceSplit, 0, inWidth, inHeight, null);
				g2d.drawImage(equirectinearImage, (outWidth - sourceSplit), 0, outWidth, outHeight, 0, 0, sourceSplit, inHeight, null);
			}
		} else {
			int sourceSplit = (int)((double)inWidth * longitude / 360);
			if (sourceSplit == 0)
				g2d.drawImage(equirectinearImage, 0, 0, outWidth, outHeight, 0, 0, inWidth, inHeight, null);
			else {
				g2d.drawImage(equirectinearImage, 0, 0, (outWidth - sourceSplit), outHeight, sourceSplit, 0, inWidth, inHeight, null);
				g2d.drawImage(equirectinearImage, (outWidth - sourceSplit), 0, outWidth, outHeight, 0, 0, sourceSplit, inHeight, null);
			}
		}
		
	    g2d.dispose();
		
	    lastLon = longitude;
	    
	    setOriginalImage(lastBi);
	}
}
