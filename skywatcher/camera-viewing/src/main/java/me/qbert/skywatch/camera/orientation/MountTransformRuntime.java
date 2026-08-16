package me.qbert.skywatch.camera.orientation;

import me.qbert.skywatch.astro.ObservationTime;
import me.qbert.skywatch.astro.ObserverLocation;
import me.qbert.skywatch.camera.config.CameraConfig;

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

// The missing wiring found by an earlier "full backlog audit" round: EquatorialMountTransform/
// EquatorialMountController/GeolocationStabilizerTransform were all built and unit-tested in
// isolation, but nothing in the actual render path ever constructed one or called compute(...) - a
// hand-edited config with mountControl.mode set would render as if the mount were off. This class is
// the missing per-camera orchestrator: one instance held for the lifetime of a PTZ camera's
// PreviewController (see that class's own mountRuntime field), owning the live EquatorialMount/
// GeolocationStabilizer transform instances and detecting MountControl.isEnabled() edges every render
// (a render-loop poll, not a listener push - see EquatorialMountTransform.stateChanged()'s own
// comment on why the transforms themselves are pure functions with nothing to cache).
//
// PTZ Virtual cameras only, this round (direct user confirmation) - same population as ui.
// PtzOrientationPanel/the "Camera Pan/Tilt" tab. A Fixed Virtual camera has no single mutable
// currentOrientation field to lock/engage against (Fixed cameras persist via the append-only
// CalibrationHistory with explicit Save/Revert instead) - wiring the mount into that case needs a new
// commit-into-CalibrationHistory path that doesn't exist yet, deliberately left for a later round.
public final class MountTransformRuntime {
	private final EquatorialMountTransform eqTransform = new EquatorialMountTransform();
	private final EquatorialMountController eqController;
	private final GeolocationStabilizerTransform stabilizerTransform = new GeolocationStabilizerTransform();
	private boolean stabilizerLastEnabled;

	public MountTransformRuntime(CameraConfig camera) {
		if (camera == null)
			throw new IllegalArgumentException("camera must not be null");
		this.eqController = new EquatorialMountController(camera, eqTransform);
	}

	// Sets the tracking rate for the equatorial mount - a pass-through to the owned
	// EquatorialMountTransform, exposed here so callers (ui.ControlPanel) never need to reach into
	// this class's otherwise-private transform instances.
	public void setTrackingRateDegreesPerHour(double trackingRateDegreesPerHour) {
		eqTransform.setTrackingRateDegreesPerHour(trackingRateDegreesPerHour);
	}

	public double getTrackingRateDegreesPerHour() {
		return eqTransform.getTrackingRateDegreesPerHour();
	}

	public void setRaLocked(boolean raLocked) {
		stabilizerTransform.setRaLocked(raLocked);
	}

	public boolean isRaLocked() {
		return stabilizerTransform.isRaLocked();
	}

	public void setDecLocked(boolean decLocked) {
		stabilizerTransform.setDecLocked(decLocked);
	}

	public boolean isDecLocked() {
		return stabilizerTransform.isDecLocked();
	}

	public void setRollLocked(boolean rollLocked) {
		stabilizerTransform.setRollLocked(rollLocked);
	}

	public boolean isRollLocked() {
		return stabilizerTransform.isRollLocked();
	}

	// Backed by EquatorialMountTransform.isLocked(), NOT EquatorialMountController.isEnabled() -
	// the controller's isEnabled() is just a thin read of MountControl.isEnabled() (the same flag
	// this class's own edge-detection compares against), so it can never independently answer "is
	// the transform ACTUALLY locked right now" - only the transform's own lock state can.
	public boolean isEquatorialMountEngaged() {
		return eqTransform.isLocked();
	}

	public boolean isStabilizerEngaged() {
		return stabilizerTransform.isEngaged();
	}

