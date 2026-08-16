package me.qbert.skywatch.camera.astro;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.camera.astro.CameraAstronomy.ManagedStar;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

// Ports the registration/mode-switch contract validated by the user's own
// src/test/java/me/qbert/skywatch/camera/TestDifferentStarCases.java (a real, run,
// failing-then-passing manual test) into real JUnit assertions against the production
// CameraAstronomy class - see that file's own history (superseded twice during design) and
// CLAUDE.md/docs/tasks.md's Item 0 entry for the full reasoning behind each assertion below.
class CameraAstronomyTest {

	private static List<StarCoordinate> sampleCatalog() {
		List<StarCoordinate> stars = new ArrayList<StarCoordinate>();
		// One star per groupLevel, plus one extra groupLevel-3 star, plus one visible=true star
		// buried in groupLevel 3 (proving VISIBLE_ONLY ignores groupLevel entirely).
		stars.add(new StarCoordinate("Main1", "D1", 1.0, 10.0, 10.0, 1, false));
		stars.add(new StarCoordinate("Named1", "D2", 2.0, 20.0, 20.0, 2, false));
		stars.add(new StarCoordinate("Bulk1", "D3", 3.0, 30.0, 30.0, 3, false));
		stars.add(new StarCoordinate("Bulk2", "D4", 4.0, 40.0, 40.0, 3, false));
		stars.add(new StarCoordinate("VisibleBulk", "D5", 5.0, 50.0, 50.0, 3, true));
		return stars;
	}

	private static CameraAstronomy build() throws Exception {
		return new CameraAstronomy(TimeZone.getTimeZone("UTC"), sampleCatalog());
	}

	private static ObjectDirectionRaDec directionOf(ManagedStar star) {
		return star.getObject().getCurrentDirection();
	}

	// Ground truth: builds and recomputes a wholly independent StarObject for the given catalog
	// entry at the given time/location, bypassing CameraAstronomy entirely - used to verify a
	// managed star's actual reported direction, rather than trusting a hardcoded "degenerate"
	// sentinel (declination comes straight from catalog data regardless of recompute state, so
	// RA==0 && Dec==0 does not reliably detect "never recomputed" for a star - confirmed the hard
	// way: an earlier version of this test using that check kept passing even with the production
	// force-recompute-on-mode-switch call deliberately removed).
	private static ObjectDirectionRaDec independentlyComputedDirection(StarCoordinate star, long unixTimeMillis,
			double latitude, double longitude) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(TimeZone.getTimeZone("UTC"));
		time.setUnixTime(unixTimeMillis);

		ObserverLocation location = new ObserverLocation();
		location.setGeoLocation(latitude, longitude);

		CelestialAddress address = new CelestialAddress();
		address.setAddress(star.getRightAscension(), star.getDeclination());

