package me.qbert.skywatch.ui.renderers;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.List;

import me.qbert.skywatch.service.ArcRendererLocationSetterI;
import me.qbert.ui.RendererI;
import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.LineRenderer;

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

public class PinnableCelestialObject {
	private ArcRenderer outerHighlightedLocation;
	private ArcRenderer innerLocation;
	private ArcRenderer groundPositionLocation;
	private LineRenderer pinRendererLine;
	
	private ArcRendererLocationSetterI locationSetter;
	
	private boolean configured;
	
	public PinnableCelestialObject(ArcRendererLocationSetterI locationSetter) throws Exception {
		configured = false;
		
		this.locationSetter = locationSetter;

		outerHighlightedLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		innerLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		groundPositionLocation = new ArcRenderer(ArcRenderer.FRACTIONAL_COORDINATES, ArcRenderer.ABSOLUTE_COORDINATES);
		pinRendererLine = new LineRenderer(LineRenderer.FRACTIONAL_COORDINATES);
	}

	public void configure(Color pinColor, Color pinStemColor, List<RendererI> renderer) throws Exception {
		if (configured)
			throw new Exception("Object already initialized");
		
		configured = true;
		
		ColorRenderer colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(pinColor);
        colorRenderer.setForegroundColor(pinColor);
        renderer.add(colorRenderer);
        renderer.add(groundPositionLocation);
        
		colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(pinStemColor);
        colorRenderer.setForegroundColor(pinStemColor);
        renderer.add(colorRenderer);
        renderer.add(pinRendererLine);
        
		colorRenderer = new ColorRenderer();
        colorRenderer.setBackgroundColor(pinColor);
        colorRenderer.setForegroundColor(pinColor);
        renderer.add(colorRenderer);
        renderer.add(outerHighlightedLocation);
        
		colorRenderer = new ColorRenderer();
		Color c = brighten(pinColor, 3.0/2.0);
        colorRenderer.setBackgroundColor(c);
        colorRenderer.setForegroundColor(c);
        renderer.add(colorRenderer);
        renderer.add(innerLocation);
 	}
	
	public void updatePin(double latitude, double longitude, boolean pinMode) {
		updatePin(latitude, longitude, 8, 8, 6, pinMode);
	}
	
	public void updatePin(double latitude, double longitude, int outerSize, int innerSize, int groundSize, boolean pinMode) {
		locationSetter.updateLocation(outerHighlightedLocation, latitude, longitude, outerSize, true);
		locationSetter.updateLocation(innerLocation, latitude, longitude, innerSize, true);
		
		if (pinMode) {
			Point2D.Double p2d = locationSetter.updateLocation(latitude, longitude, false);
    		if (p2d != null) {
    			pinRendererLine.setRenderComponent(true);
    			pinRendererLine.setX1(p2d.x);
    			pinRendererLine.setY1(p2d.y);
    			pinRendererLine.setX2(outerHighlightedLocation.getX());
    			pinRendererLine.setY2(outerHighlightedLocation.getY());
    			locationSetter.updateLocation(groundPositionLocation, latitude, longitude, groundSize, false);
    			
    		} else {
    			pinRendererLine.setRenderComponent(false);
    			groundPositionLocation.setRenderComponent(false);
    		}
			
		} else {
			pinRendererLine.setRenderComponent(pinMode);
			groundPositionLocation.setRenderComponent(pinMode);
		}
	}
	
	public static Color brighten(Color c, double ratio) {
		int r = (int)((double)c.getRed() * ratio);
		int g = (int)((double)c.getGreen() * ratio);
		int b = (int)((double)c.getBlue() * ratio);
		
		// we want a bit of increased intensity pop if some of the channels were black
		if (ratio > 1.0) {
			if (c.getRed() == 0)
				r = (int)((double)(c.getGreen() + c.getBlue()) * ratio / 4.0);
			if (c.getGreen() == 0)
				g = (int)((double)(c.getRed() + c.getBlue()) * ratio / 4.0);
			if (c.getBlue() == 0)
				b = (int)((double)(c.getGreen() + c.getRed()) * ratio / 4.0);
		}
		
		if (r < 0)
			r = 0;
		if (r > 255)
			r = 255;
		if (g < 0)
			g = 0;
		if (g > 255)
			g = 255;
		if (b < 0)
			b = 0;
		if (b > 255)
			b = 255;
		return new Color(r, g, b);
	}
}