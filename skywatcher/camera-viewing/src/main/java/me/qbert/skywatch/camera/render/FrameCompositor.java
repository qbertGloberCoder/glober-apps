package me.qbert.skywatch.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import me.qbert.skywatch.astro.CelestialObject;
import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.astro.impl.SunObject;
import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.watch.WatchedObject;

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

// The general-purpose Phase 4 compositor - ties LayerVisibility's draw-order/auto-disable rules
// together with SkyColor/GroundFill/CelestialObjectsLayer into one composited frame, for BOTH Real
// and Virtual cameras (unlike batch.BatchReprocessor/LiveCameraSaver, which only ever handle Real
// cameras always sitting in Layer 1). Deliberately does not decide *which* file to load or *what*
// time to render at - the caller (a batch job, a live daemon, an eventual interactive UI) already
// has to make those decisions for its own reasons, so this class takes a pre-loaded image (or none)
// and a fully-resolved time/location/orientation instead of reaching for a clock or a file path
// itself.
//
// This class composites layers 1, 2 (graticule + watched-object crosshair + watched-object path),
// 3A, 3B/3C (including the ecliptic/analemma path, painted as part of the objects sub-layer - see
// below), 4, and 5 (OSD, both the always-on summary and the expandable per-watched-object detail
// tier) - every spec §6 path/trajectory mode and every built §8 OSD field now has a wired consumer.
public final class FrameCompositor {
	private FrameCompositor() {
	}

