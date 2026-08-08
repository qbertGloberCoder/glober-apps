# foucault_pendulum Module Catalog

The `foucault_pendulum` module (Maven `groupId: me.qbert.simulators`, `artifactId: foucault-pendulum`, packaging `jar`) is a JavaFX-based 3D simulator of a Foucault pendulum. It renders a pendulum swinging inside a 3D room with a compass-ring floor, animates the Coriolis-driven precession of the swing plane over time, and provides a live Swing control panel for adjusting latitude, pendulum length, gravity, drag, camera angle, and integration time-step parameters. The root package is `me.qbert.foucault`, with sub-packages `listeners`, `model`, `physics`, `service`, and `ui`. The Maven main class in all build profiles is `me.qbert.foucault.PendulumSceneFX`.

The module ships a Java Platform Module System descriptor at `src/main/modular/module-info.java` (used only when the Maven `modular` profile is activated, e.g. for `mvn -Pmodular clean javafx:jlink`):

```java
module me.qbert {
    requires javafx.controls;
    requires javafx.graphics;

    // Swing/AWT live in java.desktop
    requires java.desktop;

    exports me.qbert.foucault;
}
```

This declares the module name `me.qbert`, requires the `javafx.controls` and `javafx.graphics` JavaFX modules plus the JDK's `java.desktop` module (needed because `me.qbert.foucault.ui.Settings` uses AWT/Swing), and exports only the `me.qbert.foucault` package. The `pom.xml` declares direct Maven dependencies on `org.openjfx:javafx-controls` and `org.openjfx:javafx-graphics` (version 21.0.8) plus `junit:junit` (3.8.1, test scope), and configures the `org.openjfx:javafx-maven-plugin` for `mvn javafx:run` / `jlink` packaging. This module has no dependency (Maven or source-level) on any sibling module in this repository — in particular it does not use the shared `base` module's `me.qbert.ui.*` package (unrelated namespace: this module's own `me.qbert.foucault.ui` package is self-contained).

## Package `me.qbert.foucault`

### `me.qbert.foucault.PendulumSceneFX` (class)
Application entry point (`public class PendulumSceneFX extends javafx.application.Application implements PendulumStatisticsUpdateListener`). Builds and drives the entire JavaFX 3D scene (floor, walls, compass ring/disc, pendulum pivot/rod/bob, lights, camera), runs the per-frame `AnimationTimer` loop that advances the physics model and updates the 3D scene graph, exposes getter/setter API consumed by the Swing `Settings` panel, and implements the ink-trail "apex dot" drawing callback.
- **Depends on (this module):** `me.qbert.foucault.listeners.PendulumStatisticsUpdateListener`, `me.qbert.foucault.model.PendulumStatistics`, `me.qbert.foucault.model.WeightPosition`, `me.qbert.foucault.physics.Pendulum`, `me.qbert.foucault.ui.Settings`.
- **Sibling-module (`base`, `me.qbert.ui.*`) dependency:** none.
- **Notable external/JDK dependencies:** `javax.swing.SwingUtilities` (for marshalling statistics updates onto the Swing EDT).
- **Platform-specific imports:** yes — both categories.
  - AWT/Swing: `javax.swing.SwingUtilities`
  - JavaFX: `javafx.animation.AnimationTimer`, `javafx.application.Application`, `javafx.geometry.Point3D`, `javafx.scene.*`, `javafx.scene.image.Image`, `javafx.scene.paint.Color`, `javafx.scene.paint.PhongMaterial`, `javafx.scene.shape.*`, `javafx.scene.transform.Rotate`, `javafx.scene.transform.Scale`, `javafx.scene.transform.Translate`, `javafx.stage.Stage`

## Package `me.qbert.foucault.listeners`

### `me.qbert.foucault.listeners.PendulumStatisticsUpdateListener` (interface)
Callback interface for receiving `PendulumStatistics` snapshots each time the physics model detects a new apex/nadir event; implemented by `PendulumSceneFX` to draw ink-trail dots and refresh the settings panel.
- **Depends on (this module):** `me.qbert.foucault.model.PendulumStatistics`.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none beyond `java.lang.Object` (used as a generic parameter type).
- **Platform-specific imports:** none.

## Package `me.qbert.foucault.model`

### `me.qbert.foucault.model.CelestialObjectProfile` (class)
Immutable value object describing a preset celestial body/location "profile" (name, sidereal rotation period, gravitational acceleration, and optionally pendulum length, swing radius, and latitude) used to populate the profile drop-down in the UI.
- **Depends on (this module):** none.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none (pure `java.lang`).
- **Platform-specific imports:** none.

### `me.qbert.foucault.model.PendulumStatistics` (class)
Mutable snapshot/aggregate of simulation statistics: last apex position, precession rate, sidereal time, current and previous forward/return apex and nadir swing vectors, swing-stability correction factor, and elapsed simulation seconds. Provides a deep `copy()` method used to hand off immutable-ish snapshots across threads (JavaFX animation thread to Swing EDT).
- **Depends on (this module):** `me.qbert.foucault.model.WeightPosition`, `me.qbert.foucault.model.SwingVector` (same package).
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none.
- **Platform-specific imports:** none.

### `me.qbert.foucault.model.SwingVector` (class)
Simple data holder for a polar-style swing extreme (radius, azimuth) plus a `lastUpdate` flag indicating whether this vector was the most recently updated one (used for UI highlighting). Provides `copy()`.
- **Depends on (this module):** none.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none.
- **Platform-specific imports:** none.

### `me.qbert.foucault.model.WeightPosition` (class)
Simple mutable 3D position holder (x, y, z) representing the pendulum bob/weight's last apex position, with `updatePosition(...)` and `copy()`.
- **Depends on (this module):** none.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none.
- **Platform-specific imports:** none.

## Package `me.qbert.foucault.physics`

### `me.qbert.foucault.physics.Location` (class)
Trivial holder for a latitude value (degrees), used by `PrecessionRate` to compute Coriolis components.
- **Depends on (this module):** none.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none.
- **Platform-specific imports:** none.

### `me.qbert.foucault.physics.Pendulum` (class)
Core physics engine of the simulator. Integrates the spherical-pendulum equations of motion under gravity and Coriolis force using a velocity-Verlet + RATTLE constraint scheme, with adaptive time-stepping (finer steps near apex/nadir), optional swing-stabilization correction, optional drag damping, apex/nadir/precession-rate event detection, and statistics reporting via the `PendulumStatisticsUpdateListener` callback. Exposes extensive getters/setters for latitude, precession rate, pendulum length, gravity, drag, stable-swing mode, and min/mid/max integration time steps.
- **Depends on (this module):** `me.qbert.foucault.physics.Location`, `me.qbert.foucault.physics.PrecessionRate` (same package), `me.qbert.foucault.listeners.PendulumStatisticsUpdateListener`, `me.qbert.foucault.model.PendulumStatistics`.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none beyond `java.lang.Math`.
- **Platform-specific imports:** none.

### `me.qbert.foucault.physics.PrecessionRate` (class)
Computes the Earth-rotation-rate (angular velocity) vector components (`omegaX/Y/Z`) resolved into the pendulum's local coordinate frame based on a `Location`'s latitude and a configurable rotational rate `omega`; supports toggling whether Coriolis/precession is active (returns zero components when inactive).
- **Depends on (this module):** `me.qbert.foucault.physics.Location` (same package).
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none beyond `java.lang.Math`.
- **Platform-specific imports:** none.

## Package `me.qbert.foucault.service`

### `me.qbert.foucault.service.CelestialObjectService` (class)
Static registry/service exposing an unmodifiable list of built-in `CelestialObjectProfile` presets (Earth at various latitudes, several real-world Foucault pendulum installations, and other planets/pulsars) via `getObjectProfiles()`.
- **Depends on (this module):** `me.qbert.foucault.model.CelestialObjectProfile`.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** `java.util.ArrayList`, `java.util.Collections`, `java.util.List`.
- **Platform-specific imports:** none.

## Package `me.qbert.foucault.ui`

### `me.qbert.foucault.ui.Settings` (class)
Swing `JFrame`-based control/telemetry panel (`public class Settings extends javax.swing.JFrame`) that lets the user pick a celestial-object profile and manually adjust camera azimuth/altitude/field-of-view, time scale, latitude, pendulum length, swing diameter, initial azimuth, Coriolis/stable-swing/drag toggles, drag coefficient, rotation period, gravity, and min/mid/max integration time steps via sliders, text fields, and checkboxes built with `GridBagLayout`. Also displays live read-only statistics (precession rate, time to complete 360°, computed latitude, forward/return apex and nadir vectors, swing-correction coefficient, simulation time) pushed in via `updateStatistics(PendulumStatistics)`. Runs on the Swing EDT alongside the JavaFX scene, and calls `javafx.application.Platform.exit()` on window close to shut down the JavaFX side cleanly.
- **Depends on (this module):** `me.qbert.foucault.PendulumSceneFX` (back-reference to the main application/controller), `me.qbert.foucault.model.CelestialObjectProfile`, `me.qbert.foucault.model.PendulumStatistics`, `me.qbert.foucault.model.SwingVector`, `me.qbert.foucault.service.CelestialObjectService`.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** `java.util.List`.
- **Platform-specific imports:** yes — both categories.
  - AWT/Swing: `java.awt.Color`, `java.awt.Dimension`, `java.awt.GridBagConstraints`, `java.awt.event.FocusEvent`, `java.awt.event.FocusListener`, `javax.swing.JCheckBox`, `javax.swing.JComboBox`, `javax.swing.JFrame`, `javax.swing.JLabel`, `javax.swing.JSlider`, `javax.swing.JTextField` (also uses fully-qualified `java.awt.GridBagLayout`, `java.awt.event.WindowAdapter`, `java.awt.event.WindowEvent` in-line without import statements)
  - JavaFX: `javafx.application.Platform` (used only to call `Platform.exit()`)

## Test sources

### `me.qbert.foucault.physics.PendulumTest` (class, `src/test/java`)
Not a JUnit test class despite the name and location under `src/test/java` — it is a standalone `main()`-driven manual smoke test that constructs a `Pendulum`, configures latitude/precession/length, calls `stepOnce()` in a loop, and prints bob coordinates to stdout for manual inspection. No dependency on the `junit` artifact declared in the pom is actually exercised by this class.
- **Depends on (this module):** `me.qbert.foucault.physics.Pendulum` (same package).
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** `java.lang.System` (stdout).
- **Platform-specific imports:** none.

### `me.qbert.JavaFX3DBoxExample` (class, `src/test/java`, package `me.qbert` — note: different package root than the rest of the module)
Standalone JavaFX demo/scratch `Application` (unrelated to the pendulum domain model) that renders a single rotating 3D `Box` with a `RotateTransition` animation; appears to be a learning/reference example for the JavaFX 3D APIs used elsewhere in the module, not an automated test.
- **Depends on (this module):** none.
- **Sibling-module dependency:** none.
- **Notable external/JDK dependencies:** none beyond `java.lang`.
- **Platform-specific imports:** yes — JavaFX only.
  - JavaFX: `javafx.animation.Animation`, `javafx.animation.Interpolator`, `javafx.animation.RotateTransition`, `javafx.application.Application`, `javafx.scene.Group`, `javafx.scene.PerspectiveCamera`, `javafx.scene.Scene`, `javafx.scene.paint.Color`, `javafx.scene.paint.PhongMaterial`, `javafx.scene.shape.Box`, `javafx.scene.transform.Rotate`, `javafx.stage.Stage`, `javafx.util.Duration`

## Platform-Specific Imports

This module's primary UI toolkit is JavaFX (used for the main 3D scene), but it also pulls in AWT/Swing (via the JDK's `java.desktop` module, explicitly required in `module-info.java`) for the secondary 2D settings/control panel. Both are non-portable to headless or module-restricted JVM environments and are called out separately below.

