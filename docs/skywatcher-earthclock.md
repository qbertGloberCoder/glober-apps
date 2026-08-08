# skywatcher/earthclock Module Catalog

The `skywatcher/earthclock` module is the flagship Skywatcher application: a
Swing desktop app that renders an animated "Earth clock" — a real-time map of
solar/lunar/planetary/stellar positions and day/night terminator lines,
drawn onto one of several selectable map projections (Azimuthal Equidistant
North/South Pole, Mercator, Equirectangular, and an orthographic-style
"Globe" projection, including stereoscopic side-by-side variants), with an
overlaid analog/digital clock face and a solar-system calendar panel.

Its Maven artifact is `me.qbert.skywatch:sw-earth-clock` (version 1.2.0,
packaging `jar`), a child of the `me.qbert.skywatch:skywatcher` parent POM.
The module's package root is `me.qbert.skywatch`. Per its `pom.xml`, it is
the largest and most dependency-heavy Skywatcher submodule:

- Sibling modules: `me.qbert.skywatch:ga-base` 1.3.0 (the shared UI/rendering
  framework, package root `me.qbert.ui.*`) and `me.qbert.skywatch:sw-base`
  1.3.0 (the astronomy calculation engine, package root
  `me.qbert.skywatch.astro.*`).
- Third-party/JDK libraries declared: Gson 2.8.5, AppleJavaExtensions 1.4,
  opencsv 5.3, a TwelveMonkeys-derived TIFF ImageIO plugin, and
  `org.apache.logging.log4j:log4j-1.2-api` 2.14.1. JUnit 3.8.1 is declared
  for tests. Of these, **only log4j** (via `org.apache.logging.log4j.*`
  and one legacy `org.apache.log4j.xml.DOMConfigurator` import) is actually
  used by the source; Gson, opencsv, AppleJavaExtensions and the TIFF plugin
  are unused by any class in this module. `me.qbert.skywatch.Main` is
  configured as the runnable main class in both the `maven-jar-plugin` and
  `maven-assembly-plugin` (`jar-with-dependencies`) configurations.
- There is no `src/test` tree in this module — all 49 `.java` files live
  under `src/main/java` and are covered below.

## Package `me.qbert.skywatch`

### `me.qbert.skywatch.Main` (class)

**Responsibility:** Application entry point. Configures log4j2 logging
level, applies a Linux-only OpenGL/system-look-and-feel workaround, and
constructs the `MainFrame` to start the Swing UI.

**Dependencies:**
- Module: `me.qbert.skywatch.ui.MainFrame`.
- External: `org.apache.logging.log4j.*` (Level, LogManager, Logger,
  Configurator) and the legacy `org.apache.log4j.xml.DOMConfigurator`
  (unused import, log4j 1.x compatibility bridge).
- JDK: `java.lang.System` (property checks).

**Platform-specific imports:** `javax.swing.UIManager`,
`javax.swing.UnsupportedLookAndFeelException` — used to set the system
Look & Feel.

## Package `me.qbert.skywatch.dao`

### `me.qbert.skywatch.dao.StarsCoordinateDao` (class)

**Responsibility:** Static data-access helper that lazily loads and caches
a fixed catalog of bright stars from the classpath resource `stars.txt`
(CSV-like, `#`-comment lines skipped), parsing name, designation, magnitude,
right ascension and declination into `StarCoordinate` model objects,
de-duplicated by designation.

**Dependencies:**
- Module: `me.qbert.skywatch.model.StarCoordinate`.
- JDK: `java.io.BufferedReader`/`InputStream`/`InputStreamReader`,
  `java.util.ArrayList`, `java.util.HashMap`, `java.util.stream.Collectors`.

**Platform-specific imports:** None.

## Package `me.qbert.skywatch.model`

### `me.qbert.skywatch.model.BooleanState` (class)

**Responsibility:** Trivial mutable boolean flag wrapper (e.g. used to
represent "show planet trails" state that can be driven by the sequence/
scripting engine).

**Dependencies:** None (no imports; pure JDK primitives).

**Platform-specific imports:** None.

### `me.qbert.skywatch.model.CoordinateBias` (class, with nested enum `CoordinateMode`)

**Responsibility:** Holds a clamped multiplier/offset pair used to bias
observer latitude or longitude (e.g. for exaggerated/animated coordinate
sweeps in scripted sequences). The nested `CoordinateMode` enum
(`LATITUDE`, `LONGITUDE`) determines the offset clamp range (±90 vs ±360).

**Dependencies:** None (no imports).

**Platform-specific imports:** None.

### `me.qbert.skywatch.model.StarCoordinate` (class)

**Responsibility:** Plain data bean for a catalog star: name, designation,
magnitude, right ascension, declination, group level, and visibility flag.

**Dependencies:** None (no imports).

**Platform-specific imports:** None.

## Package `me.qbert.skywatch.service`

### `me.qbert.skywatch.service.AbstractCelestialObjects` (abstract class)