	public static final class Options {
		private BufferedImage cameraImage;
		private ImagePlacement placement = ImagePlacement.NONE;
		private boolean groundPaintsOverObjects;
		private boolean manualHideGroundToggle;
		private boolean manualSkyToggle = true;
		private int canvasWidth;
		private int canvasHeight;
		private double minSunMoonRadiusPixels = 5.0;
		// Layer1DuskFade scope - direct user instruction, correcting an earlier round's default:
		// the dusk/night fade toward black was originally applied uniformly to Real and Virtual
		// cameras alike ("a deliberate deviation from the old code's narrower Virtual-panorama-only
		// scope" - see Layer1DuskFade's own class comment, not yet updated to match). After actually
		// seeing a real camera's own photo fade toward black overnight, the user asked for the OLD,
		// narrower scope back: Virtual cameras only - a real photo's own natural night lighting
		// (streetlights, IR illumination, etc.) shouldn't be overridden by a computed sun-altitude
		// guess. Defaults to false (off) - every call site that's always Real (RealCameraScrubber,
		// BatchReprocessor/LiveCameraSaver, the CLI) gets the new correct behavior for free, without
		// needing to touch them; only CameraImageDispatch.compositeVirtual(...) (always Virtual) and
		// PlateSolveSession.render(...) (Real OR Virtual-Fixed, conditional on which) explicitly opt
		// in.
		private boolean applyLayer1DuskFade;
		// The AWT default font is tiny at typical canvas resolutions (the user's own report: "the
		// text is scaled very small"). Applied once, to the whole canvas's Graphics2D, before any
		// layer paints - every TextRenderer-based draw (Labels, Osd, WatchedObjectCrosshair's label)
		// shares the same Graphics2D within one compose(...) call, so a single g2d.setFont(...) here
		// covers all of them without threading a font-size parameter through every draw call site.
		private int fontSizePixels = 16;
		private ColorScheme colorScheme = ColorPresets.defaultScheme();
		private Color groundColor = GroundFill.DEFAULT_GROUND_COLOR;
		private List<StarCoordinate> stars = Collections.emptyList();
		// Per-object-type visibility - a real user report ("additional layer controls: show/hide the
		// camera images, show/hide stars, show/hide planets, etc"). All default true, preserving
		// existing behavior for every caller that never touches these. showStars is independent of
		// the "stars" field above (the catalog-tier CONTENT selector) - a whole-feature on/off gate
		// over whatever list is configured there, not a replacement for it.
		private boolean showSun = true;
		private boolean showMoon = true;
		private boolean showPlanets = true;
		private boolean showStars = true;
		// Task 4.4 - independent toggle, off by default (matching CelestialObjectsLayer's own
		// no-label overloads' default behavior).
		private boolean showLabels;
		// Layer 5 / spec §8 - see setShowOsd/setOsdTimezone/setOsdTextColor below.
		private boolean showOsd;
		private ZoneId osdTimezone;
		private Color osdTextColor = Color.WHITE;
		// Layer 2's graticule mode / spec §6 - see setShowGraticule/setShowCelestialOrigin/
		// setGraticuleStepDegrees below.
		private boolean showGraticule;
		// Item 5 ("Graticule redesign") - renamed from showCelestialEquator: this toggle now draws
		// BOTH the RA=0 celestial prime meridian (new) and the Dec=0 celestial equator (the original
		// behavior) together as one "celestial origin" reference-line group - see
		// render.Graticule.paintPrimeMeridian/paintCelestialEquator and ColorScheme.
		// getCelestialOriginColor().
		private boolean showCelestialOrigin;
		private double graticuleRaStepDegrees = 30.0;
		private double graticuleDecStepDegrees = 15.0;
		// Item 5's three other reference-line groups - see render.Graticule's matching paint methods.
		// The observer cardinal cross and the watched-object/boresight reference lines are each their
		// own independent toggle, per CLAUDE.md's "4 independently-toggleable reference-line groups".
		private boolean showObserverCardinalCross;
		private WatchedObject watchedObjectReferenceLineTarget;
		private boolean showBoresightReferenceLines;
		// Layer 2's watched-object crosshair / spec §6 - see setWatchedObjectCrosshair below. null
		// watchedObject (the default) means no crosshair is drawn.
		private WatchedObject watchedObject;
		private ObservationTime watchedObjectReferenceTime;
		// The ecliptic/analemma path - painted as part of the OBJECTS sub-layer (3B/3C, not Layer 2),
		// before the live sun/moon/planet/star glyphs - see setSunPathMode/setMoonPathMode below and
		// CLAUDE.md's "Layer 2 sits above sky but below ground/objects" section for why this doesn't
		// live in Layer 2 alongside the graticule/crosshair. NONE (the default) means nothing is
		// sampled or drawn for that body.
		private EclipticAnalemmaMode sunPathMode = EclipticAnalemmaMode.NONE;
		private EclipticAnalemmaMode moonPathMode = EclipticAnalemmaMode.NONE;
		// Layer 2's watched-object path (trailing window, spec §6, default past 24h) - stays in
		// Layer 2, unlike the ecliptic/analemma path above (see CLAUDE.md's "Ecliptic/analemma path
		// moved into the objects sub-layer" section for why those two were treated differently). null
		// watchedObjectPathTarget (the default) means no path is drawn - independent of the crosshair's
		// own watchedObject field above, since a caller may want one without the other, or even
		// different objects for each, even though the ordinary case is keeping them in sync.
		private WatchedObject watchedObjectPathTarget;
		private long watchedObjectPathTrailingWindowMillis = WatchedObjectPath.DEFAULT_TRAILING_WINDOW_MILLIS;
		private long watchedObjectPathSampleIntervalMillis = WatchedObjectPath.DEFAULT_SAMPLE_INTERVAL_MILLIS;
		// Layer 5's expandable per-watched-object OSD detail tier / spec §8 - see
		// setShowWatchedObjectDetail below. null (the default) means it isn't drawn. Shares osdTimezone/
		// osdTextColor with the always-on summary tier above - both are the same Layer-5/OSD concern,
		// independently toggleable, but there's no reason for them to disagree on timezone or color.
		private WatchedObject watchedObjectDetailTarget;

		// The camera's own image, already loaded by the caller (a batch job's archived frame, a
		// live daemon's latest capture, a Virtual camera's scene file) - null for "no image"/hidden.
		public Options setCameraImage(BufferedImage cameraImage) {
			this.cameraImage = cameraImage;
			return this;
		}

		// Where the image sits (NONE/LAYER_1/LAYER_4) - the caller resolves this from CameraConfig
		// (Real cameras are always LAYER_1 when shown; Virtual cameras use
		// CameraConfig.getVirtualImagePlacement()) plus whatever show/hide state is currently in
		// effect, since that's a runtime/session toggle this class has no opinion on.
		public Options setPlacement(ImagePlacement placement) {
			if (placement == null)
				throw new IllegalArgumentException("placement must not be null");
			this.placement = placement;
			return this;
		}

		public Options setGroundPaintsOverObjects(boolean groundPaintsOverObjects) {
			this.groundPaintsOverObjects = groundPaintsOverObjects;
			return this;
		}

		public Options setManualHideGroundToggle(boolean manualHideGroundToggle) {
			this.manualHideGroundToggle = manualHideGroundToggle;
			return this;
		}

