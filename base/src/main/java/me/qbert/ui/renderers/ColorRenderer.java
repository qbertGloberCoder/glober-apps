package me.qbert.ui.renderers;

import java.awt.Color;
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

public class ColorRenderer implements RendererI {
	
	private	Color backgroundColor = null;
	private	Color foregroundColor = null;

	@Override
	public void renderComponent(Graphics2D g2d) {
		if (backgroundColor != null)
			g2d.setBackground(backgroundColor);
		if (foregroundColor != null)
			g2d.setColor(foregroundColor);
	}

	public Color getBackgroundColor() {
		return backgroundColor;
	}

	public void setBackgroundColor(Color backgroundColor) {
		this.backgroundColor = backgroundColor;
	}

	public Color getForegroundColor() {
		return foregroundColor;
	}

	public void setForegroundColor(Color foregroundColor) {
		this.foregroundColor = foregroundColor;
	}
}