### AWT/Swing (`java.awt.*` / `javax.swing.*`)

| Class | Import lines |
|---|---|
| `me.qbert.foucault.PendulumSceneFX` | `import javax.swing.SwingUtilities;` |
| `me.qbert.foucault.ui.Settings` | `import java.awt.Color;`<br>`import java.awt.Dimension;`<br>`import java.awt.GridBagConstraints;`<br>`import java.awt.event.FocusEvent;`<br>`import java.awt.event.FocusListener;`<br>`import javax.swing.JCheckBox;`<br>`import javax.swing.JComboBox;`<br>`import javax.swing.JFrame;`<br>`import javax.swing.JLabel;`<br>`import javax.swing.JSlider;`<br>`import javax.swing.JTextField;`<br>(plus unimported fully-qualified uses: `java.awt.GridBagLayout`, `java.awt.event.WindowAdapter`, `java.awt.event.WindowEvent`) |

### JavaFX (`javafx.*`)

| Class | Import lines |
|---|---|
| `me.qbert.foucault.PendulumSceneFX` | `import javafx.animation.AnimationTimer;`<br>`import javafx.application.Application;`<br>`import javafx.geometry.Point3D;`<br>`import javafx.scene.*;`<br>`import javafx.scene.image.Image;`<br>`import javafx.scene.paint.Color;`<br>`import javafx.scene.paint.PhongMaterial;`<br>`import javafx.scene.shape.*;`<br>`import javafx.scene.transform.Rotate;`<br>`import javafx.scene.transform.Scale;`<br>`import javafx.scene.transform.Translate;`<br>`import javafx.stage.Stage;` |
| `me.qbert.foucault.ui.Settings` | `import javafx.application.Platform;` |
| `me.qbert.JavaFX3DBoxExample` (test) | `import javafx.animation.Animation;`<br>`import javafx.animation.Interpolator;`<br>`import javafx.animation.RotateTransition;`<br>`import javafx.application.Application;`<br>`import javafx.scene.Group;`<br>`import javafx.scene.PerspectiveCamera;`<br>`import javafx.scene.Scene;`<br>`import javafx.scene.paint.Color;`<br>`import javafx.scene.paint.PhongMaterial;`<br>`import javafx.scene.shape.Box;`<br>`import javafx.scene.transform.Rotate;`<br>`import javafx.stage.Stage;`<br>`import javafx.util.Duration;` |

No classes in this module import `java.awt.image.*` specifically, and no other platform-specific packages (e.g. AWT peers, `sun.*`) appear.
