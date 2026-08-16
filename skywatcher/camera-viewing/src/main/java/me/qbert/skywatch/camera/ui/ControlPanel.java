package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import javax.swing.WindowConstants;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.impl.SolarObjects;
import me.qbert.skywatch.camera.catalog.StarCatalogLoader;
import me.qbert.skywatch.camera.catalog.StarCatalogTier;
import me.qbert.skywatch.camera.catalog.StarCoordinate;
import me.qbert.skywatch.camera.catalog.StarVisibilityOverrides;
import me.qbert.skywatch.camera.clock.WallClock;
import me.qbert.skywatch.camera.config.CameraConfig;
import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.config.GlobalSettings;
import me.qbert.skywatch.camera.config.ObserverLocationSetting;
import me.qbert.skywatch.camera.config.OrientationMode;
import me.qbert.skywatch.camera.config.TimezoneCatalog;
import me.qbert.skywatch.camera.orientation.MountControl;
import me.qbert.skywatch.camera.orientation.MountTransformRuntime;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.orientation.TrackingRate;
import me.qbert.skywatch.camera.plate.DistortionSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveFitter;
import me.qbert.skywatch.camera.plate.PlateSolveMark;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;
import me.qbert.skywatch.camera.projection.FisheyeProjection;
import me.qbert.skywatch.camera.projection.RectilinearProjection;
import me.qbert.skywatch.camera.render.ColorPresets;
import me.qbert.skywatch.camera.render.ColorScheme;
import me.qbert.skywatch.camera.render.EclipticAnalemmaMode;
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

// The "control panel" the user asked for directly: a single, live-updating window for managing
// cameras and render settings without relaunching the app. Implemented as a JFrame rather than the
// literal JDialog the user's own phrase ("modeless dialog control box") suggested - a JDialog needs
// an owner Window, but this window has to exist independently BEFORE any camera (and therefore any
// PreviewWindow) is selected, per the user's own "start with no camera selected" instruction. A
// JFrame is the right Swing tool for an independent top-level window; "modeless" (never blocking
// other windows) falls out for free, since nothing in Swing is modal unless you explicitly build a
// modal JDialog (CameraEditDialog does, deliberately, for its one-shot Add/Edit form).
//
// Deliberately thin, same reasoning as PreviewWindow/CalibrationWindow: every real decision lives
// in AppController - and like every other Window subclass in this module, this one throws
// HeadlessException at construction in this module's own display-less test environment, so it was
// written and reviewed, not run (see docs/tasks.md's verification note for this round).
//
// Round two - the tabbed redesign [CLAUDE.md's "control panel redesign" round]: a non-tab top row
// (camera selector/Add/Edit/Remove/Open - "Calibrate..." retired, orientation is now a tab) plus
// six tabs: My Location, Camera Location, Camera Orientation, Time, Rendering, Watched Object.
// CalibrationWindow is NOT deleted (the "calibrate" CLI subcommand still needs it independently of
// app mode) but is no longer opened from here - its CalibrationPanel widget is instead embedded
// directly in the Camera Orientation tab, wired to the same active PlateSolveSession as before.
public final class ControlPanel extends JFrame {
	private static final long serialVersionUID = 1L;

	private final AppController appController;
	private final JComboBox<String> cameraList = new JComboBox<>();
	private PreviewWindow previewWindow;

	// Loaded once (not per-tier-change) so the Rendering tab's tier selector can cheaply re-filter
	// in memory [CLAUDE.md's Part I wiring pass] rather than re-reading stars.db from the classpath
	// on every change; also backs the Watched Object tab's star-by-name lookup, searched against the
	// FULL catalog rather than whichever tier happens to be selected for rendering - a deliberate UI
	// simplification over the CLI's stricter "only within the loaded --stars tier" behavior, since a
	// user typing a star name has no reason to expect it to depend on an unrelated rendering toggle.
	private final List<StarCoordinate> starCatalog;
	private final StarCatalogLoader starCatalogLoader = new StarCatalogLoader();

	// "My Location" tab's own derived suggestion. null until the operator actually touches the
	// lat/lon spinners (or, now, until a previously-SAVED location is loaded at construction - see
	// buildMyLocationTab()); the OSD timezone default falls back to ZoneId.systemDefault() until
	// then [Part I].
	private TimezoneCatalog timezoneCatalog;
	private ZoneId myTimezone;

	private final JCheckBox graticule = new JCheckBox("Graticule");
	// Item 5 ("Graticule redesign") - renamed field (was "celestialEquator"): now also draws the new
	// RA=0 celestial prime meridian alongside the original Dec=0 celestial equator - see
	// FrameCompositor.Options.setShowCelestialOrigin(...).
	private final JCheckBox celestialOrigin = new JCheckBox("Celestial origin (RA=0 / Dec=0)");
	private final JCheckBox observerCardinalCross = new JCheckBox("Observer cardinal cross (local meridian / prime vertical)");
	private final JCheckBox boresightReferenceLines = new JCheckBox("Camera boresight RA/Dec reference lines");
	private final JCheckBox osd = new JCheckBox("On-screen display");
	private final JCheckBox labels = new JCheckBox("Labels");
	private final JComboBox<String> colorScheme = new JComboBox<>(new String[] { "default", "deuteranopia", "high-contrast" });
	private final JComboBox<String> starTier = new JComboBox<>(new String[] { "main", "named", "all", "visible" });
	private final JSpinner fontSize = new JSpinner(new SpinnerNumberModel(16, 4, 200, 1));
	// Wires FrameCompositor.Options' own already-existing sky/ground toggles, which previously had
	// zero UI exposure anywhere - defaults mirror each Options field's own default exactly. Only
	// visibly meaningful when Layer 1 has no image (LayerVisibility force-disables sky/ground
	// whenever this camera's own image occupies Layer 1) - expected/pre-existing, not special-cased
	// here.
	private final JCheckBox skyToggle = new JCheckBox("Sky", true);
	private final JCheckBox hideGround = new JCheckBox("Hide ground (as seen from space)");
	private final JCheckBox groundOverObjects = new JCheckBox("Ground paints over objects");
	// Layer 1 show/hide - a real user report: this state (PreviewController.isImageShown()/
	// setImageShown(...)) already existed but was only ever reachable from PreviewWindow's own
	// separate checkbox, never from ControlPanel. Kept in sync with PreviewWindow's checkbox via the
	// existing timeFieldsSync poll (see the constructor) rather than any direct wiring between the
	// two windows - both just read/write the same PreviewController instance.
	private final JCheckBox showCameraImage = new JCheckBox("Show camera image");
	private boolean updatingShowCameraImageProgrammatically;
	// Per-object-type visibility - a real user report ("start adding the additional layer controls").
	// All default true, matching FrameCompositor.Options' own defaults for these new fields exactly -
	// preserves existing behavior until the operator actually unchecks one.
	private final JCheckBox showSun = new JCheckBox("Sun", true);
	private final JCheckBox showMoon = new JCheckBox("Moon", true);
	private final JCheckBox showPlanets = new JCheckBox("Planets", true);
	private final JCheckBox showStars = new JCheckBox("Stars", true);

	// One axis at a time (direct user instruction, matching task 1.3b's original design: "the user
	// would have to alternately slide latitude and longitude separately and could not move
	// diagonally, but it was quick and effective") - whole-degree sliders paired with a text field
	// for precise entry. Disabled until a camera with an active edit session is open - see
	// setLocationControlsEnabled(...).
	private final JSlider latitudeSlider = new JSlider(-90, 90, 0);
	private final JTextField latitudeField = new JTextField(6);
	private final JSlider longitudeSlider = new JSlider(-180, 180, 0);
	private final JTextField longitudeField = new JTextField(6);
	// JSlider/JTextField.setValue(...)/setText(...) fire the same listeners a user drag/keystroke
	// does (the same gotcha CalibrationPanel's updatingProgrammatically guard exists for) -
	// refreshLocationControls() needs this to avoid re-adjusting the session it's syncing FROM.
	private boolean updatingLocationProgrammatically;

	// Camera Orientation tab - the SAME CalibrationPanel widget CalibrationWindow uses, embedded
	// directly rather than rebuilt (it's already a plain, reusable JPanel - see its own class
	// comment). Deliberately no embedded preview image here - the already-open PreviewWindow is the
	// visual feedback for a live edit, avoiding a second, redundant render surface.
	private final CalibrationPanel orientationPanel = new CalibrationPanel();
	private boolean updatingOrientationProgrammatically;

	// Camera Pan/Tilt tab - PTZ cameras only (a real user report: this used to REPLACE the Camera
	// Orientation tab's own numeric controls for a PTZ camera, which felt like a regression and hid
	// the barrel distortion spinners with nowhere else to reach them - this is now its own separate
	// tab instead, and the Camera Orientation tab above always shows the normal CalibrationPanel for
	// every camera kind). tabs/panTiltTabIndex let refreshOrientationControls() grey out this whole
	// tab (not just the panel inside it) when the active camera isn't PTZ.
	private final JTabbedPane tabs = new JTabbedPane();
	private int panTiltTabIndex = -1;
	private final PtzOrientationPanel ptzOrientationPanel = new PtzOrientationPanel();
	// Camera Mount tab - the missing equatorial-mount/geolocation-stabilizer wiring, see orientation.
	// MountTransformRuntime's own class comment. PTZ Virtual cameras only, this round (same gate as
	// Camera Pan/Tilt above) - a Fixed Virtual camera's mount would need a new commit-into-
	// CalibrationHistory path that doesn't exist yet, a deliberate follow-up.
	private int mountTabIndex = -1;
	private final MountControlPanel mountControlPanel = new MountControlPanel();
	// Plate Solve tab (sprint Item 2, Phase 4) - technique 2's click-and-mark UI [CLAUDE.md's "Plate
	// solving is Real archived-camera-only"], Real cameras with an archive only - never PTZ (PTZ is
	// always Virtual) and never a Live+recorded-only Real camera with no archive template configured.
	// markingController is rebuilt fresh on every camera switch (refreshMarkingControls()) rather
	// than reused - null whenever the active camera isn't eligible.
	//
	// A direct user request redesigned WHERE marking clicks happen: the small dedicated
	// ui.PlateSolveMarkingPanel embedded here (retired) is replaced by clicking directly in the
	// larger, user-resizable PreviewWindow (ui.PreviewWindow.setMarkingModeActive(...)) - better click
	// accuracy was the user's own explicit reasoning. This tab now only holds the object-identity
	// selector, the aiming-circle radius, the mark list, and the solve buttons - no image canvas or
	// frame-navigation controls of its own anymore (marking always uses whatever frame the Preview
	// window currently shows).
	private int plateSolveTabIndex = -1;
	private PlateSolveMarkingController markingController;
	private final JCheckBox markingModeToggle = new JCheckBox("Marking mode - click objects in the Preview window");
	private final JComboBox<String> markObjectKind = new JComboBox<>(watchedObjectKindOptions());
	private final JTextField markStarName = new JTextField(10);
	private final JSlider aimingCircleRadius = new JSlider(2, 80, 12);
	private final DefaultListModel<String> markListModel = new DefaultListModel<>();
	private final JList<String> markList = new JList<>(markListModel);
	private final JButton solveOrientationButton = new JButton("Solve orientation / zoom...");
	private final JButton solveDistortionButton = new JButton("Solve distortion...");
	// System.nanoTime() of the previous pan/tilt integration tick, -1 before the first tick - see
	// tickPtzOrientation().
	private long ptzLastTickNanos = -1;
	// Whether the previous tick found either shuttle deflected - tickPtzOrientation() uses the
	// falling edge (was dragging, now isn't - i.e. release) to trigger exactly one full-quality
	// render, replacing the fast preview shown while actually dragging.
	private boolean ptzWasDragging;
	private static final int PTZ_FAST_PREVIEW_MAX_DIMENSION_PIXELS = 256;

