package me.qbert.ui;

import java.awt.Graphics2D;

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

public interface RendererI {
	public void renderComponent(Graphics2D g2d);
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
<<<<<<< HEAD
	public double getAspectRatio();
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
	public double getAspectRatio();
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> dbf883f (add the first barely adequate version of the multi-transformation earth clock)
=======
	public double getAspectRatio();
>>>>>>> 21e91f4 (new pom version, expand the UI renderers to support earth clock component rendering)
=======
>>>>>>> 701e448 (add the first barely adequate version of the multi-transformation earth clock)
=======
	public double getAspectRatio();
>>>>>>> 63cfaa2 (new pom version, expand the UI renderers to support earth clock component rendering)
	public void setRenderDimensions(int dimensionLeftX, int dimensionTopY, int dimensionWidth, int dimensionHeight);
}
