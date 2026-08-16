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

// Which orientation-transformer occupies a Virtual (or Pre-recorded-only Real) camera's slot.
// See CLAUDE.md's "Mount mode + enable control" - this is a persisted camera setting, distinct
// from the separate mount-enable toggle (which does not persist - see MountControl).
public enum MountMode {
	NONE,
	EQUATORIAL_MOUNT,
	LOCATION_STABILIZER
}
