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

	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
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
	public void renderComponent(Graphics2D g2d) {
		for (RendererI renderer : renderers) {
			renderer.renderComponent(g2d);
		}
	}

	public List<RendererI> getRenderers() {
		return renderers;
	}

	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
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
