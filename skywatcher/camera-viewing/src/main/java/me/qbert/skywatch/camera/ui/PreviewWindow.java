package me.qbert.skywatch.camera.ui;

import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.WindowConstants;

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

// Task 8.1's interactive preview window - Swing, consistent with ga-base/earthclock's toolkit (see
// PreviewPanel's own note on how it relates to earthclock's ui.component.Canvas). Deliberately
// thin: every real decision (what to render, current time, show/hide state) lives in
// PreviewController - this class only wires Swing widgets to it. That split is not just style: a
// JFrame throws HeadlessException the instant it's constructed in a display-less environment, so
// this class could not be exercised by this module's own automated test suite in the sandbox it
// was built in (confirmed directly - see docs/tasks.md's verification note for this round). It was
// verified by code review here, not by an automated test - running/clicking through it for real is
// still outstanding.
//
// Controls: play/pause (SimulatedClock) and a show/hide toggle for Layer 1 (task 0.6). The original
// fixed ±1-minute step buttons were removed in the control panel redesign round [CLAUDE.md] -
// superseded by the control panel's own Time tab (a spring-loaded shuttle scrub control plus direct
// year/month/day/hour/minute jump fields), which offers a strictly more capable scrub affordance
// than a fixed ±1-minute step ever did.
public final class PreviewWindow extends JFrame {
	private static final long serialVersionUID = 1L;

	private static final int TICK_MILLIS = 250;

	private PreviewController controller;
	private final PreviewPanel panel = new PreviewPanel();
	private final JButton playPause = new JButton();
	private final JCheckBox showImage = new JCheckBox("Show camera image");
	// Shown at most once per camera (see switchTo(...), which resets this) - the underlying archive
	// scan stays truncated on every subsequent render until the user actually runs "cache-update",
	// so without this guard the 250ms render Timer below would pop this modal dialog up to 4 times
	// a second, effectively freezing the app.
	private boolean scanLimitWarningShown;
	// Same reasoning, for any OTHER render failure (a real user report: an uncaught exception from
	// this window's render Timer - see refresh()'s own comment below - surfaced as a stack trace on
	// stderr plus, apparently, some empty/context-less default dialog from Swing's own uncaught-
	// exception handling on the EDT, not anything this class ever showed itself). One-shot guard for
	// the same "don't repeat a modal dialog 4 times a second" reason as scanLimitWarningShown.
	private boolean renderErrorShown;
	// See setFastPreviewActive(...)'s own comment, below onTick().
	private boolean fastPreviewActive;

