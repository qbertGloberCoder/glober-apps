# base module (`ga-base`)

Maven coordinates: groupId `me.qbert.skywatch`, artifactId `ga-base`, version `1.3.0`, packaging `jar`, parent `me.qbert.skywatch:parent:1.0.0`. Root package: `me.qbert.ui`.

This module is a small, self-contained 2D UI/rendering framework shared by other modules in the repository (`foucault_pendulum`, `skywatcher/*`). It defines a `RendererI` abstraction for drawing onto a `java.awt.Graphics2D` surface within a rectangular region, a coordinate-transformation system that lets shapes be positioned either in absolute pixels or as fractions of their containing boundary, and a family of concrete renderers (points, lines, polygons, arcs, text, solid colors, images, and containers that lay out or composite other renderers). It has no third-party runtime dependencies beyond the JDK; its only declared Maven dependency is JUnit 3.8.1, scoped to `test` (no test sources currently exist under `base/src/test`). The module targets Java 1.8 (`maven-compiler-plugin` source/target 1.8).

Every class in this module is built directly on top of AWT (`java.awt.*`, `java.awt.image.*`, `java.awt.geom.*`) or, in one case, Swing (`javax.swing.JPanel`) — see the "Platform-Specific Imports" section at the end for the full breakdown. There is effectively no platform-agnostic core; AWT/Swing types are threaded through nearly the entire public API (method signatures use `Graphics2D`, `Point`, `BufferedImage`, `Color`, etc.), so this module could not currently run headless-free or be ported to a non-AWT UI toolkit without significant rework.

No files under `base/src/main/java` or `base/src/test/java` were skipped; `base/src/test` contains no `.java` files.

---

## Package `me.qbert.ui`

Root package holding the module's core interfaces.

### `me.qbert.ui.CoordinatesTransformationI` (interface)
Defines the contract for converting a stored logical coordinate into a concrete pixel `Point` given a bounding rectangle (left, top, width, height). This is the abstraction that both absolute-pixel and fraction-of-boundary coordinate transformations implement.
- Dependencies (in-module): none (implemented by `me.qbert.ui.coordinates.AbstractCoordinateTransformation`).
- External/JDK: `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Point`.

### `me.qbert.ui.ImageTransformerI` (interface)
Callback contract for post-processing a rendered `BufferedImage` produced by a renderer (e.g. `VirtualImageCanvasRenderer`) before it is drawn, given a reference to the renderer that produced it.
- Dependencies (in-module): `me.qbert.ui.RendererI` (parameter type).
- External/JDK: `java.awt.image.BufferedImage`.
- Platform-specific: yes — imports `java.awt.image.BufferedImage`.

### `me.qbert.ui.RendererI` (interface)
The central rendering abstraction of the module. Declares the three operations every renderer must support: paint itself onto a `Graphics2D` context, report its preferred aspect ratio (or a negative value to mean "no preference"), and receive the pixel rectangle it has been allocated to draw within.
- Dependencies (in-module): implemented throughout `me.qbert.ui.renderers.*`; used pervasively as a field/collection element type by container renderers.
- External/JDK: `java.awt.Graphics2D`.
- Platform-specific: yes — imports `java.awt.Graphics2D`.

---

## Package `me.qbert.ui.coordinates`

Small hierarchy implementing two coordinate-interpretation strategies (absolute pixels vs. fractions of a boundary) behind a common base class.

### `me.qbert.ui.coordinates.AbstractCoordinateTransformation` (abstract class)
Base class holding a mutable logical `(x, y)` pair (as `double`s) with getters/setters. Implements `CoordinatesTransformationI` but leaves the actual `transform(...)` conversion to subclasses.
- Dependencies (in-module): implements `me.qbert.ui.CoordinatesTransformationI`; extended by `AbsoluteCoordinateTransformation` and `FractionCoordinateTransformation`.
- External/JDK: none directly (inherits the `Point`-returning contract from the interface).
- Platform-specific: no direct AWT import in this file, but its inherited abstract method signature is AWT-typed (`java.awt.Point`).

### `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation` (class)
Interprets its stored `(x, y)` as absolute pixel offsets. `transform(...)` casts them to `int` and, unless `floatTransformation` is set, adds the boundary's left/top offset so the coordinate is relative to the boundary's origin rather than the canvas origin.
- Dependencies (in-module): extends `me.qbert.ui.coordinates.AbstractCoordinateTransformation`.
- External/JDK: `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Point`.