**Responsibility:** The central, ~2,150-line orchestration class of the
whole app. It builds and wires the entire renderer tree for a projection
(background map, clock hands/faces, sun/moon/planet "pins", day/night
terminator fills via flood-fill on pixel buffers, sunlight/moonlight
zenith-angle contour lines, great-circle sight lines, precession/analemma
paths, star field, user-defined tracked objects, and the split-screen
solar-system calendar panel), and drives per-frame updates from a
`SequenceGenerator`. It implements `ImageTransformerI` (to post-process
day/night imagery with a scanline flood fill) and
`ArcRendererLocationSetterI`/`ProjectionTransformerI` (to place renderer
objects at lat/lon coordinates). Concrete projection subclasses supply the
projection-specific `updateLocation` transform and various projection
metadata via abstract methods. Declares nested enums `ClockFaces`
(NEEDLEHANDS/MICKEYMOUSE/SILLYWALKS) and `MapCenterMode`
(OBSERVER_LAT_LON/OBSERVER_LON/SUN/MOON), and a nested
`UserObjectSettings` helper class.

**Dependencies:**
- Module (same-module): `SequenceGenerator`, `ArcRendererLocationSetterI`,
  `SwitchableProjectionObjects.ProjectionType`,
  `me.qbert.skywatch.ui.component.Canvas`,
  `me.qbert.skywatch.ui.renderers.GlobeImageRenderer`,
  `me.qbert.skywatch.ui.renderers.PinnableCelestialObject`,
  `me.qbert.skywatch.ui.renderers.SolarSystemDateRenderer`,
  `me.qbert.skywatch.util.TrackPathLoader`,
  `me.qbert.skywatch.model.GeoLocation`* (see note),
  `me.qbert.skywatch.model.ObjectDirectionAltAz`*,
  `me.qbert.skywatch.model.ObjectDirectionRaDec`* (*these three types are
  actually declared in the `sw-base` sibling module's
  `me.qbert.skywatch.model` package, imported here under the same package
  name).
- Sibling module `sw-base` (`me.qbert.skywatch.astro.*`): `CelestialObject`,
  `ObserverLocation`, `ObserverLocation3D`,
  `astro.impl.AbstractCelestialObject`, `astro.impl.GeoCalculator`,
  `astro.impl.SolarObjects` (and its nested `SolarSystemCoordinate`),
  `astro.service.AbstractPrecession.PrecessionData`,
  `astro.service.ContourLineGenerator`, `astro.service.MoonPrecession`,
  `astro.service.ProjectionTransformerI`, `astro.service.SunPrecession`.
- Sibling module `ga-base` (`me.qbert.ui.*`): `ImageTransformerI`,
  `RendererI`, `renderers.AbstractFractionRenderer`,
  `renderers.AbstractImageRenderer`, `renderers.ArcRenderer`,
  `renderers.BoundaryContainerRenderer`, `renderers.ColorRenderer`,
  `renderers.EncapsulatingRenderer`, `renderers.ImageRenderer`,
  `renderers.LineRenderer`, `renderers.PolyRenderer`,
  `renderers.SplitContainerRenderer`, `renderers.TextRenderer`,
  `renderers.VirtualImageCanvasRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`Calendar`/`List`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Point`,
`java.awt.geom.Point2D`, `java.awt.image.BufferedImage` — Color drives all
renderer color settings; `BufferedImage`/pixel-array flood fill implements
the day/night terminator shading and out-of-bounds masking directly on
image pixel data.

### `me.qbert.skywatch.service.ArcRendererLocationSetterI` (interface)

**Responsibility:** Contract for placing an `ArcRenderer` at a given
lat/lon (with optional pin size and full-circumference-size flag), used by
`PinnableCelestialObject` and implemented by `AbstractCelestialObjects`.
Extends the sibling module's `ProjectionTransformerI`.

**Dependencies:**
- Sibling module `sw-base`: `me.qbert.skywatch.astro.service.ProjectionTransformerI`
  (extended).
- Sibling module `ga-base`: `me.qbert.ui.renderers.ArcRenderer`.

**Platform-specific imports:** None directly (the `ArcRenderer` parameter
type originates in the `ga-base` module, which itself uses AWT).

### `me.qbert.skywatch.service.AzimuthalEquidistantNPPObjects` (class)

**Responsibility:** Concrete `AbstractCelestialObjects` projection driving
the North-Pole azimuthal-equidistant map ("ae-north"), including special
handling for Flat-Earth-style "FE eclipse center" calculations, a sun
azimuth sight line, and per-object arc renderers for up to five
user-tracked objects.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `service.projections.AzimuthalEquidistantNPTransform`,
  `ui.component.Canvas`.
- Sibling `sw-base` (via inherited `model.GeoLocation`/`ObjectDirectionRaDec`).
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.LineRenderer`, `renderers.TextRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Point`,
`java.awt.geom.Point2D` (and `Point2D.Double`), `java.awt.image.BufferedImage`.

### `me.qbert.skywatch.service.AzimuthalEquidistantSPPObjects` (class)

**Responsibility:** Concrete `AbstractCelestialObjects` projection for the
South-Pole azimuthal-equidistant map ("ae-south"), mirroring the north-pole
variant with inverted rotation direction and simpler user-object handling
(two fixed arc markers, no eclipse/sight-line logic).

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `service.projections.AzimuthalEquidistantSPTransform`, `ui.component.Canvas`.
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.TextRenderer`.
- JDK: `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Point`,
`java.awt.geom.Point2D` (and `Point2D.Double`).

### `me.qbert.skywatch.service.EquirectilinearObjects` (class)

