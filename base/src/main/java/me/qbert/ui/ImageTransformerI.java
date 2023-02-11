package me.qbert.ui;

import java.awt.image.BufferedImage;

public interface ImageTransformerI {
	public void transformImage(RendererI renderer, BufferedImage image);
}
