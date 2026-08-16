package me.qbert.skywatch.camera.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ZoomRangeTest {

	@Test
	void rejectsAnInvertedRange() {
		assertThrows(IllegalArgumentException.class, () -> new ZoomRange(70.0, 18.0));
	}

	@Test
	void rejectsNonPositiveFocalLengths() {
		assertThrows(IllegalArgumentException.class, () -> new ZoomRange(0.0, 70.0));
	}

	@Test
	void aSingleFocalLengthLensIsMinEqualsMax() {
		ZoomRange fixed = new ZoomRange(50.0, 50.0);

		assertTrue(fixed.contains(50.0));
		assertFalse(fixed.contains(49.9));
	}

	@Test
	void containsAndClampMatchTheDeclaredRange() {
		// The user's own worked example, 18-70mm.
		ZoomRange range = new ZoomRange(18.0, 70.0);

		assertTrue(range.contains(35.0));
		assertFalse(range.contains(17.9));
		assertFalse(range.contains(70.1));

		assertEquals(18.0, range.clamp(10.0));
		assertEquals(70.0, range.clamp(200.0));
		assertEquals(35.0, range.clamp(35.0));
	}
}
