package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class LabelsTest {

	private static final int CANVAS_SIZE = 200;

	@Test
	void drawsTextSomewhereNearTheOffsetPosition() throws Exception {
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Labels.draw(g2d, "Vega", new Point2D.Double(100.0, 100.0), Color.WHITE, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		assertTrue(hasNonBackgroundPixelNear(image, 106, 94, 30), "expected label text to appear near the offset position");
	}

	@Test
	void emptyOrNullTextDrawsNothing() throws Exception {
		BufferedImage image = blankImage();
		Graphics2D g2d = image.createGraphics();

		Labels.draw(g2d, "", new Point2D.Double(100.0, 100.0), Color.WHITE, CANVAS_SIZE, CANVAS_SIZE);
		Labels.draw(g2d, null, new Point2D.Double(100.0, 100.0), Color.WHITE, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();

		for (int y = 0; y < CANVAS_SIZE; y++)
			for (int x = 0; x < CANVAS_SIZE; x++)
				assertEquals(Color.BLACK.getRGB(), image.getRGB(x, y));
	}

	private BufferedImage blankImage() {
		BufferedImage image = new BufferedImage(CANVAS_SIZE, CANVAS_SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.fillRect(0, 0, CANVAS_SIZE, CANVAS_SIZE);
		g2d.dispose();
		return image;
	}

	private boolean hasNonBackgroundPixelNear(BufferedImage image, int centerX, int centerY, int radius) {
		for (int y = Math.max(0, centerY - radius); y < Math.min(CANVAS_SIZE, centerY + radius); y++)
			for (int x = Math.max(0, centerX - radius); x < Math.min(CANVAS_SIZE, centerX + radius); x++)
				if ((image.getRGB(x, y) & 0x00FFFFFF) != (Color.BLACK.getRGB() & 0x00FFFFFF))
					return true;
		return false;
	}
}
