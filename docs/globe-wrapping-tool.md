# Project Topology — `globe-wrapping-tool` (new implementation, `src/`)

Structural map of the current, active implementation under `src/` (36 main classes and 23 test
classes, 103 tests — line counts drift as work continues; see each package's table below for
current per-file counts). Built per
`globe-unwrapper-requirements.md`'s design brief, following `tasks.md`'s step-by-step plan and
execution log. See `CLAUDE.md` for narrative/behavioral context; this file is the class-by-class
map.

## Package tree

```
src/main/java/me/qbert/globewrapping/
├── geometry/        pure, dependency-free projection math (11 classes)
├── calibration/      SourceCalibration, config loading, resolution order (6 classes)
├── image/            ImageIO wrappers, canonical canvas, bilinear sampling (3 classes)
├── blend/            confidence weighting + multi-source accumulation (4 classes)
├── pipeline/          unwrap/combine/wrap stage orchestrators (4 classes)
└── cli/               subcommand parsing + Main (8 classes)

src/test/java/me/qbert/globewrapping/   mirrors the above, 22 test classes, 99 tests
```

Dependency direction is strictly downward through this list: `cli` → `pipeline` → `blend`/`image`/
`calibration` → `geometry`. `geometry` depends on nothing else in the project (pure math); `image`
also depends on nothing else in the project. No cycles.

## `geometry` — the math-heavy core (11 classes, 405 lines)

