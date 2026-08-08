# skywatcher/geolocator_one Module Catalog

The `skywatcher/geolocator_one` module is a standalone simulator application
that models a rotating, two-axis (azimuth/altitude) light sensor "seeking"
the sun and uses the accumulated pointing data to estimate the observer's
geographic latitude. Its Maven artifact is `me.qbert.skywatch:sw-geoloc1`
(version 1.0.0, packaging `jar`), a child of the `me.qbert.skywatch:skywatcher`
parent POM. The module's package root is `me.qbert.skywatch`.

Per its `pom.xml`, the module depends on two sibling modules
(`me.qbert.skywatch:ga-base` and `me.qbert.skywatch:sw-base`, both `1.3.0`)
plus several third-party libraries (Gson, AppleJavaExtensions, opencsv, a
TwelveMonkeys-derived TIFF ImageIO plugin) and JUnit 3.8.1 for tests, although
none of these third-party libraries are actually referenced by this module's
source code (they are declared but unused here). The `maven-jar-plugin` and
`maven-assembly-plugin` both designate `me.qbert.skywatch.Main` as the
runnable main class, confirming this module builds a standalone launchable
simulator jar.

The source tree contains seven Java files, all under `src/main/java`; there
is no `src/test` directory and no test sources at all.

## Package `me.qbert.skywatch`

### `me.qbert.skywatch.Main` (class)

**Responsibility:** Application entry point. `main(String[] args)`
instantiates `MainFrame`, which in turn builds and displays the Swing UI and
wires up the simulation.

**Dependencies:**
- Module: `me.qbert.skywatch.ui.MainFrame`.
- No dependency on `skywatcher/base` (`me.qbert.skywatch.astro.*`) or the
  shared `base` module (`me.qbert.ui.*`) directly.
- JDK: none beyond implicit `java.lang.*`.

**Platform-specific imports:** None directly (it only imports `MainFrame`,
which itself is Swing-based — see below).

## Package `me.qbert.skywatch.controller`

### `me.qbert.skywatch.controller.LightSensor` (class)

**Responsibility:** Models a simulated two-axis light sensor with a
randomized starting azimuth/altitude offset and step counters
(`STEPS_PER_REVOLUTION = 2000`) that can be incremented to simulate the
sensor physically rotating; converts step counts into relative
azimuth/altitude angles.

**Dependencies:**
- No dependency on other classes in this module.
- No dependency on `skywatcher/base` (`me.qbert.skywatch.astro.*`) or the
  shared `base` module (`me.qbert.ui.*`).
- JDK: only `java.lang.Math` (implicit), no explicit imports.

**Platform-specific imports:** None.

### `me.qbert.skywatch.controller.SensorLocator` (class, with nested class `CurrentLocation`)

**Responsibility:** Drives the simulated `LightSensor` step-by-step to hunt
for the sun's current position (as reported by a `CelestialObject`/
`ObservationTime` pair from `skywatcher/base`), tracking whether the sensor
is currently "locked on" to the sun and exposing the sun's and the sensor's
pointing direction as normalized screen-percentage coordinates via the
nested `CurrentLocation` data holder.

**Dependencies:**
- Module: `me.qbert.skywatch.controller.LightSensor`.
- `skywatcher/base` (`me.qbert.skywatch.astro.*`): `CelestialObject`,
  `ObservationTime`.
- `skywatcher/base`-adjacent packages likely re-exported/shared: 
  `me.qbert.skywatch.exception.UninitializedObject`,
  `me.qbert.skywatch.model.ObjectDirectionAltAz`,
  `me.qbert.skywatch.model.ObjectDirectionRaDec`.
- No dependency on the shared `base` module (`me.qbert.ui.*`).
- JDK: none beyond implicit `java.lang.Math`.

**Platform-specific imports:** None.

### `me.qbert.skywatch.controller.SensorTracker` (class, with nested class `ChangeEntry`)

**Responsibility:** Higher-level controller that owns an `ObservationTime`,
`ObserverLocation`, and a `SunObject`-backed `CelestialObject`, and drives a
`SensorLocator` on a repeating basis to build up a bounded history
(`trackChanges`, capped at 1000 entries) of pointing-direction deltas over
time once the sensor has locked onto the sun; this history is later used by
`MainFrame` to estimate latitude from the tracked slope.

**Dependencies:**
- Module: `me.qbert.skywatch.controller.SensorLocator` (and its nested
  `CurrentLocation`), `me.qbert.skywatch.controller.LightSensor` (for the
  `STEPS_PER_REVOLUTION` constant).
- `skywatcher/base` (`me.qbert.skywatch.astro.*`): `CelestialObject`,
  `ObservationTime`, `ObserverLocation`, and `me.qbert.skywatch.astro.impl.SunObject`.
- `me.qbert.skywatch.exception.UninitializedObject`,
  `me.qbert.skywatch.model.ObjectDirectionAltAz` (sibling astro-support
  packages).
- No dependency on the shared `base` module (`me.qbert.ui.*`).
- JDK: `java.util.ArrayList`, `java.util.List`, `java.util.TimeZone`.

**Platform-specific imports:** None.

## Package `me.qbert.skywatch.ui`

### `me.qbert.skywatch.ui.MainFrame` (class, extends `javax.swing.JFrame`)