	// Time tab.
	private final ShuttleControl shuttle = new ShuttleControl();
	// Item 4 (sprint backlog review) - each spinner's model is extended one slot past its normal
	// bound in each direction it can roll, so the spin buttons stay clickable at the edge instead of
	// disabling there; TimeFieldEditor's matching applyXxx(...) detects the overflow/underflow slot
	// and rolls into the next/previous bigger unit instead of literally setting an invalid field
	// value - see that class's own comment for the full design. Year is the one exception (nothing
	// bigger than a year to roll into) - it keeps a plain clamped [1990,2050] range, per the sprint
	// plan's explicit instruction (this module's own J2000-based solar calculators aren't meaningful
	// outside that window).
	private final JSpinner yearSpinner = new JSpinner(new SpinnerNumberModel(2026, 1990, 2050, 1));
	private final JSpinner monthSpinner = new JSpinner(new SpinnerNumberModel(1, 0, 13, 1));
	private final JSpinner daySpinner = new JSpinner(new SpinnerNumberModel(1, 0, 32, 1));
	private final JSpinner hourSpinner = new JSpinner(new SpinnerNumberModel(0, -1, 24, 1));
	private final JSpinner minuteSpinner = new JSpinner(new SpinnerNumberModel(0, -1, 60, 1));
	// Per-second granularity - most useful for a Virtual camera or a Real camera with Layer 1
	// hidden, where render time is continuous rather than snapped to an archived frame (a real
	// user report: the fields only ever supported minute granularity). No new conditional
	// visibility - always present/enabled, same as the existing fields (a shown Real camera simply
	// snaps this away like it already does for minutes).
	private final JSpinner secondSpinner = new JSpinner(new SpinnerNumberModel(0, -1, 60, 1));
	private final JButton timePlayPause = new JButton("Pause");
	// Jumps back to real wall-clock "now" after scrubbing away from it - a real user report.
	private final JButton timeNow = new JButton("Now");
	private boolean updatingTimeFieldsProgrammatically;