	public PreviewWindow(PreviewController controller) {
		super("camera-viewing preview - " + controller.getCameraConfig().getName());
		if (controller == null)
			throw new IllegalArgumentException("controller must not be null");
		this.controller = controller;

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(panel, BorderLayout.CENTER);
		add(buildControlBar(), BorderLayout.SOUTH);
		setSize(controller.getCanvasWidthPixels(), controller.getCanvasHeightPixels() + 40);

		// The panel's own client-area pixels (not the frame's outer size, which also includes the
		// control bar/borders) is what the render canvas should actually match - without this,
		// resizing the window never changes what gets rendered at all (the bug the user found: a
		// fixed-size render just gets letterboxed inside whatever the window becomes).
		panel.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				if (panel.getWidth() > 0 && panel.getHeight() > 0) {
					controller.setCanvasSize(panel.getWidth(), panel.getHeight());
					refresh();
				}
			}
		});

		// A real user report: a Virtual PTZ camera's rendered aspect ratio was wrong on first open.
		// Root cause - this constructor's own setSize(...) call above uses controller.
		// getCanvasWidthPixels()/getCanvasHeightPixels(), which for the very first camera opened in a
		// session is AppController's DEFAULT_CANVAS_WIDTH/HEIGHT_PIXELS (900x700), not this window's
		// real eventual on-screen size - and the constructor's own refresh() below runs before
		// setVisible(true) is ever called (that happens later, in ControlPanel.openCamera(...)), so
		// that first render bakes in the stale default. This is invisible for Real/Fixed-Virtual
		// cameras (FrameCompositor sizes the canvas from the real loaded image's own dimensions
		// instead), but for a PTZ camera EquirectangularSceneRenderer allocates its output at exactly
		// the given canvas size, so the wrong aspect ratio is baked directly into the rendered pixels.
		// The componentResized listener above only fixes this if the window's size later actually
		// CHANGES - if the user never manually resizes it, that never fires. windowOpened(...) is a
		// stronger guarantee: AWT fires it exactly once, after the window is realized with its real
		// on-screen dimensions, regardless of whether the user ever resizes it themselves.
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowOpened(WindowEvent e) {
				if (panel.getWidth() > 0 && panel.getHeight() > 0) {
					controller.setCanvasSize(panel.getWidth(), panel.getHeight());
					refresh();
				}
			}
		});

		Timer timer = new Timer(TICK_MILLIS, e -> onTick());
		timer.start();

		refresh();
	}

	// Rebinds this same window to a different camera's controller - task "camera-viewing app mode"
	// (direct user instruction): switching cameras from the control panel must not require
	// relaunching the whole app or even closing/reopening this window. Reuses the window's current
	// size as the new controller's starting canvas size (AppController.switchToCamera(...) already
	// does this too, on its own side, when it builds the new controller - keeping both in sync
	// rather than fighting over which one wins), and re-syncs every control's displayed state
	// (play/pause label, show-image checkbox) to the new controller's own starting values, since
	// they can differ from whatever the previous camera was showing.
	public void switchTo(PreviewController newController) {
		if (newController == null)
			throw new IllegalArgumentException("newController must not be null");
		this.controller = newController;
		setTitle("camera-viewing preview - " + controller.getCameraConfig().getName());
		showImage.setSelected(controller.isImageShown());
		showImage.setEnabled(true);
		scanLimitWarningShown = false;
		renderErrorShown = false;
		// newController is always a freshly-constructed PreviewController (never marking-active by
		// construction), but panel itself is REUSED across camera switches - reset it explicitly so a
		// new camera never inherits marking mode left on from whichever camera was open before it.
		panel.setMarkingModeActive(false);
		if (panel.getWidth() > 0 && panel.getHeight() > 0)
			controller.setCanvasSize(panel.getWidth(), panel.getHeight());
		refresh();
	}

	// Plate-solve technique 2's click-and-mark redesign (a direct user request, moving marking clicks
	// from a small dedicated panel - now retired - into this larger, resizable window for better
	// click accuracy). While active: the panel shows an aiming-circle cursor and reports clicks via
	// onMarkClicked(...) (see PreviewPanel.MarkClickListener), the render forces the real photo
	// visible with every overlay layer suppressed (PreviewController.setMarkingModeActive(...)'s own
	// comment), and the "Show camera image" checkbox is disabled and force-checked - it would
	// otherwise misleadingly suggest the image could be hidden while marking mode is overriding it
	// anyway. Deactivating restores the checkbox to whatever the underlying imageShown toggle
	// actually says, since marking mode never touches that field itself.
	public void setMarkingModeActive(boolean active) {
		controller.setMarkingModeActive(active);
		panel.setMarkingModeActive(active);
		showImage.setEnabled(!active);
		showImage.setSelected(active || controller.isImageShown());
		refresh();
	}

	public boolean isMarkingModeActive() {
		return controller.isMarkingModeActive();
	}

	public void setAimingCircleRadiusPixels(int radiusPixels) {
		panel.setAimingCircleRadiusPixels(radiusPixels);
	}

	public void onMarkClicked(PreviewPanel.MarkClickListener listener) {
		panel.onMarkClicked(listener);
	}

	// The image currently displayed in the panel - for marking mode, this is exactly the raw camera
	// image being clicked against (no overlay - see setMarkingModeActive(...)), so its own pixel
	// dimensions are what a mark's normalized position should be resolved against, matching the
	// retired ui.PlateSolveMarkingPanel's own "canvasWidth/Height = the displayed image's own
	// dimensions" convention exactly.
	public BufferedImage getDisplayedImage() {
		return panel.getImage();
	}

	private JPanel buildControlBar() {
		JPanel bar = new JPanel();

		playPause.addActionListener(e -> {
			if (controller.getClock().isPlaying())
				controller.getClock().pause();
			else
				controller.getClock().resume();
			refresh();
		});
		bar.add(playPause);

		showImage.setSelected(controller.isImageShown());
		showImage.addActionListener(e -> {
			controller.setImageShown(showImage.isSelected());
			refresh();
		});
		bar.add(showImage);

		return bar;
	}

	// A real user report: this window's own 250ms tick was independently calling a full,
	// expensive refresh() (whenever the clock is playing, the default) REGARDLESS of whether
	// ControlPanel's own PTZ tick loop was actively showing a cheap fast preview - completely
	// undermining that optimization, since a full render would silently override the fast preview
	// again every 250ms. fastPreviewActive (set by ControlPanel.tickPtzOrientation() for exactly as
	// long as a PTZ shuttle is actively deflected) suppresses this tick's own refresh() entirely, so
	// ONLY the cheap fast-preview renders happen while dragging - normal time-driven playback resumes
	// automatically the instant dragging stops and the flag is cleared.
	void setFastPreviewActive(boolean fastPreviewActive) {
		this.fastPreviewActive = fastPreviewActive;
	}

	private void onTick() {
		if (fastPreviewActive)
			return;
		if (controller.getClock().isPlaying())
			refresh();
	}

	// The fast-preview path (a real user report about PTZ pan/tilt performance - see
	// PreviewController.renderFastPtzPreview(...)'s own comment for the full reasoning): blits the
	// given already-composited image directly, bypassing refresh()'s own controller.
	// renderCurrentFrame() call and its scan-limit/error-dialog bookkeeping - neither applies to this
	// synthetic composite, and re-deriving them from a small/fast render would be misleading anyway.
	void showFastPreview(BufferedImage image) {
		panel.setImage(image);
	}

	// Package-visible (not private) so ControlPanel (same package) can trigger a re-render after
	// mutating the shared FrameCompositor.Options (a layer toggle flipped, say) - ControlPanel has
	// no other way to know when this window's Timer will next tick.
	//
	// Reports a failure via a real, informative dialog rather than re-throwing - a real user report:
	// this used to rethrow as an uncaught RuntimeException straight out of the Timer's callback on
	// the EDT, which printed a real stack trace to stderr but ALSO appears to have surfaced as some
	// empty/context-less dialog from Swing's own default uncaught-exception handling (not anything
	// this class ever built itself) - and, with the render Timer firing every 250ms, a persistently
	// failing render would keep re-triggering that indefinitely. One error is still printed to
	// stderr (so the full stack trace remains available for real debugging, matching what already
	// got captured), but the user-facing dialog now always has real, specific text and only shows
	// once per camera (renderErrorShown, reset in switchTo(...) like scanLimitWarningShown above).
	void refresh() {
		try {
			BufferedImage frame = controller.renderCurrentFrame();
			panel.setImage(frame);
			playPause.setText(controller.getClock().isPlaying() ? "Pause" : "Play");

			if (controller.isArchiveScanTruncated() && !scanLimitWarningShown) {
				scanLimitWarningShown = true;
				JOptionPane.showMessageDialog(this,
						"There are too many archived images to synchronize yet for \""
								+ controller.getCameraConfig().getName() + "\" - only part of the archive has been "
								+ "cached so far. Run \"cache-update --camera " + controller.getCameraConfig().getName()
								+ "\" from the command line to finish synchronizing it (with visible progress), or "
								+ "raise --cache-scan-limit.",
						"Archive scan incomplete", JOptionPane.WARNING_MESSAGE);
			}
		} catch (Exception e) {
			e.printStackTrace();
			if (!renderErrorShown) {
				renderErrorShown = true;
				JOptionPane.showMessageDialog(this,
						"Failed to render the preview for \"" + controller.getCameraConfig().getName() + "\": "
								+ e.getClass().getSimpleName() + (e.getMessage() != null ? " - " + e.getMessage() : "")
								+ ". See the console for the full error.",
						"Render error", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}
