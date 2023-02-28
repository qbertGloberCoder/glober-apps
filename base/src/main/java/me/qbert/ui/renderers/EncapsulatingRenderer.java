package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

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

public class EncapsulatingRenderer extends AbstractFractionRenderer {
	private List<RendererI> renderers;
	private Double lockApsectRatio;
	
	public EncapsulatingRenderer() throws Exception {
		renderers = null;
	}
	
	public void setRenderer(RendererI renderer) {
		this.renderers = new ArrayList<RendererI>();
		this.renderers.add(renderer);
	}
	
	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
	}
	
	public Double getLockApsectRatio() {
		return lockApsectRatio;
	}

	public void setLockApsectRatio(Double lockApsectRatio) {
		this.lockApsectRatio = lockApsectRatio;
	}

	@Override
	public double getAspectRatio() {
		if (lockApsectRatio != null)
			return lockApsectRatio.doubleValue();
		
		if ((renderers != null) && (renderers.size() > 0) && (renderers.get(0) != null))
			return renderers.get(0).getAspectRatio();
		
		return 1.0;
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
		if (renderers != null) {
			for (RendererI renderer : renderers)
				if (renderer != null)
					renderer.renderComponent(g2d);
		}
	}

	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		super.setRenderDimensions(dimensionLeftX, dimensionTopY, dimensionWidth, dimensionHeight);
		
		double left = getBoundaryLeft();
		double top = getBoundaryTop();
		double width = getBoundaryWidth();
		double height = getBoundaryHeight();
		
		if (renderers != null) {
			for (RendererI renderer : renderers) {
				if (renderer != null)
					renderer.setRenderDimensions((int)left, (int)top, (int)width, (int)height);
			}
		}
	}
	
}