**Responsibility:** Concrete projection for an equirectangular ("plate
carrée") world map, using `EquirectilinearScrollImageRenderer` for a
scrollable background and `DigitalClockImageRenderer` as its top-level
digital clock overlay; a pacman-style horizontal-wrap line renderer marks
the fill boundary.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `service.projections.EquirectilinearTransform`,
  `ui.component.Canvas`, `ui.renderers.DigitalClockImageRenderer`,
  `ui.renderers.EquirectilinearScrollImageRenderer`.
- Sibling `ga-base`: `RendererI`, `renderers.AbstractImageRenderer`,
  `renderers.LineRenderer`, `renderers.TextRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Point`, `java.awt.geom.Point2D`
(and `Point2D.Double`).

### `me.qbert.skywatch.service.GlobeObjects` (class)

**Responsibility:** Concrete projection rendering the Earth as an
orthographic-looking 3D globe (`GlobeImageRenderer`), with a switchable
ring/star clock overlay (`RingClockImageRenderer` or
`StarClockImageRenderer`), zoom-level (full-size vs. inset) handling, and
support for computing moon-shadow overscan (eclipse) coordinates from the
globe's 3D projection math.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `service.projections.GlobeTransform`, `ui.component.Canvas`,
  `ui.renderers.GlobeImageRenderer`, `ui.renderers.RingClockImageRenderer`,
  `ui.renderers.StarClockImageRenderer`.
- Sibling `ga-base`: `RendererI`, `renderers.AbstractImageRenderer`,
  `renderers.ArcRenderer`, `renderers.BoundaryContainerRenderer`,
  `renderers.TextRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`Calendar`/`List`.

**Platform-specific imports:** `java.awt.Point`, `java.awt.geom.Point2D`
(and `Point2D.Double`).

### `me.qbert.skywatch.service.MercatorObjects` (class)

**Responsibility:** Concrete projection for a Mercator world map
(`MercatorScrollImageRenderer` background, `DigitalClockImageRenderer`
overlay), structurally near-identical to `EquirectilinearObjects` but
using `MercatorTransform` for coordinate placement.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `service.projections.MercatorTransform`, `ui.component.Canvas`,
  `ui.renderers.DigitalClockImageRenderer`,
  `ui.renderers.MercatorScrollImageRenderer`.
- Sibling `ga-base`: `RendererI`, `renderers.AbstractImageRenderer`,
  `renderers.LineRenderer`, `renderers.TextRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Point`, `java.awt.geom.Point2D`
(and `Point2D.Double`).

### `me.qbert.skywatch.service.ProjectionTransformI` (interface)

**Responsibility:** Contract implemented by each projection's `*Transform`
class: converts a lat/lon (plus observer position, extra DST rotation, and
overscan factor) into a fractional 2D screen coordinate, with an overload
supporting a "positive Z only" (near-side/visible-hemisphere) filter.

**Dependencies:** None beyond the return type.

**Platform-specific imports:** `java.awt.geom.Point2D`.

### `me.qbert.skywatch.service.SequenceElementI` (interface)

**Responsibility:** Contract for one step in the scripted animation
sequencer: reports a jump-pointer offset (for looping), whether it's an
"intermediate" step that shouldn't pause rendering, and an `update()`
action that can throw `UninitializedObject`.

**Dependencies:**
- Sibling module `sw-base`: `me.qbert.skywatch.exception.UninitializedObject`.

**Platform-specific imports:** None.

### `me.qbert.skywatch.service.SequenceGenerator` (class)

**Responsibility:** Owns the app's live astronomical state — observer
location, observation time, sun/moon/planets (`AbstractCelestialObject`/
`SolarObjects`), the loaded star catalog, latitude/longitude bias, and
animation "speed" (calendar field to auto-advance). It also parses and
executes a simple text scripting DSL (`script.txt`: `fulltime`, `addtime`,
`setobserver`, `setlatbias`/`addlatbias`, `setlonbias`/`addlonbias`,
`nextalt`, `nextaz`, `nextanyaz`, `userobjs`/`setuserobj`, `trails`,
`draw`, `loop`/`label`) into a list of `SequenceElementI` steps that are
stepped through by `advanceSequence()`, including DST-change time
adjustment.

**Dependencies:**
- Module: `SequenceElementI` and every class under `service.sequence.*`
  (`AddLatLonBiasSequence`, `AltitudeAdvanceSequence`,
  `AnyAzimuthAdvanceSequence`, `AzimuthAdvanceSequence`,
  `BooleanStateSequence`, `SequenceJump`, `SequencePause`,
  `SetLatLonBiasSequence`, `SetObserverSequencee`, `SetUserObjectSequence`,
  `TimeAddSequence`, `TimeSetSequence`), `dao.StarsCoordinateDao`,
  `model.BooleanState`, `model.CoordinateBias`, `model.StarCoordinate`.
- Sibling module `sw-base`: `astro.CelestialObject`, `astro.ObservationTime`,
  `astro.ObserverLocation`, `astro.ObserverLocation3D`,
  `astro.TransactionalStateChangeListener`,
  `astro.impl.AbstractCelestialObject`, `astro.impl.MoonObject`,
  `astro.impl.SolarObjects` (and nested `SolarSystemCoordinate`),
  `astro.impl.StarObject`, `astro.impl.SunObject` (imported, largely
  superseded by `SolarObjects`/commented-out `sun` field),
  `exception.UninitializedObject`, `model.CelestialAddress`.
- JDK: `java.io.BufferedReader`/`File`/`FileReader`/`IOException`,
  `java.util.ArrayList`/`Calendar`/`List`/`TimeZone`.

**Platform-specific imports:** None (no AWT/Swing).

### `me.qbert.skywatch.service.StereoAzimuthalObjects` (class)

**Responsibility:** Composite `AbstractCelestialObjects` implementation
that renders two `AzimuthalEquidistantNPPObjects` panels side by side (via
`SplitContainerRenderer`) with opposite stereo-vision rotation offsets, for
cross-eye/parallel stereoscopic viewing. Nearly every abstract/overridden
method simply delegates to `leftPanel`/`rightPanel`.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `AzimuthalEquidistantNPPObjects`, `ui.component.Canvas`.
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.SplitContainerRenderer`, `renderers.TextRenderer`.
- JDK: `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.geom.Point2D` (and
`Point2D.Double`).

### `me.qbert.skywatch.service.StereoGlobeObjects` (class)

**Responsibility:** Same side-by-side stereoscopic composite pattern as
`StereoAzimuthalObjects`, but wrapping two `GlobeObjects` panels instead —
used for the "Stereo (far)"/"Stereo (near)" globe menu options.

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends), `GlobeObjects`,
  `ui.component.Canvas`, `ui.renderers.GlobeImageRenderer` (imported,
  unused directly).
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.SplitContainerRenderer`, `renderers.TextRenderer`.
- JDK: `java.io.File` (imported, unused), `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Point` (imported, unused),
`java.awt.geom.Point2D` (and `Point2D.Double`).

### `me.qbert.skywatch.service.SwitchableProjectionObjects` (class, with nested enum `ProjectionType`)

**Responsibility:** Composite `AbstractCelestialObjects` that owns both an
`AzimuthalEquidistantNPPObjects` and an `EquirectilinearObjects` instance
in a dispatch map, allowing the active projection (`ProjectionType.AE` /
`EQUIRECTILINEAR`) to be switched at runtime by delegating every abstract
method to whichever is currently `activeProjection`. (Not wired into
`MainFrame`'s projection menu — appears to be an alternate/experimental
dispatcher.)

**Dependencies:**
- Module: `AbstractCelestialObjects` (extends),
  `AzimuthalEquidistantNPPObjects`, `EquirectilinearObjects`,
  `ui.component.Canvas`.
- Sibling `sw-base` (transitively, via imported but here-unused astro
  types: `astro.CelestialObject`, `astro.ObservationTime`,
  `astro.ObserverLocation`, `astro.TransactionalStateChangeListener`,
  `astro.impl.GeoCalculator`, `astro.impl.MoonObject`, `astro.impl.SunObject`,
  `astro.service.AbstractPrecession.PrecessionData`,
  `astro.service.MoonPrecession`, `astro.service.SunPrecession`,
  `exception.UninitializedObject`).
- Sibling `ga-base`: `ImageTransformerI`, `RendererI`,
  `renderers.AbstractFractionRenderer`, `renderers.ArcRenderer`,
  `renderers.BoundaryContainerRenderer`, `renderers.ColorRenderer`,
  `renderers.ImageRenderer`, `renderers.LineRenderer`,
  `renderers.PolyRenderer`, `renderers.TextRenderer`,
  `renderers.VirtualImageCanvasRenderer`.
- JDK: `java.io.File`, `java.util.ArrayList`/`Calendar`/`HashMap`/`List`/`TimeZone`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Point`,
`java.awt.geom.Point2D` (and `Point2D.Double`), `java.awt.image.BufferedImage`.