		// Only consulted when the sky is actually user-selectable (a Layer-1 image with a
		// transparent region, detected automatically from the loaded image's own alpha channel -
		// see hasAnyTransparentPixel(...)) - ignored otherwise, matching LayerVisibility's rules.
		public Options setManualSkyToggle(boolean manualSkyToggle) {
			this.manualSkyToggle = manualSkyToggle;
			return this;
		}

		// Only consulted when cameraImage is null - otherwise the canvas takes the image's own
		// dimensions, matching BatchReprocessor/LiveCameraSaver's existing behavior for Real cameras.
		public Options setCanvasSize(int canvasWidth, int canvasHeight) {
			if (canvasWidth <= 0 || canvasHeight <= 0)
				throw new IllegalArgumentException("canvas dimensions must be positive");
			this.canvasWidth = canvasWidth;
			this.canvasHeight = canvasHeight;
			return this;
		}

		public Options setMinSunMoonRadiusPixels(double minSunMoonRadiusPixels) {
			this.minSunMoonRadiusPixels = minSunMoonRadiusPixels;
			return this;
		}

		// See the applyLayer1DuskFade field's own comment - Virtual cameras only, per direct user
		// instruction.
		public Options setApplyLayer1DuskFade(boolean applyLayer1DuskFade) {
			this.applyLayer1DuskFade = applyLayer1DuskFade;
			return this;
		}

		public Options setFontSizePixels(int fontSizePixels) {
			if (fontSizePixels <= 0)
				throw new IllegalArgumentException("fontSizePixels must be positive");
			this.fontSizePixels = fontSizePixels;
			return this;
		}

		public Options setColorScheme(ColorScheme colorScheme) {
			if (colorScheme == null)
				throw new IllegalArgumentException("colorScheme must not be null");
			this.colorScheme = colorScheme;
			return this;
		}

		public Options setGroundColor(Color groundColor) {
			if (groundColor == null)
				throw new IllegalArgumentException("groundColor must not be null");
			this.groundColor = groundColor;
			return this;
		}

		public Options setStars(List<StarCoordinate> stars) {
			if (stars == null)
				throw new IllegalArgumentException("stars must not be null");
			this.stars = stars;
			return this;
		}

		public Options setShowSun(boolean showSun) {
			this.showSun = showSun;
			return this;
		}

		public Options setShowMoon(boolean showMoon) {
			this.showMoon = showMoon;
			return this;
		}

		public Options setShowPlanets(boolean showPlanets) {
			this.showPlanets = showPlanets;
			return this;
		}

		// Independent of setStars(List<StarCoordinate>) above (the catalog-tier CONTENT selector) -
		// this is a whole-feature on/off gate over whatever list is configured there, not a
		// replacement for it.
		public Options setShowStars(boolean showStars) {
			this.showStars = showStars;
			return this;
		}

		// Task 4.4 / spec §5 - printed beside each rendered sun/moon/planet/star, see render.Labels.
		public Options setShowLabels(boolean showLabels) {
			this.showLabels = showLabels;
			return this;
		}

		// Layer 5 / spec §8 - the always-on summary tier, see render.Osd. Off by default, matching
		// showLabels' additive/non-breaking default.
		public Options setShowOsd(boolean showOsd) {
			this.showOsd = showOsd;
			return this;
		}

		// Required when showOsd is true - see Osd's own class comment for why this is caller-supplied
		// rather than derived from the observer location's bare lat/lon.
		public Options setOsdTimezone(ZoneId osdTimezone) {
			this.osdTimezone = osdTimezone;
			return this;
		}

		public Options setOsdTextColor(Color osdTextColor) {
			if (osdTextColor == null)
				throw new IllegalArgumentException("osdTextColor must not be null");
			this.osdTextColor = osdTextColor;
			return this;
		}

		// Layer 2 / spec §6 - the static RA/Dec reference grid, drawn with colorScheme.getGraticuleColor().
		public Options setShowGraticule(boolean showGraticule) {
			this.showGraticule = showGraticule;
			return this;
		}

		// Independent of setShowGraticule(...) - see Graticule's own class comment and CLAUDE.md's
		// Layer model note on this being a separate toggle from the general grid. Item 5: renamed from
		// setShowCelestialEquator(...) - now draws RA=0 alongside the original Dec=0, see the
		// showCelestialOrigin field's own comment.
		public Options setShowCelestialOrigin(boolean showCelestialOrigin) {
			this.showCelestialOrigin = showCelestialOrigin;
			return this;
		}

