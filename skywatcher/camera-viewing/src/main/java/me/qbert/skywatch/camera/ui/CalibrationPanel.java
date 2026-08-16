package me.qbert.skywatch.camera.ui;

import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

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

// Task 8.2's calibration controls - "Update the camera's orientation" [CLAUDE.md's "Orientation
// editing"] as six plain JSpinners (altitude/azimuth/roll/zoom/latitude/longitude) plus Save/Revert
// buttons. A bare JPanel doesn't throw HeadlessException (see PreviewPanel's own note - only
// Window/Frame/Dialog subclasses check headlessness in their constructor), so this class itself is
// constructible in this module's own headless test environment; CalibrationPanelTest exercises the
// spinner-to-callback wiring directly rather than leaving it entirely unverified.
public final class CalibrationPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private final JSpinner altitude = new JSpinner(new SpinnerNumberModel(0.0, -90.0, 90.0, 0.1));
	private final JSpinner azimuth = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 359.9, 0.1));
	private final JSpinner barrelRoll = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 0.1));
	private final JSpinner zoom = new JSpinner(new SpinnerNumberModel(50.0, 1.0, 2000.0, 1.0));
	private final JSpinner latitude = new JSpinner(new SpinnerNumberModel(0.0, -90.0, 90.0, 0.01));
	private final JSpinner longitude = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 0.01));

	// Barrel/pincushion distortion [CLAUDE.md's "Barrel distortion built into the lens hierarchy"] -
	// projection.AbstractCameraProjection's own quartic coefficients, identity default a=b=c=0, d=1.
	// Small step (the user's own real calibrated values are all under 0.03 in magnitude, except D
	// near 1.0) so the spinner arrows are actually useful for fine-tuning, not just typing over.
	private final JSpinner distortionA = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 10.0, 0.0001));
	private final JSpinner distortionB = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 10.0, 0.0001));
	private final JSpinner distortionC = new JSpinner(new SpinnerNumberModel(0.0, -10.0, 10.0, 0.0001));
	private final JSpinner distortionD = new JSpinner(new SpinnerNumberModel(1.0, -10.0, 10.0, 0.0001));

	private final JButton save = new JButton("Save");
	private final JButton revert = new JButton("Revert to previously saved values");

	// JSpinner.setValue(...) fires the same ChangeEvent a user drag does - without this guard,
	// setValues(...) (used to reflect a save/revert back into the spinners) would immediately
	// re-trigger the onXxxChanged callbacks below, marking a fresh pending edit right after the
	// save/revert that was supposed to clear it.
	private boolean updatingProgrammatically;

	public CalibrationPanel() {
		setLayout(new GridLayout(0, 2, 4, 4));
		addRow("Altitude (deg)", altitude);
		addRow("Azimuth (deg)", azimuth);
		addRow("Barrel roll (deg)", barrelRoll);
		addRow("Zoom / focal length (mm)", zoom);
		addRow("Latitude (deg)", latitude);
		addRow("Longitude (deg)", longitude);
		addRow("Distortion A", distortionA);
		addRow("Distortion B", distortionB);
		addRow("Distortion C", distortionC);
		addRow("Distortion D", distortionD);
		add(save);
		add(revert);
	}

	private void addRow(String label, JSpinner spinner) {
		add(new JLabel(label));
		add(spinner);
	}

	// Populates the spinners from the session's current pending values, without firing the
	// change listeners below - used when a preview refresh (e.g. after a save/revert) needs to
	// reflect a value that changed for a reason OTHER than the user dragging a spinner.
	public void setValues(double altitudeDegrees, double azimuthDegrees, double barrelRollDegrees, double zoomMillimeters,
			double latitudeDegrees, double longitudeDegrees) {
		updatingProgrammatically = true;
		try {
			altitude.setValue(altitudeDegrees);
			azimuth.setValue(azimuthDegrees);
			barrelRoll.setValue(barrelRollDegrees);
			zoom.setValue(zoomMillimeters);
			latitude.setValue(latitudeDegrees);
			longitude.setValue(longitudeDegrees);
		} finally {
			updatingProgrammatically = false;
		}
	}

	// Populates the four distortion spinners from a projection's CURRENT coefficients, without
	// firing onDistortionChanged - a separate method from setValues(...) above since distortion
	// isn't part of plate.PlateSolveSession's pending orientation/location/zoom at all; it lives
	// directly on the CameraProjection instance (see AbstractCameraProjection).
	public void setDistortionValues(double a, double b, double c, double d) {
		updatingProgrammatically = true;
		try {
			distortionA.setValue(a);
			distortionB.setValue(b);
			distortionC.setValue(c);
			distortionD.setValue(d);
		} finally {
			updatingProgrammatically = false;
		}
	}

	// onOrientationChanged receives (altitude, azimuth, barrelRoll) together, matching
	// PlateSolveSession.adjustOrientation(...)'s own bundled Orientation - onLocationChanged
	// receives (latitude, longitude) together for the same reason. None of these fire while
	// setValues(...) is programmatically updating the spinners - see updatingProgrammatically.
	public void onOrientationChanged(Runnable listener) {
		altitude.addChangeListener(e -> runIfNotProgrammatic(listener));
		azimuth.addChangeListener(e -> runIfNotProgrammatic(listener));
		barrelRoll.addChangeListener(e -> runIfNotProgrammatic(listener));
	}

	public void onZoomChanged(Consumer<Double> listener) {
		zoom.addChangeListener(e -> {
			if (!updatingProgrammatically)
				listener.accept((Double) zoom.getValue());
		});
	}

	public void onLocationChanged(Runnable listener) {
		latitude.addChangeListener(e -> runIfNotProgrammatic(listener));
		longitude.addChangeListener(e -> runIfNotProgrammatic(listener));
	}

	// Bundled together like onOrientationChanged/onLocationChanged, since a distortion change is
	// always applied as all four coefficients at once (AbstractCameraProjection.
	// setDistortionCoefficients(...) itself takes all four together).
	public void onDistortionChanged(Runnable listener) {
		distortionA.addChangeListener(e -> runIfNotProgrammatic(listener));
		distortionB.addChangeListener(e -> runIfNotProgrammatic(listener));
		distortionC.addChangeListener(e -> runIfNotProgrammatic(listener));
		distortionD.addChangeListener(e -> runIfNotProgrammatic(listener));
	}

	private void runIfNotProgrammatic(Runnable listener) {
		if (!updatingProgrammatically)
			listener.run();
	}

	// Defensive: a value typed directly into a spinner's text field only commits to the underlying
	// model on focus-lost/Enter (JFormattedTextField's own default behavior) - normally guaranteed to
	// happen before this button's own click is processed (focus transfers before a click fires), but
	// forcing the commit here removes any doubt that a just-typed value (as opposed to one set via
	// the spinner's arrows, which commits immediately) is what actually gets read and saved.
	public void onSave(Runnable listener) {
		save.addActionListener(e -> {
			commitAllSpinners();
			listener.run();
		});
	}

	private void commitAllSpinners() {
		JSpinner[] spinners = { altitude, azimuth, barrelRoll, zoom, latitude, longitude, distortionA, distortionB,
				distortionC, distortionD };
		for (JSpinner spinner : spinners) {
			try {
				spinner.commitEdit();
			} catch (java.text.ParseException e) {
				// Leave the spinner's own invalid-input recovery (revert-or-flag) to Swing itself -
				// nothing else here depends on this specific spinner's value being freshly committed.
			}
		}
	}

	public void onRevert(Runnable listener) {
		revert.addActionListener(e -> listener.run());
	}

	public double getAltitude() {
		return (Double) altitude.getValue();
	}

	public double getAzimuth() {
		return (Double) azimuth.getValue();
	}

	public double getBarrelRoll() {
		return (Double) barrelRoll.getValue();
	}

	public double getZoom() {
		return (Double) zoom.getValue();
	}

	public double getLatitude() {
		return (Double) latitude.getValue();
	}

	public double getLongitude() {
		return (Double) longitude.getValue();
	}

	public double getDistortionA() {
		return (Double) distortionA.getValue();
	}

	public double getDistortionB() {
		return (Double) distortionB.getValue();
	}

	public double getDistortionC() {
		return (Double) distortionC.getValue();
	}

	public double getDistortionD() {
		return (Double) distortionD.getValue();
	}

	// JPanel.setEnabled(...) does NOT cascade to child components in vanilla Swing - disabling the
	// panel alone leaves every spinner/button independently clickable. The control panel's Camera
	// Orientation tab [CLAUDE.md's control panel redesign round] needs this to actually gray out
	// editing for a camera with no active PlateSolveSession (a PTZ camera - see AppController.
	// autoCreateEditSession(...)), not just cosmetically.
	public void setControlsEnabled(boolean enabled) {
		altitude.setEnabled(enabled);
		azimuth.setEnabled(enabled);
		barrelRoll.setEnabled(enabled);
		zoom.setEnabled(enabled);
		latitude.setEnabled(enabled);
		longitude.setEnabled(enabled);
		setDistortionControlsEnabled(enabled);
		save.setEnabled(enabled);
		revert.setEnabled(enabled);
	}

	// A narrower gate than setControlsEnabled(...) above, specifically for camera KIND rather than
	// "is there a session at all": distortion correction only makes sense for a Real camera's real
	// photo (direct user instruction) - a Virtual camera (Fixed or PTZ) still gets a normal, enabled
	// orientation/zoom/location panel, just with these 4 spinners grayed out. Called independently of
	// setControlsEnabled(...) - a caller with a valid session for a Virtual camera calls
	// setControlsEnabled(true) followed by setDistortionControlsEnabled(false), not one or the other.
	public void setDistortionControlsEnabled(boolean enabled) {
		distortionA.setEnabled(enabled);
		distortionB.setEnabled(enabled);
		distortionC.setEnabled(enabled);
		distortionD.setEnabled(enabled);
	}

	// Another narrower gate, same pattern as setDistortionControlsEnabled(...) above but for camera
	// ORIENTATION MODE rather than kind: a PTZ camera has no meaningful Save/Revert at all (CLAUDE.md's
	// "Orientation editing" - it auto-persists instead), so ControlPanel disables just these two
	// buttons for PTZ while the six orientation/zoom/location spinners (and, kind-permitting, the four
	// distortion spinners) stay live and immediately-effective either way.
	public void setSaveRevertEnabled(boolean enabled) {
		save.setEnabled(enabled);
		revert.setEnabled(enabled);
	}
}