## Package `me.qbert.skywatch.service.projections`

### `me.qbert.skywatch.service.projections.AzimuthalEquidistantNPTransform` (class)

**Responsibility:** Implements `ProjectionTransformI` for the North-Pole
azimuthal-equidistant projection: radius is proportional to co-latitude,
angle to longitude offset from the observer.

**Dependencies:** Module: `service.ProjectionTransformI` (implements).

**Platform-specific imports:** `java.awt.geom.Point2D` (and `Point2D.Double`).

### `me.qbert.skywatch.service.projections.AzimuthalEquidistantSPTransform` (class)

**Responsibility:** Implements `ProjectionTransformI` for the South-Pole
azimuthal-equidistant projection (mirrors the north-pole variant with a
different radius/angle formula).

**Dependencies:** Module: `service.ProjectionTransformI` (implements).

**Platform-specific imports:** `java.awt.geom.Point2D` (and `Point2D.Double`).

### `me.qbert.skywatch.service.projections.EquirectilinearTransform` (class)

**Responsibility:** Implements `ProjectionTransformI` for a linear
lat/lon-to-pixel-fraction mapping calibrated against a specific background
map image's pixel geometry.

**Dependencies:** Module: `service.ProjectionTransformI` (implements).

**Platform-specific imports:** `java.awt.geom.Point2D` (and `Point2D.Double`).

### `me.qbert.skywatch.service.projections.GlobeTransform` (class)

**Responsibility:** Implements `ProjectionTransformI` with full 3D
sphere-to-2D-orthographic math (rotation by latitude/longitude/stereo
angle, near/far-hemisphere visibility test, zoom level and "zoomed out"
state), plus a reverse-lookup `getOverscanLatLon()` used for computing
where an overscanned (off-globe) point would land on the sphere surface
(used for eclipse/moon-shadow placement).

**Dependencies:** Module: `service.ProjectionTransformI` (implements).

