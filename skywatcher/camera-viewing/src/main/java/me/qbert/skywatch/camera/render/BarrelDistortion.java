package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

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

// Ported close to verbatim from org.bluerock.ui.components.BarrelDistortion - see CLAUDE.md's
// porting-notes table: fully self-contained (AWT-only, no coupling to the god classes), confirmed
// to have no equivalent in sw-base/ga-base. The lens-distortion model is a quartic radial mapping
// with coefficients a/b/c/d (d defaults to 1-a-b-c so a=b=c=0 is the identity mapping). Renamed
// the original's confusingly-named "dRadius" local (it holds the DESTINATION radius, not a
// diminutive/derivative) to destRadius, and dropped a commented-out unused bilinear-interpolation
// TODO block - no behavior changes otherwise.
public class BarrelDistortion {
	private double a = 0.0;
	private double b = 0.0;
	private double c = 0.0;
	private double d = 1.0 - a - b - c;

	private boolean needRecompute = true;
	private int defaultPixel = 0x00000000;

	public void setCoefficients(double a, double b, double c, double d) {
		if ((this.a != a) || (this.b != b) || (this.c != c) || (this.d != d))
			needRecompute = true;

		this.a = a;
		this.b = b;
		this.c = c;
		this.d = d;
	}

	public boolean isNeedRecompute() {
		return needRecompute;
	}

	public void setDefaultTransparent() {
		defaultPixel = 0x00000000;
	}

	public void setDefaultBlack() {
		defaultPixel = 0xFF000000;
	}

	public void setDefaultWhite() {
		defaultPixel = 0xFFFFFFFF;
	}

	public Point2D.Double convert(double destXFraction, double destYFraction) {
		if ((a == 0) && (b == 0) && (c == 0))
			return new Point2D.Double(destXFraction, destYFraction);

		double destRadius = Math.sqrt(destXFraction * destXFraction + destYFraction * destYFraction);
		double radians = Math.atan2(destYFraction, destXFraction);
		double sourceRadius = (a * destRadius * destRadius * destRadius * destRadius)
				+ (b * destRadius * destRadius * destRadius)
				+ (c * destRadius * destRadius)
				+ (d * destRadius);

		double newX = sourceRadius * Math.cos(radians);
		double newY = sourceRadius * Math.sin(radians);

		return new Point2D.Double(newX, newY);
	}

	private int getBestPixel(int[] srcPixels, int w, int h, int x, int y) {
		int bestPixel = defaultPixel;

		double halfWidth = (double) w / 2.0;
		double halfHeight = (double) h / 2.0;
		double halfImage = Math.max(halfWidth, halfHeight);

		Point2D.Double p = convert(((double) x - halfWidth) / halfImage, (halfHeight - (double) y) / halfImage);
		int xInt = (int) ((p.x * halfImage) + halfWidth);
		int yInt = (int) (halfHeight - (p.y * halfImage));

		if ((xInt >= 0) && (xInt < w) && (yInt >= 0) && (yInt < h))
			bestPixel = srcPixels[yInt * w + xInt];

		return bestPixel;
	}

	public boolean isCorrectDistortion() {
		return (a != 0) || (b != 0) || (c != 0) || (d != 1.0);
	}

	public BufferedImage convert(BufferedImage image) {
		if (image == null)
			return null;

		int w = image.getWidth();
		int h = image.getHeight();

		if ((w < 1) || (h < 1))
			return null;

		if (!isCorrectDistortion())
			return image;

		int[] srcPixels = new int[w * h];
		int[] dstPixels = new int[w * h];
		image.getRGB(0, 0, w, h, srcPixels, 0, w);

		BufferedImage clone = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				dstPixels[y * w + x] = getBestPixel(srcPixels, w, h, x, y);
			}
		}

		clone.setRGB(0, 0, w, h, dstPixels, 0, w);

		needRecompute = false;
		return clone;
	}

	public BufferedImage makeReferenceFrame(int width, int height) {
		BufferedImage clone = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

		Graphics2D g2d = clone.createGraphics();
		g2d.setColor(Color.BLACK);
		g2d.setBackground(Color.BLACK);
		g2d.fillRect(0, 0, width, height);

		g2d.setColor(Color.WHITE);
		g2d.setBackground(Color.WHITE);

		int squareSteps = width / 20;
		if ((height / 20) < squareSteps)
			squareSteps = height / 20;

		int x;
		int y;
		x = width / 2;
		while ((x - squareSteps) > 0)
			x -= squareSteps;
		for (; x < width; x += squareSteps) {
			g2d.drawLine(x, 0, x, height);
		}

		y = height / 2;
		while ((y - squareSteps) > 0)
			y -= squareSteps;
		for (; y < height; y += squareSteps) {
			g2d.drawLine(0, y, width, y);
		}

		int max = width / 2;
		if ((height / 2) > max)
			max = height / 2;
		for (int r = squareSteps; r < max; r += squareSteps) {
			g2d.drawArc((width / 2) - r, (height / 2) - r, r * 2, r * 2, 0, 360);
		}

		g2d.drawRect(0, 0, width, height);

		g2d.dispose();

		return clone;
	}
}
