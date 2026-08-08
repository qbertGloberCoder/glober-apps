# skywatcher-base (sw-base)

`skywatcher/base` is the astronomy and geolocation calculation core shared by
the other `skywatcher` submodules (`app`, `earthclock`, `geolocator_one`). It
has no UI of its own: it computes celestial object positions (Sun, Moon,
planets, arbitrary stars) from an observer's time and location, converts
between right-ascension/declination and altitude/azimuth coordinate systems,
performs great-circle geodesy on the globe (plus a "flat earth" comparison
model), and generates precession/analemma tracks and visibility contour
lines that the presentation-layer modules render.

- Maven coordinates: `groupId=me.qbert.skywatch`, `artifactId=sw-base`,
  `version=1.3.0`, `packaging=jar`, parent `me.qbert.skywatch:skywatcher:1.0.0`.
- Java target: 1.8.
- Runtime dependencies: `org.apache.logging.log4j:log4j-core:2.14.1`,
  `org.apache.logging.log4j:log4j-api:2.14.1`.
- Test dependency: `junit:junit:3.8.1` (JUnit 3-style `junit.framework.Assert`,
  used only via ad-hoc `main()` methods, not a real test runner).
- Root package: `me.qbert.skywatch.astro` (the module also contains sibling
  root packages `me.qbert.skywatch.exception`, `me.qbert.skywatch.listeners`,
  `me.qbert.skywatch.model`, and top-level test classes directly under
  `me.qbert.skywatch`).

No dependency on any `me.qbert.ui.*` package was found anywhere in this
module.

## Package `me.qbert.skywatch.astro`

### `me.qbert.skywatch.astro.CelestialObjectBuilder` — interface
Builder contract for producing a fully-initialized `CelestialObject`,
throwing `UninitializedObject` if required fields were never set.
- Dependencies: `CelestialObject`, `UninitializedObject`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.CelestialObject` — interface
Contract implemented by every celestial body (Sun, Moon, planets, stars):
recompute its position, report its fixed celestial-sphere RA/Dec, its
current (time-dependent) direction, that direction converted to
altitude/azimuth for an observer, and the geographic point currently
"overhead" of the object. Extends `ObjectStateChangeListener` so objects can
react to time/location changes.
- Dependencies: `ObjectStateChangeListener`, `GeoLocation`,
  `ObjectDirectionAltAz`, `ObjectDirectionRaDec`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.ObservationTime` — class