**Platform-specific imports:** `java.awt.geom.Point2D.Double`.

### `me.qbert.skywatch.service.projections.MercatorTransform` (class)

**Responsibility:** Implements `ProjectionTransformI` with the standard
Mercator formula (clamped to ±85.12° latitude to avoid infinite Y).

**Dependencies:** Module: `service.ProjectionTransformI` (implements).

**Platform-specific imports:** `java.awt.geom.Point2D` (and `Point2D.Double`).

## Package `me.qbert.skywatch.service.sequence`

All thirteen classes in this package implement `service.SequenceElementI`
and are lightweight command objects consumed by `SequenceGenerator`'s
script interpreter loop. None of them have platform-specific imports.

### `AddLatLonBiasSequence` (class)
**Responsibility:** Multiplies/offsets a `model.CoordinateBias` by given
deltas (relative adjustment), as an intermediate (non-pausing) step.
**Dependencies:** Module: `service.SequenceElementI`, `model.CoordinateBias`.
Sibling `sw-base`: `exception.UninitializedObject` (declared throws).

### `AltitudeAdvanceSequence` (class)
**Responsibility:** Binary-search-advances an observation time until a
given `CelestialObject`'s altitude matches a target value, by repeatedly
recomputing at shrinking time steps.
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`astro.CelestialObject`, `astro.ObservationTime`,
`exception.UninitializedObject`.

### `AnyAzimuthAdvanceSequence` (class)
**Responsibility:** Like `AltitudeAdvanceSequence` but searches for the
closest time at which a celestial object's azimuth matches any of a set of
target azimuths.
**Dependencies:** Same as `AltitudeAdvanceSequence`.

### `AzimuthAdvanceSequence` (class)
**Responsibility:** Binary-search-advances time until a celestial object's
azimuth matches a single target value.
**Dependencies:** Same as `AltitudeAdvanceSequence`.

