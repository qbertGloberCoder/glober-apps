package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Point2D;
import java.util.List;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.AbstractCelestialObject;
import me.qbert.skywatch.astro.impl.MoonObject;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.astro.impl.StarObject;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.astro.CameraAstronomy.ManagedStar;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.model.CelestialAddress;
import me.qbert.skywatch.model.ObjectDirectionAltAz;
import me.qbert.ui.renderers.AbstractFractionRenderer;
import me.qbert.ui.renderers.ArcRenderer;

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

// The objects sub-layer (3B or 3C, whichever slot LayerVisibility assigns it) - spec §5's sun/
// moon/planet/star glyphs. Iterates real astronomical objects for the current time/location,
// projects each through the SAME CameraProjection + camera Orientation as everything else
// (CameraProjector - the user's own instruction for GroundFill applies equally here: no separate,
// independently-computed projection), and draws sun/moon as filled discs [spec §5] and planets/
// stars as unfilled bounding circles [spec §5 - see BoundingCircleRenderingTest for why]. Planets
// come from sw-base's astro.impl.SolarObjects - corrected this round; an earlier draft of this
// comment wrongly claimed sw-base had no planet position source at all. SolarObjects.OBJECT_LIST
// index 0 is the Sun too (computed "as an inverse function of the earth's position", per the user's
// own description) but this class never uses that - the dedicated SunObject is used for the sun,
// matching CLAUDE.md's porting-notes table listing SunObject/MoonObject/StarObject/SolarObjects as
// siblings rather than SolarObjects covering everything. See earthclock's
// service.AbstractCelestialObjects (~line 1246) for the reference setObjectIndex(i+1) iteration
// pattern this ports.
//
// IMPORTANT gotcha, discovered while wiring this up (not documented anywhere in sw-base itself):
// AbstractCelestialObjectBuilder.build() does NOT call recompute() - it only registers the new
// instance as an ObservationTime/ObserverLocation listener, so recompute() only fires on a
// *subsequent* time/location change after build(). A freshly-built object queried immediately
// returns a degenerate default position (RA=Dec=0, which resolves to altitude = 90-latitude,
// azimuth = 180 - a suspiciously round, plausible-looking but wrong value that's easy to miss).
// Every builder use in this class calls recompute() explicitly, immediately after build(), to avoid
// this - do not drop that call.
//
// A real user report: an object (confirmed across a star, a planet, and the Moon) appeared on
// screen and visibly moved in a direction inconsistent with every other rendered object at the same
// moment - traced to a genuine two-step rejection gap the user's own diagnosis named precisely.
// CameraProjector.projectToPixels(...) only rejects (returns null) a target whose THETA exceeds the
// lens's max representable angle (89 degrees for a rectilinear lens) - a real, but much looser,
// bound than "does this point actually land inside the frame." A target well within that 89-degree
// cap can still resolve to a screen position thousands of pixels outside the actual canvas (e.g.
// theta=72 degrees on a 21mm lens, whose true edge-of-frame angle is closer to 40-45 degrees) -
// projectToPixels(...) is DELIBERATELY allowed to return such an off-canvas-but-representable point
// (viewfinder mode, spec §9, needs exactly that to predict where an object will enter/leave frame),
// so the fix belongs here, in the callers that draw a PERMANENT glyph+label, not in that shared
// utility. Every draw call site below now applies a second check - isOnCanvas(...) - after the
// existing null check, and skips drawing (glyph AND label together) for anything that fails either.
public final class CelestialObjectsLayer {
	private CelestialObjectsLayer() {
	}

	// Stars/planets are point sources - unlike the sun/moon's true ~0.52-degree angular diameter,
	// there is no true angular size to floor; the bounding circle is drawn at a small fixed radius
	// regardless of zoom. Planets get a slightly larger radius than stars purely as a visual
	// distinction (typically brighter/more prominent) - not a spec-mandated value.
	//
	// Stars specifically get TWO different radii depending on whether a real/archived (or Virtual
	// scene) image is currently shown [CLAUDE.md's "I also noticed that the stars are always
	// represented as circles" feedback, from the user's own prototype]: a larger UNFILLED bounding
	// circle when an image is shown, so the real star inside the photo is visually "bounded" for
	// identification; a smaller FILLED dot when no image is shown, matching a plain synthetic star
	// field where there's nothing underneath to bound.
	public static final double STAR_RADIUS_PIXELS_IMAGE_SHOWN = 4.0;
	public static final double STAR_RADIUS_PIXELS_NO_IMAGE = 1.0;
	public static final double PLANET_RADIUS_PIXELS = 2.5;

	public static void paintSun(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels, double minSunMoonRadiusPixels) throws Exception {
		paintSun(g2d, time, location, projection, cameraOrientation, colorScheme, canvasWidthPixels, canvasHeightPixels,
				minSunMoonRadiusPixels, false);
	}

