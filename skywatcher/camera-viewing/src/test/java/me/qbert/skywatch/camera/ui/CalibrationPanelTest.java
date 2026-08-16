package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

// Task 8.2's calibration controls. A bare JPanel doesn't throw HeadlessException (see
// PreviewPanel's own note), so this class's spinner-to-callback wiring is directly testable.
class CalibrationPanelTest {

	@Test
	void orientationListenerFiresOnAnySpinnerAmongAltitudeAzimuthRoll() {
		CalibrationPanel panel = new CalibrationPanel();
		AtomicInteger calls = new AtomicInteger();
		panel.onOrientationChanged(calls::incrementAndGet);

		panel.setValues(1.0, 2.0, 3.0, 50.0, 0.0, 0.0); // programmatic - must NOT fire
		assertEquals(0, calls.get());
	}

	@Test
	void gettersReflectSetValues() {
		CalibrationPanel panel = new CalibrationPanel();

		panel.setValues(10.5, 90.5, 1.5, 55.0, 45.25, -75.25);

		assertEquals(10.5, panel.getAltitude(), 0.0001);
		assertEquals(90.5, panel.getAzimuth(), 0.0001);
		assertEquals(1.5, panel.getBarrelRoll(), 0.0001);
		assertEquals(55.0, panel.getZoom(), 0.0001);
		assertEquals(45.25, panel.getLatitude(), 0.0001);
		assertEquals(-75.25, panel.getLongitude(), 0.0001);
	}

	@Test
	void saveAndRevertListenersFire() {
		CalibrationPanel panel = new CalibrationPanel();
		AtomicBoolean saved = new AtomicBoolean(false);
		AtomicBoolean reverted = new AtomicBoolean(false);
		panel.onSave(() -> saved.set(true));
		panel.onRevert(() -> reverted.set(true));

		fireButton(panel, "Save");
		fireButton(panel, "Revert to previously saved values");

		assertTrue(saved.get());
		assertTrue(reverted.get());
	}

	// Defensive fix for a real user report ("the barrel distortion settings are not persisted"): a
	// value typed into a spinner's text field only commits to the model on focus-lost/Enter - if
	// something clicked Save without that commit happening first, the just-typed value would never
	// reach getDistortionA() at all. onSave(...) now force-commits every spinner before running the
	// caller's listener.
	@Test
	void savingCommitsAnUnfinishedTypedEditBeforeTheListenerRuns() throws Exception {
		CalibrationPanel panel = new CalibrationPanel();
		javax.swing.JSpinner distortionASpinner = findSpinner(panel, 6);
		javax.swing.JFormattedTextField textField =
				((javax.swing.JSpinner.DefaultEditor) distortionASpinner.getEditor()).getTextField();

		// Directly sets the text field's text WITHOUT going through setValue(...)/commitEdit() -
		// exactly what a user typing a value and immediately clicking Save (before any focus-lost
		// event) would leave behind.
		textField.setText("-0.0152");
		assertEquals(0.0, panel.getDistortionA(), 0.0001, "sanity check: the model hasn't committed yet");

		java.util.concurrent.atomic.AtomicReference<Double> savedValue = new java.util.concurrent.atomic.AtomicReference<>();
		panel.onSave(() -> savedValue.set(panel.getDistortionA()));
		fireButton(panel, "Save");

		assertEquals(-0.0152, savedValue.get(), 0.0001,
				"the just-typed value must be committed and visible to the save listener");
	}

	@Test
	void aRealSpinnerChangeAfterSetValuesStillFiresTheListener() {
		// Confirms the setValues(...) guard only suppresses callbacks DURING that call, not
		// permanently - a genuine change afterward (indistinguishable from a user drag) must still
		// fire normally.
		CalibrationPanel panel = new CalibrationPanel();
		panel.setValues(1.0, 2.0, 3.0, 50.0, 0.0, 0.0);
		AtomicInteger calls = new AtomicInteger();
		panel.onOrientationChanged(calls::incrementAndGet);

		findSpinner(panel, 0).setValue(15.0); // altitude - the first spinner added

		assertEquals(1, calls.get());
	}

	@Test
	void distortionDefaultsToIdentity() {
		CalibrationPanel panel = new CalibrationPanel();

		assertEquals(0.0, panel.getDistortionA(), 0.0001);
		assertEquals(0.0, panel.getDistortionB(), 0.0001);
		assertEquals(0.0, panel.getDistortionC(), 0.0001);
		assertEquals(1.0, panel.getDistortionD(), 0.0001);
	}