	// Called once per render. Detects MountControl.isEnabled() edges against whichever transform
	// matches the CURRENT mode, engaging/disengaging exactly once per transition (not once per
	// render - re-locking on every call would reset the lock reference constantly, defeating the
	// whole point of a fixed reference point), and cleans up a transform left active by a PREVIOUS
	// mode that the mode selector has since moved away from (switching mode never auto-engages the
	// new one - see MountControl.setMode(...)'s own callers in ui.ControlPanel).
	public Orientation resolve(CameraConfig camera, Orientation baseOrientation, ObservationTime time,
			ObserverLocation location) {
		if (camera == null)
			throw new IllegalArgumentException("camera must not be null");
		if (baseOrientation == null)
			throw new IllegalArgumentException("baseOrientation must not be null");
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");

		MountControl mc = camera.getMountControl();
		long epochMillis = time.getTime().getTimeInMillis();

		// A transform left active by a mode the selector has since moved away from - commit and
		// disengage it before evaluating the current mode, regardless of what that current mode is.
		if (mc.getMode() != MountMode.EQUATORIAL_MOUNT && eqTransform.isLocked())
			eqController.disable(epochMillis);
		if (mc.getMode() != MountMode.LOCATION_STABILIZER && stabilizerTransform.isEngaged())
			disengageStabilizer(camera, location);

		switch (mc.getMode()) {
			case EQUATORIAL_MOUNT:
				// Guarded on eqTransform.isLocked() (NOT eqController.isEnabled(), which just reads
				// mc.isEnabled() right back - see isEquatorialMountEngaged()'s own comment): without
				// this guard, every render tick would re-lock (resetting the reference point/timestamp
				// to "now"), which would make elapsed-time tracking never accumulate at all.
				if (mc.isEnabled() && !eqTransform.isLocked())
					eqController.enable(location.getLatitude(), epochMillis);
				else if (!mc.isEnabled() && eqTransform.isLocked())
					eqController.disable(epochMillis);
				return eqTransform.compute(time, location, camera.getCurrentOrientation());
			case LOCATION_STABILIZER:
				if (mc.isEnabled() && !stabilizerLastEnabled)
					stabilizerTransform.engage(baseOrientation, location.getLatitude(), location.getLongitude());
				else if (!mc.isEnabled() && stabilizerLastEnabled)
					disengageStabilizer(camera, location);
				stabilizerLastEnabled = mc.isEnabled();
				return stabilizerTransform.compute(time, location, baseOrientation);
			case NONE:
			default:
				return baseOrientation;
		}
	}

	// "Even switching cameras should turn it off" (CLAUDE.md) - called for the OUTGOING camera before
	// it's persisted/replaced, so the persisted currentOrientation reflects the mount's live computed
	// position at the moment of switching away, not a stale lock/engage-time snapshot. A no-op when
	// neither transform is active.
	public void disengageForCameraSwitch(CameraConfig camera, ObservationTime time, ObserverLocation location) {
		if (camera == null)
			throw new IllegalArgumentException("camera must not be null");
		if (time == null)
			throw new IllegalArgumentException("time must not be null");
		if (location == null)
			throw new IllegalArgumentException("location must not be null");

		if (eqTransform.isLocked())
			eqController.disable(time.getTime().getTimeInMillis());
		if (stabilizerTransform.isEngaged())
			disengageStabilizer(camera, location);
		stabilizerLastEnabled = false;
		camera.getMountControl().reset();
	}

	// Mirrors EquatorialMountController.disable()'s exact commit shape - GeolocationStabilizerTransform
	// has no controller wrapper of its own (unlike the EQ mount), so this class owns that missing
	// commit-then-disengage step directly.
	private void disengageStabilizer(CameraConfig camera, ObserverLocation location) {
		Orientation committed = stabilizerTransform.computeEngagedOrientation(location.getLatitude(),
				location.getLongitude());
		camera.setCurrentOrientation(committed);
		stabilizerTransform.disengage();
	}
}
