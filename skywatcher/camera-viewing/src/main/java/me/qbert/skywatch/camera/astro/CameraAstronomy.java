package me.qbert.skywatch.camera.astro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.TransactionalStateChangeListener;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.astro.service.MoonPrecession;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.exception.UninitializedObject;
import me.qbert.skywatch.listeners.ObjectStateChangeListener;
import me.qbert.skywatch.model.CelestialAddress;

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

// Long-lived, shared holder for one render session's astronomy state - Sun/Moon/Planets/Stars
// built ONCE and updated via a transactional recompute cascade, instead of the previous per-paint-
// call rebuild-from-scratch pattern in CelestialObjectsLayer/Graticule (confirmed: up to ~119,000
// fresh StarObject instances constructed per frame at the "all" tier, plus a fresh ObservationTime/
// ObserverLocation per render dispatch). Modeled directly on earthclock's own
// service.SequenceGenerator constructor pattern, refined against a real, run, failing-then-passing
// test the user wrote: src/test/java/me/qbert/skywatch/camera/TestDifferentStarCases.java - its
// final version (which superseded two earlier design iterations tried in the same session) is the
// authoritative reference this class ports. Do not "improve" on the registration shape below
// without re-reading that test's own history first (see CLAUDE.md/docs/tasks.md's Item 0 entry).
//
// Registration graph (load-bearing, confirmed against the reference test - do not restructure
// casually): Sun/Moon/Planets/every star, at every tier, build with
// setStateChangeListener(transactionalListener) - never setStateChangeListener(this).
// transactionalListener.addListener(...) is called only for sun, moon, solarObjects, and this
// CameraAstronomy instance itself - individual stars are NEVER added to the transactional
// listener's own broadcast list (that's what keeps the listener list bounded regardless of catalog
// size). This keeps stateChanged(...) firing EXACTLY ONCE per commit() no matter how many of
// time/location changed in that transaction - an earlier design (stars registering `this` directly
// on ObservationTime/ObserverLocation) fired 2-3x per transaction, because it created an
// additional, unbatched direct-registration path alongside the transactional one.
public final class CameraAstronomy implements ObjectStateChangeListener {

	// A deliberately far-past sentinel so the FIRST real applyTimeAndLocation(...) call is
	// guaranteed to represent a genuine Julian-date change (ObservationTime.recompute() only
	// notifies listeners when the Julian date actually changes) - this is what primes Sun/Moon/
	// Planets/Stars out of their post-build() degenerate-default position, without a separate,
	// bespoke "prime now" method. Relying on construction-time-vs-first-real-use coincidentally
	// differing (as the reference test implicitly does, via its own fixed test dates) would be
	// correct almost always but not provably always - this sentinel removes the "almost".
	private static final long CONSTRUCTION_SENTINEL_EPOCH_MILLIS = 0L; // 1970-01-01T00:00:00Z

	// Pairs a star's static catalog metadata (magnitude/name - needed for rendering) with its
	// already-built, shared CelestialObject (needed for position) - CelestialObjectsLayer.paintStars
	// needs both together per star; this avoids a second lookup into a parallel structure.
	public static final class ManagedStar {
		private final StarCoordinate coordinate;
		private final CelestialObject object;

		private ManagedStar(StarCoordinate coordinate, CelestialObject object) {
			this.coordinate = coordinate;
			this.object = object;
		}

		public StarCoordinate getCoordinate() {
			return coordinate;
		}

		public CelestialObject getObject() {
			return object;
		}
	}

	private final ObserverLocation location = new ObserverLocation();
	private final TransactionalStateChangeListener transactionalListener = new TransactionalStateChangeListener();
	private final ObservationTime time = new ObservationTime();

	private final CelestialObject sun;
	private final CelestialObject moon;
	private final AbstractCelestialObject solarObjects;

	// Item 7b: EclipticAnalemmaPath previously built a fresh SunPrecession/MoonPrecession (and,
	// lazily inside it, a fresh internal CelestialObject - see AbstractPrecession.init()) on EVERY
	// paint call. Both hold their own separate internal ObservationTime/CelestialObject entirely
	// distinct from this class's own sun/moon/time/location fields (a precession sample walks back
	// up to a full year of HISTORICAL points, not "now" - it can't share the transactional
	// listener/cascade above, which represents a single moment). What CAN be shared across calls is
	// the object identity itself: built once here, pointed at this astronomy's own `location` (so a
	// later location change is picked up automatically, same as sun/moon/planets/stars), and reused
	// by every subsequent paint - only calculatePrecession(...)'s inherent per-sample work remains
	// per-call, not the CelestialObject construction/registration AbstractPrecession.init() does the
	// first time calculatePrecession(...) is ever invoked on a given instance.
	private final SunPrecession sunPrecession;
	private final MoonPrecession moonPrecession;

	// Indexed groupLevel-1 - matches the reference test's starObjects[] shape exactly.
	private final List<List<ManagedStar>> starsByGroupLevel = new ArrayList<List<ManagedStar>>(3);

	// Precomputed per-mode views, rebuilt only when the star catalog itself changes (rebuildStars)
	// - never reconstructed per-frame/per-getActiveStars() call, which would reintroduce exactly the
	// kind of per-render allocation this class exists to eliminate.
	private List<ManagedStar> visibleStars = Collections.emptyList();
	private List<ManagedStar> namedStars = Collections.emptyList();
	private List<ManagedStar> allStars = Collections.emptyList();

	private StarCatalogTier starMode = StarCatalogTier.MAIN;

