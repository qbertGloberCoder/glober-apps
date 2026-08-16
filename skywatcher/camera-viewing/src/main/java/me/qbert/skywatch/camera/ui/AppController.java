package me.qbert.skywatch.camera.ui;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.TimeZone;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.clock.SimulatedClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraLibrary;
import me.qbert.skywatch.camera.config.GlobalSettings;
import me.qbert.skywatch.camera.config.GlobalSettingsStore;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
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

// The headless-testable coordinator behind full interactive "app mode" (no CLI flags per launch -
// direct user instruction): owns the CameraLibrary plus everything that should persist ACROSS a
// camera switch rather than being torn down and rebuilt - the SimulatedClock (so play/pause state
// and "now" survive switching cameras), the FrameCompositor.Options (so an enabled graticule/OSD/
// etc. toggle stays on when you switch cameras, rather than resetting), and the DirectoryCache
// (already internally partitioned by absolute directory path - see source.DirectoryCache - so one
// shared instance safely serves every camera's own archive directory, no per-camera instance
// needed).
//
// switchToCamera(...) is what "switch cameras without relaunching the app" (the user's own
// complaint about the CLI-flags-per-launch model) actually means: it builds a fresh
// PreviewController for the newly-active camera against the SAME shared clock/options/cache,
// replacing (not disposing) whatever was active before - ui.ControlPanel/ui.AppCommand rebind the
// existing PreviewWindow to the new controller rather than opening a second window.
public final class AppController {
	private static final int DEFAULT_CANVAS_WIDTH_PIXELS = 900;
	private static final int DEFAULT_CANVAS_HEIGHT_PIXELS = 700;

	private final CameraLibrary library;
	private final DirectoryCache cache;
	private final FrameCompositor.Options options;
	private final SimulatedClock clock;
	private final long archiveFrameCacheRefreshIntervalMillis;
	private final GlobalSettings settings;
	// Where saveSettings() persists - null (the two shorter constructors below) means "in-memory
	// only, nothing to write to" - safe for the existing tests, which never call saveSettings().
	private final File settingsFile;

	private String activeCameraName;
	private PreviewController activePreviewController;

	public AppController(CameraLibrary library, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock) {
		this(library, cache, options, clock, ArchiveFrameCache.DEFAULT_REFRESH_INTERVAL_MILLIS);
	}

	// archiveFrameCacheRefreshIntervalMillis - see PreviewController's matching overload/comment;
	// passed through to every PreviewController this coordinator builds in switchToCamera(...). The
	// overload above keeps the previous default for every existing caller/test.
	public AppController(CameraLibrary library, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, long archiveFrameCacheRefreshIntervalMillis) {
		this(library, cache, options, clock, archiveFrameCacheRefreshIntervalMillis, new GlobalSettings(), null);
	}

	// settings/settingsFile - CLAUDE.md's "separate the persistence of the camera settings from the
	// 'global' settings": the operator's own "My Location"/world-model choice, loaded once here
	// (typically via GlobalSettingsStore.loadOrDefault(...)) rather than duplicated per camera.
	// Threaded into every PreviewController this coordinator builds (switchToCamera(...)) so a PTZ
	// Virtual camera's "use my locale" location setting can actually resolve - see
	// config.ObserverLocationSetting.resolve(...). The two shorter constructors above keep their
	// exact original behavior: a fresh, unpersisted GlobalSettings, matching this class's own
	// "unconfigured until explicitly set" stance elsewhere.
	public AppController(CameraLibrary library, DirectoryCache cache, FrameCompositor.Options options,
			SimulatedClock clock, long archiveFrameCacheRefreshIntervalMillis, GlobalSettings settings,
			File settingsFile) {
		if (library == null)
			throw new IllegalArgumentException("library must not be null");
		if (cache == null)
			throw new IllegalArgumentException("cache must not be null");
		if (options == null)
			throw new IllegalArgumentException("options must not be null");
		if (clock == null)
			throw new IllegalArgumentException("clock must not be null");
		if (settings == null)
			throw new IllegalArgumentException("settings must not be null");
		this.library = library;
		this.cache = cache;
		this.options = options;
		this.clock = clock;
		this.archiveFrameCacheRefreshIntervalMillis = archiveFrameCacheRefreshIntervalMillis;
		this.settings = settings;
		this.settingsFile = settingsFile;
	}

	public GlobalSettings getSettings() {
		return settings;
	}

	// A no-op when this coordinator was built without a settingsFile (the two shorter constructors) -
	// safe for existing tests/callers, which never rely on persistence. ControlPanel's "My Location"
	// tab calls this after every change; AppCommand always supplies a real file, so interactive app
	// mode always persists.
	public void saveSettings() throws IOException {
		if (settingsFile != null)
			GlobalSettingsStore.save(settings, settingsFile);
	}

	public CameraLibrary getLibrary() {
		return library;
	}

	// Exposed so ControlPanel can build a CalibrationController for the "Calibrate..." button using
	// the SAME shared cache every camera's own PreviewController already uses - DirectoryCache is
	// internally partitioned by absolute directory path, so one shared instance safely serves both.
	public DirectoryCache getCache() {
		return cache;
	}

	public SimulatedClock getClock() {
		return clock;
	}

	public FrameCompositor.Options getOptions() {
		return options;
	}

	public List<String> listCameraNames() {
		return library.listCameraNames();
	}

	public void addCamera(String name, CameraConfig config) throws IOException {
		library.save(name, config);
	}

