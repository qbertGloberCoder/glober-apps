package me.qbert.skywatch.camera.clock;

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

// Not in spec - see CLAUDE.md's "Simulated clock (pause/resume, like Stellarium)". Independent of
// wall-clock time; used by Virtual-camera and roaming rendering. Fixed (real-camera) mode never
// wires this in - a real frame's time is always its own capture timestamp, see CameraConfig/
// CalibrationHistory instead.
//
// Anyone needing "the current render time" (orientation-transformer calls, OSD, path/graticule
// sampling) should take it from here rather than calling System.currentTimeMillis() directly, so
// pausing actually freezes everything downstream.
public class SimulatedClock {
	private final WallClock wallClock;

	// Generalizes the original binary play(1.0)/pause(0.0) model to an arbitrary multiplier - see
	// setRate(double) below. A negative rate plays backward; the magnitude scales speed. This is
	// the "Part E" extension the control panel's shuttle time control needs (CLAUDE.md's control
	// panel redesign round) - pause()/resume()/isPlaying()/setTime(...) keep their exact original
	// contracts on top of it, unaffected by the generalization.
	private double rateMultiplier;
	private long simulatedTimeAtAnchorMillis;
	private long wallTimeAtAnchorMillis;

	public SimulatedClock() {
		this(WallClock.SYSTEM);
	}

	public SimulatedClock(WallClock wallClock) {
		if (wallClock == null)
			throw new IllegalArgumentException("wallClock must not be null");
		this.wallClock = wallClock;

		// Play is the "live preview" default - see CLAUDE.md.
		this.rateMultiplier = 1.0;
		this.wallTimeAtAnchorMillis = wallClock.currentTimeMillis();
		this.simulatedTimeAtAnchorMillis = this.wallTimeAtAnchorMillis;
	}

	public long getCurrentTimeMillis() {
		if (rateMultiplier == 0.0)
			return simulatedTimeAtAnchorMillis;

		long elapsedRealMillis = wallClock.currentTimeMillis() - wallTimeAtAnchorMillis;
		return simulatedTimeAtAnchorMillis + Math.round(elapsedRealMillis * rateMultiplier);
	}

	public boolean isPlaying() {
		return rateMultiplier != 0.0;
	}

	public double getRate() {
		return rateMultiplier;
	}

	// Freezes at the current simulated time. A no-op if already paused. Exact original contract -
	// equivalent to setRate(0.0), but doesn't re-anchor wallTimeAtAnchorMillis (unneeded once
	// rateMultiplier is 0.0, since getCurrentTimeMillis() ignores it in that state).
	public void pause() {
		if (rateMultiplier == 0.0)
			return;

		simulatedTimeAtAnchorMillis = getCurrentTimeMillis();
		rateMultiplier = 0.0;
	}

	// Resumes advancing in real time from wherever it was paused - NOT from wall-clock "now".
	// A no-op if already playing (ANY nonzero rate, e.g. a shuttle-control fast-forward left in
	// effect) - the exact original "if (playing) return;" check, translated directly rather than
	// narrowed to "already at exactly 1.0".
	public void resume() {
		if (isPlaying())
			return;

		wallTimeAtAnchorMillis = wallClock.currentTimeMillis();
		rateMultiplier = 1.0;
	}

	// Jumps to an arbitrary timestamp without changing play/pause state - the fourth capability
	// alongside play/pause/resume implied by the spec's time-scrubbing features (hybrid mode's
	// "preview 3 weeks from now", roaming's adjustable render time).
	public void setTime(long epochMillis) {
		simulatedTimeAtAnchorMillis = epochMillis;
		wallTimeAtAnchorMillis = wallClock.currentTimeMillis();
	}

	// The shuttle control's entry point - re-anchors so changing rate mid-flight doesn't jump: the
	// current computed time (under the OLD rate) becomes the new anchor, then the wall-clock anchor
	// resets to now, then the new rate applies going forward. A rate of exactly 0.0 is equivalent to
	// pause(); a rate of exactly 1.0 is equivalent to resume() - both expressible through this one
	// general entry point, since re-anchoring at the current computed time is a no-op for isPlaying()/
	// getCurrentTimeMillis() either way.
	public void setRate(double rateMultiplier) {
		long currentTimeMillis = getCurrentTimeMillis();
		this.simulatedTimeAtAnchorMillis = currentTimeMillis;
		this.wallTimeAtAnchorMillis = wallClock.currentTimeMillis();
		this.rateMultiplier = rateMultiplier;
	}
}
