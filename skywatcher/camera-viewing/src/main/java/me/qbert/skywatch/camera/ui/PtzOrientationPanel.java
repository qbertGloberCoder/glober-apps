package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.text.DecimalFormat;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;

import me.qbert.skywatch.camera.orientation.Orientation;

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

// A PTZ camera has no CalibrationHistory and no PlateSolveSession (see CLAUDE.md's "Orientation
// editing" - Fixed cameras get explicit Save/Revert, PTZ auto-persists instead), so a session-shaped
// widget like CalibrationPanel can't drive it. This lives in ControlPanel's own "Camera Pan/Tilt"
// tab (a real user report: an earlier round embedded this by REPLACING the Camera Orientation tab's
// numeric controls instead of giving it a separate tab, which felt like a regression) alongside the
// existing, now PTZ-aware CalibrationPanel in the Camera Orientation tab - the same live orientation
// is reflected in both places, kept in sync by ControlPanel.
//
// The user's own explicit design, refined over two rounds: a horizontal pan (azimuth) shuttle that
// "should just spin in either direction forever, wrapping at 0/360," a vertical tilt (altitude)
// shuttle that "should stop at + and - 90 degrees," and (added the second round) a vertical zoom
// shuttle "on the left side of the azimuth" - all three modeled on the Time tab's existing
// spring-loaded ShuttleControl. The two vertical shuttles are deliberately narrow
// (VERTICAL_SHUTTLE_WIDTH_PIXELS) - a real user report that they were "too wide," crowding out
// horizontal room for the pan shuttle, which should get the majority of the panel's width.
// Reuses ShuttleControl.getCurrentRate() (a poll-based rate readout) rather than its push-based
// onRateChanged(...) callback, since ControlPanel drives all three integrations from its own
// periodic Timer independent of exactly when a shuttle's slider last moved.
//
// A bare JPanel doesn't throw HeadlessException (only Window/Frame/Dialog subclasses check
// headlessness in their constructor - see CalibrationPanel's own note), so this class and
// PtzOrientationPanelTest are both fully constructible/testable in this module's own display-less
// sandbox; integrate(...)/integrateZoom(...)'s wrap/clamp math is exercised directly there.
public final class PtzOrientationPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	// Degrees/second - first-guess constants, easy to retune from feedback, same "not load-bearing"
	// framing as ShuttleControl's own original time-scrub constants.
	public static final double PAN_SLOW_MAX_DEGREES_PER_SECOND = 10.0;
	public static final double PAN_FAST_MAX_DEGREES_PER_SECOND = 90.0;
	public static final double TILT_SLOW_MAX_DEGREES_PER_SECOND = 10.0;
	public static final double TILT_FAST_MAX_DEGREES_PER_SECOND = 90.0;

	// Millimeters/second - same first-guess framing. Bounds match CalibrationPanel's own zoom
	// spinner (1.0-2000.0mm) so a shuttle-driven zoom can never drift outside what the numeric
	// spinner itself would ever accept.
	public static final double ZOOM_SLOW_MAX_MILLIMETERS_PER_SECOND = 20.0;
	public static final double ZOOM_FAST_MAX_MILLIMETERS_PER_SECOND = 200.0;
	static final double MIN_FOCAL_LENGTH_MILLIMETERS = 1.0;
	static final double MAX_FOCAL_LENGTH_MILLIMETERS = 2000.0;

	private static final int VERTICAL_SHUTTLE_WIDTH_PIXELS = 60;
	private static final int VERTICAL_SHUTTLE_HEIGHT_PIXELS = 220;

	private static final DecimalFormat DEGREES_FORMAT = new DecimalFormat("0.0");
	private static final DecimalFormat MILLIMETERS_FORMAT = new DecimalFormat("0.0");

	private final ShuttleControl zoom = new ShuttleControl(JSlider.VERTICAL, ZOOM_SLOW_MAX_MILLIMETERS_PER_SECOND,
			ZOOM_FAST_MAX_MILLIMETERS_PER_SECOND);
	private final ShuttleControl pan = new ShuttleControl(JSlider.HORIZONTAL, PAN_SLOW_MAX_DEGREES_PER_SECOND,
			PAN_FAST_MAX_DEGREES_PER_SECOND);
	private final ShuttleControl tilt = new ShuttleControl(JSlider.VERTICAL, TILT_SLOW_MAX_DEGREES_PER_SECOND,
			TILT_FAST_MAX_DEGREES_PER_SECOND);
	private final JLabel currentZoomLabel = new JLabel("Zoom: -");
	private final JLabel currentAzimuthLabel = new JLabel("Azimuth: -");
	private final JLabel currentAltitudeLabel = new JLabel("Altitude: -");

	public PtzOrientationPanel() {
		setLayout(new BorderLayout(4, 4));

		Dimension verticalShuttleSize = new Dimension(VERTICAL_SHUTTLE_WIDTH_PIXELS, VERTICAL_SHUTTLE_HEIGHT_PIXELS);
		zoom.setPreferredSize(verticalShuttleSize);
		tilt.setPreferredSize(verticalShuttleSize);

		JPanel zoomSection = new JPanel(new BorderLayout(2, 2));
		zoomSection.setBorder(new TitledBorder("Zoom"));
		zoomSection.add(zoom, BorderLayout.CENTER);
		zoomSection.add(currentZoomLabel, BorderLayout.SOUTH);

		JPanel panSection = new JPanel(new BorderLayout(4, 4));
		panSection.setBorder(new TitledBorder("Pan (azimuth)"));
		panSection.add(pan, BorderLayout.CENTER);
		panSection.add(currentAzimuthLabel, BorderLayout.SOUTH);

		JPanel tiltSection = new JPanel(new BorderLayout(2, 2));
		tiltSection.setBorder(new TitledBorder("Tilt (altitude)"));
		tiltSection.add(tilt, BorderLayout.CENTER);
		tiltSection.add(currentAltitudeLabel, BorderLayout.SOUTH);

		// BorderLayout gives WEST/EAST exactly their preferred (narrow) width and hands the CENTER
		// component - pan - everything left over, which is the "more horizontal control for the
		// azimuth" the user asked for.
		add(zoomSection, BorderLayout.WEST);
		add(panSection, BorderLayout.CENTER);
		add(tiltSection, BorderLayout.EAST);
	}

	// Degrees or millimeters per second, positive or negative - 0.0 whenever the corresponding
	// shuttle is at rest (center, not currently being dragged). Polled by ControlPanel's own
	// periodic integration Timer, not pushed - see this class's own comment for why.
	public double getPanRateDegreesPerSecond() {
		return pan.getCurrentRate();
	}

	public double getTiltRateDegreesPerSecond() {
		return tilt.getCurrentRate();
	}

	public double getZoomRateMillimetersPerSecond() {
		return zoom.getCurrentRate();
	}

	// Purely a display readout - the shuttles themselves never show a "value," only a momentary rate
	// (matching the Time tab's own shuttle, which never displays "the current time" either) - without
	// this, panning would have no feedback except the render itself.
	public void setCurrentValues(double azimuthDegrees, double altitudeDegrees, double zoomMillimeters) {
		currentAzimuthLabel.setText("Azimuth: " + DEGREES_FORMAT.format(azimuthDegrees));
		currentAltitudeLabel.setText("Altitude: " + DEGREES_FORMAT.format(altitudeDegrees));
		currentZoomLabel.setText("Zoom: " + MILLIMETERS_FORMAT.format(zoomMillimeters) + "mm");
	}

	public void setControlsEnabled(boolean enabled) {
		pan.setEnabled(enabled);
		tilt.setEnabled(enabled);
		zoom.setEnabled(enabled);
	}

	// Package-visible for direct unit testing - integrates the given pan/tilt rates over the elapsed
	// real time, wrapping azimuth continuously at 0/360 (a pan control should spin forever, never
	// "hit a wall" the way a Fixed camera's azimuth spinner does) and clamping altitude at +/-90 (a
	// physical tilt cannot point past straight up or straight down) - the user's own exact
	// specification. Barrel roll is carried over unchanged; nothing asked for a roll control here.
	static Orientation integrate(Orientation current, double panRateDegreesPerSecond, double tiltRateDegreesPerSecond,
			double elapsedSeconds) {
		double azimuth = wrapDegrees(current.getAzimuth() + panRateDegreesPerSecond * elapsedSeconds);
		double altitude = clampAltitude(current.getAltitude() + tiltRateDegreesPerSecond * elapsedSeconds);
		return new Orientation(altitude, azimuth, current.getBarrelRoll());
	}

	// Zoom clamps rather than wraps (like tilt, not like pan) - a lens has a real minimum/maximum
	// focal length, there's no "spin past the end" case for it. Bounds match CalibrationPanel's own
	// zoom spinner exactly, so a shuttle-driven value is never something the numeric spinner
	// couldn't also represent.
	static double integrateZoom(double currentFocalLengthMillimeters, double zoomRateMillimetersPerSecond,
			double elapsedSeconds) {
		double updated = currentFocalLengthMillimeters + zoomRateMillimetersPerSecond * elapsedSeconds;
		return Math.max(MIN_FOCAL_LENGTH_MILLIMETERS, Math.min(MAX_FOCAL_LENGTH_MILLIMETERS, updated));
	}

	static double wrapDegrees(double degrees) {
		double result = degrees % 360.0;
		if (result < 0.0)
			result += 360.0;
		return result;
	}

	static double clampAltitude(double altitude) {
		return Math.max(-90.0, Math.min(90.0, altitude));
	}
}
