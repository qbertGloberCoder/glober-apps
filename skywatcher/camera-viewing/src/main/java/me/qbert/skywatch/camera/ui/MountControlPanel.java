package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.GridLayout;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import me.qbert.skywatch.camera.orientation.MountMode;
import me.qbert.skywatch.camera.orientation.TrackingRate;

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

// The Camera Mount tab's widget - lives in ui.ControlPanel alongside the existing Camera Pan/Tilt
// tab (ui.PtzOrientationPanel), gated the same way (PTZ Virtual cameras only, this round - see
// orientation.MountTransformRuntime's own class comment). Mode selection swaps a CardLayout area
// between the equatorial mount's tracking-rate control and the geolocation stabilizer's three
// per-axis lock checkboxes, rather than showing/graying-out both sets of controls at once - a direct
// user concern ("the UI controls might be tricky... the EQ mount has tracking speeds but the
// location stabilizer doesn't").
//
// A bare JPanel doesn't throw HeadlessException (only Window/Frame/Dialog subclasses check
// headlessness in their constructor - see CalibrationPanel's own note), so this class is fully
// constructible/testable in this module's own display-less sandbox. Push-callback style, matching
// CalibrationPanel's convention (discrete mode/checkbox/combo changes, not continuous shuttle rates
// like ui.PtzOrientationPanel - no polling needed here).
public final class MountControlPanel extends JPanel {
	private static final long serialVersionUID = 1L;

	private static final String CARD_NONE = "none";
	private static final String CARD_EQUATORIAL = "equatorial";
	private static final String CARD_STABILIZER = "stabilizer";

	// A UI-only preset wrapper - CUSTOM has no meaningful getDegreesPerHour() of its own, the custom
	// spinner supplies that value instead. Kept distinct from orientation.TrackingRate (the real
	// domain enum with only the three real presets) rather than adding a UI-specific member there.
	private enum RatePreset {
		SIDEREAL(TrackingRate.SIDEREAL.getDegreesPerHour(), "Sidereal"),
		SOLAR(TrackingRate.SOLAR.getDegreesPerHour(), "Solar"),
		LUNAR(TrackingRate.LUNAR.getDegreesPerHour(), "Lunar"),
		CUSTOM(Double.NaN, "Custom");

		private final double degreesPerHour;
		private final String label;

