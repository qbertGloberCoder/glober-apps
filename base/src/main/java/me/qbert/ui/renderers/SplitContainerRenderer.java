package me.qbert.ui.renderers;

import java.awt.Graphics2D;
import java.awt.Point;
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

public class SplitContainerRenderer implements RendererI {
	public static final int A_LEFT_OF_B = 1;
	public static final int A_ABOVE_B = 2;
	
	private List<RendererI> rendererA;
	private List<RendererI> rendererB;
	
	private int splitOrientation;
	private double aFraction;
	
	private boolean renderA;
	private boolean renderB;
	
	private int left;
	private int top;
	private int width;
	private int height;
	
	public SplitContainerRenderer(int splitOrientation, double aFraction) {
		rendererA = rendererB = null;
		this.splitOrientation = splitOrientation;
		this.aFraction = aFraction;
		
		renderA = renderB = true;

		// if orientation is invalid, default to left/right;
		if ((splitOrientation != A_LEFT_OF_B) && (splitOrientation != A_ABOVE_B))
			this.splitOrientation = A_LEFT_OF_B;
	}
	
	public void setRendererA(List<RendererI> renderer) {
		this.rendererA = renderer;
	}
	
	public void setRendererB(List<RendererI> renderer) {
		this.rendererB = renderer;
	}
	
	@Override
	public double getAspectRatio() {
		double aspect = 1.0;
		
		if ((rendererA != null) && (renderA)) {
			aspect = rendererA.get(0).getAspectRatio();
		}
		
		if ((rendererB != null) && (renderB)) {
			aspect += rendererB.get(0).getAspectRatio();
		}
			
		return aspect;
	}

	@Override
	public void renderComponent(Graphics2D g2d) {
		if ((rendererA != null) && (renderA)) {
			for (RendererI renderer: rendererA)
				renderer.renderComponent(g2d);
		}
		if ((rendererB != null) && (renderB)) {
			for (RendererI renderer: rendererB)
				renderer.renderComponent(g2d);
		}
	}

	public boolean isRenderA() {
		return renderA;
	}

	public void setRenderA(boolean renderA) {
		this.renderA = renderA;
		setRenderDimensions(left, top, width, height);
	}

	public boolean isRenderB() {
		return renderB;
	}

	public void setRenderB(boolean renderB) {
		this.renderB = renderB;
		setRenderDimensions(left, top, width, height);
	}

	@Override
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight) {
		this.left = dimensionLeftX;
		this.top = dimensionTopY;
		this.width = dimensionWidth;
		this.height = dimensionHeight;
		
		boolean aValid = false;
		boolean bValid = false;
		
		if ((rendererA != null) && (renderA))
			aValid = true;
		if ((rendererB != null) && (renderB))
			bValid = true;
		
		if ((aValid) && (bValid)) {
			if (splitOrientation == A_LEFT_OF_B) {
				double leftWidth = (aFraction*(double)dimensionWidth);
				int splitX = (int)(dimensionLeftX+leftWidth);
				for (RendererI renderer: rendererA)
					renderer.setRenderDimensions(dimensionLeftX, dimensionTopY, (int)leftWidth, dimensionHeight);
				for (RendererI renderer: rendererB)
					renderer.setRenderDimensions(splitX, dimensionTopY, dimensionWidth - (int)leftWidth, dimensionHeight);
			} else {
				double splitHeight = (aFraction*(double)dimensionHeight);
				int splitY = (int)(dimensionTopY+splitHeight);
				for (RendererI renderer: rendererA)
					renderer.setRenderDimensions(dimensionLeftX, dimensionTopY, dimensionWidth, splitY);
				for (RendererI renderer: rendererB)
					renderer.setRenderDimensions(dimensionLeftX, splitY, dimensionWidth, dimensionHeight - (int)splitHeight);
			}
		} else if (aValid) {
			for (RendererI renderer: rendererA)
				renderer.setRenderDimensions(dimensionLeftX, dimensionTopY, dimensionWidth, dimensionHeight);
		} else if (bValid) {
			for (RendererI renderer: rendererB)
				renderer.setRenderDimensions(dimensionLeftX, dimensionTopY, dimensionWidth, dimensionHeight);
		}
	}
	
}
