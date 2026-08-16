package me.qbert.skywatch.camera.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.border.TitledBorder;

import org.junit.jupiter.api.Test;

import me.qbert.skywatch.camera.orientation.MountMode;

// A bare JPanel doesn't throw HeadlessException (see CalibrationPanel's own note), so this class's
// combo/checkbox-to-callback wiring and CardLayout swap are directly testable.
class MountControlPanelTest {

	@Test
	void startsOnTheNoneCardWithEngageDisabled() {
		MountControlPanel panel = new MountControlPanel();

		assertEquals(MountMode.NONE, findComboByItemType(panel, MountMode.class).getSelectedItem());
		assertFalse(findCheckboxByText(panel, "Engage mount").isEnabled());
	}

	@Test
	void selectingEquatorialMountShowsTheTrackingRateCardAndEnablesEngage() {
		MountControlPanel panel = new MountControlPanel();

		selectItem(findComboByItemType(panel, MountMode.class), MountMode.EQUATORIAL_MOUNT);

		assertTrue(findPanelByBorderTitle(panel, "Tracking rate").isVisible());
		assertFalse(findPanelByBorderTitle(panel, "Locked axes").isVisible());
		assertTrue(findCheckboxByText(panel, "Engage mount").isEnabled());
	}

	@Test
	void selectingLocationStabilizerShowsTheAxisLockCardInstead() {
		MountControlPanel panel = new MountControlPanel();

		selectItem(findComboByItemType(panel, MountMode.class), MountMode.LOCATION_STABILIZER);

		assertTrue(findPanelByBorderTitle(panel, "Locked axes").isVisible());
		assertFalse(findPanelByBorderTitle(panel, "Tracking rate").isVisible());
	}

	@Test
	void modeChangeFiresTheCallbackWithTheSelectedMode() {
		MountControlPanel panel = new MountControlPanel();
		AtomicReference<MountMode> lastMode = new AtomicReference<>();
		panel.setOnModeChanged(lastMode::set);

		selectItem(findComboByItemType(panel, MountMode.class), MountMode.LOCATION_STABILIZER);

		assertEquals(MountMode.LOCATION_STABILIZER, lastMode.get());
	}

