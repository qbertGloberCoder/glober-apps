package me.qbert.ui.renderers;

import java.awt.Graphics2D;

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

public class PolyRenderer implements RendererI {
	
	private int [] x;
	private int [] y;
	
	private boolean fill;

	@Override
	public void renderComponent(Graphics2D g2d) {
		if ((x == null) || (y == null) || (x.length != y.length))
			return;
		
		if (fill)
			g2d.fillPolygon(x, y, x.length);
		else
			g2d.drawPolygon(x, y, x.length);
	}

	public int[] getX() {
		return x;
	}

	public void setX(int[] x) {
		this.x = x;
	}

	public int[] getY() {
		return y;
	}

	public void setY(int[] y) {
		this.y = y;
	}

	public boolean isFill() {
		return fill;
	}

	public void setFill(boolean fill) {
		this.fill = fill;
	}
}
