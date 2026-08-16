package me.qbert.skywatch.camera.ui;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import me.qbert.skywatch.camera.astro.CameraAstronomy;
import me.qbert.skywatch.camera.batch.CameraImageCaches;
import me.qbert.skywatch.camera.batch.CameraImageDispatch;
import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.GlobalSettings;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.orientation.MountTransformRuntime;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.render.FrameCompositor;
import me.qbert.skywatch.camera.source.ArchiveFrameCache;
import me.qbert.skywatch.camera.source.DirectoryCache;

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

// Task 8.1's headless-testable core: everything the interactive preview window needs to decide
// "what to render right now," independent of Swing entirely. PreviewWindow is a thin wrapper
// gluing this to actual widgets - and deliberately so, not just for style: java.awt.Window/
// javax.swing.JFrame subclasses throw HeadlessException the instant they're constructed in a
// display-less environment (confirmed while building this round - this module's own CI/sandbox has
// no display, see docs/tasks.md's verification note), so keeping every real decision here is what
// makes any of this testable at all outside a real desktop.
//
// Wraps exactly the same pieces task 5.1's CameraImageDispatch already unifies (Real-camera
// scrubbing, Virtual-camera image loading) plus the two session-scoped pieces CLAUDE.md's "Camera
// image display" section describes on top of that: the Layer-1 show/hide toggle (task 0.6) and the
// simulated clock (task "Simulated clock") that supplies "current render time" instead of a raw
// scrub target, so play/pause/resume/set-time all just work by construction - CameraImageDispatch
// itself has no opinion on where its targetEpochMillis argument comes from.
public final class PreviewController {
	private final CameraConfig cameraConfig;
	private final DirectoryCache cache;
	private final FrameCompositor.Options options;
	private final SimulatedClock clock;

	private boolean imageShown = true;
	private int canvasWidthPixels;
	private int canvasHeightPixels;
	// The most recent full-resolution renderCurrentFrame() result - see renderFastPtzPreview(...),
	// which composites a small live inset over a COPY of this rather than mutating it.
	private BufferedImage lastFullFrame;
	private PlateSolveSession activeEditSession;
	private boolean lastRenderArchiveScanTruncated;
	// A real user report: the control panel's Time tab spinners never reflected what was actually
	// on screen, stuck at their construction-time default forever - this is what CameraImageDispatch.
	// Result.getRenderedEpochMillis() was already computing (the snapped archived frame's own
	// timestamp for a shown Real camera, or the raw scrub/clock target otherwise) but nothing kept
	// after the render call returned. Starts at 0L - "no render has happened yet," which is only ever
	// observable for the sliver of time before the very first renderCurrentFrame() call completes.
	private long lastRenderedEpochMillis;
	// Memoizes the archive scan (see ArchiveFrameCache's own class comment - a real user report:
	// re-walking the whole archive tree on every render, up to 4 times a second while playing,
	// stalls the UI for a large archive) - null for a Virtual camera (no archive to scan at all).
	private final ArchiveFrameCache archiveFrameCache;
	// Resolves a PTZ Virtual camera's "use my locale" location setting - see config.
	// ObserverLocationSetting.resolve(...). null (via the two shorter constructors below) preserves
	// this class's original behavior exactly: such a camera throws a clear error rather than
	// silently guessing at a location.
	private final GlobalSettings globalSettings;
	// A direct user report: time-scrubbing needlessly rebuilds Layer 1 on every render tick, even
	// when nothing that actually determines its content has changed since the last call - see
	// batch.CameraImageCaches/LastImageCache's own class comments. Naturally fresh per camera (a new
	// PreviewController is built per AppController.switchToCamera(...)), so no explicit
	// invalidation-on-switch logic is needed here, matching how archiveFrameCache already behaves.
	private final CameraImageCaches layer1Caches = new CameraImageCaches();
	// The missing equatorial-mount/geolocation-stabilizer wiring - see orientation.
	// MountTransformRuntime's own class comment. Null for a camera that's never mount-eligible (a
	// Live+recorded Real camera - CameraConfig.getMountControl() would throw for it); constructed
	// once, naturally fresh per camera for the same reason layer1Caches/archiveFrameCache are.
	private final MountTransformRuntime mountRuntime;
	// Item 0's shared-instance architecture - see the CameraAstronomy-accepting constructor's own
	// comment for the full story, including what's still open.
	private final CameraAstronomy astronomy;
	// Plate-solve technique 2's click-and-mark redesign - a direct user request, moving marking
	// clicks from a small dedicated panel into this (larger, resizable) preview window for better
	// click precision. See setMarkingModeActive(...)'s own comment for what this actually changes
	// about rendering.
	private boolean markingModeActive;

