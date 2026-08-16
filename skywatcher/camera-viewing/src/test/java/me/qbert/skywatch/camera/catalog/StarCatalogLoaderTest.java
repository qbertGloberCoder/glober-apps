package me.qbert.skywatch.camera.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

class StarCatalogLoaderTest {

	private List<StarCoordinate> loadStarsDb() throws IOException {
		InputStream stream = getClass().getResourceAsStream("/stars.db");
		assertTrue(stream != null, "stars.db must be on the test classpath");
		return new StarCatalogLoader().load(stream);
	}

	@Test
	void loadsAllRowsExceptTheSentinel() throws IOException {
		List<StarCoordinate> stars = loadStarsDb();

		// 119,608 raw rows minus the one "Null,NULLNULL,..." sentinel row.
		assertEquals(119607, stars.size());
		for (StarCoordinate star : stars) {
			assertFalse("Null".equals(star.getName()) && "NULLNULL".equals(star.getDesignation()));
		}
	}

	// stars.db dropped its trailing "visible" column entirely this round - a real user complaint
	// that per-user local-visibility data "does not belong in the project repository and even if it
	// did, it would be compiled into the jar file, making updates very hard to implement". Every
	// star loaded straight from stars.db now defaults visible=false - see
	// catalog.StarVisibilityOverrides for where the real per-user flag now lives.
	@Test
	void everyStarLoadedFromStarsDbDefaultsToNotVisible() throws IOException {
		List<StarCoordinate> stars = loadStarsDb();
		for (StarCoordinate star : stars)
			assertFalse(star.isVisible());
	}

	@Test
	void groupLevelTiersMatchConfirmedCountsAndAreCumulative() throws IOException {
		List<StarCoordinate> stars = loadStarsDb();
		StarCatalogLoader loader = new StarCatalogLoader();

		// Confirmed by direct inspection of stars.db: groupLevel 1 = "main" (336, matches
		// earthclock/src/main/resources/stars.txt's row count), groupLevel 2 = 21 raw rows (20 after
		// the sentinel, itself groupLevel 2, is filtered out), groupLevel 3 = the bulk remainder.
		// MAIN/NAMED/ALL are CUMULATIVE groupLevel<=1/2/3 - restructured this round from an earlier
		// exact-match design that had no way to select "groupLevel 1 and 2 together" at all.
		assertEquals(336, loader.filterByTier(stars, StarCatalogTier.MAIN).size());
		assertEquals(336 + 20, loader.filterByTier(stars, StarCatalogTier.NAMED).size());
		assertEquals(119607, loader.filterByTier(stars, StarCatalogTier.ALL).size());
		// Every star defaults visible=false (see the test above), so VISIBLE_ONLY is empty until an
		// override file marks some stars visible.
		assertEquals(0, loader.filterByTier(stars, StarCatalogTier.VISIBLE_ONLY).size());
	}

	@Test
	void aKnownStarParsesCorrectly() throws IOException {
		List<StarCoordinate> stars = loadStarsDb();

		StarCoordinate acamar = stars.stream()
				.filter(s -> "Acamar".equals(s.getName()))
				.findFirst()
				.orElse(null);

		assertTrue(acamar != null, "Acamar should be in the catalog");
		assertEquals("HR 897", acamar.getDesignation());
		assertEquals(2.88, acamar.getApparentMagnitude(), 0.0001);
		assertEquals(44.565311, acamar.getRightAscension(), 0.0001);
		assertEquals(-40.304672, acamar.getDeclination(), 0.0001);
		assertEquals(1, acamar.getGroupLevel());
		assertFalse(acamar.isVisible());
	}
}
