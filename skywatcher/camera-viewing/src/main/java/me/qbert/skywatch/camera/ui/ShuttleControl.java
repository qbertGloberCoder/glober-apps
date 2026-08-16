package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;

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

// The "Time" tab's spring-loaded time-scrub control [CLAUDE.md's control panel redesign round] -
// modeled directly on a physical shuttle/jog control, per the user's own description: grab it,
// slide away from center, it scrubs at a rate proportional to distance from center (fine control
// near the middle, rapid acceleration toward the edges), continues for as long as the mouse button
// is held, and snaps back to 0 and stops on release.
//
// Built on a real JSlider rather than a fully custom-painted component - JSlider already provides
// the drag/paint/ChangeListener behavior; this class adds only the two things a plain JSlider
// doesn't do on its own: continuous rate reporting while dragging (via the quadratic
// position-to-rate mapping in rateForPosition(...)) and the spring-back-to-zero-on-release behavior.
// A bare JPanel (and the JSlider/JRadioButton components inside it) doesn't throw HeadlessException
// - only Window/Frame/Dialog subclasses check headlessness in their constructor (see
// CalibrationPanel's own note) - so this class is constructible, and its pure position-to-rate
// mapping directly testable, in this module's own display-less test/build sandbox.
public final class ShuttleControl extends JPanel {
	private static final long serialVersionUID = 1L;

	public static final int SLIDER_RANGE = 100;

	// First-guess constants, easy to retune from feedback - not load-bearing on the rest of this
	// widget's design. Expressed as a plain rateMultiplier (simulated-milliseconds-per-real-
	// millisecond), matching clock.SimulatedClock.setRate(double)'s own units: "1 simulated hour per
	// real-time second" at full deflection in slow mode is 3600 simulated seconds per 1 real second,
	// i.e. a multiplier of 3600; "1 simulated week per real-time second" in fast mode is
	// 604,800 (seconds in a week) by the same reasoning.
	public static final double SLOW_MODE_MAX_RATE = 3_600.0;
	public static final double FAST_MODE_MAX_RATE = 604_800.0;

	private final JSlider slider;
	private final JRadioButton slowMode = new JRadioButton("Slow", true);
	private final JRadioButton fastMode = new JRadioButton("Fast");
	private final double slowModeMaxRate;
	private final double fastModeMaxRate;

	private Consumer<Double> onRateChanged = rate -> {
	};
	private Runnable onRelease = () -> {
	};

	public ShuttleControl() {
		this(JSlider.HORIZONTAL, SLOW_MODE_MAX_RATE, FAST_MODE_MAX_RATE);
	}

	// Generalization for other rate-controlled quantities (e.g. a PTZ camera's pan/tilt, in
	// degrees/second rather than this class's own original simulated-time units) that still want the
	// exact same spring-loaded shuttle mechanic - a vertical slider orientation for a "tilt" control,
	// and caller-supplied slow/fast max rates instead of this class's own SLOW_MODE_MAX_RATE/
	// FAST_MODE_MAX_RATE (which stay as the DEFAULT values the no-arg constructor above uses, for the
	// Time tab's own existing usage - unchanged). rateForPosition(...)'s quadratic position-to-rate
	// mapping itself needs no change - it was always generic in the max-rate parameter, only
	// getCurrentRate() ever hardcoded which constant to pass it.
	public ShuttleControl(int sliderOrientation, double slowModeMaxRate, double fastModeMaxRate) {
		this.slowModeMaxRate = slowModeMaxRate;
		this.fastModeMaxRate = fastModeMaxRate;
		this.slider = new JSlider(sliderOrientation, -SLIDER_RANGE, SLIDER_RANGE, 0);

		setLayout(new BorderLayout(4, 4));

		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(slowMode);
		modeGroup.add(fastMode);
		JPanel modePanel = new JPanel();
		modePanel.add(slowMode);
		modePanel.add(fastMode);
		add(modePanel, BorderLayout.NORTH);
		add(slider, BorderLayout.CENTER);

		slider.addChangeListener(e -> onRateChanged.accept(getCurrentRate()));
		// Changing mode WHILE held recomputes the rate immediately from the slider's current
		// position, rather than waiting for the next drag movement.
		slowMode.addActionListener(e -> onRateChanged.accept(getCurrentRate()));
		fastMode.addActionListener(e -> onRateChanged.accept(getCurrentRate()));

		slider.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased(MouseEvent e) {
				// setValue(0) itself fires the ChangeListener above (reporting rate 0.0) before this
				// method continues - the caller's separate onRelease callback is the deliberate
				// "stop" signal on top of that (e.g. actually pausing the clock), not a duplicate of
				// the same information.
				slider.setValue(0);
				onRelease.run();
			}
		});
	}

	public void onRateChanged(Consumer<Double> listener) {
		if (listener == null)
			throw new IllegalArgumentException("listener must not be null");
		this.onRateChanged = listener;
	}

	public void onRelease(Runnable listener) {
		if (listener == null)
			throw new IllegalArgumentException("listener must not be null");
		this.onRelease = listener;
	}

	public int getSliderPosition() {
		return slider.getValue();
	}

	public boolean isFastMode() {
		return fastMode.isSelected();
	}

	// Public (unlike the private helper it replaced) so a caller driven by its own polling Timer -
	// rather than the onRateChanged(...) push callback - can read the current rate directly (e.g.
	// PtzOrientationPanel's pan/tilt integration, ticked independently of this control's own events).
	public double getCurrentRate() {
		return rateForPosition(slider.getValue(), fastMode.isSelected() ? fastModeMaxRate : slowModeMaxRate);
	}

	// Package-visible for direct unit testing without needing a real JSlider drag. Quadratic in the
	// normalized position: fine control near the center, rapid acceleration toward the edges -
	// matches "grab it and slide, speed increases the further you go." Math.signum(...) recovers the
	// direction that squaring the normalized position would otherwise discard.
	static double rateForPosition(int sliderPosition, double maxRateForMode) {
		double normalized = sliderPosition / (double) SLIDER_RANGE;
		return Math.signum(normalized) * normalized * normalized * maxRateForMode;
	}
}