| Class | Lines | Role |
|---|---|---|
| `EarthModel` | 11 | `EARTH_RADIUS_KM = 6371.0` constant. |
| `GeoPoint` | 22 | Record `(latitudeDeg, longitudeDeg)`; validates latitude range, normalizes longitude into `[-180, 180)` in its compact constructor. |
| `PixelPoint` | 10 | Record `(x, y)` — meaning (fractional `[0,1]` vs. absolute pixel index) is documented per producing method, not baked into the type. |
| `DiscCalibration` | 32 | Record: one source's explicit calibration geometry (`subLatitudeDeg`, `subLongitudeDeg`, `distanceKm`, `nadirX/Y`, `radiusX/Y`). Pure data + `subPoint()` convenience. |
| `ObserverParameters` | 15 | Record: a synthetic observer for `wrap` (`centerLatitudeDeg/LongitudeDeg`, `heightKm`). |
| `GeoLimb` | 92 | The Earth-center-angle (`theta`) ⇄ camera-view-angle (`alpha`) relationship: `visibleHalfAngleRadians`, `maxViewAngleRadians`, `viewAngleFromGroundAngle`, `groundAngleFromViewAngle` (closed-form, law-of-sines derived — see `CLAUDE.md`'s "Key geometry decisions"). |
| `GreatCircle` | 58 | Standard spherical trig: `angularDistanceRadians` (haversine), `initialBearingRadians`, `destinationPoint`. |
| `EquirectangularMapping` | 28 | Pure lat/lon ⇄ absolute-pixel plate-carrée mapping for a given width/height. |
| `SatelliteDiscProjection` | 51 | **Forward** projection (`unwrap`'s core): `DiscCalibration` + target `GeoPoint` → `Optional<PixelPoint>` fractional source pixel, or empty if beyond the visible limb. Uses `tan(alpha)` as the linear image-plane radius (rectilinear-lens model). |
| `ObserverProjection` | 25 | Interface: one `unproject(observer, outputW, outputH, pixelX, pixelY) -> Optional<GeoPoint>` method. One implementation per projection *strategy* by design (see `CLAUDE.md`). |
| `PerspectiveObserverProjection` | 97 | **Inverse** projection (`wrap`'s core), implements `ObserverProjection`: `tanAlphaPerPixel` "lens" + observer height fully determines scale, per requirements §6. Default lens is derived *per call* from the requested output size, anchored to `REFERENCE_ALTITUDE_KM` (35786 km, real GEO) so the disc inscribes the frame's shorter dimension at that height regardless of size — fixed to a real usability bug (see requirements §6.1, `CLAUDE.md`, `tasks.md`'s post-plan log) where a single hardcoded constant made real GEO altitude produce a disc ~2x too big for a 1024×1024 frame. An explicit fixed-lens constructor remains available for callers who want output-size-independent scale. |

**Test coverage**: 6 test classes (`GeoLimbTest` 7 tests, `GreatCircleTest` 6, `EquirectangularMappingTest` 4, `SatelliteDiscProjectionTest` 4, `PerspectiveObserverProjectionTest` 5 — including a forward/inverse round-trip cross-consistency check and a reference-altitude inscribe check across multiple output sizes). 26 tests total, all pure/fast (no I/O).

## `calibration` (6 classes, 226 lines)

| Class | Lines | Role |
|---|---|---|
| `SourceCalibration` | 21 | Record `(alias, DiscCalibration disc)` — the named-profile wrapper requirements §4 calls for. |
| `DefaultCalibrationProfiles` | 39 | Built-in `goes8`/`himawari` profiles, seed values cross-confirmed by two prior codebases (see `CLAUDE.md`). |
| `CalibrationOverrides` | 51 | Record of 7 nullable `Double` fields (one per `DiscCalibration` field) + `applyTo(SourceCalibration)`; `none()` singleton. |
| `CalibrationRegistry` | 52 | `withBuiltInDefaults()` → `withConfigProfiles(Map)` → `resolve(alias, overrides)`, implementing the built-in → config → override resolution order. |
| `CalibrationConfigLoader` | 96 | Parses a YAML file/stream (SnakeYAML) into `Map<String, SourceCalibration>`; strict — missing/non-numeric fields throw `CalibrationConfigException` naming the alias and field. |
| `CalibrationConfigException` | 13 | Config-parsing error type. |

**Test coverage**: 3 classes, 16 tests (`CalibrationRegistryTest`, `CalibrationOverridesTest`, `CalibrationConfigLoaderTest`) — resolution-order precedence, override merging, and YAML parsing (including the exact example config from the requirements doc).

## `image` (3 classes, 231 lines)

| Class | Lines | Role |
|---|---|---|
| `ImageFiles` | 35 | `ImageIO` load/save wrappers; no polling-retry hack — failures surface directly. |
| `EquirectCanvas` | 105 | The canonical equirect's in-memory state: `double[]` red/green/blue/weight buffers sized `width*height`; `accumulate(x,y,r,g,b,weight)`, `averageRgb`, `isCovered`, `toBufferedImage()` (alpha derived purely from accumulated weight > 0). |
| `BilinearSampler` | 91 | Alpha-weighted bilinear sampling of a `BufferedImage` at fractional coordinates — each of 4 corners weighted by both bilinear basis weight *and* its own alpha, so partially-transparent neighbors don't bleed a dark fringe into the result. |

**Test coverage**: 3 classes, 14 tests, including `transparentNeighborsDoNotDilutePartiallyOpaqueRegion` in `BilinearSamplerTest`.

## `blend` (4 classes, 176 lines)

| Class | Lines | Role |
|---|---|---|
| `ConfidenceWeightFunction` | 19 | `@FunctionalInterface`: `weight(angularDistanceFromNadirRadians, maxVisibleAngleRadians) -> double`. The "swappable falloff curve" requirements §5 asks for. |
| `LinearFalloffConfidenceWeight` | 23 | Default impl: `1.0` at nadir, linearly down to `0.0` at the limb. |
| `SourceContribution` | 17 | Record `(DiscCalibration, BufferedImage)` — a loaded, ready-to-sample source. |
| `SourceAccumulator` | 82 | The actual `O(width·height·sources)` loop: for every canonical pixel, ask every source's `SatelliteDiscProjection` + weight + `BilinearSampler`, accumulate into an `EquirectCanvas`. Uncovered pixels are simply never accumulated into. |

**Test coverage**: 2 classes, 9 tests — including an *exact*-value 50/50-blend test (two sources with identical sub-point/distance have mathematically identical weight at any shared point) and a closer-source-dominance test.

## `pipeline` (4 classes, 218 lines)

| Class | Lines | Role |
|---|---|---|
| `OutputFormats` | 20 | Package-private: infers an `ImageIO` format name from an output path's extension. |
| `UnwrapStage` | 57 | `List<SourceInput> -> canonical.png`. `SourceInput` record `(SourceCalibration, Path)`. `DEFAULT_CANVAS_WIDTH/HEIGHT = 3600×1800`. |
| `CombineStage` | 52 | `canonical + optional basemap -> flattened.jpg`. Basemap resized via AWT `Graphics2D`/bilinear `RenderingHints` (plain raster resize, not a geometric reprojection — deliberately not routed through `BilinearSampler`). `DEFAULT_BACKGROUND = black` when no basemap given. |
| `WrapStage` | 89 | `canonical + ObserverParameters + size -> wrapped.jpg`. Always opaque output (background-filled), since `.jpg` can't hold alpha. |

**Test coverage**: 4 classes, 12 tests — 3 per-stage (`UnwrapStageTest`, `CombineStageTest`, `WrapStageTest`) plus `EndToEndPipelineTest` chaining all three together with synthetic fixtures (the one genuinely automated full-chain test; Step 7's chained verification was otherwise done manually against the real built jar — see `tasks.md`).

## `cli` (8 classes, 316 lines)

| Class | Lines | Role |
|---|---|---|
| `Main` | 23 | Entry point: catches `CliUsageException` (exit 2) and any other `Exception` (exit 1), otherwise delegates to `CommandDispatcher`. |
| `CommandDispatcher` | 30 | Dispatches `args[0]` to `unwrap`/`combine`/`wrap`. |
| `UnwrapCommand` | 57 | Parses `<output> <alias1> <path1> [...] [--config] [--override ...]`; resolves each alias via `CliConfig`/`CalibrationRegistry`, runs `UnwrapStage`. |
| `CombineCommand` | 29 | Parses `<basemap|none> <input> <output>`, runs `CombineStage`. |
| `WrapCommand` | 101 | Parses the `center <lat,lon> [height <km>] size <WxH>` keyword syntax directly (not via `ArgScanner` — it isn't `--flag`-shaped). Keywords are matched by name, not fixed position, so `height` can be omitted (defaults to `PerspectiveObserverProjection.REFERENCE_ALTITUDE_KM`, real GEO) and the three keywords can appear in any order. Runs `WrapStage`. |
| `ArgScanner` | 61 | Hand-rolled `--flag value` / `--flag=value` vs. positional-argument splitter — no external CLI-parsing dependency. |
| `CliConfig` | 105 | Resolves `--config` (explicit path, or default search for `./globe-wrapping-tool.yaml`, optional either way) and parses repeatable `--override <alias>.<field>=<value>` into `CalibrationOverrides` per alias. |
| `CliUsageException` | 9 | User-facing usage-error type; `Main` maps it to exit code 2. |

**Test coverage**: 6 classes, 30 tests — including full command-level integration tests (`UnwrapCommandTest`, `CombineCommandTest`, `WrapCommandTest`) that write real files and invoke the command classes end-to-end, not just parsing-level unit tests. `WrapCommandTest` covers the optional-`height` default and keyword-order-independence directly (e.g. asserting the omitted-height and explicit-reference-altitude invocations produce pixel-identical output).

## What isn't here

No `module-info.java` (plain classpath jar, not a JPMS module). No logging framework — output is
plain `System.out.println("Wrote " + outputPath)` / `System.err.println` in the `cli` package only;
every other package is silent (no logging calls at all, consistent with `geometry`/`image`/`blend`/
`pipeline` being pure/testable and free of side-effecting diagnostics). No dependency-injection
framework — every class is wired by hand via plain constructors (`new UnwrapStage()`,
`new SourceAccumulator(weightFunction)`, etc.), matching the project's small size.
