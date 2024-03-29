package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

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

public abstract class AbstractImageRenderer extends AbstractFractionRenderer {
	private BufferedImage originalImage;
	private BufferedImage overlayImage;
	private BufferedImage image;

	private double lastRotatedAngle = -9999999999.999;
	private double rotateAngle;
	private double rotateX = -1;
	private double rotateY = -1;
	
	private double boundMinimumXFraction;
	private double boundMinimumYFraction;
	private double boundMaximumXFraction;
	private double boundMaximumYFraction;
	
	public AbstractImageRenderer() {
		boundMinimumXFraction = boundMinimumYFraction = 0.0;
		boundMaximumXFraction = boundMaximumYFraction = 1.0;
	}
	
	@Override
	public double getAspectRatio() {
		if (originalImage == null)
			return 1.0;
		
		return (double)originalImage.getWidth() / (double)originalImage.getHeight();
	}
	
	public void setOriginalImage(BufferedImage originalImage) {
		image = null;
		this.originalImage = originalImage;
	}

	public void setOriginalOverlay(BufferedImage overlayImage) {
		image = null;
		this.overlayImage = overlayImage;
	}

    protected BufferedImage loadImageFromFile(File file) throws NullPointerException,IOException {
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

    protected void resetImage() {
    	image = null;
    	if (originalImage == null)
    		return;
    	
    	lastRotatedAngle = rotateAngle;
    	
    	image = new BufferedImage((int)getBoundaryWidth(),
    			(int)getBoundaryHeight(), BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g2d = image.createGraphics();
//	    g2d.drawImage(originalImage, 0, 0, null);
	    
	    AffineTransform oldXForm = null;
	    
    	if (rotateAngle != 0.0) {
    		oldXForm = g2d.getTransform();
    		
		    if ((rotateX < 0) || (rotateY < 0))
		    	g2d.rotate(Math.toRadians(rotateAngle), image.getWidth() / 2, image.getHeight() / 2);
		    else
		    	g2d.rotate(Math.toRadians(rotateAngle), rotateX, rotateY);
    	}
    	
    	g2d.drawImage(originalImage, 0, 0, image.getWidth(), image.getHeight(), 0, 0, originalImage.getWidth(), originalImage.getHeight(), null);	    	
       
    	if (oldXForm != null)
    		g2d.setTransform(oldXForm);
    	
	    if (overlayImage != null) {
	    	g2d.drawImage(overlayImage, 0, 0, image.getWidth(), image.getHeight(), 0, 0, overlayImage.getWidth(), overlayImage.getHeight(), null);	    	
	    }
	    
	    g2d.dispose();
    }
    
	@Override
	public void renderComponent(Graphics2D g2d) {
		if (! isRenderComponent())
			return;
		
		if ((image == null) || (lastRotatedAngle != rotateAngle) || ((int)getBoundaryWidth() != image.getWidth()) ||
				((int)getBoundaryHeight() != image.getHeight())) {
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
	}
}
