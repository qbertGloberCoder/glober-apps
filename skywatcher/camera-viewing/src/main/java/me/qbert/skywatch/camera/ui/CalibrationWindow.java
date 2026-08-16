package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.WindowConstants;

import me.qbert.skywatch.camera.config.CameraType;
import me.qbert.skywatch.camera.orientation.Orientation;
import me.qbert.skywatch.camera.plate.PlateSolveSession;
import me.qbert.skywatch.camera.projection.AbstractCameraProjection;
import me.qbert.skywatch.camera.projection.CameraProjection;

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

// Task 8.2's calibration UI - technique 1's manual plate-solve dial-in [CLAUDE.md's "Plate
// solving"]: drag the spinners, watch the overlay move against a real archived still frame,
// Save/Revert. Deliberately thin, same reasoning as PreviewWindow: every real decision lives in
// CalibrationController - this class could not be exercised by this module's own automated test
// suite (JFrame throws HeadlessException in a display-less environment - see docs/tasks.md's
// verification note for this round), so it was written and reviewed, not run.
//
// Previews against whatever archived frame is at or before a fixed target time chosen once at
// construction (the frame the camera was pointed at when the shift was noticed, typically) - unlike
// PreviewWindow, there is no play/scrub here: technique 1 is a static, one-frame visual comparison
// (CLAUDE.md's own description - "find an unambiguous key frame... scrub time/location/orientation
// until the computed sun/moon/stars visually line up"), not a live feed.
public final class CalibrationWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private final CalibrationController controller;
	private final CalibrationPanel calibrationPanel = new CalibrationPanel();
	private final PreviewPanel previewPanel = new PreviewPanel();
	private final long targetEpochMillis;
	// See PreviewWindow's own identically-named field for why this guard exists - repeated
	// spinner-drag ChangeEvents would otherwise re-show this modal dialog on every single drag tick.
	private boolean scanLimitWarningShown;
	// Same reasoning as PreviewWindow's own identically-named field - refresh() is called on every
	// single spinner drag, so a persistently failing render (or the always-legitimate "no archived
	// frame at or before this target time yet" case) must not re-show a modal dialog on every one of
	// those events.
	private boolean renderErrorShown;

	public CalibrationWindow(CalibrationController controller, long targetEpochMillis) {
		super("camera-viewing calibration - " + controller.getSession().getCameraConfig().getName());
		if (controller == null)
			throw new IllegalArgumentException("controller must not be null");
		this.controller = controller;
		this.targetEpochMillis = targetEpochMillis;

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(previewPanel, BorderLayout.CENTER);
		add(calibrationPanel, BorderLayout.EAST);
		setSize(1000, 700);

		wireControls();
		syncPanelFromSession();
		refresh();
	}

	private void wireControls() {
		calibrationPanel.onOrientationChanged(() -> {
			controller.adjustOrientation(
					new Orientation(calibrationPanel.getAltitude(), calibrationPanel.getAzimuth(), calibrationPanel.getBarrelRoll()));
			refresh();
		});
		calibrationPanel.onZoomChanged(zoom -> {
			controller.adjustZoom(zoom);
			refresh();
		});
		calibrationPanel.onLocationChanged(() -> {
			controller.adjustLocation(calibrationPanel.getLatitude(), calibrationPanel.getLongitude());
			refresh();
		});
		// Distortion coefficients are mutable state directly ON the live CameraProjection instance
		// (CLAUDE.md's "Barrel distortion built into the lens hierarchy") - no PlateSolveSession
		// involvement at all: a spinner change mutates the projection in place, visible on the very
		// next render, and Save/Revert fall out for free from the EXISTING CameraConfigStore
		// round-trip (Save persists whatever's currently on the camera including its mutated
		// projection; Revert reloads a fresh CameraConfig+projection from disk).
		calibrationPanel.onDistortionChanged(() -> {
			distortableProjection().setDistortionCoefficients(calibrationPanel.getDistortionA(),
					calibrationPanel.getDistortionB(), calibrationPanel.getDistortionC(), calibrationPanel.getDistortionD());
			refresh();
		});
		calibrationPanel.onSave(() -> {
			try {
				controller.save(targetEpochMillis);
				JOptionPane.showMessageDialog(this, "Saved.");
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Save failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			}
		});
		calibrationPanel.onRevert(() -> {
			try {
				controller.revert();
				syncPanelFromSession();
				refresh();
			} catch (Exception e) {
				JOptionPane.showMessageDialog(this, "Revert failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			}
		});
	}

	private void syncPanelFromSession() {
		PlateSolveSession session = controller.getSession();
		Orientation orientation = session.getPendingOrientation();
		calibrationPanel.setValues(orientation.getAltitude(), orientation.getAzimuth(), orientation.getBarrelRoll(),
				session.getPendingZoom(), session.getPendingLatitude(), session.getPendingLongitude());

		AbstractCameraProjection projection = distortableProjection();
		calibrationPanel.setDistortionValues(projection.getDistortionCoefficientA(), projection.getDistortionCoefficientB(),
				projection.getDistortionCoefficientC(), projection.getDistortionCoefficientD());

		// Distortion only makes sense for a Real camera's real photo - direct user instruction. This
		// window is reused for Virtual Fixed cameras too (PlateSolveSession's own Save/Revert model
		// isn't Real-only - see its class comment), so gate independently of the rest of the panel.
		calibrationPanel.setDistortionControlsEnabled(session.getCameraConfig().getType().getKind() == CameraType.Kind.REAL);
	}

	// Every CameraProjection this codebase can construct extends AbstractCameraProjection - the same
	// assumption config.CameraConfigStore's own writeProjection(...) already makes.
	private AbstractCameraProjection distortableProjection() {
		CameraProjection projection = controller.getSession().getCameraConfig().getProjection();
		return (AbstractCameraProjection) projection;
	}

	// See PreviewWindow.refresh()'s own comment for why this reports via a real dialog + a printed
	// stack trace rather than re-throwing.
	private void refresh() {
		try {
			BufferedImage frame = controller.renderPreview(targetEpochMillis);
			previewPanel.setImage(frame);

			if (controller.isArchiveScanTruncated() && !scanLimitWarningShown) {
				scanLimitWarningShown = true;
				String cameraName = controller.getSession().getCameraConfig().getName();
				JOptionPane.showMessageDialog(this,
						"There are too many archived images to synchronize yet for \"" + cameraName + "\" - only "
								+ "part of the archive has been cached so far. Run \"cache-update --camera "
								+ cameraName + "\" from the command line to finish synchronizing it (with visible "
								+ "progress), or raise --cache-scan-limit.",
						"Archive scan incomplete", JOptionPane.WARNING_MESSAGE);
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (!renderErrorShown) {
				renderErrorShown = true;
				JOptionPane.showMessageDialog(this,
						"Failed to render the calibration preview: " + e.getClass().getSimpleName()
								+ (e.getMessage() != null ? " - " + e.getMessage() : "") + ". See the console for the full error.",
						"Render error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}