	// My Location tab.
	private final JSpinner myLatitude = new JSpinner(new SpinnerNumberModel(0.0, -90.0, 90.0, 0.1));
	private final JSpinner myLongitude = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 0.1));
	private final JLabel nearestTimezoneLabel = new JLabel("Nearest timezone: (not yet set)");

	// Watched Object tab.
	private static final String STAR_BY_NAME_LABEL = "Star (by name)";
	private final JComboBox<String> watchedObjectKind = new JComboBox<>(watchedObjectKindOptions());
	private final JTextField watchedStarName = new JTextField(12);
	private final JCheckBox crosshairEnabled = new JCheckBox("Crosshair");
	private final JSpinner crosshairOffsetHours = new JSpinner(new SpinnerNumberModel(-24.0, -100000.0, 100000.0, 1.0));
	// Item 8: previously configurable window-hours/interval-minutes, dropped per direct user
	// instruction - the mental model is simply "show me the last 24 hours," not "let me tune an
	// arbitrary window/interval" (the CLI's --watched-path-hours/--watched-path-interval-minutes
	// flags stay as-is for scripted/batch use - see cli.CameraConfigArgs, unaffected by this). Fixed
	// values now come from simply not overriding FrameCompositor.Options' own defaults (already
	// 24h/10min - see WatchedObjectPath.DEFAULT_TRAILING_WINDOW_MILLIS/DEFAULT_SAMPLE_INTERVAL_MILLIS)
	// rather than hardcoding a duplicate constant here.
	private final JCheckBox watchedPathEnabled = new JCheckBox("Watched object's path for the last 24 hours");
	// Item 5 - the "watched object" reference-line group (yellow), see render.Graticule.
	// paintWatchedObjectReferenceLines. Independent of both the crosshair and the path above - a
	// caller may want any combination of the three.
	private final JCheckBox watchedObjectReferenceLinesEnabled = new JCheckBox("RA/Dec reference lines");
	private final JComboBox<String> sunPathMode = new JComboBox<>(new String[] { "none", "ecliptic", "analemma" });
	private final JComboBox<String> moonPathMode = new JComboBox<>(new String[] { "none", "ecliptic", "analemma" });

	public ControlPanel(AppController appController) {
		super("camera-viewing control panel");
		if (appController == null)
			throw new IllegalArgumentException("appController must not be null");
		this.appController = appController;
		this.starCatalog = loadStarCatalog();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(buildCameraPanel(), BorderLayout.NORTH);

		tabs.addTab("My Location", buildMyLocationTab());
		tabs.addTab("Camera Location", buildCameraLocationTab());
		tabs.addTab("Camera Orientation", buildCameraOrientationTab());
		plateSolveTabIndex = tabs.getTabCount();
		tabs.addTab("Plate Solve", buildPlateSolveTab());
		panTiltTabIndex = tabs.getTabCount();
		tabs.addTab("Camera Pan/Tilt", buildCameraPanTiltTab());
		mountTabIndex = tabs.getTabCount();
		tabs.addTab("Camera Mount", buildCameraMountTab());
		tabs.addTab("Time", buildTimeTab());
		tabs.addTab("Rendering", buildRenderingTab());
		tabs.addTab("Watched Object", buildWatchedObjectTab());
		add(tabs, BorderLayout.CENTER);

		refreshCameraList();
		setLocationControlsEnabled(false);
		setOrientationControlsEnabled(false);
		showCameraImage.setEnabled(false);
		tabs.setEnabledAt(plateSolveTabIndex, false);
		pack();

		// The control panel is app mode's home base (it's what exists before any camera is
		// selected) - closing it ends the session, including any camera window it opened. Also the
		// app-is-closing half of the PTZ auto-persist contract (CLAUDE.md's "Orientation editing") -
		// a PTZ camera has no explicit Save button, so this is the only remaining place its current
		// orientation/location ever reaches disk.
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				appController.persistActiveCameraOnExit();
				if (previewWindow != null)
					previewWindow.dispose();
			}
		});

		// Keeps the Time tab's duplicate Play/Pause label AND date/time spinners from going stale -
		// a real user report: the spinners never reflected the actually-rendered frame's time at all,
		// stuck at their construction-time default (2026-01-01 00:00) forever, since nothing ever
		// wrote to them outside the constructor. Both this label and PreviewWindow's own Play/Pause
		// button operate on the same shared clock, so a click in either window needs this poll to stay
		// in sync with the other; the date/time fields need it for the same reason PLUS every tick of
		// ordinary playback (PreviewWindow's own 250ms render Timer advances the clock without ever
		// notifying this window directly).
		Timer timeFieldsSync = new Timer(500, e -> {
			updateTimePlayPauseLabel();
			refreshTimeFields();
			refreshShowCameraImageControl();
		});
		timeFieldsSync.start();

		// Short interval (unlike timeFieldsSync's 500ms above) so panning/tilting a PTZ camera feels
		// smooth rather than choppy - always ticking, not started/stopped per-drag, since
		// tickPtzOrientation() itself is a cheap no-op whenever nothing's actually being dragged.
		Timer ptzTimer = new Timer(50, e -> tickPtzOrientation());
		ptzTimer.start();
	}

	// "Sun" + "Moon" + all 8 planets (SolarObjects.OBJECT_LIST indices 1..8 - index 0 is "Sun" again,
	// deliberately skipped, matching CelestialObjectsLayer.paintPlanets(...)'s own iteration) +
	// "Star (by name)".
	private static String[] watchedObjectKindOptions() {
		String[] options = new String[SolarObjects.OBJECT_LIST.length + 2];
		options[0] = "Sun";
		options[1] = "Moon";
		int index = 2;
		for (int i = 1; i < SolarObjects.OBJECT_LIST.length; i++)
			options[index++] = SolarObjects.OBJECT_LIST[i].trim();
		options[index] = STAR_BY_NAME_LABEL;
		return options;
	}

	// The bundled classpath resource (src/main/resources/stars.db) is expected to always be
	// present - a missing/unreadable catalog here is a packaging error, not a recoverable runtime
	// condition, so it's wrapped and rethrown unchecked rather than given its own UI error path
	// (matching how PreviewWindow.refresh() treats an impossible-in-practice IOException).
	private List<StarCoordinate> loadStarCatalog() {
		try (InputStream stream = ControlPanel.class.getResourceAsStream("/stars.db")) {
			if (stream == null)
				throw new IOException("stars.db not found on classpath");
			List<StarCoordinate> base = starCatalogLoader.load(stream);
			File overridesFile = new File(System.getProperty("user.home"), ".camera-viewing/star-visibility-overrides.csv");
			List<StarCoordinate> overrides = StarVisibilityOverrides.load(overridesFile);
			return StarVisibilityOverrides.merge(base, overrides);
		} catch (IOException e) {
			throw new RuntimeException("failed to load the bundled star catalog", e);
		}
	}

	private JPanel buildCameraPanel() {
		JPanel panel = new JPanel();
		panel.setBorder(BorderFactory.createTitledBorder("Camera"));
		panel.add(new JLabel("Camera:"));
		panel.add(cameraList);

		JButton add = new JButton("Add...");
		add.addActionListener(e -> openEditDialog(null));
		panel.add(add);

		JButton edit = new JButton("Edit...");
		edit.addActionListener(e -> withSelectedCamera(this::openEditDialogFor));
		panel.add(edit);

		JButton remove = new JButton("Remove");
		remove.addActionListener(e -> withSelectedCamera(this::removeCamera));
		panel.add(remove);

		JButton open = new JButton("Open");
		open.addActionListener(e -> withSelectedCamera(this::openCamera));
		panel.add(open);

		return panel;
	}

	// CLAUDE.md's "separate the persistence of the camera settings from the 'global' settings":
	// loads from AppController.getSettings() at construction (so a previously-SAVED location
	// survives a restart, instead of always resetting to 0,0) and persists via
	// AppController.saveSettings() on every change - mirroring how camera edits already auto-persist
	// promptly. Feeds the OSD timezone default [Part I] once set; falls back to
	// ZoneId.systemDefault() until then.
	private JPanel buildMyLocationTab() {
		JPanel panel = new JPanel(new GridLayout(0, 2, 4, 4));
		panel.setBorder(BorderFactory.createTitledBorder("My location (used to suggest a timezone)"));

		GlobalSettings settings = appController.getSettings();
		if (settings.hasMyLocation()) {
			myLatitude.setValue(settings.getMyLatitude());
			myLongitude.setValue(settings.getMyLongitude());
		}

		panel.add(new JLabel("Latitude (deg)"));
		panel.add(myLatitude);
		panel.add(new JLabel("Longitude (deg)"));
		panel.add(myLongitude);
		panel.add(nearestTimezoneLabel);

		myLatitude.addChangeListener(e -> updateMyLocation());
		myLongitude.addChangeListener(e -> updateMyLocation());

		if (settings.hasMyLocation())
			updateMyLocation();

		return panel;
	}

	// Shared by "My Location", color scheme, and font size - all auto-persist into the same
	// GlobalSettings file on every change (see GlobalSettingsStore).
	private void persistGlobalSettings() {
		try {
			appController.saveSettings();
		} catch (IOException e) {
			showError("Failed to save global settings", e);
		}
	}

	private void updateMyLocation() {
		double latitude = (Double) myLatitude.getValue();
		double longitude = (Double) myLongitude.getValue();

		appController.getSettings().setMyLocation(latitude, longitude);
		persistGlobalSettings();

		try {
			if (timezoneCatalog == null)
				timezoneCatalog = TimezoneCatalog.loadFromClasspath();
			myTimezone = timezoneCatalog.nearestZoneTo(latitude, longitude);
			nearestTimezoneLabel.setText("Nearest timezone: " + myTimezone.getId());
		} catch (IOException e) {
			nearestTimezoneLabel.setText("Nearest timezone: (catalog unavailable - " + e.getMessage() + ")");
		}
	}

	// Task "app mode"'s live location editing: edits the ACTIVE camera's PlateSolveSession pending
	// location directly (PreviewController.setActiveEditSession(...) already wired this session
	// into rendering when the camera was opened - see AppController.switchToCamera(...)), so a drag
	// here is visible in the preview on the very next render, with no separate "apply"/"save" step.
	// Longitude/latitude are read from each other's CURRENT field value when adjusting one axis,
	// since PlateSolveSession.adjustLocation(...) always takes both together.
	private JPanel buildCameraLocationTab() {
		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.setBorder(BorderFactory.createTitledBorder("Location (active camera)"));

		JPanel latRow = new JPanel(new BorderLayout());
		latRow.add(new JLabel("Latitude"), BorderLayout.WEST);
		latRow.add(latitudeSlider, BorderLayout.CENTER);
		latRow.add(latitudeField, BorderLayout.EAST);
		panel.add(latRow);

		JPanel lonRow = new JPanel(new BorderLayout());
		lonRow.add(new JLabel("Longitude"), BorderLayout.WEST);
		lonRow.add(longitudeSlider, BorderLayout.CENTER);
		lonRow.add(longitudeField, BorderLayout.EAST);
		panel.add(lonRow);

		latitudeSlider.addChangeListener(e -> {
			if (updatingLocationProgrammatically)
				return;
			latitudeField.setText(String.valueOf(latitudeSlider.getValue()));
			applyLocationEdit(latitudeSlider.getValue(), currentLongitude());
		});
		latitudeField.addActionListener(e -> {
			if (updatingLocationProgrammatically)
				return;
			try {
				double lat = Double.parseDouble(latitudeField.getText().trim());
				// Guard the cross-widget slider mutation (item 3 fix) - without this, setValue(...)
				// synchronously re-enters the slider's own ChangeListener below, which stomps this
				// field's just-typed text back to a rounded int before applyLocationEdit(...) even
				// runs - the backend ends up correct either way (this call still fires with the
				// precise typed value), but the visible text snapped away from what was typed.
				updatingLocationProgrammatically = true;
				try {
					latitudeSlider.setValue((int) Math.round(lat));
				} finally {
					updatingLocationProgrammatically = false;
				}
				applyLocationEdit(lat, currentLongitude());
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Latitude must be a number", "Invalid value", JOptionPane.ERROR_MESSAGE);
			}
		});

		longitudeSlider.addChangeListener(e -> {
			if (updatingLocationProgrammatically)
				return;
			longitudeField.setText(String.valueOf(longitudeSlider.getValue()));
			applyLocationEdit(currentLatitude(), longitudeSlider.getValue());
		});
		longitudeField.addActionListener(e -> {
			if (updatingLocationProgrammatically)
				return;
			try {
				double lon = Double.parseDouble(longitudeField.getText().trim());
				// See latitudeField's matching listener above for why this guard is needed.
				updatingLocationProgrammatically = true;
				try {
					longitudeSlider.setValue((int) Math.round(lon));
				} finally {
					updatingLocationProgrammatically = false;
				}
				applyLocationEdit(currentLatitude(), lon);
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this, "Longitude must be a number", "Invalid value", JOptionPane.ERROR_MESSAGE);
			}
		});

		return panel;
	}

	// The active camera's fine-grained orientation/zoom/location editor - works for BOTH Fixed
	// cameras (through the existing plate.PlateSolveSession, unchanged) and PTZ cameras (a real user
	// report: an earlier round swapped this tab's whole widget out for PTZ instead of teaching it to
	// write directly to CameraConfig, which both felt like a regression and hid the barrel distortion
	// spinners with nowhere else to reach them - PTZ pan/tilt now lives in its own separate tab
	// instead, see buildCameraPanTiltTab()). isActiveCameraPtz() branches every callback; PTZ writes
	// straight to CameraConfig.setCurrentOrientation(...)/setCurrentLocation(...)/
	// setProjection(...withFocalLength(...)) with no session involved at all, matching this camera
	// type's documented "no Save/Revert, always just whatever it currently is" design.
	private JPanel buildCameraOrientationTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Orientation / zoom (active camera)"));
		panel.add(orientationPanel, BorderLayout.CENTER);

		orientationPanel.onOrientationChanged(() -> {
			if (updatingOrientationProgrammatically)
				return;
			Orientation orientation = new Orientation(orientationPanel.getAltitude(), orientationPanel.getAzimuth(),
					orientationPanel.getBarrelRoll());
			if (isActiveCameraPtz()) {
				appController.getActivePreviewController().getCameraConfig().setCurrentOrientation(orientation);
				refreshActivePreview();
				return;
			}
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null)
				return;
			session.adjustOrientation(orientation);
			refreshActivePreview();
		});
		orientationPanel.onZoomChanged(zoom -> {
			if (updatingOrientationProgrammatically)
				return;
			if (isActiveCameraPtz()) {
				CameraConfig camera = appController.getActivePreviewController().getCameraConfig();
				camera.setProjection(camera.getProjection().withFocalLength(zoom));
				refreshActivePreview();
				return;
			}
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null)
				return;
			session.adjustZoom(zoom);
			refreshActivePreview();
		});
		orientationPanel.onLocationChanged(() -> {
			if (updatingOrientationProgrammatically)
				return;
			if (isActiveCameraPtz()) {
				appController.getActivePreviewController().getCameraConfig().setCurrentLocation(
						ObserverLocationSetting.explicit(orientationPanel.getLatitude(), orientationPanel.getLongitude()));
				refreshActivePreview();
				refreshLocationControls(); // keep the Camera Location tab's sliders in sync
				return;
			}
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null)
				return;
			session.adjustLocation(orientationPanel.getLatitude(), orientationPanel.getLongitude());
			refreshActivePreview();
			refreshLocationControls();
		});
		// Distortion coefficients live directly ON the active camera's live CameraProjection instance
		// [CLAUDE.md's "Barrel distortion built into the lens hierarchy"] - no PlateSolveSession
		// involvement at all, for EITHER camera kind (distortableProjectionOrNull() resolves the
		// projection straight from the active camera, not through a session - see that method's own
		// comment). For a Fixed camera, Save/Revert falls out for free from the session's EXISTING
		// save()/revert() (both round-trip the whole CameraConfig, including its projection); a PTZ
		// camera's distortion, like everything else about it, is simply always-current (and, per the
		// "Distortion scope-gating" round, inert at render time regardless - distortionEnabled is
		// always false for any Virtual camera - so this mostly matters for reviewing/copying a value,
		// not for anything that visibly changes the render).
		orientationPanel.onDistortionChanged(() -> {
			if (updatingOrientationProgrammatically)
				return;
			AbstractCameraProjection projection = distortableProjectionOrNull();
			if (projection == null)
				return;
			projection.setDistortionCoefficients(orientationPanel.getDistortionA(), orientationPanel.getDistortionB(),
					orientationPanel.getDistortionC(), orientationPanel.getDistortionD());
			refreshActivePreview();
		});
		orientationPanel.onSave(() -> {
			// The button is disabled for PTZ (see refreshOrientationControls()'s
			// setSaveRevertEnabled(false) call) - this guard is defense in depth, not the primary gate.
			if (isActiveCameraPtz())
				return;
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null)
				return;
			try {
				session.save(WallClock.SYSTEM.currentTimeMillis());
				JOptionPane.showMessageDialog(this, "Saved.");
			} catch (Exception ex) {
				showError("Save failed", ex);
			}
		});
		orientationPanel.onRevert(() -> {
			if (isActiveCameraPtz())
				return;
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null)
				return;
			try {
				session.revert();
				refreshOrientationControls();
				refreshLocationControls();
				refreshActivePreview();
			} catch (Exception ex) {
				showError("Revert failed", ex);
			}
		});

		return panel;
	}

	// Sprint Item 2 (Phase 4) - technique 2's click-and-mark plate-solve tab. Real cameras with an
	// archive only - see requireRealCameraWithArchive(...) in PlateSolveMarkingController itself
	// (the same gate this tab's own enabled/disabled state mirrors via refreshMarkingControls()).
	// Deliberately its OWN dedicated tab rather than a toggle inside Camera Orientation, per the
	// sprint's own explicit design. Redesigned in a later round (see the field-level comment above
	// markingModeToggle) to click in the Preview window instead of a small canvas embedded here - this
	// tab now only holds identity/mark-list/solve controls.
	private JPanel buildPlateSolveTab() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));
		panel.setBorder(BorderFactory.createTitledBorder(
				"Plate solve - click real objects in the Preview window (Real cameras with an archive only)"));

		JPanel identityRow = new JPanel();
		identityRow.add(new JLabel("Object:"));
		identityRow.add(markObjectKind);
		identityRow.add(markStarName);
		identityRow.add(new JLabel("Aim circle radius:"));
		identityRow.add(aimingCircleRadius);
		panel.add(identityRow, BorderLayout.NORTH);

		JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
		centerPanel.add(markingModeToggle, BorderLayout.NORTH);
		centerPanel.add(new JLabel("<html>Enable marking mode, then click objects directly in the open Preview "
				+ "window - it shows the raw archived photo with every computed overlay hidden while marking "
				+ "mode is active.</html>"), BorderLayout.CENTER);
		panel.add(centerPanel, BorderLayout.CENTER);

		JPanel eastPanel = new JPanel(new BorderLayout(4, 4));
		eastPanel.add(new JLabel("Marks:"), BorderLayout.NORTH);
		markList.setVisibleRowCount(10);
		eastPanel.add(new JScrollPane(markList), BorderLayout.CENTER);
		JPanel solveButtons = new JPanel(new GridLayout(0, 1, 4, 4));
		solveButtons.add(solveOrientationButton);
		solveButtons.add(solveDistortionButton);
		eastPanel.add(solveButtons, BorderLayout.SOUTH);
		panel.add(eastPanel, BorderLayout.EAST);

		markObjectKind.addActionListener(
				e -> markStarName.setEnabled(STAR_BY_NAME_LABEL.equals(markObjectKind.getSelectedItem())));
		markStarName.setEnabled(false);

		aimingCircleRadius.addChangeListener(e -> {
			if (previewWindow != null)
				previewWindow.setAimingCircleRadiusPixels(aimingCircleRadius.getValue());
		});

		markingModeToggle.addActionListener(e -> {
			if (previewWindow == null) {
				markingModeToggle.setSelected(false);
				return;
			}
			previewWindow.setMarkingModeActive(markingModeToggle.isSelected());
		});

		solveOrientationButton.addActionListener(e -> solveOrientation());
		solveDistortionButton.addActionListener(e -> solveDistortion());

		return panel;
	}

	// A click in the Preview window while marking mode is active - wired ONCE, right after the
	// window's own one-time construction (see openCamera(...)), since ui.PreviewWindow is a single
	// long-lived instance for the whole app-mode session (rebound via switchTo(...), never replaced).
	// The mark's timestamp comes from PreviewController.getLastRenderedEpochMillis() - exactly the
	// timestamp marking mode's own forced-visible render just resolved (RealCameraScrubber's
	// snap-to-nearest-older-frame for a shown Real camera), the same value the Time tab already
	// displays - so a mark always lands on whatever moment is actually on screen, with no separate
	// "current marking frame" concept to keep in sync (see markingModeToggle's own field comment).
	private void handleMarkClick(double imagePixelX, double imagePixelY) {
		if (markingController == null || previewWindow == null)
			return;
		BufferedImage image = previewWindow.getDisplayedImage();
		PreviewController controller = appController.getActivePreviewController();
		if (image == null || controller == null)
			return;
		try {
			WatchedObject object = resolveSelectedMarkObject();
			markingController.addMarkAtTime(object, controller.getLastRenderedEpochMillis(), imagePixelX, imagePixelY,
					image.getWidth(), image.getHeight());
			refreshMarkList();
		} catch (Exception ex) {
			showError("Failed to add mark", ex);
		}
	}

	private WatchedObject resolveSelectedMarkObject() {
		String kind = (String) markObjectKind.getSelectedItem();
		if ("Sun".equals(kind))
			return WatchedObject.sun();
		if ("Moon".equals(kind))
			return WatchedObject.moon();
		if (STAR_BY_NAME_LABEL.equals(kind)) {
			String name = markStarName.getText().trim();
			for (StarCoordinate star : starCatalog)
				if (star.getName().equalsIgnoreCase(name))
					return WatchedObject.star(star);
			throw new IllegalArgumentException("no star named \"" + name + "\" found in the bundled catalog");
		}
		for (int i = 1; i < SolarObjects.OBJECT_LIST.length; i++)
			if (SolarObjects.OBJECT_LIST[i].trim().equalsIgnoreCase(kind))
				return WatchedObject.planet(i);
		throw new IllegalStateException("unreachable - unknown watched-object selection \"" + kind + "\"");
	}

	// Called after every camera switch/removal (openCamera(...)/removeCamera(...)) - rebuilds a
	// FRESH PlateSolveMarkingController for the newly-active camera (a fresh, empty mark set every
	// time, matching PlateSolveMarkSet's own deliberately append-only-within-one-session design; no
	// "clear marks" control exists for the same reason - switching away and back is how a mark set
	// resets) if it's a Real camera with an archive, gated the same way
	// PlateSolveMarkingController's own constructor gates it - checked here first so an ineligible
	// camera never even attempts construction.
	private void refreshMarkingControls() {
		markingController = null;
		markListModel.clear();
		markingModeToggle.setSelected(false);
		if (previewWindow != null)
			previewWindow.setMarkingModeActive(false);

		PreviewController controller = appController.getActivePreviewController();
		if (controller == null) {
			tabs.setEnabledAt(plateSolveTabIndex, false);
			return;
		}
		CameraConfig camera = controller.getCameraConfig();
		PlateSolveSession session = activeEditSessionOrNull();
		boolean eligible = session != null && camera.getType().getKind() == CameraType.Kind.REAL
				&& camera.getRealImageSource() != null && camera.getRealImageSource().getArchiveTemplate() != null;
		tabs.setEnabledAt(plateSolveTabIndex, eligible);
		if (!eligible)
			return;

		markingController = new PlateSolveMarkingController(session, appController.getCache());
	}

	private void refreshMarkList() {
		markListModel.clear();
		if (markingController == null)
			return;
		for (PlateSolveMark mark : markingController.getMarkSet().getMarks())
			markListModel.addElement(mark.getObject().getDisplayName() + " @ " + Instant.ofEpochMilli(mark.getEpochMillis()));
	}

	// Phase A - "Solve" -> preview -> accept, per the sprint's own design: the fitted result is
	// shown for review, NOT auto-committed - accepting writes into the session's pending values
	// (existing Save/Revert on the Camera Orientation tab, unchanged, is what actually persists it).
	private void solveOrientation() {
		if (markingController == null || previewWindow == null || previewWindow.getDisplayedImage() == null)
			return;
		if (markingController.getMarkSet().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Click at least one object in the photo first.");
			return;
		}
		try {
			int width = previewWindow.getDisplayedImage().getWidth();
			int height = previewWindow.getDisplayedImage().getHeight();
			PlateSolveFitter.Result result = markingController.solveOrientation(width, height, WallClock.SYSTEM.currentTimeMillis(),
					4000);
			Orientation o = result.getCandidate().getOrientation();
			String message = String.format(
					"Residual: %.2f px%naltitude=%.3f azimuth=%.3f roll=%.3f%nzoom=%.2fmm%n%nAccept these values?",
					result.getResidualPixels(), o.getAltitude(), o.getAzimuth(), o.getBarrelRoll(),
					result.getCandidate().getFocalLengthMillimeters());
			int choice = JOptionPane.showConfirmDialog(this, message, "Solve orientation / zoom result",
					JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				markingController.acceptOrientationResult(result);
				refreshOrientationControls();
				refreshActivePreview();
			}
		} catch (Exception ex) {
			showError("Solve orientation / zoom failed", ex);
		}
	}

	// Phase B - holds alt/az/roll/zoom fixed at whatever is currently pending (normally just
	// accepted from Phase A above) and fits only the four distortion coefficients. Accepting writes
	// directly onto the camera's live CameraProjection instance (no session involvement - matches
	// the Camera Orientation tab's own existing distortion wiring, see
	// PlateSolveMarkingController.acceptDistortionResult(...)'s own comment).
	private void solveDistortion() {
		if (markingController == null || previewWindow == null || previewWindow.getDisplayedImage() == null)
			return;
		if (markingController.getMarkSet().isEmpty()) {
			JOptionPane.showMessageDialog(this, "Click at least one object in the photo first.");
			return;
		}
		try {
			int width = previewWindow.getDisplayedImage().getWidth();
			int height = previewWindow.getDisplayedImage().getHeight();
			DistortionSolveFitter.Coefficients initialGuess = markingController.currentDistortionCoefficients();
			DistortionSolveFitter.Result result = markingController.solveDistortion(initialGuess, width, height,
					WallClock.SYSTEM.currentTimeMillis(), 4000);
			DistortionSolveFitter.Coefficients c = result.getCoefficients();
			String message = String.format("Residual: %.2f px%nA=%.6f B=%.6f C=%.6f D=%.6f%n%nAccept these values?",
					result.getResidualPixels(), c.getA(), c.getB(), c.getC(), c.getD());
			int choice = JOptionPane.showConfirmDialog(this, message, "Solve distortion result", JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION) {
				markingController.acceptDistortionResult(result);
				refreshOrientationControls();
				refreshActivePreview();
			}
		} catch (Exception ex) {
			showError("Solve distortion failed", ex);
		}
	}

	// PTZ-only pan/tilt shuttle controls, in their own tab rather than replacing the Camera
	// Orientation tab above (a real user report - see that method's own comment for the full
	// reasoning). Disabled/enabled at the tab level (tabs.setEnabledAt(...)) by
	// refreshOrientationControls(), on top of the panel's own setControlsEnabled(...) gate.
	private JPanel buildCameraPanTiltTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Pan / tilt (PTZ cameras only)"));
		panel.add(ptzOrientationPanel, BorderLayout.CENTER);
		return panel;
	}

	// Callbacks write straight to CameraConfig.getMountControl() (mode/enabled) and, for the
	// tracking-rate/per-axis-lock values, through PreviewController.getMountRuntime() - the runtime
	// owns the live EquatorialMountTransform/GeolocationStabilizerTransform instances actually used
	// by the render path (batch.CameraImageDispatch), so this class never touches those directly.
	private JPanel buildCameraMountTab() {
		JPanel panel = new JPanel(new BorderLayout());
		panel.setBorder(BorderFactory.createTitledBorder("Mount (PTZ cameras only)"));
		panel.add(mountControlPanel, BorderLayout.CENTER);

		mountControlPanel.setOnModeChanged(mode -> {
			if (!isActiveCameraPtz())
				return;
			MountControl mc = appController.getActivePreviewController().getCameraConfig().getMountControl();
			mc.setMode(mode);
			// Switching mode never auto-engages the new one - the user must explicitly re-check
			// "Engage." MountTransformRuntime.resolve(...) also independently cleans up a transform
			// left active by the PREVIOUS mode on the very next render either way.
			mc.setEnabled(false);
			refreshMountControls();
			refreshActivePreview();
		});
		mountControlPanel.setOnEngageChanged(engaged -> {
			if (!isActiveCameraPtz())
				return;
			appController.getActivePreviewController().getCameraConfig().getMountControl().setEnabled(engaged);
			refreshActivePreview();
			refreshMountControls();
		});
		mountControlPanel.setOnTrackingRateChanged(rate -> {
			if (!isActiveCameraPtz())
				return;
			appController.getActivePreviewController().getMountRuntime().setTrackingRateDegreesPerHour(rate);
			refreshActivePreview();
		});
		mountControlPanel.setOnRaLockChanged(locked -> {
			if (!isActiveCameraPtz())
				return;
			appController.getActivePreviewController().getMountRuntime().setRaLocked(locked);
			refreshActivePreview();
		});
		mountControlPanel.setOnDecLockChanged(locked -> {
			if (!isActiveCameraPtz())
				return;
			appController.getActivePreviewController().getMountRuntime().setDecLocked(locked);
			refreshActivePreview();
		});
		mountControlPanel.setOnRollLockChanged(locked -> {
			if (!isActiveCameraPtz())
				return;
			appController.getActivePreviewController().getMountRuntime().setRollLocked(locked);
			refreshActivePreview();
		});

		return panel;
	}

	private JPanel buildTimeTab() {
		JPanel panel = new JPanel(new BorderLayout(4, 4));

		JPanel shuttleRow = new JPanel(new BorderLayout());
		shuttleRow.setBorder(BorderFactory.createTitledBorder("Scrub (grab and hold - snaps back on release)"));
		shuttleRow.add(shuttle, BorderLayout.CENTER);
		panel.add(shuttleRow, BorderLayout.CENTER);

		shuttle.onRateChanged(rate -> appController.getClock().setRate(rate));
		shuttle.onRelease(() -> {
			appController.getClock().pause();
			refreshActivePreview();
			updateTimePlayPauseLabel();
		});

		JPanel bottom = new JPanel(new GridLayout(0, 1, 4, 4));

		JPanel dateRow = new JPanel();
		dateRow.setBorder(BorderFactory.createTitledBorder("Jump to (system timezone) - lands and pauses"));
		dateRow.add(new JLabel("Year"));
		dateRow.add(yearSpinner);
		dateRow.add(new JLabel("Month"));
		dateRow.add(monthSpinner);
		dateRow.add(new JLabel("Day"));
		dateRow.add(daySpinner);
		dateRow.add(new JLabel("Hour"));
		dateRow.add(hourSpinner);
		dateRow.add(new JLabel("Minute"));
		dateRow.add(minuteSpinner);
		dateRow.add(new JLabel("Second"));
		dateRow.add(secondSpinner);
		bottom.add(dateRow);

		// Item 4 - one listener per field (not a single shared listener rebuilding the whole
		// timestamp from all six spinners, the previous design) so each edit mutates exactly the
		// field the user touched via TimeFieldEditor's matching applyXxx(...), preserving every
		// other field untouched - see that class's own comment.
		yearSpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applyYear, (Integer) yearSpinner.getValue()));
		monthSpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applyMonth, (Integer) monthSpinner.getValue()));
		daySpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applyDay, (Integer) daySpinner.getValue()));
		hourSpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applyHour, (Integer) hourSpinner.getValue()));
		minuteSpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applyMinute, (Integer) minuteSpinner.getValue()));
		secondSpinner.addChangeListener(e -> applyTimeField(TimeFieldEditor::applySecond, (Integer) secondSpinner.getValue()));

		JPanel playPauseRow = new JPanel();
		timePlayPause.addActionListener(e -> {
			if (appController.getClock().isPlaying())
				appController.getClock().pause();
			else
				appController.getClock().resume();
			refreshActivePreview();
			updateTimePlayPauseLabel();
		});
		playPauseRow.add(timePlayPause);

		// Mirrors applyDateTimeFields()'s own exact pattern (jump -> pause -> refresh) rather than
		// inventing a different state-transition rule - "Now" is really just "jump to a specific
		// instant" too, only computed from the wall clock instead of the typed fields.
		timeNow.addActionListener(e -> {
			appController.getClock().setTime(WallClock.SYSTEM.currentTimeMillis());
			appController.getClock().pause();
			refreshActivePreview();
			updateTimePlayPauseLabel();
			refreshTimeFields();
		});
		playPauseRow.add(timeNow);

		bottom.add(playPauseRow);

		panel.add(bottom, BorderLayout.SOUTH);

		return panel;
	}

	// Item 4 - the shared entry point every per-field spinner listener above calls: read the CURRENT
	// render time, apply exactly one field's edit (in-bounds mutate, or overflow/underflow rollover -
	// see TimeFieldEditor's own comment for which), then jump there. None of TimeFieldEditor's
	// applyXxx(...) methods can throw for a value the spinner's own (already-guarded) model produced -
	// see that class's comment for why - so no exception handling is needed here, unlike the previous
	// single-shared-listener design's ZonedDateTime.of(...) reconstruction, which could throw for an
	// inconsistent combination of all six raw field values.
	private void applyTimeField(java.util.function.BiFunction<ZonedDateTime, Integer, ZonedDateTime> editor,
			int spinnerValue) {
		if (updatingTimeFieldsProgrammatically)
			return;

		ZonedDateTime current = Instant.ofEpochMilli(appController.getClock().getCurrentTimeMillis())
				.atZone(ZoneId.systemDefault());
		ZonedDateTime target = editor.apply(current, spinnerValue);

		appController.getClock().setTime(target.toInstant().toEpochMilli());
		appController.getClock().pause();
		refreshActivePreview();
		updateTimePlayPauseLabel();
		// A rollover (or a Real camera's own scrub-to-nearest-older-frame rule) can land on a
		// different timestamp than the literal target - reflect what was ACTUALLY rendered, not just
		// echo the target back unchanged.
		refreshTimeFields();
	}

	// A real user report: these spinners never reflected what was actually on screen, stuck at their
	// construction-time default (2026-01-01 00:00) forever - nothing ever wrote to them outside the
	// constructor. Reads PreviewController.getLastRenderedEpochMillis() - for a shown Real camera,
	// the snapped archived frame's own timestamp (task 0.6's scrub rule), not necessarily the raw
	// clock/shuttle target - so this reflects "the date/time of the latest image drawn" exactly, per
	// the user's own words, not just an approximation from the clock alone. A no-op before any camera
	// has been opened (nothing to reflect yet).
	private void refreshTimeFields() {
		if (!appController.hasActiveCamera())
			return;

		long epochMillis = appController.getActivePreviewController().getLastRenderedEpochMillis();
		ZonedDateTime dateTime = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault());

		updatingTimeFieldsProgrammatically = true;
		try {
			yearSpinner.setValue(dateTime.getYear());
			monthSpinner.setValue(dateTime.getMonthValue());
			daySpinner.setValue(dateTime.getDayOfMonth());
			hourSpinner.setValue(dateTime.getHour());
			minuteSpinner.setValue(dateTime.getMinute());
			secondSpinner.setValue(dateTime.getSecond());
		} finally {
			updatingTimeFieldsProgrammatically = false;
		}
	}

	private void updateTimePlayPauseLabel() {
		timePlayPause.setText(appController.getClock().isPlaying() ? "Pause" : "Play");
	}

	// Same shape as refreshLocationControls()/refreshOrientationControls() - a real user report:
	// PreviewController.isImageShown() already existed but ControlPanel had no way to see or set it,
	// only PreviewWindow's own separate checkbox did. Polled from the same 500ms timeFieldsSync
	// Timer that already keeps the Time tab in sync, rather than any direct wiring to PreviewWindow -
	// both this checkbox and PreviewWindow's own read/write the exact same PreviewController
	// instance, so a change in either becomes visible in the other within one tick.
	private void refreshShowCameraImageControl() {
		if (!appController.hasActiveCamera()) {
			showCameraImage.setEnabled(false);
			return;
		}

		updatingShowCameraImageProgrammatically = true;
		try {
			showCameraImage.setSelected(appController.getActivePreviewController().isImageShown());
		} finally {
			updatingShowCameraImageProgrammatically = false;
		}
		showCameraImage.setEnabled(true);
	}

	private JPanel buildRenderingTab() {
		JPanel panel = new JPanel(new GridLayout(0, 1));
		panel.setBorder(BorderFactory.createTitledBorder("Layers"));

		graticule.addActionListener(e -> {
			appController.getOptions().setShowGraticule(graticule.isSelected());
			refreshActivePreview();
		});
		panel.add(graticule);

		celestialOrigin.addActionListener(e -> {
			appController.getOptions().setShowCelestialOrigin(celestialOrigin.isSelected());
			refreshActivePreview();
		});
		panel.add(celestialOrigin);

		observerCardinalCross.addActionListener(e -> {
			appController.getOptions().setShowObserverCardinalCross(observerCardinalCross.isSelected());
			refreshActivePreview();
		});
		panel.add(observerCardinalCross);

		boresightReferenceLines.addActionListener(e -> {
			appController.getOptions().setShowBoresightReferenceLines(boresightReferenceLines.isSelected());
			refreshActivePreview();
		});
		panel.add(boresightReferenceLines);

		skyToggle.addActionListener(e -> {
			appController.getOptions().setManualSkyToggle(skyToggle.isSelected());
			refreshActivePreview();
		});
		panel.add(skyToggle);

		hideGround.addActionListener(e -> {
			appController.getOptions().setManualHideGroundToggle(hideGround.isSelected());
			refreshActivePreview();
		});
		panel.add(hideGround);

		groundOverObjects.addActionListener(e -> {
			appController.getOptions().setGroundPaintsOverObjects(groundOverObjects.isSelected());
			refreshActivePreview();
		});
		panel.add(groundOverObjects);

		showCameraImage.addActionListener(e -> {
			if (updatingShowCameraImageProgrammatically || !appController.hasActiveCamera())
				return;
			appController.getActivePreviewController().setImageShown(showCameraImage.isSelected());
			refreshActivePreview();
		});
		panel.add(showCameraImage);

		osd.addActionListener(e -> {
			// The OSD timezone default is "My Location"'s derived suggestion once the operator has
			// set one [Part I wiring pass] - falling back to the app operator's own system zone
			// otherwise, consistent with this module's established "assume it's you" default
			// elsewhere (see CLAUDE.md's "Timezone resolution").
			ZoneId osdTimezone = myTimezone != null ? myTimezone : ZoneId.systemDefault();
			appController.getOptions().setOsdTimezone(osdTimezone).setShowOsd(osd.isSelected());
			refreshActivePreview();
		});
		panel.add(osd);

		labels.addActionListener(e -> {
			appController.getOptions().setShowLabels(labels.isSelected());
			refreshActivePreview();
		});
		panel.add(labels);

		// Item 5 - GlobalSettings persistence: applies the previously-SAVED scheme at startup (instead
		// of always resetting to the hardcoded default preset), and auto-persists on every change,
		// matching "My Location"'s own established load-at-construction/save-on-every-change pattern.
		// The dropdown itself stays a 3-preset quick-select (individual per-reference-line color
		// pickers are a separate, not-yet-built follow-up - out of this round's scope) - selecting a
		// preset overwrites and persists whatever custom scheme was loaded.
		appController.getOptions().setColorScheme(appController.getSettings().getColorScheme());
		colorScheme.addActionListener(e -> {
			ColorScheme selected = colorSchemeFor((String) colorScheme.getSelectedItem());
			appController.getOptions().setColorScheme(selected);
			appController.getSettings().setColorScheme(selected);
			persistGlobalSettings();
			refreshActivePreview();
		});
		panel.add(colorScheme);

		JPanel starTierRow = new JPanel();
		starTierRow.add(new JLabel("Star catalog:"));
		starTierRow.add(starTier);
		starTier.setSelectedItem("main");
		starTier.addActionListener(e -> {
			appController.getOptions().setStars(starCatalogLoader.filterByTier(starCatalog, tierFor((String) starTier.getSelectedItem())));
			refreshActivePreview();
		});
		panel.add(starTierRow);

		// Whole-feature on/off gate, independent of the tier selector above (which controls the
		// CONTENT of the stars list, not whether any of it renders at all) - placed together so the
		// two don't get confused for one another.
		showStars.addActionListener(e -> {
			appController.getOptions().setShowStars(showStars.isSelected());
			refreshActivePreview();
		});
		panel.add(showStars);

		showSun.addActionListener(e -> {
			appController.getOptions().setShowSun(showSun.isSelected());
			refreshActivePreview();
		});
		panel.add(showSun);

		showMoon.addActionListener(e -> {
			appController.getOptions().setShowMoon(showMoon.isSelected());
			refreshActivePreview();
		});
		panel.add(showMoon);

		showPlanets.addActionListener(e -> {
			appController.getOptions().setShowPlanets(showPlanets.isSelected());
			refreshActivePreview();
		});
		panel.add(showPlanets);

		// Item 5 - GlobalSettings persistence, same load-at-startup/save-on-every-change pattern as
		// colorScheme above.
		fontSize.setValue(appController.getSettings().getFontSizePixels());
		appController.getOptions().setFontSizePixels(appController.getSettings().getFontSizePixels());
		JPanel fontSizeRow = new JPanel();
		fontSizeRow.add(new JLabel("Font size (px):"));
		fontSizeRow.add(fontSize);
		fontSize.addChangeListener(e -> {
			int selected = (Integer) fontSize.getValue();
			appController.getOptions().setFontSizePixels(selected);
			appController.getSettings().setFontSizePixels(selected);
			persistGlobalSettings();
			refreshActivePreview();
		});
		panel.add(fontSizeRow);

		return panel;
	}

	private StarCatalogTier tierFor(String name) {
		if ("named".equals(name))
			return StarCatalogTier.NAMED;
		if ("all".equals(name))
			return StarCatalogTier.ALL;
		if ("visible".equals(name))
			return StarCatalogTier.VISIBLE_ONLY;
		return StarCatalogTier.MAIN;
	}

	// UI over the same Options fields/values the CLI's --watched-object/--crosshair/--watched-path/
	// --sun-path/--moon-path flags already wire (see cli.CameraConfigArgs.buildOptions(...)) -
	// deliberately excludes plate-solve mark-and-fit (technique 2's click-and-mark UI), a substantial
	// standalone feature with no UI at all yet, per the plan's own explicit scope note.
	private JPanel buildWatchedObjectTab() {
		JPanel panel = new JPanel(new GridLayout(0, 1, 4, 4));

		// The path checkbox sits directly in the identity row (item 8) - it's conceptually about
		// WHICH object is being watched, matching the selector it belongs beside, rather than being
		// grouped with the crosshair controls below as it was before.
		JPanel identityRow = new JPanel();
		identityRow.setBorder(BorderFactory.createTitledBorder("Watched object"));
		identityRow.add(watchedObjectKind);
		identityRow.add(watchedStarName);
		identityRow.add(watchedPathEnabled);
		identityRow.add(watchedObjectReferenceLinesEnabled);
		panel.add(identityRow);

		JPanel crosshairRow = new JPanel();
		crosshairRow.add(crosshairEnabled);
		crosshairRow.add(new JLabel("Offset (hours):"));
		crosshairRow.add(crosshairOffsetHours);
		panel.add(crosshairRow);

		JPanel bodyPathRow = new JPanel();
		bodyPathRow.add(new JLabel("Sun path:"));
		bodyPathRow.add(sunPathMode);
		bodyPathRow.add(new JLabel("Moon path:"));
		bodyPathRow.add(moonPathMode);
		panel.add(bodyPathRow);

		watchedObjectKind.addActionListener(e -> {
			watchedStarName.setEnabled(STAR_BY_NAME_LABEL.equals(watchedObjectKind.getSelectedItem()));
			applyCrosshairAndPath();
		});
		watchedStarName.addActionListener(e -> applyCrosshairAndPath());
		crosshairEnabled.addActionListener(e -> applyCrosshairAndPath());
		crosshairOffsetHours.addChangeListener(e -> applyCrosshairAndPath());
		watchedPathEnabled.addActionListener(e -> applyCrosshairAndPath());
		watchedObjectReferenceLinesEnabled.addActionListener(e -> applyCrosshairAndPath());

		sunPathMode.addActionListener(e -> {
			appController.getOptions().setSunPathMode(eclipticAnalemmaModeFor((String) sunPathMode.getSelectedItem()));
			refreshActivePreview();
		});
		moonPathMode.addActionListener(e -> {
			appController.getOptions().setMoonPathMode(eclipticAnalemmaModeFor((String) moonPathMode.getSelectedItem()));
			refreshActivePreview();
		});

		watchedStarName.setEnabled(false);

		return panel;
	}

	private void applyCrosshairAndPath() {
		if (crosshairEnabled.isSelected()) {
			try {
				WatchedObject target = resolveSelectedWatchedObject();
				double offsetHours = (Double) crosshairOffsetHours.getValue();
				long referenceEpochMillis = WallClock.SYSTEM.currentTimeMillis() + (long) (offsetHours * 3_600_000.0);
				appController.getOptions().setWatchedObjectCrosshair(target, observationTimeAt(referenceEpochMillis));
			} catch (Exception ex) {
				showError("Failed to set watched-object crosshair", ex);
				crosshairEnabled.setSelected(false);
				appController.getOptions().clearWatchedObjectCrosshair();
			}
		} else {
			appController.getOptions().clearWatchedObjectCrosshair();
		}

		if (watchedPathEnabled.isSelected()) {
			try {
				WatchedObject target = resolveSelectedWatchedObject();
				// Fixed 24h/10min (item 8) - simply not overriding Options' own already-24h/10min
				// defaults, rather than duplicating those constants here.
				appController.getOptions().setShowWatchedObjectPath(target);
			} catch (Exception ex) {
				showError("Failed to set watched-object path", ex);
				watchedPathEnabled.setSelected(false);
				appController.getOptions().clearWatchedObjectPath();
			}
		} else {
			appController.getOptions().clearWatchedObjectPath();
		}

		if (watchedObjectReferenceLinesEnabled.isSelected()) {
			try {
				WatchedObject target = resolveSelectedWatchedObject();
				appController.getOptions().setShowWatchedObjectReferenceLines(target);
			} catch (Exception ex) {
				showError("Failed to set watched-object reference lines", ex);
				watchedObjectReferenceLinesEnabled.setSelected(false);
				appController.getOptions().clearWatchedObjectReferenceLines();
			}
		} else {
			appController.getOptions().clearWatchedObjectReferenceLines();
		}

		refreshActivePreview();
	}

	private WatchedObject resolveSelectedWatchedObject() {
		String kind = (String) watchedObjectKind.getSelectedItem();
		if ("Sun".equals(kind))
			return WatchedObject.sun();
		if ("Moon".equals(kind))
			return WatchedObject.moon();
		if (STAR_BY_NAME_LABEL.equals(kind)) {
			String name = watchedStarName.getText().trim();
			for (StarCoordinate star : starCatalog)
				if (star.getName().equalsIgnoreCase(name))
					return WatchedObject.star(star);
			throw new IllegalArgumentException("no star named \"" + name + "\" found in the bundled catalog");
		}
		for (int i = 1; i < SolarObjects.OBJECT_LIST.length; i++)
			if (SolarObjects.OBJECT_LIST[i].trim().equalsIgnoreCase(kind))
				return WatchedObject.planet(i);
		throw new IllegalStateException("unreachable - unknown watched-object selection \"" + kind + "\"");
	}

	private ObservationTime observationTimeAt(long epochMillis) throws Exception {
		ObservationTime time = new ObservationTime();
		time.initTime(java.util.TimeZone.getTimeZone("UTC"));
		time.setUnixTime(epochMillis);
		return time;
	}

	private EclipticAnalemmaMode eclipticAnalemmaModeFor(String name) {
		if ("ecliptic".equals(name))
			return EclipticAnalemmaMode.ECLIPTIC;
		if ("analemma".equals(name))
			return EclipticAnalemmaMode.ANALEMMA;
		return EclipticAnalemmaMode.NONE;
	}

	private double currentLatitude() {
		return Double.parseDouble(latitudeField.getText().trim());
	}

	private double currentLongitude() {
		return Double.parseDouble(longitudeField.getText().trim());
	}

	// PTZ cameras write straight to CameraConfig.setCurrentLocation(...)/setCurrentOrientation(...)
	// (no PlateSolveSession exists for them at all - see CLAUDE.md's "Orientation editing") and rely
	// on AppController's auto-persist-on-switch/exit instead of an explicit Save button.
	private boolean isActiveCameraPtz() {
		return appController.getActivePreviewController() != null && appController.getActivePreviewController()
				.getCameraConfig().getType().getOrientationMode() != OrientationMode.FIXED;
	}

	private void applyLocationEdit(double latitude, double longitude) {
		if (isActiveCameraPtz()) {
			appController.getActivePreviewController().getCameraConfig()
					.setCurrentLocation(ObserverLocationSetting.explicit(latitude, longitude));
			refreshActivePreview();
			refreshOrientationControls(); // keep the Camera Orientation tab's lat/lon spinners in sync -
			// this branch skipped the call entirely before, a real bug (item 3)
			return;
		}
		PlateSolveSession session = activeEditSessionOrNull();
		if (session == null)
			return;
		session.adjustLocation(latitude, longitude);
		refreshActivePreview();
		refreshOrientationControls(); // keep the Camera Orientation tab's lat/lon spinners in sync
	}

	private PlateSolveSession activeEditSessionOrNull() {
		return appController.getActivePreviewController() != null
				? appController.getActivePreviewController().getActiveEditSession()
				: null;
	}

	// Called after every successful camera switch - reflects the newly-active camera's own pending
	// location for a Fixed camera, or its current (auto-persisting) location for a PTZ one -
	// disabled only when no camera is open at all.
	private void refreshLocationControls() {
		if (isActiveCameraPtz()) {
			ObserverLocationSetting location = appController.getActivePreviewController().getCameraConfig().getCurrentLocation();
			// useSystemLocale() has no fixed lat/lon to show - leave the sliders at whatever they
			// already displayed rather than guessing at a value, same "don't invent a number" stance
			// CameraEditDialog.prefillVirtualFrom(...) already takes for this exact case.
			if (!location.isUseSystemLocale())
				setLocationFields(location.getLatitude(), location.getLongitude());
			setLocationControlsEnabled(true);
			return;
		}

		PlateSolveSession session = activeEditSessionOrNull();
		if (session == null) {
			setLocationControlsEnabled(false);
			return;
		}
		setLocationFields(session.getPendingLatitude(), session.getPendingLongitude());
		setLocationControlsEnabled(true);
	}

	private void setLocationFields(double latitude, double longitude) {
		updatingLocationProgrammatically = true;
		try {
			latitudeSlider.setValue((int) Math.round(latitude));
			latitudeField.setText(String.valueOf(latitude));
			longitudeSlider.setValue((int) Math.round(longitude));
			longitudeField.setText(String.valueOf(longitude));
		} finally {
			updatingLocationProgrammatically = false;
		}
	}

	private void setLocationControlsEnabled(boolean enabled) {
		latitudeSlider.setEnabled(enabled);
		latitudeField.setEnabled(enabled);
		longitudeSlider.setEnabled(enabled);
		longitudeField.setEnabled(enabled);
	}

	// Populates the Camera Orientation tab's spinners for either camera kind - PTZ from CameraConfig's
	// own current orientation/location/projection directly, Fixed from the session's pending values
	// (unchanged) - then applies the distortion-visibility gate (unchanged - a PTZ camera is always
	// Virtual, never Real, so it's already correctly grayed out by the existing kind check) and the
	// Save/Revert gate (new - PTZ has none). Also grays the whole Camera Pan/Tilt tab in or out.
	private void refreshOrientationControls() {
		PreviewController controller = appController.getActivePreviewController();
		if (controller == null) {
			setOrientationControlsEnabled(false);
			ptzOrientationPanel.setControlsEnabled(false);
			tabs.setEnabledAt(panTiltTabIndex, false);
			return;
		}
		CameraConfig camera = controller.getCameraConfig();

		if (isActiveCameraPtz()) {
			Orientation orientation = camera.getCurrentOrientation();
			ObserverLocationSetting location = camera.getCurrentLocation();
			// useSystemLocale() has no fixed lat/lon to show - leave the fields at whatever they
			// already displayed rather than guessing at a value (same stance
			// CameraEditDialog.prefillVirtualFrom(...) already takes for this exact case).
			double latitude = location.isUseSystemLocale() ? orientationPanel.getLatitude() : location.getLatitude();
			double longitude = location.isUseSystemLocale() ? orientationPanel.getLongitude() : location.getLongitude();
			double zoom = currentZoomOf(camera.getProjection());
			setOrientationFields(orientation.getAltitude(), orientation.getAzimuth(), orientation.getBarrelRoll(), zoom,
					latitude, longitude);
			setOrientationControlsEnabled(true);
			orientationPanel.setSaveRevertEnabled(false); // PTZ auto-persists - no meaningful Save/Revert
			ptzOrientationPanel.setCurrentValues(orientation.getAzimuth(), orientation.getAltitude(), zoom);
			ptzOrientationPanel.setControlsEnabled(true);
			tabs.setEnabledAt(panTiltTabIndex, true);
		} else {
			PlateSolveSession session = activeEditSessionOrNull();
			if (session == null) {
				setOrientationControlsEnabled(false);
				ptzOrientationPanel.setControlsEnabled(false);
				tabs.setEnabledAt(panTiltTabIndex, false);
				return;
			}
			Orientation orientation = session.getPendingOrientation();
			setOrientationFields(orientation.getAltitude(), orientation.getAzimuth(), orientation.getBarrelRoll(),
					session.getPendingZoom(), session.getPendingLatitude(), session.getPendingLongitude());
			setOrientationControlsEnabled(true);
			orientationPanel.setSaveRevertEnabled(true);
			ptzOrientationPanel.setControlsEnabled(false);
			tabs.setEnabledAt(panTiltTabIndex, false);
		}

		AbstractCameraProjection projection = distortableProjectionOrNull();
		if (projection != null) {
			orientationPanel.setDistortionValues(projection.getDistortionCoefficientA(),
					projection.getDistortionCoefficientB(), projection.getDistortionCoefficientC(),
					projection.getDistortionCoefficientD());
		}
		// Distortion only makes sense for a Real camera's real photo - direct user instruction. A PTZ
		// camera is always Virtual, never Real, so this already correctly grays the 4 spinners out for
		// it with no PTZ-specific branch needed here.
		orientationPanel.setDistortionControlsEnabled(camera.getType().getKind() == CameraType.Kind.REAL);
	}

	// Populates the Camera Mount tab from the active camera's MountControl/MountTransformRuntime -
	// PTZ Virtual cameras only (see the field's own comment). Also enforces the small polish item
	// flagged directly by the user's own framing of the tab: manually dragging Pan/Tilt while the
	// mount is actively computing the orientation every render has no visible effect until disengage
	// silently overwrites it, so Pan/Tilt is disabled for as long as the mount is active - this method
	// must therefore run AFTER refreshOrientationControls() at every shared call site, since that
	// method unconditionally re-enables Pan/Tilt for any PTZ camera.
	private void refreshMountControls() {
		if (!isActiveCameraPtz()) {
			mountControlPanel.setControlsEnabled(false);
			if (mountTabIndex >= 0)
				tabs.setEnabledAt(mountTabIndex, false);
			return;
		}
		PreviewController controller = appController.getActivePreviewController();
		CameraConfig camera = controller.getCameraConfig();
		MountControl mountControl = camera.getMountControl();
		MountTransformRuntime mountRuntime = controller.getMountRuntime();

		double trackingRate = mountRuntime != null ? mountRuntime.getTrackingRateDegreesPerHour()
				: TrackingRate.SIDEREAL.getDegreesPerHour();
		boolean raLocked = mountRuntime != null && mountRuntime.isRaLocked();
		boolean decLocked = mountRuntime != null && mountRuntime.isDecLocked();
		boolean rollLocked = mountRuntime != null && mountRuntime.isRollLocked();
		String status = mountControl.isActive() ? "Engaged" : "Not engaged";

		mountControlPanel.refresh(mountControl.getMode(), mountControl.isEnabled(), trackingRate, raLocked, decLocked,
				rollLocked, status);
		mountControlPanel.setControlsEnabled(true);
		tabs.setEnabledAt(mountTabIndex, true);

		boolean mountActive = mountControl.isActive();
		ptzOrientationPanel.setControlsEnabled(!mountActive);
		tabs.setEnabledAt(panTiltTabIndex, !mountActive);
	}

	private void setOrientationFields(double altitudeDegrees, double azimuthDegrees, double barrelRollDegrees,
			double zoomMillimeters, double latitudeDegrees, double longitudeDegrees) {
		updatingOrientationProgrammatically = true;
		try {
			orientationPanel.setValues(altitudeDegrees, azimuthDegrees, barrelRollDegrees, zoomMillimeters, latitudeDegrees,
					longitudeDegrees);
		} finally {
			updatingOrientationProgrammatically = false;
		}
	}

	// CameraProjection has no generic "current focal length" getter (an equirectangular "lens" has no
	// single focal-length concept - see CameraConfig's own comment on why the interface doesn't
	// expose one), so this reads it the same way CameraEditDialog.prefillFrom(...) already does for
	// display purposes: instanceof against the two concrete lens types this codebase can construct.
	private double currentZoomOf(me.qbert.skywatch.camera.projection.CameraProjection projection) {
		if (projection instanceof FisheyeProjection)
			return ((FisheyeProjection) projection).getFocalLengthMillimeters();
		if (projection instanceof RectilinearProjection)
			return ((RectilinearProjection) projection).getFocalLengthMillimeters();
		return 50.0; // matches CalibrationPanel's own zoom spinner default
	}

	private void setOrientationControlsEnabled(boolean enabled) {
		orientationPanel.setControlsEnabled(enabled);
	}

	// The pan/tilt/zoom integration loop - ticks continuously (cheap no-op when nothing's happening)
	// rather than being started/stopped per-drag, matching how the render Timer elsewhere in this
	// class already always ticks. Only actually moves anything when the active camera is PTZ and at
	// least one shuttle is currently deflected from center.
	//
	// A real user report, round 1: a full render is expensive enough (EquirectangularSceneRenderer's
	// per-pixel inverse-projection loop, at full window resolution) that doing one on every 50ms tick
	// made panning/tilting feel like it updated once a second - not a timer-interval problem, a
	// per-tick work-cost problem. While dragging, this shows a cheap, small "fast preview" composited
	// over the last full frame instead (PreviewController.renderFastPtzPreview(...)); the exact tick
	// where dragging stops (release) does exactly one real, full-quality render.
	//
	// Round 2, two more real user reports against that same fix: (a) PreviewWindow's OWN 250ms tick
	// was independently calling a full refresh() regardless of dragging state (whenever the clock is
	// playing, the default), silently overriding the fast preview every 250ms - this is what "still
	// draws the large image" and "almost 1 per second" actually were. Fixed by having this method set
	// previewWindow.setFastPreviewActive(...) for exactly as long as dragging is happening, so that
	// tick backs off entirely rather than fighting this one. (b) The fast preview's whole reason to
	// exist is EquirectangularSceneRenderer's cost - with Layer 1 (the camera's own image) hidden,
	// there is no equirectangular sampling at all, so a full render is already cheap; the fast/PIP
	// path is skipped entirely in that case, always doing one real render per tick instead.
	private void tickPtzOrientation() {
		long now = System.nanoTime();
		long lastTick = ptzLastTickNanos;
		ptzLastTickNanos = now;
		if (lastTick < 0 || !isActiveCameraPtz())
			return;

		double panRate = ptzOrientationPanel.getPanRateDegreesPerSecond();
		double tiltRate = ptzOrientationPanel.getTiltRateDegreesPerSecond();
		double zoomRate = ptzOrientationPanel.getZoomRateMillimetersPerSecond();
		boolean dragging = panRate != 0.0 || tiltRate != 0.0 || zoomRate != 0.0;
		if (!dragging && !ptzWasDragging)
			return; // idle - the tick where dragging stopped already did the final full render below

		double elapsedSeconds = (now - lastTick) / 1_000_000_000.0;
		PreviewController controller = appController.getActivePreviewController();
		CameraConfig camera = controller.getCameraConfig();
		Orientation updated = PtzOrientationPanel.integrate(camera.getCurrentOrientation(), panRate, tiltRate, elapsedSeconds);
		camera.setCurrentOrientation(updated);
		if (zoomRate != 0.0) {
			double newZoom = PtzOrientationPanel.integrateZoom(currentZoomOf(camera.getProjection()), zoomRate, elapsedSeconds);
			camera.setProjection(camera.getProjection().withFocalLength(newZoom));
		}
		double currentZoom = currentZoomOf(camera.getProjection());
		ptzOrientationPanel.setCurrentValues(updated.getAzimuth(), updated.getAltitude(), currentZoom);
		// The user's own explicit ask: the Camera Orientation tab's numeric spinners should update
		// live too, not just this tab's own readout labels. Reuses the existing helper (already
		// guarded by updatingOrientationProgrammatically, so this can't loop back into the spinners'
		// own onChanged callbacks) rather than the heavier refreshOrientationControls(), which also
		// re-resolves distortion/tab-enable state that can't have changed from panning.
		setOrientationFields(updated.getAltitude(), updated.getAzimuth(), updated.getBarrelRoll(), currentZoom,
				orientationPanel.getLatitude(), orientationPanel.getLongitude());

		if (previewWindow != null) {
			try {
				boolean needsFastPath = dragging && controller.isImageShown();
				previewWindow.setFastPreviewActive(needsFastPath);
				if (needsFastPath)
					previewWindow.showFastPreview(controller.renderFastPtzPreview(PTZ_FAST_PREVIEW_MAX_DIMENSION_PIXELS));
				else
					previewWindow.refresh(); // no Layer-1 image to make expensive, or dragging just stopped
			} catch (Exception ex) {
				showError("Failed to render PTZ preview", ex);
			}
		}
		ptzWasDragging = dragging;
	}

	// Every CameraProjection this codebase can construct extends AbstractCameraProjection - the same
	// assumption config.CameraConfigStore's own writeProjection(...) already makes. Resolved straight
	// from the active camera's own CameraConfig - NOT through activeEditSessionOrNull() (a real,
	// pre-existing gap this round found: distortion editing was already broken for PTZ before the
	// Camera Pan/Tilt tab even existed, since PTZ never has a PlateSolveSession) - so this now works
	// uniformly for Fixed and PTZ. null when no camera is open, or the active camera has no projection
	// configured yet (CameraConfig.getProjection() is optional/unconfigured-until-set).
	private AbstractCameraProjection distortableProjectionOrNull() {
		PreviewController controller = appController.getActivePreviewController();
		if (controller == null)
			return null;
		me.qbert.skywatch.camera.projection.CameraProjection projection = controller.getCameraConfig().getProjection();
		return projection != null ? (AbstractCameraProjection) projection : null;
	}

	private ColorScheme colorSchemeFor(String name) {
		if ("deuteranopia".equals(name))
			return ColorPresets.deuteranopiaFriendlyScheme();
		if ("high-contrast".equals(name))
			return ColorPresets.highContrastScheme();
		return ColorPresets.defaultScheme();
	}

	private void refreshCameraList() {
		cameraList.removeAllItems();
		for (String name : appController.listCameraNames())
			cameraList.addItem(name);
	}

	private void withSelectedCamera(java.util.function.Consumer<String> action) {
		String name = (String) cameraList.getSelectedItem();
		if (name != null)
			action.accept(name);
	}

	private void openEditDialog(CameraConfig existingConfig) {
		CameraEditDialog dialog = new CameraEditDialog(this, existingConfig, config -> {
			try {
				appController.addCamera(config.getName(), config);
				refreshCameraList();
			} catch (Exception ex) {
				showError("Failed to save camera", ex);
			}
		});
		dialog.setVisible(true);
	}

	private void openEditDialogFor(String name) {
		try {
			openEditDialog(appController.getLibrary().load(name));
		} catch (Exception ex) {
			showError("Failed to load camera \"" + name + "\"", ex);
		}
	}

	private void removeCamera(String name) {
		int confirmed = JOptionPane.showConfirmDialog(this, "Remove camera \"" + name + "\"?", "Confirm removal",
				JOptionPane.YES_NO_OPTION);
		if (confirmed == JOptionPane.YES_OPTION) {
			appController.removeCamera(name);
			refreshCameraList();
			refreshLocationControls(); // disables them if the removed camera was the active one
			refreshOrientationControls();
			refreshMountControls();
			refreshShowCameraImageControl();
			refreshMarkingControls();
		}
	}

	private void openCamera(String name) {
		try {
			PreviewController controller = appController.switchToCamera(name);
			if (previewWindow == null) {
				previewWindow = new PreviewWindow(controller);
				// Wired ONCE - previewWindow is a single long-lived instance for the whole app-mode
				// session (rebound via switchTo(...) above, never replaced - see this field's own
				// comment), so this listener stays correctly wired across every future camera switch.
				previewWindow.onMarkClicked(this::handleMarkClick);
			} else {
				previewWindow.switchTo(controller);
			}
			previewWindow.setVisible(true);
			refreshLocationControls();
			refreshOrientationControls();
			refreshMountControls();
			refreshTimeFields();
			refreshShowCameraImageControl();
			refreshMarkingControls();
		} catch (Exception ex) {
			showError("Failed to open camera \"" + name + "\"", ex);
		}
	}

	private void refreshActivePreview() {
		if (previewWindow != null)
			previewWindow.refresh();
	}

	// A real user report: opening a camera silently left the Camera Location/Orientation tabs
	// disabled with no obvious cause - traced to an exception during switchToCamera(...) (e.g. an
	// older saved camera profile missing a since-added required key) being caught by openCamera(...)
	// and shown ONLY as this dialog, with no stack trace anywhere - cause.getMessage() alone (often
	// null for some exception types) is not enough to diagnose a real bug from. Matches
	// PreviewWindow/CalibrationWindow's own refresh() error handling, which already prints to
	// stderr for exactly this reason.
	private void showError(String message, Exception cause) {
		cause.printStackTrace();
		JOptionPane.showMessageDialog(this,
				message + ": " + cause.getClass().getSimpleName() + (cause.getMessage() != null ? " - " + cause.getMessage() : "")
						+ ". See the console for the full error.",
				"Error", JOptionPane.ERROR_MESSAGE);
	}
}
