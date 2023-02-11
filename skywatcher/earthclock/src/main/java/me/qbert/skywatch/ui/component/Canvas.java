package me.qbert.skywatch.ui.component;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import me.qbert.ui.RendererI;

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
	private static final long serialVersionUID = -8096630972921259096L;

	private List<RendererI> renderers = new ArrayList<RendererI>();
	
	private boolean recordMode = false;
	private boolean repaintPanelFromImage = false;
	private BufferedImage repaintImageBuffer = null;
	
	private boolean currentlyRendering = false;
	
	private static Boolean linuxMachine = null;

    public void paintToImage(BufferedImage drawToImage, boolean repaintPanelFromImage) {
    	this.repaintPanelFromImage = repaintPanelFromImage;
    	this.repaintImageBuffer = drawToImage;
    	
		Graphics2D g2d = drawToImage.createGraphics();
		
		renderComponents(g2d, drawToImage.getWidth(), drawToImage.getHeight());
		
		g2d.dispose();
    }
    
    private void renderComponents(Graphics2D g2d, int width, int height) {
        RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        rh.put(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2d.setRenderingHints(rh);
        
        for (RendererI renderer : renderers) {
        	AffineTransform oldXForm = g2d.getTransform();
        	renderer.setRenderDimensions(0, 0, width, height);
        	renderer.renderComponent(g2d);
        	g2d.setTransform(oldXForm);
        }
    }
    
    @Override
    public void repaint() {
    	currentlyRendering = true;
    	super.repaint();
    }

    @Override
    public void paintComponent(Graphics g) {
    	currentlyRendering = true;
    	
        super.paintComponent(g);
        
        int width = getWidth();
        int height = getHeight();

        Graphics2D g2d = (Graphics2D) g.create();

        if ((repaintPanelFromImage == true) && (repaintImageBuffer != null)) {
        	int leftX = 0;
        	int topY = 0;
        	int rightX = width;
        	int bottomY = height;
        	
        	double aspectDifference = ((double)repaintImageBuffer.getWidth() / (double)repaintImageBuffer.getHeight()) - ((double)width / (double)height);
        	
        	if (aspectDifference > 0.0) {
        		bottomY = (int)((double)width * (double)repaintImageBuffer.getHeight() / (double)repaintImageBuffer.getWidth());
        		topY = (height - bottomY) / 2;
        		bottomY += topY;
        	} else if (aspectDifference < 0.0) {
        		rightX = (int)((double)height * (double)repaintImageBuffer.getWidth() / (double)repaintImageBuffer.getHeight());
        		leftX = (width - rightX) / 2;
        		rightX += leftX;
        	}
        	
            RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            rh.put(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);

            g2d.setRenderingHints(rh);
        	g2d.drawImage(repaintImageBuffer, leftX, topY, rightX, bottomY, 0, 0, repaintImageBuffer.getWidth(), repaintImageBuffer.getHeight(), this);
        } else {
	        try {
	            
	            renderComponents(g2d, width, height);
	        } catch (Exception e) {
	        	currentlyRendering = false;
	            throw e;
	        }
        }
        
        if (recordMode) {
        	g2d.setColor(Color.red);
        	g2d.setBackground(Color.red);
        	g2d.fillArc(width - 30, 10, 20, 20, 0, 360);
        }
        
        currentlyRendering = false;
    }
    
    public void setRecordMode(boolean recordMode) {
    	this.recordMode = recordMode;
    }
    
    public boolean isRecordMode() {
    	return recordMode;
    }
    
    public void clearRepaintPanelFromImage() {
    	repaintPanelFromImage = false;
    }

	public List<RendererI> getRenderers() {
		return renderers;
	}

	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
	}
	
	public boolean isCurrentlyRendering() {
		return currentlyRendering;
	}
}