### `me.qbert.ui.coordinates.FractionCoordinateTransformation` (class)
Interprets its stored `(x, y)` as fractions (0.0–1.0 typically) of the boundary's width/height. `transform(...)` multiplies by the supplied `dimensionWidth`/`dimensionHeight` and, unless `floatTransformation` is set, offsets by the boundary's left/top.
- Dependencies (in-module): extends `me.qbert.ui.coordinates.AbstractCoordinateTransformation`.
- External/JDK: `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Point`.

---

## Package `me.qbert.ui.renderers`

The bulk of the module: concrete `RendererI` implementations for primitive shapes, text, images, solid colors, and composite/layout containers, plus the shared `AbstractFractionRenderer` base that handles aspect-ratio-aware boundary computation and coordinate conversion.

### `me.qbert.ui.renderers.AbstractFractionRenderer` (abstract class)
The shared base for most renderers. Given an outer pixel rectangle, computes this renderer's actual drawing boundary (`boundaryLeft/Top/Width/Height`), optionally constraining it to preserve `getAspectRatio()` and shifting the resulting box left/right/up/down (or centering it) within the outer rectangle via `shiftDirectionX/Y`. Also exposes `convertToCoordinates(...)`, a helper that converts an `(x, y)` pair through either an internal absolute or fractional coordinate transformer relative to the computed boundary. Defines the `ABSOLUTE_COORDINATES`/`FRACTIONAL_COORDINATES` mode constants used throughout the package.
- Dependencies (in-module): implements `me.qbert.ui.RendererI`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`. Extended by `AbstractImageRenderer`, `ArcRenderer`, `BoundaryContainerRenderer`, `EncapsulatingRenderer`, `LineRenderer`, `PointRenderer`, `PolyRenderer`, `TextRenderer`, `VirtualImageCanvasRenderer`.
- External/JDK: `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Point`.

### `me.qbert.ui.renderers.AbstractImageRenderer` (abstract class)
Base class for image-backed renderers. Holds an `originalImage`, an optional `overlayImage`, and a cached, boundary-sized/rotated composite `image` that is regenerated (`resetImage()`) whenever the boundary size or rotation angle changes. Supports rotating the composited image around an arbitrary pivot and cropping/positioning the drawn result via fractional bound-min/max X/Y. `loadImageFromFile(File)` reads an image via `ImageIO`, polling until its width is known. `getAspectRatio()` derives from the original image's pixel dimensions.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`. Extended by `ImageRenderer`.
- External/JDK: `java.awt.Graphics2D`, `java.awt.geom.AffineTransform`, `java.awt.image.BufferedImage`, `javax.imageio.ImageIO`, `java.io.File`, `java.io.IOException`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.geom.AffineTransform`, `java.awt.image.BufferedImage`.

### `me.qbert.ui.renderers.ArcRenderer` (class)
Draws or fills an arc (via `Graphics2D.drawArc`/`fillArc`) centered at a coordinate with a given width/height ("arc size"), start angle, and arc angle (360 degrees produces a full circle/ellipse). Both the center point and the size can independently be absolute or fractional coordinates, selected at construction time. Reports aspect ratio `-1.0` (no preference).
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`.
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.BoundaryContainerRenderer` (class)
A composite renderer that holds a list of child `RendererI`s and forwards `setRenderDimensions`/`renderComponent` to all of them, after first computing its own aspect-constrained boundary (via the inherited `AbstractFractionRenderer` logic) restricted further by fractional min/max X/Y bounds. Its own `getAspectRatio()` is either delegated to an optional `followContainer` (another `BoundaryContainerRenderer` it mirrors) or computed as the largest aspect ratio among its children.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.RendererI` (list element type and parameter type).
- External/JDK: `java.awt.Graphics2D`, `java.util.ArrayList`, `java.util.List`.
- Platform-specific: yes — imports `java.awt.Graphics2D`.

### `me.qbert.ui.renderers.ColorRenderer` (class)
A minimal renderer that sets the `Graphics2D` background and/or foreground `Color` when invoked, without drawing any shape itself; used to set drawing state ahead of subsequent renderers in a composite. Ignores `setRenderDimensions` (no-op) and reports aspect ratio `-1.0`.
- Dependencies (in-module): implements `me.qbert.ui.RendererI` directly (not via `AbstractFractionRenderer`).
- External/JDK: `java.awt.Color`, `java.awt.Graphics2D`.
- Platform-specific: yes — imports `java.awt.Color`, `java.awt.Graphics2D`.

