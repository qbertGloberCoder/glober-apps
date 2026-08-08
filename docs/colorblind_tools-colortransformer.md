# colorblind_tools/colortransformer

## Overview

`colortransformer` is a desktop Swing application for simulating and experimenting with color-vision-deficiency ("colorblindness") transformations applied to an image. A user loads an image (from a file or the system clipboard), picks a predefined colorblindness simulation mode (e.g. Protanopia, Deuteranomaly, Achromatopsia) from a menu, and the canvas renders the original image stacked alongside the transformed version. A separate dialog also lets the user interactively build a custom 3x3 RGB transform matrix via sliders and apply it as an additional "color rotate" pass before the simulation transform.

## Purpose

The window title (`"Colorblind color rotate utility"`, set in `MainFrame`'s constructor) and the pipeline order in `Canvas.doDrawing` are the key evidence for what this tool is actually for: it is **not** just a "here's what a colorblind person sees" simulator. It's an interactive aid meant to be used *by* a colorblind person to re-map ("rotate") an image's palette and check, live, whether the remapped colors still collapse into a confusable pair under their specific type of color-vision deficiency.

The per-pixel transform chain in `Canvas.doDrawing` is:

1. `colorRotateTransformer` (a user-editable `ColorMatrixTransformer`, driven by `ColorMatrixDialog`) — the manual "palette rotate" pass, applied first, if one has been configured.
2. `colorTransformer` (a `MultiColorTransformer` wrapping one of the nine predefined `ColorMatrixTransformerFactory` matrices, or a no-op) — the colorblindness *simulation* pass, applied second.

`ColorMatrixDialog` exposes per-output-channel (Red/Green/Blue row) sliders that auto-balance to sum to 255, plus one-click "Red↔Green", "Red↔Blue", and "Green↔Blue" swap buttons and a "Reset" button — i.e. quick, coarse palette remaps a user can try without hand-tuning nine sliders. `Canvas` always draws the *unmodified* original image right alongside the doubly-transformed one (rotated, then simulated), so the intended workflow is:

1. Load an image and pick the simulated colorblindness type that matches the user's own deficiency, to see which colors in the original are hard to distinguish once simulated.
2. Open the "Transform" dialog and adjust the rotate matrix (via sliders or a channel-swap shortcut) to shift the problematic hues elsewhere in color space.
3. Watch the simulated-output half of the stacked canvas update live (`stateChanged` pushes the new rotate matrix into `Canvas` and repaints) to see whether that remapping restores a distinction the user's own colorblindness would otherwise erase.

In short: the colorblindness-simulation matrices act as a feedback signal for tuning a *personal* color-rotation the user could in principle apply to their own images/displays, rather than being the end product themselves.

- **Maven artifactId:** `colortransformer` (`groupId` `me.qbert.cbtools`, packaging `jar`, version `1.0-SNAPSHOT`)
- **Root package:** `me.qbert.cbtools`
- **Main class:** `me.qbert.cbtools.App` (configured as the jar's `mainClass`)
- **Standalone module note:** This module is **not** listed in the root aggregator `pom.xml`'s `<modules>` section (which only lists `base`, `skywatcher`, `foucault_pendulum`). It is built and versioned independently, with its own `pom.xml` under `colorblind_tools/colortransformer/`. It targets Java 1.8 and declares only one Maven dependency, `junit:junit:3.8.1` (test scope) — it does not depend on the repo's shared `base` module (`me.qbert.ui.*`) at all; no source file in this module imports anything from that package.

---

## Package `me.qbert.cbtools`

### `me.qbert.cbtools.App` (class)
**File:** `src/main/java/me/qbert/cbtools/App.java`

Application entry point. Its `main` method simply constructs a `MainFrame`, which builds and displays the entire Swing UI.

- **Dependencies (in-module):** `me.qbert.cbtools.ui.MainFrame`
- **Dependencies (base module):** none
- **External/JDK dependencies:** none beyond `java.lang`
- **Platform-specific imports:** none directly (it triggers Swing UI construction indirectly through `MainFrame`)

---

## Package `me.qbert.cbtools.transformers`

### `me.qbert.cbtools.transformers.ColorTransformerI` (interface)
**File:** `src/main/java/me/qbert/cbtools/transformers/ColorTransformerI.java`

Core strategy interface for the module: defines a single method, `Color transformColor(Color sourceColor)`, that all color-transformation implementations must provide.

- **Dependencies (in-module):** none
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.awt.Color`
- **Platform-specific imports:** `java.awt.Color` (AWT)

### `me.qbert.cbtools.transformers.ColorMatrixTransformerFactory` (class)
**File:** `src/main/java/me/qbert/cbtools/transformers/ColorMatrixTransformerFactory.java`

Static factory/registry that holds the hard-coded names and 3x3 percentage matrices for the nine supported colorblindness simulation modes (Normal, Protanopia, Protanomaly, Deuteranopia, Deuteranomaly, Tritanopia, Tritanomaly, Achromatopsia, Achromatomaly), and produces the appropriate `ColorTransformerI` implementation for a given mode name (`NoOpColorTransformer` for "Normal", otherwise a configured `ColorMatrixTransformer`).

- **Dependencies (in-module):** `me.qbert.cbtools.transformers.ColorTransformerI` (return type), `me.qbert.cbtools.transformers.impl.ColorMatrixTransformer`, `me.qbert.cbtools.transformers.impl.NoOpColorTransformer`
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.util.Arrays`, `java.util.List`
- **Platform-specific imports:** none

### `me.qbert.cbtools.transformers.MultiColorTransformer` (class)
**File:** `src/main/java/me/qbert/cbtools/transformers/MultiColorTransformer.java`

A `ColorTransformerI` implementation that wraps/delegates to an internally-held "worker" transformer, which can be swapped at runtime by name via `changeTransformer`. Defaults to `NoOpColorTransformer` until a mode is selected. Used by `Canvas` to hold the "currently selected simulation mode."

- **Dependencies (in-module):** implements `ColorTransformerI`; uses `me.qbert.cbtools.transformers.impl.NoOpColorTransformer` (default worker) and `ColorMatrixTransformerFactory` (to resolve transformer by name)
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.util.List`
- **Platform-specific imports:** `java.awt.Color` (AWT) — used in the `transformColor` method signature

---

## Package `me.qbert.cbtools.transformers.impl`

### `me.qbert.cbtools.transformers.impl.ColorMatrixTransformer` (class)
**File:** `src/main/java/me/qbert/cbtools/transformers/impl/ColorMatrixTransformer.java`

Implements `ColorTransformerI` by applying a configurable 3x3 linear color transform matrix (R/G/B rows and columns) to a source `Color`, producing a new `Color`. Exposes `MATRIX_X`/`MATRIX_Y` constants (both 3), a default identity matrix, and `updateColorMatrix` to replace the matrix (optionally expressed as percentages via a `maximumValue` divisor). Throws a generic `Exception` if a supplied matrix has the wrong dimensions.

- **Dependencies (in-module):** implements `me.qbert.cbtools.transformers.ColorTransformerI`
- **Dependencies (base module):** none
- **External/JDK dependencies:** none beyond `java.lang`
- **Platform-specific imports:** `java.awt.Color` (AWT)

### `me.qbert.cbtools.transformers.impl.NoOpColorTransformer` (class)
**File:** `src/main/java/me/qbert/cbtools/transformers/impl/NoOpColorTransformer.java`

Trivial `ColorTransformerI` implementation that returns the source color unchanged; used for the "Normal" (no colorblindness) mode as a faster path than running an identity matrix.

- **Dependencies (in-module):** implements `me.qbert.cbtools.transformers.ColorTransformerI`
- **Dependencies (base module):** none
- **External/JDK dependencies:** none
- **Platform-specific imports:** `java.awt.Color` (AWT)

---

## Package `me.qbert.cbtools.ui`

### `me.qbert.cbtools.ui.MainFrame` (class)
**File:** `src/main/java/me/qbert/cbtools/ui/MainFrame.java`

The application's main window (`extends JFrame`, `implements ChangeListener`). Builds the menu bar (File > open/clipboard/Exit, "Type of Colorblindness" mode list populated from `ColorMatrixTransformerFactory`, and a "Transform" menu item that opens the `ColorMatrixDialog`), owns the `Canvas` and a console `JTextArea` for error output, and wires up all the action listeners that drive image loading, mode switching, and matrix-dialog updates. `stateChanged` is invoked when the `ColorMatrixDialog`'s sliders change, pulling the current transform matrix and pushing it into the `Canvas`.

- **Dependencies (in-module):** `me.qbert.cbtools.transformers.ColorMatrixTransformerFactory`, `me.qbert.cbtools.ui.component.Canvas`, `me.qbert.cbtools.ui.dialogs.ColorMatrixDialog`
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.io.File`, `java.util.List`
- **Platform-specific imports:** heavy Swing/AWT usage — `java.awt.BorderLayout`, `java.awt.Dimension`, `java.awt.event.ActionEvent`, `java.awt.event.ActionListener`, `javax.swing.JFileChooser`, `javax.swing.JFrame`, `javax.swing.JMenu`, `javax.swing.JMenuBar`, `javax.swing.JMenuItem`, `javax.swing.JScrollPane`, `javax.swing.JTextArea`, `javax.swing.event.ChangeEvent`, `javax.swing.event.ChangeListener`

---

## Package `me.qbert.cbtools.ui.component`

### `me.qbert.cbtools.ui.component.Canvas` (class)
**File:** `src/main/java/me/qbert/cbtools/ui/component/Canvas.java`

The custom drawing surface (`extends JPanel`) that holds the loaded/clipboard image, applies the active `ColorMatrixTransformer` "rotate" pass and `MultiColorTransformer` simulation pass pixel-by-pixel, and paints the original and transformed images stacked (vertically or side by side, chosen by aspect ratio) via an inner `StackedImageCoordinates` helper class. Supports loading an image from a `File` (`ImageIO`) or from the system clipboard, and clones buffered images to preserve an untouched "original" copy.

- **Dependencies (in-module):** `me.qbert.cbtools.transformers.MultiColorTransformer`, `me.qbert.cbtools.transformers.impl.ColorMatrixTransformer`
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.io.File`, `java.io.IOException`, `java.util.ArrayList`, `java.util.List`, `javax.imageio.ImageIO`
- **Platform-specific imports:** extensive AWT/Swing/image usage — `java.awt.Color`, `java.awt.Graphics`, `java.awt.Graphics2D`, `java.awt.Image`, `java.awt.Point`, `java.awt.RenderingHints`, `java.awt.Toolkit`, `java.awt.datatransfer.DataFlavor`, `java.awt.datatransfer.Transferable`, `java.awt.image.BufferedImage`, `javax.swing.JPanel`

---

## Package `me.qbert.cbtools.ui.dialogs`

### `me.qbert.cbtools.ui.dialogs.ColorMatrixDialog` (class)
**File:** `src/main/java/me/qbert/cbtools/ui/dialogs/ColorMatrixDialog.java`

A modeless dialog (`extends JDialog`, `implements ChangeListener, ActionListener`) presenting a 3x3 grid of RGB sliders/text fields (one row per output color: Red/Green/Blue) that lets the user hand-build a custom color transform matrix, with buttons to reset to identity or swap color channel pairs (Red<->Green, Red<->Blue, Green<->Blue). Slider changes auto-balance the other two sliders in the same row so the row sums to 255, and notify an externally-registered `ChangeListener` (set via `setChangeListener`, wired by `MainFrame`) whenever the matrix changes. `getTransformMatrix()` returns the current matrix normalized to 0.0-1.0 values.

- **Dependencies (in-module):** `me.qbert.cbtools.transformers.impl.ColorMatrixTransformer` (only for its `MATRIX_X`/`MATRIX_Y` constants)
- **Dependencies (base module):** none
- **External/JDK dependencies:** none beyond `java.lang`
- **Platform-specific imports:** extensive AWT/Swing usage — `java.awt.BorderLayout`, `java.awt.Dimension`, `java.awt.Frame`, `java.awt.event.ActionEvent`, `java.awt.event.ActionListener`, `javax.swing.BoxLayout`, `javax.swing.JButton`, `javax.swing.JDialog`, `javax.swing.JLabel`, `javax.swing.JPanel`, `javax.swing.JSlider`, `javax.swing.JTextField`, `javax.swing.event.ChangeEvent`, `javax.swing.event.ChangeListener`

---

## Test sources

### `me.qbert.cbtools.AppTest` (class)
**File:** `src/test/java/me/qbert/cbtools/AppTest.java`

A boilerplate JUnit 3 (`junit.framework.TestCase`) test-suite scaffold generated by the Maven archetype. Its single test method (`testApp`) only asserts `true`; it does not exercise any application code.

- **Dependencies (in-module):** none
- **Dependencies (base module):** none
- **External/JDK dependencies:** `junit.framework.Test`, `junit.framework.TestCase`, `junit.framework.TestSuite` (JUnit 3.8.1, test scope)
- **Platform-specific imports:** none

### `me.qbert.cbtools.transformers.ColorMatrixTransformerFactoryTest` (class)
**File:** `src/test/java/me/qbert/cbtools/transformers/ColorMatrixTransformerFactoryTest.java`

Not an actual JUnit test (no `@Test`/`TestCase` usage) — it is a standalone `main`-method smoke-test/demo that lists all transform names from `ColorMatrixTransformerFactory`, transforms a sample `Color` through the "Normal" transformer, and prints the before/after RGB values to stdout.

- **Dependencies (in-module):** `me.qbert.cbtools.transformers.ColorMatrixTransformerFactory`, `me.qbert.cbtools.transformers.ColorTransformerI` (same package, no explicit import needed)
- **Dependencies (base module):** none
- **External/JDK dependencies:** `java.util.List`
- **Platform-specific imports:** `java.awt.Color` (AWT)

---

## Platform-Specific Imports

Every class below imports at least one AWT/Swing/image-toolkit package. Since this is a Swing desktop application, most classes touch these APIs to some degree; classes without any such imports are omitted from this table (`App`, `ColorMatrixTransformerFactory`, `ColorMatrixTransformerFactoryTest`'s data type is `java.awt.Color` so it is included, `AppTest`).

| Class | Platform-specific import lines |
|---|---|
| `me.qbert.cbtools.transformers.ColorTransformerI` | `java.awt.Color` |
| `me.qbert.cbtools.transformers.MultiColorTransformer` | `java.awt.Color` |
| `me.qbert.cbtools.transformers.impl.ColorMatrixTransformer` | `java.awt.Color` |
| `me.qbert.cbtools.transformers.impl.NoOpColorTransformer` | `java.awt.Color` |
| `me.qbert.cbtools.transformers.ColorMatrixTransformerFactoryTest` (test) | `java.awt.Color` |
| `me.qbert.cbtools.ui.MainFrame` | `java.awt.BorderLayout`<br>`java.awt.Dimension`<br>`java.awt.event.ActionEvent`<br>`java.awt.event.ActionListener`<br>`javax.swing.JFileChooser`<br>`javax.swing.JFrame`<br>`javax.swing.JMenu`<br>`javax.swing.JMenuBar`<br>`javax.swing.JMenuItem`<br>`javax.swing.JScrollPane`<br>`javax.swing.JTextArea`<br>`javax.swing.event.ChangeEvent`<br>`javax.swing.event.ChangeListener` |
| `me.qbert.cbtools.ui.component.Canvas` | `java.awt.Color`<br>`java.awt.Graphics`<br>`java.awt.Graphics2D`<br>`java.awt.Image`<br>`java.awt.Point`<br>`java.awt.RenderingHints`<br>`java.awt.Toolkit`<br>`java.awt.datatransfer.DataFlavor`<br>`java.awt.datatransfer.Transferable`<br>`java.awt.image.BufferedImage`<br>`javax.swing.JPanel` |
| `me.qbert.cbtools.ui.dialogs.ColorMatrixDialog` | `java.awt.BorderLayout`<br>`java.awt.Dimension`<br>`java.awt.Frame`<br>`java.awt.event.ActionEvent`<br>`java.awt.event.ActionListener`<br>`javax.swing.BoxLayout`<br>`javax.swing.JButton`<br>`javax.swing.JDialog`<br>`javax.swing.JLabel`<br>`javax.swing.JPanel`<br>`javax.swing.JSlider`<br>`javax.swing.JTextField`<br>`javax.swing.event.ChangeEvent`<br>`javax.swing.event.ChangeListener` |

**Heaviest AWT/Swing consumers:** `MainFrame` (application window/menus), `Canvas` (custom-painted panel doing pixel-level image manipulation with `BufferedImage`/`Graphics2D`/clipboard access), and `ColorMatrixDialog` (slider-based matrix editor dialog) — exactly the three classes called out in the task description as needing careful review. `ColorTransformerI` and its implementations only depend on AWT for the `java.awt.Color` type, not for any rendering/UI toolkit behavior.