**Responsibility:** The application's top-level Swing window ("Geo-Locator
simulator 1"). Builds the menu bar (Timer toggle, Exit), a drawing `Canvas`,
a text console, and latitude/longitude input fields; owns a `SensorTracker`
and a `RenderComponentUtil`-based rendering pipeline; on each animation tick
(driven by an `AnimationTimer`) it re-seeks the sun, updates a set of
`RendererI` drawables (sun position, sensor-pointing position) on the
`Canvas`, and prints diagnostic/tracking text — including a slope-based
latitude estimate computed from `SensorTracker`'s change history — to the
console.

**Dependencies:**
- Module: `me.qbert.skywatch.controller.SensorLocator` (and its nested
  `CurrentLocation`), `me.qbert.skywatch.controller.SensorTracker` (and its
  nested `ChangeEntry`), `me.qbert.skywatch.ui.component.Canvas`,
  `me.qbert.skywatch.util.AnimationTimer`.
- `skywatcher/base` (`me.qbert.skywatch.astro.*`): `CelestialObject`,
  `ObservationTime`, `ObserverLocation`, `me.qbert.skywatch.astro.impl.SunObject`
  (imported directly but not actually instantiated in this class's code —
  sun/time/location setup happens inside `SensorTracker`).
- `me.qbert.skywatch.exception.UninitializedObject`,
  `me.qbert.skywatch.model.ObjectDirectionAltAz`.
- Shared `base` module (`me.qbert.ui.*`): `RendererI`,
  `me.qbert.ui.util.RenderComponentUtil`.
- JDK: `java.util.ArrayList`, `java.util.List`, `java.util.TimeZone`,
  `java.util.Timer`.

**Platform-specific imports:** YES — this is a Swing UI class.
```java
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
```
The class itself extends `javax.swing.JFrame` and builds its whole UI tree
out of these AWT/Swing types.

## Package `me.qbert.skywatch.ui.component`

### `me.qbert.skywatch.ui.component.Canvas` (class, extends `javax.swing.JPanel`)

**Responsibility:** A custom Swing drawing surface that holds a list of
`RendererI` drawables and, on each repaint, sets up antialiasing/quality
rendering hints and delegates drawing to each renderer in turn via
`paintComponent`/`doDrawing`.

**Dependencies:**
- No dependency on other `skywatcher/geolocator_one` classes.
- No dependency on `skywatcher/base` (`me.qbert.skywatch.astro.*`).
- Shared `base` module (`me.qbert.ui.*`): `RendererI`.
- JDK: `java.util.ArrayList`, `java.util.List`.

**Platform-specific imports:** YES — this is a Swing/AWT rendering class.
```java
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;
```
The class extends `javax.swing.JPanel`, overrides
`paintComponent(java.awt.Graphics)`, and does its custom painting via a
`java.awt.Graphics2D` context configured with `java.awt.RenderingHints`.

## Package `me.qbert.skywatch.util`

### `me.qbert.skywatch.util.AnimationTimer` (class, extends `java.util.TimerTask`)

**Responsibility:** A recurring timer task that, on each `run()` invocation,
calls back into the owning `MainFrame.animate(this)` to drive one simulation
tick and UI refresh; scheduled by `MainFrame` at a fixed 20ms period via
`java.util.Timer`.

**Dependencies:**
- Module: `me.qbert.skywatch.ui.MainFrame`.
- No dependency on `skywatcher/base` (`me.qbert.skywatch.astro.*`) or the
  shared `base` module (`me.qbert.ui.*`).
- JDK: `java.util.TimerTask` (superclass).

**Platform-specific imports:** None (`java.util.TimerTask` is a
general-purpose JDK utility class, not a GUI toolkit type). Note, however,
that this class holds a direct reference to `MainFrame`, a Swing `JFrame`,
so it is tightly coupled to the UI layer even though it has no GUI-toolkit
imports itself.

## Platform-Specific Imports

Two classes in this module import GUI-toolkit packages directly:

- **`me.qbert.skywatch.ui.MainFrame`** (extends `javax.swing.JFrame`):
  ```java
  import java.awt.BorderLayout;
  import java.awt.Color;
  import java.awt.Dimension;
  import java.awt.event.ActionEvent;
  import java.awt.event.ActionListener;

  import javax.swing.JFrame;
  import javax.swing.JLabel;
  import javax.swing.JMenu;
  import javax.swing.JMenuBar;
  import javax.swing.JMenuItem;
  import javax.swing.JPanel;
  import javax.swing.JScrollPane;
  import javax.swing.JTextArea;
  import javax.swing.JTextField;
  ```

- **`me.qbert.skywatch.ui.component.Canvas`** (extends `javax.swing.JPanel`):
  ```java
  import java.awt.Graphics;
  import java.awt.Graphics2D;
  import java.awt.RenderingHints;

  import javax.swing.JPanel;
  ```

No other files in the module import `java.awt.*`, `javax.swing.*`,
`java.awt.image.*`, `java.awt.geom.*`, `javafx.*`, or any other GUI-toolkit
package. `me.qbert.skywatch.util.AnimationTimer` holds a runtime reference
to `MainFrame` but has no GUI-toolkit imports of its own, and
`me.qbert.skywatch.Main`, `LightSensor`, `SensorLocator`, and
`SensorTracker` are all free of platform-specific imports.