		RatePreset(double degreesPerHour, String label) {
			this.degreesPerHour = degreesPerHour;
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final JComboBox<MountMode> modeCombo = new JComboBox<>(MountMode.values());
	private final JCheckBox engageCheckbox = new JCheckBox("Engage mount");
	private final JLabel statusLabel = new JLabel("Not engaged");

	private final JComboBox<RatePreset> trackingRateCombo = new JComboBox<>(RatePreset.values());
	private final JSpinner customRateSpinner = new JSpinner(
			new SpinnerNumberModel(TrackingRate.SIDEREAL.getDegreesPerHour(), -360.0, 360.0, 0.001));

	private final JCheckBox raLockCheckbox = new JCheckBox("Lock RA");
	private final JCheckBox decLockCheckbox = new JCheckBox("Lock Dec");
	private final JCheckBox rollLockCheckbox = new JCheckBox("Lock Barrel Roll");

	private final CardLayout modeCards = new CardLayout();
	private final JPanel modeCardPanel = new JPanel(modeCards);

	// See CalibrationPanel's identical field/comment - JComboBox/JCheckBox/JSpinner all fire their
	// own change events on a programmatic setSelectedItem(...)/setSelected(...)/setValue(...), which
	// would otherwise immediately re-trigger the onXxxChanged callbacks below every time refresh(...)
	// pushes state in from ControlPanel.
	private boolean updatingProgrammatically;

	private Consumer<MountMode> onModeChanged = mode -> {
	};
	private Consumer<Boolean> onEngageChanged = engaged -> {
	};
	private Consumer<Double> onTrackingRateChanged = rate -> {
	};
	private Consumer<Boolean> onRaLockChanged = locked -> {
	};
	private Consumer<Boolean> onDecLockChanged = locked -> {
	};
	private Consumer<Boolean> onRollLockChanged = locked -> {
	};

	public MountControlPanel() {
		setLayout(new BorderLayout(4, 4));

		JPanel top = new JPanel(new GridLayout(0, 2, 4, 4));
		top.add(new JLabel("Mount mode"));
		top.add(modeCombo);
		top.add(new JLabel(""));
		top.add(engageCheckbox);
		add(top, BorderLayout.NORTH);

		JPanel nonePanel = new JPanel();
		nonePanel.add(new JLabel("No orientation transform active for this mode."));

		JPanel equatorialPanel = new JPanel(new GridLayout(0, 2, 4, 4));
		equatorialPanel.setBorder(BorderFactory.createTitledBorder("Tracking rate"));
		equatorialPanel.add(new JLabel("Rate"));
		equatorialPanel.add(trackingRateCombo);
		equatorialPanel.add(new JLabel("Custom (deg/hour)"));
		equatorialPanel.add(customRateSpinner);

		JPanel stabilizerPanel = new JPanel(new GridLayout(0, 1, 4, 4));
		stabilizerPanel.setBorder(BorderFactory.createTitledBorder("Locked axes"));
		stabilizerPanel.add(raLockCheckbox);
		stabilizerPanel.add(decLockCheckbox);
		stabilizerPanel.add(rollLockCheckbox);

		modeCardPanel.add(nonePanel, CARD_NONE);
		modeCardPanel.add(equatorialPanel, CARD_EQUATORIAL);
		modeCardPanel.add(stabilizerPanel, CARD_STABILIZER);
		add(modeCardPanel, BorderLayout.CENTER);

		add(statusLabel, BorderLayout.SOUTH);

		customRateSpinner.setEnabled(false);
		engageCheckbox.setEnabled(false);

		modeCombo.addActionListener(e -> {
			showCardFor((MountMode) modeCombo.getSelectedItem());
			engageCheckbox.setEnabled(modeCombo.getSelectedItem() != MountMode.NONE);
			if (!updatingProgrammatically)
				onModeChanged.accept((MountMode) modeCombo.getSelectedItem());
		});
		engageCheckbox.addActionListener(e -> {
			if (!updatingProgrammatically)
				onEngageChanged.accept(engageCheckbox.isSelected());
		});
		trackingRateCombo.addActionListener(e -> {
			customRateSpinner.setEnabled(trackingRateCombo.getSelectedItem() == RatePreset.CUSTOM);
			if (!updatingProgrammatically)
				fireTrackingRateChanged();
		});
		customRateSpinner.addChangeListener(e -> {
			if (!updatingProgrammatically && trackingRateCombo.getSelectedItem() == RatePreset.CUSTOM)
				fireTrackingRateChanged();
		});
		raLockCheckbox.addActionListener(e -> {
			if (!updatingProgrammatically)
				onRaLockChanged.accept(raLockCheckbox.isSelected());
		});
		decLockCheckbox.addActionListener(e -> {
			if (!updatingProgrammatically)
				onDecLockChanged.accept(decLockCheckbox.isSelected());
		});
		rollLockCheckbox.addActionListener(e -> {
			if (!updatingProgrammatically)
				onRollLockChanged.accept(rollLockCheckbox.isSelected());
		});

		showCardFor(MountMode.NONE);
	}

	private void fireTrackingRateChanged() {
		RatePreset selected = (RatePreset) trackingRateCombo.getSelectedItem();
		double rate = selected == RatePreset.CUSTOM ? (Double) customRateSpinner.getValue() : selected.degreesPerHour;
		onTrackingRateChanged.accept(rate);
	}

	private void showCardFor(MountMode mode) {
		switch (mode) {
			case EQUATORIAL_MOUNT:
				modeCards.show(modeCardPanel, CARD_EQUATORIAL);
				break;
			case LOCATION_STABILIZER:
				modeCards.show(modeCardPanel, CARD_STABILIZER);
				break;
			case NONE:
			default:
				modeCards.show(modeCardPanel, CARD_NONE);
				break;
		}
	}

	// Pushes the camera's current mount state into the panel without firing any of the onXxxChanged
	// callbacks - called by ui.ControlPanel after every camera switch/mode change, mirroring
	// CalibrationPanel.setValues(...)'s identical role.
	public void refresh(MountMode mode, boolean engaged, double trackingRateDegreesPerHour, boolean raLocked,
			boolean decLocked, boolean rollLocked, String statusText) {
		updatingProgrammatically = true;
		try {
			modeCombo.setSelectedItem(mode);
			showCardFor(mode);
			engageCheckbox.setEnabled(mode != MountMode.NONE);
			engageCheckbox.setSelected(engaged);

			RatePreset matchingPreset = presetFor(trackingRateDegreesPerHour);
			trackingRateCombo.setSelectedItem(matchingPreset);
			customRateSpinner.setEnabled(matchingPreset == RatePreset.CUSTOM);
			customRateSpinner.setValue(trackingRateDegreesPerHour);

			raLockCheckbox.setSelected(raLocked);
			decLockCheckbox.setSelected(decLocked);
			rollLockCheckbox.setSelected(rollLocked);

			statusLabel.setText(statusText);
		} finally {
			updatingProgrammatically = false;
		}
	}

	private static RatePreset presetFor(double trackingRateDegreesPerHour) {
		for (RatePreset preset : new RatePreset[] { RatePreset.SIDEREAL, RatePreset.SOLAR, RatePreset.LUNAR }) {
			if (Math.abs(preset.degreesPerHour - trackingRateDegreesPerHour) < 1e-9)
				return preset;
		}
		return RatePreset.CUSTOM;
	}

	public void setOnModeChanged(Consumer<MountMode> listener) {
		this.onModeChanged = listener;
	}

	public void setOnEngageChanged(Consumer<Boolean> listener) {
		this.onEngageChanged = listener;
	}

	public void setOnTrackingRateChanged(Consumer<Double> listener) {
		this.onTrackingRateChanged = listener;
	}

	public void setOnRaLockChanged(Consumer<Boolean> listener) {
		this.onRaLockChanged = listener;
	}

	public void setOnDecLockChanged(Consumer<Boolean> listener) {
		this.onDecLockChanged = listener;
	}

	public void setOnRollLockChanged(Consumer<Boolean> listener) {
		this.onRollLockChanged = listener;
	}

	// JPanel.setEnabled(...) does not cascade to child components in vanilla Swing (see
	// CalibrationPanel's identical note) - explicit whole-panel gate, called false whenever the
	// active camera isn't PTZ.
	public void setControlsEnabled(boolean enabled) {
		modeCombo.setEnabled(enabled);
		engageCheckbox.setEnabled(enabled && modeCombo.getSelectedItem() != MountMode.NONE);
		trackingRateCombo.setEnabled(enabled);
		customRateSpinner.setEnabled(enabled && trackingRateCombo.getSelectedItem() == RatePreset.CUSTOM);
		raLockCheckbox.setEnabled(enabled);
		decLockCheckbox.setEnabled(enabled);
		rollLockCheckbox.setEnabled(enabled);
	}
}
