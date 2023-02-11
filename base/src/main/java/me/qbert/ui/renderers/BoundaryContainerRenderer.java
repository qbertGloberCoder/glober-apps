package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

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

public class BoundaryContainerRenderer extends AbstractFractionRenderer {
	List<RendererI> renderers = new ArrayList<RendererI>();
	
	private double boundMinimumXFraction = 0.0;
	private double boundMinimumYFraction = 0.0;
	private double boundMaximumXFraction = 1.0;
	private double boundMaximumYFraction = 1.0;

<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
	private double largestAspect = 1.0;
	
	private BoundaryContainerRenderer followContainer = null;

	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		updateAspectRatio();
		
<<<<<<< HEAD
<<<<<<< HEAD
=======
	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		super.setRenderDimensions(dimensionLeftX, dimensionTopY, dimensionWidth, dimensionHeight);
		
		double boundLeftX = getBoundaryLeft();
		double boundTopY = getBoundaryTop();
		double width = getBoundaryWidth();
		double height = getBoundaryHeight();
		
		int boundaryLeft = (int)(boundLeftX) + (int)(boundMinimumXFraction * width);
		int boundaryRight = (int)(boundLeftX) + (int)(boundMaximumXFraction * width);
		int boundaryTop = (int)(boundTopY) + (int)(boundMinimumYFraction * height);
		int boundaryBottom = (int)(boundTopY) + (int)(boundMaximumYFraction * height);
		
		for (RendererI renderer : renderers) {
			renderer.setRenderDimensions(boundaryLeft, boundaryTop, (boundaryRight - boundaryLeft), (boundaryBottom - boundaryTop));
		}
	}
	
	@Override
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
=======
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
	public double getAspectRatio() {
		if (followContainer != null)
			return followContainer.getAspectRatio();
		
		return largestAspect;
	}
	
	public BoundaryContainerRenderer getFollowContainer() {
		return followContainer;
	}

	public void setFollowContainer(BoundaryContainerRenderer followContainer) {
		this.followContainer = followContainer;
	}

	private void updateAspectRatio() {
		if (followContainer != null)
			return;
		
		largestAspect = -1.0;
		
		for (RendererI renderer : renderers) {
			if (renderer.getAspectRatio() > largestAspect) {
				largestAspect = renderer.getAspectRatio();
			}
//			if (isDebug())
//				System.out.println("DEBUG: " + renderer.getClass().getCanonicalName() + " : largest aspect ratio: " + renderer.getAspectRatio());
		}
		
//		if (isDebug())
//			System.out.println("DEBUG: largest aspect ratio: " + largestAspect);
	}
	
	@Override
	public void renderComponent(Graphics2D g2d) {
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
		if ((! isRenderComponent()) || (renderers == null))
			return;
		
=======
	public void renderComponent(Graphics2D g2d) {
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
		if (! isRenderComponent())
=======
		if ((! isRenderComponent()) || (renderers == null))
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
			return;
		
>>>>>>> d611045 (many changes in the base UI to support the earth clock app)
=======
	public void renderComponent(Graphics2D g2d) {
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
		if (! isRenderComponent())
=======
		if ((! isRenderComponent()) || (renderers == null))
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
			return;
		
>>>>>>> 34bfd38 (many changes in the base UI to support the earth clock app)
=======
	public void renderComponent(Graphics2D g2d) {
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
		for (RendererI renderer : renderers) {
			renderer.renderComponent(g2d);
		}
	}

	public List<RendererI> getRenderers() {
		return renderers;
	}

	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
		updateAspectRatio();
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
		updateAspectRatio();
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
		updateAspectRatio();
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
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