	// [spec §5's labels: "independent toggle, printed beside each rendered object" - see Labels]
	public static void paintSun(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels, double minSunMoonRadiusPixels, boolean showLabel)
			throws Exception {
		CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
		sun.recompute(); // build() does not recompute - see the class comment's gotcha note
		paintFilledDisc(g2d, sun.getCurrentDirectionAsAltitudeAzimuth(), projection, cameraOrientation,
				colorScheme.getSunColor(), canvasWidthPixels, canvasHeightPixels, minSunMoonRadiusPixels,
				showLabel ? "Sun" : null, colorScheme.getLabelColor());
	}

	// CameraAstronomy-backed overloads (Item 0's shared-instance architecture) - read the already-
	// built, already-recomputed Sun/Moon/Planets/active-star-bucket instead of constructing a fresh
	// SunObject/MoonObject/SolarObjects/StarObject on every single call, which is what the plain
	// ObservationTime/ObserverLocation overloads above still do (kept unchanged, additive - every
	// existing caller/test is unaffected). Callers should prefer these once they own a CameraAstronomy;
	// the old overloads remain for call sites not yet migrated (see docs/tasks.md's Item 0 entry).
	public static void paintSun(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, ColorScheme colorScheme, int canvasWidthPixels, int canvasHeightPixels,
			double minSunMoonRadiusPixels, boolean showLabel) throws Exception {
		paintFilledDisc(g2d, astronomy.getSun().getCurrentDirectionAsAltitudeAzimuth(), projection, cameraOrientation,
				colorScheme.getSunColor(), canvasWidthPixels, canvasHeightPixels, minSunMoonRadiusPixels,
				showLabel ? "Sun" : null, colorScheme.getLabelColor());
	}

	public static void paintMoon(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels, double minSunMoonRadiusPixels) throws Exception {
		paintMoon(g2d, time, location, projection, cameraOrientation, colorScheme, canvasWidthPixels, canvasHeightPixels,
				minSunMoonRadiusPixels, false);
	}

	public static void paintMoon(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels, double minSunMoonRadiusPixels, boolean showLabel)
			throws Exception {
		CelestialObject moon = MoonObject.create().setObserverLocation(location).setObserverTime(time).build();
		moon.recompute(); // build() does not recompute - see the class comment's gotcha note
		paintFilledDisc(g2d, moon.getCurrentDirectionAsAltitudeAzimuth(), projection, cameraOrientation,
				colorScheme.getMoonColor(), canvasWidthPixels, canvasHeightPixels, minSunMoonRadiusPixels,
				showLabel ? "Moon" : null, colorScheme.getLabelColor());
	}

	public static void paintMoon(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, ColorScheme colorScheme, int canvasWidthPixels, int canvasHeightPixels,
			double minSunMoonRadiusPixels, boolean showLabel) throws Exception {
		paintFilledDisc(g2d, astronomy.getMoon().getCurrentDirectionAsAltitudeAzimuth(), projection, cameraOrientation,
				colorScheme.getMoonColor(), canvasWidthPixels, canvasHeightPixels, minSunMoonRadiusPixels,
				showLabel ? "Moon" : null, colorScheme.getLabelColor());
	}

	public static void paintPlanets(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		paintPlanets(g2d, time, location, projection, cameraOrientation, colorScheme, canvasWidthPixels, canvasHeightPixels,
				false);
	}

	// SolarObjects.OBJECT_LIST is {"Sun", "Mercury", "Venus", "Mars", "Jupiter", "Saturn ",
	// "Uranus", "Neptune", "Pluto"} - index 0 is deliberately skipped (the dedicated SunObject is
	// used for the sun instead, see the class comment) and doubles as the label text when
	// showLabels is set. One recompute() covers every body in a single pass (SolarObjects computes
	// the whole OBJECT_LIST array internally); setObjectIndex(i) only selects which already-computed
	// slot the getters read from, so it does not need its own recompute() call per planet - matching
	// earthclock's own AbstractCelestialObjects usage.
	public static void paintPlanets(Graphics2D g2d, ObservationTime time, ObserverLocation location,
			CameraProjection projection, Orientation cameraOrientation, ColorScheme colorScheme,
			int canvasWidthPixels, int canvasHeightPixels, boolean showLabels) throws Exception {
		SolarObjects solarObjects = (SolarObjects) SolarObjects.create().setObserverLocation(location).setObserverTime(time).build();
		solarObjects.recompute(); // build() does not recompute - see the class comment's gotcha note

		for (int objectIndex = 1; objectIndex < SolarObjects.OBJECT_LIST.length; objectIndex++) {
			solarObjects.setObjectIndex(objectIndex);
			ObjectDirectionAltAz altAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();

			Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
					altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
				continue;

			drawCircle(g2d, screenPosition, PLANET_RADIUS_PIXELS, colorScheme.getPlanetColor(), false,
					canvasWidthPixels, canvasHeightPixels);
			if (showLabels)
				Labels.draw(g2d, SolarObjects.OBJECT_LIST[objectIndex].trim(), screenPosition, colorScheme.getLabelColor(),
						canvasWidthPixels, canvasHeightPixels);
		}
	}

