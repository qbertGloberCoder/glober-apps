package me.qbert.cbtools.ui.component;

import java.awt.Graphics;
import java.awt.Graphics2D;
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
        
        g2d.drawImage(originalImage, 0, 0, getWidth(), getHeight(), 0, 0, originalImage.getWidth(), originalImage.getHeight(), null);
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
    
}
