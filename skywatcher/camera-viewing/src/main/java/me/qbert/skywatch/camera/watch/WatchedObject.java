package me.qbert.skywatch.camera.watch;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.GeoLocation;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.skywatch.model.ObjectDirectionRaDec;

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

// An immutable identity for "which real object is currently being watched" [spec §6/§8] - Sun,
// Moon, one of SolarObjects.OBJECT_LIST's eight planets, or a catalog star. This is the piece the
// three still-deferred Layer-2/Layer-5 features (4.3's crosshair, 4.6's watched-object-path mode,
// 4.9's expandable per-watched-object OSD detail tier - see docs/tasks.md's "Still open, all
// converging on the same missing piece") were all blocked on: none of them needed a NEW way to
// compute a position, they needed a uniform way to say "this object, resolved at this (possibly
// not-now) time" regardless of which of the four underlying sw-base classes actually backs it.
//
// resolveAltAz(...)/resolveRaDec(...) deliberately take their own ObservationTime/ObserverLocation
// rather than capturing one at construction - the whole point of a watched-object crosshair (a
// position at a DIFFERENT reference time than the live render) and a watched-object path (positions
// sampled across a trailing time window) is resolving the same identity at many different times,
// so a WatchedObject has to stay a plain, reusable identity rather than a single computed result.
//
// Reuses the exact SunObject/MoonObject/SolarObjects/StarObject construction + the build()-does-not-
// recompute() gotcha handling already established in render.CelestialObjectsLayer - no new
// RA/Dec-to-Alt/Az math is written here, only object selection.
public final class WatchedObject {

	public enum Kind {
		SUN, MOON, PLANET, STAR
	}

	private final Kind kind;
	private final int planetIndex; // SolarObjects.OBJECT_LIST index, 1..8 - meaningful for PLANET only
	private final StarCoordinate star; // meaningful for STAR only

	private WatchedObject(Kind kind, int planetIndex, StarCoordinate star) {
		this.kind = kind;
		this.planetIndex = planetIndex;
		this.star = star;
	}

	// Spec §6's own stated default ("Object defaults to the sun") - see WatchedObjectRegistry.
	public static WatchedObject sun() {
		return new WatchedObject(Kind.SUN, -1, null);
	}

	public static WatchedObject moon() {
		return new WatchedObject(Kind.MOON, -1, null);
	}

	// planetIndex matches SolarObjects.OBJECT_LIST's own indexing (1..8 - index 0 is the Sun's
	// alternate slot, deliberately unused here too, see CelestialObjectsLayer's paintPlanets note).
	public static WatchedObject planet(int planetIndex) {
		if (planetIndex < 1 || planetIndex >= SolarObjects.OBJECT_LIST.length)
			throw new IllegalArgumentException(
					"planetIndex must be in [1, " + (SolarObjects.OBJECT_LIST.length - 1) + "]");
		return new WatchedObject(Kind.PLANET, planetIndex, null);
	}

	public static WatchedObject star(StarCoordinate star) {
		if (star == null)
			throw new IllegalArgumentException("star must not be null");
		return new WatchedObject(Kind.STAR, -1, star);
	}

	public Kind getKind() {
		return kind;
	}

	public int getPlanetIndex() {
		if (kind != Kind.PLANET)
			throw new IllegalStateException("getPlanetIndex() only applies to a PLANET watched object, was " + kind);
		return planetIndex;
	}

	public StarCoordinate getStar() {
		if (kind != Kind.STAR)
			throw new IllegalStateException("getStar() only applies to a STAR watched object, was " + kind);
		return star;
	}

	public String getDisplayName() {
		switch (kind) {
			case SUN:
				return "Sun";
			case MOON:
				return "Moon";
			case PLANET:
				return SolarObjects.OBJECT_LIST[planetIndex].trim();
			case STAR:
				return star.getName();
			default:
				throw new IllegalStateException("unhandled kind: " + kind);
		}
	}

	public ObjectDirectionAltAz resolveAltAz(ObservationTime time, ObserverLocation location) throws Exception {
		return resolve(time, location).getCurrentDirectionAsAltitudeAzimuth();
	}

	public ObjectDirectionRaDec resolveRaDec(ObservationTime time, ObserverLocation location) throws Exception {
		return resolve(time, location).getCurrentDirection();
	}

	// Spec §8's "Object RA/Dec" OSD field - the object's own celestial-sphere coordinate, distinct
	// from resolveRaDec(...)'s local/hour-angle-adjusted "Local RA/current Dec" field. The two map
	// directly onto CelestialObject's own two methods (getCurrentDirection() vs
	// getCelestialSphereLocation()), already a stable, established distinction in sw-base - not a
	// new concept invented here.
	//
	// CAVEAT found while wiring this up, worth flagging rather than silently trusting: for MOON
	// specifically, sw-base's MoonObject.getCelestialSphereLocation() does NOT return true equatorial
	// right ascension - its internal `rightAscension` field is actually assigned the moon's geocentric
	// ECLIPTIC longitude (astro.impl.MoonObject.recompute(), the `rightAscension = L` line), not the
	// atan2-derived equatorial RA computed a few lines earlier in the same method. This looks like a
	// pre-existing naming/labeling inconsistency in sw-base itself (SunObject's equivalent method does
	// NOT have this problem - its rightAscension field is the real atan2-derived equatorial RA). Not
	// changed here - sw-base is a shared dependency also used by earthclock/geolocator_one, and this
	// module's own convention is to fix a confirmed sw-base bug only after a deliberate, separately
	// justified decision, not as a side effect of an unrelated feature. Callers displaying this value
	// for a Moon watched object should label it accordingly (or avoid relying on it being true RA)
	// until/unless that's addressed upstream.
	public ObjectDirectionRaDec resolveCelestialSphereLocation(ObservationTime time, ObserverLocation location)
			throws Exception {
		return resolve(time, location).getCelestialSphereLocation();
	}

	// Spec §8's "Sub-object (overhead) lat/lon" OSD field - the point on Earth's surface directly
	// beneath the object right now.
	public GeoLocation resolveSubObjectLocation(ObservationTime time, ObserverLocation location) throws Exception {
		return resolve(time, location).getEarthPositionOverhead();
	}

	private CelestialObject resolve(ObservationTime time, ObserverLocation location) throws Exception {
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");

		CelestialObject object;
		switch (kind) {
			case SUN:
				object = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
				break;
			case MOON:
				object = MoonObject.create().setObserverLocation(location).setObserverTime(time).build();
				break;
			case PLANET: {
				SolarObjects solarObjects = (SolarObjects) SolarObjects.create().setObserverLocation(location)
						.setObserverTime(time).build();
				solarObjects.setObjectIndex(planetIndex);
				object = solarObjects;
				break;
			}
			case STAR: {
				CelestialAddress address = new CelestialAddress();
				address.setAddress(star.getRightAscension(), star.getDeclination());
				object = StarObject.create().setStarLocation(address).setObserverLocation(location)
						.setObserverTime(time).build();
				break;
			}
			default:
				throw new IllegalStateException("unhandled kind: " + kind);
		}

		// build() does not recompute() - see CelestialObjectsLayer's class comment gotcha note; every
		// resolve() call needs its own explicit recompute() since each call may target a different time.
		object.recompute();
		return object;
	}
}
