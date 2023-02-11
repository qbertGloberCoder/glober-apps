package me.qbert.ui.renderers;

<<<<<<< HEAD
import java.awt.image.BufferedImage;
import java.io.File;
=======
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)

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

<<<<<<< HEAD
public class ImageRenderer extends AbstractImageRenderer {
	public ImageRenderer(File imageFile) {
		this(imageFile, null);
	}
	
	public ImageRenderer(File imageFile, File overlayFile) {
		super();

		reinitImage(imageFile);
		reinitOverlay(overlayFile);
	}
	
	public void reinitImage(File imageFile) {
		BufferedImage image;
		
		if (imageFile != null) {
			try {
				image = loadImageFromFile(imageFile);
			} catch (Exception e) {
				e.printStackTrace();
				image = null;
			}
		} else {
			image = null;
		}
		
		setOriginalImage(image);
	}
	
	public void reinitOverlay(File overlayFile) {
		BufferedImage image;
		
		if ((overlayFile != null) && (overlayFile.exists())) {
			try {
				image = loadImageFromFile(overlayFile);
			} catch (Exception e) {
				e.printStackTrace();
				image = null;
			}
		} else {
			image = null;
		}
		
		setOriginalOverlay(image);
=======
public class ImageRenderer extends AbstractFractionRenderer {
	private BufferedImage originalImage;
	private BufferedImage image;

	private double lastRotatedAngle = -9999999999.999;
	private double rotateAngle;
	private double rotateX = -1;
	private double rotateY = -1;
	
	private double boundMinimumXFraction;
	private double boundMinimumYFraction;
	private double boundMaximumXFraction;
	private double boundMaximumYFraction;
	
	public ImageRenderer(File imageFile) {
		boundMinimumXFraction = boundMinimumYFraction = 0.0;
		boundMaximumXFraction = boundMaximumYFraction = 1.0;

		try {
			originalImage = loadImageFromFile(imageFile);
		} catch (Exception e) {
			originalImage = null;
		}
	}

    private BufferedImage loadImageFromFile(File file) throws NullPointerException,IOException {
    	BufferedImage newFile = null;
    	
    	newFile = ImageIO.read(file);
    	try {
    		while (newFile.getWidth(null) == -1)
    			Thread.sleep(500);
    	} catch (InterruptedException e) {
    		newFile = null;
    	} catch (NullPointerException e) {
    		// Let's print the offending file name and pretend no file was loaded
    		System.out.println("Unable to load file: " + file.getName());
    		newFile = null;
    		throw e;
    	}
    	
    	return newFile;
    }
    
    private void resetImage() {
    	image = null;
    	if (originalImage == null)
    		return;
    	
    	lastRotatedAngle = rotateAngle;
    	
    	if (rotateAngle == 0.0)
    		image = originalImage;
    	
    	image = new BufferedImage(originalImage.getWidth(),
	    		originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g2d = image.createGraphics();
//	    g2d.drawImage(originalImage, 0, 0, null);
	    
	    if ((rotateX < 0) || (rotateY < 0))
	    	g2d.rotate(Math.toRadians(rotateAngle), originalImage.getWidth() / 2, originalImage.getHeight() / 2);
	    else
	    	g2d.rotate(Math.toRadians(rotateAngle), rotateX, rotateY);
	    g2d.drawImage(originalImage, null, 0, 0);
        
	    g2d.dispose();
    }
    
	@Override
	public void renderComponent(Graphics2D g2d) {
		if ((image == null) || (lastRotatedAngle != rotateAngle)) {
			resetImage();
		}
		
		if (image == null)
			return;
		
		
		double boundLeftX = getBoundaryLeft();
		double boundTopY = getBoundaryTop();
		double width = getBoundaryWidth();
		double height = getBoundaryHeight();
		
		int boundaryLeft = (int)(boundLeftX + (boundMinimumXFraction * width));
		int boundaryRight = (int)(boundLeftX + (boundMaximumXFraction * width));
		int boundaryTop = (int)(boundTopY + (boundMinimumYFraction * height));
		int boundaryBottom = (int)(boundTopY + (boundMaximumYFraction * height));
		
    	g2d.drawImage(image, boundaryLeft, boundaryTop, boundaryRight, boundaryBottom, 0, 0, image.getWidth(), image.getHeight(), null);
	}

	public double getRotateAngle() {
		return rotateAngle;
	}

	public void setRotateAngle(double rotateAngle) {
		this.rotateAngle = rotateAngle;
	}

	public double getRotateX() {
		return rotateX;
	}

	public void setRotateX(double rotateX) {
		this.rotateX = rotateX;
	}

	public double getRotateY() {
		return rotateY;
	}

	public void setRotateY(double rotateY) {
		this.rotateY = rotateY;
	}

	public double getBoundMinimumXFraction() {
		return boundMinimumXFraction;
	}

	public void setBoundMinimumXFraction(double boundMinimumXFraction) {
		this.boundMinimumXFraction = boundMinimumXFraction;
	}

	public double getBoundMinimumYFraction() {
		return boundMinimumYFraction;
	}

	public void setBoundMinimumYFraction(double boundMinimumYFraction) {
		this.boundMinimumYFraction = boundMinimumYFraction;
	}

	public double getBoundMaximumXFraction() {
		return boundMaximumXFraction;
	}

	public void setBoundMaximumXFraction(double boundMaximumXFraction) {
		this.boundMaximumXFraction = boundMaximumXFraction;
	}

	public double getBoundMaximumYFraction() {
		return boundMaximumYFraction;
	}

	public void setBoundMaximumYFraction(double boundMaximumYFraction) {
		this.boundMaximumYFraction = boundMaximumYFraction;
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
	}
}
