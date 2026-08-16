package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.service.AbstractPrecession.PrecessionData;
import me.qbert.skywatch.astro.service.MoonPrecession;
import me.qbert.skywatch.astro.service.SunPrecession;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.exception.UninitializedObject;
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

// The ecliptic/analemma slice of the "sample positions over time, plot as path" primitive [spec
// §6] - see CLAUDE.md's "Reused core" table: sw-base's AbstractPrecession/SunPrecession/
// MoonPrecession already implement the sampling itself, so this is deliberately just a thin
// adapter, not a reimplementation of the sampling loop.
//
// sampleSun(...)/sampleMoon(...) (RA/Dec) predate paintSun(...)/paintMoon(...) (this round) - kept
// as-is rather than removed, since they're independently useful for anything that wants the raw
// RA/Dec series (e.g. a map-projection consumer, the way earthclock's AbstractCelestialObjects
// plots PrecessionData.getGroundPosition() on a map instead - a different projection target
// entirely, not something this module needs, but the RA/Dec accessor costs nothing to keep).
//
// paintSun(...)/paintMoon(...) do NOT use PrecessionData.getAltAz() directly - a real, confirmed
// bug found by the user (analemma mode rendered as a straight line/huge annual sweep, not a compact
// figure-8): getAltAz() is derived through AbstractPrecession's own RA/hour-angle pipeline, which
// has a genuine bug in sw-base (SunPrecession/MoonPrecession's getRaAdvanceAnalemma() are both
// hardcoded to 0.0, so analemma mode's raAdjust correction never fires - confirmed via a direct
// sampler run: altitude swept -49 to +49 degrees and azimuth through nearly the full 360 degrees
// over a year, nowhere close to a compact figure-8). Deliberately NOT fixed in sw-base as part of
// this round - the user's own words: "it IS a bug and should eventually be fixed, but I need to
// rework the clock app with test cases so we can rework it as needed to work there as well as
// here" - shared with earthclock/geolocator_one, a separate, deferred round.
//
// Instead, this class sidesteps the buggy pipeline entirely using the user's own verified
// workaround: each PrecessionData's getGroundPosition() (the sub-object point on Earth - the
// latitude/longitude directly beneath the object at that sample's own moment) is derived through an
// independent, correct geographic calculation, regardless of the RA/hour-angle bug. Two standard
// identities convert it to a genuine alt/az without ever touching the buggy path: the sub-point's
// latitude IS the object's declination, and the longitude difference between the observer and the
// sub-point directly encodes local hour angle (longitude and hour angle are tied 1:1 by Earth's
// rotation at any instant) - building a synthetic RA/Dec from those two values and running it
// through the ordinary GeoCalculator.raDeclinationToAltitudeAzimuth(...) conversion reproduces the
// exact same physically-correct position as the (working) ecliptic mode, but ALSO now correctly
// collapses to a compact figure-8 in analemma mode - verified numerically by the user's own
// standalone test against both modes before this fix was written here.
//
// Layer placement: painted as part of the objects sub-layer (3B/3C, whichever slot isn't ground),
// BEFORE the live sun/moon/planet/star glyphs - see CLAUDE.md's "Layer 2 sits above sky but below
// ground/objects" section for why this moved out of Layer 2 (unlike the graticule and
// watched-object crosshair, which stayed there) - render.FrameCompositor wires the ordering, this
// class only knows how to sample and draw, not where in the stack it belongs.
public final class EclipticAnalemmaPath {
	private EclipticAnalemmaPath() {
	}

	public static List<ObjectDirectionRaDec> sampleSun(ObserverLocation location, ObservationTime endTime,
			boolean asAnalemma) throws UninitializedObject {
		SunPrecession precession = new SunPrecession(location, asAnalemma);
		return toRaDecList(precession.calculatePrecession(endTime));
	}

	public static List<ObjectDirectionRaDec> sampleMoon(ObserverLocation location, ObservationTime endTime,
			boolean asAnalemma) throws UninitializedObject {
		MoonPrecession precession = new MoonPrecession(location, asAnalemma);
		return toRaDecList(precession.calculatePrecession(endTime));
	}

	// Public alongside sampleSun/sampleMoon's RA/Dec equivalents - symmetric accessors, and useful
	// on their own for tests/future consumers that want the resolved alt/az series without also
	// pulling in a Graphics2D (e.g. aiming a camera precisely at one sample the way this module's
	// other tests aim precisely at a live object's own computed position). location is threaded
	// through to toAltAzList(...) now (previously unused for this conversion) - required by the
	// ground-position-based reformulation, see the class comment.
	public static List<ObjectDirectionAltAz> sampleSunAltAz(ObserverLocation location, ObservationTime endTime,
			boolean asAnalemma) throws UninitializedObject {
		SunPrecession precession = new SunPrecession(location, asAnalemma);
		return toAltAzList(precession.calculatePrecession(endTime), location);
	}