		public Options setGraticuleStepDegrees(double raStepDegrees, double decStepDegrees) {
			if (raStepDegrees <= 0.0 || raStepDegrees >= 360.0)
				throw new IllegalArgumentException("raStepDegrees must be in (0, 360)");
			if (decStepDegrees <= 0.0 || decStepDegrees >= 180.0)
				throw new IllegalArgumentException("decStepDegrees must be in (0, 180)");
			this.graticuleRaStepDegrees = raStepDegrees;
			this.graticuleDecStepDegrees = decStepDegrees;
			return this;
		}

		// Item 5's three other reference-line groups.
		public Options setShowObserverCardinalCross(boolean showObserverCardinalCross) {
			this.showObserverCardinalCross = showObserverCardinalCross;
			return this;
		}

		// Presence-is-the-toggle, matching setWatchedObjectCrosshair(...)/setShowWatchedObjectPath(...)'s
		// own convention - independent of BOTH of those (a caller may want reference lines without a
		// crosshair/path, or a different object for each).
		public Options setShowWatchedObjectReferenceLines(WatchedObject watchedObject) {
			if (watchedObject == null)
				throw new IllegalArgumentException("watchedObject must not be null");
			this.watchedObjectReferenceLineTarget = watchedObject;
			return this;
		}

		public Options clearWatchedObjectReferenceLines() {
			this.watchedObjectReferenceLineTarget = null;
			return this;
		}

		public Options setShowBoresightReferenceLines(boolean showBoresightReferenceLines) {
			this.showBoresightReferenceLines = showBoresightReferenceLines;
			return this;
		}

		// Layer 2 / spec §6, ../CLAUDE.md's "Watched-object rendering" - a crosshair marking
		// watchedObject's resolved position at referenceTime, deliberately separate from the live
		// glyph OBJECTS paints for the frame's own render time. Both arguments are required together
		// (there is no meaningful "which object, but no time" state) - call this only when a crosshair
		// should actually be drawn; the default (never called) means none is drawn, matching stars'
		// empty-list-means-none-drawn convention rather than a separate boolean toggle.
		public Options setWatchedObjectCrosshair(WatchedObject watchedObject, ObservationTime referenceTime) {
			if (watchedObject == null)
				throw new IllegalArgumentException("watchedObject must not be null");
			if (referenceTime == null)
				throw new IllegalArgumentException("referenceTime must not be null");
			this.watchedObject = watchedObject;
			this.watchedObjectReferenceTime = referenceTime;
			return this;
		}

		// Turns the crosshair back off - the CLI never needed this (a flag is either passed once, per
		// invocation, or not), but a live UI toggle (the control panel's Watched Object tab) does.
		// Additive, backward-compatible with every existing caller - no other method's contract
		// changes.
		public Options clearWatchedObjectCrosshair() {
			this.watchedObject = null;
			this.watchedObjectReferenceTime = null;
			return this;
		}

		// Spec §6's ecliptic/analemma modes for the sun - sampled ending at the frame's own render
		// time (compose(...)'s `time` parameter), not a separate reference time: unlike the
		// watched-object crosshair, there's no request for these paths to show anything other than
		// "the year/month leading up to right now."
		public Options setSunPathMode(EclipticAnalemmaMode sunPathMode) {
			if (sunPathMode == null)
				throw new IllegalArgumentException("sunPathMode must not be null");
			this.sunPathMode = sunPathMode;
			return this;
		}

		public Options setMoonPathMode(EclipticAnalemmaMode moonPathMode) {
			if (moonPathMode == null)
				throw new IllegalArgumentException("moonPathMode must not be null");
			this.moonPathMode = moonPathMode;
			return this;
		}

		// Layer 2 / spec §6's "watched-object path" mode - a trailing trajectory ending at the
		// frame's own render time, using the default window/interval (render.WatchedObjectPath's own
		// constants) unless overridden via setWatchedObjectPathWindow(...). Calling this is what turns
		// the path on, matching setWatchedObjectCrosshair(...)'s own "no default toggle, presence is
		// the toggle" convention.
		public Options setShowWatchedObjectPath(WatchedObject watchedObject) {
			if (watchedObject == null)
				throw new IllegalArgumentException("watchedObject must not be null");
			this.watchedObjectPathTarget = watchedObject;
			return this;
		}

		// See clearWatchedObjectCrosshair(...)'s own comment - same live-UI-toggle reasoning.
		public Options clearWatchedObjectPath() {
			this.watchedObjectPathTarget = null;
			return this;
		}

