package me.qbert.skywatch.ui.component;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
	private List<RendererI> renderers = new ArrayList<RendererI>();

    private void doDrawing(Graphics g) {
        Graphics2D g2d = (Graphics2D) g.create();

        RenderingHints rh = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

        rh.put(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        g2d.setRenderingHints(rh);
        
        int width = getWidth();
        int height = getHeight();

        for (RendererI renderer : renderers) {
        	renderer.setRenderDimensions(0, 0, width, height);
        	renderer.renderComponent(g2d);
        }
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        doDrawing(g);
    }

	public List<RendererI> getRenderers() {
		return renderers;
	}

	public void setRenderers(List<RendererI> renderers) {
		this.renderers = renderers;
	}
}
