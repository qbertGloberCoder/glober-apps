package me.qbert.skywatch.camera.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class WatchedObjectRegistryTest {

	@Test
	void defaultsToTheSun() {
		WatchedObjectRegistry registry = new WatchedObjectRegistry();
		assertEquals(WatchedObject.Kind.SUN, registry.getCurrent().getKind());
	}

	@Test
	void selectionCanBeChanged() {
		WatchedObjectRegistry registry = new WatchedObjectRegistry();

		registry.setCurrent(WatchedObject.planet(4));

		assertEquals(WatchedObject.Kind.PLANET, registry.getCurrent().getKind());
		assertEquals(4, registry.getCurrent().getPlanetIndex());
	}

	@Test
	void rejectsNullSelection() {
		WatchedObjectRegistry registry = new WatchedObjectRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.setCurrent(null));
	}
}