	public CameraAstronomy(TimeZone timeZone, List<StarCoordinate> starCatalog) throws UninitializedObject {
		if (timeZone == null)
			throw new IllegalArgumentException("timeZone must not be null");
		if (starCatalog == null)
			throw new IllegalArgumentException("starCatalog must not be null");

		time.initTime(timeZone);
		time.setUnixTime(CONSTRUCTION_SENTINEL_EPOCH_MILLIS);

		sun = SunObject.create().setStateChangeListener(transactionalListener).setObserverLocation(location)
				.setObserverTime(time).build();
		moon = MoonObject.create().setStateChangeListener(transactionalListener).setObserverLocation(location)
				.setObserverTime(time).build();
		solarObjects = (AbstractCelestialObject) SolarObjects.create().setStateChangeListener(transactionalListener)
				.setObserverLocation(location).setObserverTime(time).build();

		// Not registered on transactionalListener - see the field comment above, they track their
		// own independent historical time series rather than "now".
		sunPrecession = new SunPrecession(location, false);
		moonPrecession = new MoonPrecession(location, false);

		for (int i = 0; i < 3; i++)
			starsByGroupLevel.add(new ArrayList<ManagedStar>());
		buildStars(starCatalog);

		transactionalListener.addListener(sun);
		transactionalListener.addListener(moon);
		transactionalListener.addListener(solarObjects);
		transactionalListener.addListener(this);
	}

	private void buildStars(List<StarCoordinate> starCatalog) throws UninitializedObject {
		List<ManagedStar> visible = new ArrayList<ManagedStar>();
		for (StarCoordinate star : starCatalog) {
			CelestialAddress address = new CelestialAddress();
			address.setAddress(star.getRightAscension(), star.getDeclination());

			CelestialObject built = StarObject.create().setStarLocation(address)
					.setStateChangeListener(transactionalListener).setObserverLocation(location)
					.setObserverTime(time).build();

			ManagedStar managed = new ManagedStar(star, built);

			int groupLevel = star.getGroupLevel();
			if (groupLevel >= 1 && groupLevel <= 3)
				starsByGroupLevel.get(groupLevel - 1).add(managed);

			if (star.isVisible())
				visible.add(managed);
		}

		visibleStars = Collections.unmodifiableList(visible);
		namedStars = concat(starsByGroupLevel.get(0), starsByGroupLevel.get(1));
		allStars = concat(namedStars, starsByGroupLevel.get(2));
	}

	private static List<ManagedStar> concat(List<ManagedStar> a, List<ManagedStar> b) {
		List<ManagedStar> combined = new ArrayList<ManagedStar>(a.size() + b.size());
		combined.addAll(a);
		combined.addAll(b);
		return Collections.unmodifiableList(combined);
	}

	// Re-derives the full star set from a fresh catalog (e.g. after the "locally visible" override
	// file changes on disk) - rebuilds every bucket from scratch, then forces an immediate recompute
	// of whatever's currently active (see setStarMode's own comment for why that step isn't
	// optional). Not a per-frame operation - called only when the catalog itself actually changes.
	public void rebuildStars(List<StarCoordinate> starCatalog) throws UninitializedObject {
		if (starCatalog == null)
			throw new IllegalArgumentException("starCatalog must not be null");
		for (List<ManagedStar> bucket : starsByGroupLevel)
			bucket.clear();
		buildStars(starCatalog);
		recomputeActiveStars();
	}

	// Mode switches must force an immediate, manual recompute of the newly-active bucket - validated
	// as necessary (not optional) by the reference test's own canary/recovery cycle: the
	// transactional/commit path alone will not catch a dormant bucket up until its NEXT natural
	// time/location change, which could be arbitrarily delayed, leaving stale positions rendered in
	// the meantime.
	public void setStarMode(StarCatalogTier starMode) {
		if (starMode == null)
			throw new IllegalArgumentException("starMode must not be null");
		this.starMode = starMode;
		recomputeActiveStars();
	}

	public StarCatalogTier getStarMode() {
		return starMode;
	}

	public ObservationTime getObservationTime() {
		return time;
	}

	public ObserverLocation getObserverLocation() {
		return location;
	}

	public CelestialObject getSun() {
		return sun;
	}

	public CelestialObject getMoon() {
		return moon;
	}

	public AbstractCelestialObject getSolarObjects() {
		return solarObjects;
	}

	// Item 7b - the shared, built-once precession instances (see the field comment). Callers set
	// showAsAnalemma via the instance's own existing setShowAsAnalemma(...) rather than reconstructing.
	public SunPrecession getSunPrecession() {
		return sunPrecession;
	}

	public MoonPrecession getMoonPrecession() {
		return moonPrecession;
	}

	// The currently-active star bucket, per getStarMode() - what CelestialObjectsLayer.paintStars
	// should iterate. O(1) - returns a precomputed view, never reconstructed per call.
	public List<ManagedStar> getActiveStars() {
		switch (starMode) {
			case VISIBLE_ONLY:
				return visibleStars;
			case MAIN:
				return starsByGroupLevel.get(0);
			case NAMED:
				return namedStars;
			case ALL:
			default:
				return allStars;
		}
	}

	// The single mutation entry point every caller should use instead of touching
	// getObservationTime()/getObserverLocation() directly and separately - wraps both changes in one
	// transaction so a same-tick time+location change (e.g. roaming) cascades as a single recompute,
	// not two.
	public void applyTimeAndLocation(long unixTimeMillis, double latitude, double longitude)
			throws UninitializedObject {
		transactionalListener.begin();
		time.setUnixTime(unixTimeMillis);
		location.setGeoLocation(latitude, longitude);
		transactionalListener.commit();
	}

	@Override
	public void stateChanged(Object source, ObjectStateChangeListener listener) {
		recomputeActiveStars();
	}

	private void recomputeActiveStars() {
		for (ManagedStar star : getActiveStars())
			star.getObject().stateChanged(this, star.getObject());
	}
}