### `me.qbert.ui.renderers.EncapsulatingRenderer` (class)
Wraps one or more child `RendererI`s, forwarding `renderComponent`/`setRenderDimensions` to each (after computing its own aspect-constrained boundary). Its aspect ratio is either an explicit `lockApsectRatio` override or taken from the first child renderer.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.RendererI` (list element type).
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point` (imported, `Point` appears unused directly in this class but is part of the import list), `java.util.ArrayList`, `java.util.List`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.ImageRenderer` (class)
Concrete file-backed image renderer. Loads a primary image (and optional overlay image) from `java.io.File`s via the inherited `loadImageFromFile`, and installs them as the original image/overlay via `AbstractImageRenderer`'s setters. `reinitImage`/`reinitOverlay` allow swapping images after construction.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractImageRenderer`.
- External/JDK: `java.awt.image.BufferedImage`, `java.io.File`.
- Platform-specific: yes — imports `java.awt.image.BufferedImage`.

### `me.qbert.ui.renderers.LineRenderer` (class)
Draws one or more line segments, each defined by a pair of coordinate transformations (absolute or fractional, chosen at construction). Supports configuring line width/stroke (absolute or fractional), an alpha channel applied to both background and foreground colors during drawing, and a "pacman mode" (`lineConnectionMode`) that, for very long segments, draws the line as wrapping around/off one edge and back in from the opposite edge rather than as a single straight line — useful for wrap-around or polar-style displays. Exposes convenience single-segment `x1/y1/x2/y2` accessors as well as bulk array setters for multiple segments.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`.
- External/JDK: `java.awt.BasicStroke`, `java.awt.Color`, `java.awt.Graphics2D`, `java.awt.Point`, `java.awt.Stroke`, `java.util.ArrayList`.
- Platform-specific: yes — imports `java.awt.BasicStroke`, `java.awt.Color`, `java.awt.Graphics2D`, `java.awt.Point`, `java.awt.Stroke`.

### `me.qbert.ui.renderers.PointRenderer` (class)
Draws a single pixel/point at a coordinate (absolute or fractional) by issuing a zero-length `Graphics2D.drawLine` from the point to itself. Reports aspect ratio `-1.0`.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`.
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.PolyRenderer` (class)
Draws or fills an arbitrary polygon from parallel arrays of X/Y coordinate transformations (absolute or fractional, chosen at construction), via `Graphics2D.drawPolygon`/`fillPolygon`. Reports aspect ratio `-1.0`.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`.
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point`, `java.util.ArrayList`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.SplitContainerRenderer` (class)
A composite renderer that splits its allocated rectangle into two regions — either left/right (`A_LEFT_OF_B`) or top/bottom (`A_ABOVE_B`) — at a configurable fraction (`aFraction`), and assigns each region to a list of child renderers ("A" and "B"). Either side can be toggled on/off (`renderA`/`renderB`), and its aspect ratio is the sum of the (first) child aspect ratios from whichever sides are active. Unlike most renderers here, it implements `RendererI` directly rather than extending `AbstractFractionRenderer`.
- Dependencies (in-module): implements `me.qbert.ui.RendererI` directly; uses `me.qbert.ui.RendererI` as list element type for both child lists.
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point` (imported but not directly referenced in this file's body), `java.util.List`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.TextRenderer` (class)
Draws a string at a coordinate (absolute or fractional) via `Graphics2D.drawString`. Reports aspect ratio `-1.0`.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation`.
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.Point`.