Wraps a `java.util.Calendar` and derives Julian date, Julian century, UTC
fraction-of-day, and timezone offset from it; recomputes and notifies
registered listeners whenever the underlying instant changes (used to drive
recomputation of every `CelestialObject` watching it). Also supports a
millisecond time bias for "what will the sky look like N seconds from now"
style calculations.
- Dependencies: `UninitializedObject`, `ObjectStateChangeListener`.
- External/JDK: `java.util.ArrayList`, `java.util.Calendar`,
  `java.util.Locale`, `java.util.TimeZone`, log4j (`Logger`/`LogManager`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.ObserverLocation` — class
Observer's ground position (extends `GeoLocation`) with listener support so
`CelestialObject`s can be notified and recompute when the observer moves.
- Dependencies: `GeoLocation` (extends), `ObjectStateChangeListener`.
- External/JDK: `java.util.ArrayList`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.ObserverLocation3D` — class
Same role as `ObserverLocation` but for the 3D/altitude-aware location model
(extends `GeoLocation3D`); notifies listeners on change.
- Dependencies: `GeoLocation3D` (extends), `ObjectStateChangeListener`,
  `GeoLocation` (imported, inherited type).
- External/JDK: `java.util.ArrayList`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.TransactionalStateChangeListener` — class
Batches multiple state-change notifications (e.g. changing time and location
together) into a single "transaction" so listening `CelestialObject`s only
recompute once per `begin()`/`commit()` cycle instead of once per individual
setter call.
- Dependencies: `ObjectStateChangeListener` (implements).
- External/JDK: `java.util.ArrayList`, `java.util.HashMap`, `java.util.Set`.
- No platform-specific imports.

## Package `me.qbert.skywatch.astro.impl`

### `me.qbert.skywatch.astro.impl.AbstractCelestialObject` — abstract class
Base implementation shared by all concrete celestial objects: holds the
`ObserverLocation`/`ObservationTime` state, implements
`stateChanged` to trigger `recompute()`, and implements the RA/Dec→Alt/Az
conversion methods from `CelestialObject` by delegating to the inherited
`GeoCalculator`. Declares the nested abstract class
`AbstractCelestialObjectBuilder`, the common fluent builder (implements
`CelestialObjectBuilder`) that wires an observer time/location and optional
state-change listener into a new instance.
- Dependencies (extends `GeoCalculator`, implements `CelestialObject`):
  `GeoCalculator`, `CelestialObject`, `CelestialObjectBuilder`,
  `ObservationTime`, `ObserverLocation`, `UninitializedObject`,
  `ObjectStateChangeListener`, `GeoLocation`, `ObjectDirectionAltAz`,
  `ObjectDirectionRaDec`.
- External/JDK: log4j (`Logger`/`LogManager`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.impl.GeoCalculator` — class
The core coordinate-math library: great-circle distance/bearing/intermediate
point on a spherical globe (haversine formulas), a deliberately labeled
"silly flat earth" set of equivalent formulas kept for comparison/debunking
purposes, and the RA/Dec ⇄ Alt/Az conversion routines
(`altAzToRaDec`/`raDeclinationToAltitudeAzimuth`) used throughout the module.
Declares the nested class `FlatEarthPosition`, a simple x/y/z + source
RA/Dec holder for the flat-earth projection.
- Dependencies: `GeoLocation`, `ObjectDirectionAltAz`, `ObjectDirectionRaDec`,
  `ObserverLocation`.
- External/JDK: `java.util.ArrayList`, log4j (`Logger`/`LogManager`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.impl.MoonObject` — class
`CelestialObject` implementation computing the Moon's position (a reduced
lunar theory adapted from the `commons-suncalc` project) — right ascension,
declination, and hour angle from an `ObservationTime`/`ObserverLocation`
pair. Instantiated only via its private constructor plus the nested
`MoonObjectBuilder` (extends `AbstractCelestialObjectBuilder`).
- Dependencies: `AbstractCelestialObject` (extends), `GeoLocation`,
  `ObjectDirectionRaDec`.
- External/JDK: log4j (`Logger`/`LogManager`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.impl.SolarObjects` — class
Computes heliocentric/geocentric orbital positions for the Sun and the eight
planets (Mercury through Pluto) using Keplerian orbital elements and Kepler's
equation (`true_anomaly`), selectable via `setObjectIndex`. Declares nested
classes `SunObjectBuilder` (extends `AbstractCelestialObjectBuilder`),
`SolarSystemCoordinate` (public x/y/z coordinate holder), and the private
`ObjectInformation`/`ObjectSettings` value holders used internally during
orbital-element computation.
- Dependencies: `AbstractCelestialObject` (extends), `GeoLocation`,
  `ObjectDirectionRaDec`.
- External/JDK: `java.util.Calendar`, log4j (`Level`, `Logger`, `LogManager`,
  `org.apache.logging.log4j.core.config.Configurator`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.impl.StarObject` — class
`CelestialObject` implementation for a fixed star given a `CelestialAddress`
(RA/Dec); computes Greenwich apparent sidereal time and hour angle to derive
the star's current direction. Nested `StarObjectBuilder` (extends
`AbstractCelestialObjectBuilder`) adds `setStarLocation(...)` and validates it
is set before `build()`.
- Dependencies: `AbstractCelestialObject` (extends), `CelestialObject`,
  `UninitializedObject`, `CelestialAddress`, `GeoLocation`,
  `ObjectDirectionRaDec`.
- External/JDK: log4j (`Logger`/`LogManager`).
- No platform-specific imports.

### `me.qbert.skywatch.astro.impl.SunObject` — class
`CelestialObject` implementation for the Sun, ported from the "NOAA Solar
Calculations" spreadsheet formulas: mean anomaly, equation of center,
obliquity correction, hour angle, solar zenith/elevation, atmospheric
refraction, and azimuth.
- Dependencies: `AbstractCelestialObject` (extends), `GeoLocation`,
  `ObjectDirectionRaDec`.
- External/JDK: log4j (`Logger`/`LogManager`).
- No platform-specific imports.

## Package `me.qbert.skywatch.astro.service`

### `me.qbert.skywatch.astro.service.AbstractPrecession` — abstract class
Generates a time-series of celestial-object positions ("precession" points)
by repeatedly rewinding an `ObservationTime` in fixed steps across one full
orbital period and recording RA/Dec, ground position, and Alt/Az at each
step; supports drawing either the ecliptic path or an analemma by choosing
which RA-advance rate to apply each step. Subclasses supply orbital period,
step size, RA advance rates, and the concrete `CelestialObject` builder to
drive. Declares nested class `PrecessionData`, the per-step result bundle
(RA/Dec, Alt/Az, ground position).
- Dependencies: `CelestialObject`, `ObservationTime`, `ObserverLocation`,
  `TransactionalStateChangeListener`,
  `AbstractCelestialObject.AbstractCelestialObjectBuilder`,
  `UninitializedObject`, `GeoLocation`, `ObjectDirectionAltAz`,
  `ObjectDirectionRaDec`.
- External/JDK: `java.util.ArrayList`, `java.util.List`,
  `java.util.TimeZone`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.service.ContourLineGenerator` — class
Static utility that walks a full circle of bearings around an observer at a
fixed distance, projects each resulting lat/lon through a supplied
`ProjectionTransformerI`, and assembles the visible/valid points into an
ordered polyline (`Point2D.Double` list) — used to draw range-circle /
visibility contour overlays on a map projection. Handles gaps where the
projection returns null (off-screen) and adaptively subdivides segments that
are too long relative to the average segment length.
- Dependencies: `ObserverLocation`, `GeoCalculator` (static calls),
  `GeoLocation`, `ProjectionTransformerI` (parameter type).
- External/JDK: `java.util.ArrayList`.
- **Platform-specific import:** `java.awt.geom.Point2D` (see
  "Platform-Specific Imports" below).

### `me.qbert.skywatch.astro.service.MoonPrecession` — class
`AbstractPrecession` subclass configured for the Moon: supplies a ~29.53-day
(synodic-month-based) orbital period, an 89460-second (~24.85 hour) step
size, and the Moon's ecliptic RA-advance rate, wired to `MoonObject`'s
builder.
- Dependencies: `AbstractPrecession` (extends), `ObserverLocation`,
  `AbstractCelestialObject.AbstractCelestialObjectBuilder`, `MoonObject`.
- No platform-specific imports.

### `me.qbert.skywatch.astro.service.ProjectionTransformerI` — interface
Contract for a map/globe projection: converts a lat/lon (optionally relative
to an observer longitude, and optionally constrained to a full-circumference
render or overscan amount) into 2D screen/canvas coordinates, returning null
when the point is not representable (e.g. off the visible hemisphere).
Implemented by projection classes in other `skywatcher` submodules, not in
this module.
- Dependencies: none within this module.
- **Platform-specific import:** `java.awt.geom.Point2D` (see
  "Platform-Specific Imports" below).

### `me.qbert.skywatch.astro.service.SunPrecession` — class
`AbstractPrecession` subclass configured for the Sun: supplies a 365.25-day
orbital period, a 1-day (86400s) step size, and the mean solar
ecliptic RA-advance rate (`360/365.25` deg/day), wired to `SunObject`'s
builder.
- Dependencies: `AbstractPrecession` (extends), `CelestialObject`,
  `ObservationTime`, `ObserverLocation`, `TransactionalStateChangeListener`,
  `SunObject`, `AbstractCelestialObject.AbstractCelestialObjectBuilder`,
  `UninitializedObject`, `GeoLocation`, `ObjectDirectionAltAz`,
  `ObjectDirectionRaDec`.
- External/JDK: `java.util.ArrayList`, `java.util.List`,
  `java.util.TimeZone`.
- No platform-specific imports.

## Package `me.qbert.skywatch.exception`

### `me.qbert.skywatch.exception.UninitializedObject` — class (checked exception)
Checked `Exception` thrown when a builder (`CelestialObjectBuilder`,
`ObservationTime`) is asked to operate before its required fields
(observer location, observer time, star location, etc.) have been set.
- Dependencies: none within this module (extends `java.lang.Exception`).
- No platform-specific imports.

## Package `me.qbert.skywatch.listeners`

### `me.qbert.skywatch.listeners.ObjectStateChangeListener` — interface
The module's single change-notification contract: `stateChanged(source,
listener)`, implemented by anything that must react when an
`ObservationTime` or `ObserverLocation`/`ObserverLocation3D` changes
(`CelestialObject` implementations, `TransactionalStateChangeListener`).
- Dependencies: none.
- No platform-specific imports.

## Package `me.qbert.skywatch.model`

### `me.qbert.skywatch.model.GeoLocation` — class
Plain latitude/longitude value holder with change-tracking setters that call
a protected, overridable `settingsChanged()` hook (used by subclasses like
`ObserverLocation` to fire listener notifications only on real changes).
- Dependencies: none.
- No platform-specific imports.

### `me.qbert.skywatch.model.GeoLocation3D` — class
Extends `GeoLocation` with altitude and diameter fields, for observers/objects
whose 3D extent or elevation matters (used by `ObserverLocation3D`).
- Dependencies: `GeoLocation` (extends).
- No platform-specific imports.

### `me.qbert.skywatch.model.CelestialAddress` — class
Fixed right-ascension/declination value holder for a star, with
change-tracking setters and an overridable `settingsChanged()` hook, used by
`StarObject`.
- Dependencies: none.
- No platform-specific imports.

### `me.qbert.skywatch.model.ObjectDirectionAltAz` — class
Simple altitude/azimuth value holder with a value-based `equals()`
override.
- Dependencies: none.
- No platform-specific imports.

### `me.qbert.skywatch.model.ObjectDirectionRaDec` — class
Simple right-ascension/declination value holder.
- Dependencies: none.
- No platform-specific imports.

## Package `me.qbert.skywatch` (test sources)

### `me.qbert.skywatch.TestAstroCalculators` — class (test, `src/test/java`)
Ad-hoc regression harness (run via `main()`, not a JUnit-discovered test
class, despite using `junit.framework.Assert`) that builds a
`TransactionalStateChangeListener`-driven set of Sun/Moon/`SolarObjects`/two
`StarObject`s sharing one `ObserverLocation`/`ObservationTime`, drives them
through several known lat/lon/time scenarios, and asserts the resulting
RA/Dec against previously recorded expected values. Also exercises
alt/az conversion and prints formatted DMS/HMS output for a
caller-supplied "current" location.
- Dependencies: `CelestialObject`, `ObservationTime`, `ObserverLocation`,
  `TransactionalStateChangeListener`, `MoonObject`, `SolarObjects`,
  `StarObject`, `SunObject`, `UninitializedObject`, `CelestialAddress`,
  `ObjectDirectionAltAz`, `ObjectDirectionRaDec`.
- External/JDK: `junit.framework.Assert`, `junit.framework.AssertionFailedError`,
  `java.util.Calendar`, `java.util.TimeZone`, log4j (`Level`, `Logger`,
  `LogManager`, `org.apache.logging.log4j.core.config.Configurator`).
- No platform-specific imports.

### `me.qbert.skywatch.TestGeoCalc` — class (test, `src/test/java`)
Ad-hoc regression harness (run via `main()`) that round-trips several RA/Dec
values through `GeoCalculator.raDeclinationToAltitudeAzimuth` and
`GeoCalculator.altAzToRaDec` for various observer locations and asserts the
result matches the original RA/Dec within a small epsilon.
- Dependencies: `ObserverLocation`, `GeoCalculator`, `ObjectDirectionAltAz`,
  `ObjectDirectionRaDec`.
- External/JDK: `junit.framework.Assert`, `junit.framework.AssertionFailedError`.
- No platform-specific imports.

## Platform-Specific Imports

Two files import a platform/GUI-toolkit package — in both cases only
`java.awt.geom.Point2D`, used purely as a lightweight 2D coordinate value
type (no AWT rendering, windowing, or `javax.swing`/`java.awt.image`/
`javafx` usage anywhere in the module):

- `me.qbert.skywatch.astro.service.ContourLineGenerator`
  (`glober-apps/skywatcher/base/src/main/java/me/qbert/skywatch/astro/service/ContourLineGenerator.java`)
  — `import java.awt.geom.Point2D;`
- `me.qbert.skywatch.astro.service.ProjectionTransformerI`
  (`glober-apps/skywatcher/base/src/main/java/me/qbert/skywatch/astro/service/ProjectionTransformerI.java`)
  — `import java.awt.geom.Point2D;`

No other class in this module imports `java.awt.*`, `javax.swing.*`,
`java.awt.image.*`, or `javafx.*`.
