package me.qbert.skywatch.camera.watch;

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

// Holds "which WatchedObject is currently selected" - deliberately plain session/UI state, not a
// CameraConfig-persisted setting: unlike orientation/location/calibration, the spec never describes
// the watched object as belonging to a particular camera's saved profile, and treating it that way
// would force a redundant per-camera default choice for something that's really "whatever the user
// is currently inspecting" - closer in spirit to the mount-enable toggle's ephemeral, never-written
// design (../CLAUDE.md's "Mount mode + enable control") than to a persisted calibration field.
// Revisit if a future UI round decides otherwise.
//
// One mutable instance is meant to be shared by whatever eventually renders the crosshair (4.3),
// samples the trailing path (4.6), and reads the OSD detail tier (4.9) for a given viewing session,
// so all three agree on the same current selection without each holding their own copy.
public final class WatchedObjectRegistry {

	// Spec §6's own stated default: "Object defaults to the sun."
	private WatchedObject current = WatchedObject.sun();

	public WatchedObject getCurrent() {
		return current;
	}

	public void setCurrent(WatchedObject watchedObject) {
		if (watchedObject == null)
			throw new IllegalArgumentException("watchedObject must not be null");
		this.current = watchedObject;
	}
}
