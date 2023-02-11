package me.qbert.ui.renderers;

import java.awt.image.BufferedImage;
import java.io.File;

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

public class ImageRenderer extends AbstractImageRenderer {
	public ImageRenderer(File imageFile) {
		this(imageFile, null);
	}
	
	public ImageRenderer(File imageFile, File overlayFile) {
		super();

		reinitImage(imageFile);
		reinitOverlay(overlayFile);
	}
	
	public void reinitImage(File imageFile) {
		BufferedImage image;
		
		if (imageFile != null) {
			try {
				image = loadImageFromFile(imageFile);
			} catch (Exception e) {
				e.printStackTrace();
				image = null;
			}
		} else {
			image = null;
		}
		
		setOriginalImage(image);
	}
	
	public void reinitOverlay(File overlayFile) {
		BufferedImage image;
		
		if ((overlayFile != null) && (overlayFile.exists())) {
			try {
				image = loadImageFromFile(overlayFile);
			} catch (Exception e) {
				e.printStackTrace();
				image = null;
			}
		} else {
			image = null;
		}
		
		setOriginalOverlay(image);
	}
}
