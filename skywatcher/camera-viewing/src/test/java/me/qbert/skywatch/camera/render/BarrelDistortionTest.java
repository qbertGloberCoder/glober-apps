package me.qbert.skywatch.camera.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class BarrelDistortionTest {

	@Test
	void defaultCoefficientsAreTheIdentityMapping() {
		BarrelDistortion distortion = new BarrelDistortion();

		assertFalse(distortion.isCorrectDistortion());

		Point2D.Double p = distortion.convert(0.4, -0.3);
		assertEquals(0.4, p.x, 0.0001);
		assertEquals(-0.3, p.y, 0.0001);
	}

	@Test
	void nonZeroCoefficientsAreDetectedAsCorrectingDistortion() {
		BarrelDistortion distortion = new BarrelDistortion();
		distortion.setCoefficients(0.1, 0.0, 0.0, 0.9);

		assertTrue(distortion.isCorrectDistortion());
	}

	@Test
	void settingCoefficientsFlagsRecomputeOnlyWhenTheyActuallyChange() {
		BarrelDistortion distortion = new BarrelDistortion();
		distortion.setCoefficients(0.05, 0.0, 0.0, 0.95);
		// convert() only clears needRecompute along the actual-distortion path (isCorrectDistortion()
		// true) - matching the original's behavior exactly, not a bug to paper over.
		distortion.convert(new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB));
		assertFalse(distortion.isNeedRecompute());

		distortion.setCoefficients(0.05, 0.0, 0.0, 0.95); // identical to current values
		assertFalse(distortion.isNeedRecompute(), "setting the same coefficients should not flag a recompute");

		distortion.setCoefficients(0.1, 0.0, 0.0, 0.9);
		assertTrue(distortion.isNeedRecompute(), "a genuinely different coefficient must flag a recompute");
	}

	@Test
	void convertReturnsTheSameImageInstanceWhenUncorrected() {
		BarrelDistortion distortion = new BarrelDistortion();
		BufferedImage source = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);

		assertSame(source, distortion.convert(source), "no distortion configured - should pass the source through untouched");
	}

	@Test
	void convertProducesANewImageOfTheSameSizeWhenDistorting() {
		BarrelDistortion distortion = new BarrelDistortion();
		distortion.setCoefficients(0.05, 0.0, 0.0, 0.95);

		BufferedImage source = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
		BufferedImage result = distortion.convert(source);

		assertNotNull(result);
		assertEquals(20, result.getWidth());
		assertEquals(20, result.getHeight());
		assertFalse(distortion.isNeedRecompute(), "convert() should clear the recompute flag");
	}

	@Test
	void convertRejectsNullOrEmptyImages() {
		BarrelDistortion distortion = new BarrelDistortion();
		assertNull(distortion.convert((BufferedImage) null));
	}

	@Test
	void makeReferenceFrameProducesTheRequestedSizeWithAWhiteBorder() {
		BarrelDistortion distortion = new BarrelDistortion();
		BufferedImage frame = distortion.makeReferenceFrame(100, 80);

		assertEquals(100, frame.getWidth());
		assertEquals(80, frame.getHeight());

		int borderPixel = frame.getRGB(0, 40) & 0x00FFFFFF;
		assertEquals(Color.WHITE.getRGB() & 0x00FFFFFF, borderPixel, "the drawn border rectangle should be white");
	}
}