	// cache may be null only for a Virtual camera - CameraImageDispatch.composite(...) never
	// consults it in that case (only Real-camera dispatch does, and rejects a null cache itself).
	public PreviewController(CameraConfig cameraConfig, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, int initialCanvasWidthPixels, int initialCanvasHeightPixels) {
		this(cameraConfig, cache, options, clock, initialCanvasWidthPixels, initialCanvasHeightPixels,
				ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS);
	}

	// archiveFrameCacheRefreshIntervalMillis - see ArchiveFrameCache's own class comment; the "value
	// to be fine tuned" tuning knob (CLAUDE.md's "Local file cache" section), exposed via
	// --cache-refresh-interval-seconds in the CLI. The overload above keeps the previous default for
	// every existing caller/test.
	public PreviewController(CameraConfig cameraConfig, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, int initialCanvasWidthPixels, int initialCanvasHeightPixels,
			long archiveFrameCacheRefreshIntervalMillis) {
		this(cameraConfig, cache, options, clock, initialCanvasWidthPixels, initialCanvasHeightPixels,
				archiveFrameCacheRefreshIntervalMillis, null);
	}

	// globalSettings - see the field's own comment. The two shorter constructors above pass null,
	// preserving their exact original behavior for every existing caller/test.
	public PreviewController(CameraConfig cameraConfig, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, int initialCanvasWidthPixels, int initialCanvasHeightPixels,
			long archiveFrameCacheRefreshIntervalMillis, GlobalSettings globalSettings) {
		this(cameraConfig, cache, options, clock, initialCanvasWidthPixels, initialCanvasHeightPixels,
				archiveFrameCacheRefreshIntervalMillis, globalSettings, null);
	}