		CelestialObject reference = StarObject.create().setStarLocation(address).setObserverLocation(location)
				.setObserverTime(time).build();
		reference.recompute();
		return reference.getCurrentDirection();
	}

	@Test
	void constructionSucceedsAndBuildsEveryObject() throws Exception {
		CameraAstronomy astronomy = build();

		assertTrue(astronomy.getSun() != null);
		assertTrue(astronomy.getMoon() != null);
		assertTrue(astronomy.getSolarObjects() != null);
	}

	@Test
	void starModeFilteringMatchesTheCumulativeDesign() throws Exception {
		CameraAstronomy astronomy = build();

		astronomy.setStarMode(StarCatalogTier.MAIN);
		assertEquals(1, astronomy.getActiveStars().size(), "MAIN is groupLevel<=1 only");

		astronomy.setStarMode(StarCatalogTier.NAMED);
		assertEquals(2, astronomy.getActiveStars().size(), "NAMED is groupLevel<=2 (union of 1+2)");

		astronomy.setStarMode(StarCatalogTier.ALL);
		assertEquals(5, astronomy.getActiveStars().size(), "ALL is every star regardless of groupLevel");

		astronomy.setStarMode(StarCatalogTier.VISIBLE_ONLY);
		assertEquals(1, astronomy.getActiveStars().size(), "VISIBLE_ONLY ignores groupLevel entirely");
		assertEquals("VisibleBulk", astronomy.getActiveStars().get(0).getCoordinate().getName());
	}

	@Test
	void applyTimeAndLocationRecomputesSunMoonPlanetsAndTheActiveStarBucket() throws Exception {
		CameraAstronomy astronomy = build();
		astronomy.setStarMode(StarCatalogTier.ALL);

		ObjectDirectionRaDec initialSun = astronomy.getSun().getCurrentDirection();
		ObjectDirectionRaDec initialStar = directionOf(astronomy.getActiveStars().get(0));

		astronomy.applyTimeAndLocation(1_700_000_000_000L, 45.0, -75.0);
		ObjectDirectionRaDec afterFirstMove = astronomy.getSun().getCurrentDirection();
		ObjectDirectionRaDec afterFirstMoveStar = directionOf(astronomy.getActiveStars().get(0));

		assertNotEquals(initialSun.getRightAscension(), afterFirstMove.getRightAscension(),
				"sun must recompute after the first real time+location change");
		assertNotEquals(initialStar.getRightAscension(), afterFirstMoveStar.getRightAscension(),
				"an active-tier star must recompute too - hour angle depends on both time and location");

		astronomy.applyTimeAndLocation(1_700_003_600_000L, 45.0, -75.0);
		ObjectDirectionRaDec afterSecondMove = astronomy.getSun().getCurrentDirection();
		assertNotEquals(afterFirstMove.getRightAscension(), afterSecondMove.getRightAscension(),
				"a second, later time change must recompute again, not just once ever");
	}

	// The critical, previously-broken behavior the user's own test caught by running it: switching
	// to a mode whose bucket has been dormant (never told to recompute while a different mode was
	// active) must immediately reflect the CURRENT time/location, not whatever stale value it held
	// from before it went dormant (or its post-build() degenerate default if it was never active at
	// all). Without setStarMode(...) forcing an immediate recompute, this would fail exactly the way
	// the user's own canary test failed before its fix.
	@Test
	void switchingStarModeImmediatelyRecomputesTheNewlyActiveBucketWithoutWaitingForTheNextTimeChange()
			throws Exception {
		CameraAstronomy astronomy = build();

		// ALL active first, so every star (including groupLevel 3) is correctly recomputed at T1.
		astronomy.setStarMode(StarCatalogTier.ALL);
		astronomy.applyTimeAndLocation(1_700_000_000_000L, 45.0, -75.0);

		// Switch to MAIN - groupLevel 2/3 stars go dormant, still holding T1's position.
		astronomy.setStarMode(StarCatalogTier.MAIN);

		// A DIFFERENT time/location, while groupLevel 2/3 stars are dormant - they must NOT
		// recompute (that's the whole point of the bucketed design), so they're now stale relative
		// to T2/loc2.
		long t2 = 1_700_003_600_000L;
		astronomy.applyTimeAndLocation(t2, 47.0, -80.0);

		// Switch back to ALL, WITHOUT any further time/location change - the previously-dormant
		// groupLevel 2/3 stars must IMMEDIATELY reflect T2/loc2, not their stale T1 value.
		astronomy.setStarMode(StarCatalogTier.ALL);

		for (ManagedStar star : astronomy.getActiveStars()) {
			if (star.getCoordinate().getGroupLevel() == 1)
				continue; // groupLevel 1 was never dormant - not the behavior under test here.

			ObjectDirectionRaDec actual = directionOf(star);
			ObjectDirectionRaDec expected = independentlyComputedDirection(star.getCoordinate(), t2, 47.0, -80.0);
			assertEquals(expected.getRightAscension(), actual.getRightAscension(), 0.0001,
					"star " + star.getCoordinate().getName()
							+ " must be force-recomputed to T2's position immediately on mode switch, not left at its stale T1 value");
			assertEquals(expected.getDeclination(), actual.getDeclination(), 0.0001);
		}
	}

	@Test
	void rebuildStarsReplacesTheCatalogAndRecomputesImmediately() throws Exception {
		CameraAstronomy astronomy = build();
		astronomy.setStarMode(StarCatalogTier.ALL);
		long time = 1_700_000_000_000L;
		astronomy.applyTimeAndLocation(time, 45.0, -75.0);

		List<StarCoordinate> replacement = new ArrayList<StarCoordinate>();
		StarCoordinate onlyCoordinate = new StarCoordinate("OnlyStar", "D9", 9.0, 99.0, 9.0, 1, false);
		replacement.add(onlyCoordinate);
		astronomy.rebuildStars(replacement);

		assertEquals(1, astronomy.getActiveStars().size());
		ManagedStar onlyStar = astronomy.getActiveStars().get(0);
		assertEquals("OnlyStar", onlyStar.getCoordinate().getName());

		ObjectDirectionRaDec actual = directionOf(onlyStar);
		ObjectDirectionRaDec expected = independentlyComputedDirection(onlyCoordinate, time, 45.0, -75.0);
		assertEquals(expected.getRightAscension(), actual.getRightAscension(), 0.0001,
				"the rebuilt star must be force-recomputed immediately, matching mode-switch");
		assertEquals(expected.getDeclination(), actual.getDeclination(), 0.0001);
	}

	// Item 7b - proves the precession instances are built exactly ONCE (shared object identity across
	// calls), not reconstructed per getSunPrecession()/getMoonPrecession() call - the same
	// per-frame-construction cost Item 0 already eliminated elsewhere in this class.
	@Test
	void sunAndMoonPrecessionInstancesAreSharedAcrossCalls() throws Exception {
		CameraAstronomy astronomy = build();

		assertTrue(astronomy.getSunPrecession() == astronomy.getSunPrecession(),
				"getSunPrecession() must return the same instance every call, not a fresh one");
		assertTrue(astronomy.getMoonPrecession() == astronomy.getMoonPrecession(),
				"getMoonPrecession() must return the same instance every call, not a fresh one");
	}

	@Test
	void rejectsNullConstructorArguments() {
		assertThrows(IllegalArgumentException.class, () -> new CameraAstronomy(null, sampleCatalog()));
		assertThrows(IllegalArgumentException.class,
				() -> new CameraAstronomy(TimeZone.getTimeZone("UTC"), null));
	}
}
