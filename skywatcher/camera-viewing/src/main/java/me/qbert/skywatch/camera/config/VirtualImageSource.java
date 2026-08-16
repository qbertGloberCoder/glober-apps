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

// What a Virtual camera's own image is sourced from - determines Fixed vs. PTZ (see
// CameraType.getOrientationMode()). Every Virtual camera has exactly one of these two - there is
// no "no image" case; a Virtual camera is more like Stellarium than a real camera and always shows
// some picture (which may itself be meaningless, e.g. "unicorns on a see-through background" -
// see CLAUDE.md's "Camera setup: Real vs. Virtual"). Always a single static image, never a
// multi-frame sequence - unknown-video plate-solving and similar scrubbing scenarios are always
// Real, Pre-recorded-only cameras instead (see CLAUDE.md's "Plate solving").
public enum VirtualImageSource {
	// A full 360-degree equirectangular PNG - can pan/tilt/zoom anywhere on the sphere -> PTZ.
	EQUIRECTANGULAR_360,
	// One fixed framing - a user-uploaded static scene image, or a cached fixed crop of a
	// 360-degree source computed once (not recomputed live) -> Fixed.
	STATIC_DIRECTIONAL
}
