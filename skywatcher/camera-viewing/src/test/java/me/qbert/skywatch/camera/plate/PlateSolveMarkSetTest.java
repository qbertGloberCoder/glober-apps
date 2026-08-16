package me.qbert.skywatch.camera.plate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.watch.WatchedObject;

class PlateSolveMarkSetTest {

	@Test
	void startsEmpty() {
		PlateSolveMarkSet marks = new PlateSolveMarkSet();

		assertTrue(marks.isEmpty());
		assertEquals(0, marks.size());
		assertTrue(marks.getMarks().isEmpty());
	}

	@Test
	void accumulatesMarksInAdditionOrder() {
		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		PlateSolveMark first = new PlateSolveMark(1_000L, WatchedObject.sun(), 0.1, 0.1);
		PlateSolveMark second = new PlateSolveMark(2_000L, WatchedObject.moon(), 0.9, 0.9);

		marks.add(first);
		marks.add(second);

		assertEquals(2, marks.size());
		assertEquals(first, marks.getMarks().get(0));
		assertEquals(second, marks.getMarks().get(1));
	}

	@Test
	void rejectsANullMark() {
		PlateSolveMarkSet marks = new PlateSolveMarkSet();

		assertThrows(IllegalArgumentException.class, () -> marks.add(null));
	}

	@Test
	void getMarksIsUnmodifiable() {
		PlateSolveMarkSet marks = new PlateSolveMarkSet();
		marks.add(new PlateSolveMark(0L, WatchedObject.sun(), 0.5, 0.5));

		assertThrows(UnsupportedOperationException.class,
				() -> marks.getMarks().add(new PlateSolveMark(1L, WatchedObject.moon(), 0.5, 0.5)));
	}
}