	// Reads the already-recomputed SolarObjects instance directly - no per-call build()/recompute(),
	// see this section's own leading comment.
	public static void paintPlanets(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, ColorScheme colorScheme, int canvasWidthPixels, int canvasHeightPixels,
			boolean showLabels) throws Exception {
		AbstractCelestialObject solarObjects = astronomy.getSolarObjects();

		for (int objectIndex = 1; objectIndex < SolarObjects.OBJECT_LIST.length; objectIndex++) {
			((SolarObjects) solarObjects).setObjectIndex(objectIndex);
			ObjectDirectionAltAz altAz = solarObjects.getCurrentDirectionAsAltitudeAzimuth();

			Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
					altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
				continue;

			drawCircle(g2d, screenPosition, PLANET_RADIUS_PIXELS, colorScheme.getPlanetColor(), false,
					canvasWidthPixels, canvasHeightPixels);
			if (showLabels)
				Labels.draw(g2d, SolarObjects.OBJECT_LIST[objectIndex].trim(), screenPosition, colorScheme.getLabelColor(),
						canvasWidthPixels, canvasHeightPixels);
		}
	}

	public static void paintStars(Graphics2D g2d, List<StarCoordinate> stars, ObservationTime time,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation,
			int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		paintStars(g2d, stars, time, location, projection, cameraOrientation, canvasWidthPixels, canvasHeightPixels, false,
				false);
	}

	public static void paintStars(Graphics2D g2d, List<StarCoordinate> stars, ObservationTime time,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation,
			int canvasWidthPixels, int canvasHeightPixels, boolean showLabels) throws Exception {
		paintStars(g2d, stars, time, location, projection, cameraOrientation, canvasWidthPixels, canvasHeightPixels,
				showLabels, false);
	}

	// grayscale-by-magnitude [spec §5] - brightest/dimmest are computed from whatever subset of the
	// catalog the caller passes in (e.g. catalog.StarCatalogTier.COMMON for a typical view), not a
	// hardcoded global range, matching StarBrightness's own "whatever set is currently selected"
	// design. cameraImageShown picks between the two STAR_RADIUS_PIXELS_* constants above - see
	// their own comment for why.
	public static void paintStars(Graphics2D g2d, List<StarCoordinate> stars, ObservationTime time,
			ObserverLocation location, CameraProjection projection, Orientation cameraOrientation,
			int canvasWidthPixels, int canvasHeightPixels, boolean showLabels, boolean cameraImageShown) throws Exception {
		if (stars.isEmpty())
			return;

		double brightestMagnitude = Double.POSITIVE_INFINITY;
		double dimmestMagnitude = Double.NEGATIVE_INFINITY;
		for (StarCoordinate star : stars) {
			brightestMagnitude = Math.min(brightestMagnitude, star.getApparentMagnitude());
			dimmestMagnitude = Math.max(dimmestMagnitude, star.getApparentMagnitude());
		}

		double radiusPixels = cameraImageShown ? STAR_RADIUS_PIXELS_IMAGE_SHOWN : STAR_RADIUS_PIXELS_NO_IMAGE;
		boolean fill = !cameraImageShown;

		for (StarCoordinate star : stars) {
			CelestialAddress address = new CelestialAddress();
			address.setAddress(star.getRightAscension(), star.getDeclination());

			CelestialObject starObject = StarObject.create().setStarLocation(address)
					.setObserverLocation(location).setObserverTime(time).build();
			starObject.recompute(); // build() does not recompute - see the class comment's gotcha note
			ObjectDirectionAltAz altAz = starObject.getCurrentDirectionAsAltitudeAzimuth();

			Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
					altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
				continue;

			Color color = StarBrightness.grayscaleFor(star.getApparentMagnitude(), brightestMagnitude, dimmestMagnitude);
			drawCircle(g2d, screenPosition, radiusPixels, color, fill, canvasWidthPixels, canvasHeightPixels);
			// Star labels use the star's own already-computed brightness color, matching the glyph,
			// rather than ColorScheme.getLabelColor() - a fixed label color would be
			// indistinguishable for the dimmest stars against a dark sky, defeating the same
			// purpose bounding circles have.
			if (showLabels)
				Labels.draw(g2d, star.getName(), screenPosition, color, canvasWidthPixels, canvasHeightPixels);
		}
	}