	public void removeCamera(String name) {
		library.remove(name);
		if (name.equals(activeCameraName)) {
			activeCameraName = null;
			activePreviewController = null;
		}
	}

	public String getActiveCameraName() {
		return activeCameraName;
	}

	// Null until switchToCamera(...) has been called at least once - "no camera selected" is the
	// app's own starting state (direct user instruction), not an error.
	public PreviewController getActivePreviewController() {
		return activePreviewController;
	}

	public boolean hasActiveCamera() {
		return activePreviewController != null;
	}

	// Reuses the currently active controller's own canvas size when switching (so an already-
	// resized window doesn't snap back to the default on every switch) - falls back to a sensible
	// default only for the very first camera selected in a session.
	//
	// Auto-creates a plate.PlateSolveSession for the newly-active camera (Fixed cameras only - see
	// autoCreateEditSession(...) below) and wires it into the new PreviewController via
	// setActiveEditSession(...), so "manage settings in real time" (direct user instruction) is
	// true from the moment a camera is opened: the control panel's Location tab and an opened
	// CalibrationWindow for THIS camera both edit the SAME session, and this controller's very next
	// render reflects it - no separate "start editing" step needed.
	public PreviewController switchToCamera(String name) throws IOException {
		if (name == null || name.isEmpty())
			throw new IllegalArgumentException("name must not be empty");

		persistActiveCameraIfPtz();

		int width = activePreviewController != null ? activePreviewController.getCanvasWidthPixels()
				: DEFAULT_CANVAS_WIDTH_PIXELS;
		int height = activePreviewController != null ? activePreviewController.getCanvasHeightPixels()
				: DEFAULT_CANVAS_HEIGHT_PIXELS;

		CameraConfig camera = library.load(name);
		activeCameraName = name;
		activePreviewController = new PreviewController(camera, cache, options, clock, width, height,
				archiveFrameCacheRefreshIntervalMillis, settings);
		activePreviewController.setActiveEditSession(autoCreateEditSession(name, camera));
		return activePreviewController;
	}

	// PTZ cameras have no Save/Revert - CLAUDE.md's "Orientation editing" section documents the
	// intended persistence model directly: current orientation/location auto-persist on app exit and
	// on camera switch, full stop, no explicit-Save checkpoint in between. Called both here (before
	// replacing the active controller) and from persistActiveCameraOnExit() below (the app's own
	// closing hook) - a Fixed camera's current calibration already persists explicitly via
	// PlateSolveSession.save(...), so this is deliberately a no-op for anything but PTZ.
	//
	// "Even switching cameras should turn it off" (CLAUDE.md) - if the outgoing camera's mount is
	// still engaged, commit its LIVE computed orientation (not the stale lock/engage-time snapshot)
	// via MountTransformRuntime.disengageForCameraSwitch(...) before it's persisted, using the exact
	// time/location that camera was actually rendering against.
	private void persistActiveCameraIfPtz() throws IOException {
		if (activePreviewController == null || activeCameraName == null)
			return;
		CameraConfig camera = activePreviewController.getCameraConfig();
		if (camera.getType().getOrientationMode() != OrientationMode.FIXED) {
			MountTransformRuntime mountRuntime = activePreviewController.getMountRuntime();
			if (mountRuntime != null) {
				try {
					ObservationTime time = new ObservationTime();
					time.initTime(TimeZone.getTimeZone("UTC"));
					time.setUnixTime(activePreviewController.getClock().getCurrentTimeMillis());

					ObserverLocationSetting locationSetting = camera.getCurrentLocation().resolve(settings);
					ObserverLocation location = new ObserverLocation();
					location.setGeoLocation(locationSetting.getLatitude(), locationSetting.getLongitude());

					mountRuntime.disengageForCameraSwitch(camera, time, location);
				} catch (me.qbert.skywatch.exception.UninitializedObject e) {
					// Cannot actually happen - initTime(...) is always called immediately before
					// setUnixTime(...) above - but ObservationTime declares it as a checked exception,
					// so it needs a home; wrapped unchecked rather than widening this class's whole
					// IOException-only throws chain (switchToCamera(...)/persistActiveCameraOnExit())
					// for something structurally impossible.
					throw new IllegalStateException("unexpected uninitialized ObservationTime", e);
				}
			}
			library.save(activeCameraName, camera);
		}
	}

	// The app-is-closing half of the auto-persist contract above - wired into ControlPanel's existing
	// windowClosed(...) handler (which already disposes the preview window there), not a new listener
	// of its own. Swallows IOException into a printed stack trace rather than propagating it - by the
	// time the app is closing there is no sensible way to surface a dialog/retry to the user, and a
	// failed save here shouldn't block the window from actually closing.
	public void persistActiveCameraOnExit() {
		try {
			persistActiveCameraIfPtz();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// PlateSolveSession itself rejects PTZ cameras and cameras with no calibration entry yet (see
	// its own constructor) - both are already impossible for anything this pass's CameraEditDialog/
	// CameraConfigArgs.buildRealCamera(...) can produce (Real cameras are always Fixed, and both
	// always append an initial calibration entry), but this checks defensively rather than assuming
	// - a camera added some other way (e.g. hand-edited .properties file) could still violate either.
	private PlateSolveSession autoCreateEditSession(String name, CameraConfig camera) {
		if (camera.getType().getOrientationMode() != OrientationMode.FIXED)
			return null;
		if (camera.getCalibrationHistory().isEmpty())
			return null;
		return new PlateSolveSession(camera, library.fileFor(name));
	}
}
