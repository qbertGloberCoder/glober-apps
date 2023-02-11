package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import me.qbert.ui.ImageTransformerI;
import me.qbert.ui.RendererI;
import me.qbert.ui.coordinates.AbsoluteCoordinateTransformation;
import me.qbert.ui.coordinates.AbstractCoordinateTransformation;
import me.qbert.ui.coordinates.FractionCoordinateTransformation;

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

public class VirtualImageCanvasRenderer extends AbstractFractionRenderer {
	List<RendererI> renderers = new ArrayList<RendererI>();
	
	private BufferedImage image;
	
	private ImageTransformerI imageTransformer;
	
	private double rotateAngle;
	private double rotateX = -1;
	private double rotateY = -1;
	
	private double boundMinimumXFraction = 0.0;
	private double boundMinimumYFraction = 0.0;
	private double boundMaximumXFraction = 1.0;
	private double boundMaximumYFraction = 1.0;
	
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
	
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
	
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	public VirtualImageCanvasRenderer() {
		this(null);
	}

	public VirtualImageCanvasRenderer(ImageTransformerI imageTransformer) {
		this.imageTransformer = imageTransformer;
	}

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	@Override
	public double getAspectRatio() {
		return -1.0;
	}
	
<<<<<<< HEAD
=======
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
	private void resetImage() {
		double width = getBoundaryWidth();
		double height = getBoundaryHeight();
		image = new BufferedImage((int)width, (int)height, BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g2d = image.createGraphics();
        for (RendererI renderer : renderers) {
        	renderer.setRenderDimensions(0, 0, (int)width, (int)height);
        	renderer.renderComponent(g2d);
        }
	    
	    g2d.dispose();
        
        if (imageTransformer != null)
        	imageTransformer.transformImage(this, image);
	}

	private BufferedImage rotateImage() {
		int width = image.getWidth();
		int height = image.getHeight();
		BufferedImage newImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
	    Graphics2D g2d = newImage.createGraphics();
	    
	    AffineTransform oldXForm = null;
	    
    	if (rotateAngle != 0.0) {
    		oldXForm = g2d.getTransform();
    		
		    if ((rotateX < 0) || (rotateY < 0))
		    	g2d.rotate(Math.toRadians(rotateAngle), width / 2, height / 2);
		    else
		    	g2d.rotate(Math.toRadians(rotateAngle), rotateX, rotateY);
    	}
    	
    	g2d.drawImage(image, 0, 0, width, height, 0, 0, width, height, null);	    	
        
     	if (oldXForm != null)
     		g2d.setTransform(oldXForm);

    	g2d.dispose();
        
    	return newImage;
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
		if (! isRenderComponent())
			return;
		
		if ((image == null) || ((int)getBoundaryWidth() != image.getWidth()) || ((int)getBoundaryHeight() != image.getHeight())) {
			resetImage();
		}
		
		if (image == null)
			return;
		
		double boundLeftX = getBoundaryLeft();
		double boundTopY = getBoundaryTop();
		double width = getBoundaryWidth();
		double height = getBoundaryHeight();
		
		int boundaryLeft = (int)(boundLeftX + (boundMinimumXFraction * width));
		int boundaryTop = (int)(boundTopY + (boundMinimumYFraction * height));
		int boundaryRight = (int)(boundLeftX + (boundMaximumXFraction * width));
		int boundaryBottom = (int)(boundTopY + (boundMaximumYFraction * height));
		
	    AffineTransform oldXForm = null;
	    
    	if (rotateAngle != 0.0) {
    		oldXForm = g2d.getTransform();
    		
		    if ((rotateX < 0) || (rotateY < 0)) {
		    	int middleX = boundaryLeft + ((boundaryRight - boundaryLeft) / 2);
		    	int middleY = boundaryTop + ((boundaryBottom - boundaryTop) / 2);
		    	g2d.rotate(Math.toRadians(rotateAngle), middleX, middleY);
		    }
		    else {
		    	g2d.rotate(Math.toRadians(rotateAngle), rotateX, rotateY);
		    }
    	}
    	
    	g2d.drawImage(image, boundaryLeft, boundaryTop, boundaryRight, boundaryBottom, 0, 0, image.getWidth(), image.getHeight(), null);

    	if (oldXForm != null)
    		g2d.setTransform(oldXForm);
	}
	
	public void invalidate() {
		image = null;
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
	
	public List<RendererI> getRenderers() {
		return renderers;
	}

	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
		image = null;
	}
}