	// Reads astronomy.getActiveStars() (the currently-selected tier's already-built, already-
	// recomputed CelestialObjects, paired with each star's static StarCoordinate metadata for
	// magnitude/name) instead of constructing a fresh StarObject per star per call - the single
	// biggest object-churn cost this class had (up to ~119,000 fresh StarObject+CelestialAddress
	// instances per frame at the "all" tier, per Item 0's own investigation).
	public static void paintStars(Graphics2D g2d, CameraAstronomy astronomy, CameraProjection projection,
			Orientation cameraOrientation, int canvasWidthPixels, int canvasHeightPixels, boolean showLabels,
			boolean cameraImageShown) throws Exception {
		List<ManagedStar> stars = astronomy.getActiveStars();
		if (stars.isEmpty())
			return;

		double brightestMagnitude = Double.POSITIVE_INFINITY;
		double dimmestMagnitude = Double.NEGATIVE_INFINITY;
		for (ManagedStar star : stars) {
			brightestMagnitude = Math.min(brightestMagnitude, star.getCoordinate().getApparentMagnitude());
			dimmestMagnitude = Math.max(dimmestMagnitude, star.getCoordinate().getApparentMagnitude());
		}

		double radiusPixels = cameraImageShown ? STAR_RADIUS_PIXELS_IMAGE_SHOWN : STAR_RADIUS_PIXELS_NO_IMAGE;
		boolean fill = !cameraImageShown;

		for (ManagedStar star : stars) {
			ObjectDirectionAltAz altAz = star.getObject().getCurrentDirectionAsAltitudeAzimuth();

			Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
					altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
			if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
				continue;

			Color color = StarBrightness.grayscaleFor(star.getCoordinate().getApparentMagnitude(), brightestMagnitude,
					dimmestMagnitude);
			drawCircle(g2d, screenPosition, radiusPixels, color, fill, canvasWidthPixels, canvasHeightPixels);
			if (showLabels)
				Labels.draw(g2d, star.getCoordinate().getName(), screenPosition, color, canvasWidthPixels,
						canvasHeightPixels);
		}
	}

	private static void paintFilledDisc(Graphics2D g2d, ObjectDirectionAltAz altAz, CameraProjection projection,
			Orientation cameraOrientation, Color color, int canvasWidthPixels, int canvasHeightPixels,
			double minRadiusPixels, String label, Color labelColor) throws Exception {
		Point2D.Double screenPosition = CameraProjector.projectToPixels(projection, cameraOrientation,
				altAz.getAltitude(), altAz.getAzimuth(), canvasWidthPixels, canvasHeightPixels);
		if (screenPosition == null || !CameraProjector.isOnCanvas(screenPosition, canvasWidthPixels, canvasHeightPixels))
			return;

		// The full field of view implied by this lens across the whole 36mm reference sensor width -
		// derived from the projection rather than guessed at, so CelestialObjectSizing's existing,
		// already-tested linear pixels-per-degree formula gets a properly-derived FOV. This is a
		// locally-accurate approximation near the frame center for a rectilinear lens (true
		// tan(theta) scaling is nonlinear away from center) - negligible error for an object as
		// small as the sun/moon's ~0.52-degree disc, not worth a bespoke nonlinear sizing formula.
		double fovDegrees = 2.0
				* Math.toDegrees(projection.angleForSensorRadiusMillimeters(CameraProjection.SENSOR_WIDTH_MILLIMETERS / 2.0));
		double radiusPixels = CelestialObjectSizing.radiusPixels(CelestialObjectSizing.SUN_MOON_ANGULAR_DIAMETER_DEGREES,
				fovDegrees, canvasWidthPixels, minRadiusPixels);

		drawCircle(g2d, screenPosition, radiusPixels, color, true, canvasWidthPixels, canvasHeightPixels);
		if (label != null)
			Labels.draw(g2d, label, screenPosition, labelColor, canvasWidthPixels, canvasHeightPixels);
	}

	private static void drawCircle(Graphics2D g2d, Point2D.Double center, double radiusPixels, Color color,
			boolean fill, int canvasWidthPixels, int canvasHeightPixels) throws Exception {
		ArcRenderer circle = new ArcRenderer(AbstractFractionRenderer.ABSOLUTE_COORDINATES,
				AbstractFractionRenderer.ABSOLUTE_COORDINATES);
		circle.setFill(fill);
		circle.setStartAngle(0);
		circle.setArcAngle(360);
		circle.setX(center.x);
		circle.setY(center.y);
		circle.setWidth(radiusPixels * 2.0);
		circle.setHeight(radiusPixels * 2.0);
		circle.setRenderDimensions(0, 0, canvasWidthPixels, canvasHeightPixels);

		g2d.setColor(color);
		circle.renderComponent(g2d);
	}
}