	@Test
	void engageCheckboxFiresTheCallback() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.EQUATORIAL_MOUNT);
		AtomicReference<Boolean> lastEngaged = new AtomicReference<>();
		panel.setOnEngageChanged(lastEngaged::set);

		clickCheckbox(findCheckboxByText(panel, "Engage mount"));

		assertEquals(Boolean.TRUE, lastEngaged.get());
	}

	@Test
	void selectingASidereaTrackingRatePresetFiresTheResolvedDegreesPerHour() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.EQUATORIAL_MOUNT);
		AtomicReference<Double> lastRate = new AtomicReference<>();
		panel.setOnTrackingRateChanged(lastRate::set);

		JComboBox<?> rateCombo = findRateCombo(panel);
		selectItemByToString(rateCombo, "Lunar");

		assertEquals(me.qbert.skywatch.camera.orientation.TrackingRate.LUNAR.getDegreesPerHour(), lastRate.get(), 0.0001);
	}

	@Test
	void customRateSpinnerIsDisabledUntilCustomIsSelected() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.EQUATORIAL_MOUNT);
		JSpinner customSpinner = findSpinner(panel);

		assertFalse(customSpinner.isEnabled(), "custom rate spinner must start disabled - a preset is selected by default");

		selectItemByToString(findRateCombo(panel), "Custom");

		assertTrue(customSpinner.isEnabled());
	}

	@Test
	void customRateSpinnerChangeFiresTheCallbackOnlyWhenCustomIsSelected() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.EQUATORIAL_MOUNT);
		selectItemByToString(findRateCombo(panel), "Custom");
		AtomicInteger calls = new AtomicInteger();
		panel.setOnTrackingRateChanged(rate -> calls.incrementAndGet());

		findSpinner(panel).setValue(12.5);

		assertEquals(1, calls.get());
	}

	@Test
	void axisLockCheckboxesFireTheirOwnCallbacks() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.LOCATION_STABILIZER);
		AtomicReference<Boolean> ra = new AtomicReference<>();
		AtomicReference<Boolean> dec = new AtomicReference<>();
		AtomicReference<Boolean> roll = new AtomicReference<>();
		panel.setOnRaLockChanged(ra::set);
		panel.setOnDecLockChanged(dec::set);
		panel.setOnRollLockChanged(roll::set);

		clickCheckbox(findCheckboxByText(panel, "Lock RA"));
		clickCheckbox(findCheckboxByText(panel, "Lock Dec"));
		clickCheckbox(findCheckboxByText(panel, "Lock Barrel Roll"));

		assertEquals(Boolean.TRUE, ra.get());
		assertEquals(Boolean.TRUE, dec.get());
		assertEquals(Boolean.TRUE, roll.get());
	}

	// The same guard CalibrationPanel already established: refresh(...) pushes state into the widgets
	// without re-triggering the onXxxChanged callbacks, since JComboBox/JCheckBox/JSpinner all fire
	// their own change events on a programmatic update.
	@Test
	void refreshUpdatesTheWidgetsWithoutFiringAnyCallback() {
		MountControlPanel panel = new MountControlPanel();
		AtomicInteger modeCalls = new AtomicInteger();
		AtomicInteger engageCalls = new AtomicInteger();
		AtomicInteger raCalls = new AtomicInteger();
		panel.setOnModeChanged(mode -> modeCalls.incrementAndGet());
		panel.setOnEngageChanged(engaged -> engageCalls.incrementAndGet());
		panel.setOnRaLockChanged(locked -> raCalls.incrementAndGet());

		panel.refresh(MountMode.LOCATION_STABILIZER, true, 15.0, true, false, false, "Engaged");

		assertEquals(0, modeCalls.get());
		assertEquals(0, engageCalls.get());
		assertEquals(0, raCalls.get());
		assertEquals(MountMode.LOCATION_STABILIZER, findComboByItemType(panel, MountMode.class).getSelectedItem());
		assertTrue(findCheckboxByText(panel, "Engage mount").isSelected());
		assertTrue(findCheckboxByText(panel, "Lock RA").isSelected());
		assertFalse(findCheckboxByText(panel, "Lock Dec").isSelected());
	}

	@Test
	void refreshWithACustomRateSelectsTheCustomPresetAndEnablesItsSpinner() {
		MountControlPanel panel = new MountControlPanel();

		panel.refresh(MountMode.EQUATORIAL_MOUNT, false, 7.5, false, false, false, "Not engaged");

		assertEquals("Custom", findRateCombo(panel).getSelectedItem().toString());
		assertTrue(findSpinner(panel).isEnabled());
		assertEquals(7.5, (Double) findSpinner(panel).getValue(), 0.0001);
	}

	@Test
	void setControlsEnabledFalseDisablesEveryWidget() {
		MountControlPanel panel = new MountControlPanel();
		selectItem(findComboByItemType(panel, MountMode.class), MountMode.LOCATION_STABILIZER);

		panel.setControlsEnabled(false);

		assertFalse(findComboByItemType(panel, MountMode.class).isEnabled());
		assertFalse(findCheckboxByText(panel, "Engage mount").isEnabled());
		assertFalse(findCheckboxByText(panel, "Lock RA").isEnabled());
	}

	// --- Swing component-tree helpers (same shape as CalibrationPanelTest's own, generalized to
	// recurse since this panel's layout is nested, unlike CalibrationPanel's flat GridLayout) ---

	@SuppressWarnings("unchecked")
	private JComboBox<MountMode> findComboByItemType(Container root, Class<MountMode> itemType) {
		for (JComboBox<?> combo : findAll(root, JComboBox.class))
			if (combo.getItemCount() > 0 && itemType.isInstance(combo.getItemAt(0)))
				return (JComboBox<MountMode>) combo;
		throw new IllegalStateException("no combo box found with item type " + itemType);
	}

	private JComboBox<?> findRateCombo(Container root) {
		for (JComboBox<?> combo : findAll(root, JComboBox.class))
			if (combo.getItemCount() > 0 && !(combo.getItemAt(0) instanceof MountMode))
				return combo;
		throw new IllegalStateException("no tracking-rate combo box found");
	}

	private JSpinner findSpinner(Container root) {
		List<JSpinner> spinners = findAll(root, JSpinner.class);
		if (spinners.isEmpty())
			throw new IllegalStateException("no spinner found");
		return spinners.get(0);
	}

	private JCheckBox findCheckboxByText(Container root, String text) {
		for (JCheckBox checkbox : findAll(root, JCheckBox.class))
			if (text.equals(checkbox.getText()))
				return checkbox;
		throw new IllegalStateException("no checkbox found with text \"" + text + "\"");
	}

	private JPanel findPanelByBorderTitle(Container root, String title) {
		for (JPanel panel : findAll(root, JPanel.class))
			if (panel.getBorder() instanceof TitledBorder && title.equals(((TitledBorder) panel.getBorder()).getTitle()))
				return panel;
		throw new IllegalStateException("no panel found with border title \"" + title + "\"");
	}

	private <T extends Component> List<T> findAll(Container root, Class<T> type) {
		List<T> found = new ArrayList<>();
		for (Component component : root.getComponents()) {
			if (type.isInstance(component))
				found.add(type.cast(component));
			if (component instanceof Container)
				found.addAll(findAll((Container) component, type));
		}
		return found;
	}

	private void selectItem(JComboBox<MountMode> combo, MountMode item) {
		combo.setSelectedItem(item);
	}

	private void selectItemByToString(JComboBox<?> combo, String text) {
		for (int i = 0; i < combo.getItemCount(); i++) {
			if (text.equals(String.valueOf(combo.getItemAt(i)))) {
				combo.setSelectedIndex(i);
				return;
			}
		}
		throw new IllegalStateException("no combo item found with toString() \"" + text + "\"");
	}

	private void clickCheckbox(JCheckBox checkbox) {
		checkbox.setSelected(!checkbox.isSelected());
		for (ActionListener listener : checkbox.getActionListeners())
			listener.actionPerformed(new ActionEvent(checkbox, ActionEvent.ACTION_PERFORMED, "click"));
	}
}