	// astronomy (Item 0's shared-instance architecture - see CameraAstronomy's own class comment):
	// when non-null, every render call mutates this SAME instance (via CameraImageDispatch's
	// matching overload) instead of constructing fresh ObservationTime/ObserverLocation/Sun/Moon/
	// Planets/Stars on every single call - the fix for the confirmed per-render-tick object churn
	// this controller's own renderCurrentFrame()/renderFastPtzPreview() calls used to cause, up to
	// 4 times a second while playing. Null (every constructor above) preserves this class's
	// original always-fresh-construction behavior exactly - every existing caller/test is
	// unaffected. **Still open, flagged rather than silently left undone**: wiring a REAL
	// CameraAstronomy through from ui.AppController/ui.ControlPanel (which currently own star-
	// catalog loading and tier selection via Options.stars, not yet CameraAstronomy.setStarMode(...))
	// is a separate, deliberate follow-up - see docs/tasks.md's Item 0 entry. This constructor and
	// the render-method wiring below are the tested, ready-to-use capability; the call site that
	// actually constructs and passes a live CameraAstronomy from the app's real star catalog does
	// not exist yet.
	public PreviewController(CameraConfig cameraConfig, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, int initialCanvasWidthPixels, int initialCanvasHeightPixels,
			long archiveFrameCacheRefreshIntervalMillis, GlobalSettings globalSettings, CameraAstronomy astronomy) {
		if (cameraConfig == null)
			throw new IllegalArgumentException("cameraConfig must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		if (clock == null)
			throw new IllegalArgumentException("clock must not be null");
		requirePositive(initialCanvasWidthPixels, "initialCanvasWidthPixels");
		requirePositive(initialCanvasHeightPixels, "initialCanvasHeightPixels");

		this.cameraConfig = cameraConfig;
		this.cache = cache;
		this.options = options;
		this.clock = clock;
		this.canvasWidthPixels = initialCanvasWidthPixels;
		this.canvasHeightPixels = initialCanvasHeightPixels;
		this.globalSettings = globalSettings;
		this.astronomy = astronomy;
		this.archiveFrameCache = (cameraConfig.getType().getKind() == CameraType.Kind.REAL
				&& cameraConfig.getRealImageSource() != null && cache != null)
						? new ArchiveFrameCache(cameraConfig.getRealImageSource(), cache, WallClock.SYSTEM,
								archiveFrameCacheRefreshIntervalMillis)
						: null;
		// PTZ Virtual only, this round (direct user confirmation) - see MountTransformRuntime's own
		// class comment on why a Fixed Virtual camera isn't wired yet.
		this.mountRuntime = (cameraConfig.getType().getKind() == CameraType.Kind.VIRTUAL
				&& cameraConfig.getType().getOrientationMode() != OrientationMode.FIXED)
						? new MountTransformRuntime(cameraConfig)
						: null;
	}

	// Renders exactly one frame for "right now" per the simulated clock, using the current show/hide
	// and canvas-size state - the same composited canvas CameraImageDispatch always produces (never
	// null - see its own Result.getImage() contract), suitable for a direct PreviewPanel.setImage(...)
	// call each time this is invoked.
	//
	// When an image IS shown, the real photo's own pixel dimensions govern the canvas anyway (see
	// CameraImageDispatch/RealCameraScrubber), so canvasWidthPixels/canvasHeightPixels are passed
	// through unchanged - PreviewPanel's own aspect-preserving fit is correct there, since a real
	// photo's content can't be stretched.
	//
	// When NO image is shown, "letterbox the full sensor width into the window" would waste screen
	// space for any non-matching window aspect - instead this renders a SQUARE canvas at
	// max(canvasWidthPixels, canvasHeightPixels) (so pixels-per-degree, derived from that square's
	// one width, reflects the LONGER window dimension) and crops the centered
	// canvasWidthPixels x canvasHeightPixels rectangle back out of it before returning. That crop is
	// exactly "FOV = max(width,height), crop the sensor rather than letterbox": the longer axis keeps
	// its full max(w,h)-based field of view, the shorter axis is narrowed (cropped, not padded) - the
	// returned image always exactly matches the requested canvas size, so PreviewPanel never needs to
	// letterbox this case at all. Confined entirely to this method - CameraProjector/FrameCompositor's
	// core width-based pixels-per-mm convention is unchanged, since it's still correct for the
	// has-image case above.
	public BufferedImage renderCurrentFrame() throws Exception {
		// Marking mode always needs the real photo visible (nothing to click on otherwise) and never
		// wants any overlay drawn on top of it (the whole point of technique 2 is clicking a REAL
		// object exactly as it appears in the photo, not a computed prediction next to/on top of it -
		// see the retired ui.PlateSolveMarkingPanel's own original design comment). A FRESH, disposable
		// Options is built here rather than mutating the shared `options` field this controller was
		// constructed with: FrameCompositor.Options is a write-only fluent builder (no getters), so
		// there is no safe way to save its current toggle state before overriding it and restore it
		// afterward - every field not explicitly set on a fresh instance already defaults to "off"
		// except showSun/showMoon/showPlanets/showStars (default true), which are the only ones that
		// need forcing here.
		boolean showImageForThisRender = markingModeActive || imageShown;
		FrameCompositor.Options optionsForThisRender = markingModeActive
				? new FrameCompositor.Options().setShowSun(false).setShowMoon(false).setShowPlanets(false).setShowStars(false)
				: options;

		if (!showImageForThisRender) {
			int squareSize = Math.max(canvasWidthPixels, canvasHeightPixels);
			CameraImageDispatch.Result squareResult = CameraImageDispatch.composite(cameraConfig, cache,
					clock.getCurrentTimeMillis(), false, squareSize, squareSize, optionsForThisRender, activeEditSession,
					archiveFrameCache, globalSettings, layer1Caches, mountRuntime, astronomy);
			lastRenderArchiveScanTruncated = squareResult.isArchiveScanTruncated();
			lastRenderedEpochMillis = squareResult.getRenderedEpochMillis();
			BufferedImage square = squareResult.getImage();
			int cropX = (squareSize - canvasWidthPixels) / 2;
			int cropY = (squareSize - canvasHeightPixels) / 2;
			BufferedImage cropped = square.getSubimage(cropX, cropY, canvasWidthPixels, canvasHeightPixels);
			lastFullFrame = cropped;
			return cropped;
		}

		CameraImageDispatch.Result result = CameraImageDispatch.composite(cameraConfig, cache,
				clock.getCurrentTimeMillis(), true, canvasWidthPixels, canvasHeightPixels, optionsForThisRender,
				activeEditSession, archiveFrameCache, globalSettings, layer1Caches, mountRuntime, astronomy);
		lastRenderArchiveScanTruncated = result.isArchiveScanTruncated();
		lastRenderedEpochMillis = result.getRenderedEpochMillis();
		lastFullFrame = result.getImage();
		return lastFullFrame;
	}

	public void setMarkingModeActive(boolean markingModeActive) {
		this.markingModeActive = markingModeActive;
	}

	public boolean isMarkingModeActive() {
		return markingModeActive;
	}

	// A real user report: panning/tilting a PTZ camera felt like it updated once a second, because
	// every integration tick was triggering a full renderCurrentFrame() - EquirectangularSceneRenderer's
	// per-pixel inverse-projection loop, at full window resolution, on every tick. This renders a
	// small, fast preview instead (preserving the current canvas aspect ratio, capped so
	// max(width,height) == maxDimensionPixels - a "256 maxpect" thumbnail, the user's own term) and
	// composites it as a small inset over a COPY of the last real full-resolution frame, so most of
	// the screen stays crisp while only the small inset updates live during a drag. Falls back to a
	// plain full render if no previous frame exists yet (e.g. the very first frame after opening a PTZ
	// camera, before renderCurrentFrame() has ever been called). ui.ControlPanel's own
	// tickPtzOrientation() is what decides WHEN to call this vs. a normal full render (while dragging
	// vs. on release) - this method only knows how to render cheaply, not when that's appropriate.
	public BufferedImage renderFastPtzPreview(int maxDimensionPixels) throws Exception {
		if (maxDimensionPixels <= 0)
			throw new IllegalArgumentException("maxDimensionPixels must be positive");
		if (lastFullFrame == null)
			return renderCurrentFrame();

		int fullWidth = lastFullFrame.getWidth();
		int fullHeight = lastFullFrame.getHeight();
		double scale = maxDimensionPixels / (double) Math.max(fullWidth, fullHeight);
		int smallWidth = Math.max(1, (int) Math.round(fullWidth * scale));
		int smallHeight = Math.max(1, (int) Math.round(fullHeight * scale));

		CameraImageDispatch.Result smallResult = CameraImageDispatch.composite(cameraConfig, cache,
				clock.getCurrentTimeMillis(), imageShown, smallWidth, smallHeight, options, activeEditSession,
				archiveFrameCache, globalSettings, layer1Caches, mountRuntime, astronomy);

		BufferedImage composite = new BufferedImage(fullWidth, fullHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = composite.createGraphics();
		g2d.drawImage(lastFullFrame, 0, 0, null);
		int insetMargin = 8;
		int insetX = fullWidth - smallWidth - insetMargin;
		int insetY = fullHeight - smallHeight - insetMargin;
		g2d.drawImage(smallResult.getImage(), insetX, insetY, null);
		g2d.setColor(Color.WHITE);
		g2d.drawRect(insetX, insetY, smallWidth - 1, smallHeight - 1);
		g2d.dispose();

		return composite;
	}

	// True when the most recent renderCurrentFrame() call's archive scan was cut short by
	// DirectoryCache's circuit breaker - see CameraImageDispatch.Result.isArchiveScanTruncated().
	// PreviewWindow uses this to show a one-time "too many archived images to synchronize yet"
	// notice rather than crashing or silently under-scanning forever.
	public boolean isArchiveScanTruncated() {
		return lastRenderArchiveScanTruncated;
	}

	// The timestamp the most recent renderCurrentFrame() call actually rendered against - for a
	// shown Real camera, the snapped archived frame's own filename-derived timestamp (task 0.6's
	// scrub algorithm), not necessarily clock.getCurrentTimeMillis() itself. ui.ControlPanel's Time
	// tab uses this to keep its date/time spinners honest about what's actually on screen, rather
	// than only ever showing whatever they were constructed with.
	public long getLastRenderedEpochMillis() {
		return lastRenderedEpochMillis;
	}

	// The "app mode" live-editing bridge (task "camera-viewing app mode"): when set, this camera's
	// orientation/location render from the session's PENDING values instead of its persisted
	// CalibrationHistory - the same session the control panel's Location tab and an open
	// CalibrationWindow (if any) are editing, so a live edit from either place is reflected in the
	// very next render here. Null (the default) preserves this class's original, CameraImageDispatch-
	// only behavior exactly. Not validated against getCameraConfig() - callers (AppController) are
	// responsible for only setting a session that actually belongs to this controller's own camera.
	public void setActiveEditSession(PlateSolveSession activeEditSession) {
		this.activeEditSession = activeEditSession;
	}

	public PlateSolveSession getActiveEditSession() {
		return activeEditSession;
	}

	public CameraConfig getCameraConfig() {
		return cameraConfig;
	}

	// Null for a camera that's never mount-eligible or isn't PTZ (see the field's own comment).
	// ui.AppController's switch/exit hook uses this to commit a still-engaged mount's live computed
	// orientation before the camera is persisted/replaced - "even switching cameras should turn it
	// off" (CLAUDE.md). ui.ControlPanel's Camera Mount tab uses this to reach the live transform
	// instances (tracking rate, per-axis locks) it doesn't otherwise have access to.
	public MountTransformRuntime getMountRuntime() {
		return mountRuntime;
	}

	// Null unless this controller was built via the CameraAstronomy-accepting constructor - see
	// that constructor's own comment.
	public CameraAstronomy getAstronomy() {
		return astronomy;
	}

	public SimulatedClock getClock() {
		return clock;
	}

	public boolean isImageShown() {
		return imageShown;
	}

	public void setImageShown(boolean imageShown) {
		this.imageShown = imageShown;
	}

	public int getCanvasWidthPixels() {
		return canvasWidthPixels;
	}

	public int getCanvasHeightPixels() {
		return canvasHeightPixels;
	}

	// A real window is resizable - the next renderCurrentFrame() call picks up the new size.
	public void setCanvasSize(int widthPixels, int heightPixels) {
		requirePositive(widthPixels, "widthPixels");
		requirePositive(heightPixels, "heightPixels");
		this.canvasWidthPixels = widthPixels;
		this.canvasHeightPixels = heightPixels;
	}

	private static void requirePositive(int value, String name) {
		if (value <= 0)
			throw new IllegalArgumentException(name + " must be positive");
	}
}
