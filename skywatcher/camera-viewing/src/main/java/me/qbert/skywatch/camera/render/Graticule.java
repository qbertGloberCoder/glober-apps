package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.GeoCalculator;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;
import me.qbert.skywatch.model.CelestialAddress;
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

// Layer 2's "static RA/Dec reference grid, independent of any watched object" [spec §6] - the last
// of the four path/trajectory-overlay modes listed there, and the only one that isn't a time-sampled
// path at all (see EclipticAnalemmaPath's class comment for the other three's shared-primitive
// framing; this one is genuinely different in shape, hence its own class rather than a mode flag on
// that one).
//
// Reuses astro.impl.StarObject as the RA/Dec -> Alt/Az converter for arbitrary grid points, the same
// way CelestialObjectsLayer.paintStars(...) does for real catalog stars - a grid intersection is
// just a CelestialAddress with no corresponding real star, and StarObject already owns the
// GAST/hour-angle math needed to place it correctly for the current observer time/location. No
// separate RA/Dec-to-Alt/Az implementation is written here.
//
// Each meridian/parallel is sampled at SAMPLE_STEP_DEGREES and drawn as connected line segments
// rather than a single path, so that points which fall outside the lens' representable angle
// (CameraProjector.projectToPixels(...) returning null) or which jump an implausibly large distance
// on screen (the near-90-degree-theta blowup inherent to a rectilinear projection, or a segment that
// crosses behind the camera) simply break the line instead of drawing a stray edge-to-edge streak.
public final class Graticule {
	private Graticule() {
	}

	private static final double SAMPLE_STEP_DEGREES = 2.0;

	public static void paintGrid(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, Color color, double raStepDegrees,
			double decStepDegrees, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		if (raStepDegrees <= 0.0 || raStepDegrees >= 360.0)
			throw new IllegalArgumentException("raStepDegrees must be in (0, 360)");
		if (decStepDegrees <= 0.0 || decStepDegrees >= 180.0)
			throw new IllegalArgumentException("decStepDegrees must be in (0, 180)");

		for (double ra = 0.0; ra < 360.0; ra += raStepDegrees)
			paintMeridian(g2d, ra, time, location, projection, cameraOrientation, color, canvasWidthPixels,
					canvasHeightPixels);
		for (double dec = -90.0; dec <= 90.0; dec += decStepDegrees)
			paintParallel(g2d, dec, time, location, projection, cameraOrientation, color, canvasWidthPixels,
					canvasHeightPixels);
	}

