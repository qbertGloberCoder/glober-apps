package me.qbert.skywatch.camera.config;

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

// Derived from CameraType - never chosen independently. See CLAUDE.md's "Camera setup: Real vs.
// Virtual, and how that determines Fixed vs. PTZ".
public enum OrientationMode {
	// Orientation only ever changes via an explicit, time-versioned CalibrationHistory entry.
	FIXED,
	// Software-orientable; no calibration history, only a single current/most-recent orientation.
	PTZ
}
