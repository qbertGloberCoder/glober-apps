package me.qbert.ui.util;

import java.awt.Color;

import javax.swing.JPanel;

import me.qbert.ui.renderers.ArcRenderer;
import me.qbert.ui.renderers.ColorRenderer;
import me.qbert.ui.renderers.PointRenderer;
import me.qbert.ui.renderers.PolyRenderer;

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

public class RenderComponentUtil {
	private JPanel targetFrame;
	
	public RenderComponentUtil(JPanel targetFrame) {
		this.targetFrame = targetFrame;
	}
	
	public ColorRenderer setColors(Color foregroundColor, Color backgroundColor) {
		ColorRenderer r = new ColorRenderer();
		r.setForegroundColor(foregroundColor);
		r.setBackgroundColor(backgroundColor);
		return r;
	}
	
	public PolyRenderer drawBox(double leftPercent, double topPercent, double rightPercent, double bottomPercent, boolean isFilled) {
		PolyRenderer r = new PolyRenderer();
		
		int w = targetFrame.getWidth();
		int h = targetFrame.getWidth();
		
		int lx = (int)(w * leftPercent);
		int ty = (int)(h * topPercent);
		int rx = (int)(w * rightPercent);
		int by = (int)(h * bottomPercent);
		
		int [] x = new int[4];
		int [] y = new int[4];
		
		x[0] = lx;
		x[1] = rx;
		x[2] = rx;
		x[3] = lx;
		
		y[0] = ty;
		y[1] = ty;
		y[2] = by;
		y[3] = by;
		
		r.setX(x);
		r.setY(y);
		r.setFill(isFilled);
		
		return r;
	}
	
	public PointRenderer drawPoint(double xPercent, double yPercent) {
		PointRenderer r = new PointRenderer();
		
		int w = targetFrame.getWidth();
		int h = targetFrame.getWidth();
		
		int x = (int)(w * xPercent);
		int y = (int)(h * yPercent);

		r.setX(x);
		r.setY(y);
		
		return r;
	}
	
	public ArcRenderer drawCircle(double xPercent, double yPercent, int radius) {
		ArcRenderer r = new ArcRenderer();
		
		int w = targetFrame.getWidth();
		int h = targetFrame.getWidth();
		
		int x = (int)(w * xPercent);
		int y = (int)(h * yPercent);

		r.setX(x);
		r.setY(y);
		r.setWidth(radius);
		r.setHeight(radius);
		r.setArcAngle(360);
		
		return r;
	}
	
	public ArcRenderer fillCircle(double xPercent, double yPercent, int radius) {
		ArcRenderer r = drawCircle(xPercent, yPercent, radius);

		r.setFill(true);
		
		return r;
	}
}
