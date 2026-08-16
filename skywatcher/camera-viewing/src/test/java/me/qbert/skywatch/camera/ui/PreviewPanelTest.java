package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

// Task 8.1's image-display canvas. A bare JPanel can be constructed in a headless environment
// (confirmed while building this round - unlike JFrame/Window subclasses, which throw
// HeadlessException immediately), so paintComponent(...) is invoked directly here against a
// BufferedImage's own Graphics2D - real behavioral coverage of the letterboxing/blit logic without
// needing an actual display. Same-package test, so the protected paintComponent(...) and
// package-private fitWithinPreservingAspect(...) are both directly callable, no reflection needed.
class PreviewPanelTest {

	@Test
	void getAndSetImageRoundTrip() {
		PreviewPanel panel = new PreviewPanel();
		BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);

		panel.setImage(image);

		assertSame(image, panel.getImage());
	}

	@Test
	void paintingWithNoImageSetOnlyPaintsTheBlackBackground() throws Exception {
		PreviewPanel panel = new PreviewPanel();
		panel.setSize(100, 100);
		BufferedImage canvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		fill(canvas, Color.MAGENTA); // a contrasting starting color the background paint must overwrite

		paintComponent(panel, canvas);

		assertEquals(Color.BLACK.getRGB(), canvas.getRGB(50, 50),
				"with no image set, only the panel's own black background should show");
	}

	@Test
	void paintingBlitsTheImageLetterboxedForATallerPanel() throws Exception {
		// A wide image (200x100, aspect 2.0) into a taller-than-wide panel (100x200) - must be
		// letterboxed top/bottom, full width, matching PreviewPanel.fitWithinPreservingAspect(...).
		PreviewPanel panel = new PreviewPanel();
		panel.setSize(100, 200);
		BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
		fill(image, Color.RED);
		panel.setImage(image);

		BufferedImage canvas = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
		fill(canvas, Color.MAGENTA);

		paintComponent(panel, canvas);

		// Center of the letterboxed image region must be red...
		assertEquals(Color.RED.getRGB(), canvas.getRGB(50, 100));
		// ...but the letterbox bars (top/bottom) must be the panel's own black background, not the
		// pre-existing magenta.
		assertEquals(Color.BLACK.getRGB(), canvas.getRGB(50, 2));
		assertEquals(Color.BLACK.getRGB(), canvas.getRGB(50, 198));
	}

	@Test
	void fitWithinPreservingAspectLetterboxesATallerPanel() throws Exception {
		Rectangle destination = fitWithinPreservingAspect(200, 100, 100, 200);

		assertEquals(100, destination.width, "should use the full panel width");
		assertEquals(50, destination.height, "height follows the image's own 2:1 aspect ratio");
		assertEquals(0, destination.x);
		assertEquals(75, destination.y, "vertically centered within the 200-tall panel");
	}

	@Test
	void fitWithinPreservingAspectLetterboxesAWiderPanel() throws Exception {
		Rectangle destination = fitWithinPreservingAspect(100, 200, 200, 100);

		assertEquals(100, destination.height, "should use the full panel height");
		assertEquals(50, destination.width, "width follows the image's own 1:2 aspect ratio");
		assertEquals(75, destination.x, "horizontally centered within the 200-wide panel");
		assertEquals(0, destination.y);
	}

	@Test
	void fitWithinPreservingAspectFillsExactlyWhenAspectsMatch() throws Exception {
		Rectangle destination = fitWithinPreservingAspect(100, 100, 300, 300);

		assertEquals(new Rectangle(0, 0, 300, 300), destination);
	}

	@Test
	void fitWithinPreservingAspectReturnsEmptyForNonPositiveInputs() throws Exception {
		assertEquals(new Rectangle(0, 0, 0, 0), fitWithinPreservingAspect(0, 100, 100, 100));
		assertEquals(new Rectangle(0, 0, 0, 0), fitWithinPreservingAspect(100, 100, 0, 100));
	}

	// --- Plate-solve marking mode - ported from the now-retired ui.PlateSolveMarkingPanel, whose
	// role moved here directly (see this class's own MarkClickListener/toImagePixel comment) ---

	@Test
	void toImagePixelMapsAClickAtTheCenterOfTheDestinationToTheCenterOfTheImage() {
		Rectangle destination = new Rectangle(10, 20, 100, 200);

		Point2D.Double imagePixel = PreviewPanel.toImagePixel(60, 120, destination, 400, 800);

		assertEquals(200.0, imagePixel.x, 0.0001);
		assertEquals(400.0, imagePixel.y, 0.0001);
	}

	@Test
	void toImagePixelMapsTheDestinationsTopLeftCornerToImageOrigin() {
		Rectangle destination = new Rectangle(10, 20, 100, 200);

		Point2D.Double imagePixel = PreviewPanel.toImagePixel(10, 20, destination, 400, 800);

		assertEquals(0.0, imagePixel.x, 0.0001);
		assertEquals(0.0, imagePixel.y, 0.0001);
	}

	@Test
	void toImagePixelReturnsNullForAClickInTheLetterboxBars() {
		Rectangle destination = new Rectangle(10, 20, 100, 200);

		assertNull(PreviewPanel.toImagePixel(5, 120, destination, 400, 800), "left of the destination rectangle");
		assertNull(PreviewPanel.toImagePixel(60, 5, destination, 400, 800), "above the destination rectangle");
		assertNull(PreviewPanel.toImagePixel(200, 120, destination, 400, 800), "right of the destination rectangle");
	}

	@Test
	void toImagePixelReturnsNullForAnEmptyDestination() {
		assertNull(PreviewPanel.toImagePixel(0, 0, new Rectangle(0, 0, 0, 0), 400, 800));
	}

	@Test
	void aimingCircleRadiusRoundTripsAndRejectsOutOfRangeValues() {
		PreviewPanel panel = new PreviewPanel();

		panel.setAimingCircleRadiusPixels(25);
		assertEquals(25, panel.getAimingCircleRadiusPixels());

		assertThrows(IllegalArgumentException.class, () -> panel.setAimingCircleRadiusPixels(0));
		assertThrows(IllegalArgumentException.class, () -> panel.setAimingCircleRadiusPixels(1000));
	}

	@Test
	void markingModeDefaultsOffAndRoundTrips() {
		PreviewPanel panel = new PreviewPanel();

		assertFalse(panel.isMarkingModeActive());
		panel.setMarkingModeActive(true);
		assertTrue(panel.isMarkingModeActive());
	}

	// A real design requirement (not present in the retired PlateSolveMarkingPanel, which was ALWAYS
	// in marking mode): ordinary preview use of this panel must never accidentally consume a click as
	// a mark, or paint an aiming circle nobody asked for - both must stay off unless marking mode is
	// explicitly active.
	@Test
	void handleClickDoesNothingWhenMarkingModeIsInactive() {
		PreviewPanel panel = new PreviewPanel();
		panel.setSize(100, 100);
		BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
		panel.setImage(image);
		boolean[] clicked = { false };
		panel.onMarkClicked((x, y) -> clicked[0] = true);

		invokeHandleClick(panel, 50, 50);

		assertFalse(clicked[0], "a click must not register as a mark while marking mode is off");
	}

	@Test
	void handleClickReportsImagePixelCoordinatesWhenMarkingModeIsActive() {
		PreviewPanel panel = new PreviewPanel();
		panel.setSize(100, 100);
		BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
		panel.setImage(image);
		panel.setMarkingModeActive(true);
		double[] reported = { -1, -1 };
		panel.onMarkClicked((x, y) -> {
			reported[0] = x;
			reported[1] = y;
		});

		invokeHandleClick(panel, 50, 50);

		assertEquals(25.0, reported[0], 0.0001, "a 100x100 panel showing a 50x50 image fills it exactly - "
				+ "the panel's own center maps to the image's own center");
		assertEquals(25.0, reported[1], 0.0001);
	}

	@Test
	void aimingCircleOnlyPaintsWhenMarkingModeIsActive() throws Exception {
		PreviewPanel panel = new PreviewPanel();
		panel.setSize(100, 100);
		BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
		fill(image, Color.RED);
		panel.setImage(image);
		moveMouse(panel, 50, 50);

		BufferedImage inactiveCanvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		paintComponent(panel, inactiveCanvas);
		assertFalse(containsYellow(inactiveCanvas), "no aiming circle without marking mode active");

		panel.setMarkingModeActive(true);
		moveMouse(panel, 50, 50);
		BufferedImage activeCanvas = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		paintComponent(panel, activeCanvas);
		assertTrue(containsYellow(activeCanvas), "the aiming circle must paint once marking mode is active");
	}

	private boolean containsYellow(BufferedImage image) {
		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if (image.getRGB(x, y) == Color.YELLOW.getRGB())
					return true;
		return false;
	}

	private void moveMouse(PreviewPanel panel, int x, int y) {
		panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_MOVED,
				System.currentTimeMillis(), 0, x, y, 0, false));
	}

	private void invokeHandleClick(PreviewPanel panel, int x, int y) {
		panel.dispatchEvent(new java.awt.event.MouseEvent(panel, java.awt.event.MouseEvent.MOUSE_CLICKED,
				System.currentTimeMillis(), 0, x, y, 1, false));
	}

	private void fill(BufferedImage image, Color color) {
		Graphics2D g2d = image.createGraphics();
		g2d.setColor(color);
		g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
		g2d.dispose();
	}

	private void paintComponent(PreviewPanel panel, BufferedImage canvas) {
		Graphics2D g2d = canvas.createGraphics();
		try {
			panel.paintComponent(g2d);
		} finally {
			g2d.dispose();
		}
	}

	private Rectangle fitWithinPreservingAspect(int imageWidth, int imageHeight, int panelWidth, int panelHeight) {
		return PreviewPanel.fitWithinPreservingAspect(imageWidth, imageHeight, panelWidth, panelHeight);
	}
}
