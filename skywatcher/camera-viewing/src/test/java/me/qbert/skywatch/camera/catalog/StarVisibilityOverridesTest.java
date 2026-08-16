package me.qbert.skywatch.camera.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StarVisibilityOverridesTest {

	private static StarCoordinate star(String name, String designation, int groupLevel, boolean visible) {
		return new StarCoordinate(name, designation, 3.0, 10.0, 20.0, groupLevel, visible);
	}

	@Test
	void loadOfAMissingFileReturnsEmptyNotAnError(@TempDir File tempDir) throws IOException {
		File missing = new File(tempDir, "does-not-exist.csv");
		assertTrue(StarVisibilityOverrides.load(missing).isEmpty());
	}

	@Test
	void saveThenLoadRoundTrips(@TempDir File tempDir) throws IOException {
		File file = new File(tempDir, "overrides.csv");
		List<StarCoordinate> overrides = new ArrayList<StarCoordinate>();
		overrides.add(star("Vega", "HR 7001", 3, true));
		overrides.add(star("Custom", "USER 1", 3, true));

		StarVisibilityOverrides.save(file, overrides);
		List<StarCoordinate> loaded = StarVisibilityOverrides.load(file);

		assertEquals(2, loaded.size());
		assertEquals("Vega", loaded.get(0).getName());
		assertTrue(loaded.get(0).isVisible());
		assertEquals("Custom", loaded.get(1).getName());
	}

	@Test
	void saveCreatesParentDirectoriesIfNeeded(@TempDir File tempDir) throws IOException {
		File file = new File(new File(tempDir, "nested/dir"), "overrides.csv");
		StarVisibilityOverrides.save(file, java.util.Collections.singletonList(star("A", "D1", 1, true)));
		assertTrue(file.exists());
	}

	@Test
	void mergeOverridesAnExistingDesignationWholesale() {
		List<StarCoordinate> base = new ArrayList<StarCoordinate>();
		base.add(star("Acamar", "HR 897", 1, false));
		base.add(star("Achernar", "HR 472", 1, false));

		List<StarCoordinate> overrides = new ArrayList<StarCoordinate>();
		overrides.add(star("Acamar", "HR 897", 1, true));

		List<StarCoordinate> merged = StarVisibilityOverrides.merge(base, overrides);

		assertEquals(2, merged.size(), "overriding an existing designation must not add a duplicate entry");
		StarCoordinate acamar = merged.stream().filter(s -> "HR 897".equals(s.getDesignation())).findFirst().get();
		assertTrue(acamar.isVisible());
		StarCoordinate achernar = merged.stream().filter(s -> "HR 472".equals(s.getDesignation())).findFirst().get();
		assertFalse(achernar.isVisible(), "a designation with no override must keep the base's own value");
	}

	@Test
	void mergeIntroducesABrandNewStarNotPresentInBase() {
		List<StarCoordinate> base = new ArrayList<StarCoordinate>();
		base.add(star("Acamar", "HR 897", 1, false));

		List<StarCoordinate> overrides = new ArrayList<StarCoordinate>();
		overrides.add(star("MyOwnStar", "USER 42", 3, true));

		List<StarCoordinate> merged = StarVisibilityOverrides.merge(base, overrides);

		assertEquals(2, merged.size());
		assertTrue(merged.stream().anyMatch(s -> "USER 42".equals(s.getDesignation())));
	}

	@Test
	void withVisibilityTogglesAnExistingBaseStarByDesignation() {
		List<StarCoordinate> base = new ArrayList<StarCoordinate>();
		base.add(star("Acamar", "HR 897", 1, false));

		List<StarCoordinate> overrides = StarVisibilityOverrides.withVisibility(java.util.Collections.emptyList(), base,
				"HR 897", true);

		assertEquals(1, overrides.size());
		assertEquals("Acamar", overrides.get(0).getName(), "the star's other fields must be looked up from base");
		assertTrue(overrides.get(0).isVisible());
	}

	@Test
	void withVisibilityReplacesAnExistingOverrideRatherThanDuplicatingIt() {
		List<StarCoordinate> base = new ArrayList<StarCoordinate>();
		base.add(star("Acamar", "HR 897", 1, false));

		List<StarCoordinate> firstToggle = StarVisibilityOverrides.withVisibility(java.util.Collections.emptyList(), base,
				"HR 897", true);
		List<StarCoordinate> secondToggle = StarVisibilityOverrides.withVisibility(firstToggle, base, "HR 897", false);

		assertEquals(1, secondToggle.size());
		assertFalse(secondToggle.get(0).isVisible());
	}

	@Test
	void withVisibilityRejectsAnUnknownDesignation() {
		List<StarCoordinate> base = new ArrayList<StarCoordinate>();
		base.add(star("Acamar", "HR 897", 1, false));

		assertThrows(IllegalArgumentException.class,
				() -> StarVisibilityOverrides.withVisibility(java.util.Collections.emptyList(), base, "NO SUCH STAR", true));
	}

	@Test
	void addOrReplaceIntroducesABrandNewStarByDesignation() {
		List<StarCoordinate> overrides = StarVisibilityOverrides.addOrReplace(java.util.Collections.emptyList(),
				star("MyOwnStar", "USER 1", 3, true));

		assertEquals(1, overrides.size());
		assertEquals("MyOwnStar", overrides.get(0).getName());
	}
}
