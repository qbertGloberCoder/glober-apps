package me.qbert.skywatch.camera.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

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

// Task 8.1's image-display canvas. A much smaller, single-purpose relative of earthclock's own
// ui.component.Canvas: this module always has a fully pre-rendered FrameCompositor.compose(...)
// output ready to show (never a live ga-base RendererI list this component would need to drive
// itself, the way earthclock's Canvas alternates between an image-replay mode and a live-renderer
// mode), so this only needs the "blit a BufferedImage, scaled to fit, aspect-preserving,
// letterboxed" half of that class - ported/simplified rather than reused as-is.
//
// Unlike JFrame/Window subclasses, a bare JPanel can be constructed and painted against in a
// headless environment (confirmed while building this round) as long as nothing calls setVisible(...)
// on a real top-level window - PreviewPanelTest exploits exactly this to paint directly against a
// BufferedImage's own Graphics2D and inspect the result, real behavioral coverage rather than a
// display-dependent smoke test.
public final class PreviewPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	// Plate-solve technique 2's click-and-mark redesign (a direct user request, moving click-to-mark
	// out of the small dedicated ui.PlateSolveMarkingPanel - now retired - into this larger,
	// user-resizable window for better click precision). The interface, the aiming-circle overlay,
	// and the image-pixel coordinate conversion below are a direct port of that class's own design -
	// see its retired class comment for the original reasoning, unchanged here.
	public interface MarkClickListener {
		void onMarkClicked(double imagePixelX, double imagePixelY);
	}

	private static final int DEFAULT_AIMING_CIRCLE_RADIUS_PIXELS = 12;
	private static final int MIN_AIMING_CIRCLE_RADIUS_PIXELS = 2;
	private static final int MAX_AIMING_CIRCLE_RADIUS_PIXELS = 80;

	private BufferedImage image;
	// Off by default - ordinary preview use (not marking) must be completely unaffected: no aiming
	// circle painted, no click ever consumed as a mark. ui.PreviewWindow.setMarkingModeActive(...) is
	// the only caller that flips this.
	private boolean markingModeActive;
	private int aimingCircleRadiusPixels = DEFAULT_AIMING_CIRCLE_RADIUS_PIXELS;
	private int lastMouseX = -1;
	private int lastMouseY = -1;
	private MarkClickListener markClickListener;

	// Black, not the look-and-feel default gray - matches ordinary media-viewer letterboxing
	// convention for the bars around a non-matching-aspect image (and for the "no image yet"
	// state before the first frame renders).
	public PreviewPanel() {
		setBackground(Color.BLACK);

		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseMoved(MouseEvent e) {
				if (!markingModeActive)
					return;
				lastMouseX = e.getX();
				lastMouseY = e.getY();
				repaint();
			}
		});
		addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				handleClick(e.getX(), e.getY());
			}
		});
	}

	public void setImage(BufferedImage image) {
		this.image = image;
		repaint();
	}

	public BufferedImage getImage() {
		return image;
	}

	public void setMarkingModeActive(boolean markingModeActive) {
		this.markingModeActive = markingModeActive;
		repaint();
	}

	public boolean isMarkingModeActive() {
		return markingModeActive;
	}

	public void setAimingCircleRadiusPixels(int radiusPixels) {
		if (radiusPixels < MIN_AIMING_CIRCLE_RADIUS_PIXELS || radiusPixels > MAX_AIMING_CIRCLE_RADIUS_PIXELS)
			throw new IllegalArgumentException("radiusPixels must be in [" + MIN_AIMING_CIRCLE_RADIUS_PIXELS + ", "
					+ MAX_AIMING_CIRCLE_RADIUS_PIXELS + "]");
		this.aimingCircleRadiusPixels = radiusPixels;
		repaint();
	}

	public int getAimingCircleRadiusPixels() {
		return aimingCircleRadiusPixels;
	}

	public void onMarkClicked(MarkClickListener listener) {
		this.markClickListener = listener;
	}

	private void handleClick(int mouseX, int mouseY) {
		if (!markingModeActive || image == null || markClickListener == null)
			return;
		Rectangle destination = fitWithinPreservingAspect(image.getWidth(), image.getHeight(), getWidth(), getHeight());
		Point2D.Double imagePixel = toImagePixel(mouseX, mouseY, destination, image.getWidth(), image.getHeight());
		if (imagePixel != null)
			markClickListener.onMarkClicked(imagePixel.x, imagePixel.y);
	}

	// Converts a click at (mouseX, mouseY) in this panel's own component-space coordinates into
	// image-pixel coordinates, given the letterboxed destination rectangle the image is actually
	// drawn into - null if the click landed in a letterbox bar (outside the image entirely), which is
	// not a click ON anything and must not register a mark. Package-visible, directly testable
	// without needing a real displayed component.
	static Point2D.Double toImagePixel(int mouseX, int mouseY, Rectangle destination, int imageWidth, int imageHeight) {
		if (destination.width <= 0 || destination.height <= 0)
			return null;
		if (!destination.contains(mouseX, mouseY))
			return null;

		double fractionX = (mouseX - destination.x) / (double) destination.width;
		double fractionY = (mouseY - destination.y) / (double) destination.height;
		return new Point2D.Double(fractionX * imageWidth, fractionY * imageHeight);
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		if (image == null)
			return;

		Rectangle destination = fitWithinPreservingAspect(image.getWidth(), image.getHeight(), getWidth(), getHeight());
		if (destination.width <= 0 || destination.height <= 0)
			return;

		Graphics2D g2d = (Graphics2D) g.create();
		try {
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g2d.drawImage(image, destination.x, destination.y, destination.x + destination.width,
					destination.y + destination.height, 0, 0, image.getWidth(), image.getHeight(), null);

			if (markingModeActive && lastMouseX >= 0 && lastMouseY >= 0) {
				g2d.setColor(Color.YELLOW);
				g2d.draw(new Ellipse2D.Double(lastMouseX - aimingCircleRadiusPixels, lastMouseY - aimingCircleRadiusPixels,
						aimingCircleRadiusPixels * 2.0, aimingCircleRadiusPixels * 2.0));
			}
		} finally {
			g2d.dispose();
		}
	}

	// The centered, aspect-preserving rectangle within a panelWidth x panelHeight area that
	// imageWidth x imageHeight should be drawn into - letterboxed (bars on the sides, or top/
	// bottom, whichever the aspect mismatch calls for). Matches earthclock's own Canvas.
	// paintComponent(...) letterboxing math, extracted here as a standalone, directly-testable
	// method rather than left inline (earthclock's own version has no equivalent test - see
	// PreviewPanelTest for real coverage of this specific geometry).
	static Rectangle fitWithinPreservingAspect(int imageWidth, int imageHeight, int panelWidth, int panelHeight) {
		if (imageWidth <= 0 || imageHeight <= 0 || panelWidth <= 0 || panelHeight <= 0)
			return new Rectangle(0, 0, 0, 0);

		double imageAspect = (double) imageWidth / imageHeight;
		double panelAspect = (double) panelWidth / panelHeight;

		int width = panelWidth;
		int height = panelHeight;

		if (imageAspect > panelAspect) {
			// Image is relatively wider than the panel - use the full width, letterboxed top/bottom.
			height = (int) Math.round(panelWidth / imageAspect);
		} else if (imageAspect < panelAspect) {
			// Image is relatively taller than the panel - use the full height, letterboxed left/right.
			width = (int) Math.round(panelHeight * imageAspect);
		}

		int x = (panelWidth - width) / 2;
		int y = (panelHeight - height) / 2;
		return new Rectangle(x, y, width, height);
	}
}