	public static List<ObjectDirectionAltAz> sampleMoonAltAz(ObserverLocation location, ObservationTime endTime,
			boolean asAnalemma) throws UninitializedObject {
		MoonPrecession precession = new MoonPrecession(location, asAnalemma);
		return toAltAzList(precession.calculatePrecession(endTime), location);
	}

	public static void paintSun(Graphics2D g2d, ObserverLocation location, ObservationTime endTime, boolean asAnalemma,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintPath(g2d, sampleSunAltAz(location, endTime, asAnalemma), projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
	}

	public static void paintMoon(Graphics2D g2d, ObserverLocation location, ObservationTime endTime, boolean asAnalemma,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintPath(g2d, sampleMoonAltAz(location, endTime, asAnalemma), projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
	}

	// CameraAstronomy-backed overloads (Item 7b) - reuse astronomy's own long-lived SunPrecession/
	// MoonPrecession instances (built once at CameraAstronomy construction, sharing its
	// ObserverLocation) instead of constructing a fresh SunPrecession/MoonPrecession - and, lazily
	// inside it, a fresh internal CelestialObject - on every single paint call, the same per-frame-
	// construction cost Item 0 already eliminated for sun/moon/planets/stars/graticule. Mode
	// switching is just a setter on the reused instance, not a reconstruction.
	public static List<ObjectDirectionAltAz> sampleSunAltAz(CameraAstronomy astronomy, boolean asAnalemma)
			throws UninitializedObject {
		SunPrecession precession = astronomy.getSunPrecession();
		precession.setShowAsAnalemma(asAnalemma);
		return toAltAzList(precession.calculatePrecession(astronomy.getObservationTime()), astronomy.getObserverLocation());
	}

	public static List<ObjectDirectionAltAz> sampleMoonAltAz(CameraAstronomy astronomy, boolean asAnalemma)
			throws UninitializedObject {
		MoonPrecession precession = astronomy.getMoonPrecession();
		precession.setShowAsAnalemma(asAnalemma);
		return toAltAzList(precession.calculatePrecession(astronomy.getObservationTime()), astronomy.getObserverLocation());
	}

	public static void paintSun(Graphics2D g2d, CameraAstronomy astronomy, boolean asAnalemma,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintPath(g2d, sampleSunAltAz(astronomy, asAnalemma), projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	public static void paintMoon(Graphics2D g2d, CameraAstronomy astronomy, boolean asAnalemma,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintPath(g2d, sampleMoonAltAz(astronomy, asAnalemma), projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	private static void paintPath(Graphics2D g2d, List<ObjectDirectionAltAz> points, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) {
		Point2D.Double previous = null;
		for (ObjectDirectionAltAz altAz : points) {
			Point2D.Double current = CameraProjector.projectToPixels(projection, cameraOrientation, altAz.getAltitude(),
					altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			Graticule.drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static List<ObjectDirectionRaDec> toRaDecList(List<PrecessionData> points) {
		List<ObjectDirectionRaDec> result = new ArrayList<ObjectDirectionRaDec>(points.size());
		for (PrecessionData point : points)
			result.add(point.getRaDec());
		return Collections.unmodifiableList(result);
	}

	// Ground-position-based reformulation (see the class comment for the full derivation) - sidesteps
	// AbstractPrecession's own buggy getAltAz() entirely rather than trusting it. One GeoCalculator
	// instance reused across every sample - it holds no per-call state, matching how this module
	// reuses other stateless helper instances elsewhere (e.g. Graticule's single reusable
	// CelestialObject for grid points).
	private static List<ObjectDirectionAltAz> toAltAzList(List<PrecessionData> points, ObserverLocation location) {
		GeoCalculator geoCalculator = new GeoCalculator();
		List<ObjectDirectionAltAz> result = new ArrayList<ObjectDirectionAltAz>(points.size());
		for (PrecessionData point : points) {
			GeoLocation groundPosition = point.getGroundPosition();
			ObjectDirectionRaDec syntheticRaDec = new ObjectDirectionRaDec();
			syntheticRaDec.setDeclination(groundPosition.getLatitude());
			syntheticRaDec.setRightAscension(location.getLongitude() - groundPosition.getLongitude());
			result.add(geoCalculator.raDeclinationToAltitudeAzimuth(syntheticRaDec, location));
		}
		return Collections.unmodifiableList(result);
	}
}