		public Options setWatchedObjectPathWindow(long trailingWindowMillis, long sampleIntervalMillis) {
			if (trailingWindowMillis <= 0)
				throw new IllegalArgumentException("trailingWindowMillis must be positive");
			if (sampleIntervalMillis <= 0)
				throw new IllegalArgumentException("sampleIntervalMillis must be positive");
			this.watchedObjectPathTrailingWindowMillis = trailingWindowMillis;
			this.watchedObjectPathSampleIntervalMillis = sampleIntervalMillis;
			return this;
		}

		// Layer 5 / spec §8's expandable per-watched-object detail tier, see render.Osd's class
		// comment for exactly which fields this covers. Requires setOsdTimezone(...) to also be
		// called (validated in compose(...)), same requirement as the always-on summary tier -
		// independently toggleable from it via setShowOsd(...), not bundled together.
		public Options setShowWatchedObjectDetail(WatchedObject watchedObject) {
			if (watchedObject == null)
				throw new IllegalArgumentException("watchedObject must not be null");
			this.watchedObjectDetailTarget = watchedObject;
			return this;
		}

		// See clearWatchedObjectCrosshair(...)'s own comment - same live-UI-toggle reasoning.
		public Options clearWatchedObjectDetail() {
			this.watchedObjectDetailTarget = null;
			return this;
		}
	}

	public static BufferedImage compose(CameraProjection projection, Orientation cameraOrientation,
			ObservationTime time, ObserverLocation location, Options options) throws Exception {
		return compose(projection, cameraOrientation, time, location, options, null);
	}

