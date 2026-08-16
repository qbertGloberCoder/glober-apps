package me.qbert.skywatch.camera.plate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.watch.WatchedObject;

class PlateSolveMarkTest {

	@Test
	void storesTheBasicTuple() {
		PlateSolveMark mark = new PlateSolveMark(1_000L, WatchedObject.sun(), 0.25, 0.75);

		assertEquals(1_000L, mark.getEpochMillis());
		assertEquals(WatchedObject.Kind.SUN, mark.getObject().getKind());
		assertEquals(0.25, mark.getNormalizedX(), 0.0001);
		assertEquals(0.75, mark.getNormalizedY(), 0.0001);
	}

	@Test
	void rejectsANullObject() {
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMark(0L, null, 0.5, 0.5));
	}

	@Test
	void rejectsNormalizedCoordinatesOutsideTheUnitRange() {
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMark(0L, WatchedObject.moon(), -0.01, 0.5));
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMark(0L, WatchedObject.moon(), 1.01, 0.5));
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMark(0L, WatchedObject.moon(), 0.5, -0.01));
		assertThrows(IllegalArgumentException.class, () -> new PlateSolveMark(0L, WatchedObject.moon(), 0.5, 1.01));
	}

	@Test
	void acceptsTheUnitRangeBoundariesThemselves() {
		new PlateSolveMark(0L, WatchedObject.moon(), 0.0, 0.0);
		new PlateSolveMark(0L, WatchedObject.moon(), 1.0, 1.0);
	}

	@Test
	void fromPixelClickNormalizesAgainstTheCanvasSize() {
		PlateSolveMark mark = PlateSolveMark.fromPixelClick(500L, WatchedObject.moon(), 480.0, 270.0, 1920, 1080);

		assertEquals(0.25, mark.getNormalizedX(), 0.0001);
		assertEquals(0.25, mark.getNormalizedY(), 0.0001);
	}

	@Test
	void fromPixelClickRejectsNonPositiveCanvasDimensions() {
		assertThrows(IllegalArgumentException.class,
				() -> PlateSolveMark.fromPixelClick(0L, WatchedObject.sun(), 10.0, 10.0, 0, 100));
		assertThrows(IllegalArgumentException.class,
				() -> PlateSolveMark.fromPixelClick(0L, WatchedObject.sun(), 10.0, 10.0, 100, 0));
	}

	@Test
	void pixelXAndPixelYAreTheInverseOfFromPixelClick() {
		PlateSolveMark mark = PlateSolveMark.fromPixelClick(0L, WatchedObject.sun(), 480.0, 270.0, 1920, 1080);

		assertEquals(480.0, mark.pixelX(1920), 0.0001);
		assertEquals(270.0, mark.pixelY(1080), 0.0001);
	}
}