	// A new, independent toggle [CLAUDE.md's Layer model: "a new independent 'show the celestial
	// equator' toggle, living in this same layer alongside the existing graticule toggle"] - always
	// exactly the Dec=0 parallel, regardless of whatever decStepDegrees the general grid (if also
	// shown) is using.
	public static void paintCelestialEquator(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintParallel(g2d, 0.0, time, location, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	// CameraAstronomy-backed overloads (Item 0's shared-instance architecture) - the grid's sample
	// points (up to ~180x180 = ~32,400 per full-density paint, confirmed the single dominant
	// previously-unflagged object-churn cost in this module) reuse ONE CelestialObject/
	// CelestialAddress pair across every sample, mutating the address and calling recompute()
	// per point, instead of constructing a brand-new StarObject+CelestialAddress for each - the
	// same "many fixed points, recompute only on time/location change" shape as
	// CelestialObjectsLayer's star buckets, just driven by a manual recompute() per sample rather
	// than the transactional listener cascade (a grid point isn't a real star with an independent
	// lifetime - it's a disposable coordinate-conversion utility, cheaper to just recompute
	// on-demand than to register/track through CameraAstronomy's own listener graph).
	public static void paintGrid(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, Color color, double raStepDegrees, double decStepDegrees,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		if (raStepDegrees <= 0.0 || raStepDegrees >= 360.0)
			throw new IllegalArgumentException("raStepDegrees must be in (0, 360)");
		if (decStepDegrees <= 0.0 || decStepDegrees >= 180.0)
			throw new IllegalArgumentException("decStepDegrees must be in (0, 180)");

		CelestialAddress address = new CelestialAddress();
		CelestialObject gridPoint = StarObject.create().setStarLocation(address)
				.setObserverLocation(astronomy.getObserverLocation()).setObserverTime(astronomy.getObservationTime())
				.build();

		for (double ra = 0.0; ra < 360.0; ra += raStepDegrees)
			paintMeridian(g2d, ra, gridPoint, address, projection, cameraOrientation, color, canvasWidthPixels,
					canvasHeightPixels);
		for (double dec = -90.0; dec <= 90.0; dec += decStepDegrees)
			paintParallel(g2d, dec, gridPoint, address, projection, cameraOrientation, color, canvasWidthPixels,
					canvasHeightPixels);
	}

	public static void paintCelestialEquator(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		CelestialAddress address = new CelestialAddress();
		CelestialObject gridPoint = StarObject.create().setStarLocation(address)
				.setObserverLocation(astronomy.getObserverLocation()).setObserverTime(astronomy.getObservationTime())
				.build();
		paintParallel(g2d, 0.0, gridPoint, address, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	// Item 5 (sprint backlog review, "Graticule redesign") - the "celestial origin" reference-line
	// group: RA=0 (this method, new) paired with Dec=0 (paintCelestialEquator, existing, recolored -
	// see FrameCompositor's showCelestialOrigin option and ColorScheme.celestialOriginColor). Mirrors
	// paintCelestialEquator's own "call the existing meridian/parallel primitive at a fixed value"
	// shape exactly.
	public static void paintPrimeMeridian(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) throws Exception {
		paintMeridian(g2d, 0.0, time, location, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	public static void paintPrimeMeridian(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		CelestialAddress address = new CelestialAddress();
		CelestialObject gridPoint = StarObject.create().setStarLocation(address)
				.setObserverLocation(astronomy.getObserverLocation()).setObserverTime(astronomy.getObservationTime())
				.build();
		paintMeridian(g2d, 0.0, gridPoint, address, projection, cameraOrientation, color, canvasWidthPixels,
				canvasHeightPixels);
	}

	// Item 5 - the "observer cardinal cross" reference-line group (green): the local meridian
	// (N-S great circle through the zenith/nadir, azimuth 0/180) and the prime vertical (E-W great
	// circle through the zenith/nadir, azimuth 90/270). Both are traced directly in the camera's own
	// alt/az frame via paintAltitudeSweep(...) below - purely a function of (projection,
	// cameraOrientation), no ObservationTime/ObserverLocation/CameraAstronomy needed at all. (A local
	// meridian is ALSO expressible as a fixed-RA line at RA=local-sidereal-time, per CLAUDE.md's own
	// design note - but since it's mathematically the exact same great circle either way, tracing it
	// directly in alt/az avoids needing sidereal time at all, and lets both halves of the cross share
	// one simple primitive.)
	public static void paintObserverCardinalCross(Graphics2D g2d, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) {
		paintAltitudeSweep(g2d, 0.0, projection, cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
		paintAltitudeSweep(g2d, 180.0, projection, cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
		paintAltitudeSweep(g2d, 90.0, projection, cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
		paintAltitudeSweep(g2d, 270.0, projection, cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
	}

	// Item 5 - the horizon reference line (altitude=0, every azimuth). Not an independently-toggled
	// source - FrameCompositor draws this automatically whenever the ground sub-layer isn't rendered
	// this frame, per CLAUDE.md's "Horizon reference line" note, reusing the ground-fill's own color
	// rather than a dedicated ColorScheme field.
	public static void paintHorizon(Graphics2D g2d, CameraProjection projection, Orientation cameraOrientation,
			Color color, int canvasWidthPixels, int canvasHeightPixels) {
		paintAzimuthSweep(g2d, 0.0, projection, cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
	}

	// Walks altitude -90..90 at a FIXED azimuth - one half of a great circle through the zenith/
	// nadir. Two calls at azimuth A and A+180 (see paintObserverCardinalCross above) together trace
	// the full circle as two separate polylines meeting visually at the shared zenith/nadir pixel -
	// simpler than one continuous piecewise parametrization, and visually identical. Projects
	// straight from (altitude, azimuth) - no RA/Dec conversion, no time/location dependency.
	private static void paintAltitudeSweep(Graphics2D g2d, double azimuthDegrees, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) {
		Point2D.Double previous = null;
		for (double altitude = -90.0; altitude <= 90.0; altitude += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = CameraProjector.projectToPixels(projection, cameraOrientation, altitude,
					azimuthDegrees, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	// Walks azimuth 0..360 at a FIXED altitude - a full circle at that altitude (e.g. the horizon,
	// altitude=0). Same "project straight from alt/az" shape as paintAltitudeSweep above.
	private static void paintAzimuthSweep(Graphics2D g2d, double altitudeDegrees, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels) {
		Point2D.Double previous = null;
		for (double azimuth = 0.0; azimuth <= 360.0; azimuth += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = CameraProjector.projectToPixels(projection, cameraOrientation, altitudeDegrees,
					azimuth, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	// Item 5 - the "watched object" reference-line group (yellow): meridian+parallel through the
	// watched object's own TRUE celestial-sphere RA/Dec - deliberately
	// resolveCelestialSphereLocation(...), NOT resolveRaDec(...)'s hour-angle-adjusted "local RA"
	// (see watch.WatchedObject's own comment on that distinction) - the latter would draw the lines
	// through the wrong point, since paintMeridian/paintParallel below build a fresh StarObject that
	// itself re-derives hour angle from a TRUE RA input.
	public static void paintWatchedObjectReferenceLines(Graphics2D g2d, WatchedObject watchedObject,
			ObservationTime time, ObserverLocation location, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels)
			throws Exception {
		ObjectDirectionRaDec raDec = watchedObject.resolveCelestialSphereLocation(time, location);
		paintMeridian(g2d, raDec.getRightAscension(), time, location, projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
		paintParallel(g2d, raDec.getDeclination(), time, location, projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
	}

	public static void paintWatchedObjectReferenceLines(Graphics2D g2d, WatchedObject watchedObject,
			CameraAstronomy astronomy, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		ObjectDirectionRaDec raDec = watchedObject.resolveCelestialSphereLocation(astronomy.getObservationTime(),
				astronomy.getObserverLocation());
		CelestialAddress address = new CelestialAddress();
		CelestialObject gridPoint = StarObject.create().setStarLocation(address)
				.setObserverLocation(astronomy.getObserverLocation()).setObserverTime(astronomy.getObservationTime())
				.build();
		paintMeridian(g2d, raDec.getRightAscension(), gridPoint, address, projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
		paintParallel(g2d, raDec.getDeclination(), gridPoint, address, projection, cameraOrientation, color,
				canvasWidthPixels, canvasHeightPixels);
	}

	// Item 5 - the "camera boresight" reference-line group (cyan): meridian+parallel through
	// wherever the camera is CURRENTLY pointing (cameraOrientation's own altitude/azimuth - theta=0/
	// phi=0 by BoresightAngles' own convention IS the orientation itself, no decomposition needed).
	//
	// Deliberately does NOT convert to true celestial RA/Dec (which would need local sidereal time -
	// see the confirmed risk flagged in CLAUDE.md's Graticule-redesign design note). Instead: convert
	// the boresight's alt/az to (hourAngle, declination) via GeoCalculator.altAzToRaDec(...) - despite
	// its name, this family of GeoCalculator methods works in an HOUR-ANGLE frame, not true sidereal
	// RA (confirmed by reading astro.impl.StarObject.recompute(): it computes
	// `hourAngle = gast*15 - trueRA + longitude` and feeds hourAngle, not trueRA, into the inherited
	// raDeclinationToAltitudeAzimuth(...) formula - true RA never reaches GeoCalculator directly
	// anywhere in this codebase). Declination is unaffected by this distinction (it's the same value
	// either way - hour angle rotation doesn't change declination).
	//
	// This still draws the mathematically CORRECT meridian/parallel through the boresight: at a FIXED
	// render moment, hour angle and true RA are related by a constant offset (H = GAST*15 + longitude
	// - RA), so sweeping declination at a fixed hour angle traces the EXACT SAME great circle as
	// sweeping declination at the corresponding fixed true RA - and sweeping the full 0-360 range
	// (the parallel) traces the same closed loop regardless of which constant offset is applied. No
	// GAST/true-RA conversion is needed to get the right on-screen curve.
	public static void paintBoresightReferenceLines(Graphics2D g2d, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, Color color, int canvasWidthPixels,
			int canvasHeightPixels) {
		ObjectDirectionAltAz boresightAltAz = new ObjectDirectionAltAz();
		boresightAltAz.setAltitude(cameraOrientation.getAltitude());
		boresightAltAz.setAzimuth(cameraOrientation.getAzimuth());

		GeoCalculator geoCalculator = new GeoCalculator();
		ObjectDirectionRaDec hourAngleDec = geoCalculator.altAzToRaDec(boresightAltAz, location);

		paintHourAngleMeridian(g2d, hourAngleDec.getRightAscension(), location, geoCalculator, projection,
				cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
		paintHourAngleParallel(g2d, hourAngleDec.getDeclination(), location, geoCalculator, projection,
				cameraOrientation, color, canvasWidthPixels, canvasHeightPixels);
	}

	private static void paintHourAngleMeridian(Graphics2D g2d, double hourAngleDegrees, ObserverLocation location,
			GeoCalculator geoCalculator, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) {
		Point2D.Double previous = null;
		for (double dec = -90.0; dec <= 90.0; dec += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectHourAngleDec(hourAngleDegrees, dec, location, geoCalculator, projection,
					cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static void paintHourAngleParallel(Graphics2D g2d, double declinationDegrees, ObserverLocation location,
			GeoCalculator geoCalculator, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) {
		Point2D.Double previous = null;
		for (double hourAngle = 0.0; hourAngle <= 360.0; hourAngle += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectHourAngleDec(hourAngle, declinationDegrees, location, geoCalculator,
					projection, cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static Point2D.Double projectHourAngleDec(double hourAngleDegrees, double declinationDegrees,
			ObserverLocation location, GeoCalculator geoCalculator, CameraProjection projection,
			Orientation cameraOrientation, int canvasWidthPixels, int canvasHeightPixels) {
		ObjectDirectionRaDec hourAngleDec = new ObjectDirectionRaDec();
		hourAngleDec.setRightAscension(hourAngleDegrees);
		hourAngleDec.setDeclination(declinationDegrees);
		ObjectDirectionAltAz altAz = geoCalculator.raDeclinationToAltitudeAzimuth(hourAngleDec, location);
		return CameraProjector.projectToPixels(projection, cameraOrientation, altAz.getAltitude(), altAz.getAzimuth(),
				canvasWidthPixels, canvasHeightPixels);
	}

	private static void paintMeridian(Graphics2D g2d, double rightAscensionDegrees, CelestialObject gridPoint,
			CelestialAddress address, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		Point2D.Double previous = null;
		for (double dec = -90.0; dec <= 90.0; dec += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectRaDec(rightAscensionDegrees, dec, gridPoint, address, projection,
					cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static void paintParallel(Graphics2D g2d, double declinationDegrees, CelestialObject gridPoint,
			CelestialAddress address, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		Point2D.Double previous = null;
		for (double ra = 0.0; ra <= 360.0; ra += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectRaDec(ra, declinationDegrees, gridPoint, address, projection,
					cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static Point2D.Double projectRaDec(double rightAscensionDegrees, double declinationDegrees,
			CelestialObject gridPoint, CelestialAddress address, CameraProjection projection,
			Orientation cameraOrientation, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		address.setAddress(rightAscensionDegrees, declinationDegrees);
		gridPoint.recompute();
		ObjectDirectionAltAz altAz = gridPoint.getCurrentDirectionAsAltitudeAzimuth();

		return CameraProjector.projectToPixels(projection, cameraOrientation, altAz.getAltitude(), altAz.getAzimuth(),
				canvasWidthPixels, canvasHeightPixels);
	}

	private static void paintMeridian(Graphics2D g2d, double rightAscensionDegrees, ObservationTime time,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		Point2D.Double previous = null;
		for (double dec = -90.0; dec <= 90.0; dec += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectRaDec(rightAscensionDegrees, dec, time, location, projection,
					cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static void paintParallel(Graphics2D g2d, double declinationDegrees, ObservationTime time,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation, Color color,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		Point2D.Double previous = null;
		for (double ra = 0.0; ra <= 360.0; ra += SAMPLE_STEP_DEGREES) {
			Point2D.Double current = projectRaDec(ra, declinationDegrees, time, location, projection,
					cameraOrientation, canvasWidthPixels, canvasHeightPixels);
			drawSegmentIfPlausible(g2d, previous, current, color, canvasWidthPixels, canvasHeightPixels);
			previous = current;
		}
	}

	private static Point2D.Double projectRaDec(double rightAscensionDegrees, double declinationDegrees,
			ObservationTime time, ObserverLocation location, CameraProjection projection, Orientation cameraOrientation,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		CelestialAddress address = new CelestialAddress();
		address.setAddress(rightAscensionDegrees, declinationDegrees);

		CelestialObject gridPoint = StarObject.create().setStarLocation(address).setObserverLocation(location)
				.setObserverTime(time).build();
		gridPoint.recompute(); // build() does not recompute - see CelestialObjectsLayer's class comment gotcha
		ObjectDirectionAltAz altAz = gridPoint.getCurrentDirectionAsAltitudeAzimuth();

		return CameraProjector.projectToPixels(projection, cameraOrientation, altAz.getAltitude(), altAz.getAzimuth(),
				canvasWidthPixels, canvasHeightPixels);
	}

	// Package-visible (not private) - reused as-is by EclipticAnalemmaPath's paintSun/paintMoon,
	// which need the exact same "connect two projected points, break on an implausible jump" rule
	// for their own sampled paths rather than a second, driftable copy of this logic.
	static void drawSegmentIfPlausible(Graphics2D g2d, Point2D.Double a, Point2D.Double b, Color color,
			int canvasWidthPixels, int canvasHeightPixels) {
		if (a == null || b == null)
			return;

		// A rectilinear projection's sensor radius grows without bound as theta approaches 90
		// degrees, so two adjacent samples straddling that edge can land implausibly far apart on
		// screen even though projectToPixels(...) returned a non-null point for both - guard against
		// drawing that stray streak the same way an off-canvas-but-still-representable point (e.g.
		// viewfinder mode, spec §9) is deliberately still allowed to draw.
		double maxPlausibleJumpPixels = 3.0 * Math.max(canvasWidthPixels, canvasHeightPixels);
		if (a.distance(b) > maxPlausibleJumpPixels)
			return;

		g2d.setColor(color);
		g2d.draw(new Line2D.Double(a, b));
	}
}
