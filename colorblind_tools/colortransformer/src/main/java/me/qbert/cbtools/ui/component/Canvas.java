package me.qbert.cbtools.ui.component;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

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

public class Canvas extends JPanel {
	private class StackedImageCoordinates {
		Point originalImageTopLeft;
		Point modifiedImageTopLeft;
		int imageWidth;
		int imageHeight;
	}
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -6631139156484663442L;
	
	private BufferedImage image = null;
	private BufferedImage originalImage = null;
	private File imageFile = null;
	
    private void doDrawing(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();
        
        

        RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        rh.put(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2d.setRenderingHints(rh);
        
        StackedImageCoordinates stackedCoords = getStackedImageCoordinates();
        
        g2d.drawImage(originalImage, stackedCoords.originalImageTopLeft.x, stackedCoords.originalImageTopLeft.y,
        		stackedCoords.originalImageTopLeft.x + stackedCoords.imageWidth, stackedCoords.originalImageTopLeft.y + stackedCoords.imageHeight, 
        		0, 0, originalImage.getWidth(), originalImage.getHeight(), null);
        g2d.drawImage(image, stackedCoords.modifiedImageTopLeft.x, stackedCoords.modifiedImageTopLeft.y,
        		stackedCoords.modifiedImageTopLeft.x + stackedCoords.imageWidth, stackedCoords.modifiedImageTopLeft.y + stackedCoords.imageHeight, 
        		0, 0, originalImage.getWidth(), originalImage.getHeight(), null);
    }

    @Override
    public void paintComponent(Graphics g) {

        super.paintComponent(g);
        doDrawing(g);
    }

	public void loadImage(File file) throws IOException {
    	if (file == null) {
    		image = null;
    		file = null;
    		return;
    	}
    	
    	if ((this.imageFile != null) && (image != null)) {
    		if (this.imageFile.getAbsolutePath().equals(file.getAbsolutePath())) {
    			return;
    		}
    	}
    	
    	this.imageFile = file;
    	
    	image = ImageIO.read(file);
    	try {
    		while (image.getWidth(null) == -1)
    			Thread.sleep(500);
    	} catch (InterruptedException e) {
    		image = null;
    		this.imageFile = null;
    	} catch (NullPointerException e) {
    		// Let's print the offending file name and pretend no file was loaded
    		System.out.println("Unable to load file: " + file.getName());
    		image = null;
    		file = null;
    		this.imageFile = null;
    		return;
    	}
    	
    	if (image != null) {
    		originalImage = clone(image);
    	}
		
	}

	private BufferedImage clone(BufferedImage image) {
	    BufferedImage clone = new BufferedImage(image.getWidth(),
	            image.getHeight(), image.getType());
	    Graphics2D g2d = clone.createGraphics();
	    g2d.drawImage(image, 0, 0, null);
	    g2d.dispose();
	    return clone;
	}
	
	private StackedImageCoordinates getStackedImageCoordinates() {
		int outputWidth = getWidth();
		int outputHeight = getHeight();
		
		int originalWidth = originalImage.getWidth();
		int originalHeight = originalImage.getHeight();
		
		int modifiedWidth = image.getWidth();
		int modifiedHeight = image.getHeight();
		
		double outputAspect = (double)outputWidth / (double)outputHeight;
		double originalAspect = (double)originalWidth / (double)originalHeight;
		double modifiedAspect = (double)modifiedWidth / (double)modifiedHeight;
		
		StackedImageCoordinates coords = new StackedImageCoordinates();
		
		// if it's landscape, aspect ration will be >= 1.0
		if (originalAspect >= 1.0) {
			// stack them one above the other
			// calculate required width for the given height
			// if height is "getHeight() / 2", then
			// sx / sy = dx / (getHeight() / 2)
			// dx = (getHeight() / 2) * sx / sy
			int requiredX = (int)(((double)outputHeight / 2.0) * originalAspect);
			int requiredY = (int)(outputWidth / originalAspect);
			
			int x;
			int y;
			
			if (requiredX > outputWidth) {
				// we need to stack using the requiredY
				coords.imageWidth = outputWidth;
				coords.imageHeight = requiredY;
				
				x = 0;
				y = (int)((double)outputHeight / 2.0) - requiredY;
			} else {
				// we stack using the requiredX
				coords.imageWidth = requiredX;
				coords.imageHeight = (int)((double)outputHeight / 2.0);
				
				x = (int)((double)(outputWidth - requiredX) / 2.0);
				y = 0;
			}
			
			coords.originalImageTopLeft = new Point();
			coords.originalImageTopLeft.x = x;
			coords.originalImageTopLeft.y = y;
			
			coords.modifiedImageTopLeft = new Point();
			coords.modifiedImageTopLeft.x = x;
			coords.modifiedImageTopLeft.y = y + coords.imageHeight;
		} else {
			// stack them side by side
			// calculate required width for the given height
			// if width is "getWidth() / 2", then
			// sx / sy = (getWidth() / 2) / dy
			// dx = (getHeight() / 2) * sx / sy
			int requiredX = (int)(outputHeight * originalAspect);
			int requiredY = (int)(((double)outputWidth / 2.0) / originalAspect);
			
			int x;
			int y;
			
			if (requiredY > outputHeight) {
				// we need to stack using the requiredX
				coords.imageWidth = requiredX;
				coords.imageHeight = outputHeight;
				
				x = (int)((double)outputWidth / 2.0) - requiredX;
				y = 0;
			} else {
				// we stack using the requiredY
				coords.imageWidth = (int)((double)outputWidth / 2.0);
				coords.imageHeight = requiredY;

				x = 0;
				y = (int)((double)(outputHeight - requiredY)/ 2.0);
			}
			
			coords.originalImageTopLeft = new Point();
			coords.originalImageTopLeft.x = x;
			coords.originalImageTopLeft.y = y;
			
			coords.modifiedImageTopLeft = new Point();
			coords.modifiedImageTopLeft.x = x + coords.imageWidth;
			coords.modifiedImageTopLeft.y = y;
		}
		
		return coords;
	}
}
