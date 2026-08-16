package me.qbert.skywatch.camera.orientation;

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

// Orchestrates enable/disable persistence for a PTZ camera's equatorial mount - see CLAUDE.md's
// "Equatorial mount: activation/deactivation persistence". Deliberately lives in the camera-config
// layer, not inside EquatorialMountTransform itself: the transform is a pure function given lock
// state, this class is what captures/writes back CameraConfig's single current orientation around
// it. (Only meaningful for PTZ cameras in practice - see CameraConfig.getCurrentOrientation()/
// docs/tasks.md task 3.3's note on the still-open Fixed-virtual-camera question, which this class
// does not attempt to answer.)
public class EquatorialMountController {
	private final CameraConfig camera;
	private final EquatorialMountTransform transform;

	public EquatorialMountController(CameraConfig camera, EquatorialMountTransform transform) {
		if (camera == null)
			throw new IllegalArgumentException("camera must not be null");
		if (transform == null)
			throw new IllegalArgumentException("transform must not be null");
		this.camera = camera;
		this.transform = transform;
	}

	// Captures the camera's current orientation + this instant as the lock reference. Each call
	// is an independent lock, exactly like re-engaging a real mount's clutch after manually
	// repointing it - see CLAUDE.md.
	public void enable(double observerLatitude, long epochMillis) {
		MountControl control = camera.getMountControl();
		if (control.getMode() != MountMode.EQUATORIAL_MOUNT)
			throw new IllegalStateException("mount mode must be EQUATORIAL_MOUNT to enable this controller");

		transform.lock(camera.getCurrentOrientation(), observerLatitude, epochMillis);
		control.setEnabled(true);
	}

	// Writes the just-computed current orientation back as the camera's new resting/base position
	// - it must NOT snap back to the pre-enable orientation. A no-op if not currently enabled.
	public void disable(long epochMillis) {
		if (!transform.isLocked())
			return;

		camera.setCurrentOrientation(transform.computeLockedOrientation(epochMillis));
		transform.unlock();
		camera.getMountControl().setEnabled(false);
	}

	public boolean isEnabled() {
		return camera.getMountControl().isEnabled();
	}
}