### `me.qbert.ui.renderers.VirtualImageCanvasRenderer` (class)
Renders a list of child `RendererI`s into an internally-managed, boundary-sized offscreen `BufferedImage` "virtual canvas" (regenerated whenever the boundary size changes or `invalidate()` is called), optionally post-processed by an `ImageTransformerI`, then draws that composited image into the real `Graphics2D` target — with support for rotating the composite around a pivot and cropping/positioning it via fractional bound-min/max X/Y, similar to `AbstractImageRenderer`. Useful for compositing a whole sub-scene once and reusing/transforming it as a single image (e.g. for rotation) rather than re-rendering every child renderer each frame.
- Dependencies (in-module): extends `me.qbert.ui.renderers.AbstractFractionRenderer`; uses `me.qbert.ui.ImageTransformerI`, `me.qbert.ui.RendererI` (list element type), `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation`, `AbstractCoordinateTransformation`, `FractionCoordinateTransformation` (imported; not directly instantiated in this file's visible logic beyond the import).
- External/JDK: `java.awt.Graphics2D`, `java.awt.Point` (imported, not directly used in this file's body), `java.awt.geom.AffineTransform`, `java.awt.image.BufferedImage`, `java.util.ArrayList`, `java.util.List`.
- Platform-specific: yes — imports `java.awt.Graphics2D`, `java.awt.geom.AffineTransform`, `java.awt.image.BufferedImage`, and `java.awt.Point`.

---

## Package `me.qbert.ui.util`

### `me.qbert.ui.util.RenderComponentUtil` (class)
A convenience factory for building common renderers pre-populated with pixel coordinates computed from percentages of a target `JPanel`'s current width/height (note: both width and height calculations use `targetFrame.getWidth()` — the height calculation does not call `getHeight()`, which looks like a bug/quirk worth flagging to consumers). Provides `setColors(...)` (builds a `ColorRenderer`), `drawBox(...)` (builds a `PolyRenderer` rectangle), `drawPoint(...)` (builds a `PointRenderer`), and `drawCircle(...)`/`fillCircle(...)` (build an `ArcRenderer` with a 360-degree arc).
- Dependencies (in-module): uses `me.qbert.ui.renderers.ArcRenderer`, `ColorRenderer`, `PointRenderer`, `PolyRenderer`.
- External/JDK: `java.awt.Color`, `javax.swing.JPanel`.
- Platform-specific: yes — imports `java.awt.Color` and `javax.swing.JPanel` (the only Swing import in the module).

---

## Platform-Specific Imports

This module is thoroughly AWT-based; essentially every class imports at least one `java.awt.*` package. Full list, by file, of platform-toolkit import lines found:

| Class | Import lines |
|---|---|
| `me.qbert.ui.CoordinatesTransformationI` | `import java.awt.Point;` |
| `me.qbert.ui.ImageTransformerI` | `import java.awt.image.BufferedImage;` |
| `me.qbert.ui.RendererI` | `import java.awt.Graphics2D;` |
| `me.qbert.ui.coordinates.AbsoluteCoordinateTransformation` | `import java.awt.Point;` |
| `me.qbert.ui.coordinates.FractionCoordinateTransformation` | `import java.awt.Point;` |
| `me.qbert.ui.renderers.AbstractFractionRenderer` | `import java.awt.Point;` |
| `me.qbert.ui.renderers.AbstractImageRenderer` | `import java.awt.Graphics2D;` `import java.awt.geom.AffineTransform;` `import java.awt.image.BufferedImage;` |
| `me.qbert.ui.renderers.ArcRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.BoundaryContainerRenderer` | `import java.awt.Graphics2D;` |
| `me.qbert.ui.renderers.ColorRenderer` | `import java.awt.Color;` `import java.awt.Graphics2D;` |
| `me.qbert.ui.renderers.EncapsulatingRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.ImageRenderer` | `import java.awt.image.BufferedImage;` |
| `me.qbert.ui.renderers.LineRenderer` | `import java.awt.BasicStroke;` `import java.awt.Color;` `import java.awt.Graphics2D;` `import java.awt.Point;` `import java.awt.Stroke;` |
| `me.qbert.ui.renderers.PointRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.PolyRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.SplitContainerRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.TextRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` |
| `me.qbert.ui.renderers.VirtualImageCanvasRenderer` | `import java.awt.Graphics2D;` `import java.awt.Point;` `import java.awt.geom.AffineTransform;` `import java.awt.image.BufferedImage;` |
| `me.qbert.ui.util.RenderComponentUtil` | `import java.awt.Color;` `import javax.swing.JPanel;` |

Additional non-AWT/Swing JDK platform imports of note:
- `me.qbert.ui.renderers.AbstractImageRenderer` also imports `javax.imageio.ImageIO` (image I/O, not a GUI toolkit package per se, but tied to `BufferedImage` loading and part of the same "platform" concern).

Summary: all 19 classes/interfaces in the module import at least one `java.awt.*` package. `java.awt.image.BufferedImage` appears in `ImageTransformerI`, `AbstractImageRenderer`, `ImageRenderer`, and `VirtualImageCanvasRenderer` — the image/canvas-backed classes, as expected. `java.awt.geom.AffineTransform` appears in the two rotation-capable classes, `AbstractImageRenderer` and `VirtualImageCanvasRenderer`. The single Swing (`javax.swing.*`) import in the module is `javax.swing.JPanel` in `me.qbert.ui.util.RenderComponentUtil`, which uses it only to read a target panel's current pixel dimensions.