	// astronomy (Item 0's shared-instance architecture): when non-null, the CelestialObjectsLayer/
	// Graticule calls below read its already-built, already-recomputed Sun/Moon/Planets/active-star-
	// bucket instead of constructing fresh ones on every single compose() call - the caller is
	// responsible for astronomy's ObservationTime/ObserverLocation already being time/location
	// (mutated via astronomy.applyTimeAndLocation(...) before calling this), so there's nothing to
	// reconcile between the two here. null (the default, via the overload above) preserves this
	// class's original always-fresh-construction behavior exactly - every existing caller/test is
	// unaffected. The watched-object crosshair/path, ecliptic/analemma path, and OSD are
	// deliberately NOT migrated to astronomy - they resolve positions at MANY different timestamps
	// per call (not "now"), which doesn't fit a single shared instance representing one moment (see
	// CLAUDE.md's Item 0 entry) - they keep using time/location directly, unaffected either way.
	public static BufferedImage compose(CameraProjection projection, Orientation cameraOrientation,
			ObservationTime time, ObserverLocation location, Options options, CameraAstronomy astronomy)
			throws Exception {
		if (projection == null)
			throw new IllegalArgumentException("projection must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		if (options.placement != ImagePlacement.NONE && options.cameraImage == null)
			throw new IllegalStateException("placement is " + options.placement + " but no cameraImage was set");
		if (options.showOsd && options.osdTimezone == null)
			throw new IllegalStateException("showOsd is true but no osdTimezone was set");
		if (options.watchedObjectDetailTarget != null && options.osdTimezone == null)
			throw new IllegalStateException("watched-object detail is enabled but no osdTimezone was set");

		int width;
		int height;
		if (options.cameraImage != null) {
			width = options.cameraImage.getWidth();
			height = options.cameraImage.getHeight();
		} else {
			if (options.canvasWidth <= 0 || options.canvasHeight <= 0)
				throw new IllegalStateException("no cameraImage set - call setCanvasSize(...) first");
			width = options.canvasWidth;
			height = options.canvasHeight;
		}

		BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

		boolean hasTransparentSkyRegion = options.placement == ImagePlacement.LAYER_1
				&& options.cameraImage != null && hasAnyTransparentPixel(options.cameraImage);
		boolean renderSky = LayerVisibility.shouldRenderSky(options.placement, hasTransparentSkyRegion, options.manualSkyToggle);
		boolean renderGround = LayerVisibility.shouldRenderGround(options.placement, options.manualHideGroundToggle);
		// Item 5's horizon reference line auto-shows specifically for the "hide the ground / as seen
		// from space" scenario (ground absent because of the MANUAL toggle) - not the separate,
		// much-more-common case of ground being force-disabled because Layer 1 already holds an
		// opaque image (a real photo, or a PTZ Virtual camera's default equirectangular placement) -
		// see LayerVisibility.isGroundForcedOff(...). Conflating the two (a bug caught by two existing
		// PTZ-panorama pixel-position tests failing once this was wired in) would auto-draw the
		// horizon line over ordinary Layer-1 image renders too, which was never the intent.
		boolean showHorizonReferenceLine = !renderGround && !LayerVisibility.isGroundForcedOff(options.placement);

		List<RenderLayer> order = LayerVisibility.compositeOrder(options.placement, renderSky, renderGround,
				options.groundPaintsOverObjects);

		double sunAltitudeDegrees;
		if (astronomy != null) {
			sunAltitudeDegrees = astronomy.getSun().getCurrentDirectionAsAltitudeAzimuth().getAltitude();
		} else {
			CelestialObject sun = SunObject.create().setObserverLocation(location).setObserverTime(time).build();
			sun.recompute();
			sunAltitudeDegrees = sun.getCurrentDirectionAsAltitudeAzimuth().getAltitude();
		}

		// The user's own real/archived (or Virtual scene) photo "bounds" a star for identification
		// [CLAUDE.md's "stars are always represented as circles" feedback] - true whenever the image
		// is actually visible, regardless of which layer slot it occupies.
		boolean cameraImageShown = options.cameraImage != null && options.placement != ImagePlacement.NONE;

		Graphics2D g2d = canvas.createGraphics();
		try {
			g2d.setFont(g2d.getFont().deriveFont((float) options.fontSizePixels));
			for (RenderLayer layer : order)
				paintLayer(layer, g2d, canvas, projection, cameraOrientation, time, location, options, sunAltitudeDegrees,
						cameraImageShown, astronomy, showHorizonReferenceLine);
		} finally {
			g2d.dispose();
		}

		return canvas;
	}

	private static void paintLayer(RenderLayer layer, Graphics2D g2d, BufferedImage canvas, CameraProjection projection,
			Orientation cameraOrientation, ObservationTime time, ObserverLocation location, Options options,
			double sunAltitudeDegrees, boolean cameraImageShown, CameraAstronomy astronomy,
			boolean showHorizonReferenceLine) throws Exception {
		int width = canvas.getWidth();
		int height = canvas.getHeight();

		switch (layer) {
			case IMAGE_BACKGROUND:
			case IMAGE_FOREGROUND:
				// Dusk/night fade (direct user request), Virtual cameras only
				// (options.applyLayer1DuskFade - see that field's own comment). A real bug, caught by
				// the user directly: darkening the SHARED canvas in place (the original approach) was
				// only ever safe for IMAGE_BACKGROUND (Layer 1) - it paints FIRST, before anything else
				// is on the canvas, so Layer1DuskFade.darken(canvas,...)'s own "skip alpha==0" check
				// only ever touched this image's own just-drawn pixels. IMAGE_FOREGROUND (Layer 4)
				// paints LAST, after sky/graticule/ground/objects are already opaque on the canvas - the
				// same canvas-wide darken() call there darkened THOSE already-painted layers too,
				// wherever the foreground image itself was transparent, crushing the entire frame to
				// black at night instead of just the foreground image (a "unicorn on a see-through
				// background" image should fade while the moon/stars visible through its transparent
				// regions stay untouched - the user's own explicit requirement). Fixed by isolating the
				// image (and its fade) to an off-canvas buffer that starts fully transparent and holds
				// ONLY this camera's own just-drawn pixels - darken() there respects ONLY the image's
				// own alpha footprint, never whatever the shared canvas already contains - then
				// compositing the (possibly darkened) result onto the real canvas via ordinary alpha
				// blending, so a transparent source pixel leaves whatever is underneath completely
				// alone. Identical output to the old approach for Layer 1 (nothing was ever underneath
				// it anyway); corrects Layer 4.
				BufferedImage imageLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				Graphics2D imageLayerG2d = imageLayer.createGraphics();
				imageLayerG2d.drawImage(options.cameraImage, 0, 0, null);
				imageLayerG2d.dispose();
				if (options.applyLayer1DuskFade)
					Layer1DuskFade.darken(imageLayer, sunAltitudeDegrees);
				g2d.drawImage(imageLayer, 0, 0, null);
				break;
			case GRATICULE_AND_PATHS:
				if (options.showGraticule) {
					if (astronomy != null)
						Graticule.paintGrid(g2d, astronomy, projection, cameraOrientation,
								options.colorScheme.getGraticuleColor(), options.graticuleRaStepDegrees,
								options.graticuleDecStepDegrees, width, height);
					else
						Graticule.paintGrid(g2d, time, location, projection, cameraOrientation,
								options.colorScheme.getGraticuleColor(), options.graticuleRaStepDegrees,
								options.graticuleDecStepDegrees, width, height);
				}
				if (options.showCelestialOrigin) {
					if (astronomy != null) {
						Graticule.paintPrimeMeridian(g2d, astronomy, projection, cameraOrientation,
								options.colorScheme.getCelestialOriginColor(), width, height);
						Graticule.paintCelestialEquator(g2d, astronomy, projection, cameraOrientation,
								options.colorScheme.getCelestialOriginColor(), width, height);
					} else {
						Graticule.paintPrimeMeridian(g2d, time, location, projection, cameraOrientation,
								options.colorScheme.getCelestialOriginColor(), width, height);
						Graticule.paintCelestialEquator(g2d, time, location, projection, cameraOrientation,
								options.colorScheme.getCelestialOriginColor(), width, height);
					}
				}
				if (options.showObserverCardinalCross)
					Graticule.paintObserverCardinalCross(g2d, projection, cameraOrientation,
							options.colorScheme.getObserverCardinalCrossColor(), width, height);
				if (options.watchedObjectReferenceLineTarget != null) {
					if (astronomy != null)
						Graticule.paintWatchedObjectReferenceLines(g2d, options.watchedObjectReferenceLineTarget, astronomy,
								projection, cameraOrientation, options.colorScheme.getWatchedObjectReferenceLineColor(), width,
								height);
					else
						Graticule.paintWatchedObjectReferenceLines(g2d, options.watchedObjectReferenceLineTarget, time,
								location, projection, cameraOrientation, options.colorScheme.getWatchedObjectReferenceLineColor(),
								width, height);
				}
				if (options.showBoresightReferenceLines)
					Graticule.paintBoresightReferenceLines(g2d, location, projection, cameraOrientation,
							options.colorScheme.getBoresightReferenceColor(), width, height);
				// Not an independent toggle - auto-shown specifically for the manual "hide ground / as seen
				// from space" scenario (see showHorizonReferenceLine's own comment above for why that's
				// narrower than "ground isn't rendering this frame"), reusing the ground-fill's own color
				// rather than a dedicated ColorScheme field - see CLAUDE.md's "Horizon reference line" note.
				if (showHorizonReferenceLine)
					Graticule.paintHorizon(g2d, projection, cameraOrientation, options.groundColor, width, height);
				if (options.watchedObject != null)
					WatchedObjectCrosshair.paint(g2d, options.watchedObject, options.watchedObjectReferenceTime, location,
							projection, cameraOrientation, options.colorScheme.getWatchedObjectMarkerColor(), width, height,
							options.showLabels);
				if (options.watchedObjectPathTarget != null)
					WatchedObjectPath.paint(g2d, options.watchedObjectPathTarget, time, location, projection,
							cameraOrientation, options.colorScheme.getWatchedObjectPathColor(), width, height,
							options.watchedObjectPathTrailingWindowMillis, options.watchedObjectPathSampleIntervalMillis);
				break;
			case SKY:
				paintSky(canvas, SkyColor.forSunAltitude(sunAltitudeDegrees));
				break;
			case GROUND:
				GroundFill.paint(canvas, projection, cameraOrientation, options.groundColor);
				break;
			case OBJECTS:
				// Ecliptic/analemma paths paint FIRST, so the live glyphs below composite on top of
				// them (the user's own instruction: "put those as the first renders. The rest will sit
				// on top of those.") - moved here from Layer 2 this round, see CLAUDE.md's "Layer 2
				// sits above sky but below ground/objects" section for why.
				if (options.sunPathMode != EclipticAnalemmaMode.NONE) {
					if (astronomy != null)
						EclipticAnalemmaPath.paintSun(g2d, astronomy, options.sunPathMode == EclipticAnalemmaMode.ANALEMMA,
								projection, cameraOrientation, options.colorScheme.getSunColor(), width, height);
					else
						EclipticAnalemmaPath.paintSun(g2d, location, time, options.sunPathMode == EclipticAnalemmaMode.ANALEMMA,
								projection, cameraOrientation, options.colorScheme.getSunColor(), width, height);
				}
				if (options.moonPathMode != EclipticAnalemmaMode.NONE) {
					if (astronomy != null)
						EclipticAnalemmaPath.paintMoon(g2d, astronomy, options.moonPathMode == EclipticAnalemmaMode.ANALEMMA,
								projection, cameraOrientation, options.colorScheme.getMoonColor(), width, height);
					else
						EclipticAnalemmaPath.paintMoon(g2d, location, time, options.moonPathMode == EclipticAnalemmaMode.ANALEMMA,
								projection, cameraOrientation, options.colorScheme.getMoonColor(), width, height);
				}
				if (astronomy != null) {
					if (options.showSun)
						CelestialObjectsLayer.paintSun(g2d, astronomy, projection, cameraOrientation, options.colorScheme,
								width, height, options.minSunMoonRadiusPixels, options.showLabels);
					if (options.showMoon)
						CelestialObjectsLayer.paintMoon(g2d, astronomy, projection, cameraOrientation, options.colorScheme,
								width, height, options.minSunMoonRadiusPixels, options.showLabels);
					if (options.showPlanets)
						CelestialObjectsLayer.paintPlanets(g2d, astronomy, projection, cameraOrientation, options.colorScheme,
								width, height, options.showLabels);
					if (options.showStars)
						CelestialObjectsLayer.paintStars(g2d, astronomy, projection, cameraOrientation, width, height,
								options.showLabels, cameraImageShown);
				} else {
					if (options.showSun)
						CelestialObjectsLayer.paintSun(g2d, time, location, projection, cameraOrientation, options.colorScheme,
								width, height, options.minSunMoonRadiusPixels, options.showLabels);
					if (options.showMoon)
						CelestialObjectsLayer.paintMoon(g2d, time, location, projection, cameraOrientation, options.colorScheme,
								width, height, options.minSunMoonRadiusPixels, options.showLabels);
					if (options.showPlanets)
						CelestialObjectsLayer.paintPlanets(g2d, time, location, projection, cameraOrientation, options.colorScheme,
								width, height, options.showLabels);
					if (options.showStars)
						CelestialObjectsLayer.paintStars(g2d, options.stars, time, location, projection, cameraOrientation, width,
								height, options.showLabels, cameraImageShown);
				}
				break;
			case OSD:
				if (options.showOsd)
					Osd.draw(g2d, time.getTime().getTimeInMillis(), options.osdTimezone, location.getLatitude(),
							location.getLongitude(), cameraOrientation, options.osdTextColor, width, height);
				if (options.watchedObjectDetailTarget != null)
					Osd.drawWatchedObjectDetail(g2d, options.watchedObjectDetailTarget, time, location, options.osdTimezone,
							options.osdTextColor, width, height, Osd.DEFAULT_DETAIL_TIER_TOP_Y_PIXELS);
				break;
			default:
				throw new IllegalStateException("unhandled layer: " + layer);
		}
	}

	// Fills only pixels that are not already fully opaque, rather than blindly overwriting the whole
	// canvas - a real user report found the old unconditional g2d.fillRect(...) here silently painted
	// over a Virtual camera's own Layer-1 image the instant it had even a few genuinely-transparent
	// pixels (a real uploaded scene PNG's own anti-aliased edge, or - unavoidably - a rendered
	// equirectangular panorama's own out-of-lens transparent margin), completely hiding it instead of
	// only filling the intended gap. When no image occupies Layer 1 at all, this canvas starts fully
	// transparent (a fresh TYPE_INT_ARGB image), so every pixel gets painted anyway - identical
	// behavior to the old unconditional fill for that case, the common one. Mirrors GroundFill.
	// paint(...)'s own "leave the rest of the canvas untouched" per-pixel-loop shape.
	private static void paintSky(BufferedImage canvas, Color skyColor) {
		int width = canvas.getWidth();
		int height = canvas.getHeight();
		int opaqueRgb = 0xFF000000 | (skyColor.getRGB() & 0x00FFFFFF);

		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				boolean fullyOpaque = (canvas.getRGB(x, y) >>> 24) == 0xFF;
				if (!fullyOpaque)
					canvas.setRGB(x, y, opaqueRgb);
			}
		}
	}

	// Whether a Virtual camera's Layer-1 image has any actually-transparent pixel - the "the user
	// prepared it that way deliberately" exception from LayerVisibility/CLAUDE.md is detected
	// directly from the image data rather than needing a separate caller-supplied flag.
	private static boolean hasAnyTransparentPixel(BufferedImage image) {
		if (!image.getColorModel().hasAlpha())
			return false;

		for (int y = 0; y < image.getHeight(); y++)
			for (int x = 0; x < image.getWidth(); x++)
				if ((image.getRGB(x, y) >>> 24) != 0xFF)
					return true;

		return false;
	}
}