### `BooleanStateSequence` (class)
**Responsibility:** Sets a `model.BooleanState` (e.g. "show planet
trails") to a fixed value; a non-intermediate (pausing) step.
**Dependencies:** Module: `service.SequenceElementI`, `model.BooleanState`.
Sibling `sw-base`: `exception.UninitializedObject`.

### `SequenceEnd` (class)
**Responsibility:** Sentinel step with a very large jump pointer
(`0x3FFFFF`) signalling script termination; no-op `update()`.
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`exception.UninitializedObject`.

### `SequenceJump` (class)
**Responsibility:** Implements bounded looping — jumps back
`jumpPointerCount` steps while `loopCount` (if non-zero) is decremented,
enabling `loop N` script directives.
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`exception.UninitializedObject`.

### `SequencePause` (class)
**Responsibility:** No-op step whose only purpose is to be a
non-intermediate step, causing the sequencer to pause/render on this frame
(implements the `draw` script directive).
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`exception.UninitializedObject`.

### `SetLatLonBiasSequence` (class)
**Responsibility:** Sets a `model.CoordinateBias` multiplier/offset to
absolute values (vs. `AddLatLonBiasSequence`'s relative adjustment).
**Dependencies:** Module: `service.SequenceElementI`, `model.CoordinateBias`.
Sibling `sw-base`: `astro.ObservationTime`, `astro.ObserverLocation3D`
(imported, unused), `exception.UninitializedObject`.

### `SetObserverSequencee` (class)
**Responsibility:** Sets the observer's geographic location on an
`ObserverLocation` (note the class name's trailing typo "Sequencee").
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`astro.ObservationTime` (imported, unused), `astro.ObserverLocation`,
`exception.UninitializedObject`.

### `SetUserObjectSequence` (class)
**Responsibility:** Sets a scripted user-tracked object's lat/lon/altitude/
diameter on an `ObserverLocation3D`.
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`astro.ObservationTime` (imported, unused), `astro.ObserverLocation3D`,
`exception.UninitializedObject`.

### `TimeAddSequence` (class)
**Responsibility:** Adds a fixed number of seconds to an `ObservationTime`
(implements the `addtime` script directive).
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`astro.ObservationTime`, `exception.UninitializedObject`.

### `TimeSetSequence` (class)
**Responsibility:** Sets an `ObservationTime` to an absolute Unix time in
milliseconds (implements the `fulltime` script directive).
**Dependencies:** Module: `service.SequenceElementI`. Sibling `sw-base`:
`astro.ObservationTime`, `exception.UninitializedObject`.

## Package `me.qbert.skywatch.ui`

### `me.qbert.skywatch.ui.MainFrame` (class)

**Responsibility:** The application's top-level Swing `JFrame` and
controller. Builds the entire menu bar (Projection, View, Options, Speed,
Location menus and dozens of `JCheckBoxMenuItem`s), owns the active
`AbstractCelestialObjects` projection instance (swapped on menu selection
among all seven projection modes including both stereo variants), drives a
`java.util.Timer`/`AnimationTimer` render loop, persists user settings
(latitude/longitude, clock style, day/night opacity, precession path mode,
justification/orientation, etc.) to a `settings.properties` file, supports
full-screen toggling, image export to disk (`ImageIO.write`, JPEG frames
for scripted "record mode"), and handles the `Escape` key to leave full
screen.

**Dependencies:**
- Module: `Main` (for the logger name), every projection class in
  `service.*` (`AbstractCelestialObjects`, `AzimuthalEquidistantNPPObjects`,
  `AzimuthalEquidistantSPPObjects`, `EquirectilinearObjects`,
  `GlobeObjects`, `MercatorObjects`, `StereoAzimuthalObjects`,
  `StereoGlobeObjects`, and the nested `AbstractCelestialObjects.MapCenterMode`),
  `ui.component.Canvas`, `util.AnimationTimer`.
- External: `org.apache.logging.log4j.*` (Level, LogManager, Logger,
  Configurator).
- JDK: `java.io.File`/`FileInputStream`/`FileOutputStream`,
  `java.util.Properties`, `java.util.Timer`.

**Platform-specific imports:** Extensive — `java.awt.BorderLayout`,
`java.awt.Dimension`, `java.awt.Frame`, `java.awt.Graphics2D`,
`java.awt.GraphicsDevice`, `java.awt.GraphicsEnvironment`,
`java.awt.event.ActionEvent`, `java.awt.event.ActionListener`,
`java.awt.event.AdjustmentEvent`, `java.awt.event.AdjustmentListener`,
`java.awt.event.KeyEvent`, `java.awt.event.KeyListener`,
`java.awt.image.BufferedImage`, `javax.imageio.ImageIO`,
`javax.swing.JCheckBoxMenuItem`, `javax.swing.JFrame`, `javax.swing.JMenu`,
`javax.swing.JMenuBar`, `javax.swing.JMenuItem`, `javax.swing.JOptionPane`,
`javax.swing.JScrollBar`, `javax.swing.JScrollPane`,
`javax.swing.JSeparator`. The class itself `extends JFrame implements
KeyListener`.

## Package `me.qbert.skywatch.ui.component`

### `me.qbert.skywatch.ui.component.Canvas` (class)

**Responsibility:** The Swing drawing surface (`extends JPanel`) that all
projections render into. Iterates the current `List<RendererI>` and calls
`renderComponent(Graphics2D)` on each with antialiasing/quality rendering
hints; supports rendering either live (via `paintComponent`) or to an
off-screen `BufferedImage` (`paintToImage`, used for scripted export), with
aspect-ratio-preserving letterboxing when redrawing from a cached image,
and an optional red "recording" indicator dot.

**Dependencies:**
- Sibling module `ga-base`: `me.qbert.ui.RendererI`.
- JDK: `java.util.ArrayList`/`List`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Graphics`,
`java.awt.Graphics2D`, `java.awt.Rectangle` (imported, unused),
`java.awt.RenderingHints`, `java.awt.geom.AffineTransform`,
`java.awt.image.BufferedImage`, `javax.swing.JPanel`. The class itself
`extends JPanel`.

## Package `me.qbert.skywatch.ui.renderers`

### `me.qbert.skywatch.ui.renderers.DigitalClockImageRenderer` (class)

**Responsibility:** `AbstractImageRenderer` subclass that composites a
digital-clock face from a background image plus per-digit sprite images
and AM/PM/separator "decoration" overlays, using mask images to
auto-detect digit/decoration bounding boxes at construction time; redraws
only when the displayed time actually changes.

**Dependencies:** Sibling `ga-base`: `me.qbert.ui.renderers.AbstractImageRenderer`
(extends). JDK: `java.io.File`.

**Platform-specific imports:** `java.awt.Graphics2D`, `java.awt.Point`,
`java.awt.image.BufferedImage`.

### `me.qbert.skywatch.ui.renderers.EquirectilinearScrollImageRenderer` (class)

**Responsibility:** `AbstractImageRenderer` subclass that horizontally
scrolls/wraps an equirectangular map image so a given longitude appears
centered, by splitting and recompositing the source image left/right of
the wrap point; caches the last-rendered longitude to avoid redundant work.

**Dependencies:** Sibling `ga-base`: `renderers.AbstractImageRenderer`
(extends). JDK: `java.io.File`.

**Platform-specific imports:** `java.awt.Graphics2D`, `java.awt.image.BufferedImage`.

### `me.qbert.skywatch.ui.renderers.GlobeImageRenderer` (class)

**Responsibility:** `AbstractImageRenderer` subclass performing a
per-pixel inverse spherical projection (rotating an equirectangular source
texture by latitude/longitude/stereo angle to synthesize an
orthographic-looking globe view) with zoom-level and "zoomed out" support;
caches by (lat, lon, zoom, stereo) to skip redundant recomputation.

**Dependencies:** Sibling `ga-base`: `renderers.AbstractImageRenderer`
(extends). JDK: `java.io.File`.

**Platform-specific imports:** `java.awt.image.BufferedImage`.

### `me.qbert.skywatch.ui.renderers.MercatorScrollImageRenderer` (class)

**Responsibility:** `AbstractImageRenderer` subclass that horizontally
scrolls/wraps a Mercator map image to center a given longitude — structurally
identical to `EquirectilinearScrollImageRenderer`.

**Dependencies:** Sibling `ga-base`: `renderers.AbstractImageRenderer`
(extends). JDK: `java.io.File`.

**Platform-specific imports:** `java.awt.Graphics2D`, `java.awt.image.BufferedImage`.

### `me.qbert.skywatch.ui.renderers.PinnableCelestialObject` (class)

**Responsibility:** Composable helper that renders a celestial object as
either a plain "pin" marker (arc) or a full pushpin (ground position +
connecting line + outer highlight ring + inner dot), using a supplied
`ArcRendererLocationSetterI` for coordinate placement; also provides a
static `brighten(Color, ratio)` color utility used throughout the module
for tinting.

**Dependencies:**
- Module: `service.ArcRendererLocationSetterI`.
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.ColorRenderer`, `renderers.LineRenderer`.
- JDK: `java.util.List`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.geom.Point2D`.

### `me.qbert.skywatch.ui.renderers.RingClockImageRenderer` (class)

**Responsibility:** `AbstractImageRenderer` subclass rendering a
concentric-ring "slip ring" analog-style clock face directly into a pixel
buffer — three bands (hour/minute/second) each showing scrolling digit
text sampled from a numbers spritesheet, positioned by local hour angle;
includes a dead-code `SinglePixelPackedSampleModel`/`WritableRaster`
alternate image-construction path guarded by an always-false flag.

**Dependencies:** Sibling `ga-base`: `renderers.AbstractImageRenderer`
(extends). JDK: `java.io.File`.

**Platform-specific imports:** `java.awt.Point`, `java.awt.image.BufferedImage`,
`java.awt.image.ColorModel`, `java.awt.image.DataBuffer`,
`java.awt.image.DataBufferInt`, `java.awt.image.Raster`,
`java.awt.image.SinglePixelPackedSampleModel`, `java.awt.image.WritableRaster`.

### `me.qbert.skywatch.ui.renderers.SolarSystemDateRenderer` (class)

**Responsibility:** Builds and updates the "solar system calendar" panel —
a circular diagram plotting the Sun and planets (`SolarObjects.OBJECT_LIST`)
at scaled or schematic radii, a day-of-year pointer, meridian/horizon
sight-line indicators, month tick marks (computed once from actual solar
positions), an optional per-planet trail (`LineRenderer.addSegment`), and
supports toggling between sun-centric and Earth-centric display and
to-scale vs. schematic orbit radii.

**Dependencies:**
- Sibling `sw-base`: `astro.ObservationTime`, `astro.ObserverLocation`,
  `astro.TransactionalStateChangeListener`, `astro.impl.SolarObjects`
  (and nested `SolarSystemCoordinate`).
- Sibling `ga-base`: `RendererI`, `renderers.ArcRenderer`,
  `renderers.ColorRenderer`, `renderers.EncapsulatingRenderer`,
  `renderers.LineRenderer`, `renderers.PolyRenderer` (imported, unused
  directly — used only in a commented-out block).
- JDK: `java.util.ArrayList`/`Calendar`/`List`/`TimeZone`.

**Platform-specific imports:** `java.awt.Color`.

### `me.qbert.skywatch.ui.renderers.SplitScreenRenderer<T>` (generic class)

**Responsibility:** Minimal, apparently unfinished/unused generic
`AbstractImageRenderer` subclass holding a `left`/`right` pair of some
type `T`; overrides `setRenderDimensions` as a no-op. No other class in
the module references it.

**Dependencies:** Sibling `ga-base`: `renderers.AbstractImageRenderer`
(extends).

**Platform-specific imports:** None directly (superclass is AWT-based).

### `me.qbert.skywatch.ui.renderers.StarClockImageRenderer` (class)

**Responsibility:** `BoundaryContainerRenderer` subclass implementing a
whimsical "star clock" globe overlay — renders hour/minute/second digits
as constellations of small dots ("stars") positioned on the globe surface
via a 6x8 bitmap font (`digits` static array) and a supplied
`ProjectionTransformerI`, with per-star jittered spacing for a natural
starfield look; includes a second dead-code raster-construction path like
`RingClockImageRenderer`.

**Dependencies:**
- Sibling `sw-base`: `astro.service.ProjectionTransformerI`.
- Module: `service.ArcRendererLocationSetterI` (imported, unused directly).
- Sibling `ga-base`: `renderers.AbstractImageRenderer` (imported, unused —
  actual superclass is `BoundaryContainerRenderer`), `RendererI`,
  `renderers.ArcRenderer`, `renderers.BoundaryContainerRenderer` (extends),
  `renderers.ColorRenderer`.
- JDK: `java.io.File` (imported, unused), `java.util.ArrayList`.

**Platform-specific imports:** `java.awt.Color`, `java.awt.Point`
(imported, unused), `java.awt.geom.Point2D.Double`,
`java.awt.image.BufferedImage`, `java.awt.image.ColorModel`,
`java.awt.image.DataBuffer`, `java.awt.image.DataBufferInt`,
`java.awt.image.Raster`, `java.awt.image.SinglePixelPackedSampleModel`,
`java.awt.image.WritableRaster`.

## Package `me.qbert.skywatch.util`

### `me.qbert.skywatch.util.AnimationTimer` (class)

**Responsibility:** `java.util.TimerTask` subclass that calls back into
`MainFrame.animate(this)` on each scheduled tick, driving the render loop.

**Dependencies:** Module: `ui.MainFrame`. JDK: `java.util.TimerTask`.

**Platform-specific imports:** None.

### `me.qbert.skywatch.util.TrackPathLoader` (class)

**Responsibility:** Static helper that loads a comma-separated waypoint
track (lat/lon pairs) from a classpath resource file into a list of
`GeoLocation` objects, for the "user tracks" line overlay on the map.

**Dependencies:**
- Module: `model.GeoLocation` (actually resolves to the `sw-base` sibling
  module's `me.qbert.skywatch.model.GeoLocation`, imported under the
  module's own package name).
- JDK: `java.io.BufferedReader`/`File`/`InputStream`/`InputStreamReader`,
  `java.util.ArrayList`, `java.util.stream.Collectors`.

**Platform-specific imports:** None.

## Platform-Specific Imports

26 of the module's 49 source files import a GUI-toolkit package
(`java.awt.*`, `javax.swing.*`, `java.awt.image.*`, `java.awt.geom.*`,
`java.awt.event.*`, `javax.imageio.*`). This is expected for a Swing
desktop app whose core loop is "compute astronomy → paint pixels." The
two heaviest are `ui.MainFrame` (full Swing menu/window/event-listener
stack) and `ui.component.Canvas` (the `JPanel` render surface); most of
the rest use only `java.awt.Color`/`Point`/`Graphics2D`/`geom.Point2D`/
`image.BufferedImage` for coordinate math and pixel-buffer manipulation,
with no Swing UI of their own.

| Class | Platform-specific imports |
|---|---|
| `me.qbert.skywatch.Main` | `javax.swing.UIManager`, `javax.swing.UnsupportedLookAndFeelException` |
| `me.qbert.skywatch.service.AbstractCelestialObjects` | `java.awt.Color`, `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.service.AzimuthalEquidistantNPPObjects` | `java.awt.Color`, `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.service.AzimuthalEquidistantSPPObjects` | `java.awt.Color`, `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.EquirectilinearObjects` | `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.GlobeObjects` | `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.MercatorObjects` | `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.ProjectionTransformI` | `java.awt.geom.Point2D` |
| `me.qbert.skywatch.service.StereoAzimuthalObjects` | `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.StereoGlobeObjects` | `java.awt.Point` (unused), `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.SwitchableProjectionObjects` | `java.awt.Color`, `java.awt.Point`, `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.service.projections.AzimuthalEquidistantNPTransform` | `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.projections.AzimuthalEquidistantSPTransform` | `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.projections.EquirectilinearTransform` | `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.projections.GlobeTransform` | `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.service.projections.MercatorTransform` | `java.awt.geom.Point2D`, `java.awt.geom.Point2D.Double` |
| `me.qbert.skywatch.ui.component.Canvas` | `java.awt.Color`, `java.awt.Graphics`, `java.awt.Graphics2D`, `java.awt.Rectangle` (unused), `java.awt.RenderingHints`, `java.awt.geom.AffineTransform`, `java.awt.image.BufferedImage`, `javax.swing.JPanel` (extends) |
| `me.qbert.skywatch.ui.MainFrame` | `java.awt.BorderLayout`, `java.awt.Dimension`, `java.awt.Frame`, `java.awt.Graphics2D`, `java.awt.GraphicsDevice`, `java.awt.GraphicsEnvironment`, `java.awt.event.ActionEvent`, `java.awt.event.ActionListener`, `java.awt.event.AdjustmentEvent`, `java.awt.event.AdjustmentListener`, `java.awt.event.KeyEvent`, `java.awt.event.KeyListener` (implements), `java.awt.image.BufferedImage`, `javax.imageio.ImageIO`, `javax.swing.JCheckBoxMenuItem`, `javax.swing.JFrame` (extends), `javax.swing.JMenu`, `javax.swing.JMenuBar`, `javax.swing.JMenuItem`, `javax.swing.JOptionPane`, `javax.swing.JScrollBar`, `javax.swing.JScrollPane`, `javax.swing.JSeparator` |
| `me.qbert.skywatch.ui.renderers.DigitalClockImageRenderer` | `java.awt.Graphics2D`, `java.awt.Point`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.ui.renderers.EquirectilinearScrollImageRenderer` | `java.awt.Graphics2D`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.ui.renderers.GlobeImageRenderer` | `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.ui.renderers.MercatorScrollImageRenderer` | `java.awt.Graphics2D`, `java.awt.image.BufferedImage` |
| `me.qbert.skywatch.ui.renderers.PinnableCelestialObject` | `java.awt.Color`, `java.awt.geom.Point2D` |
| `me.qbert.skywatch.ui.renderers.RingClockImageRenderer` | `java.awt.Point`, `java.awt.image.BufferedImage`, `java.awt.image.ColorModel`, `java.awt.image.DataBuffer`, `java.awt.image.DataBufferInt`, `java.awt.image.Raster`, `java.awt.image.SinglePixelPackedSampleModel`, `java.awt.image.WritableRaster` |
| `me.qbert.skywatch.ui.renderers.SolarSystemDateRenderer` | `java.awt.Color` |
| `me.qbert.skywatch.ui.renderers.StarClockImageRenderer` | `java.awt.Color`, `java.awt.Point` (unused), `java.awt.geom.Point2D.Double`, `java.awt.image.BufferedImage`, `java.awt.image.ColorModel`, `java.awt.image.DataBuffer`, `java.awt.image.DataBufferInt`, `java.awt.image.Raster`, `java.awt.image.SinglePixelPackedSampleModel`, `java.awt.image.WritableRaster` |

Classes **not** in the table above (23 of 49) have no platform-specific
imports: `dao.StarsCoordinateDao`; all three `model.*` classes; `service.
ArcRendererLocationSetterI`, `service.SequenceElementI`, `service.
SequenceGenerator`; all thirteen `service.sequence.*` classes; `ui.
renderers.SplitScreenRenderer`; and both `util.*` classes.