	@Test
	void setDistortionValuesUpdatesGettersWithoutFiringTheListener() {
		CalibrationPanel panel = new CalibrationPanel();
		AtomicInteger calls = new AtomicInteger();
		panel.onDistortionChanged(calls::incrementAndGet);

		panel.setDistortionValues(-0.0152, -0.0262, 0.0009, 1.0579); // programmatic - must NOT fire
		assertEquals(0, calls.get());

		assertEquals(-0.0152, panel.getDistortionA(), 0.0001);
		assertEquals(-0.0262, panel.getDistortionB(), 0.0001);
		assertEquals(0.0009, panel.getDistortionC(), 0.0001);
		assertEquals(1.0579, panel.getDistortionD(), 0.0001);
	}

	@Test
	void aRealDistortionSpinnerChangeFiresTheListener() {
		CalibrationPanel panel = new CalibrationPanel();
		AtomicInteger calls = new AtomicInteger();
		panel.onDistortionChanged(calls::incrementAndGet);

		findSpinner(panel, 6).setValue(-0.02); // distortionA - the 7th spinner added

		assertEquals(1, calls.get());
	}

	@Test
	void setControlsEnabledCascadesToEverySpinnerAndButton() {
		CalibrationPanel panel = new CalibrationPanel();

		panel.setControlsEnabled(false);
		for (java.awt.Component component : panel.getComponents())
			if (component instanceof javax.swing.JSpinner || component instanceof javax.swing.JButton)
				assertTrue(!component.isEnabled(), "expected every spinner/button to be disabled");

		panel.setControlsEnabled(true);
		for (java.awt.Component component : panel.getComponents())
			if (component instanceof javax.swing.JSpinner || component instanceof javax.swing.JButton)
				assertTrue(component.isEnabled(), "expected every spinner/button to be re-enabled");
	}

	// Direct user instruction: distortion only makes sense for a Real camera - a caller with a valid
	// session for a Virtual camera calls setControlsEnabled(true) followed by
	// setDistortionControlsEnabled(false), leaving the other 6 spinners/buttons untouched.
	@Test
	void setDistortionControlsEnabledOnlyAffectsTheFourDistortionSpinners() {
		CalibrationPanel panel = new CalibrationPanel();
		panel.setControlsEnabled(true);

		panel.setDistortionControlsEnabled(false);

		for (int i = 0; i < 6; i++)
			assertTrue(findSpinner(panel, i).isEnabled(), "the non-distortion spinner at index " + i + " must stay enabled");
		for (int i = 6; i < 10; i++)
			assertTrue(!findSpinner(panel, i).isEnabled(), "the distortion spinner at index " + i + " must be disabled");

		panel.setDistortionControlsEnabled(true);
		for (int i = 6; i < 10; i++)
			assertTrue(findSpinner(panel, i).isEnabled(), "the distortion spinner at index " + i + " must be re-enabled");
	}

	// PTZ has no meaningful Save/Revert (CLAUDE.md's "Orientation editing" - it auto-persists
	// instead) - ControlPanel calls this with false for PTZ while leaving the ten spinners enabled,
	// same "narrower gate" pattern as setDistortionControlsEnabled(...) above.
	@Test
	void setSaveRevertEnabledOnlyAffectsTheTwoButtons() {
		CalibrationPanel panel = new CalibrationPanel();
		panel.setControlsEnabled(true);

		panel.setSaveRevertEnabled(false);

		for (int i = 0; i < 10; i++)
			assertTrue(findSpinner(panel, i).isEnabled(), "spinner " + i + " must stay enabled - only Save/Revert are gated");
		for (java.awt.Component component : panel.getComponents())
			if (component instanceof javax.swing.JButton)
				assertTrue(!component.isEnabled(), "both Save and Revert must be disabled");

		panel.setSaveRevertEnabled(true);
		for (java.awt.Component component : panel.getComponents())
			if (component instanceof javax.swing.JButton)
				assertTrue(component.isEnabled(), "both Save and Revert must be re-enabled");
	}

	private javax.swing.JSpinner findSpinner(CalibrationPanel panel, int index) {
		int seen = 0;
		for (java.awt.Component component : panel.getComponents()) {
			if (component instanceof javax.swing.JSpinner) {
				if (seen == index)
					return (javax.swing.JSpinner) component;
				seen++;
			}
		}
		throw new IllegalStateException("no JSpinner at index " + index);
	}

	private void fireButton(CalibrationPanel panel, String text) {
		for (java.awt.Component component : panel.getComponents()) {
			if (component instanceof javax.swing.JButton && text.equals(((javax.swing.JButton) component).getText())) {
				for (java.awt.event.ActionListener listener : ((javax.swing.JButton) component).getActionListeners())
					listener.actionPerformed(new java.awt.event.ActionEvent(component, java.awt.event.ActionEvent.ACTION_PERFORMED, "click"));
				return;
			}
		}
		throw new IllegalStateException("no button found with text \"" + text + "\"");
	}
}
