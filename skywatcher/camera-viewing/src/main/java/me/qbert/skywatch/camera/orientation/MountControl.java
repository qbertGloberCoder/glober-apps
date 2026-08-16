package me.qbert.skywatch.camera.orientation;

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

// Two decoupled controls for a camera's orientation-transformer slot - see CLAUDE.md's "Mount
// mode + enable control". The mode selector is a persisted camera setting; the enable toggle is
// NOT persisted (user's own words: "if they turn it on, even switching cameras should turn it
// off") - callers are expected to construct a fresh MountControl (or call reset()) whenever a
// camera is (re)selected, rather than reading enabled state back out of saved config.
public class MountControl {
	private MountMode mode = MountMode.NONE;
	private boolean enabled = false;

	public MountMode getMode() {
		return mode;
	}

	public void setMode(MountMode mode) {
		if (mode == null)
			throw new IllegalArgumentException("mode must not be null");
		this.mode = mode;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	// True only when a transform is actually plugged in AND engaged. mode == NONE makes the
	// enable toggle moot regardless of its raw value - see CLAUDE.md.
	public boolean isActive() {
		return mode != MountMode.NONE && enabled;
	}

	// Ephemeral by design (see class comment) - resets to the disengaged/no-op state, e.g. on
	// camera switch.
	public void reset() {
		enabled = false;
	}
}
